// A brief stays in this browser tab, never in a URL or long-lived local storage.
export const inquiryDraftKey = 'getlancer:inquiry-draft';
export type InquiryDraft = { productSlug: string; clientEmail: string; description: string; expiresAt: number };
export function takeInquiryDraft(productSlug: string): InquiryDraft | null {
 try {
  const raw = sessionStorage.getItem(inquiryDraftKey);
  sessionStorage.removeItem(inquiryDraftKey);
  if (!raw) return null;
  const d = JSON.parse(raw) as InquiryDraft;
  return d.productSlug === productSlug && typeof d.expiresAt === 'number' && d.expiresAt > Date.now() && typeof d.clientEmail === 'string' && d.clientEmail.length <= 254 && typeof d.description === 'string' && d.description.length <= 5000 ? d : null;
 } catch { return null; }
}
