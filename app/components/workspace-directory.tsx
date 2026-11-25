'use client';

import Link from 'next/link';
import {ArrowUpRight, Search, X} from 'lucide-react';
import {useCallback, useEffect, useRef, useState} from 'react';
import {api} from '@/lib/api';
import {clientTools, trustTools} from '@/lib/workspace-tools';
import {WorkspaceFrame} from './workspace-frame';
import NavIcon from './nav-icon';

export default function WorkspaceDirectory({area,preview=false}:{area:'clients'|'trust';preview?:boolean}) {
  const [query,setQuery]=useState('');
  const [operator,setOperator]=useState(false);
  const searchField=useRef<HTMLInputElement>(null);
  const attachSearchField=useCallback((input:HTMLInputElement|null)=>{
    searchField.current=input;
    // A visitor can type into the server-rendered field before React attaches.
    if(input?.value)setQuery(input.value);
  },[]);
  useEffect(()=>{
    if(area!=='trust'||preview)return;
    const controller=new AbortController();
    void api('/me',{signal:controller.signal}).then((user:{roles?:string[]})=>{
      if(!controller.signal.aborted)setOperator(!!user.roles?.includes('ADMIN'));
    }).catch(()=>{if(!controller.signal.aborted)setOperator(false)});
    return()=>controller.abort();
  },[area,preview]);

  const isClients=area==='clients';
  const root=preview?'/preview':'/workspace';
  const groups=isClients?clientTools:trustTools(preview,operator);
  const term=query.trim().toLowerCase();
  const visible=groups.map(({group,tools})=>({
    group, tools:tools.filter(tool=>[group,tool.name,tool.description ?? ''].some(value=>value.toLowerCase().includes(term))),
  })).filter(({tools})=>tools.length>0);
  const count=visible.reduce((total,{tools})=>total+tools.length,0);
  const title=isClients?'Clients':'Trust';
  return <WorkspaceFrame section={isClients?'clients':'trust-directory'} title={title}
    description={isClients?'Manage hiring, delivery, purchases, and support.':'Manage reliability and find operational tools.'}
    preview={preview}>
    <div className="studio-directory-controls">
      <label className="studio-directory-search">
        <Search size={18} aria-hidden="true"/>
        <span className="sr-only">Find a tool in {title}</span>
        <input ref={attachSearchField} type="search" placeholder={'Find a tool in '+title} value={query} onChange={event=>setQuery(event.target.value)}/>
      </label>
      {query && <button type="button" className="button studio-clear-search" onClick={()=>{
        setQuery('');
        searchField.current?.focus();
      }}><X size={16} aria-hidden="true"/>Clear search</button>}
    </div>
    {term&&<p role="status" className="studio-directory-result">{count?count+' matching '+(count===1?'tool':'tools'):'No matching tools.'}</p>}
    <div className="studio-directory">{visible.map(({group,tools})=><section key={group}>
      <h2>{group}</h2>
      <ul>{tools.map(({route,name,description,icon})=><li key={route}>
        <Link prefetch={false} tabIndex={0} href={root+'/'+route}>
          <NavIcon name={icon}/>
          <span><strong>{name}</strong><small>{description}</small></span>
          <ArrowUpRight size={16} aria-hidden="true"/>
        </Link>
      </li>)}</ul>
    </section>)}</div>
  </WorkspaceFrame>;
}
