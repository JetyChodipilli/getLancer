import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

export const projectRoot=fileURLToPath(new URL('../',import.meta.url));
export function readEnvironment(file=new URL('../.env',import.meta.url)) {
  const values={};
  for(const line of readFileSync(file,'utf8').split(/\r?\n/)) {
    if(!line.trim()||line.trim().startsWith('#'))continue;
    const match=line.match(/^\s*([A-Z][A-Z0-9_]*)\s*=(.*)$/);
    if(!match)throw Error('Use KEY=value lines in .env.');
    const [,key,raw]=match;
    if(key in values)throw Error('Duplicate environment key: '+key);
    const value=raw.trimStart();
    if(/^['"]/.test(value))throw Error('Use unquoted Java-properties values for '+key+'.');
    values[key]=value.replace(/\\u([0-9a-fA-F]{4})|\\(.)/g,(_,hex,ch)=>hex?String.fromCharCode(parseInt(hex,16)):({n:'\n',r:'\r',t:'\t',f:'\f'}[ch]??ch));
  }
  return values;
}
export function localDatabase(values) {
  if(values.APP_ENV!=='local')throw Error('This command is for APP_ENV=local only.');
  if(!values.DB_URL?.startsWith('jdbc:postgresql://'))throw Error('Set a PostgreSQL JDBC DB_URL.');
  const url=new URL(values.DB_URL.slice(5));
  if(!['localhost','127.0.0.1','[::1]'].includes(url.hostname)||url.username||url.password||url.search||url.hash)
    throw Error('Local setup needs a localhost JDBC URL without credentials or query parameters.');
  if(!url.pathname.slice(1))throw Error('DB_URL must include your existing database name.');
  if(!/^[a-z][a-z0-9_]{0,62}$/.test(values.DB_SCHEMA||'public'))throw Error('Use a lowercase DB_SCHEMA.');
  return {host:url.hostname.replace(/^\[|\]$/g,''),port:Number(url.port||5432),database:decodeURIComponent(url.pathname.slice(1))};
}
export const placeholder=value=>!value||/REPLACE_|YOUR_|CHANGE_ME/.test(value);
