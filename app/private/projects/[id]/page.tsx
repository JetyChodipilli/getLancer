import PrivatePreview from '@/app/components/private-preview';
export const metadata={title:'Private project preview',robots:{index:false,follow:false}};
export default async function Page({params}:{params:Promise<{id:string}>}){const {id}=await params;return <PrivatePreview id={id}/>;}
