"""Frozen nearest-centroid intent exercise; no training or remote model code."""
import json
import re
from pathlib import Path

def load_model(path):
    path = Path(path)
    if path.stat().st_size > 65536: raise ValueError('Model exceeds 64 KiB')
    model = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(model, dict) or set(model) != {'vocabulary', 'centroids'}: raise ValueError('Invalid model schema')
    vocabulary, centroids = model['vocabulary'], model['centroids']
    if not isinstance(vocabulary, list) or not 1 <= len(vocabulary) <= 20 or any(not isinstance(word, str) or not re.fullmatch(r'[a-z]{1,20}', word) for word in vocabulary) or len(set(vocabulary)) != len(vocabulary): raise ValueError('Invalid vocabulary')
    if not isinstance(centroids, list) or not 1 <= len(centroids) <= 10: raise ValueError('Invalid centroids')
    labels = set()
    for centroid in centroids:
        if not isinstance(centroid, dict) or set(centroid) != {'label', 'vector'} or not isinstance(centroid['label'], str) or not re.fullmatch(r'[a-z-]{1,30}', centroid['label']) or centroid['label'] in labels: raise ValueError('Invalid label')
        vector = centroid['vector']
        if not isinstance(vector, list) or len(vector) != len(vocabulary) or any(type(value) is not int or value not in [0, 1] for value in vector) or not any(vector): raise ValueError('Invalid vector')
        labels.add(centroid['label'])
    return model

def classify(text, model=None):
    if not isinstance(text, str) or not text.strip() or len(text) > 500: raise ValueError('Text must contain 1 to 500 characters')
    model = model or load_model(Path(__file__).with_name('model.json'))
    words = set(re.findall(r'[a-z]+', text.lower()))
    vector = [int(word in words) for word in model['vocabulary']]
    if not any(vector): return 'unknown'
    ranked = sorted((sum((value - actual) ** 2 for value, actual in zip(centroid['vector'], vector)), centroid['label']) for centroid in model['centroids'])
    # A tied distance is uncertainty, not a guessed success.
    if len(ranked) > 1 and ranked[0][0] == ranked[1][0]: return 'unknown'
    return ranked[0][1]

def evaluate():
    rows = json.loads(Path(__file__).with_name('evaluation.json').read_text(encoding='utf-8'))
    predictions = [classify(row['text']) for row in rows]
    correct = sum(prediction == row['label'] for prediction, row in zip(predictions, rows))
    return {'synthetic': True, 'frozenModel': True, 'testRows': len(rows), 'correct': correct,
            'accuracy': correct / len(rows) if rows else None, 'predictions': predictions,
            'context': 'Three hand-written held-out rows only; not real support routing accuracy.'}

if __name__ == '__main__': print(json.dumps(evaluate(), sort_keys=True))
