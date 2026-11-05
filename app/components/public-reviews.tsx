'use client';
import {useEffect,useRef,useState} from 'react';
import {api} from '@/lib/api';
import {recordPage,type RecordPage} from '@/lib/record-page';
import MoreRecords from './more-records';

export type PublicReview={id?:string;rating:number;reviewText:string;createdAt:string;clientName?:string};
type Props={slug:string;initialPage:RecordPage<PublicReview>|null;initialError?:string};
export default function PublicReviews(props:Props){return <ReviewList key={props.slug} {...props}/>;}
function ReviewList({slug,initialPage,initialError=''}:Props){
 const [page,setPage]=useState(initialPage),[reviews,setReviews]=useState(initialPage?.items||[]),[error,setError]=useState(initialError),[loading,setLoading]=useState(false),[epoch,setEpoch]=useState(0);
 const generation=useRef(0),pending=useRef(false),controller=useRef<AbortController|null>(null);
 const path='/builders/'+encodeURIComponent(slug)+'/reviews';
 useEffect(()=>()=>{generation.current++;controller.current?.abort();},[]);
 async function retry(){
  if(pending.current)return;pending.current=true;const current=++generation.current,request=new AbortController();controller.current=request;setLoading(true);setError('');setPage(null);
  try{const first=recordPage<PublicReview>(await api(path+'?page=0',{signal:request.signal}));if(current!==generation.current)return;setReviews(first.items);setPage(first);setEpoch(value=>value+1);}
  catch(problem){if(current===generation.current&&!request.signal.aborted)setError((problem as Error).message);}
  finally{if(current===generation.current){pending.current=false;setLoading(false);}}
 }
 return <section id="client-reviews" style={{marginTop:40}}><h2>Client reviews</h2><p className="muted">Reviews follow client-confirmed completed engagements.</p>
  {loading&&<p role="status">Loading client reviews…</p>}{error&&<p role="alert" className="error">{error}</p>}{!page&&!loading&&<button type="button" className="button" onClick={()=>void retry()}>Retry client reviews</button>}
  {reviews.map((review,index)=><article className="panel" key={review.id||index}><strong>{review.rating} / 5 · Verified engagement review</strong><p>{review.reviewText}</p><p className="muted">{review.clientName||'Verified client'}</p><time dateTime={review.createdAt}>{new Date(review.createdAt).toLocaleDateString()}</time></article>)}
  {page&&!reviews.length&&<p>No published engagement reviews yet.</p>}{page&&<MoreRecords<PublicReview> key={epoch} path={path} firstPage={page} label="Load more client reviews" onItems={rows=>setReviews(old=>[...old,...rows.filter(row=>!row.id||!old.some(item=>item.id===row.id))])}/>}
 </section>;
}
