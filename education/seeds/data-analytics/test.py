from pathlib import Path
from analysis import summarize
result=summarize(Path(__file__).with_name('synthetic.csv'))
assert result=={'rows':6,'revenue':{'store':'15.00','web':'21.50'}},result
print('Six-row Decimal analysis regression passed.')
