'use client';

import Link from 'next/link';
import {ArrowRight, ChevronDown, Search} from 'lucide-react';
import {useEffect, useRef, useState, type KeyboardEvent, type PointerEvent} from 'react';
import {clientTools, trustTools, type WorkspaceTool, type WorkspaceToolGroup} from '@/lib/workspace-tools';
import NavIcon, {type NavIconName} from './nav-icon';

type Area = 'work' | 'clients' | 'trust' | 'teams';
type Menu = Area | 'search';
type OpenMenu = {id: Menu; via: 'pointer' | 'button'};

const areaNames: Record<Area, string> = {work: 'Work', clients: 'Clients', trust: 'Trust', teams: 'Teams'};
const areaIcons: Record<Area, NavIconName> = {work: 'workspace', clients: 'briefcase', trust: 'shield', teams: 'teams'};
const areaKeys = Object.keys(areaNames) as Area[];

function workspaceGroups(home: string, preview: boolean, operator: boolean): Record<Area, WorkspaceToolGroup[]> {
  return {
    work: [
      {group: 'Workspace', tools: [{route: home, name: 'Personal workspace', icon: 'workspace'}, ...(operator && !preview ? [{route: '/admin', name: 'Admin Dashboard', icon: 'workspace' as const}] : [])]},
      {group: 'Publish', tools: [
        {route: 'components', name: 'Components & slots', icon: 'components'},
        {route: 'slots', name: 'Publishing slots', icon: 'publish'},
        {route: 'college-projects', name: 'College projects', icon: 'college'},
      ]},
      {group: 'Demos', tools: [{route: 'hosting', name: 'Hosted frontend demos', icon: 'workspace'}]},
    ],
    clients: clientTools,
    trust: trustTools(preview, operator),
    teams: [
      {group: 'Studio', tools: [{route: 'teams?tab=members', name: 'Members', icon: 'teams'}]},
      {group: 'Recruitment', tools: [{route: 'teams?tab=recruitment', name: 'Open roles', icon: 'recruitment'}]},
      {group: 'Client leads', tools: [{route: 'teams?tab=leads', name: 'Client leads', icon: 'briefcase'}]},
      {group: 'Shared work', tools: [{route: 'teams?tab=projects', name: 'Shared projects', icon: 'components'}, {route: 'teams?tab=activity', name: 'Team activity', icon: 'activity'}]},
    ],
  };
}

