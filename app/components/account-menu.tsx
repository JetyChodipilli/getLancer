'use client';
import {ChevronDown,Check,LogOut,UserRound,LayoutDashboard,FlaskConical,Settings} from 'lucide-react';
import {DropdownMenu,DropdownMenuTrigger,DropdownMenuContent,DropdownMenuLabel,DropdownMenuItem,DropdownMenuSeparator,DropdownMenuSub,DropdownMenuSubTrigger,DropdownMenuSubContent} from '@/components/ui/dropdown-menu';

type Account={id:string;name:string;role:string};
export default function AccountMenu({name,role,email,demoAccounts,selectedId,onSwitch,onWorkspace,onProfile,onSettings,onExit}:{name:string;role:string;email?:string;demoAccounts?:Account[];selectedId?:string;onSwitch?:(id:string)=>void;onWorkspace:()=>void;onProfile?:()=>void;onSettings?:()=>void;onExit:()=>void}){
 const initials=name.trim().split(/\s+/).slice(0,2).map(word=>word[0]).join('').toUpperCase();
 return <DropdownMenu><DropdownMenuTrigger asChild><button className="account-trigger" aria-label={`Account menu for ${name}`}><span className="account-avatar" aria-hidden="true">{initials}</span><span className="account-identity"><strong>{name}</strong><small>{demoAccounts?'Demo · ':''}{role}</small></span><ChevronDown size={16} aria-hidden="true"/></button></DropdownMenuTrigger><DropdownMenuContent className="account-popover" align="end" sideOffset={12}>
  <DropdownMenuLabel className="account-menu-heading"><strong>{name}</strong><span>{demoAccounts?'Sample account · no real sign-in':email||role}</span></DropdownMenuLabel>
  <DropdownMenuSeparator/>
  <DropdownMenuItem onSelect={onWorkspace}><LayoutDashboard/>My workspace</DropdownMenuItem>
  {onProfile&&<DropdownMenuItem onSelect={onProfile}><UserRound/>Your profile</DropdownMenuItem>}
  {onSettings&&<DropdownMenuItem onSelect={onSettings}><Settings/>Account settings</DropdownMenuItem>}
  {demoAccounts&&<><DropdownMenuSeparator/><DropdownMenuLabel className="account-switch-label">Demo tools</DropdownMenuLabel><DropdownMenuSub><DropdownMenuSubTrigger><FlaskConical/>Switch demo account</DropdownMenuSubTrigger><DropdownMenuSubContent className="account-switch-popover">{demoAccounts.map(account=><DropdownMenuItem key={account.id} onSelect={()=>onSwitch?.(account.id)}><span className="account-avatar account-avatar-small" aria-hidden="true">{account.name.split(' ').map(w=>w[0]).join('')}</span><span className="account-choice"><strong>{account.name}</strong><small>{account.role}</small></span>{account.id===selectedId&&<Check size={16} className="account-check"/>}</DropdownMenuItem>)}</DropdownMenuSubContent></DropdownMenuSub></>}
  <DropdownMenuSeparator/><DropdownMenuItem onSelect={onExit}><LogOut/>{demoAccounts?'Exit demo':'Log out'}</DropdownMenuItem>
 </DropdownMenuContent></DropdownMenu>;
}
