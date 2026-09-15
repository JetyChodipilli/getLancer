import {backendOrigin,getCatalog} from './server';
import {emptyFilters} from './discovery';
export async function taxonomyPage(kind:'categories'|'technologies',slug:string,page=0){
 const origin=await backendOrigin();if(!origin)return undefined;
 const response=await fetch(origin+'/api/v1/'+kind,{cache:'no-store',signal:AbortSignal.timeout(10000)});if(!response.ok)throw Error('Discovery is temporarily unavailable');
 const data=await response.json();const item=data.items.find((x:{slug:string;name:string})=>x.slug===slug);if(!item)return undefined;
 const catalog=await getCatalog({...emptyFilters,[kind==='categories'?'category':'technology']:item.name,page});
 if(catalog.totalItems===0)return undefined;return {item,catalog};
}
