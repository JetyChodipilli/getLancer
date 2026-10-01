import Link from 'next/link';
export default function Footer({preview=false}:{preview?:boolean}) {
 return <footer className="footer site-footer"><div className="market-footer">
  <Link className="brand footer-logo" href="/" aria-label="getLancer home"><img className="brand-logo" src="/brand/getlancer-transparent.png" alt="getLancer" width={767} height={325}/></Link>
  <p>Real work. Human connections.</p>
  <nav aria-label="Footer navigation"><Link href="/">Explore</Link><Link href={preview?'/preview/workspace':'/workspace'}>For builders</Link><Link href="/templates">Source templates</Link><Link href="/teams">Teams & studios</Link><Link href="/report">Support</Link><Link href="/policies#terms">Terms</Link><Link href="/policies#privacy">Privacy</Link></nav>
 </div><div className="market-footer-legal"><span>© {new Date().getFullYear()} getLancer</span><Link href="/how-it-works">How getLancer works</Link></div></footer>;
}
