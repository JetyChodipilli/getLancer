"""Small bounded transports. All errors omit payloads, paths, credentials and guest output."""
import hmac
import http.server
import os
import socket
import socketserver
import struct
import threading

from protocol import MAX_COMMAND, MAX_RESULT, Rejected, command, decode, encode, require


def receive(connection, length):
    data = bytearray()
    while len(data) < length:
        part = connection.recv(length - len(data))
        require(part, "TRUNCATED_BODY", 400)
        data.extend(part)
    return bytes(data)


def read_frame(connection, maximum):
    length = struct.unpack("!I", receive(connection, 4))[0]
    require(0 < length <= maximum, "BODY_TOO_LARGE", 413)
    return receive(connection, length)


def write_frame(connection, data):
    connection.sendall(struct.pack("!I", len(data)) + data)


def deadline(connection, seconds=8):
    def close():
        try:
            connection.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
    timer = threading.Timer(seconds, close)
    timer.daemon = True
    timer.start()
    connection.settimeout(seconds)
    return timer


class RunnerSocket(socketserver.UnixStreamServer):
    request_queue_size = 16
    def __init__(self, path, runner, gateway_uid):
        self.runner, self.gateway_uid = runner, gateway_uid
        super().__init__(path, RunnerHandler)
        os.chown(path, gateway_uid, -1)
        os.chmod(path, 0o600)


class RunnerHandler(socketserver.BaseRequestHandler):
    def handle(self):
        timer = deadline(self.request)
        try:
            _, uid, _ = struct.unpack("3i", self.request.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))
            require(uid == self.server.gateway_uid, "RUNNER_PEER_DENIED", 403)
            raw = read_frame(self.request, MAX_COMMAND)
            response = self.server.runner.execute(raw)
            body = {"status": 200, "body": response}
        except Rejected as error:
            body = {"status": error.status, "body": {"error": error.code}}
        except Exception:
            body = {"status": 503, "body": {"error": "RUNNER_UNCONFIRMED"}}
        try:
            write_frame(self.request, encode(body))
        except OSError:
            pass
        finally:
            timer.cancel()


def forward(path, raw, expected_uid=0):
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
        timer = deadline(connection)
        try:
            connection.connect(path)
            _, uid, _ = struct.unpack("3i", connection.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))
            require(uid == expected_uid, "RUNNER_PEER_DENIED", 503)
            write_frame(connection, raw)
            envelope = decode(read_frame(connection, MAX_RESULT + 256), MAX_RESULT + 256)
            require(isinstance(envelope, dict) and envelope.keys() == {"status", "body"}
                    and type(envelope["status"]) is int and envelope["status"] in (200, 400, 403, 404, 409, 410, 413, 429, 502, 503),
                    "RUNNER_UNCONFIRMED", 503)
            return envelope["status"], envelope["body"]
        finally:
            timer.cancel()


class Gateway(http.server.HTTPServer):
    request_queue_size = 16
    allow_reuse_address = False
    def __init__(self, address, secret, send):
        require(isinstance(secret, str) and 32 <= len(secret) <= 256 and secret.isascii()
                and all(32 < ord(c) < 127 for c in secret), "INVALID_GATEWAY_SECRET", 503)
        self.secret, self.send = secret, send
        super().__init__(address, GatewayHandler)


class GatewayHandler(http.server.BaseHTTPRequestHandler):
    server_version = "LabGateway"
    sys_version = ""
    protocol_version = "HTTP/1.1"

    def handle(self):
        timer = deadline(self.connection, 10)
        try:
            super().handle()
        except (OSError, ValueError):
            pass
        finally:
            timer.cancel()

    def log_message(self, *_):
        pass

    def reply(self, status, body):
        payload = encode(body)
        require(len(payload) <= MAX_RESULT, "RESPONSE_TOO_LARGE", 503)
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        self.wfile.write(payload)

    def do_POST(self):
        self.close_connection = True
        try:
            require(self.path == "/v1/lab-commands", "NOT_FOUND", 404)
            require(sum(len(k) + len(v) + 4 for k, v in self.headers.items()) <= 8192, "HEADERS_TOO_LARGE", 400)
            auth = self.headers.get_all("Authorization", [])
            require(len(auth) == 1 and hmac.compare_digest(auth[0].encode("utf-8"),
                    ("Bearer " + self.server.secret).encode("ascii")), "UNAUTHORIZED", 403)
            require(not self.headers.get_all("Transfer-Encoding") and not self.headers.get_all("Expect"), "INVALID_FRAMING", 400)
            length = self.headers.get_all("Content-Length", [])
            require(len(length) == 1 and length[0].isascii() and length[0].isdigit()
                    and len(length[0]) <= 6 and 0 < int(length[0]) <= MAX_COMMAND, "INVALID_LENGTH", 413)
            require(self.headers.get_all("Content-Type", []) == ["application/json"], "INVALID_CONTENT_TYPE", 400)
            raw = self.rfile.read(int(length[0]))
            require(len(raw) == int(length[0]), "TRUNCATED_BODY", 400)
            command(raw)  # Cheap closed-schema validation before the privileged boundary.
            status, body = self.server.send(raw)
            self.reply(status, body)
        except Rejected as error:
            self.reply(error.status, {"error": error.code})
        except Exception:
            self.reply(503, {"error": "PROVIDER_UNCONFIRMED"})

    def handle_expect_100(self):
        self.reply(400, {"error": "INVALID_FRAMING"})
        return False

    def send_error(self, code, message=None, explain=None):
        self.reply(code, {"error": "HTTP_REQUEST_REJECTED"})
