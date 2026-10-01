type Count = number | null;
type Supply = Record<'approvedBuilders'|'activeBuilders'|'activePublicShowcases'|'averageShowcasesPerApprovedBuilder'|'liveDemoShowcases'|'liveDemoPercent'|'videoShowcases'|'videoPercent'|'imageShowcases',Count>;
type Outcomes = Record<'qualifiedInquiries'|'respondedInquiries'|'responseEligibleInquiries'|'respondedEligibleInquiries'|'responseRatePercent'|'medianResponseHours'|'confirmedHires'|'confirmedCompletions'|'inquiryToHirePercent'|'hireToCompletionPercent'|'uniqueClients'|'repeatClients'|'repeatHiredClients'|'leadRecipients'|'topBuilderCount'|'topBuilderLeadCount'|'top10LeadSharePercent'|'verifiedReviews',Count>;
type Slots = Record<'eligibleBuilders'|'totalCapacity'|'activeUsage'|'availableCapacity'|'overCapacityBuilders'|'utilisationPercent'|'activationEvents'|'archiveEvents'|'activatedProducts'|'repeatActivationProducts'|'timeToThreeActiveShowcasesDays'|'blockedActivationBuilders',Count>;
type Search = Record<'receivedEvents'|'sampleSearches'|'searchesWithThreeResults'|'noResultSearches'|'threeResultPercent'|'noResultPercent'|'fallbackCoveragePercent',Count>;
type Safety = Record<'reports'|'resolvedReports'|'openReports'|'resolutionRatePercent'|'maliciousLinkReports'|'ipComplaints'|'spamReports'|'appeals'|'decidedAppeals'|'overturnedAppeals'|'reversalRatePercent'|'enforcementActions'|'reportActionRatePercent',Count>;
export type MarketplaceMetricsData = {
 window: {days:number;from:string;to:string;responseMaturityHours:number;qualification:string;supply:string;privacy:string};
 supply: Supply;
 outcomes: Outcomes;
 slots: Slots & {timeToThreeReason:string;blockedActivationReason:string};
 portfolioCohorts: {activeShowcaseCohort:number;builders:number;qualifiedInquiries:number;confirmedHires:number;inquiryToHirePercent:Count}[];
 search: Search & {reason:string;fallbackCoverageReason:string};
 safety: Safety & {bySeverity:{severity:string;reports:number}[];byReason:{reason:string;reports:number}[];reportActionRateReason:string};
};

function value(n:Count|undefined,kind:'count'|'percent'|'hours'='count'){
 if(n==null)return 'No eligible cohort';
 const formatted=Number(n).toLocaleString(undefined,{maximumFractionDigits:kind==='count'?2:1});
 return kind==='percent'?formatted+'%':kind==='hours'?formatted+' hours':formatted;
}
function Facts({items}:{items:[string,Count|undefined,('count'|'percent'|'hours')?][]}){
 return <dl className="studio-summary-list">{items.map(([label,count,kind])=><div className="row" key={label}><dt className="grow">{label}</dt><dd>{value(count,kind)}</dd></div>)}</dl>;
}
function WindowDate({date}:{date:string}){return <time dateTime={date}>{new Date(date).toLocaleDateString()}</time>}

