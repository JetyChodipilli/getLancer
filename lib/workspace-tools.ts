export const clientTools = [
  {group: 'Hire', tools: [{route: 'business', name: 'Business hiring', description: 'Create project requests and review builder proposals.'}]},
  {group: 'Deliver', tools: [{route: 'delivery', name: 'Delivery & payments', description: 'Track milestones, approvals, and payments.'}]},
  {group: 'Buy', tools: [{route: 'templates', name: 'Templates & purchases', description: 'Find reusable source and review past purchases.'}]},
  {group: 'Support', tools: [{route: 'maintenance', name: 'Maintenance & support', description: 'Manage ongoing support and maintenance requests.'}]},
];

const reliability = {route: 'trust', name: 'Trust & reliability', description: 'Manage availability, delivery evidence, and reliability.'};
const release = {route: 'operations', name: 'Operations & release readiness', description: 'Review operational evidence and release requirements.'};
const labs = {route: 'labs', name: 'Lab operations', description: 'Review the state of lab programs and environments.'};

export function trustTools(preview: boolean, operator: boolean) {
  return [
    {group: 'Reliability', tools: [reliability]},
    {group: 'Operations', tools: [...(preview || operator ? [release] : []), ...(!preview ? [labs] : [])]},
  ];
}
