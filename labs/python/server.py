#!/usr/bin/env python3
"""Disposable local HTTP labs. Python 3.12 standard library only."""

import hashlib
import hmac
import json
import os
import re
import socket
import time
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import unquote_to_bytes


BODY_LIMIT = 16 * 1024
RESPONSE_LIMIT = 4096
SOCKET_BUDGET = 0.250
COUNTER_LIMIT = 2**53 - 1
PAYMENT_KEY = b"getlancer-payment-fixture-only"
IDENTITY_KEY = b"getlancer-identity-fixture-only"
EVENT_BODY = re.compile(r"(evt-[1-9][0-9]{0,3})\|order-1\|(payment|refund)\.succeeded\Z")
SIGNATURE = re.compile(r"[0-9a-f]{64}\Z")
BAD_ESCAPE = re.compile(r"%(?![0-9a-fA-F]{2})")
OPERATIONS = {
    "cache": {"read": set(), "expire": set(), "read-unavailable": set(),
              "read-timeout": set(), "reset": set()},
    "security": {"authorize": {"identity"}, "revoke": set(), "reset": set()},
    "payment": {"deliver": {"eventBody", "signature"}, "timeout": set(), "reset": set()},
}


class ProtocolError(Exception):
    def __init__(self, status, code, message):
        self.status, self.code, self.message = status, code, message


class RedisError(Exception):
    pass


def canonical_uuid(value):
    try:
        return str(uuid.UUID(value)) == value
    except (ValueError, AttributeError):
        return False


def configured_port(name, default):
    value = os.environ.get(name, str(default))
    if not re.fullmatch(r"[0-9]{1,5}", value) or not 1 <= int(value) <= 65535:
        raise ValueError(f"{name} must be a port from 1 to 65535")
    return int(value)


def parse_form(raw):
    try:
        text = raw.decode("utf-8", "strict")
        if not text or BAD_ESCAPE.search(text):
            raise ValueError
        fields = {}
        for pair in text.split("&"):
            if "=" not in pair:
                raise ValueError
            name, value = pair.split("=", 1)
            name, value = (
                unquote_to_bytes(part.replace("+", " ")).decode("utf-8", "strict")
                for part in (name, value)
            )
            if not name or name in fields:
                raise ValueError
            fields[name] = value
        return fields
    except (UnicodeError, ValueError):
        raise ProtocolError(400, "INVALID_FORM", "Form fields must be unique and valid UTF-8.") from None


class RedisConnection:
    """Only fixed fixture commands, with one deadline for the whole operation."""

    def __init__(self, port):
        self.deadline = time.monotonic() + SOCKET_BUDGET
        self.sock = socket.create_connection(("127.0.0.1", port), self.remaining())
        self.buffer = bytearray()

    def remaining(self):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError
        return remaining

    def close(self):
        self.sock.close()

    def receive(self):
        self.sock.settimeout(self.remaining())
        chunk = self.sock.recv(1024)
        if not chunk:
            raise RedisError
        self.buffer.extend(chunk)
        if len(self.buffer) > 2048:
            raise RedisError

    def line(self):
        while b"\r\n" not in self.buffer:
            self.receive()
        end = self.buffer.index(b"\r\n")
        result = bytes(self.buffer[:end])
        del self.buffer[:end + 2]
        return result

    def command(self, *parts):
        encoded = [str(part).encode("utf-8") for part in parts]
        wire = b"*" + str(len(encoded)).encode() + b"\r\n"
        wire += b"".join(b"$" + str(len(part)).encode() + b"\r\n" + part + b"\r\n" for part in encoded)
        self.sock.settimeout(self.remaining())
        self.sock.sendall(wire)
        line = self.line()
        try:
            if line.startswith(b"+"):
                return line[1:]
            if line.startswith(b":"):
                return int(line[1:])
            if line.startswith(b"$"):
                length = int(line[1:])
                if length == -1:
                    return None
                if not 0 <= length <= 1024:
                    raise RedisError
                while len(self.buffer) < length + 2:
                    self.receive()
                if self.buffer[length:length + 2] != b"\r\n":
                    raise RedisError
                result = bytes(self.buffer[:length])
                del self.buffer[:length + 2]
                return result
        except ValueError:
            pass
        raise RedisError


