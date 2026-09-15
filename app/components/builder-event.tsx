'use client';
import {useEffect} from 'react';
import {track} from '@/lib/analytics';
export default function BuilderEvent({id}:{id:string}){useEffect(()=>{track('builder_profile_view',id)},[id]);return null;}
