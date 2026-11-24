import type {NavIconName} from '@/app/components/animated-icons/parts';

export type WorkspaceTool = {route: string; name: string; icon: NavIconName; description?: string};
export type WorkspaceToolGroup = {group: string; tools: WorkspaceTool[]};

export const clientTools: WorkspaceToolGroup[] = [
  {group: 'Hire', tools: [{route: 'business', icon: 'briefcase', name: 'Business hiring', description: 'Create project requests and review builder proposals.'}]},
  {group: 'Deliver', tools: [{route: 'delivery', icon: 'delivery', name: 'Delivery & payments', description: 'Track milestones, approvals, and payments.'}]},
  {group: 'Buy', tools: [{route: 'templates', icon: 'templates', name: 'Templates & purchases', description: 'Find reusable source and review past purchases.'}]},
  {group: 'Support', tools: [{route: 'maintenance', icon: 'help', name: 'Maintenance & support', description: 'Manage ongoing support and maintenance requests.'}]},
];

const reliability: WorkspaceTool = {route: 'trust', icon: 'shield', name: 'Trust & reliability', description: 'Manage availability, delivery evidence, and reliability.'};
const release: WorkspaceTool = {route: 'operations', icon: 'activity', name: 'Operations & release readiness', description: 'Review operational evidence and release requirements.'};
const labs: WorkspaceTool = {route: 'labs', icon: 'learning', name: 'Lab operations', description: 'Review the state of lab programs and environments.'};

export function trustTools(preview: boolean, operator: boolean): WorkspaceToolGroup[] {
  return [
    {group: 'Reliability', tools: [reliability]},
    {group: 'Operations', tools: [...(preview || operator ? [release] : []), ...(!preview ? [labs] : [])]},
  ];
}
