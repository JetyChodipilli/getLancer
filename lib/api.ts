export class ApiError extends Error {
 constructor(message:string,public code:string,public status:number,public requestId?:string,public fieldErrors:Record<string,string>={}){super(message+(requestId?' (reference '+requestId+')':''));this.name='ApiError';}
}
export async function api(path:string,init:RequestInit={}){
 const headers=new Headers(init.headers);
 if(!(init.body instanceof FormData)&&!headers.has('Content-Type'))headers.set('Content-Type','application/json');
 if(!headers.has('X-Requested-With'))headers.set('X-Requested-With','getlancer');
 const response=await fetch('/api/v1'+path,{...init,credentials:'same-origin',headers});
 if(response.ok&&[204,205].includes(response.status))return {};
 let body;
 try{body=await response.json();}
 catch{
  if(response.ok)throw new ApiError('The server response could not be confirmed. Refresh the saved record before retrying.','INVALID_RESPONSE',502);
  throw new ApiError('We could not complete that request. Please try again.','REQUEST_FAILED',response.status);
 }
 if(!response.ok){
  const detail=body&&typeof body==='object'&&body.error&&typeof body.error==='object'?body.error:{};
  const fields=detail.fieldErrors&&typeof detail.fieldErrors==='object'&&!Array.isArray(detail.fieldErrors)?Object.fromEntries(Object.entries(detail.fieldErrors).filter(([,value])=>typeof value==='string')):{};
  throw new ApiError(typeof detail.message==='string'&&detail.message?detail.message:'We could not complete that request. Please try again.',typeof detail.code==='string'&&detail.code?detail.code:'REQUEST_FAILED',response.status,typeof detail.requestId==='string'?detail.requestId:undefined,fields as Record<string,string>);
 }
 return body;
}
