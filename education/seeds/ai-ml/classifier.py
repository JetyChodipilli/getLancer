"""Original frozen JSON likelihood exercise; no training or executable model loader."""
import json,math,re
from pathlib import Path
ROOT=Path(__file__).resolve().parent
MODEL=json.loads((ROOT/'model.json').read_text(encoding='utf-8'))
TEST=json.loads((ROOT/'evaluation.json').read_text(encoding='utf-8'))
REFERENCE=json.loads((ROOT/'synthetic.json').read_text(encoding='utf-8'))
def tokens(text):return re.findall(r'[a-z]+',text.lower())
def classify(text):
    scores={label:math.log(MODEL['priors'][label])+sum(math.log(MODEL['likelihoods'][label][word]) for word in tokens(text) if word in MODEL['likelihoods'][label]) for label in MODEL['labels']}
    return max(MODEL['labels'],key=lambda label:scores[label])
def evaluate():
    predictions=[classify(row['text']) for row in TEST];correct=sum(prediction==row['label'] for prediction,row in zip(predictions,TEST));return {'referenceRows':len(REFERENCE),'testRows':len(TEST),'correct':correct,'accuracy':correct/len(TEST),'predictions':predictions,'frozenModel':True,'context':'Two separate hand-written synthetic evaluation rows; no training or real-world accuracy claim.'}
if __name__=='__main__':print(json.dumps(evaluate(),sort_keys=True))
