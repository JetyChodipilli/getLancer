#!/usr/bin/env python3
"""Original fixed synthetic analytics/inference. No user-selected loaders or network."""
import hashlib
import hmac
import json
import math
import os
import re
import socket
import sys
import time
import uuid
from datetime import datetime, timezone
from decimal import Decimal
from http.client import HTTPMessage
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import unquote_to_bytes

BASE = Path(__file__).resolve().parent
REQUEST_LIMIT = 8192
RESPONSE_LIMIT = 4096
MAX_ATTEMPTS = 100
INVENTORY = sorted(['runtime.py', 'catalogue.json', 'README.md', 'License.md', 'DATA_LICENSE.md',
    'MODEL_LICENSE.md', 'fixtures/revenue.json', 'fixtures/sensors.json',
    'fixtures/equipment-inference-inputs.json', 'fixtures/sentiment-inference-inputs.json',
    'fixtures/equipment-inference-evaluation.json', 'fixtures/sentiment-inference-evaluation.json',
    'models/equipment-inference.json', 'models/sentiment-inference.json'])
SCENARIOS = ('revenue-summary', 'sensor-quality', 'sentiment-inference', 'equipment-inference')
FIELDS = {'revenue-summary': {'region', 'upliftPercent'}, 'sensor-quality': {'channel', 'tolerance'},
          'sentiment-inference': {'text'}, 'equipment-inference': {'temperature', 'vibration'}}
BASE_FIELDS = {'runId', 'scenarioId', 'operationId'}
SHA = re.compile(r'[0-9a-f]{64}\Z')
BAD_ESCAPE = re.compile(r'%(?![0-9a-fA-F]{2})')


class ProtocolError(Exception):
    def __init__(self, status, code, message):
        self.status, self.code, self.message = status, code, message


def canonical(value):
    """Sorted compact UTF-8; bounded six-decimal values agree with JS JSON.stringify."""
    def encode(item):
        if isinstance(item, float):
            if not math.isfinite(item):
                raise ValueError('Non-finite output')
            item = round(item, 6)
            if item.is_integer():
                return str(int(item))
            return format(Decimal(str(item)), 'f').rstrip('0').rstrip('.')
        if isinstance(item, list):
            return '[' + ','.join(encode(v) for v in item) + ']'
        if isinstance(item, dict):
            return '{' + ','.join(json.dumps(k, ensure_ascii=False) + ':' + encode(item[k])
                                   for k in sorted(item)) + '}'
        return json.dumps(item, ensure_ascii=False, allow_nan=False, separators=(',', ':'))
    return encode(value).encode('utf-8')


def sha256(value):
    return hashlib.sha256(value).hexdigest()


