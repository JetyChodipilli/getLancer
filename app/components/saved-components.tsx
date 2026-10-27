'use client';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { api, ApiError } from '@/lib/api';
import { componentSeeds, type ComponentEntry } from '@/lib/components';
import { componentSaveKey, parseComponentSaves, setComponentSave } from '@/lib/component-saves';
export default function SavedComponents({ preview }: { preview: boolean }) {
  return <SavedList key={String(preview)} preview={preview}/>;
}
function SavedList({ preview }: { preview: boolean }) {
  const [items,setItems]=useState<(ComponentEntry&{available:boolean})[]>([]),[error,setError]=useState(''),[loading,setLoading]=useState(true),[busy,setBusy]=useState<string|null>(null),[needsLogin,setNeedsLogin]=useState(false);
  const active=useRef(true),generation=useRef(0),pending=useRef(false);
  function current(value:number){return active.current&&value===generation.current;}
  function denied(e:unknown){if(e instanceof ApiError&&[401,403].includes(e.status)){setItems([]);setNeedsLogin(true);}}
  async function load(){const value=++generation.current;setLoading(true);setError('');try{const data=preview?parseComponentSaves(sessionStorage.getItem(componentSaveKey('visitor'))).map(slug=>{const seed=componentSeeds.find(item=>item.slug===slug);return seed?{...seed,available:true}:{slug,available:false} as ComponentEntry&{available:boolean};}):(await api('/me/saved-components')).items;if(current(value)){setItems(data);setNeedsLogin(false);}}catch(e){if(current(value)){denied(e);setError((e as Error).message);}}finally{if(current(value))setLoading(false);}}
  useEffect(()=>{active.current=true;void load();return()=>{active.current=false;generation.current++;}},[]);
  async function remove(slug:string){if(pending.current||loading||needsLogin)return;const value=generation.current;pending.current=true;setBusy(slug);setError('');try{if(preview){const key=componentSaveKey('visitor');sessionStorage.setItem(key,JSON.stringify(setComponentSave(parseComponentSaves(sessionStorage.getItem(key)),slug,false)));}else await api('/components/'+encodeURIComponent(slug)+'/save',{method:'DELETE'});if(current(value))setItems(old=>old.filter(item=>item.slug!==slug));}catch(e){if(current(value)){denied(e);setError((e as Error).message);}}finally{pending.current=false;if(current(value))setBusy(null);}}
  return <main id="main" className="kit-page"><header className="kit-detail-head"><div><h1>Saved components</h1><p>Keep useful building blocks close.</p></div><Link href="/saved" className="button">Saved projects</Link></header>{preview&&<p className="kit-notice">Sample visitor saves in this browser. No real account data is changed.</p>}{error&&<p className="error" role="alert">{error}</p>}{needsLogin?<Link className="button" href="/login">Log in to view saved components</Link>:error&&<button className="button" disabled={loading||busy!==null} onClick={()=>void load()}>Reload saved components</button>}{loading?<p role="status">Loading saved components…</p>:!error&&!items.length?<section className="kit-empty"><h2>Your next building block is here.</h2><p>Save a component while exploring to return to it later.</p><Link className="button" href="/components">Explore components</Link></section>:<div className="kit-admin-list">{items.map(item=><article key={item.slug}><div><h2>{item.available?<Link href={'/components/'+item.slug}>{item.title}</Link>:'Component unavailable'}</h2><p>{item.available?item.summary:'This component was withdrawn or is no longer public.'}</p></div><button className="button" disabled={busy!==null} onClick={()=>void remove(item.slug)}>{busy===item.slug?'Removing…':'Remove'}</button></article>)}</div>}</main>;
}
