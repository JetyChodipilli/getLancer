from classifier import classify,evaluate
assert classify('helpful useful')=='positive'
assert classify('bad useless')=='negative'
assert classify('unknown vocabulary') in {'positive','negative'}
result=evaluate();assert result['referenceRows']==4 and result['frozenModel'] is True and result['testRows']==2 and result['correct']==2
assert 'synthetic' in result['context']
print('Tiny synthetic classifier regression passed; accuracy applies only to two rows.')
