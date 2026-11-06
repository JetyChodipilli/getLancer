'use client';
import {useEffect,useState} from 'react';
import {api} from '@/lib/api';
type Version={version?:string;revision?:number;sha256:string;published_at?:string};
type Props={slug:string;preview:boolean;version:string;sha256:string};
export default function ComponentVersionHistory(props:Props){return <VersionHistory key={JSON.stringify([props.slug,props.preview,props.version,props.sha256])} {...props}/>;}
function VersionHistory({slug,preview,version,sha256}:Props){
 const [items,setItems]=useState<Version[]>([]),[error,setError]=useState(''),[loading,setLoading]=useState(true);
 useEffect(()=>{let active=true;const controller=new AbortController();async function load(){try{const values=preview?[{version,sha256}]:(await api('/components/'+encodeURIComponent(slug)+'/versions',{signal:controller.signal})).items;if(active)setItems(values);}catch(e){if(active)setError((e as Error).message);}finally{if(active)setLoading(false);}}void load();return()=>{active=false;controller.abort();}},[slug,preview,version,sha256]);
 return <section className="kit-info-panel"><h2>Reviewed releases</h2><p>Each release preserves its reviewed source hash. Withdrawal and account restrictions also apply to this history.</p>{loading?<p role="status">Loading releases…</p>:error?<p role="alert" className="error">{error}</p>:items.length?<ol className="kit-release-list">{items.map((item,i)=><li key={item.revision??item.version??i}><strong>{item.revision?'Reviewed revision '+item.revision:'Version '+item.version}</strong>{item.published_at&&<span>{new Date(item.published_at).toLocaleDateString()}</span>}<code>{item.sha256}</code></li>)}</ol>:<p>No version history is available for this earlier release.</p>}</section>;
}
