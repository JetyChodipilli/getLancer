'use client';
import { useEffect } from 'react';
import { track } from '@/lib/analytics';
export default function ProofEvents({id}: {id: string}) {
  useEffect(()=>{
    track('product_view',id);
    const clicks=(event:MouseEvent)=>{const anchor=(event.target as Element)?.closest<HTMLAnchorElement>('a[data-proof-event]');if(anchor?.dataset.proofEvent)track(anchor.dataset.proofEvent,id);};
    document.addEventListener('click',clicks);return()=>document.removeEventListener('click',clicks);
  },[id]);
  return null;
}
