'use client';
import {useEffect} from 'react';
import {observeArtifact} from '@/lib/analytics';
export default function ArtifactObservation({event,slug}:{event:'component_discovery'|'lab_discovery';slug?:string}){useEffect(()=>{observeArtifact(event,slug);},[event,slug]);return null;}
