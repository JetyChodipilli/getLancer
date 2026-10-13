import material from '../../material.json';
import {isFrameworkId} from '../../security';
export async function GET(_request:Request,{params}:{params:Promise<{framework:string}>}){
 const {framework}=await params;
 if(!isFrameworkId(framework))return new Response('Source unavailable.',{status:404,headers:{'Content-Type':'text/plain; charset=utf-8','Cache-Control':'no-store'}});
 const entry=material.items.find(item=>item.id===framework);
 if(!entry)return new Response('Source unavailable.',{status:404});
 const bytes=Uint8Array.from(atob(entry.archiveBase64),character=>character.charCodeAt(0));
 return new Response(bytes,{headers:{'Content-Type':'application/gzip','Content-Disposition':`attachment; filename="getlancer-${framework}-original-1.0.0.tar.gz"`,'Content-Length':String(bytes.byteLength),'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','X-Source-SHA256':entry.sourceSha256,'X-Archive-SHA256':entry.archiveSha256}});
}
