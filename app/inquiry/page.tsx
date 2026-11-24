import Link from 'next/link';
import {ArrowRight} from 'lucide-react';
import {InquiryForm} from '../components/forms';
import {backendOrigin,getProduct} from '@/lib/server';

export default async function Page({searchParams}:{searchParams:Promise<{product?:string|string[]}>}) {
 const {product}=await searchParams;
 const initialProduct=typeof product==='string'&&product?await getProduct(product):undefined;
 if(!initialProduct)return <main id="main" className="wrap structured-form-page">
  <section className="panel structured-report">
   <header className="structured-form-header">
    <h1>{product?'Project unavailable':'Choose a project to inquire about'}</h1>
    <p>{product?'This project is no longer available. Explore the collection to find another builder.':'Open a project and choose an inquiry option to contact its builder.'}</p>
   </header>
   <Link className="button primary" href="/#project-collection">Explore projects <ArrowRight size={16} aria-hidden="true"/></Link>
  </section>
 </main>;
 return <InquiryForm initialProduct={initialProduct} preview={!(await backendOrigin())}/>;
}
