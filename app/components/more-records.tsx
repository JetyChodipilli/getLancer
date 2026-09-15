'use client';
import {useState} from 'react';
import {api} from '@/lib/api';
export default function MoreRecords({path,onItems,firstPage}:{path:string;onItems:(items:any[])=>void;firstPage?:{page:number;hasMore:boolean}}){
 const [page,setPage]=useState(0),[more,setMore]=useState(firstPage?.hasMore??true),[busy,setBusy]=useState(false),[error,setError]=useState('');
 if(!more)return null;
 return <div style={{marginTop:20}}><button className="button" disabled={busy} onClick={async()=>{setBusy(true);setError('');try{const data=await api(path+(path.includes('?')?'&':'?')+'page='+(page+1));onItems(data.items);setPage(page+1);setMore(data.hasMore===true);}catch(e){setError((e as Error).message)}finally{setBusy(false)}}}>{busy?'Loading…':'Load more'}</button>{error&&<p role="alert" className="error">{error}</p>}</div>;
}
