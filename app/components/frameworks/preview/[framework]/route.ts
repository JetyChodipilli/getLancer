import material from '../../material.json';
import {isFrameworkId,frameworkPreviewHeaders} from '../../security';
export async function GET(_request:Request,{params}:{params:Promise<{framework:string}>}){
 const {framework}=await params,headers=frameworkPreviewHeaders(framework);
 if(!isFrameworkId(framework)||!headers)return new Response('Native preview unavailable.',{status:404,headers:{'Content-Type':'text/plain; charset=utf-8','Cache-Control':'no-store'}});
 const entry=material.items.find(item=>item.id===framework);
 if(!entry)return new Response('Native preview unavailable.',{status:404});
 return new Response(entry.html,{headers});
}
