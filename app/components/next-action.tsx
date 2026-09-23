'use client';
import {ArrowRight,Inbox} from 'lucide-react';
export default function NextAction({title,detail,action,onAction}:{title:string;detail:string;action:string;onAction:()=>void}){
 return <section className="next-action" aria-label="Your next step"><span className="next-action-icon"><Inbox size={20} aria-hidden="true"/></span><div><strong>{title}</strong><p>{detail}</p></div><button className="button" onClick={onAction}>{action}<ArrowRight size={16} aria-hidden="true"/></button></section>;
}
