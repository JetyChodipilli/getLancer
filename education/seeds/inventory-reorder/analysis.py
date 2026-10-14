"""Bounded inventory reorder calculations, using exact synthetic currency."""
import csv
import json
import re
from decimal import Decimal
from pathlib import Path

FIELDS = ['sku', 'stock', 'reserved', 'daily_demand', 'lead_days', 'buffer', 'unit_cost']

def integer(value, maximum):
    if not isinstance(value, str) or not re.fullmatch(r'0|[1-9][0-9]{0,6}', value) or int(value) > maximum:
        raise ValueError('A bounded non-negative whole number is required')
    return int(value)

def summarize(path):
    if Path(path).stat().st_size > 2 * 1024 * 1024: raise ValueError('CSV exceeds 2 MiB')
    rows, seen = [], set()
    with Path(path).open(newline='', encoding='utf-8') as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != FIELDS: raise ValueError('Exact inventory columns required')
        for row in reader:
            if len(rows) >= 5000 or set(row) != set(FIELDS) or any(value is None for value in row.values()): raise ValueError('Invalid row or limit exceeded')
            sku = row['sku']
            if not re.fullmatch(r'SYN-[A-Z0-9-]{1,24}', sku) or sku in seen: raise ValueError('Unique synthetic SKU required')
            stock, reserved, demand, buffer = [integer(row[key], 1000000) for key in ['stock', 'reserved', 'daily_demand', 'buffer']]
            lead = integer(row['lead_days'], 30)
            if lead < 1 or reserved > stock: raise ValueError('Lead time or reserved quantity is invalid')
            if not re.fullmatch(r'(0|[1-9][0-9]{0,5})\.[0-9]{2}', row['unit_cost']): raise ValueError('Exact non-negative currency with two decimals required')
            available, point = stock - reserved, demand * lead + buffer
            order = max(0, point - available)
            rows.append({'sku': sku, 'available': available, 'reorderPoint': point, 'suggestedOrder': order,
                         'suggestedCost': str((Decimal(row['unit_cost']) * order).quantize(Decimal('0.01')))})
            seen.add(sku)
    rows.sort(key=lambda row: (-row['suggestedOrder'], row['sku']))
    return {'synthetic': True, 'rows': len(rows), 'currency': 'synthetic units', 'items': rows,
            'totalSuggestedCost': str(sum((Decimal(row['suggestedCost']) for row in rows), Decimal('0.00')))}

if __name__ == '__main__':
    print(json.dumps(summarize(Path(__file__).with_name('synthetic.csv')), sort_keys=True))
