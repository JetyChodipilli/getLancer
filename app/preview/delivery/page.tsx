import DeliveryWorkspace from '@/app/components/delivery-workspace';
import {backendOrigin} from '@/lib/server';
import {redirect} from 'next/navigation';
export const metadata={title:'Delivery preview',robots:{index:false,follow:false}};
export default async function Page(){if(await backendOrigin())redirect('/workspace/delivery');return <DeliveryWorkspace demo/>;}
