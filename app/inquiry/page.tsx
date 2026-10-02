import {InquiryForm} from '../components/forms';
import {backendOrigin,getProduct} from '@/lib/server';
export default async function Page({searchParams}:{searchParams:Promise<{product?:string}>}) {
 const {product}=await searchParams;
 const initialProduct=product?await getProduct(product):undefined;
 return <InquiryForm initialProduct={initialProduct} preview={!(await backendOrigin())}/>;
}
