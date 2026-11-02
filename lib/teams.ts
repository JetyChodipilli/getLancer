/** JSON projections from TeamRepository/TeamService and the isolated team demo. */
export type TeamRequest = <T = unknown>(path: string, init?: RequestInit) => Promise<T>;
export type TeamTransport = (path: string, init?: RequestInit) => Promise<unknown>;
export type TeamRole = 'OWNER' | 'BUSINESS_MANAGER' | 'RECRUITER' | 'PROJECT_MANAGER' | 'MEMBER';
export type TeamPage = {page: number; size?: number; hasMore: boolean; totalItems?: number; totalPages?: number};
export type TeamList<T> = TeamPage & {items: T[]; size: number; totalItems: number; totalPages: number};
export type TeamSummary = {
 id: string; slug: string; name: string; summary: string; availability: string;
 projectRange: string; ownerId: string; status: 'ACTIVE' | 'SUSPENDED';
 memberCount: number; roleCount: number; myRole?: TeamRole;
};
export type TeamMember = {
 userId: string; name: string; role: TeamRole; membershipType: 'PERMANENT' | 'CONTRACT';
 expiresAt: string | null; projectLabel: string | null;
};
export type TeamInvitation = TeamMember & {
 id: string; teamId: string; teamName: string; status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED';
};
export type TeamOpenRole = {
 id: string; teamId: string; title: string; description: string; skills: string | string[];
 contractType: 'PERMANENT' | 'CONTRACT'; compensationBand: string; status: 'OPEN' | 'CLOSED';
};
export type TeamApplication = {
 id: string; teamId: string; roleId: string; roleTitle: string; userId: string; name: string;
 message: string; status: 'APPLIED' | 'SUBMITTED' | 'PENDING' | 'SHORTLISTED' | 'REJECTED' | 'INVITED';
};
export type TeamNote = {id: string; text: string; createdAt: string; actorName: string};
export type TeamLead = {
 id: string; teamId: string; teamName: string; clientId: string; clientName: string;
 title: string; description: string; budget: string; timeline: string; status: string;
 assigneeId: string | null; followUpAt: string | null; notes: TeamNote[]; notesPage?: TeamPage;
};
export type TeamProduct = {
 id: string; title: string; summary: string; description?: string; slug?: string; imageUrl?: string;
 approvalStatus: string; visibility: string; canWithdraw?: boolean;
 developer?: {displayName: string}; developerName?: string; ownerName?: string; builder?: string;
};
export type TeamCandidate = {id: string; name: string; headline: string; skills: string | string[]};
export type TeamStaffing = {
 id: string; userId: string; name: string; projectLabel: string; skills: string | string[];
 endsAt: string; status: 'ACTIVE' | 'COMPLETED';
};
export type TeamActivity = {id: string; action: string; detail: string; actorName: string; createdAt: string};
export type TeamReview = {
 id: string; rating: number; reviewText: string; createdAt: string; memberId: string; memberName: string;
 // Retained aliases support existing review fixtures and imported product attribution.
 text?: string; review_text?: string; developerName?: string; builderName?: string; clientName?: string;
};
export type TeamUser = {
 id: string; roles: string[]; emailVerified: boolean;
 profile?: {approval_status?: string} | null; profileStatus?: string;
};
export type TeamIndexCollections = {
 items: TeamSummary[]; invitations: TeamInvitation[]; applications: TeamApplication[]; requests: TeamLead[];
};
export type TeamIndex = TeamIndexCollections & {canCreate: boolean; pages?: Partial<Record<keyof TeamIndexCollections, TeamPage>>};
export type TeamWorkspaceCollections = {
 members: TeamMember[]; invitations: TeamInvitation[]; roles: TeamOpenRole[]; applications: TeamApplication[];
 leads: TeamLead[]; projects: TeamProduct[]; staffing: TeamStaffing[]; activity: TeamActivity[];
};
export type TeamWorkspaceData = TeamWorkspaceCollections & {
 team: TeamSummary; myRole: TeamRole; pages?: Partial<Record<keyof TeamWorkspaceCollections, TeamPage>>;
};
export type TeamPublicCollections = {members: TeamMember[]; projects: TeamProduct[]; roles: TeamOpenRole[]; reviews: TeamReview[]};
export type TeamPublicData = TeamPublicCollections & {team: TeamSummary; pages?: Partial<Record<keyof TeamPublicCollections, TeamPage>>};
export type TeamMutationBody = Record<string, string | null | undefined>;