class Lab:
    def __init__(self):
        self.run_id = os.environ.get("LAB_RUN_ID", str(uuid.uuid4()))
        if not canonical_uuid(self.run_id):
            raise ValueError("LAB_RUN_ID must be a canonical UUID")
        self.source_hash = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
        self.redis_port = configured_port("LAB_REDIS_PORT", 6379)
        self.failure_port = configured_port("LAB_REDIS_FAILURE_PORT", 6380)
        self.timeout_port = configured_port("LAB_REDIS_TIMEOUT_PORT", 6381)
        self.key = f"getlancer:{self.run_id}:item"
        self.sequence = 0
        self.store_reads = 0
        self.current_role = "editor"
        self.payment_events = {}
        self.paid = False
        self.refunded = False

    def health(self):
        return {"mode": "Local execution", "language": "python", "runId": self.run_id,
                "sourceHash": self.source_hash}

    def validate(self, fields):
        if not {"runId", "pattern", "operationId"} <= fields.keys():
            raise ProtocolError(400, "INVALID_FIELDS", "Required operation fields are missing.")
        if not canonical_uuid(fields["runId"]):
            raise ProtocolError(400, "INVALID_RUN", "The run ID must be a canonical UUID.")
        if fields["runId"] != self.run_id:
            raise ProtocolError(403, "FOREIGN_RUN", "This request belongs to a different local run.")
        pattern, operation = fields["pattern"], fields["operationId"]
        if pattern not in OPERATIONS or operation not in OPERATIONS[pattern]:
            raise ProtocolError(400, "INVALID_OPERATION", "Choose a supported pattern and operation.")
        allowed = {"runId", "pattern", "operationId"} | OPERATIONS[pattern][operation]
        if set(fields) != allowed:
            raise ProtocolError(400, "INVALID_FIELDS", "This operation requires its exact allowed fields.")
        if pattern == "security" and operation == "authorize" and fields["identity"] not in {
            "editor", "viewer", "other-tenant", "expired", "tampered"
        }:
            raise ProtocolError(400, "INVALID_IDENTITY", "Choose a supported synthetic identity.")
        if pattern == "payment" and operation == "deliver" and not SIGNATURE.fullmatch(fields["signature"]):
            raise ProtocolError(400, "INVALID_SIGNATURE", "The signature must be 64 lowercase hexadecimal characters.")
        if self.sequence > COUNTER_LIMIT - 4 or self.store_reads >= COUNTER_LIMIT:
            raise ProtocolError(503, "RUN_LIMIT", "Restart this disposable local run.")

    def execute(self, fields):
        self.validate(fields)
        started = time.monotonic()
        request_id = str(uuid.uuid4())
        events = []

        def emit(kind, edge, summary):
            self.sequence += 1
            events.append({"runId": self.run_id, "sourceHash": self.source_hash,
                           "requestId": request_id, "sequence": self.sequence, "type": kind,
                           "edge": edge, "recordedAt": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
                           "summary": summary})

        pattern, operation = fields["pattern"], fields["operationId"]
        if pattern == "cache":
            status, state = self.cache(operation, emit)
        elif pattern == "security":
            status, state = self.security(operation, fields, emit)
        else:
            status, state = self.payment(operation, fields, emit)
        return status, {"runId": self.run_id, "sourceHash": self.source_hash,
                        "requestId": request_id, "pattern": pattern, "status": status,
                        "durationMs": int((time.monotonic() - started) * 1000),
                        "state": state, "events": events}

    def store_read(self, emit):
        self.store_reads += 1
        emit("STORE_READ", "service-store", "The synthetic store returned its fixed fixture item.")
        return "fixture-item"

    def cache(self, operation, emit):
        if operation in {"reset", "expire"}:
            connection = None
            try:
                connection = RedisConnection(self.redis_port)
                result = connection.command("DEL", self.key) if operation == "reset" else connection.command("PEXPIRE", self.key, 0)
                if type(result) is not int or result not in {0, 1}:
                    raise RedisError
            except (OSError, RedisError):
                raise ProtocolError(503, "REDIS_UNAVAILABLE", "The Redis fixture action could not complete.") from None
            finally:
                if connection:
                    connection.close()
            if operation == "reset":
                self.store_reads = 0
                emit("CACHE_RESET", "service-redis", "Redis cleared only the current run's fixture key.")
                cache_state = "RESET"
            else:
                emit("CACHE_EXPIRED", "service-redis", "Redis completed expiry of the current run's fixture key.")
                cache_state = "EXPIRED"
            return 200, {"cache": cache_state, "storeReads": self.store_reads, "ttlMs": 0}

        port = {"read-unavailable": self.failure_port, "read-timeout": self.timeout_port}.get(operation, self.redis_port)
        connection, read_store = None, False
        try:
            connection = RedisConnection(port)
            value = connection.command("GET", self.key)
            if value is None:
                emit("CACHE_MISS", "service-redis", "Redis missed the current run's fixture key.")
                value = self.store_read(emit)
                read_store = True
                if connection.command("SET", self.key, value, "PX", 5000) != b"OK":
                    raise RedisError
                cache_state = "MISS"
            elif value == b"fixture-item":
                cache_state = "HIT"
            else:
                raise RedisError
            ttl = connection.command("PTTL", self.key)
            if type(ttl) is not int or not -2 <= ttl <= 5000:
                raise RedisError
            if cache_state == "HIT":
                emit("CACHE_HIT", "service-redis", "Redis returned the current run's cached fixture item.")
            return 200, {"cache": cache_state, "storeReads": self.store_reads, "ttlMs": max(0, ttl)}
        except (OSError, RedisError):
            emit("CACHE_FALLBACK", "service-redis", "The bounded Redis action failed; cache fallback was selected.")
            if not read_store:
                self.store_read(emit)
            return 200, {"cache": "FALLBACK", "storeReads": self.store_reads, "ttlMs": 0}
        finally:
            if connection:
                connection.close()

    def security(self, operation, fields, emit):
        if operation == "reset":
            self.current_role = "editor"
            emit("AUTH_RESET", "policy-store", "The current run's synthetic editor policy was restored.")
            return 200, {"decision": "RESET", "reason": "RESET", "currentRole": self.current_role}
        if operation == "revoke":
            self.current_role = "viewer"
            emit("AUTH_REVOKED", "policy-store", "The current role policy revoked editor authority.")
            return 200, {"decision": "DENY", "reason": "REVOKED", "currentRole": self.current_role}

        identity = fields["identity"]
        claims = {"tenant": "other" if identity == "other-tenant" else "fixture",
                  "role": "viewer" if identity == "viewer" else "editor",
                  "expires": int(time.time()) + (-60 if identity == "expired" else 300)}
        encoded = json.dumps(claims, sort_keys=True, separators=(",", ":")).encode("utf-8")
        signature = hmac.digest(IDENTITY_KEY, encoded, "sha256")
        if identity == "tampered":
            signature = bytes([signature[0] ^ 1]) + signature[1:]
        verified = hmac.compare_digest(signature, hmac.digest(IDENTITY_KEY, encoded, "sha256"))
        # Read current policy on every authorization, including a formerly signed editor.
        current_role = self.current_role
        if not verified:
            reason, status, edge = "SIGNATURE", 401, "service-policy"
        elif claims["expires"] <= int(time.time()):
            reason, status, edge = "EXPIRED", 401, "service-policy"
        elif claims["tenant"] != "fixture":
            reason, status, edge = "TENANT", 403, "service-policy"
        elif claims["role"] != "editor":
            reason, status, edge = "ROLE", 403, "policy-store"
        elif current_role != "editor":
            reason, status, edge = "REVOKED", 403, "policy-store"
        else:
            reason, status, edge = "ALLOW", 200, "policy-store"
        decision = "ALLOW" if status == 200 else "DENY"
        summaries = {"ALLOW": "The verified synthetic editor has current authority.",
                     "SIGNATURE": "The synthetic identity signature did not verify.",
                     "EXPIRED": "The synthetic identity expired.",
                     "TENANT": "The synthetic identity belongs to another tenant.",
                     "ROLE": "The synthetic identity lacks the required editor role.",
                     "REVOKED": "The current role policy has revoked editor authority."}
        emit("AUTH_ALLOWED" if decision == "ALLOW" else "AUTH_DENIED", edge, summaries[reason])
        return status, {"decision": decision, "reason": reason, "currentRole": current_role}

    def payment_state(self):
        state = "REFUNDED" if self.paid and self.refunded else "PAID" if self.paid else "PENDING"
        return {"payment": state, "entitlements": int(self.paid and not self.refunded),
                "processedEvents": len(self.payment_events)}

    def payment(self, operation, fields, emit):
        if operation == "reset":
            self.payment_events.clear()
            self.paid, self.refunded = False, False
            emit("PAYMENT_RESET", "emulator-ledger", "The current run's synthetic payment ledger was cleared.")
            return 200, self.payment_state()
        if operation == "timeout":
            # A bounded synthetic emulator deadline, with no ledger mutation.
            time.sleep(0.025)
            emit("PAYMENT_PENDING", "service-emulator", "The emulator timed out; no new ledger effect was applied.")
            state = self.payment_state()
            return (202 if state["payment"] == "PENDING" else 200), state

        body = fields["eventBody"]
        expected = hmac.digest(PAYMENT_KEY, body.encode("utf-8"), "sha256").hex()
        if not hmac.compare_digest(expected, fields["signature"]):
            emit("PAYMENT_SIGNATURE_DENIED", "service-emulator", "The exact event body signature did not verify.")
            return 401, self.payment_state()
        match = EVENT_BODY.fullmatch(body)
        if not match:
            raise ProtocolError(400, "INVALID_EVENT", "The signed event must use the fixed fixture grammar.")
        event_id, event_kind = match.groups()
        if event_id in self.payment_events:
            if self.payment_events[event_id] != body:
                emit("PAYMENT_CONFLICT", "emulator-ledger", "The event identifier already belongs to a different verified body.")
                return 409, self.payment_state()
            emit("PAYMENT_DUPLICATE", "emulator-ledger", "The verified event was already applied; no additional effect occurred.")
            return 200, self.payment_state()
        if len(self.payment_events) >= 100:
            raise ProtocolError(409, "EVENT_LIMIT", "The current run has reached its 100 event limit.")
        self.payment_events[event_id] = body
        if event_kind == "payment":
            self.paid = True
            emit("PAYMENT_APPLIED", "emulator-ledger", "The verified payment was recorded once in the synthetic ledger.")
            if self.refunded:
                emit("REFUND_APPLIED", "emulator-ledger", "The recorded refund reconciled without granting an entitlement.")
        else:
            self.refunded = True
            if self.paid:
                emit("REFUND_APPLIED", "emulator-ledger", "The verified refund was recorded; the synthetic entitlement is absent.")
            else:
                emit("REFUND_PENDING", "emulator-ledger", "The verified refund awaits payment; no entitlement was granted.")
        return (202 if not self.paid else 200), self.payment_state()


