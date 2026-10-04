import {notFound} from 'next/navigation';
import {componentDetail} from '@/lib/component-server';
import ComponentDetailView from '../component-library';
export async function generateMetadata({params}:{params:Promise<{slug:string}>}){const {item}=await componentDetail((await params).slug);return {title:item?.title||'Component',description:item?.summary};}
export default async function Component({params}:{params:Promise<{slug:string}>}){const {item,preview}=await componentDetail((await params).slug);if(!item)notFound();return <ComponentDetailView item={item} preview={preview}/>;}
