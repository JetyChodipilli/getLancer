export type ShowcasePricing = {pricingMode?: 'NONE'|'STARTING_FROM'|'RANGE'|'CUSTOM_QUOTE';priceMinMinor?: number|null;priceMaxMinor?: number|null;currency?: string|null};
export const pricingCurrencies = ['INR','USD','EUR','GBP','CAD','AUD','JPY','KWD'];
export function currencyDigits(currency:string) { return new Intl.NumberFormat('en',{style:'currency',currency}).resolvedOptions().maximumFractionDigits ?? 2; }
export function toMinorAmount(value:string,currency:string):number|null {
 if(!value.trim())return null;
 const digits=currencyDigits(currency),match=value.trim().match(/^(\d+)(?:\.(\d+))?$/);
 if(!match||(match[2]?.length||0)>digits)throw Error('Use a positive amount with the currency’s supported decimal places.');
 const minor=Number(match[1])*10**digits+Number((match[2]||'').padEnd(digits,'0'));
 if(!Number.isSafeInteger(minor)||minor>100000000000)throw Error('The amount is too large.');return minor;
}
export function majorAmount(value:number|null|undefined,currency:string) {return value==null?'':(value/10**currencyDigits(currency)).toFixed(currencyDigits(currency));}
export function showcasePricingLabel(p:ShowcasePricing):string|null {
 if(p.pricingMode==='CUSTOM_QUOTE')return 'Custom quote';
 if(!p.currency||p.priceMinMinor==null||!['RANGE','STARTING_FROM'].includes(p.pricingMode||''))return null;
 const money=(minor:number)=>new Intl.NumberFormat('en',{style:'currency',currency:p.currency!}).format(minor/10**currencyDigits(p.currency!));
 return p.pricingMode==='RANGE'&&p.priceMaxMinor!=null?`${money(p.priceMinMinor)} – ${money(p.priceMaxMinor)}`:`Starting from ${money(p.priceMinMinor)}`;
}
