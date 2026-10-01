import {TemplateCatalog} from '@/app/components/template-marketplace';
import {backendOrigin} from '@/lib/server';
import {previewTemplateCatalog} from '@/lib/template-preview';
import {templateFilterParams,type TemplateFilters,type TemplatePage,type SourceTemplate} from '@/lib/templates';
export const metadata={title:'Source templates',description:'Reviewed source packages, versioned releases and clear licenses from approved getLancer builders.'};
export default async function Page({searchParams}:{searchParams:Promise<Record<string,string|string[]|undefined>>}){
 const query=await searchParams,value=(key:string)=>typeof query[key]==='string'?String(query[key]):'',filters:TemplateFilters={q:value('q').slice(0,100),category:value('category').slice(0,80),technology:value('technology').slice(0,80)},page=Math.max(0,Math.min(10000,Number.parseInt(value('page')||'0',10)||0)),origin=await backendOrigin();
 let records:TemplatePage<SourceTemplate>;
 if(!origin)records=previewTemplateCatalog(filters,page);else{const params=templateFilterParams(filters,page);params.set('size','12');const response=await fetch(origin+'/api/v1/templates?'+params,{cache:'no-store',signal:AbortSignal.timeout(15000)});if(!response.ok)throw Error('Source templates are temporarily unavailable. Please try again.');records=await response.json();}
 return <TemplateCatalog {...records} filters={filters} preview={!origin}/>;
}
