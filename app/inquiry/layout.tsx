import type {Metadata} from 'next';
export const metadata:Metadata={robots:{index:false,follow:false},alternates:{canonical:null}};
export default function PrivateLayout({children}:{children:React.ReactNode}){return children;}
