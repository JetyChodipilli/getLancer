'use client';
import Link from 'next/link';
import {usePathname} from 'next/navigation';
import {useEffect,useRef,useState} from 'react';
import {X} from 'lucide-react';
import {DropdownMenu,DropdownMenuTrigger,DropdownMenuContent,DropdownMenuItem} from '@/components/ui/dropdown-menu';
import AccountMenu from './account-menu';
import {api} from '@/lib/api';
import {demoPublishingPath,demoSavedPath} from '@/lib/demo-links';
import Brand from './brand';
import WorkspaceNavigation from './workspace-navigation';
import NavIcon,{type NavIconName} from './nav-icon';

type SessionUser={displayName?:string;email:string;roles?:string[]};
export default function Header({preview=false}:{preview?:boolean}){
 const path=usePathname();
 const adminDashboard=path==='/admin'||path==='/preview/admin';
 const authPage=path==='/login'||path==='/signup';
 const workspacePreview=path.startsWith('/preview/');
 const workspace=workspacePreview||path==='/workspace'||path.startsWith('/workspace/');
 const [me,setMe]=useState<SessionUser|null>(null);
 const [exitPending,setExitPending]=useState(false),[exitError,setExitError]=useState('');
 const exitLock=useRef(false),bar=useRef<HTMLDivElement>(null);
 useEffect(()=>{
  let cancelled=false;
  if(preview||adminDashboard||path.startsWith('/preview/'))return;
  api('/me').then(user=>{if(cancelled)return;if(!user||typeof user.email!=='string'){setMe(null);return;}setMe({email:user.email,displayName:typeof user.displayName==='string'?user.displayName:undefined,roles:Array.isArray(user.roles)?user.roles.filter((role:unknown):role is string=>typeof role==='string'):[]});}).catch(()=>{if(!cancelled)setMe(null)});
  return()=>{cancelled=true};
 },[path,preview,adminDashboard]);
 useEffect(()=>{
  const element=bar.current;if(!element)return;
  const resize=new ResizeObserver(()=>document.documentElement.style.setProperty('--nav-height',`${element.getBoundingClientRect().height}px`));
  resize.observe(element);return()=>resize.disconnect();
 },[authPage,adminDashboard]);
 async function logout(){
  if(exitLock.current)return;
  exitLock.current=true;setExitPending(true);setExitError('');
  try{await api('/auth/logout',{method:'POST'});setMe(null);window.location.href='/';}
  catch(problem){setExitError(problem instanceof Error?problem.message:'We could not sign you out. Please try again.');}
  finally{exitLock.current=false;setExitPending(false);}
 }
 if(authPage||adminDashboard)return <a href="#main" className="skip">Skip to content</a>;
 const currentUser=preview||path.startsWith('/preview/')?null:me;
 const publish=preview?demoPublishingPath:currentUser?'/workspace?tab=projects':'/signup';
 const primaryLinks:{href:string;label:string;icon:NavIconName;active:boolean}[]=[{href:'/',label:'Projects',icon:'home',active:path==='/'||path.startsWith('/products/')},{href:'/components',label:'Components',icon:'components',active:path.startsWith('/components')},{href:'/college-projects',label:'College projects',icon:'college',active:path.startsWith('/college-projects')},{href:'/labs',label:'Learning',icon:'learning',active:path.startsWith('/labs')}];
 const moreLinks:{href:string;label:string;icon:NavIconName;active:boolean}[]=[{href:publish,label:'Publish your work',icon:'publish',active:false},{href:'/templates',label:'Source templates',icon:'templates',active:path.startsWith('/templates')},{href:'/teams',label:'Teams & studios',icon:'teams',active:path.startsWith('/teams')},{href:preview?demoSavedPath:'/saved',label:'Saved projects',icon:'saved',active:path==='/saved'},{href:'/how-it-works',label:'How it works',icon:'help',active:path==='/how-it-works'},{href:currentUser?'/workspace':'/login',label:currentUser?'My workspace':'Sign in',icon:currentUser?'workspace':'signin',active:path.startsWith('/workspace')}];
 return <><a href="#main" className="skip">Skip to content</a><header className={'site-header paper-header paper-icon-header'+(workspace?' workspace-site-header':'')}><div id="site-navigation-bar" className="topbar" ref={bar}>
  <Link prefetch={false} className="spectral-brand" href="/" aria-label="getLancer home"><Brand/></Link>
  <div id="discovery-nav-slot" className="discovery-nav-slot"/>
  {workspace?<WorkspaceNavigation key={path} path={path} preview={workspacePreview} operator={currentUser?.roles?.includes('ADMIN')??false}/>:<nav className="topnav" aria-label="Main navigation">{primaryLinks.map(link=><Link prefetch={false} tabIndex={0} key={link.href} className={'paper-icon-action'+(link.active?' current':'')} aria-current={link.active?'page':undefined} href={link.href}><NavIcon name={link.icon}/><span>{link.label}</span></Link>)}</nav>}
  <div className="paper-more-nav">{!workspace&&<Link prefetch={false} href="/#paper-search" className="iconbutton paper-header-search paper-icon-action" aria-label="Search projects"><NavIcon name="search"/></Link>}<div className="nav-menu"><DropdownMenu modal={false}><DropdownMenuTrigger asChild><button className="iconbutton paper-icon-action" aria-label="Open navigation menu"><NavIcon name="menu"/></button></DropdownMenuTrigger><DropdownMenuContent align="end" className="paper-navigation-menu">{[...primaryLinks,...moreLinks,...(workspace?[{href:'/labs/iot',label:'IoT simulation',icon:'learning' as const,active:false},{href:'/components/frameworks',label:'Framework previews',icon:'components' as const,active:false}]:[])].map((link,index)=><DropdownMenuItem asChild key={link.href} className={'paper-icon-action'+(index<primaryLinks.length&&!workspace?' mobile-only-link':'')}><Link prefetch={false} href={link.href} aria-current={link.active?'page':undefined}><NavIcon name={link.icon}/><span>{link.label}</span></Link></DropdownMenuItem>)}</DropdownMenuContent></DropdownMenu></div></div>
  <div className="account" id="account-menu-slot">{path.startsWith('/preview/')?null:currentUser?<AccountMenu name={currentUser.displayName||currentUser.email.split('@')[0]} email={currentUser.email} role={currentUser.roles?.includes('ADMIN')?'Administrator':'Member'} onWorkspace={()=>window.location.href='/workspace'} onProfile={()=>window.location.href='/workspace?tab=profile'} onSettings={()=>window.location.href='/workspace?tab=account'} exitPending={exitPending} onExit={logout}/>:<><Link prefetch={false} className="link" href="/login">Sign in</Link><Link prefetch={false} className="button primary" href={publish}>Publish</Link></>}</div>
 </div></header>{exitError&&<div className="paper-header-alert" role="alert"><span>{exitError}</span><button className="iconbutton" aria-label="Dismiss sign-out message" onClick={()=>setExitError('')}><X size={18} aria-hidden="true"/></button></div>}</>;
}
