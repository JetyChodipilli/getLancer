export type Product={id:string;slug:string;title:string;summary:string;description:string;category:string;technology:string;projectType:string;builder:string;builderSlug:string;availability:string;bookedUntil?:string;contribution:string;liveUrl?:string;imageUrl?:string;repositoryUrl?:string;pricingNote?:string;videoUrl?:string;media?:{id:string;url:string;alt:string}[];approvalStatus:string;lifecycleStatus:string;visibility:string;availableForSimilarWork:boolean;updatedAt:string;illustrative?:boolean;demoHealth?:string;demoCheckedAt?:string;repositoryVerified?:boolean;reviewSummary?:{reviewCount:number;averageRating:number|null}};
export const examples:Product[]=[
 ['stockroom','Stockroom','Inventory and orders, finally in sync.','Inventory','React','Leah Morgan','leah-morgan','Track stock across locations, follow orders and spot low inventory before it slows your business.'],
 ['booklane','Booklane','A calmer way to manage appointments.','Booking','Next.js','Daniel Kim','daniel-kim','Manage appointments, team schedules and customer bookings in one place.'],
 ['tableflow','TableFlow','From the first order to the last table.','Restaurants','React','Priya Sharma','priya-sharma','Coordinate restaurant orders, floor plans and kitchen handoffs.'],
 ['fieldnote','Fieldnote','Keep every customer conversation moving.','CRM','Vue','Marcus Lee','marcus-lee','Track sales opportunities and follow-ups for a small team.'],
 ['learnspace','Learnspace','A home for courses and curious minds.','Education','Next.js','Elena Cruz','elena-cruz','Organize courses, lessons and learner progress.'],
 ['taskline','Taskline','Less chasing. More shipping.','Productivity','React','Noah Park','noah-park','Plan work, assign responsibilities and follow team delivery.']
].map(([slug,title,summary,category,technology,builder,builderSlug,description])=>({id:slug,slug,title,summary,description,category,technology,builder,builderSlug,projectType:'SAAS',availability:'AVAILABLE_NOW',contribution:'Designed and built the interface, data model and core workflow.',approvalStatus:'APPROVED',lifecycleStatus:'ACTIVE',visibility:'PUBLIC',availableForSimilarWork:true,updatedAt:'2026-09-07T00:00:00Z',illustrative:true}));
export const categories=['Inventory','Booking','Restaurants','CRM','Education','Productivity','E-commerce','Healthcare','FinTech','AI Automation','Developer Tools'];
export const technologies=['React','Next.js','Vue','Spring Boot','PostgreSQL','Flutter'];
export const label=(s:string)=>s.replaceAll('_',' ').toLowerCase().replace(/\b\w/g,c=>c.toUpperCase());
export function availabilityLabel(status: string, date?: string) {
  const labels: Record<string, string> = { AVAILABLE_NOW: 'Available for work', ONE_SLOT_LEFT: 'One client opening', LIMITED: 'Limited availability', NOT_ACCEPTING: 'Not accepting work', BOOKED_UNTIL: date ? 'Booked until ' + date : 'Currently booked' };
  return labels[status] || 'Availability not provided';
}
