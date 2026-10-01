import { backendOrigin } from '@/lib/server';

export default async function Page() {
  let contacts: Record<string, string> = {};
  const origin = await backendOrigin();
  if (origin) {
    try {
      const response = await fetch(origin + '/api/v1/policies/config', { cache: 'no-store', signal: AbortSignal.timeout(10000) });
      if (response.ok) contacts = await response.json();
    } catch { /* Unavailable contacts are never replaced with sample addresses. */ }
  }
  return <main className="wrap prose" id="main">
    <span className="eyebrow">Marketplace information</span>
    <h1>Marketplace policies</h1>
    <p>These notices describe the implemented marketplace workflows. Final legal terms, operator details, retention periods and complaint contacts require operator approval before public launch.</p>
    <h2 id="terms">Project agreements and payments</h2>
    <p>Clients and builders agree to a proposal, milestones and any confidentiality terms before starting an engagement. Both parties record their consent. These records do not certify a legal signature or guarantee delivery.</p>
    <p>Connected payments use Razorpay and, for eligible delivery engagements, Razorpay Route. Authorization, capture, transfer and bank settlement are separate events. An uncertain payment stays on hold for reconciliation. Test payments do not represent money received. getLancer does not provide a wallet or guarantee escrow.</p>
    <h2>Source templates and licensing</h2>
    <p>Source packages require ownership evidence, seller rights confirmation and an operator review. Packages are inspected as data; the platform does not execute the submitted code. Approval is not a warranty that code is defect free.</p>
    <p>Before buying, review the listed version, price and license terms. Your purchase preserves that version, its package checksum and the terms you accepted. A new seller release does not automatically replace your purchase. Sellers retain copyright; modification, commercial use and redistribution depend on the recorded license.</p>
    <h2>Refunds, disputes and access</h2>
    <p>Raise a concern from the engagement or purchase workspace. An operator decision does not itself move funds: refunds and provider disputes require reconciliation with Razorpay. Refunds, unresolved disputes, revoked rights or a suspended package can block source downloads. Test purchases never unlock a live source license. Files already downloaded cannot be recalled.</p>
    <h2>Content and ownership</h2>
    <p>Builders must have permission to publish their work and distribute every included dependency. Do not publish client secrets, confidential information, stolen work, misleading claims or unsafe links.</p>
    <h2 id="privacy">Privacy</h2>
    <p>Approved public profiles and projects are discoverable. Inquiries, business requests, source packages and engagement evidence are restricted to authorized participants and operators. Passwords, confirmation tokens and private storage keys are never public.</p>
    <h2>Reviews</h2>
    <p>A verified review requires a client-confirmed completed engagement. One review is allowed per engagement. Abuse or confidential content may be held for moderation.</p>
    <h2>Cookies</h2>
    <p>Essential session cookies support authentication. Optional first-party usage analytics run only after you enable them in Privacy preferences. Razorpay Checkout loads when you initiate a connected payment and follows the provider’s own privacy terms.</p>
    <h2>Reports and appeals</h2>
    <p>Use Report a concern for stolen work, malicious links, impersonation or confidential information. Moderation records preserve the action and reason. Staffed appeal and privacy-request channels are required before public launch.</p>
    <h2>Contact and privacy requests</h2>
    {([['support', 'Support'], ['privacy', 'Privacy requests'], ['copyright', 'Copyright complaints']] as const).map(([key, label]) => contacts[key] && /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(contacts[key]) ? <p key={key}>{label}: <a href={'mailto:' + contacts[key]}>{contacts[key]}</a></p> : null)}
    <p>You can export your account data and request closure from Account settings. Closure immediately removes public visibility and account access. Financial, license, engagement and moderation records follow the approved retention policy and privacy review process.</p>
    <p>Signed-in members can review moderation decisions and submit an appeal from Account settings. Suspended members can use the public report form or the configured support contact and identify the original decision. Do not include passwords or confidential files.</p>
  </main>;
}
