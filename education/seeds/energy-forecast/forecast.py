"""Original frozen linear energy model, exact Decimal inference only."""
import json
import re
from decimal import Decimal
from pathlib import Path

def load_model(path):
    path = Path(path)
    if path.stat().st_size > 65536: raise ValueError('Model exceeds 64 KiB')
    model = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(model, dict) or set(model) != {'baseline', 'per_occupied_hour', 'per_cooling_degree'}: raise ValueError('Exact coefficient schema required')
    if any(not isinstance(value, str) or not re.fullmatch(r'(0|[1-9][0-9]{0,2})\.[0-9]{2}', value) for value in model.values()): raise ValueError('Bounded decimal coefficients required')
    return {key: Decimal(value) for key, value in model.items()}

def predict(temperature, occupied_hours, model=None):
    if type(temperature) not in [int, float] or not -10 <= temperature <= 45 or type(occupied_hours) is not int or not 0 <= occupied_hours <= 24: raise ValueError('Bounded temperature and whole occupied hours required')
    model = model or load_model(Path(__file__).with_name('model.json'))
    degrees = max(Decimal(str(temperature)) - 20, Decimal(0))
    value = model['baseline'] + model['per_occupied_hour'] * occupied_hours + model['per_cooling_degree'] * degrees
    return str(value.quantize(Decimal('0.01')))

def evaluate():
    rows = json.loads(Path(__file__).with_name('evaluation.json').read_text(encoding='utf-8'))
    predictions = [predict(row['temperature'], row['occupied_hours']) for row in rows]
    errors = [abs(Decimal(value) - Decimal(row['synthetic_kwh'])) for value, row in zip(predictions, rows)]
    return {'synthetic': True, 'frozenModel': True, 'testRows': len(rows), 'predictionsKwh': predictions,
            'meanAbsoluteErrorKwh': str((sum(errors) / len(errors)).quantize(Decimal('0.01'))) if errors else None,
            'context': 'Three synthetic held-out rows generated from an illustrative formula; no building forecast accuracy.'}

if __name__ == '__main__': print(json.dumps(evaluate(), sort_keys=True))
