from pathlib import Path
from tempfile import TemporaryDirectory
from analysis import summarize

result = summarize(Path(__file__).with_name('synthetic.csv'))
assert result['rows'] == 4 and result['synthetic'] is True
assert result['items'][0] == {'sku': 'SYN-PENCIL', 'available': 20, 'reorderPoint': 35, 'suggestedOrder': 15, 'suggestedCost': '18.75'}
assert result['totalSuggestedCost'] == '37.55', result
assert next(item for item in result['items'] if item['sku'] == 'SYN-NOTEBOOK')['suggestedOrder'] == 0
header = 'sku,stock,reserved,daily_demand,lead_days,buffer,unit_cost\n'
with TemporaryDirectory() as directory:
    path = Path(directory) / 'input.csv'
    path.write_text(header); assert summarize(path)['totalSuggestedCost'] == '0.00'
    for bad in ['SYN-A,1,2,1,1,0,1.00\n', 'SYN-A,-1,0,1,1,0,1.00\n',
                'SYN-A,1,0,1,0,0,1.00\n', 'SYN-A,1,0,1,31,0,1.00\n',
                'SYN-A,1,0,1,1,0,NaN\n', 'SYN-A,1,0,1,1,0,1.001\n',
                'SYN-A,1,0,1,1,0,1.00\n' * 2, 'SYN-A,1,0,1,1,0\n']:
        path.write_text(header + bad)
        try: summarize(path)
        except ValueError: pass
        else: raise AssertionError('Invalid inventory accepted')
print('Synthetic reorder and exact currency regression passed; no purchasing advice.')
