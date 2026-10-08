export class ApiError extends Error {
 constructor(message:string,public code:string,public status:number,public requestId?:string,public fieldErrors:Record<string,string>={}){super(message+(requestId?' (reference '+requestId+')':''));this.name='ApiError';}
}
export async function api(path:string,init:RequestInit={}){const r=await fetch('/api/v1'+path,{...init,credentials:'same-origin',headers:{...(init.body instanceof FormData?{}:{'Content-Type':'application/json'}),'X-Requested-With':'getlancer',...init.headers}});const b=await r.json().catch(()=>({}));if(!r.ok)throw new ApiError(b.error?.message||'We could not complete that request. Please try again.',b.error?.code||'REQUEST_FAILED',r.status,b.error?.requestId,b.error?.fieldErrors||{});return b;}
