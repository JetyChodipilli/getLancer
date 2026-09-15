import Explore from './components/explore';
import { getCatalog } from '@/lib/server';
import { parseFilters } from '@/lib/discovery';
export async function generateMetadata({searchParams}:{searchParams:Promise<Record<string,string|string[]|undefined>>}){const params=await searchParams;return Object.keys(params).length?{robots:{index:false,follow:true},alternates:{canonical:'/'}}:{};}
export default async function Home({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const params = new URLSearchParams();
  Object.entries(await searchParams).forEach(([k, v]) => { if (typeof v === 'string') params.set(k, v); });
  return <Explore {...await getCatalog(parseFilters(params))} />;
}