export default function AdminMarketplaceMetrics({metrics}:{metrics:MarketplaceMetricsData}){
 const {window:period,supply,outcomes,slots,search,safety}=metrics;
 return <details className="panel"><summary>Marketplace health and liquidity</summary>
  <section aria-labelledby="operator-metrics-title">
   <h3 id="operator-metrics-title">Real marketplace activity</h3>
   <p className="muted">Current supply snapshot. Outcomes, search samples and safety records cover {period.days} days: <WindowDate date={period.from}/> to <WindowDate date={period.to}/>.</p>
   <div className="grid" aria-label="Marketplace measures">
    {[
     ['Approved builders',supply.approvedBuilders,'count','Active accounts with verified email and approved profiles'],
     ['Public showcases',supply.activePublicShowcases,'count','Approved, active and public proof'],
     ['Qualified inquiries',outcomes.qualifiedInquiries,'count','Email-confirmed and clear of restrictions'],
     ['Response rate',outcomes.responseRatePercent,'percent',value(outcomes.respondedEligibleInquiries)+' of '+value(outcomes.responseEligibleInquiries)+' matured inquiries'],
     ['Inquiry to hire',outcomes.inquiryToHirePercent,'percent',value(outcomes.confirmedHires)+' client-confirmed hires'],
    ].map(([label,count,kind,note])=><article className="panel" key={String(label)}><h4>{String(label)}</h4><p><strong>{value(count as Count,kind as 'count'|'percent')}</strong></p><p className="muted">{String(note)}</p></article>)}
   </div>
   <details><summary>Supply and proof coverage</summary><p className="muted">{period.supply} Link presence measures supplied evidence, rather than uptime or code verification.</p><Facts items={[
    ['Builders with public active proof',supply.activeBuilders],['Average public showcases per approved builder',supply.averageShowcasesPerApprovedBuilder],
    ['Showcases with live links',supply.liveDemoShowcases],['Live-link coverage',supply.liveDemoPercent,'percent'],
    ['Showcases with videos',supply.videoShowcases],['Video coverage',supply.videoPercent,'percent'],['Showcases with verified uploaded images',supply.imageShowcases],
   ]}/></details>
   <details><summary>Conversion and client relationships</summary><p className="muted">{period.qualification} Response-rate cohorts have had at least {period.responseMaturityHours} hours to receive a response. The response-time median uses their first recorded response.</p><Facts items={[
    ['Responded inquiries, including recent confirmations',outcomes.respondedInquiries],['Median first response',outcomes.medianResponseHours,'hours'],
    ['Client-confirmed hires',outcomes.confirmedHires],['Client-confirmed completions',outcomes.confirmedCompletions],['Hire to completion',outcomes.hireToCompletionPercent,'percent'],
    ['Published verified reviews in this cohort',outcomes.verifiedReviews],['Distinct client relationships',outcomes.uniqueClients],['Clients with multiple qualified inquiries',outcomes.repeatClients],
    ['Clients with multiple confirmed hires',outcomes.repeatHiredClients],['Builders receiving a qualified lead',outcomes.leadRecipients],
    ['Lead share received by the top 10% of recipient builders',outcomes.top10LeadSharePercent,'percent'],
   ]}/><p className="muted">Concentration uses {value(outcomes.topBuilderCount)} of {value(outcomes.leadRecipients)} builders who received leads, rounded up; they received {value(outcomes.topBuilderLeadCount)} qualified inquiries. Counts do not expose client identities.</p></details>
   <details><summary>Search liquidity</summary><p className="muted">{search.reason}</p><Facts items={[
    ['Web search events received',search.receivedEvents],['Searches with a valid result count',search.sampleSearches],['Searches with at least three results',search.searchesWithThreeResults],
    ['Three-result search rate',search.threeResultPercent,'percent'],['Searches returning no results',search.noResultSearches],['No-result search rate',search.noResultPercent,'percent'],
   ]}/><p className="muted">Fallback coverage: not measured. {search.fallbackCoverageReason}</p></details>
   <details><summary>Showcase capacity and activity</summary><Facts items={[
    ['Eligible builders',slots.eligibleBuilders],['Total active-showcase capacity',slots.totalCapacity],['Active showcases using capacity, including private proof',slots.activeUsage],
    ['Available active-showcase slots',slots.availableCapacity],['Capacity utilisation',slots.utilisationPercent,'percent'],['Builders exceeding capacity',slots.overCapacityBuilders],
    ['Activation events in this window',slots.activationEvents],['Archive events in this window',slots.archiveEvents],['Products activated more than once in this window',slots.repeatActivationProducts],
   ]}/><div style={{overflowX:'auto'}}><table><caption>Current portfolio size and qualified outcomes in this window; these are observational cohorts, not proof that portfolio size caused a hire.</caption><thead><tr><th scope="col">Active showcases now</th><th scope="col">Builders</th><th scope="col">Qualified inquiries</th><th scope="col">Confirmed hires</th><th scope="col">Inquiry to hire</th></tr></thead><tbody>{metrics.portfolioCohorts.map(c=><tr key={c.activeShowcaseCohort}><th scope="row">{c.activeShowcaseCohort===4?'4 or more':c.activeShowcaseCohort}</th><td>{value(c.builders)}</td><td>{value(c.qualifiedInquiries)}</td><td>{value(c.confirmedHires)}</td><td>{value(c.inquiryToHirePercent,'percent')}</td></tr>)}</tbody></table></div>{!metrics.portfolioCohorts.length&&<p>No approved builder cohort yet.</p>}<p className="muted">Time to first three active showcases: not measured. {slots.timeToThreeReason}</p><p className="muted">Blocked activations: not measured. {slots.blockedActivationReason}</p></details>
   <details><summary>Safety and moderation</summary><Facts items={[
    ['Reports received',safety.reports],['Reports resolved',safety.resolvedReports],['Reports awaiting resolution',safety.openReports],['Resolution rate',safety.resolutionRatePercent,'percent'],
    ['Recorded restriction actions',safety.enforcementActions],['Malicious-link or phishing reports',safety.maliciousLinkReports],['IP or stolen-work complaints',safety.ipComplaints],['Spam reports',safety.spamReports],
    ['Appeals received',safety.appeals],['Appeals decided',safety.decidedAppeals],['Decisions overturned',safety.overturnedAppeals],['Appeal reversal rate',safety.reversalRatePercent,'percent'],
   ]}/><div style={{overflowX:'auto'}}><table><caption>Reports by severity</caption><thead><tr><th scope="col">Severity</th><th scope="col">Reports</th></tr></thead><tbody>{safety.bySeverity.map(r=><tr key={r.severity}><th scope="row">{r.severity.toLowerCase()}</th><td>{value(r.reports)}</td></tr>)}</tbody></table><table><caption>Reported concerns</caption><thead><tr><th scope="col">Reason</th><th scope="col">Reports</th></tr></thead><tbody>{safety.byReason.map(r=><tr key={r.reason}><th scope="row">{r.reason.toLowerCase().replaceAll('_',' ')}</th><td>{value(r.reports)}</td></tr>)}</tbody></table></div>{!safety.reports&&<p>No reports in this window.</p>}<p className="muted">Enforcement action rate per report: not measured. {safety.reportActionRateReason}</p></details>
   <p className="muted">{period.privacy} A missing rate stays unmeasured; it is never displayed as a fabricated zero.</p>
  </section>
 </details>;
}
