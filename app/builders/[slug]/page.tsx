import ExternalLink from '@/app/components/external-link';
import StructuredData from '@/app/components/structured-data';
import { backendOrigin, getCatalog } from '@/lib/server';
import { availabilityLabel } from '@/lib/catalog';
import { notFound } from 'next/navigation';
import Link from 'next/link';
import BuilderEvent from '@/app/components/builder-event';
import PublicReviews from '@/app/components/public-reviews';
import Preview from '@/app/components/preview';

export async function generateMetadata({params}:{params:Promise<{slug:string}>}){const {slug}=await params;const origin=await backendOrigin();let title='Builder profile',description='Discover demonstrated software work and meet its builder.';if(origin){const res=await fetch(origin+'/api/v1/builders/'+encodeURIComponent(slug),{cache:'no-store',signal:AbortSignal.timeout(15000)});if(res.ok){const p=await res.json();title=p.displayName+' — Builder';description=p.headline||p.bio?.slice(0,160)||description;}}return {title,description,alternates:{canonical:'/builders/'+slug},openGraph:{title,description,type:'profile'},twitter:{card:'summary',title,description}};}

export default async function Builder({params}: {params: Promise<{slug: string}>}) {
  const {slug} = await params;
  const origin = await backendOrigin();
  const catalog = await getCatalog(undefined, slug);
  let profile: {id?:string;displayName: string; headline: string; bio: string; availabilityStatus: string; bookedUntil?: string; githubUrl?: string; linkedinUrl?: string; websiteUrl?:string; country?:string; timeZone?:string; languages?:string;availabilityConfirmedAt?:string;responseSample?:number;responseRate?:number;medianResponseHours?:number} | undefined;
  let reviews: {rating: number; reviewText: string; createdAt: string}[] = [];
  if (origin) {
    const [response, feedback] = await Promise.all([
      fetch(origin + '/api/v1/builders/' + encodeURIComponent(slug), {cache: 'no-store', signal: AbortSignal.timeout(15000)}),
      fetch(origin + '/api/v1/builders/' + encodeURIComponent(slug) + '/reviews', {cache: 'no-store', signal: AbortSignal.timeout(15000)})
    ]);
    if (response.ok) profile = await response.json();
    else if(response.status !== 404) throw Error('Builder profile is temporarily unavailable.');
    if (feedback.ok) reviews = (await feedback.json()).items;
  } else {
    const p = catalog.items[0];
    if (p) profile = {displayName: p.builder, headline: 'Independent software builder', bio: 'Builds thoughtful software for everyday business problems.', availabilityStatus: p.availability};
  }
  if (!profile) notFound();
  return <main className="wrap" id="main">{!catalog.preview&&<StructuredData data={{'@context':'https://schema.org','@type':'Person',name:profile.displayName,description:profile.headline}}/>}{profile.id&&<BuilderEvent id={profile.id}/>}<Link href="/" className="link">← Explore projects</Link>
    <div className="intro" style={{marginTop:30}}><div><span className="eyebrow">Builder profile</span><h1>{profile.displayName}</h1><p>{profile.headline}</p></div><p>{availabilityLabel(profile.availabilityStatus, profile.bookedUntil)}</p></div>
    <p className="prose">{profile.bio}</p><p className="muted">Availability last confirmed: {profile.availabilityConfirmedAt?new Date(profile.availabilityConfirmedAt).toLocaleDateString():'Not yet confirmed'}</p>{profile.responseRate!==undefined&&<p className="panel">Responded to {Math.round(profile.responseRate)}% of {profile.responseSample} qualified inquiries{profile.medianResponseHours!=null&&<> · Typical first response: {Math.round(profile.medianResponseHours)} hours</>}. Inquiries have at least 48 hours of observation; confirmed spam is excluded.</p>}
    <p className="muted">{[profile.country,profile.timeZone,profile.languages].filter(Boolean).join(" · ")}</p><div className="policy-links">{profile.websiteUrl&&<ExternalLink className="link" href={profile.websiteUrl} target="_blank" rel="noopener noreferrer">Website ↗</ExternalLink>}{profile.githubUrl && <ExternalLink className="link" href={profile.githubUrl} target="_blank" rel="noopener noreferrer">GitHub ↗</ExternalLink>}{profile.linkedinUrl && <ExternalLink className="link" href={profile.linkedinUrl} target="_blank" rel="noopener noreferrer">LinkedIn ↗</ExternalLink>}</div>
    {catalog.preview && <div className="samplebar">Illustrative builder profile.</div>}
    <h2 style={{margin:'30px 0'}}>Selected work</h2><div className="grid">{catalog.items.map(p => <article key={p.id}><Link href={'/products/' + p.slug} className="thumbnail"><Preview product={p}/></Link><h3 style={{marginTop:15}}><Link href={'/products/' + p.slug}>{p.title}</Link></h3><p className="summary">{p.summary}</p></article>)}</div>
    {!catalog.items.length && <p>No active public showcases at the moment.</p>}
    <PublicReviews slug={slug} initial={reviews}/>
  </main>;
}
