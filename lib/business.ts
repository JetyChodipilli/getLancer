export type BusinessRequest = (path: string, init?: RequestInit) => Promise<any>;
export type BusinessPage = {page:number;size:number;hasMore:boolean;totalItems:number;totalPages:number};
export type BusinessCollection<T> = BusinessPage & {items:T[]};
export type Business = {id:string;name:string;summary:string;ownerId:string};
export type Brief = {id:string;businessId:string;title:string;description:string;category:string;technology:string;budget:string;timeline:string;availableOnly:boolean;repositoryVerifiedOnly:boolean;status:'DRAFT'|'OPEN'|'CLOSED';conciergeStatus?:string;createdBy?:string};
export type Candidate = {kind:'BUILDER'|'TEAM';targetId:string;name:string;summary:string;url:string;availability:string;reasons:string[];evidenceCount:number};
export type TalentList = {id:string;name:string};
export type SavedCandidate = {id:string;kind:Candidate['kind'];targetId:string;candidate?:Candidate;unavailable?:boolean;reason?:string;source?:string};
export type BusinessWorkspaceData = {business:Business;myRole:'OWNER'|'HIRING_MANAGER';membersPage:BusinessPage;invitationsPage:BusinessPage;activityPage:BusinessPage;members:{userId:string;name:string;role:string}[];invitations:{id:string;email:string;status:string}[];activity:{id:string;action:string;detail:string;createdAt:string}[]};

/** Offset continuations keep the original endpoint and request adapter, including preview mode. */
export function businessPagePath(path:string,page:BusinessPage){
 const [pathname,query='']=path.split('?'),parameters=new URLSearchParams(query);
 parameters.set('page',String(page.page+1));parameters.set('size',String(page.size));
 return pathname+'?'+parameters.toString();
}
export function appendBusinessRecords<T extends {id?:string;userId?:string}>(current:T[],incoming:T[]){
 const key=(row:T)=>row.id||row.userId;
 const seen=new Set(current.map(key));
 return [...current,...incoming.filter(row=>{const id=key(row);if(seen.has(id))return false;seen.add(id);return true;})];
}

export function businessCollectionRequest(request:BusinessRequest,collection:string):BusinessRequest{
 return async(path,init)=>{const response=await request(path,init);return {items:response[collection],...response[collection+'Page']};};
}
