import educationSeeds from '../backend/src/main/resources/catalog/education.json' with {type:'json'};
import type {EducationRelease,EducationOffer} from './education';
import componentSources from '../backend/src/main/resources/catalog/components.json' with { type: 'json' };
import { backendOrigin } from './server';
import { componentSeeds, seedCatalog, type ComponentEntry, type ComponentPage } from './components';
import { examples, type Product } from './catalog';
export type Education = { category: string; language: string; problem: string; outcome: string; prerequisites: string; contribution: string; executionMode: string; mode: string; status: string; institution?: string; academicYear?: string; branch?: string; shareAcademicDetails: boolean; reviewReason?: string; difficulty?:string;release?:EducationRelease;previewPackageUrl?:string|null };
export type CollegeProject = Product & { education: Education };
export const sampleCollegeProjects:CollegeProject[]=educationSeeds.map(seed=>{const product=examples.find(item=>item.slug===seed.productSlug)!;const release=seed.release as unknown as EducationRelease,s=release.snapshot;return {...product,title:seed.title,summary:seed.summary,education:{category:s.category,language:s.language||product.technology,problem:s.problem||s.summary,outcome:s.outcome||s.summary,prerequisites:s.package.prerequisites.join('\n'),contribution:s.contribution,executionMode:s.demoMode,mode:s.mode,status:release.status,difficulty:s.difficulty,shareAcademicDetails:false,release,previewPackageUrl:seed.previewPackageUrl}};});
async function read<T>(path: string): Promise<T | undefined> { const origin = await backendOrigin(); if (!origin) return undefined; const res = await fetch(origin + '/api/v1' + path, { cache: 'no-store', signal: AbortSignal.timeout(15000) }); if (res.status === 404) return undefined; if (!res.ok) throw Error('This catalogue is temporarily unavailable. Please try again.'); return res.json(); }
export async function componentCatalog(params: URLSearchParams) { const connected = !!(await backendOrigin()); const data = connected ? await read<ComponentPage>('/components?' + params) : (params.get('builder') ? { items: [], totalItems: 0, hasMore: false, page: 0 } : seedCatalog(params.get('q') || '', params.get('category') || '', params.get('kind') || 'FRONTEND', Number(params.get('page') || 0))); if (!data) throw Error('Component catalogue unavailable.'); return { data, preview: !connected }; }
export async function componentDetail(slug:string){
 const connected=!!(await backendOrigin());const item:ComponentEntry|undefined=connected?await read<ComponentEntry>('/components/'+encodeURIComponent(slug)):componentSources.find(c=>c.slug===slug);
 if(connected&&item?.uploaded){const state=await read<{available:boolean;url?:string;expiresAt?:string}>('/components/'+encodeURIComponent(slug)+'/preview');if(state?.available){item.previewUrl=state.url;item.previewExpiresAt=state.expiresAt;}}
 return {item,preview:!connected};
}

export async function collegeCatalog(params: URLSearchParams) {
  const connected = !!(await backendOrigin());
  const filtered = params.get('builder') ? [] : sampleCollegeProjects.filter(p => (!params.get('category') || p.education.category === params.get('category')) && (!params.get('language') || p.education.language.toLowerCase().includes(params.get('language')!.toLowerCase())) && (!params.get('q') || (p.title + ' ' + p.education.problem).toLowerCase().includes(params.get('q')!.toLowerCase())));
  const candidates=filtered.filter(p=>(!params.get('mode')||p.education.mode===params.get('mode'))&&(!params.get('difficulty')||p.education.difficulty===params.get('difficulty')));const page = Number(params.get('page') || 0);
  const data = connected ? await read<{ items: CollegeProject[]; totalItems: number; hasMore: boolean }>('/college-projects?' + params) : { items: candidates.slice(page * 12, (page + 1) * 12), totalItems: candidates.length, hasMore: (page + 1) * 12 < candidates.length };
  if (!data) throw Error('College project catalogue unavailable.'); return { data, preview: !connected };
}
export async function collegeDetail(slug: string) { const connected = !!(await backendOrigin()); return { item: connected ? await read<CollegeProject>('/college-projects/' + encodeURIComponent(slug)) : sampleCollegeProjects.find(p => p.slug === slug), preview: !connected }; }

export async function collegeReleaseOffer(id:string):Promise<EducationOffer|undefined>{if(await backendOrigin())return read<EducationOffer>('/education-releases/'+encodeURIComponent(id)+'/source-offer');const project=sampleCollegeProjects.find(p=>p.education.release?.id===id),release=project?.education.release;if(!release)return undefined;return {release,package:release.snapshot.sourceBinding,available:release.snapshot.mode==='FREE',checkoutAvailable:false,paidEnabled:false,reason:release.snapshot.mode==='PAID'?'Illustrative paid source. Merchant activation and checkout are unavailable.':release.snapshot.mode==='SHOWCASE'?'Showcase only; no source package is offered.':undefined};}
