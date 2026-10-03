export type CareSide = 'BUYER' | 'SELLER';
export type CareTerms = { title: string; scope: string; terms: string; amountMinor: number; requestsPerCycle: number; responseHours: number; totalCycles: number };
export type CareOffer = CareTerms & { id: string; engagementId: string; engagementTitle: string; side: CareSide; status: string; currency: string; createdAt: string; revision?: number; sellerConsentActorId?: string | null; buyerConsentActorId?: string | null; sellerConsentedAt?: string | null; buyerConsentedAt?: string | null; digest?: string | null };
export type CareBilling = { id: string; status: string; mode: 'test' | 'live'; providerSubscriptionId?: string | null; providerPlanId?: string | null; creationStep?: string | null; attentionReason?: string | null; cancelRequested: boolean; cancelConfirmed: boolean; createdAt: string };
export type CareInvoice = { id: string; providerInvoiceId: string; status: string; amountMinor: number; currency: string; periodStart: string; periodEnd: string; refundedMinor: number; pendingRefund?: boolean; attentionReason?: string | null; transferStatus?: string | null; disputeStatus?: string | null; mode: 'test' | 'live' };
export type CareEntitlement = { available: boolean; reason: string; invoiceId?: string | null; periodStart?: string | null; periodEnd?: string | null; usedRequests: number; remainingRequests: number; requestLimit: number };
export type CareRequest = { id: string; invoiceId: string; title: string; description: string; status: string; deliveryNote?: string | null; deliveryUrl?: string | null; createdAt: string; firstResponseAt?: string | null; responseDueAt: string; resolvedAt?: string | null };
export type CareActivity = { id: string; kind: string; detail: string; createdAt: string };
export type CareDetail = CareOffer & { billing: CareBilling | null; entitlement: CareEntitlement; invoices: CareInvoice[]; requests: CareRequest[]; activity: CareActivity[]; requestPage?: {items: CareRequest[]; page:number; size:number; hasMore:boolean; totalItems:number; totalPages:number} };
export type CareSource = { id: string; title: string; side: CareSide };
export type CareConfiguration = { enabled: boolean; keyId: string; mode: string; reason: string };
export type CareCheckout = { id: string; providerSubscriptionId: string; keyId: string; mode: string; status: string };

export function careLabel(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/^./, c => c.toUpperCase()); }

