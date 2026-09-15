'use client';
import {useEffect,useState} from 'react';
import {api,ApiError} from './api';
import {categories as previewCategories,technologies as previewTechnologies} from './catalog';
export function useTaxonomy(){
 const [categories,setCategories]=useState<string[]>([]),[technologies,setTechnologies]=useState<string[]>([]),[taxonomyError,setError]=useState('');
 useEffect(()=>{Promise.all([api('/categories'),api('/technologies')]).then(([c,t])=>{setCategories(c.items.map((x:any)=>x.name));setTechnologies(t.items.map((x:any)=>x.name));}).catch(e=>{if(e instanceof ApiError&&e.code==='BACKEND_NOT_CONFIGURED'){setCategories(previewCategories);setTechnologies(previewTechnologies);}else setError('Category options could not load. Refresh to try again.');});},[]);
 return {categories,technologies,taxonomyError};
}
