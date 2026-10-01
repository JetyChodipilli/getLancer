'use client';
import {useEffect} from 'react';
import {track} from '@/lib/analytics';
export default function FallbackObservation({id,resultCount}:{id:string;resultCount:number}){
 useEffect(()=>{track('unavailable_builder_fallback',id,{resultCount})},[id,resultCount]);
 return null;
}
