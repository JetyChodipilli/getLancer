'use client';
import { Check, ShieldCheck, UserRound, Code2 } from 'lucide-react';
import { cn } from '@/lib/utils';

type Account = { id: string; name: string; role: string; description: string };
export default function DemoRolePicker({ accounts, selected, onSelect }: { accounts: Account[]; selected: string; onSelect: (id: string) => void }) {
  return <section className="demo-role-picker" aria-label="Demo account selection">
    <div className="demo-role-intro"><strong>Try a different role</strong><span>Sample accounts · changes stay in this preview</span></div>
    <div className="demo-role-cards" role="group" aria-label="Demo roles">{accounts.map(account => {
      const Icon = account.role === 'Administrator' ? ShieldCheck : account.role === 'Builder' ? Code2 : UserRound;
      return <button type="button" key={account.id} className={cn('demo-role-card', selected === account.id && 'selected')} aria-pressed={selected === account.id} onClick={() => onSelect(account.id)}>
        <Icon size={18} aria-hidden="true" /><span><strong>{account.role}</strong><span>{account.name}</span><small>{account.description}</small></span>{selected === account.id && <Check size={16} aria-hidden="true" />}
      </button>;
    })}</div>
  </section>;
}