/** A local interface exercise. It cannot authorize billing or call a payment provider. */
export function createMaintenancePreview(now = new Date()) {
  const stamp = () => now.toISOString();
  const initial = (): CareDetail[] => [{ id: 'preview-care', engagementId: 'preview-completed-project', engagementTitle: 'Customer approval portal', side: 'SELLER', revision: 1, status: 'DRAFT', title: 'Portal care', scope: 'Bug fixes, small workflow improvements and monthly health checks for the delivered approval portal.', terms: 'Includes three support requests per monthly billing period. New features need a separate agreement. First response within 24 hours. Cancel future billing at any time; the paid period remains available.', amountMinor: 1500000, requestsPerCycle: 3, responseHours: 24, totalCycles: 12, currency: 'INR', createdAt: stamp(), billing: null, invoices: [], requests: [], activity: [], entitlement: { available: false, reason: 'Accept the terms and simulate a paid billing period to exercise support requests.', usedRequests: 0, remainingRequests: 0, requestLimit: 3 } }];
  let records = initial(), side: CareSide = 'SELLER';
  const validateTerms = (terms: CareTerms) => {
    for (const [field, min, max] of [['title',3,100],['scope',20,4000],['terms',40,8000]] as const) if (typeof terms[field] !== 'string' || terms[field].trim().length < min || terms[field].trim().length > max) throw new Error('Check the ' + field + ' length.');
    for (const [field, min, max] of [['amountMinor',100,1000000000],['requestsPerCycle',1,50],['responseHours',1,720],['totalCycles',1,12]] as const) if (!Number.isSafeInteger(terms[field]) || terms[field] < min || terms[field] > max) throw new Error('Check the ' + field + ' whole-number bounds.');
  };
  const copy = <T,>(value: T): T => structuredClone(value);
  const find = (id: string) => { const record = records.find(item => item.id === id); if (!record) throw new Error('Care agreement not found.'); return record; };
  const requireSide = (expected: CareSide) => { if (side !== expected) throw new Error('Switch the sample actor to ' + expected.toLowerCase() + ' for this action.'); };
  const event = (record: CareDetail, kind: string, detail: string) => record.activity.unshift({ id: crypto.randomUUID(), kind, detail, createdAt: stamp() });
  const entitlement = (record: CareDetail) => {
    const invoice = record.invoices.find(item => item.status === 'PAID' && item.refundedMinor === 0 && new Date(item.periodStart) <= now && new Date(item.periodEnd) > now);
    const used = invoice ? record.requests.filter(item => item.invoiceId === invoice.id).length : 0;
    return { available: !!invoice, reason: invoice ? 'Simulated paid period. No money moved.' : 'No current simulated paid period.', invoiceId: invoice?.id, periodStart: invoice?.periodStart, periodEnd: invoice?.periodEnd, usedRequests: used, remainingRequests: invoice ? Math.max(0, record.requestsPerCycle - used) : 0, requestLimit: record.requestsPerCycle };
  };
  return {
    setSide(value: CareSide) { side = value; }, reset() { records = initial(); },
    sources(): CareSource[] { return [{ id: 'preview-completed-project', title: 'Customer approval portal', side }]; },
    list(): CareOffer[] { return records.map(({ billing, entitlement, invoices, requests, activity, ...record }) => copy({ ...record, side })); },
    detail(id: string): CareDetail { const record = find(id); return copy({ ...record, side, entitlement: entitlement(record) }); },
    create(engagementId: string, terms: CareTerms) { requireSide('SELLER'); validateTerms(terms); if (engagementId !== 'preview-completed-project') throw new Error('Choose the completed sample project.'); if (records.some(item => ['DRAFT', 'OFFERED'].includes(item.status))) throw new Error('Finish or withdraw the current offer before creating another.'); const record = { ...initial()[0], ...copy(terms), id: crypto.randomUUID(), engagementId, entitlement: { ...initial()[0].entitlement, requestLimit: terms.requestsPerCycle } }; records.unshift(record); event(record, 'OFFER_DRAFTED', 'A sample maintenance offer was prepared.'); return { id: record.id }; },
    offerAction(id: string, action: string, consent = false) {
      const record = find(id);
      if (action === 'send') { requireSide('SELLER'); if (record.status !== 'DRAFT' || !consent) throw new Error('Read and consent to the draft before sending it.'); record.status = 'OFFERED'; record.sellerConsentedAt = stamp(); record.sellerConsentActorId = "Sample seller"; }
      else if (action === 'accept') { requireSide('BUYER'); if (record.status !== 'OFFERED' || !consent) throw new Error('Read and accept the sent terms before billing authorization.'); record.status = 'ACCEPTED'; record.buyerConsentedAt = stamp(); record.buyerConsentActorId = "Sample buyer"; record.digest = 'Local sample terms; no legal signature'; }
      else if (action === 'reject') { requireSide('BUYER'); if (record.status !== 'OFFERED') throw new Error('Only a sent offer can be rejected.'); record.status = 'REJECTED'; }
      else if (action === 'withdraw') { requireSide('SELLER'); if (!['DRAFT', 'OFFERED'].includes(record.status)) throw new Error('Consented terms cannot be withdrawn.'); record.status = 'WITHDRAWN'; }
      else throw new Error('Unknown offer action.');
      event(record, 'OFFER_' + action.toUpperCase(), 'Sample offer ' + action + ' recorded.');
    },
    simulateBilling(id: string, consent: boolean) {
      requireSide('BUYER'); const record = find(id);
      if (record.status !== 'ACCEPTED' || !consent) throw new Error('Accept the agreement and explicitly consent to the sample billing authorization.');
      if (record.billing) throw new Error('A sample subscription already exists.');
      const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1)), end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1));
      record.billing = { id: crypto.randomUUID(), status: 'ACTIVE', mode: 'test', cancelRequested: false, cancelConfirmed: false, createdAt: stamp() };
      record.invoices.push({ id: crypto.randomUUID(), providerInvoiceId: 'Local sample invoice', status: 'PAID', amountMinor: record.amountMinor, currency: 'INR', periodStart: start.toISOString(), periodEnd: end.toISOString(), refundedMinor: 0, transferStatus: 'PREVIEW_ONLY', mode: 'test' });
      event(record, 'PREVIEW_BILLING_SIMULATED', 'A paid period was simulated locally. No mandate, payment or transfer was created.');
    },
    cancel(id: string) { requireSide('BUYER'); const record = find(id); if (!record.billing) throw new Error('No subscription exists.'); record.billing.cancelRequested = true; record.billing.cancelConfirmed = true; record.billing.status = 'CANCELLED'; event(record, 'PREVIEW_BILLING_CANCELLED', 'Future sample billing stopped. The simulated paid period remains available.'); },
    request(id: string, body: { title: string; description: string }) {
      requireSide('BUYER'); const record = find(id), access = entitlement(record);
      if (!access.available || access.remainingRequests < 1 || !access.invoiceId) throw new Error('A current paid period with a remaining request is required.');
      if (body.title.trim().length < 3 || body.title.length > 100 || body.description.trim().length < 20 || body.description.length > 4000) throw new Error('Use a clear request title and a description of 20–4000 characters.');
      const requestId = crypto.randomUUID();
      record.requests.unshift({ id: requestId, invoiceId: access.invoiceId, ...copy(body), status: 'OPEN', createdAt: stamp(), responseDueAt: new Date(now.getTime() + record.responseHours * 3600000).toISOString() });
      event(record, 'REQUEST_OPENED', body.title); return { id: requestId };
    },
    requestAction(id: string, requestId: string, action: string, body: Record<string, string> = {}) {
      const record = find(id), request = record.requests.find(item => item.id === requestId); if (!request) throw new Error('Support request not found.');
      if (action === 'start') { requireSide('SELLER'); if (!['OPEN', 'REVISION_REQUESTED'].includes(request.status)) throw new Error('Only an open or revision-requested item can start.'); request.status = 'IN_PROGRESS'; request.firstResponseAt ||= stamp(); }
      else if (action === 'submit') { requireSide('SELLER'); if (request.status !== 'IN_PROGRESS' || !body.deliveryNote || body.deliveryNote.trim().length < 10 || body.deliveryNote.length > 4000) throw new Error('Start the request and describe its resolution before submitting.'); if (body.deliveryUrl) { let url: URL; try { url = new URL(body.deliveryUrl); } catch { throw new Error('Use an HTTPS delivery URL.'); } if (url.protocol !== 'https:' || !url.hostname || url.username || url.password || (url.port && url.port !== '443')) throw new Error('Use an HTTPS delivery URL without credentials.'); } request.status = 'SUBMITTED'; request.deliveryNote = body.deliveryNote; request.deliveryUrl = body.deliveryUrl; }
      else if (action === 'accept') { requireSide('BUYER'); if (request.status !== 'SUBMITTED') throw new Error('Only a submitted resolution can be accepted.'); request.status = 'RESOLVED'; request.resolvedAt = stamp(); }
      else if (action === 'request-revision') { requireSide('BUYER'); if (request.status !== 'SUBMITTED' || !body.reason || body.reason.trim().length < 10 || body.reason.length > 2000) throw new Error('Describe the revision needed for the submitted resolution.'); request.status = 'REVISION_REQUESTED'; }
      else if (action === 'cancel') { requireSide('BUYER'); if (!['OPEN', 'REVISION_REQUESTED'].includes(request.status)) throw new Error('This request is already in progress or completed.'); request.status = 'CANCELLED'; }
      else throw new Error('Unknown support action.');
      event(record, 'REQUEST_' + action.toUpperCase().replaceAll('-', '_'), body.reason || body.deliveryNote || request.title);
    },
  };
}
