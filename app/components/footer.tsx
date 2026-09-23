import Link from 'next/link';
import { ArrowUpRight } from 'lucide-react';

export default function Footer({ preview = false }: { preview?: boolean }) {
 const workspace = preview ? '/preview/workspace' : '/workspace';
 return <footer className="footer site-footer">
  <div className="footer-main">
   <div className="footer-brand"><Link href="/" aria-label="getLancer home"><img src="/brand/getlancer-transparent.png" alt="getLancer" width={154} height={90}/></Link><p>Real software.<br/><strong>People worth working with.</strong></p><span>Discover the work. Meet the builder.</span></div>
   <nav aria-label="Explore links"><h2>Explore</h2><Link href="/">Discover projects <ArrowUpRight size={14}/></Link><Link href="/saved">Saved projects</Link><Link href="/how-it-works">How it works</Link></nav>
   <nav aria-label="Workspace links"><h2>Your workspace</h2><Link href={workspace}>{preview?'Explore the demo':'My workspace'}</Link><Link href="/signup">Create an account</Link><Link href="/login">Log in</Link></nav>
   <nav aria-label="Trust links"><h2>Trust &amp; support</h2><Link href="/policies">Policies &amp; privacy</Link><Link href="/report">Report a concern</Link><Link href="/how-it-works">Showcase guidelines</Link></nav>
  </div>
  <div className="footer-bottom"><span>© {new Date().getFullYear()} getLancer</span><span>Built around what you build.</span><Link href="#main">Back to top ↑</Link></div>
 </footer>;
}
