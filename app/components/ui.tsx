'use client';
import {Select,SelectTrigger,SelectValue,SelectContent,SelectItem} from '@/components/ui/select';

// Taxonomy names are user-managed and may themselves be "ALL". Keep the
// empty choice outside the namespace used by real option values.
function encodePickerValue(value:string){return value?'value:'+value:'ALL';}
function decodePickerValue(value:string){return value==='ALL'?'':value.slice('value:'.length);}

export function Picker({label,value,onChange,options,optionLabels,id,invalid,describedBy}:{label:string;value:string;onChange:(v:string)=>void;options:string[];optionLabels?:Readonly<Record<string,string>>;id?:string;invalid?:boolean;describedBy?:string}){
 const display=(option:string)=>optionLabels?.[option]??option.replaceAll('_',' ');
 return <Select value={encodePickerValue(value)} onValueChange={v=>onChange(decodePickerValue(v))}>
  <SelectTrigger id={id} aria-label={label} aria-invalid={invalid||undefined} aria-describedby={describedBy} className="filtercontrol">
   <SelectValue placeholder={label}>{value ? display(value) : label}</SelectValue>
  </SelectTrigger>
  <SelectContent><SelectItem value="ALL">{label}</SelectItem>{options.map(option=><SelectItem key={option} value={encodePickerValue(option)}>{display(option)}</SelectItem>)}</SelectContent>
 </Select>;
}
