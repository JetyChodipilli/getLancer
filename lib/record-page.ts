export type RecordPage<T>={items:T[];page:number;size:number;hasMore:boolean};

/** Accept a first page only when its continuation metadata can be verified. */
export function recordPage<T>(value:unknown,expectedPage=0):RecordPage<T>{
 const data=value!==null&&typeof value==='object'&&!Array.isArray(value)?value as Record<string,unknown>:{};
 if(!Array.isArray(data.items)||data.page!==expectedPage||!Number.isSafeInteger(data.size)||Number(data.size)<1||typeof data.hasMore!=='boolean')throw new Error('The record page could not be verified. Please retry loading it.');
 return {items:data.items as T[],page:expectedPage,size:Number(data.size),hasMore:data.hasMore};
}
