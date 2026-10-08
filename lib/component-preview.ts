import {safeHostedUrl} from './hosting';

/** App-owned, script-free outer document restricts the opaque child to its reviewed origin. */
export function componentPreviewDocument(value:unknown):string|undefined{
 const url=safeHostedUrl(value);if(!url)return undefined;
 const escape=(text:string)=>text.replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;').replaceAll('>','&gt;');
 const origin=new URL(url).origin;
 return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; frame-src ${escape(origin)}; base-uri 'none'; form-action 'none'"><style>html,body,iframe{width:100%;height:100%;margin:0;border:0;display:block}</style></head><body><iframe title="Reviewed component content" sandbox="allow-scripts allow-forms" referrerpolicy="no-referrer" src="${escape(url)}"></iframe></body></html>`;
}
