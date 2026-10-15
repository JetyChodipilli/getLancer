"""Nearest-rank latency percentiles over bounded synthetic support records."""
import csv
import json
import math
from datetime import datetime
from decimal import Decimal
from pathlib import Path

FIELDS = ['ticket', 'priority', 'opened', 'closed']
TARGET_SECONDS = {'high': 1800, 'normal': 7200}

def timestamp(value):
    if not isinstance(value, str) or len(value) != 20 or not value.endswith('Z'):
        raise ValueError('Timestamps require YYYY-MM-DDTHH:MM:SSZ')
    return datetime.strptime(value, '%Y-%m-%dT%H:%M:%SZ')

def summarize(path):
    if Path(path).stat().st_size > 2 * 1024 * 1024: raise ValueError('CSV exceeds 2 MiB')
    groups = {priority: [] for priority in TARGET_SECONDS}
    seen = set()
    with Path(path).open(newline='', encoding='utf-8') as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != FIELDS:
            raise ValueError('Exact ticket,priority,opened,closed columns required')
        for row in reader:
            if len(seen) >= 10000 or set(row) != set(FIELDS) or any(value is None for value in row.values()):
                raise ValueError('Invalid row or 10000-row limit exceeded')
            ticket, priority = row['ticket'], row['priority']
            if not ticket or len(ticket) > 40 or ticket in seen or priority not in groups:
                raise ValueError('Unique bounded ticket and known priority required')
            elapsed = int((timestamp(row['closed']) - timestamp(row['opened'])).total_seconds())
            if elapsed < 0 or elapsed > 31 * 86400:
                raise ValueError('Latency must be between zero and 31 days')
            seen.add(ticket)
            groups[priority].append(elapsed)
    result = {}
    for priority, values in groups.items():
        values.sort()
        minutes = lambda seconds: str((Decimal(seconds) / 60).quantize(Decimal('0.01')))
        result[priority] = {'rows': len(values), 'p50Minutes': minutes(values[math.ceil(len(values) * .50) - 1]) if values else None,
                            'p95Minutes': minutes(values[math.ceil(len(values) * .95) - 1]) if values else None,
                            'targetMinutes': TARGET_SECONDS[priority] // 60,
                            'aboveTarget': sum(value > TARGET_SECONDS[priority] for value in values)}
    return {'synthetic': True, 'rows': len(seen), 'method': 'nearest-rank', 'groups': result}

if __name__ == '__main__':
    print(json.dumps(summarize(Path(__file__).with_name('synthetic.csv')), sort_keys=True))
