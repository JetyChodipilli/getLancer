import Link from 'next/link';
import Brand from './brand';
export default function Footer({preview=false}:{preview?:boolean}) {
 const workspace=preview?'/preview/workspace':'/workspace';
 const business=preview?'/preview/business':'/workspace/business';
 return <footer className="footer site-footer paper-footer">
  <div className="paper-footer-inner">
   <div className="paper-footer-top">
    <div className="paper-footer-brand">
     <Link href="/" aria-label="getLancer home"><Brand/></Link>
     <p>The craft behind<br/><span>the code.</span></p>
     <span className="paper-footer-note">Working software. Good company.</span>
    </div>
    <nav aria-labelledby="footer-discover"><h2 id="footer-discover">Discover</h2><Link href="/">Explore projects</Link><Link href="/components">Free components</Link><Link href="/college-projects">College projects</Link><Link href="/labs">Backend labs</Link><Link href="/labs/iot">IoT simulation</Link><Link href="/components/frameworks">Framework previews</Link><Link href="/templates">Source templates</Link><Link href="/teams">Teams & studios</Link><Link href="/how-it-works">How it works</Link></nav>
    <nav aria-labelledby="footer-work"><h2 id="footer-work">Make it happen</h2><Link href={workspace}>Builder workspace</Link><Link href={business}>Business workspace</Link><Link href={business}>Start a project request</Link><Link href="/saved">Saved projects</Link></nav>
    <nav aria-labelledby="footer-help"><h2 id="footer-help">Here to help</h2><Link href="/report">Support & reporting</Link><Link href="/policies#terms">Terms of use</Link><Link href="/policies#privacy">Privacy policy</Link></nav>
   </div>
   <div className="paper-footer-bottom"><span>© {new Date().getFullYear()} getLancer</span><span>Code builds opportunity.</span><Link href="/how-it-works">Real projects. Real talent.</Link></div>
  </div>
 </footer>;
}