def timestamp():
    return datetime.now(timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z')


def canonical_uuid(value):
    try:
        return str(uuid.UUID(value)) == value
    except (ValueError, AttributeError, TypeError):
        return False


def configured_integer(name, default, low, high):
    value = os.environ.get(name, str(default))
    if not re.fullmatch(r'0|[1-9][0-9]{0,5}', value) or not low <= int(value) <= high:
        raise ValueError('Invalid local configuration')
    return int(value)


def apply_limits():
    try:
        import resource
    except ImportError:
        return False
    for limit, maximum in [(resource.RLIMIT_AS, 256 * 1024 * 1024),
                           (resource.RLIMIT_CPU, 10), (resource.RLIMIT_NOFILE, 64),
                           (resource.RLIMIT_CORE, 0), (resource.RLIMIT_FSIZE, 512 * 1024)]:
        _, hard = resource.getrlimit(limit)
        value = maximum if hard == resource.RLIM_INFINITY else min(maximum, hard)
        resource.setrlimit(limit, (value, value))
    return True


def require(condition):
    if not condition:
        raise ValueError('Pinned fixture or model contract rejected')


def number(value, low, high):
    return type(value) in (int, float) and math.isfinite(value) and low <= value <= high


def read_json(path, pinned=None):
    require(path in INVENTORY and not path.startswith('/') and '..' not in path.split('/'))
    target = BASE / path
    require(not target.is_symlink() and target.is_file() and target.stat().st_size <= 32768)
    require(not any(parent.is_symlink() for parent in target.parents if parent != BASE and BASE in parent.parents))
    data = target.read_bytes()
    if pinned:
        require(type(pinned) is str and bool(SHA.fullmatch(pinned)) and hmac.compare_digest(sha256(data), pinned))
    def pairs(values):
        result = {}
        for key, value in values:
            require(key not in result)
            result[key] = value
        return result
    return json.loads(data.decode('utf-8'), object_pairs_hook=pairs,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Invalid JSON number')))


def parse_form(raw):
    try:
        text = raw.decode('utf-8', 'strict')
        if not text or BAD_ESCAPE.search(text):
            raise ValueError
        fields = {}
        for pair in text.split('&'):
            if '=' not in pair:
                raise ValueError
            name, value = (unquote_to_bytes(p.replace('+', ' ')).decode('utf-8', 'strict')
                           for p in pair.split('=', 1))
            if not name or name in fields:
                raise ValueError
            fields[name] = value
        return fields
    except (UnicodeError, ValueError):
        raise ProtocolError(400, 'INVALID_FORM', 'Form fields must be unique valid UTF-8 values.') from None


class Runtime:
    def __init__(self, run_id=None, os_limits=False):
        self.run_id = run_id or os.environ.get('LAB_RUN_ID')
        require(canonical_uuid(self.run_id))
        self.source_sha256 = sha256(Path(__file__).read_bytes())
        self.lifetime = configured_integer('LAB_LIFETIME_SECONDS', 300, 1, 300)
        self.idle = configured_integer('LAB_IDLE_SECONDS', 90, 1, 90)
        self.os_limits = os_limits
        self.started = self.last_activity = time.monotonic()
        self.attempts = self.executions = 0
        self.closed = False
        catalogue = read_json('catalogue.json')
        require(set(catalogue) == {'schemaVersion', 'inventory', 'items', 'assets', 'contracts'}
                and catalogue['schemaVersion'] == 'getlancer-data-ai-catalogue-v1'
                and catalogue['inventory'] == INVENTORY and len(catalogue['items']) == 4
                and set(catalogue['assets']) == set(SCENARIOS) and set(catalogue['contracts']) == set(SCENARIOS))
        self.items, self.assets, self.contracts = {}, catalogue['assets'], catalogue['contracts']
        self.data, self.models, self.evaluations = {}, {}, {}
        for item in catalogue['items']:
            ident = item['id']
            require(ident in SCENARIOS and ident not in self.items)
            analytic = ident in SCENARIOS[:2]
            require(item['kind'] == ('DATA_ANALYTICS' if analytic else 'AI_ML')
                    and item['outputSchema'] == 'getlancer-data-ai-result-v1'
                    and set(item['defaultInputs']) == FIELDS[ident] and set(item['changedInputs']) == FIELDS[ident])
            asset = self.assets[ident]
            data_path = 'fixtures/' + {'revenue-summary': 'revenue.json', 'sensor-quality': 'sensors.json'}.get(ident, ident + '-inputs.json')
            require(asset == {'dataPath': data_path, 'modelPath': None if analytic else 'models/' + ident + '.json',
                             'evaluationPath': None if analytic else 'fixtures/' + ident + '-evaluation.json'})
            self.items[ident] = item
            self.data[ident] = read_json(asset['dataPath'], item['dataSha256'])
            if not analytic:
                require(item['modelFormat'] == 'JSON' and item['evaluationSplit'] == 'held-out-synthetic'
                        and item['evaluationProtocol'] == 'frozen-model-no-training')
                self.models[ident] = read_json(asset['modelPath'], item['modelSha256'])
                self.evaluations[ident] = read_json(asset['evaluationPath'], item['evaluationSha256'])
            else:
                require(item['modelSha256'] is None and item['evaluationSha256'] is None)
        require(set(self.items) == set(SCENARIOS))
        self.validate_assets()

    def validate_assets(self):
        rows = self.data['revenue-summary']
        require(type(rows) is list and len(rows) == 6 and all(type(r) is dict and set(r) == {'month', 'region', 'revenue'}
                and r['month'] in ('Jan', 'Feb', 'Mar') and r['region'] in ('north', 'south')
                and type(r['revenue']) is int and 0 <= r['revenue'] <= 1000000 for r in rows)
                and len({(r['month'], r['region']) for r in rows}) == 6)
        rows = self.data['sensor-quality']
        require(type(rows) is list and len(rows) == 8 and all(type(r) is dict and set(r) == {'channel', 'deviation'}
                and r['channel'] in ('temperature', 'vibration') and number(r['deviation'], -100, 100) for r in rows)
                and all(sum(r['channel'] == c for r in rows) == 4 for c in ('temperature', 'vibration')))
        for ident, model in self.models.items():
            classes = ['negative', 'positive'] if ident == 'sentiment-inference' else ['normal', 'attention']
            weights = {'temperature', 'vibration'} if ident == 'equipment-inference' else {
                'excellent', 'reliable', 'great', 'good', 'helpful', 'terrible', 'broken', 'bad', 'slow', 'poor'}
            require(type(model) is dict and set(model) == {'format', 'id', 'classes', 'bias', 'weights', 'provenance'}
                    and model['format'] == 'getlancer-linear-json-v1' and model['id'] == ident
                    and model['classes'] == classes and self.contracts[ident]['classes'] == classes
                    and number(model['bias'], -20, 20) and type(model['weights']) is dict
                    and set(model['weights']) == weights and all(number(v, -3, 3) for v in model['weights'].values())
                    and type(model['provenance']) is str and 1 <= len(model['provenance']) <= 200)
            fixture = self.data[ident]
            require(type(fixture) is dict and set(fixture) == {'synthetic', 'default', 'changed'} and fixture['synthetic'] is True)
            require(fixture['default'] == self.items[ident]['defaultInputs'] and fixture['changed'] == self.items[ident]['changedInputs'])
            evaluation = self.evaluations[ident]
            require(type(evaluation) is list and len(evaluation) == 8)
            seen = set()
            public = {sha256(canonical(fixture[key])) for key in ('default', 'changed')}
            for row in evaluation:
                require(type(row) is dict and row.get('label') in classes)
                values = {key: value for key, value in row.items() if key != 'label'}
                self.validate_inputs(ident, values)
                hashed = sha256(canonical(values))
                require(hashed not in seen and hashed not in public)
                seen.add(hashed)
        for ident, item in self.items.items():
            self.validate_inputs(ident, item['defaultInputs'])
            self.validate_inputs(ident, item['changedInputs'])

    def validate_inputs(self, ident, values):
        if set(values) != FIELDS[ident] or any(type(value) is not str for value in values.values()):
            raise ProtocolError(400, 'INVALID_FIELDS', 'The scenario requires its exact string input fields.')
        for key, value in values.items():
            if key in ('region', 'channel'):
                choices = ('all', 'north', 'south') if key == 'region' else ('all', 'temperature', 'vibration')
                valid = value in choices
            elif key == 'text':
                valid = bool(re.fullmatch(r'[\x20-\x7e]{1,160}', value)) and bool(re.search(r'[A-Za-z]', value))
            else:
                low, high = {'upliftPercent': (-20, 20), 'tolerance': (1, 10), 'temperature': (0, 100), 'vibration': (0, 20)}[key]
                valid = bool(re.fullmatch(r'0|-?[1-9][0-9]{0,2}', value)) and low <= int(value) <= high
            if not valid:
                raise ProtocolError(400, 'INVALID_INPUT', 'Input values must match their bounded scenario contracts.')
        return values

    def expired(self):
        now = time.monotonic()
        return self.closed or now - self.started >= self.lifetime or now - self.last_activity >= self.idle

    def begin_attempt(self):
        if self.expired():
            raise ProtocolError(410, 'EXPIRED', 'The disposable local run has expired.')
        self.attempts += 1
        if self.attempts > MAX_ATTEMPTS:
            raise ProtocolError(429, 'ATTEMPT_LIMIT', 'The local request budget is exhausted.')
        self.last_activity = time.monotonic()

    def health(self):
        return {'schemaVersion': 'getlancer-data-ai-health-v1', 'mode': 'LOCAL', 'runId': self.run_id,
                'sourceSha256': self.source_sha256, 'limits': {'requestBytes': REQUEST_LIMIT,
                'responseBytes': RESPONSE_LIMIT, 'maxAttempts': MAX_ATTEMPTS, 'lifetimeSeconds': self.lifetime,
                'idleSeconds': self.idle, 'absoluteRequestSeconds': 1, 'memoryMiB': 256,
                'cpuSeconds': 10, 'osLimits': self.os_limits}}

    def predict(self, ident, values):
        model = self.models[ident]
        if ident == 'sentiment-inference':
            tokens = re.findall(r'[a-z]+', values['text'].lower())
            score = model['bias'] + sum(model['weights'].get(token, 0) for token in tokens)
        else:
            score = model['bias'] + sum(model['weights'][key] * int(value) for key, value in values.items())
        probability = round(1 / (1 + math.exp(-max(-30, min(30, score)))), 6)
        probabilities = [round(1 - probability, 6), probability]
        chosen = 1 if probabilities[1] > probabilities[0] else 0
        return {'label': model['classes'][chosen], 'probability': probabilities[chosen]}, probabilities

    def evaluate(self, ident):
        rows = self.evaluations[ident]
        correct = sum(self.predict(ident, {k: v for k, v in row.items() if k != 'label'})[0]['label'] == row['label'] for row in rows)
        item = self.items[ident]
        return {'split': item['evaluationSplit'], 'protocol': item['evaluationProtocol'],
                'samples': len(rows), 'correct': correct, 'accuracy': round(correct / len(rows), 6),
                'limitations': item['limitations']}

    def result(self, ident, values):
        contract = self.contracts[ident]
        table = {**contract['tables'][0], 'rows': []}
        result = {'tables': [table], 'metrics': [], 'prediction': None, 'evaluation': None}
        if ident == 'revenue-summary':
            rows = [r for r in self.data[ident] if values['region'] == 'all' or values['region'] == r['region']]
            adjusted = lambda amount: (amount * (100 + int(values['upliftPercent'])) + 50) // 100
            total = adjusted(sum(r['revenue'] for r in rows))
            table['rows'] = [{'month': month, 'revenue': adjusted(sum(r['revenue'] for r in rows if r['month'] == month)),
                              'orders': sum(r['month'] == month for r in rows)} for month in ('Jan', 'Feb', 'Mar')]
            metrics = [total, len(rows), (total + len(rows) // 2) // len(rows)]
        elif ident == 'sensor-quality':
            rows = [r for r in self.data[ident] if values['channel'] == 'all' or values['channel'] == r['channel']]
            accepted = [r for r in rows if abs(r['deviation']) <= int(values['tolerance'])]
            channels = ('temperature', 'vibration') if values['channel'] == 'all' else (values['channel'],)
            table['rows'] = [{'channel': channel, 'accepted': sum(r['channel'] == channel for r in accepted),
                             'rejected': sum(r['channel'] == channel for r in rows) - sum(r['channel'] == channel for r in accepted),
                             'total': sum(r['channel'] == channel for r in rows)} for channel in channels]
            metrics = [len(accepted), len(rows) - len(accepted), round(100 * len(accepted) / len(rows), 6)]
        else:
            prediction, probabilities = self.predict(ident, values)
            result['prediction'], result['evaluation'] = prediction, self.evaluate(ident)
            table['rows'] = [{'class': label, 'probability': probabilities[i]} for i, label in enumerate(contract['classes'])]
            metrics = [prediction['probability'], result['evaluation']['accuracy']]
        result['metrics'] = [{**spec, 'value': value} for spec, value in zip(contract['metrics'], metrics)]
        return result

    def execute(self, fields):
        if self.expired():
            raise ProtocolError(410, 'EXPIRED', 'The disposable local run has expired.')
        if not BASE_FIELDS <= fields.keys() or not canonical_uuid(fields['runId']):
            raise ProtocolError(400, 'INVALID_RUN', 'Provide exact canonical run identity fields.')
        if not hmac.compare_digest(fields['runId'], self.run_id):
            raise ProtocolError(403, 'FOREIGN_RUN', 'The request belongs to another local run.')
        ident = fields['scenarioId']
        if ident not in SCENARIOS or fields['operationId'] != 'execute':
            raise ProtocolError(400, 'INVALID_OPERATION', 'Use a curated scenario and execute operation.')
        values = self.validate_inputs(ident, {k: v for k, v in fields.items() if k not in BASE_FIELDS})
        started_at, start = timestamp(), time.monotonic()
        result = self.result(ident, values)
        self.executions += 1
        elapsed_ms = int((time.monotonic() - start) * 1000)
        require(elapsed_ms <= 1000)
        item = self.items[ident]
        return {'schemaVersion': 'getlancer-data-ai-result-v1', 'mode': 'LOCAL', 'runId': self.run_id,
                'scenarioId': ident, 'sourceSha256': self.source_sha256, 'dataSha256': item['dataSha256'],
                'modelSha256': item['modelSha256'], 'evaluationSha256': item['evaluationSha256'],
                'input': values, 'inputSha256': sha256(canonical(values)), 'result': result,
                'resultSha256': sha256(canonical(result)), 'startedAt': started_at, 'elapsedMs': elapsed_ms}

    def cleanup(self):
        self.closed = True
        for state in (self.items, self.assets, self.contracts, self.data, self.models, self.evaluations):
            state.clear()


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.0'
    server_version = 'GetLancerLocal'
    sys_version = ''

    def log_message(self, *_):
        pass

    def respond(self, status, value):
        body = canonical(value)
        if len(body) > RESPONSE_LIMIT:
            status, body = 503, canonical({'error': 'OUTPUT_LIMIT', 'message': 'The bounded output budget was exceeded.'})
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Connection', 'close')
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True

    def read_bounded(self, length, line=False):
        output = bytearray()
        while len(output) < length:
            remaining = self.deadline - time.monotonic()
            if remaining <= 0:
                raise ProtocolError(408, 'REQUEST_TIMEOUT', 'The absolute request deadline expired.')
            self.connection.settimeout(remaining)
            chunk = self.rfile.read(1) if line else self.rfile.read1(length - len(output))
            if not chunk:
                break
            output.extend(chunk)
            if line and chunk == b'\n':
                break
        return bytes(output)

    def handle_one_request(self):
        runtime = self.server.runtime
        self.request_version, self.requestline, self.command, self.close_connection = 'HTTP/1.0', '', None, True
        self.deadline = min(time.monotonic() + 1, runtime.started + runtime.lifetime, runtime.last_activity + runtime.idle)
        try:
            runtime.begin_attempt()
            self.raw_requestline = self.read_bounded(1025, line=True)
            if len(self.raw_requestline) > 1024:
                raise ProtocolError(413, 'HEADER_LIMIT', 'HTTP headers exceed their byte budget.')
            match = re.fullmatch(rb'([A-Z]+) ([\x21-\x7e]+) HTTP/(1\.[01])\r\n', self.raw_requestline)
            if not match:
                raise ProtocolError(400, 'INVALID_HTTP', 'Malformed HTTP request.')
            self.command, self.path = match[1].decode('ascii'), match[2].decode('ascii')
            self.headers = HTTPMessage()
            total = len(self.raw_requestline)
            while True:
                line = self.read_bounded(1025, line=True)
                total += len(line)
                if total > 8192 or len(line) > 1024:
                    raise ProtocolError(413, 'HEADER_LIMIT', 'HTTP headers exceed their byte budget.')
                if line == b'\r\n':
                    break
                header = re.fullmatch(rb'([A-Za-z][A-Za-z0-9-]*):[ \t]*([\x20-\x7e]*)\r\n', line)
                if not header:
                    raise ProtocolError(400, 'INVALID_HEADERS', 'HTTP headers must be unambiguous.')
                self.headers.add_header(header[1].decode('ascii'), header[2].decode('ascii').rstrip())
            self.dispatch()
        except ProtocolError as error:
            self.respond(error.status, {'error': error.code, 'message': error.message})
        except (TimeoutError, socket.timeout):
            self.respond(408, {'error': 'REQUEST_TIMEOUT', 'message': 'The absolute request deadline expired.'})
        except (ValueError, KeyError, TypeError, OverflowError):
            self.respond(500, {'error': 'INTERNAL_CONTRACT', 'message': 'Local execution failed its safe contract.'})

    def dispatch(self):
        for name in ('Host', 'Content-Length', 'Content-Type', 'Origin', 'Transfer-Encoding'):
            if len(self.headers.get_all(name, [])) > 1:
                raise ProtocolError(400, 'INVALID_HEADERS', 'HTTP headers must be unambiguous.')
        if self.headers.get('Host') != '127.0.0.1:' + str(self.server.server_port):
            raise ProtocolError(403, 'FOREIGN_HOST', 'Only the configured loopback host is accepted.')
        if self.headers.get_all('Origin') is not None or self.headers.get_all('Transfer-Encoding') is not None:
            raise ProtocolError(403, 'FOREIGN_ORIGIN', 'Cross-origin and transferred requests are denied.')
        if self.command not in ('GET', 'POST'):
            raise ProtocolError(405, 'INVALID_METHOD', 'Use local health or request methods.')
        if self.path != ('/health' if self.command == 'GET' else '/request'):
            raise ProtocolError(400, 'INVALID_PATH', 'Only exact local operation paths are accepted.')
        if self.command == 'GET':
            if self.headers.get('Content-Length') is not None or self.headers.get('Content-Type') is not None:
                raise ProtocolError(400, 'INVALID_HEADERS', 'Health accepts no body or content type.')
            self.respond(200, self.server.runtime.health())
            return
        if self.headers.get('Content-Type') != 'application/x-www-form-urlencoded':
            raise ProtocolError(415, 'INVALID_CONTENT_TYPE', 'Use bounded form encoding.')
        length = self.headers.get('Content-Length', '')
        if not re.fullmatch(r'0|[1-9][0-9]{0,4}', length):
            raise ProtocolError(400, 'INVALID_LENGTH', 'An exact bounded content length is required.')
        length = int(length)
        if length > REQUEST_LIMIT:
            raise ProtocolError(413, 'BODY_LIMIT', 'Request bytes exceed the local budget.')
        raw = self.read_bounded(length)
        if len(raw) != length:
            raise ProtocolError(400, 'INCOMPLETE_BODY', 'The request body is incomplete.')
        self.respond(200, self.server.runtime.execute(parse_form(raw)))


class LocalServer(HTTPServer):
    def handle_error(self, *_):
        pass


def main():
    limits = apply_limits()
    runtime = Runtime(os_limits=limits)
    port = configured_integer('LAB_PORT', 8105, 1, 65535)
    server = LocalServer(('127.0.0.1', port), Handler)
    server.runtime, server.timeout = runtime, 0.1
    print(json.dumps({'status': 'READY', 'runId': runtime.run_id, 'port': port}), file=sys.stderr, flush=True)
    try:
        while not runtime.expired() and runtime.attempts <= MAX_ATTEMPTS:
            server.handle_request()
    finally:
        runtime.cleanup()
        server.server_close()


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError, ProtocolError):
        print('Local configuration or pinned assets rejected.', file=sys.stderr)
        raise SystemExit(1) from None
