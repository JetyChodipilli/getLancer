#!/usr/bin/env python3
"""Build deterministic original example ZIPs and immutable public audit facts."""
from pathlib import Path
import hashlib,io,json,zipfile
root=Path(__file__).resolve().parents[1]
inventory={
    'full-stack':{'LICENSE','NOTICE','README.md','package.json','server.mjs','test.mjs'},
    'data-analytics':{'LICENSE','NOTICE','README.md','analysis.py','pyproject.toml','synthetic.csv','test.py'},
    'ai-ml':{'LICENSE','MODEL_LICENSE','NOTICE','README.md','classifier.py','evaluation.json','model.json','pyproject.toml','synthetic.json','test.py'},
    'iot':{'LICENSE','NOTICE','README.md','package.json','simulate.mjs','test.mjs'}}
output=root/'public/education';output.mkdir(parents=True,exist_ok=True)
records=[]
for index,item in enumerate(json.loads((root/'education/projects.json').read_text()),1):
    if item['folder'] not in inventory:raise ValueError('Unknown education seed folder')
    folder=root/'education/seeds'/item['folder']
    if folder.is_symlink() or any(p.is_symlink() for p in folder.iterdir()):raise ValueError('Education seed material cannot contain symbolic links')
    if item['category'] in ('DATA_ANALYTICS','AI_ML'):
        item['dataAiEvidence']=dict(item['dataAiEvidence'])
        item['dataAiEvidence']['dataSha256']=hashlib.sha256((folder/('synthetic.csv' if item['category']=='DATA_ANALYTICS' else 'synthetic.json')).read_bytes()).hexdigest()
        if item['category']=='AI_ML':item['dataAiEvidence']['modelSha256']=hashlib.sha256((folder/'model.json').read_bytes()).hexdigest()
    files=sorted(p.name for p in folder.iterdir() if p.is_file())
    if set(files)!=inventory[item['folder']]:raise ValueError('Education seed inventory changed unexpectedly')
    buffer=io.BytesIO()
    with zipfile.ZipFile(buffer,'w',compression=zipfile.ZIP_STORED) as archive:
        for name in files:
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.create_system=3;info.external_attr=0o100644<<16
            archive.writestr(info,(folder/name).read_bytes())
    data=buffer.getvalue();(output/(item['folder']+'.zip')).write_bytes(data)
    binding={'packageId':f'20000000-0000-4000-8000-{index:012d}','sha256':hashlib.sha256(data).hexdigest(),'sizeBytes':len(data),'entryCount':len(files),'files':files,'manifestFiles':[name for name in files if name in ('package.json','pyproject.toml')],'licenseTerms':item['package']['licenseTerms']}
    snapshot={key:value for key,value in item.items() if key not in ('folder',) and value is not None}
    source_binding=binding if item['mode']=='FREE' else {**{key:value for key,value in binding.items() if key!='packageId'},'templateId':f'40000000-0000-4000-8000-{index:012d}','versionId':f'30000000-0000-4000-8000-{index:012d}','version':'1.0.0'} if item['mode']=='PAID' else None
    snapshot.update(productId=item['slug'],sourceBinding=source_binding)
    release={'id':f'10000000-0000-4000-8000-{index:012d}','productId':item['slug'],'revision':1,'status':'APPROVED','sourceHash':hashlib.sha256(json.dumps(snapshot,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest(),'snapshot':snapshot,'academic':{'revision':1,'shareAcademicDetails':False},'demoAvailability':'Source only; no hosted demo','demoUrl':'','componentLinks':[]}
    records.append({'productSlug':item['slug'],'title':item['title'],'summary':item['summary'],'release':release,'packageAudit':binding,'previewPackageUrl':'/education/'+item['folder']+'.zip' if item['mode']=='FREE' else None,'illustrative':True})
path=root/'backend/src/main/resources/catalog/education.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(records,indent=2)+'\n')
print('Built four deterministic original education packages.')
