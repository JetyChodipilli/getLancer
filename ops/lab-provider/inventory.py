"""Operator filesystem authority is independent of application manifests and credentials."""
import hashlib
import os
from pathlib import Path
import stat

from protocol import HASH, SLUG, decode, instant, require


def trusted_path(path, directory=False):
    """Reject links and writable/root-unowned ancestors, including the final inode."""
    path = Path(path)
    require(path.is_absolute() and ".." not in path.parts, "UNTRUSTED_PATH", 503)
    for part in [*reversed(path.parents), path]:
        info = part.lstat()
        require(not stat.S_ISLNK(info.st_mode) and info.st_uid == 0 and not info.st_mode & 0o022,
                "UNTRUSTED_PATH", 503)
    info = path.lstat()
    require(stat.S_ISDIR(info.st_mode) if directory else stat.S_ISREG(info.st_mode), "UNTRUSTED_PATH", 503)
    return path


def read_trusted(path, maximum=1048576):
    path = trusted_path(path)
    with os.fdopen(os.open(path, os.O_RDONLY | os.O_NOFOLLOW), "rb") as stream:
        info = os.fstat(stream.fileno())
        require(stat.S_ISREG(info.st_mode) and info.st_uid == 0 and not info.st_mode & 0o022,
                "UNTRUSTED_PATH", 503)
        raw = stream.read(maximum + 1)
    require(len(raw) <= maximum, "CONFIG_TOO_LARGE", 503)
    return raw


def asset(path, expected):
    require(isinstance(expected, str) and HASH.fullmatch(expected), "INVALID_INVENTORY", 503)
    path = trusted_path(path)
    with os.fdopen(os.open(path, os.O_RDONLY | os.O_NOFOLLOW), "rb") as stream:
        info = os.fstat(stream.fileno())
        require(stat.S_ISREG(info.st_mode) and info.st_uid == 0 and not info.st_mode & 0o022,
                "UNTRUSTED_ASSET", 503)
        require(not info.st_mode & 0o222, "MUTABLE_ASSET", 503)
        observed = hashlib.file_digest(stream, "sha256").hexdigest()
    require(observed == expected, "ASSET_DIGEST_MISMATCH", 503)
    return str(path)


class Inventory:
    def __init__(self, value):
        require(isinstance(value, dict) and value.keys() == {"version", "entries"} and value["version"] == 1,
                "INVALID_INVENTORY", 503)
        require(isinstance(value["entries"], list) and len(value["entries"]) <= 100, "INVALID_INVENTORY", 503)
        self.entries = {}
        for entry in value["entries"]:
            require(isinstance(entry, dict) and entry.keys() == {"manifestSha256", "imageDigest", "sourceSha256",
                    "archiveSha256", "kernel", "kernelSha256", "rootfs", "rootfsSha256", "archive", "memoryMiB",
                    "approvedUntil", "scenarios"}, "INVALID_INVENTORY", 503)
            for key in ("manifestSha256", "sourceSha256", "archiveSha256", "kernelSha256", "rootfsSha256"):
                require(isinstance(entry[key], str) and HASH.fullmatch(entry[key]), "INVALID_INVENTORY", 503)
            require(entry["imageDigest"] == "sha256:" + entry["rootfsSha256"] and
                    type(entry["memoryMiB"]) is int and 64 <= entry["memoryMiB"] <= 512,
                    "INVALID_INVENTORY", 503)
            instant(entry["approvedUntil"])
            require(entry["manifestSha256"] not in self.entries, "INVALID_INVENTORY", 503)
            require(isinstance(entry["scenarios"], dict) and 1 <= len(entry["scenarios"]) <= 20,
                    "INVALID_INVENTORY", 503)
            for name, scenario in entry["scenarios"].items():
                require(SLUG.fullmatch(name) and scenario.keys() == {"operations", "inputs"}, "INVALID_INVENTORY", 503)
                require(isinstance(scenario["operations"], list) and 1 <= len(scenario["operations"]) <= 10 and
                        len(set(scenario["operations"])) == len(scenario["operations"]) and
                        all(isinstance(op, str) and SLUG.fullmatch(op) for op in scenario["operations"]),
                        "INVALID_INVENTORY", 503)
                require(isinstance(scenario["inputs"], dict) and len(scenario["inputs"]) <= 20, "INVALID_INVENTORY", 503)
                for definition in scenario["inputs"].values():
                    require(isinstance(definition, dict) and definition.keys() == {"type", "required", "maxLength", "min", "max", "choices"}
                            and definition["type"] in ("TEXT", "INTEGER", "BOOLEAN", "ENUM")
                            and type(definition["required"]) is bool and type(definition["maxLength"]) is int
                            and 1 <= definition["maxLength"] <= 4096 and type(definition["min"]) is int
                            and type(definition["max"]) is int and definition["min"] <= definition["max"]
                            and isinstance(definition["choices"], list) and len(definition["choices"]) <= 30
                            and all(isinstance(c, str) for c in definition["choices"]), "INVALID_INVENTORY", 503)
            self.entries[entry["manifestSha256"]] = entry

    @classmethod
    def load(cls, path):
        return cls(decode(read_trusted(path), 1048576))

    def resolve(self, cmd, now):
        entry = self.entries.get(cmd["manifestSha256"])
        require(entry is not None and entry["imageDigest"] == cmd["imageDigest"] and
                entry["memoryMiB"] == cmd["memoryMiB"] and instant(entry["approvedUntil"]) > now,
                "IMAGE_NOT_ADMITTED", 403)
        scenario = entry["scenarios"].get(cmd["scenarioId"])
        require(scenario is not None, "SCENARIO_NOT_ADMITTED", 403)
        if cmd["action"] == "REQUEST":
            require(cmd["operationId"] in scenario["operations"], "OPERATION_NOT_ADMITTED", 403)
        require(cmd["inputs"].keys() <= scenario["inputs"].keys(), "INPUT_NOT_ADMITTED", 403)
        from protocol import text
        for name, definition in scenario["inputs"].items():
            value = cmd["inputs"].get(name, "")
            require(value.strip() or not definition["required"], "INPUT_REQUIRED", 400)
            if not value.strip():
                continue
            require(text(value, definition["maxLength"]), "INVALID_INPUT", 400)
            kind = definition["type"]
            if kind == "INTEGER":
                import re
                require(re.fullmatch(r"-?[0-9]{1,18}", value) and definition["min"] <= int(value) <= definition["max"],
                        "INVALID_INPUT", 400)
            elif kind == "BOOLEAN":
                require(value in ("true", "false"), "INVALID_INPUT", 400)
            elif kind == "ENUM":
                require(value in definition["choices"], "INVALID_INPUT", 400)
        for path, sha in (("kernel", "kernelSha256"), ("rootfs", "rootfsSha256"), ("archive", "archiveSha256")):
            asset(entry[path], entry[sha])
        return entry
