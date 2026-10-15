import json
from pathlib import Path
from tempfile import TemporaryDirectory
from classifier import classify, evaluate, load_model

assert classify('password reset') == 'account'
assert classify('invoice') == 'billing'
assert classify('unknown vocabulary') == 'unknown'
assert classify('password invoice') == 'unknown'
result = evaluate(); assert result['testRows'] == 3 and result['correct'] == 3 and result['frozenModel'] is True
for invalid in [None, [], '', 'x' * 501]:
    try: classify(invalid)
    except ValueError: pass
    else: raise AssertionError('Invalid text accepted')
with TemporaryDirectory() as directory:
    path = Path(directory) / 'model.json'
    model = json.loads(Path(__file__).with_name('model.json').read_text())
    for invalid in [{**model, 'remote': 'excluded'}, {**model, 'vocabulary': ['x', 'x']},
                    {**model, 'centroids': [{'label': 'account', 'vector': [1]}]},
                    {**model, 'centroids': [{'label': 'account', 'vector': [True] * 6}]},
                    {**model, 'centroids': [{'label': 'account', 'vector': [0] * 6}]}]:
        path.write_text(json.dumps(invalid))
        try: load_model(path)
        except ValueError: pass
        else: raise AssertionError('Invalid model accepted')
print('Frozen nearest-centroid regression passed; accuracy applies only to three synthetic rows.')
