import math,re,json
from collections import Counter
TRAIN=[('good helpful','positive'),('happy useful','positive'),('bad broken','negative'),('sad useless','negative')]
TEST=[('helpful useful','positive'),('bad useless','negative')]
def tokens(text):return re.findall(r'[a-z]+',text.lower())
def classify(text):
    labels=sorted({label for _,label in TRAIN});vocabulary={word for value,_ in TRAIN for word in tokens(value)};scores={}
    for label in labels:
        rows=[value for value,current in TRAIN if current==label];counts=Counter(word for value in rows for word in tokens(value));scores[label]=math.log(len(rows)/len(TRAIN))+sum(math.log((counts[word]+1)/(sum(counts.values())+len(vocabulary))) for word in tokens(text) if word in vocabulary)
    return max(labels,key=lambda label:scores[label])
def evaluate():
    predictions=[classify(text) for text,_ in TEST];correct=sum(prediction==label for prediction,(_,label) in zip(predictions,TEST));return {'trainRows':len(TRAIN),'testRows':len(TEST),'correct':correct,'accuracy':correct/len(TEST),'predictions':predictions,'context':'Two hand-written synthetic test rows; no population performance claim.'}
if __name__=='__main__':print(json.dumps(evaluate(),sort_keys=True))
