import TaxonomyGallery from '@/app/components/taxonomy-gallery';
import {taxonomyPage} from '@/lib/taxonomy-page';
import {seoSettings} from '@/lib/seo';
import {notFound} from 'next/navigation';
type Props={params:Promise<{slug:string}>;searchParams:Promise<{page?:string}>};
export async function generateMetadata({params}:Props){const {slug}=await params;const data=await taxonomyPage('categories',slug);const settings=await seoSettings();const title=data?data.item.name+' — Real projects and builders':'Projects';return {title,description:'Explore approved software projects and the builders behind them.',alternates:{canonical:'/solutions/'+slug},robots:{index:!!data&&settings.index&&data.catalog.totalItems>=3,follow:settings.index}}}
export default async function Page({params,searchParams}:Props){const {slug}=await params;const raw=(await searchParams).page||'0';if(!/^\d+$/.test(raw)||Number(raw)>100000)notFound();return <TaxonomyGallery kind='categories' slug={slug} page={Number(raw)}/>}
