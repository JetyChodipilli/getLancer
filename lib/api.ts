export class ApiError extends Error {
 constructor(message:string,public code:string,public status:number,public requestId?:string,public fieldErrors:Record<string,string>={}){
  super(message+(requestId?' (reference '+requestId+')':''));
  this.name='ApiError';
 }
}

function fallbackMessage(status:number,mutation:boolean){
 if(status===401)return 'Your session has ended. Sign in to continue.';
 if(status===403)return 'You do not have permission to complete this action.';
 if(status===404)return 'This record is no longer available. Refresh the page to continue.';
 if(status===409)return 'This record has changed. Refresh its current status before trying again.';
 if(status===413)return 'This upload is too large. Choose a smaller file and try again.';
 if(status===429)return 'Too many requests. Please wait a moment before trying again.';
 if(status>=500&&mutation)return 'We could not confirm this change. Check the current record before retrying.';
 if(status>=500)return 'This service is temporarily unavailable. Please try loading it again.';
 return 'We could not complete that request. Check your details and try again.';
}
function record(value:unknown):value is Record<string,unknown>{return Boolean(value)&&typeof value==='object'&&!Array.isArray(value);}
function text(value:unknown,max:number){return typeof value==='string'&&value.trim()?value.trim().slice(0,max):undefined;}

export async function api(path:string,init:RequestInit={}){
 const headers=new Headers(init.headers);
 if(!(init.body instanceof FormData)&&!headers.has('Content-Type'))headers.set('Content-Type','application/json');
 if(!headers.has('Accept'))headers.set('Accept','application/json');
 if(!headers.has('X-Requested-With'))headers.set('X-Requested-With','getlancer');
 const mutation=!['GET','HEAD','OPTIONS'].includes((init.method||'GET').toUpperCase());
 let response:Response;
 try{
  response=await fetch('/api/v1'+path,{...init,credentials:'same-origin',headers});
 }catch(error){
  // Navigation and intentionally cancelled reads must stay silent to callers.
  if(init.signal?.aborted||(error instanceof Error&&error.name==='AbortError'))throw error;
  throw new ApiError(mutation?'We could not confirm this change. Check your connection and the current record before retrying.':'We could not connect. Check your connection and try loading this page again.','NETWORK_ERROR',0);
 }
 if(response.ok&&[204,205].includes(response.status))return {};
 const headerReference=text(response.headers.get('X-Request-ID'),200);
 let body;
 try{body=await response.json();}
 catch(error){
  if(init.signal?.aborted||(error instanceof Error&&error.name==='AbortError'))throw error;
  if(response.ok)throw new ApiError('The server response could not be confirmed. Refresh the saved record before retrying.','INVALID_RESPONSE',502,headerReference);
  throw new ApiError(fallbackMessage(response.status,mutation),'REQUEST_FAILED',response.status,headerReference);
 }
 if(!response.ok){
  const detail=record(body)&&record(body.error)?body.error:{};
  const fields=record(detail.fieldErrors)?Object.fromEntries(Object.entries(detail.fieldErrors).filter(([key,value])=>key.length<=100&&typeof value==='string').slice(0,30).map(([key,value])=>[key,(value as string).slice(0,1000)])):{};
  // Domain failures (for example unconfirmed payments) carry essential recovery steps.
  // Unexpected/internal server failures must never expose diagnostic details.
  const internal=response.status===500||detail.code==='INTERNAL_ERROR'||(response.status>=500&&!text(detail.code,100));
  const message=internal?fallbackMessage(response.status,mutation):text(detail.message,1500)||fallbackMessage(response.status,mutation);
  throw new ApiError(message,text(detail.code,100)||'REQUEST_FAILED',response.status,text(detail.requestId,200)||headerReference,internal?{}:fields as Record<string,string>);
 }
 if(body===null||typeof body!=='object')throw new ApiError('The server response could not be confirmed. Refresh the saved record before retrying.','INVALID_RESPONSE',502,headerReference);
 return body;
}
