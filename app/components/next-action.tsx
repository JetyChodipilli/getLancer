'use client';
import {useId} from 'react';
import {ArrowRight} from 'lucide-react';
export default function NextAction({title,detail,action,onAction}:{title:string;detail:string;action:string;onAction:()=>void}){
 const titleId=useId();
 return <section className="next-action" aria-labelledby={titleId}><div><span className="next-action-caption">Your next step</span><h2 id={titleId}>{title}</h2><p>{detail}</p></div><button type="button" className="button" onClick={onAction}>{action}<ArrowRight size={16} aria-hidden="true"/></button></section>;
}
