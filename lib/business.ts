export type BusinessRequest = (path: string, init?: RequestInit) => Promise<any>;
export type Business = {id:string;name:string;summary:string;ownerId:string};
export type Brief = {id:string;businessId:string;title:string;description:string;category:string;technology:string;budget:string;timeline:string;availableOnly:boolean;repositoryVerifiedOnly:boolean;status:'DRAFT'|'OPEN'|'CLOSED';conciergeStatus?:string;createdBy?:string};
export type Candidate = {kind:'BUILDER'|'TEAM';targetId:string;name:string;summary:string;url:string;availability:string;reasons:string[];evidenceCount:number};
export type TalentList = {id:string;name:string};
export type SavedCandidate = {id:string;kind:Candidate['kind'];targetId:string;candidate?:Candidate;unavailable?:boolean;reason?:string;source?:string};
export type BusinessWorkspaceData = {business:Business;myRole:'OWNER'|'HIRING_MANAGER';members:{userId:string;name:string;role:string}[];invitations:{id:string;email:string;status:string}[];activity:{id:string;action:string;detail:string;createdAt:string}[]};
