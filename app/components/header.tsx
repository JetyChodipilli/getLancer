'use client';
import Link from 'next/link';
import {usePathname} from 'next/navigation';
import {useEffect,useRef,useState} from 'react';
import {Menu,Search,X} from 'lucide-react';
import {DropdownMenu,DropdownMenuTrigger,DropdownMenuContent,DropdownMenuItem} from '@/components/ui/dropdown-menu';
import AccountMenu from './account-menu';
import {api} from '@/lib/api';
import Brand from './brand';

type SessionUser={displayName?:string;email:string;roles?:string[]};
export default function Header({preview=false}:{preview?:boolean}){
 const path=usePathname();
 const [me,setMe]=useState<SessionUser|null>(null);
 const [exitPending,setExitPending]=useState(false),[exitError,setExitError]=useState('');
 const exitLock=useRef(false),bar=useRef<HTMLDivElement>(null);
 useEffect(()=>{
  let cancelled=false;
  if(preview||path.startsWith('/preview/'))return;
  api('/me').then(user=>{if(cancelled)return;if(!user||typeof user.email!=='string'){setMe(null);return;}setMe({email:user.email,displayName:typeof user.displayName==='string'?user.displayName:undefined,roles:Array.isArray(user.roles)?user.roles.filter((role:unknown):role is string=>typeof role==='string'):[]});}).catch(()=>{if(!cancelled)setMe(null)});
  return()=>{cancelled=true};
 },[path,preview]);
 useEffect(()=>{
  const element=bar.current;if(!element)return;
  const resize=new ResizeObserver(()=>document.documentElement.style.setProperty('--nav-height',`${element.getBoundingClientRect().height}px`));
  resize.observe(element);return()=>resize.disconnect();
 },[]);
 async function logout(){
  if(exitLock.current)return;
  exitLock.current=true;setExitPending(true);setExitError('');
  try{await api('/auth/logout',{method:'POST'});setMe(null);window.location.href='/';}
  catch(problem){setExitError(problem instanceof Error?problem.message:'We could not sign you out. Please try again.');}
  finally{exitLock.current=false;setExitPending(false);}
 }
 if(path==='/login'||path==='/signup')return <a href="#main" className="skip">Skip to content</a>;
 const currentUser=preview||path.startsWith('/preview/')?null:me;
 const publish=preview?'/preview/workspace?tab=projects':currentUser?'/workspace?tab=projects':'/signup';
 const primaryLinks=[{href:'/',label:'Projects',active:path==='/'||path.startsWith('/products/')},{href:'/components',label:'Components',active:path.startsWith('/components')},{href:'/college-projects',label:'College projects',active:path.startsWith('/college-projects')},{href:'/labs',label:'Learning',active:path.startsWith('/labs')}];
 const moreLinks=[{href:publish,label:'Publish your work',active:false},{href:'/templates',label:'Source templates',active:path.startsWith('/templates')},{href:'/teams',label:'Teams & studios',active:path.startsWith('/teams')},{href:'/saved',label:'Saved projects',active:path==='/saved'},{href:'/how-it-works',label:'How it works',active:path==='/how-it-works'},{href:currentUser?'/workspace':'/login',label:currentUser?'My workspace':'Sign in',active:path.startsWith('/workspace')}];
 return <><a href="#main" className="skip">Skip to content</a><header className={'site-header paper-header'+(path==='/'?' paper-header-dark':'')}><div id="site-navigation-bar" className="topbar" ref={bar}>
  <Link prefetch={false} className="spectral-brand" href="/" aria-label="getLancer home"><Brand/></Link>
  <div id="discovery-nav-slot" className="discovery-nav-slot"/>
  <nav className="topnav" aria-label="Main navigation">{primaryLinks.map(link=><Link prefetch={false} key={link.href} className={link.active?'current':''} aria-current={link.active?'page':undefined} href={link.href}>{link.label}</Link>)}</nav>
  <div className="paper-more-nav"><Link prefetch={false} href="/#paper-search" className="iconbutton paper-header-search" aria-label="Search projects"><Search size={20}/></Link><div className="nav-menu"><DropdownMenu><DropdownMenuTrigger asChild><button className="iconbutton" aria-label="Open navigation menu"><Menu size={22}/></button></DropdownMenuTrigger><DropdownMenuContent align="end">{[...primaryLinks,...moreLinks].map((link,index)=><DropdownMenuItem asChild key={link.href} className={index<primaryLinks.length?'mobile-only-link':undefined}><Link prefetch={false} href={link.href}>{link.label}</Link></DropdownMenuItem>)}</DropdownMenuContent></DropdownMenu></div></div>
  <div className="account" id="account-menu-slot">{path.startsWith('/preview/')?null:currentUser?<AccountMenu name={currentUser.displayName||currentUser.email.split('@')[0]} email={currentUser.email} role={currentUser.roles?.includes('ADMIN')?'Administrator':'Member'} onWorkspace={()=>window.location.href='/workspace'} onProfile={()=>window.location.href='/workspace?tab=profile'} onSettings={()=>window.location.href='/workspace?tab=account'} exitPending={exitPending} onExit={logout}/>:<><Link prefetch={false} className="link" href="/login">Sign in</Link><Link prefetch={false} className="button primary" href={publish}>Publish</Link></>}</div>
 </div></header>{exitError&&<div className="paper-header-alert" role="alert"><span>{exitError}</span><button className="iconbutton" aria-label="Dismiss sign-out message" onClick={()=>setExitError('')}><X size={18} aria-hidden="true"/></button></div>}</>;
}
