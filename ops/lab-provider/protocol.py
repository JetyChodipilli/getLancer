"""Closed getlancer-lab-v1 wire format. No URLs, paths, shell text or uploaded code."""
import datetime as dt
import hashlib
import json
import re
import uuid

MAX_COMMAND = 65536
MAX_RESULT = 16384
HASH = re.compile(r"[a-f0-9]{64}\Z")
SLUG = re.compile(r"[a-z0-9][a-z0-9-]{0,59}\Z")
FIELDS = frozenset(("runId", "leaseGeneration", "operatorEpoch", "commandId", "manifestSha256",
                    "imageDigest", "expiresAt", "action", "scenarioId", "operationId", "inputs", "memoryMiB"))


class Rejected(Exception):
    def __init__(self, code="INVALID_COMMAND", status=400):
        super().__init__(code)
        self.code, self.status = code, status


def require(condition, code="INVALID_COMMAND", status=400):
    if not condition:
        raise Rejected(code, status)


def encode(value):
    return json.dumps(value, separators=(",", ":"), sort_keys=True, ensure_ascii=True).encode()


def decode(raw, maximum=MAX_COMMAND):
    require(isinstance(raw, bytes) and len(raw) <= maximum)
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result)
            result[key] = value
        return result
    try:
        return json.loads(raw.decode("utf-8"), object_pairs_hook=pairs,
                          parse_constant=lambda _: (_ for _ in ()).throw(Rejected()))
    except (ValueError, UnicodeError, RecursionError):
        raise Rejected() from None


def identity(value):
    try:
        require(isinstance(value, str) and str(uuid.UUID(value)) == value)
    except (ValueError, AttributeError):
        raise Rejected() from None
    return value


def instant(value):
    require(isinstance(value, str) and re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?Z", value))
    try:
        return dt.datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
    except ValueError:
        raise Rejected() from None


def text(value, maximum):
    return (isinstance(value, str) and len(value.encode("utf-16-le", errors="surrogatepass")) // 2 <= maximum
            and all(ord(c) >= 32 or c in "\n\t" for c in value) and "\x7f" not in value
            and not any(0xd800 <= ord(c) <= 0xdfff for c in value))


def command(raw):
    value = decode(raw)
    require(isinstance(value, dict) and value.keys() == FIELDS)
    for name in ("runId", "operatorEpoch", "commandId"):
        identity(value[name])
    require(type(value["leaseGeneration"]) is int and 1 <= value["leaseGeneration"] <= 2**63 - 1)
    require(isinstance(value["manifestSha256"], str) and HASH.fullmatch(value["manifestSha256"]))
    require(isinstance(value["imageDigest"], str) and re.fullmatch(r"sha256:[a-f0-9]{64}", value["imageDigest"]))
    instant(value["expiresAt"])
    require(value["action"] in ("START", "REQUEST", "STOP"))
    require(isinstance(value["scenarioId"], str) and SLUG.fullmatch(value["scenarioId"]))
    require(value["operationId"] is None if value["action"] != "REQUEST" else
            isinstance(value["operationId"], str) and SLUG.fullmatch(value["operationId"]))
    require(type(value["memoryMiB"]) is int and 64 <= value["memoryMiB"] <= 512)
    require(isinstance(value["inputs"], dict) and len(value["inputs"]) <= 20)
    require(all(isinstance(k, str) and re.fullmatch(r"[a-z][a-zA-Z0-9]{0,39}", k) and text(v, 4096)
                for k, v in value["inputs"].items()))
    require(value["action"] != "STOP" or not value["inputs"])
    return value


def digest(value):
    return hashlib.sha256(encode(value)).hexdigest()


def run_binding(value):
    return {key: value[key] for key in ("runId", "operatorEpoch", "manifestSha256", "imageDigest",
                                      "expiresAt", "scenarioId", "memoryMiB")}


def result(cmd, state, ever=False, output="", summary=""):
    healthy = state in ("RUNNING", "RESULT")
    require(text(output, 4096) and text(summary, 500), "INVALID_GUEST_RESPONSE", 502)
    value = {key: cmd[key] for key in ("runId", "leaseGeneration", "operatorEpoch", "commandId",
                                       "manifestSha256", "imageDigest", "expiresAt")}
    value.update(state=state, healthy=healthy, isolated=healthy, everHealthy=bool(ever), output=output, summary=summary)
    require(len(encode(value)) <= MAX_RESULT, "INVALID_GUEST_RESPONSE", 502)
    return value
