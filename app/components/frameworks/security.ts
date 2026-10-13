import material from './material.json';
export type FrameworkId='vue'|'svelte'|'angular';
export function isFrameworkId(value:unknown):value is FrameworkId{return value==='vue'||value==='svelte'||value==='angular';}
/** Exact reviewed material only: callers cannot supply a CSP, URL, filename or hash. */
export function frameworkPreviewPolicy(value:unknown):string|undefined{
 if(!isFrameworkId(value))return undefined;
 const item=material.items.find(entry=>entry.id===value);
 if(!item||!/^'sha256-[A-Za-z0-9+/]{43}='$/.test(item.scriptHash)||!/^'sha256-[A-Za-z0-9+/]{43}='$/.test(item.styleHash))return undefined;
 const policy=`default-src 'none'; script-src ${item.scriptHash}; script-src-attr 'none'; style-src ${item.styleHash}; style-src-attr 'none'; img-src 'none'; font-src 'none'; connect-src 'none'; frame-src 'none'; worker-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'self'; sandbox allow-scripts`;
 return item.policy===policy?policy:undefined;
}
export function frameworkPreviewHeaders(value:unknown):Headers|undefined{
 const policy=frameworkPreviewPolicy(value);if(!policy)return undefined;
 return new Headers({'Content-Type':'text/html; charset=utf-8','Content-Security-Policy':policy,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Permissions-Policy':'camera=(), microphone=(), geolocation=()','Cross-Origin-Resource-Policy':'same-origin'});
}
