import json
from pathlib import Path
from tempfile import TemporaryDirectory
from forecast import predict, evaluate, load_model

assert predict(10, 2) == '5.50'
assert predict(25.5, 4) == '9.20'
assert evaluate()['predictionsKwh'] == ['4.00', '9.00', '14.00']
assert evaluate()['meanAbsoluteErrorKwh'] == '0.00' and evaluate()['testRows'] == 3
for temperature, hours in [(46, 2), (-11, 2), (float('nan'), 2), (float('inf'), 2), (20, 25), (20, 1.5), (True, 2), (20, True), ('20', 2)]:
    try: predict(temperature, hours)
    except ValueError: pass
    else: raise AssertionError('Invalid inference inputs accepted')
with TemporaryDirectory() as directory:
    path = Path(directory) / 'model.json'
    for model in [{'baseline': 'NaN', 'per_occupied_hour': '0.75', 'per_cooling_degree': '0.40'},
                  {'baseline': 4, 'per_occupied_hour': '0.75', 'per_cooling_degree': '0.40'},
                  {'baseline': '4.00', 'per_occupied_hour': '0.75', 'per_cooling_degree': '0.40', 'loader': 'excluded'}]:
        path.write_text(json.dumps(model))
        try: load_model(path)
        except ValueError: pass
        else: raise AssertionError('Invalid model accepted')
print('Frozen Decimal forecast regression passed; zero error is only a synthetic formula check.')
