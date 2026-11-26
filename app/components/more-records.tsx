
'use client';

import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';

import {api} from '@/lib/api';

export type PageInfo={
  page:number;
  hasMore:boolean;
  size?:number;
};

type Request=(
  path:string,
  init?:RequestInit
)=>Promise<unknown>;

function record(value:unknown):Record<string,unknown>{
  return (
    value!==null&&
    typeof value==='object'&&
    !Array.isArray(value)
  )
    ?value as Record<string,unknown>
    :{};
}

// Existing API consumers have untyped records;
// callers can supply their record type.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export default function MoreRecords<T=any>({
  path,
  onItems,
  firstPage,
  request=api,
  label='Load more',
  onPage,
  onError,
  collection,
}:{
  path:string;
  onItems:(items:T[])=>void;
  firstPage?:PageInfo;
  request?:Request;
  label?:string;
  onPage?:(page:PageInfo)=>void;
  onError?:(error:unknown)=>void;
  collection?:string;
}){

  const [page,setPage]=useState(firstPage?.page??0);
  const [more,setMore]=useState(firstPage?.hasMore??true);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');

  const inFlight=useRef(false);
  const generation=useRef(0);
  const initial=useRef(firstPage);

  const button=useRef<HTMLButtonElement>(null);
  const status=useRef<HTMLParagraphElement>(null);

  const focusTarget=useRef<
    'button'|'status'|null
  >(null);

  const invalidateGeneration=useCallback(
    (current:number)=>{
      if(generation.current===current){
        generation.current++;
      }
    },
    []
  );

  useEffect(()=>{
    initial.current=firstPage;
  },[firstPage]);

  useEffect(()=>{
    const target=focusTarget.current;

    if(!busy&&target){
      focusTarget.current=null;

      if(
        document.activeElement===button.current||
        document.activeElement===document.body
      ){
        (
          target==='button'
            ?button.current
            :status.current
        )?.focus({
          preventScroll:true
        });
      }
    }
  },[busy,error,notice,more]);

  useEffect(()=>{
    const current=++generation.current;

    inFlight.current=false;
    focusTarget.current=null;

    setPage(initial.current?.page??0);
    setMore(initial.current?.hasMore??true);

    setBusy(false);
    setError('');
    setNotice('');

    return()=>{
      invalidateGeneration(current);
    };
  },[
    path,
    request,
    collection,
    invalidateGeneration
  ]);

  async function load(){
    if(inFlight.current||!more)return;

    inFlight.current=true;

    const run=generation.current;
    const next=page+1;

    const startedWithFocus=
      document.activeElement===button.current;

    setBusy(true);
    setError('');
    setNotice('');

    const [pathname,query='']=path.split('?');

    const params=new URLSearchParams(query);

    params.set('page',String(next));

    if(initial.current?.size){
      params.set(
        'size',
        String(initial.current.size)
      );
    }

    try{
      const response=record(
        await request(pathname+'?'+params)
      );

      const data=collection
        ?{
          ...record(
            record(response.pages)[collection]
          ),
          items:response[collection]
        }
        :response;

      if(run!==generation.current)return;

      if(
        !Array.isArray(data?.items)||
        (
          data.page!==undefined&&
          data.page!==next
        )
      ){
        throw new Error(
          'The next page could not be verified. Please retry.'
        );
      }

      focusTarget.current=
        startedWithFocus&&
        (
          document.activeElement===button.current||
          document.activeElement===document.body
        )
          ?data.hasMore===true
            ?'button'
            :'status'
          :null;

      onItems(data.items as T[]);

      setPage(next);
      setMore(data.hasMore===true);

      onPage?.({
        page:next,
        hasMore:data.hasMore===true,
        size:typeof data.size==='number'
          ?data.size
          :initial.current?.size
      });

      setNotice(
        data.items.length+' more records loaded.'
      );

    }catch(e){
      if(run===generation.current){
        focusTarget.current=
          startedWithFocus&&
          (
            document.activeElement===button.current||
            document.activeElement===document.body
          )
            ?'button'
            :null;

        setError(
          e instanceof Error
            ?e.message
            :'Unable to load more records. Please retry.'
        );

        onError?.(e);
      }

    }finally{
      if(run===generation.current){
        inFlight.current=false;

        setBusy(false);
      }
    }
  }

  return (
    <div
      style={{marginTop:20}}
      aria-busy={busy}
    >
      {more&&(
        <button
          ref={button}
          type="button"
          className="button"
          disabled={busy}
          onClick={load}
        >
          {busy?'Loading…':label}
        </button>
      )}

      {notice&&(
        <p
          ref={status}
          tabIndex={-1}
          role="status"
        >
          {notice}
        </p>
      )}

      {error&&(
        <p
          role="alert"
          className="error"
        >
          {error}
        </p>
      )}
    </div>
  );
}
