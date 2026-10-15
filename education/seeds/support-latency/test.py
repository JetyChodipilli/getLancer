from pathlib import Path
from tempfile import TemporaryDirectory
from analysis import summarize

result = summarize(Path(__file__).with_name('synthetic.csv'))
assert result['rows'] == 6 and result['synthetic'] is True
assert result['groups']['high'] == {'rows': 3, 'p50Minutes': '30.00', 'p95Minutes': '90.00', 'targetMinutes': 30, 'aboveTarget': 1}
assert result['groups']['normal']['p95Minutes'] == '180.00'
assert result['groups']['normal']['aboveTarget'] == 1
header = 'ticket,priority,opened,closed\n'
with TemporaryDirectory() as directory:
    path = Path(directory) / 'input.csv'
    path.write_text(header)
    assert summarize(path)['groups']['high']['p95Minutes'] is None
    for bad in ['S,high,2026-01-01T08:00:00Z,2026-01-01T07:00:00Z\n',
                'S,unknown,2026-01-01T08:00:00Z,2026-01-01T08:00:00Z\n',
                'S,high,invalid,2026-01-01T08:00:00Z\n', 'S,high,2026-01-01T08:00:00Z\n',
                'S,high,2026-01-01T08:00:00Z,2026-01-01T08:00:00Z,extra\n',
                'S,high,2026-01-01T08:00:00Z,2026-01-01T08:00:00Z\n' * 2]:
        path.write_text(header + bad)
        try: summarize(path)
        except ValueError: pass
        else: raise AssertionError('Invalid fixture accepted')
    path.write_text('wrong,columns\n')
    try: summarize(path)
    except ValueError: pass
    else: raise AssertionError('Unexpected columns accepted')
print('Synthetic latency percentile regression passed; no service SLA is certified.')
