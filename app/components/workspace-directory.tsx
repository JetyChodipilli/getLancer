'use client';

import Link from 'next/link';
import {ArrowUpRight, Search} from 'lucide-react';
import {useEffect, useState} from 'react';
import {api} from '@/lib/api';
import {WorkspaceFrame} from './workspace-frame';

const clientTools=[
  {group:'Hire', tools:[{route:'business', name:'Business hiring', description:'Create project requests and review builder proposals.'}]},
  {group:'Deliver', tools:[{route:'delivery', name:'Delivery & payments', description:'Track milestones, approvals, and payments.'}]},
  {group:'Buy', tools:[{route:'templates', name:'Templates & purchases', description:'Find reusable source and review past purchases.'}]},
  {group:'Support', tools:[{route:'maintenance', name:'Maintenance & support', description:'Manage ongoing support and maintenance requests.'}]},
];
const reliability={route:'trust', name:'Trust & reliability', description:'Manage availability, delivery evidence, and reliability.'};
const release={route:'operations', name:'Operations & release readiness', description:'Review operational evidence and release requirements.'};
const labs={route:'labs', name:'Lab operations', description:'Review the state of lab programs and environments.'};

export default function WorkspaceDirectory({area,preview=false}:{area:'clients'|'trust';preview?:boolean}) {
  const [query,setQuery]=useState('');
  const [operator,setOperator]=useState(false);
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
  const groups=isClients?clientTools:[
    {group:'Trust',tools:[reliability]},
    {group:'Operations',tools:[...(preview||operator?[release]:[]),...(!preview?[labs]:[])]},
  ];
  const term=query.trim().toLowerCase();
  const visible=groups.map(({group,tools})=>({
    group, tools:tools.filter(tool=>[group,tool.name,tool.description].some(value=>value.toLowerCase().includes(term))),
  })).filter(({tools})=>tools.length>0);
  const count=visible.reduce((total,{tools})=>total+tools.length,0);
  const title=isClients?'Clients':'Trust';
  return <WorkspaceFrame section={isClients?'clients':'trust-directory'} title={title}
    description={isClients?'Manage hiring, delivery, purchases, and support.':'Manage reliability and find operational tools.'}
    preview={preview} operator={!isClients && operator}>
    <label className="studio-directory-search">
      <Search size={18} aria-hidden="true"/>
      <span className="sr-only">Find a tool in {title}</span>
      <input type="search" placeholder={'Find a tool in '+title} value={query} onChange={event=>setQuery(event.target.value)}/>
    </label>
    {term&&<p role="status" className="studio-directory-result">{count?count+' matching '+(count===1?'tool':'tools'):'No matching tools.'}</p>}
    <div className="studio-directory">{visible.map(({group,tools})=><section key={group}>
      <h2>{group}</h2>
      <ul>{tools.map(({route,name,description})=><li key={route}>
        <Link prefetch={false} tabIndex={0} href={root+'/'+route}>
          <span><strong>{name}</strong><small>{description}</small></span>
          <ArrowUpRight size={16} aria-hidden="true"/>
        </Link>
      </li>)}</ul>
    </section>)}</div>
  </WorkspaceFrame>;
}
