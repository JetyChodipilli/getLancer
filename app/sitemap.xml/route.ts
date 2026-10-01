import {seoSettings} from '@/lib/seo';
import {backendOrigin} from '@/lib/server';
const xml=(s:string)=>s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
export async function GET(){
  const {base,index}=await seoSettings();const origin=await backendOrigin();const urls=new Set<string>();
  if(index&&base&&origin){
    urls.add(base+'/');urls.add(base+'/how-it-works');
    const counts={categories:new Map<string,number>(),technologies:new Map<string,number>()};
    // Standard sitemaps support 50,000 URLs. A future sitemap index handles larger catalogues.
    for(let page=0;page<200;page++){
      const response=await fetch(origin+'/api/v1/products?size=100&page='+page,{cache:'no-store',signal:AbortSignal.timeout(15000)});
      if(!response.ok)return new Response('Sitemap temporarily unavailable',{status:503});
      const data=await response.json();
      for(const p of data.items){counts.categories.set(p.category,(counts.categories.get(p.category)||0)+1);for(const tag of p.technology.split(','))counts.technologies.set(tag.trim(),(counts.technologies.get(tag.trim())||0)+1);urls.add(base+'/products/'+encodeURIComponent(p.slug));urls.add(base+'/builders/'+encodeURIComponent(p.builderSlug));}
      if(page+1>=data.totalPages)break;
    }
    for(const kind of ['categories','technologies'] as const){const r=await fetch(origin+'/api/v1/'+kind,{cache:'no-store',signal:AbortSignal.timeout(10000)});if(!r.ok)continue;for(const item of (await r.json()).items){if((counts[kind].get(item.name)||0)>=3)urls.add(base+(kind==='categories'?'/solutions/':'/technologies/')+encodeURIComponent(item.slug));}}
    for(const kind of ['teams','templates']){
      for(let page=0;page<100&&urls.size<49000;page++){
        const response=await fetch(origin+'/api/v1/'+kind+'?size=100&page='+page,{cache:'no-store',signal:AbortSignal.timeout(15000)});
        if(!response.ok)return new Response('Sitemap temporarily unavailable',{status:503});
        const data=await response.json();
        for(const item of data.items){const key=kind==='teams'?item.id:item.slug;if(key)urls.add(base+'/'+kind+'/'+encodeURIComponent(key));}
        if(page+1>=data.totalPages)break;
      }
    }
  }
  return new Response('<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">'+[...urls].map(url=>'<url><loc>'+xml(url)+'</loc></url>').join('')+'</urlset>',{headers:{'Content-Type':'application/xml; charset=utf-8','Cache-Control':'public, max-age=300'}});
}