class Handler(BaseHTTPRequestHandler):
    server_version = "LocalLab"
    sys_version = ""

    def setup(self):
        super().setup()
        self.connection.settimeout(1.0)

    def log_message(self, format, *args):
        pass  # Never log request bodies, query values, or arbitrary input.

    def send_json(self, status, value):
        payload = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        if len(payload) > RESPONSE_LIMIT:
            status = 500
            payload = b'{"error":{"code":"RESPONSE_LIMIT","message":"The bounded response could not be produced."}}'
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        self.wfile.write(payload)

    def deny(self, error):
        self.send_json(error.status, {"error": {"code": error.code, "message": error.message}})

    def send_error(self, code, message=None, explain=None):
        self.deny(ProtocolError(405 if code == 501 else code, "INVALID_HTTP", "The HTTP request is not supported."))

    def check_path(self, expected):
        # BaseHTTPRequestHandler normalizes leading //; retain exact targets.
        target = self.requestline.split()[1]
        if "?" in target:
            raise ProtocolError(400, "QUERY_NOT_ALLOWED", "Query strings are not accepted.")
        if target != expected:
            if target in {"/health", "/request"}:
                raise ProtocolError(405, "METHOD_NOT_ALLOWED", "The method is not supported for this path.")
            raise ProtocolError(404, "NOT_FOUND", "Choose a supported local lab path.")

    def do_GET(self):
        try:
            self.check_path("/health")
            if self.headers.get_all("Transfer-Encoding") or self.headers.get_all("Content-Length") not in (None, ["0"]):
                raise ProtocolError(400, "INVALID_BODY", "Health requests must not include a body.")
            self.send_json(200, self.server.lab.health())
        except ProtocolError as error:
            self.deny(error)

    def do_POST(self):
        try:
            self.check_path("/request")
            if self.headers.get_all("Content-Type") != ["application/x-www-form-urlencoded"]:
                raise ProtocolError(415, "CONTENT_TYPE", "Use application/x-www-form-urlencoded.")
            lengths = self.headers.get_all("Content-Length")
            if self.headers.get_all("Transfer-Encoding") or lengths is None or len(lengths) != 1 or not re.fullmatch(r"[0-9]{1,8}", lengths[0]):
                raise ProtocolError(400, "INVALID_LENGTH", "One valid content length is required.")
            length = int(lengths[0])
            if length > BODY_LIMIT:
                raise ProtocolError(413, "BODY_LIMIT", "Requests are limited to 16 KiB.")
            self.connection.settimeout(1.0)
            try:
                raw = self.rfile.read(length)
            except OSError:
                raise ProtocolError(400, "INCOMPLETE_BODY", "The request body did not complete.") from None
            if len(raw) != length:
                raise ProtocolError(400, "INCOMPLETE_BODY", "The request body did not complete.")
            status, response = self.server.lab.execute(parse_form(raw))
            self.send_json(status, response)
        except ProtocolError as error:
            self.deny(error)


def main():
    lab = Lab()
    server = HTTPServer(("127.0.0.1", configured_port("LAB_PORT", 8100)), Handler)
    server.lab = lab
    print(json.dumps(lab.health(), separators=(",", ":")), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
