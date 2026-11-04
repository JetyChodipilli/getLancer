'use client';
import {useEffect,useState} from 'react';
import {api,ApiError} from './api';
import {categories as previewCategories,technologies as previewTechnologies} from './catalog';
export function useTaxonomy(){
 const [categories,setCategories]=useState<string[]>([]),[technologies,setTechnologies]=useState<string[]>([]),[taxonomyError,setError]=useState('');
 useEffect(()=>{
  let controller:AbortController;let active=true;
  const load=()=>{
   const request=new AbortController();controller=request;
   Promise.all([api('/categories',{signal:request.signal}),api('/technologies',{signal:request.signal})])
    .then(([c,t])=>{if(!active||request.signal.aborted)return;setCategories(c.items.map((x:{name:string})=>x.name));setTechnologies(t.items.map((x:{name:string})=>x.name));setError('');})
    .catch(e=>{if(!active||request.signal.aborted)return;if(e instanceof ApiError&&e.code==='BACKEND_NOT_CONFIGURED'){setCategories(previewCategories);setTechnologies(previewTechnologies);setError('');}else setError('Category options could not load. Refresh to try again.');});
  };
  const cancel=()=>controller.abort();
  const resume=()=>{if(active&&controller.signal.aborted)load();};
  // Document navigation need not unmount React before the browser unloads.
  window.addEventListener('pagehide',cancel);
  // A back/forward-cache restore keeps this effect mounted; restart cancelled reads.
  window.addEventListener('pageshow',resume);load();
  return()=>{active=false;window.removeEventListener('pagehide',cancel);window.removeEventListener('pageshow',resume);cancel();};
 },[]);
 return {categories,technologies,taxonomyError};
}
