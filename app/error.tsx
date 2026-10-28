'use client';
import Link from 'next/link';
import {ArrowRight,RotateCcw,TriangleAlert} from 'lucide-react';
export default function ErrorPage({error,reset}:{error:Error & {digest?:string};reset:()=>void}){
 return <main className="wrap paper-error-page" id="main"><span className="paper-error-symbol" aria-hidden="true"><TriangleAlert size={26}/></span><span className="eyebrow">A pause, not the end</span><h1>We couldn’t load this page.</h1><p>The service may be temporarily unavailable. Try loading it again, or return to the project collection.</p>{error.digest&&<p className="paper-error-reference">Support reference: {error.digest}</p>}<div className="paper-error-actions"><button className="button primary" onClick={reset}><RotateCcw size={17} aria-hidden="true"/>Try again</button><Link className="button" href="/">Explore projects <ArrowRight size={17} aria-hidden="true"/></Link></div></main>;
}
