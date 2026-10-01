'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { Menu } from 'lucide-react';
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem } from '@/components/ui/dropdown-menu';
import AccountMenu from './account-menu';
import { api } from '@/lib/api';

export default function Header({preview=false}:{preview?:boolean}) {
 const path=usePathname();
 const [me,setMe]=useState<any>(null);
 const bar=useRef<HTMLDivElement>(null);
 useEffect(()=>{let cancelled=false;if(path.startsWith('/preview/')){setMe(null);return;}api('/me').then(user=>{if(!cancelled)setMe(user)}).catch(()=>{if(!cancelled)setMe(null)});return()=>{cancelled=true}},[path]);
 useEffect(()=>{
  const element=bar.current;if(!element)return;
  const resize=new ResizeObserver(()=>document.documentElement.style.setProperty('--nav-height',`${element.getBoundingClientRect().height}px`));
  resize.observe(element);return()=>resize.disconnect();
 },[]);
 const authPage=path==='/login'||path==='/signup';
 if(authPage)return <a href="#main" className="skip">Skip to content</a>;
 const demo=preview||path.startsWith('/preview/');
 const links=[{href:'/',label:'Explore projects',active:path==='/'},{href:'/templates',label:'Templates',active:path.startsWith('/templates')},{href:'/teams',label:'Teams',active:path.startsWith('/teams')},{href:'/saved',label:'Saved',active:path==='/saved'},me?{href:'/workspace',label:'My workspace',active:path.startsWith('/workspace')}:{href:'/how-it-works',label:'How it works',active:path==='/how-it-works'}];
 return <><a href="#main" className="skip">Skip to content</a><header className="site-header"><div className="topbar" ref={bar}>
  <Link className="brand" href="/"><img className="brand-logo" src="/brand/getlancer-transparent.png" alt="getLancer — Methods for Freelance Success" width={767} height={325} fetchPriority="high"/></Link>
  <div id="discovery-nav-slot" className="discovery-nav-slot"/>
  <nav className="topnav" aria-label="Main navigation">{links.map(l=><Link key={l.href} className={l.active?'current':''} aria-current={l.active?'page':undefined} href={l.href}>{l.label}</Link>)}</nav>
  <div className="nav-menu"><DropdownMenu><DropdownMenuTrigger asChild><button className="iconbutton" aria-label="Open navigation menu"><Menu size={22}/></button></DropdownMenuTrigger><DropdownMenuContent align="end">{links.map(l=><DropdownMenuItem asChild key={l.href}><Link href={l.href}>{l.label}</Link></DropdownMenuItem>)}</DropdownMenuContent></DropdownMenu></div>
  <div className="account" id="account-menu-slot">{path.startsWith('/preview/')?null:me?<AccountMenu name={me.displayName||me.email.split('@')[0]} email={me.email} role={me.roles?.includes('ADMIN')?'Administrator':'Member'} onWorkspace={()=>window.location.href='/workspace'} onProfile={()=>window.location.href='/workspace?tab=profile'} onSettings={()=>window.location.href='/workspace?tab=account'} onExit={async()=>{await api('/auth/logout',{method:'POST'});setMe(null);window.location.href='/'}}/>:<><Link className="link" href="/login">Log in</Link><Link className="button primary" href="/signup">Join getLancer</Link></>}</div>
 </div></header></>;
}
