import Link from 'next/link';
import {notFound} from 'next/navigation';
import {ArrowLeft, ArrowUpRight, ShieldCheck} from 'lucide-react';
import DemoVideo from '@/app/components/demo-video';
import ExternalLink from '@/app/components/external-link';
import HostedDemos from '@/app/components/hosted-demos';
import Preview from '@/app/components/preview';
import ProfileAvatar from '@/app/components/profile-avatar';
import ProofEvents from '@/app/components/proof-events';
import SimilarBuilders from '@/app/components/similar-builders';
import StructuredData from '@/app/components/structured-data';
import {availabilityLabel} from '@/lib/catalog';
import {demoProjectPath} from '@/lib/demo-links';
import {profileAvatarUrl} from '@/lib/profile-avatar';
import {getProduct} from '@/lib/server';
import {showcasePricingLabel} from '@/lib/showcase-pricing';

type Props = {params:Promise<{slug:string}>};

export async function generateMetadata({params}:Props) {
  const product = await getProduct((await params).slug);
  return {
    title: product?.title ?? 'Project not found',
    description: product?.summary,
    alternates: {canonical: product ? '/products/' + product.slug : null},
    twitter: product ? {card: 'summary', title: product.title, description: product.summary} : undefined,
    openGraph: product ? {
      title: product.title, description: product.summary, type: 'website',
      images: product.imageUrl ? [product.imageUrl] : undefined,
    } : undefined,
  };
}

export default async function Detail({params}:Props) {
  const product = await getProduct((await params).slug);
  if (!product) notFound();

  const {illustrative, title, summary, builder, category, projectType, availability, bookedUntil} = product;
  const builderPath = '/builders/' + product.builderSlug;
  const inquiryPath = '/inquiry?' + new URLSearchParams({product:product.slug});
  const canInquire = product.availableForSimilarWork && availability !== 'NOT_ACCEPTING';
  const pricing = showcasePricingLabel(product);
  const hasPreviewMedia = Boolean(product.imageUrl || product.previewVideoUrl);

  return <main className="wrap project-detail" id="main">
    {!illustrative && <>
      <StructuredData data={{'@context':'https://schema.org','@type':'SoftwareApplication',
        name:title, description:summary, applicationCategory:category, creator:{'@type':'Person',name:builder}}}/>
      <ProofEvents id={product.id}/>
    </>}
    <Link href="/" className="project-detail-back"><ArrowLeft size={16} aria-hidden="true"/> Explore projects</Link>
    <header className="intro project-detail-intro">
      <div><p className="eyebrow">{category} / {projectType}</p><h1>{title}</h1><p>{summary}</p></div>
      {canInquire && <div className="project-detail-actions">
        {illustrative
          ? <Link className="button primary" href={demoProjectPath('inquiry', product.slug)}>Try a sample inquiry <ArrowUpRight size={16} aria-hidden="true"/></Link>
          : <><Link data-proof-event="build_similar_click" className="button primary" href={inquiryPath}>Build something similar</Link>
              <Link className="button" href={inquiryPath + '&requestType=NEW_BUILD'}>Hire this builder</Link></>}
      </div>}
    </header>
    {illustrative && <p className="samplebar project-detail-sample">Illustrative project · Inquiries and reports use sample data.</p>}
    <div className="detailgrid project-detail-grid">
      <div className={hasPreviewMedia ? 'detailpreview project-detail-media' : 'detailpreview'}>
        <Preview product={product} naturalAspect={hasPreviewMedia} playVideo/>
      </div>
      <aside className="panel project-builder" aria-labelledby="project-builder-name">
        <div className="project-builder-heading">
          <ProfileAvatar name={builder} src={profileAvatarUrl(product.builderAvatarUrl)}/>
          <div><h2 id="project-builder-name">{builder}</h2><p>{illustrative?'Sample builder':'Independent software builder'}</p></div>
        </div>
        <p className="status">{availabilityLabel(availability,bookedUntil)}</p>
        {!illustrative && <p><ShieldCheck size={16} aria-hidden="true"/> Product reviewed</p>}
        {!illustrative && product.reviewSummary && <p>{product.reviewSummary.reviewCount > 0
          ? <Link className="link" href={builderPath+'#client-reviews'}>{product.reviewSummary.averageRating} / 5 · {product.reviewSummary.reviewCount} verified builder {product.reviewSummary.reviewCount===1?'review':'reviews'}</Link>
          : <span className="muted">No published client reviews yet.</span>}</p>}
        {!canInquire && <p>Currently unavailable for inquiries.</p>}
        <Link className="button" href={builderPath}>View builder profile</Link>
        {!illustrative && <p className="muted">Discuss scope with the builder. Manage agreed milestones and connected payments in Delivery &amp; payments.</p>}
        <Link className="project-report-link" href={illustrative?demoProjectPath('report',product.slug):'/report?'+new URLSearchParams({product:product.id})}>
          {illustrative?'Try a sample report':'Report this project'}
        </Link>
      </aside>
      <div className="stack project-detail-body">
        {product.media && product.media.length > 1 && <div className="proofgallery">
          {product.media.slice(1).map(media=><img key={media.id} src={media.url} alt={media.alt||'Project screenshot'} loading="lazy"/>)}
        </div>}
        {product.liveUrl
          ? <ExternalLink className="button" data-proof-event="live_demo_click" href={product.liveUrl} target="_blank" rel="noopener noreferrer">
              Open live demo <ArrowUpRight size={16} aria-hidden="true"/>
            </ExternalLink>
          : !illustrative && <p className="muted">No public live demo provided.</p>}
        {!illustrative && product.liveUrl && <p className="muted">Demo health: {product.demoHealth||'UNKNOWN'}
          {product.demoCheckedAt?' · Checked '+new Date(product.demoCheckedAt).toLocaleDateString():' · Not checked yet'}. Reachability is not a safety guarantee.</p>}
        {!illustrative && <HostedDemos productId={product.id}/>}
        {product.repositoryVerified && <p>Repository evidence reviewed for this showcase.</p>}
        {!product.previewVideoUrl && product.videoUrl && <DemoVideo url={product.videoUrl}/>}
        <div className="prose">
          <h2>What this project solves</h2><p>{product.description}</p>
          {product.repositoryUrl && <ExternalLink href={product.repositoryUrl} rel="noopener noreferrer" target="_blank">
            View source repository ↗
          </ExternalLink>}
          {pricing && <p>Indicative pricing: {pricing}</p>}
          {product.pricingNote && <p>Pricing context: {product.pricingNote}</p>}
          <h2>Builder’s contribution</h2><p>{product.contribution}</p>
          <div className="tags"><span className="tag">{product.technology}</span><span className="tag">{projectType}</span></div>
        </div>
      </div>
    </div>
    <SimilarBuilders product={product}/>
  </main>;
}
