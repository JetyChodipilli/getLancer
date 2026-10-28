'use client';
import {Select,SelectTrigger,SelectValue,SelectContent,SelectItem} from '@/components/ui/select';

// Taxonomy names are user-managed and may themselves be "ALL". Keep the
// empty choice outside the namespace used by real option values.
function encodePickerValue(value:string){return value?'value:'+value:'ALL';}
function decodePickerValue(value:string){return value==='ALL'?'':value.slice('value:'.length);}

export function Picker({label,value,onChange,options,id,invalid,describedBy}:{label:string;value:string;onChange:(v:string)=>void;options:string[];id?:string;invalid?:boolean;describedBy?:string}){return <Select value={encodePickerValue(value)} onValueChange={v=>onChange(decodePickerValue(v))}><SelectTrigger id={id} aria-label={label} aria-invalid={invalid||undefined} aria-describedby={describedBy} className="filtercontrol"><SelectValue placeholder={label}>{value ? value.replaceAll('_',' ') : label}</SelectValue></SelectTrigger><SelectContent><SelectItem value="ALL">{label}</SelectItem>{options.map(x=><SelectItem key={x} value={encodePickerValue(x)}>{x.replaceAll('_',' ')}</SelectItem>)}</SelectContent></Select>}
