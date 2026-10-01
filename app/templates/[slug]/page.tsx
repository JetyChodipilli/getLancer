import {TemplateDetailView} from '@/app/components/template-marketplace';
import {backendOrigin} from '@/lib/server';
import {previewTemplateDetail} from '@/lib/template-preview';
import type {SourceTemplate} from '@/lib/templates';
import {notFound} from 'next/navigation';
async function sourceTemplate(slug:string){
 const origin=await backendOrigin();let template:SourceTemplate|undefined;
 if(!origin)template=previewTemplateDetail(slug);else{const response=await fetch(origin+'/api/v1/templates/'+encodeURIComponent(slug),{cache:'no-store',signal:AbortSignal.timeout(15000)});if(response.status===404)notFound();if(!response.ok)throw Error('This source release is temporarily unavailable. Please try again.');template=await response.json();}
 if(!template)notFound();return {template,preview:!origin};
}
export async function generateMetadata({params}:{params:Promise<{slug:string}>}){const {template,preview}=await sourceTemplate((await params).slug);return {title:template.title+' · Source template',description:template.summary,alternates:{canonical:preview?null:'/templates/'+template.slug},robots:preview?{index:false,follow:false}:undefined};}
export default async function Page({params}:{params:Promise<{slug:string}>}){
 const {template,preview}=await sourceTemplate((await params).slug);return <TemplateDetailView template={template} preview={preview}/>;
}
