import type { Capacity, SlotPricing, SlotPurchase } from './components';

export const slotPools = ['PROJECT', 'TEMPLATE', 'COMPONENT'] as const;
export type SlotPool = typeof slotPools[number];
export type ProjectCapacity = Capacity & {
  earned: number; extraUsed: number; extraLimit: number;
  regular: { free: number; used: number }; college: { free: number; used: number };
  availableRegular: number; availableCollege: number;
};
export type PublishingOverview = {
  capacities: { PROJECT: ProjectCapacity; TEMPLATE: Capacity & { legacy?: number }; COMPONENT: Capacity };
  prices: Record<SlotPool, SlotPricing>;
};
export type PublishingPurchase = SlotPurchase & { pool: SlotPool; createdAt?: string };
export const slotNames: Record<SlotPool, string> = { PROJECT: 'Project', TEMPLATE: 'Template', COMPONENT: 'Component' };
export function slotPool(value: string | null): SlotPool { return slotPools.includes(value as SlotPool) ? value as SlotPool : 'PROJECT'; }
export const projectCapacitySample: ProjectCapacity = { free: 6, purchased: 0, used: 4, limit: 6, earned: 0, extraUsed: 0, extraLimit: 0, regular: { free: 3, used: 2 }, college: { free: 3, used: 2 }, availableRegular: 1, availableCollege: 1 };
export function samplePublishing(): PublishingOverview {
  const price = { amountMinor: null, enabled: false, salesEnabled: false, configured: false, currency: 'INR', mode: 'disabled', reason: 'Purchases are disabled in this design preview.' };
  return { capacities: { PROJECT: projectCapacitySample, TEMPLATE: { free: 3, purchased: 0, used: 1, limit: 3 }, COMPONENT: { free: 3, purchased: 0, used: 3, limit: 3 } }, prices: { PROJECT: { ...price }, TEMPLATE: { ...price }, COMPONENT: { ...price } } };
}
