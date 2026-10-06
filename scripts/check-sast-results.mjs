import {readFileSync,readdirSync} from 'node:fs';
import {join} from 'node:path';
const directory=process.argv[2];
const files=readdirSync(directory).filter(file=>file.endsWith('.sarif'));
if(!files.length)throw Error('No SAST output was produced.');
let blocked=0,total=0;
for(const file of files){
 const document=JSON.parse(readFileSync(join(directory,file),'utf8'));
 for(const run of document.runs||[]){
  const rules=new Map([...(run.tool?.driver?.rules||[]),...(run.tool?.extensions||[]).flatMap(extension=>extension.rules||[])].map(rule=>[rule.id,rule]));
  for(const result of run.results||[]){
   total++;
   const score=Number(rules.get(result.ruleId)?.properties?.['security-severity']||0);
   if(score>=7&&!result.suppressions?.some(item=>item.status==='accepted')){
    blocked++;console.error(`Blocked SAST finding: ${result.ruleId} severity ${score} in ${file}`);
   }
  }
 }
}
console.log(`SAST evaluated ${files.length} SARIF files and ${total} findings; ${blocked} high/critical findings.`);
if(blocked)process.exitCode=1;
