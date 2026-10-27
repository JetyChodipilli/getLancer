'use client';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Bookmark } from 'lucide-react';
import { api, ApiError } from '@/lib/api';
import { componentSaveKey, parseComponentSaves, setComponentSave } from '@/lib/component-saves';
export default function ComponentSaveButton({ slug, preview }: { slug: string; preview: boolean }) {
  return <SaveButton key={preview+':'+slug} slug={slug} preview={preview}/>;
}
function SaveButton({ slug, preview }: { slug: string; preview: boolean }) {
  const [saved,setSaved]=useState(false),[busy,setBusy]=useState(false),[ready,setReady]=useState(false),[error,setError]=useState(''),[needsLogin,setNeedsLogin]=useState(false);
  const active=useRef(true),pending=useRef(false);
  useEffect(()=>{let current=true;active.current=true;async function load(){try{const items=preview?parseComponentSaves(sessionStorage.getItem(componentSaveKey('visitor'))):(await api('/me/saved-components')).items.map((item:{slug:string})=>item.slug);if(current)setSaved(items.includes(slug));}catch(e){if(current){if(e instanceof ApiError&&[401,403].includes(e.status))setNeedsLogin(true);else setError((e as Error).message);}}finally{if(current)setReady(true);}}void load();return()=>{current=false;active.current=false;}},[slug,preview]);
  async function toggle(){if(pending.current||!ready||needsLogin)return;pending.current=true;setBusy(true);setError('');try{const next=!saved;if(preview){const key=componentSaveKey('visitor');sessionStorage.setItem(key,JSON.stringify(setComponentSave(parseComponentSaves(sessionStorage.getItem(key)),slug,next)));}else await api('/components/'+encodeURIComponent(slug)+'/save',{method:next?'POST':'DELETE'});if(active.current)setSaved(next);}catch(e){if(active.current){if(e instanceof ApiError&&[401,403].includes(e.status)){setSaved(false);setNeedsLogin(true);}setError((e as Error).message);}}finally{pending.current=false;if(active.current)setBusy(false);}}
  return <div className="kit-save-control">{needsLogin?<Link href="/login" className="button"><Bookmark size={16} aria-hidden="true"/>Log in to save</Link>:<button className="button" disabled={busy||!ready} aria-pressed={saved} onClick={()=>void toggle()}><Bookmark size={16} fill={saved?'currentColor':'none'} aria-hidden="true"/>{busy?'Saving…':saved?'Saved component':'Save component'}</button>}{saved&&<Link href="/saved/components" className="link">View saved components</Link>}{preview&&<small>Saved in this browser · sample visitor</small>}{error&&<p className="error" role="alert">{error}</p>}</div>;
}
