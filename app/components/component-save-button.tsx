'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Bookmark } from 'lucide-react';
import { api, ApiError } from '@/lib/api';
import { componentSaveKey, parseComponentSaves, setComponentSave } from '@/lib/component-saves';
export default function ComponentSaveButton({ slug, preview }: { slug: string; preview: boolean }) {
  const [saved,setSaved]=useState(false),[busy,setBusy]=useState(false),[ready,setReady]=useState(false),[error,setError]=useState(''),[needsLogin,setNeedsLogin]=useState(false);
  useEffect(()=>{let active=true;async function load(){try{const items=preview?parseComponentSaves(sessionStorage.getItem(componentSaveKey('visitor'))):(await api('/me/saved-components')).items.map((item:{slug:string})=>item.slug);if(active)setSaved(items.includes(slug));}catch(e){if(active){if(e instanceof ApiError&&e.status===401)setNeedsLogin(true);else setError((e as Error).message);}}finally{if(active)setReady(true);}}void load();return()=>{active=false;}},[slug,preview]);
  async function toggle(){if(busy)return;setBusy(true);setError('');try{const next=!saved;if(preview){const key=componentSaveKey('visitor');sessionStorage.setItem(key,JSON.stringify(setComponentSave(parseComponentSaves(sessionStorage.getItem(key)),slug,next)));}else await api('/components/'+encodeURIComponent(slug)+'/save',{method:next?'POST':'DELETE'});setSaved(next);}catch(e){if(e instanceof ApiError&&e.status===401)setNeedsLogin(true);setError((e as Error).message);}finally{setBusy(false);}}
  return <div className="kit-save-control">{needsLogin?<Link href="/login" className="button"><Bookmark size={16} aria-hidden="true"/>Log in to save</Link>:<button className="button" disabled={busy||!ready} aria-pressed={saved} onClick={()=>void toggle()}><Bookmark size={16} fill={saved?'currentColor':'none'} aria-hidden="true"/>{busy?'Saving…':saved?'Saved component':'Save component'}</button>}{saved&&<Link href="/saved/components" className="link">View saved components</Link>}{preview&&<small>Saved in this browser · sample visitor</small>}{error&&<p className="error" role="alert">{error}</p>}</div>;
}
