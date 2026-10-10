import type {EducationSnapshot} from './education';
export type TemplateVersion = {id:string;version:string;releaseNotes:string;status:string;licenseTerms?:string;reviewReason?:string|null;sha256?:string|null;sizeBytes?:number;entryCount?:number;manifestFiles?:string[];createdAt?:string};
export type SourceTemplate = {id:string;slug:string;title:string;summary:string;description:string;category:string;technology:string;priceMinor:number;currency:string;productId:string;productSlug?:string;sellerId:string;sellerName:string;versionId?:string;version?:string;releaseNotes?:string;licenseTerms:string;status:string;createdAt:string;versions?:TemplateVersion[];educationProduct?:boolean;educationProjectSlug?:string;currentEducationRelease?:string};
export type TemplatePurchase = {id:string;templateId?:string;versionId?:string;title:string;version:string;licenseTerms:string;sha256:string;amountMinor:number;currency:string;mode:string;status:string;refundedMinor:number;downloadAvailable:boolean;orderId?:string|null;paymentId?:string|null;attentionReason?:string|null;createdAt?:string;educationReleaseId?:string;educationSnapshot?:EducationSnapshot & {releaseId?:string;sourceHash?:string};dispute?:{status:string;reason:string}|null};
export type TemplateOrder = {id:string;orderId?:string|null;amountMinor:number;currency:string;keyId?:string|null;mode:string;status:string};
export type TemplateFilters = {q:string;category:string;technology:string};
export type TemplatePage<T> = {items:T[];page:number;size:number;hasMore:boolean;totalItems:number;totalPages:number};
export const templateStatus=(value:string)=>value.toLowerCase().replaceAll('_',' ').replace(/^./,letter=>letter.toUpperCase());
export const templatePrice=(minor:number)=>new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:2}).format(minor/100);
export const templateDate=(value?:string)=>value?new Date(value).toLocaleDateString('en-IN',{day:'numeric',month:'short',year:'numeric'}):'Date unavailable';
export function templateMinor(value:string):number{
 if(!/^\d+(?:\.\d{1,2})?$/.test(value.trim()))throw Error('Enter an INR price with at most two decimal places.');
 const [whole,fraction='']=value.trim().split('.'),minor=Number(whole)*100+Number(fraction.padEnd(2,'0'));
 if(!Number.isSafeInteger(minor)||minor<100||minor>1000000000)throw Error('The template price must be between ₹1.00 and ₹1,00,00,000.');
 return minor;
}
export function templateFilterParams(filters:TemplateFilters,page=0){const params=new URLSearchParams();for(const key of ['q','category','technology'] as const)if(filters[key])params.set(key,filters[key]);if(page)params.set('page',String(page));return params;}
export function canSubmitVersion(version:TemplateVersion){return ['DRAFT','CHANGES_REQUESTED'].includes(version.status);}
export function canOpenTemplateCheckout(template:SourceTemplate){return template.educationProduct!==true&&template.status==='ACTIVE'&&!!template.versionId;}
export function eligibleTemplateDownload(purchase:TemplatePurchase){return purchase.downloadAvailable===true&&purchase.mode==='live'&&purchase.status==='CAPTURED'&&purchase.refundedMinor===0&&(!purchase.dispute||purchase.dispute.status==='RESUMED');}
export function templateDisputeAvailable(purchase:TemplatePurchase){return ['CAPTURED','PARTIALLY_REFUNDED','REFUNDED','DISPUTED'].includes(purchase.status)&&!purchase.dispute;}

type CheckoutInstance={open:()=>void;close?:()=>void;on:(event:string,handler:(result:{error?:{description?:string}})=>void)=>void};
type CheckoutConstructor=new(options:Record<string,unknown>)=>CheckoutInstance;
let checkoutPending:Promise<CheckoutConstructor>|undefined;
/** Called only after a connected checkout order exists; previews never load the gateway. */
export function loadTemplateCheckout():Promise<CheckoutConstructor>{
 const current=(window as unknown as {Razorpay?:CheckoutConstructor}).Razorpay;if(current)return Promise.resolve(current);if(checkoutPending)return checkoutPending;
 checkoutPending=new Promise((resolve,reject)=>{
  const script=document.createElement('script');script.src='https://checkout.razorpay.com/v1/checkout.js';script.async=true;
  const fail=()=>{window.clearTimeout(timeout);script.remove();checkoutPending=undefined;reject(Error('Checkout could not load. Reconcile your purchase before trying again.'));};
  const timeout=window.setTimeout(fail,15000);script.onerror=fail;script.onload=()=>{window.clearTimeout(timeout);const gateway=(window as unknown as {Razorpay?:CheckoutConstructor}).Razorpay;if(gateway)resolve(gateway);else fail();};document.head.append(script);
 });return checkoutPending;
}

/** ZIP attachments are requested only after an explicit owner, operator or entitled-buyer action. */
export async function downloadTemplateSource(path:string,filename='getlancer-source.zip',method:'GET'|'POST'='GET'){
 const response=await fetch('/api/v1'+path,{method,credentials:'same-origin',headers:{'X-Requested-With':'getlancer'}});
 if(!response.ok){const body=await response.json().catch(()=>({}));throw Object.assign(Error(body.error?.message||'Source access is unavailable. Refresh your account and purchase status.'),{status:response.status,code:body.error?.code||'SOURCE_UNAVAILABLE'});}
 if(!response.headers.get('Content-Type')?.includes('application/zip'))throw Error('The source response could not be verified. No file was saved.');
 const blob=await response.blob();if(blob.size>5*1024*1024)throw Error('The source exceeds the approved package limit. No file was saved.');
 const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=filename;document.body.append(link);link.click();link.remove();window.setTimeout(()=>URL.revokeObjectURL(url),30000);
}
