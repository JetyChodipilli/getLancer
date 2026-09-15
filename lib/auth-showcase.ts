import {backendOrigin} from './server';
import {examples,type Product} from './catalog';

// Discovery being slow/unavailable must not prevent access to authentication.
export async function authShowcase():Promise<Product[]>{
 const origin=await backendOrigin();
 if(!origin)return [examples[0],examples[1],examples[5]];
 try{
  const response=await fetch(origin+'/api/v1/products?size=3',{cache:'no-store',signal:AbortSignal.timeout(2500)});
  if(!response.ok)return [];
  const data=await response.json();return Array.isArray(data.items)?data.items.slice(0,3):[];
 }catch{return []}
}
