import csv,json
from decimal import Decimal
from pathlib import Path

def summarize(path):
    totals={};count=0
    with Path(path).open(newline='') as source:
        for row in csv.DictReader(source):
            value=Decimal(row['amount'])
            if value<0: raise ValueError('Negative synthetic revenue')
            totals[row['channel']]=totals.get(row['channel'],Decimal('0'))+value;count+=1
    return {'rows':count,'revenue':{key:str(value.quantize(Decimal('0.01'))) for key,value in sorted(totals.items())}}
if __name__=='__main__':print(json.dumps(summarize(Path(__file__).with_name('synthetic.csv')),sort_keys=True))
