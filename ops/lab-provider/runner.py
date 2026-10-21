"""Single-writer durable authority; side effects never precede a committed intent."""
import fcntl
import json
import os
from pathlib import Path
import sqlite3
import threading
import time

from protocol import command, digest, encode, identity, instant, require, result, run_binding, Rejected


class Runner:
    def __init__(self, state_dir, epoch, inventory, driver, clock=time.time, max_runs=10, max_records=100000):
        self.epoch, self.inventory, self.driver, self.clock = identity(epoch), inventory, driver, clock
        self.max_runs, self.max_records = max_runs, max_records
        self.mutex = threading.RLock()
        state_dir = Path(state_dir)
        state_dir.mkdir(mode=0o700, parents=False, exist_ok=True)
        self.lock = os.open(state_dir / "writer.lock", os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        try:
            fcntl.flock(self.lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            os.close(self.lock)
            raise Rejected("RUNNER_ALREADY_ACTIVE", 503) from None
        db_path = state_dir / "state.sqlite3"
        require(not db_path.is_symlink(), "UNTRUSTED_STATE", 503)
        self.db = sqlite3.connect(db_path, isolation_level=None, check_same_thread=False)
        os.chmod(db_path, 0o600)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("PRAGMA synchronous=FULL")
        self.db.execute("PRAGMA foreign_keys=ON")
        require(self.db.execute("PRAGMA quick_check").fetchone()[0] == "ok", "STATE_CORRUPT", 503)
        self.db.executescript("""
          CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY CHECK(id=1), epoch TEXT NOT NULL);
          CREATE TABLE IF NOT EXISTS runs (
            id TEXT PRIMARY KEY, binding TEXT NOT NULL, epoch TEXT NOT NULL, lease INTEGER NOT NULL,
            expiry REAL NOT NULL, status TEXT NOT NULL, ever INTEGER NOT NULL DEFAULT 0,
            slot INTEGER, start_hash TEXT, created REAL NOT NULL);
          CREATE UNIQUE INDEX IF NOT EXISTS active_slot ON runs(slot) WHERE status <> 'CLEANED';
          CREATE TABLE IF NOT EXISTS commands (
            id TEXT PRIMARY KEY, run_id TEXT NOT NULL REFERENCES runs(id), hash TEXT NOT NULL,
            action TEXT NOT NULL, response TEXT, created REAL NOT NULL);
        """)
        # Every restart closes all old routes, even with the same epoch. No uncertain command is re-executed.
        with self.transaction():
            self.db.execute("INSERT INTO settings VALUES(1,?) ON CONFLICT(id) DO UPDATE SET epoch=excluded.epoch", (epoch,))
            self.db.execute("UPDATE runs SET status='CLEANING' WHERE status <> 'CLEANED'")
        self.recovery_complete = False
        self.recover()

    def close(self):
        self.db.close()
        os.close(self.lock)

    def transaction(self):
        runner = self
        class Transaction:
            def __enter__(self):
                runner.db.execute("BEGIN IMMEDIATE")
            def __exit__(self, kind, value, traceback):
                runner.db.execute("ROLLBACK" if kind else "COMMIT")
        return Transaction()

    def row(self, run_id):
        return self.db.execute("SELECT * FROM runs WHERE id=?", (run_id,)).fetchone()

    def cleaned(self, run_id):
        try:
            confirmed = self.driver.stop(run_id)
        except Exception:
            confirmed = False
        if confirmed:
            with self.transaction():
                self.db.execute("UPDATE runs SET status='CLEANED' WHERE id=?", (run_id,))
        return confirmed

    def recover(self):
        with self.mutex:
            try:
                # Includes orphans created between committed intent and process/resource creation.
                for run_id in self.driver.discover():
                    require(self.driver.stop(run_id), "ORPHAN_CLEANUP_UNCONFIRMED", 503)
                for row in self.db.execute("SELECT id FROM runs WHERE status <> 'CLEANED'").fetchall():
                    self.cleaned(row[0])
                self.recovery_complete = not self.db.execute("SELECT 1 FROM runs WHERE status <> 'CLEANED'").fetchone()
            except Exception:
                self.recovery_complete = False

    def sweep(self):
        with self.mutex:
            now = self.clock()
            for row in self.db.execute("SELECT id FROM runs WHERE status <> 'CLEANED' AND (expiry<=? OR status='CLEANING')", (now,)).fetchall():
                with self.transaction():
                    self.db.execute("UPDATE runs SET status='CLEANING' WHERE id=?", (row[0],))
                self.cleaned(row[0])
            if not self.recovery_complete:
                self.recover()
            # Keep immutable identities and hashes, erase private outputs after one day.
            self.db.execute("UPDATE commands SET response=NULL WHERE created<? AND response IS NOT NULL AND run_id IN (SELECT id FROM runs WHERE status='CLEANED')", (now - 86400,))

    def save(self, cmd, response):
        with self.transaction():
            self.db.execute("UPDATE commands SET response=? WHERE id=?", (encode(response).decode(), cmd["commandId"]))
        return response

    def execute(self, raw):
        cmd = command(raw)
        with self.mutex:
            self.sweep()
            row = self.row(cmd["runId"])
            old = self.db.execute("SELECT * FROM commands WHERE id=?", (cmd["commandId"],)).fetchone()
            if old:
                require(old["hash"] == digest(cmd), "COMMAND_ID_CONFLICT", 409)
            if row:
                require(json.loads(row["binding"]) == run_binding(cmd), "RUN_ID_CONFLICT", 409)
            if cmd["action"] == "STOP":
                require(row is not None or cmd["operatorEpoch"] == self.epoch, "STALE_EPOCH", 403)
                require(row is None or cmd["leaseGeneration"] >= row["lease"], "STALE_LEASE", 409)
                if row is None:
                    try:
                        self.insert_run(cmd, "CLEANING", None, None)
                    except Rejected:
                        self.driver.stop(cmd["runId"])
                        raise
                with self.transaction():
                    self.db.execute("UPDATE runs SET lease=?,status=CASE WHEN status='CLEANED' THEN status ELSE 'CLEANING' END WHERE id=?",
                                    (cmd["leaseGeneration"], cmd["runId"]))
                confirmed = self.row(cmd["runId"])["status"] == "CLEANED" or self.cleaned(cmd["runId"])
                # Cleanup is safe even when the finite command ledger cannot accept another identity.
                with self.transaction():
                    self.record(cmd, old)
                return self.save(cmd, result(cmd, "CLEANED" if confirmed else "PENDING", self.row(cmd["runId"])["ever"]))
            require(cmd["operatorEpoch"] == self.epoch, "STALE_EPOCH", 403)
            require(instant(cmd["expiresAt"]) > self.clock(), "LEASE_EXPIRED", 410)
            if row:
                require(cmd["leaseGeneration"] == row["lease"] and row["status"] == "RUNNING", "LEASE_CLOSED", 409)
            require(self.recovery_complete, "RECOVERY_UNCONFIRMED", 503)
            entry = self.inventory.resolve(cmd, self.clock())
            if cmd["action"] == "START":
                start_hash = digest({"scenario": cmd["scenarioId"], "inputs": cmd["inputs"]})
                if row:
                    require(start_hash == row["start_hash"], "RUN_ID_CONFLICT", 409)
                    if not self.driver.healthy(cmd):
                        return self.fail(cmd, old)
                    with self.transaction():
                        self.record(cmd, old)
                    return self.save(cmd, result(cmd, "RUNNING", True))
                require(cmd["leaseGeneration"] == 1, "INVALID_INITIAL_LEASE", 409)
                require(0 < instant(cmd["expiresAt"]) - self.clock() <= 300, "LEASE_TOO_LONG", 400)
                occupied = {r[0] for r in self.db.execute("SELECT slot FROM runs WHERE status<>'CLEANED'")}
                slot = next((i for i in range(self.max_runs) if i not in occupied), None)
                require(slot is not None, "CAPACITY_RESERVED", 429)
                self.insert_run(cmd, "STARTING", slot, start_hash)
                with self.transaction():
                    self.record(cmd, old)
                try:
                    require(self.driver.start(entry, cmd, slot), "GUEST_NOT_READY", 502)
                    require(instant(cmd["expiresAt"]) > self.clock(), "LEASE_EXPIRED", 410)
                    with self.transaction():
                        self.db.execute("UPDATE runs SET status='RUNNING',ever=1 WHERE id=?", (cmd["runId"],))
                    return self.save(cmd, result(cmd, "RUNNING", True))
                except Exception:
                    return self.fail(cmd, old)
            require(row is not None, "RUN_NOT_FOUND", 404)
            with self.transaction():
                self.record(cmd, old)
            if old:
                if old["response"] and self.driver.healthy(cmd):
                    return json.loads(old["response"])
                # Committed intent without result may have executed. Never replay a side effect.
                return self.fail(cmd, old)
            try:
                require(self.driver.healthy(cmd), "ISOLATION_UNCONFIRMED", 502)
                output, summary = self.driver.request(cmd)
                require(instant(cmd["expiresAt"]) > self.clock() and self.driver.healthy(cmd), "LEASE_EXPIRED", 410)
                return self.save(cmd, result(cmd, "RESULT", row["ever"], output, summary))
            except Exception:
                return self.fail(cmd, old)

    def insert_run(self, cmd, status, slot, start_hash):
        require(self.db.execute("SELECT count(*) FROM runs").fetchone()[0] < self.max_records,
                "IDENTITY_LEDGER_FULL", 503)
        with self.transaction():
            self.db.execute("INSERT INTO runs VALUES(?,?,?,?,?,?,0,?,?,?)", (cmd["runId"], encode(run_binding(cmd)).decode(),
                            cmd["operatorEpoch"], cmd["leaseGeneration"], instant(cmd["expiresAt"]), status, slot, start_hash, self.clock()))

    def record(self, cmd, old):
        existing = self.db.execute("SELECT hash FROM commands WHERE id=?", (cmd["commandId"],)).fetchone()
        if existing:
            require(existing[0] == digest(cmd), "COMMAND_ID_CONFLICT", 409)
        else:
            require(self.db.execute("SELECT count(*) FROM commands").fetchone()[0] < self.max_records * 20,
                    "IDENTITY_LEDGER_FULL", 503)
            self.db.execute("INSERT INTO commands VALUES(?,?,?,?,NULL,?)", (cmd["commandId"], cmd["runId"], digest(cmd), cmd["action"], self.clock()))

    def fail(self, cmd, old):
        with self.transaction():
            self.record(cmd, old)
            self.db.execute("UPDATE runs SET status='CLEANING' WHERE id=?", (cmd["runId"],))
        self.cleaned(cmd["runId"])
        return self.save(cmd, result(cmd, "FAILED", self.row(cmd["runId"])["ever"], summary="Execution closed; cleanup confirmation remains authoritative."))
