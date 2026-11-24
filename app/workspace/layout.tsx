import type {Metadata} from 'next';
import type {ReactNode} from 'react';
import {WorkspaceModerationTotals} from '../components/workspace-overview';
export const metadata:Metadata={robots:{index:false,follow:false},alternates:{canonical:null}};
export default function PrivateLayout({children}:{children:ReactNode}){
 return <WorkspaceModerationTotals>{children}</WorkspaceModerationTotals>;
}