export default function WorkspaceNavigation({path, preview, operator}: {path: string; preview: boolean; operator: boolean}) {
  const prefix = preview ? '/preview' : '/workspace';
  const home = preview ? '/preview/workspace' : prefix;
  const overview: Record<Area, string> = {work: home, clients: prefix + '/areas/clients', trust: prefix + '/areas/trust', teams: prefix + '/teams'};
  const groups = workspaceGroups(home, preview, operator);
  const hrefFor = (route: string) => route.startsWith('/') ? route : prefix + '/' + route;
  const activeArea = areaKeys.find(area => path === overview[area] || groups[area].some(group =>
    group.tools.some(tool => !tool.route.includes('?') && hrefFor(tool.route) === path))) ?? (path === prefix + '/operations' ? 'trust' : 'work');

  const [open, setOpen] = useState<OpenMenu | null>(null);
  const [query, setQuery] = useState('');
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const searchField = useRef<HTMLInputElement>(null);
  const panel = useRef<HTMLElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const focusPanel = useRef(false);

  useEffect(() => () => clearTimeout(timer.current), []);

  useEffect(() => {
    if (!open) return;
    if (open.id === 'search') searchField.current?.focus();
    else if (focusPanel.current) panel.current?.querySelector('a')?.focus();
    focusPanel.current = false;
    function dismiss(event: Event) {
      if (event.target instanceof Node && !root.current?.contains(event.target)) setOpen(null);
    }
    function escape(event: globalThis.KeyboardEvent) {
      if (event.key !== 'Escape') return;
      clearTimeout(timer.current);
      setOpen(null);
      trigger.current?.focus({preventScroll: true});
    }
    document.addEventListener('pointerdown', dismiss);
    document.addEventListener('focusin', dismiss);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('pointerdown', dismiss);
      document.removeEventListener('focusin', dismiss);
      document.removeEventListener('keydown', escape);
    };
  }, [open]);

  function toggle(id: Menu, button: HTMLButtonElement, focusFirst = false) {
    clearTimeout(timer.current);
    if (open?.id === id && open.via === 'button' && !focusFirst) {
      setOpen(null);
      return;
    }
    trigger.current = button;
    focusPanel.current = focusFirst;
    if (id === 'search') setQuery('');
    setOpen({id, via: 'button'});
  }

  function keyDown(event: KeyboardEvent<HTMLButtonElement>, id: Menu) {
    if (event.key !== 'ArrowDown') return;
    event.preventDefault();
    toggle(id, event.currentTarget, true);
  }

  function hover(event: PointerEvent<HTMLButtonElement>, id: Area) {
    // Use the pointer that entered the button. The primary input may be touch
    // even when a mouse is attached, and headless engines can report no hover.
    if (event.pointerType !== 'mouse') return;
    clearTimeout(timer.current);
    if (open?.via === 'button' || open?.id === id) return;
    const button = event.currentTarget;
    timer.current = setTimeout(() => {
      trigger.current = button;
      setOpen({id, via: 'pointer'});
    }, 130);
  }

  function keepHoverOpen(event: PointerEvent<HTMLElement>) {
    if (event.pointerType === 'mouse') clearTimeout(timer.current);
  }

  function leave(event: PointerEvent<HTMLElement>) {
    if (event.pointerType !== 'mouse') return;
    clearTimeout(timer.current);
    if (open?.via === 'pointer') {
      timer.current = setTimeout(() => {
        setOpen(menu => menu?.via === 'pointer' && !panel.current?.contains(document.activeElement) ? null : menu);
      }, 180);
    }
  }

  function toolLink(tool: WorkspaceTool) {
    const href = hrefFor(tool.route);
    return <Link prefetch={false} tabIndex={0} href={href} onClick={() => setOpen(null)}
      aria-current={path === href ? 'page' : undefined}>
      <NavIcon name={tool.icon}/><span>{tool.name}</span><ArrowRight size={15} aria-hidden="true"/>
    </Link>;
  }

  function areaPanel(area: Area) {
    return <section ref={panel} id={'workspace-menu-' + area} className="studio-tool-panel" aria-label={areaNames[area] + ' tools'} onPointerEnter={keepHoverOpen}>
      <div className="studio-panel-inner">
        <div className="studio-panel-heading">
          <h2>{areaNames[area]}</h2>
          <Link prefetch={false} tabIndex={0} href={overview[area]} aria-current={path === overview[area] ? 'page' : undefined}
            onClick={() => setOpen(null)}>{areaNames[area]} overview <ArrowRight size={16} aria-hidden="true"/></Link>
        </div>
        <div className="studio-menu-groups">{groups[area].filter(group => group.tools.length).map(({group, tools}) =>
          <section key={group}><h3>{group}</h3><ul>{tools.map(tool => <li key={tool.route}>{toolLink(tool)}</li>)}</ul></section>)}</div>
      </div>
    </section>;
  }

  const term = query.trim().toLowerCase();
  const results = areaKeys.flatMap(area => groups[area].flatMap(group => group.tools.filter(tool =>
    !term ? ['business', 'delivery', 'components', 'teams?tab=recruitment'].includes(tool.route)
      : [tool.name, tool.description ?? '', group.group, areaNames[area]].some(value => value.toLowerCase().includes(term)))));

  return <div ref={root} className="studio-header-controls" data-menu-open={open ? '' : undefined}
    onPointerEnter={keepHoverOpen} onPointerLeave={leave}>
    <nav className="studio-area-tabs" aria-label="Workspace navigation" onPointerEnter={keepHoverOpen}>
      {areaKeys.map(area => <div key={area} className="studio-switch-anchor">
        <button type="button" aria-expanded={open?.id === area} aria-controls={open?.id === area ? 'workspace-menu-' + area : undefined}
          aria-current={activeArea === area ? (path === overview[area] ? 'page' : 'location') : undefined}
          onClick={event => toggle(area, event.currentTarget)} onKeyDown={event => keyDown(event, area)} onPointerEnter={event => hover(event, area)}
          onPointerLeave={() => clearTimeout(timer.current)}>
          <NavIcon name={areaIcons[area]}/><span>{areaNames[area]}</span><ChevronDown className="studio-area-chevron" size={14} aria-hidden="true"/>
        </button>
        {open?.id === area && areaPanel(area)}
      </div>)}
    </nav>
    <div className="studio-tools-search" onPointerEnter={leave}>
      <button type="button" aria-label="Find a tool" aria-expanded={open?.id === 'search'} aria-controls={open?.id === 'search' ? 'workspace-menu-search' : undefined}
        onClick={event => toggle('search', event.currentTarget)} onKeyDown={event => keyDown(event, 'search')}>
        <NavIcon name="search"/><span>Find a tool</span>
      </button>
      {open?.id === 'search' && <section id="workspace-menu-search" className="studio-tool-panel" aria-label="Find a workspace tool">
        <div className="studio-panel-inner">
          <div className="studio-panel-heading"><h2>Find a workspace tool</h2></div>
          <label className="studio-menu-search-field"><Search size={18} aria-hidden="true"/><span className="sr-only">Search workspace tools</span>
            <input ref={searchField} type="search" placeholder="Try payments, publishing, or roles" value={query} onChange={event => setQuery(event.target.value)}/>
          </label>
          <div className="studio-menu-results">{results.length
            ? <ul>{results.map(tool => <li key={tool.route}>{toolLink(tool)}</li>)}</ul>
            : <p role="status">No tools match your search.</p>}</div>
        </div>
      </section>}
    </div>
    {open && <div className="studio-menu-scrim" aria-hidden="true" onPointerEnter={leave} onPointerDown={() => setOpen(null)}/>}
  </div>;
}
