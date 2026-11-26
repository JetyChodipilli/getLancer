
'use client';

import Link from 'next/link';
import {
  useState,
  useEffect,
  useRef,
  useCallback,
  type ReactNode,
  type Ref,
} from 'react';
import {Tabs,TabsList,TabsTrigger,TabsContent} from '@/components/ui/tabs';
import {Dialog,DialogContent,DialogHeader,DialogTitle,DialogDescription} from '@/components/ui/dialog';
import {api,ApiError} from '@/lib/api';
import AdminMarketplaceMetrics,{type MarketplaceMetricsData} from './admin-marketplace-metrics';
import DeliveryOperations from './delivery-operations';
import MaintenanceOperations from './maintenance-operations';
import HostingOperations from './hosting-operations';
import TemplateOperations from './template-operations';
import PrivacyOperations from './privacy-operations';
import ReportTriage from './report-triage';
import ModerationWorkflow from './moderation-workflow';
import TeamModeration from './team-moderation';
import MoreRecords,{type PageInfo} from './more-records';

function uncertain(problem:unknown){
  return !(problem instanceof ApiError)||
    problem.status===0||
    problem.status>=500;
}

type Metrics={
  failedEmails:number;
  weekly:{
    week:string;
    qualified:number;
    hires:number;
    completed:number;
  }[];
  sources?:{
    source:string;
    qualified:number;
    hires:number;
  }[];
  reportedValues?:{
    currency:string;
    total:number;
    proposals:number;
  }[];
  marketplace?:MarketplaceMetricsData;
};

type TaxonomyItem={
  slug:string;
  name:string;
  active:boolean;
};

type AuditEntry={
  id:string;
  target_type:string;
  action:string;
  reason:string;
  created_at:string;
  target_id:string;
};

type Account={
  id:string;
  email:string;
  account_status:string;
  moderation_reason?:string;
};

const centerTabs = [
 ['community','Community'], ['moderation','Moderation'], ['payments','Payments'],
 ['publishing','Publishing'], ['analytics','Analytics'],
] as const;

const locations: Record<string,[string,string?]> = {
 'admin-accounts':['community','accounts'],
 'admin-teams':['moderation','teams'],
 'admin-audit':['moderation','history'],
 'admin-email-jobs':['moderation','appeals'],
 'delivery-disputes-title':['payments','disputes'],
 'payment-attention-title':['payments','disputes'],
 'operator-metrics-title':['analytics'],
 'moderation-queue':['moderation','queue'],
 'moderation-projects':['moderation','queue'],
 'moderation-reports':['moderation','queue'],
};

function openPanel(wrapper: HTMLDivElement | null) {
  const panel = wrapper?.firstElementChild;
  if (panel instanceof HTMLDetailsElement) panel.open = true;
}

function AdminPanel({title,children,titleRef}:{title:string;children:ReactNode;titleRef?:Ref<HTMLHeadingElement>}) {
  return <section className="control-center-tool">
    <h3 ref={titleRef} tabIndex={-1}>{title}</h3>
    <div className="control-center-open" ref={openPanel}>{children}</div>
  </section>;
}

export default function AdminTools({
  request=api,
  onDenied,
  reload,
  queue,
}:{
  request?:typeof api;
  onDenied?:(problem:unknown)=>void;
  reload?:()=>Promise<boolean>;
  queue?:ReactNode;
}){

  const [section,setSection]=useState(queue?'moderation':'community');
  const [community,setCommunity]=useState('taxonomy'),[moderation,setModeration]=useState('queue');
  const [payments,setPayments]=useState('disputes'),[publishing,setPublishing]=useState('source');
  const hostingTitle=useRef<HTMLHeadingElement>(null);
  const [taxonomySearch,setTaxonomySearch]=useState(''),[editor,setEditor]=useState<TaxonomyItem|'new'|null>(null);
  const editorButton=useRef<HTMLButtonElement|null>(null);
  useEffect(()=>{
   function selectDestination() {
    const destination=locations[location.hash.slice(1)];
    if (!destination) return;
    const [tab,tool]=destination;
    setSection(tab);
    if(tool&&tab==='community')setCommunity(tool);
    if(tool&&tab==='payments')setPayments(tool);
    if(tool&&tab==='moderation')setModeration(tool);
   }
   selectDestination();
   window.addEventListener('hashchange',selectDestination);
   return()=>window.removeEventListener('hashchange',selectDestination);
  },[]);

  const [metrics,setMetrics]=useState<Metrics|null>(null);
  const [kind,setKind]=useState('categories');
  const [items,setItems]=useState<TaxonomyItem[]>([]);
  const [history,setHistory]=useState<AuditEntry[]>([]);
  const [accounts,setAccounts]=useState<Account[]>([]);
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState('');

  const [needsReload,setNeedsReload]=useState(false);
  const [recordsReady,setRecordsReady]=useState(false);
  const [taxonomyReady,setTaxonomyReady]=useState(false);

  const [historyPage,setHistoryPage]=useState<PageInfo|null>(null);
  const [historyEpoch,setHistoryEpoch]=useState(0);
  const [searching,setSearching]=useState(false);

  const [authorityEpoch,setAuthorityEpoch]=useState(0);
  const [auditRevision,setAuditRevision]=useState(0);

  const mounted=useRef(true);
  const authority=useRef(0);
  const auditVersion=useRef(0);
  const taxonomyVersion=useRef(0);
  const searchVersion=useRef(0);
  const pending=useRef(false);
  const currentKind=useRef(kind);

  const invalidateAuthority=useCallback(()=>{
    authority.current++;
  },[]);

  useEffect(()=>{
    mounted.current=true;

    return()=>{
      mounted.current=false;
      invalidateAuthority();
    };
  },[invalidateAuthority]);

  function failed(problem:unknown){
    if(
      problem instanceof ApiError&&
      [401,403].includes(problem.status)
    ){
      authority.current++;
      setAuthorityEpoch(authority.current);
      searchVersion.current++;

      setMetrics(null);
      setEditor(null);
      setItems([]);
      setHistory([]);
      setAccounts([]);
      setHistoryPage(null);
      setMessage('');
      setSearching(false);
      setRecordsReady(false);
      setTaxonomyReady(false);
    }

    setError((problem as Error).message);
  }

  async function loadTaxonomy(selectedKind=kind){
    const run=authority.current;
    const version=++taxonomyVersion.current;

    const next:{items:TaxonomyItem[]}=await request(
      '/admin/'+selectedKind
    );

    if(
      mounted.current&&
      run===authority.current&&
      version===taxonomyVersion.current&&
      selectedKind===currentKind.current
    ){
      setItems(next.items);
      setTaxonomyReady(true);
    }
  }

  async function refreshControls(){
    if(pending.current)return;

    pending.current=true;

    setBusy(true);
    setError('');
    setMessage('');

    const run=authority.current;
    const version=++auditVersion.current;

    setAuditRevision(version);

    try{
      const [nextMetrics,audit]:[
        Metrics,
        PageInfo&{items:AuditEntry[]},
        void
      ]=await Promise.all([
        request('/admin/metrics'),
        request('/admin/audit'),
        loadTaxonomy()
      ]);

      if(reload&&!await reload()){
        throw new Error(
          'Current workspace details could not be refreshed.'
        );
      }

      if(
        mounted.current&&
        run===authority.current&&
        version===auditVersion.current
      ){
        setMetrics(nextMetrics);
        setHistory(audit.items);
        setHistoryPage(audit);
        setHistoryEpoch(value=>value+1);
        setRecordsReady(true);
        setNeedsReload(false);
      }
    }catch(problem){
      if(mounted.current&&run===authority.current){
        setNeedsReload(true);
        failed(problem);
      }
    }finally{
      pending.current=false;

      if(mounted.current)setBusy(false);
    }
  }

  useEffect(()=>{
    let live=true;

    const run=authority.current;
    const version=++taxonomyVersion.current;

    queueMicrotask(()=>{
      if(!live)return;

      setTaxonomyReady(false);
      setItems([]);
      setError('');

      void request('/admin/'+kind)
        .then((r:{items:TaxonomyItem[]})=>{
          if(
            live&&
            run===authority.current&&
            version===taxonomyVersion.current
          ){
            setItems(r.items);
            setTaxonomyReady(true);
          }
        })
        .catch(problem=>{
          if(
            live&&
            run===authority.current&&
            version===taxonomyVersion.current
          ){
            setItems([]);
            failed(problem);
          }
        });
    });

    return()=>{
      live=false;
    };
  },[kind,request]);

  useEffect(()=>{
    let live=true;

    const run=authority.current;
    const version=++auditVersion.current;

    queueMicrotask(()=>{
      if(!live)return;

      setAuditRevision(version);

      void Promise.all([
        request('/admin/metrics'),
        request('/admin/audit')
      ])
      .then(([nextMetrics,audit]:[
        Metrics,
        PageInfo&{items:AuditEntry[]}
      ])=>{
        if(
          live&&
          mounted.current&&
          run===authority.current&&
          version===auditVersion.current
        ){
          setMetrics(nextMetrics);
          setHistory(audit.items);
          setHistoryPage(audit);
          setHistoryEpoch(value=>value+1);
          setRecordsReady(true);
        }
      })
      .catch(problem=>{
        if(
          live&&
          mounted.current&&
          run===authority.current
        ){
          failed(problem);
        }
      });
    });

    return()=>{
      live=false;
    };
  },[request]);

  async function mutate(
    path:string,
    body:Record<string,unknown>,
    refresh?:()=>Promise<void>
  ){
    if(
      pending.current||
      needsReload||
      !recordsReady||
      !taxonomyReady
    )return false;

    pending.current=true;

    const run=authority.current;
    const version=++auditVersion.current;

    setAuditRevision(version);
    taxonomyVersion.current++;

    let saved=false;

    setBusy(true);
    setError('');
    setMessage('');

    try{
      await request(path,{
        method:'POST',
        body:JSON.stringify(body)
      });

      saved=true;

      setNeedsReload(true);

      if(
        !mounted.current||
        run!==authority.current
      )return false;

      const audit:PageInfo&{items:AuditEntry[]}=
        await request('/admin/audit');

      await refresh?.();

      if(reload&&!await reload()){
        throw new Error(
          'Current workspace details could not be refreshed.'
        );
      }

      if(
        !mounted.current||
        run!==authority.current||
        version!==auditVersion.current
      )return false;

      setHistory(audit.items);
      setHistoryPage(audit);
      setHistoryEpoch(value=>value+1);
      setNeedsReload(false);
      setMessage('Saved and current records refreshed.');

      return true;

    }catch(problem){
      if(mounted.current&&run===authority.current){
        failed(problem);

        if(saved||uncertain(problem)){
          setNeedsReload(true);

          setMessage(
            saved
              ?'The change was recorded. Refresh community controls before another change.'
              :'This change could not be confirmed. Refresh community controls before trying again.'
          );
        }

        if(saved){
          setError(
            'The change was recorded, but current records could not be refreshed. Refresh before repeating it. '+
            (problem as Error).message
          );
        }
      }

      return false;

    }finally{
      pending.current=false;

      if(mounted.current)setBusy(false);
    }
  }

  async function saveTaxonomy(
    body:Record<string,unknown>
  ){
    const selectedKind=kind;

    if(
      await mutate(
        '/admin/'+selectedKind,
        body,
        ()=>loadTaxonomy(selectedKind)
      )
    ){
      setEditor(null);
    }
  }

  async function findAccounts(query:string){
    const version=++searchVersion.current;
    const run=authority.current;

    setSearching(true);
    setError('');
    setMessage('');
    setAccounts([]);

    try{
      const next:{items:Account[]}=await request(
        '/admin/accounts?q='+encodeURIComponent(query)
      );

      if(
        mounted.current&&
        run===authority.current&&
        version===searchVersion.current
      ){
        setAccounts(next.items);
      }
    }catch(problem){
      if(
        mounted.current&&
        run===authority.current&&
        version===searchVersion.current
      ){
        failed(problem);
      }
    }finally{
      if(
        mounted.current&&
        version===searchVersion.current
      ){
        setSearching(false);
      }
    }
  }

 const renderAuthority=authorityEpoch,renderAudit=auditRevision,blocked=busy||needsReload||!recordsReady||!taxonomyReady;
 const search = taxonomySearch.trim().toLowerCase();
 const visibleItems = items.filter(item=>!search||item.name.toLowerCase().includes(search)||item.slug.includes(search));
 const taxonomyTitle = kind==='categories'?'category':'technology';

 return <section className="control-center" aria-labelledby="control-center-title">
  <header className="control-center-heading">
   <h2 id="control-center-title">Control center</h2>
   <div className="stack">
    <Link className="button" href="/workspace/operations">Operations &amp; release readiness</Link>
    <button type="button" className="button" disabled={busy||searching} onClick={()=>void refreshControls()}>Refresh community controls</button>
   </div>
  </header>
  {!editor&&error&&<p className="error" role="alert">{error}</p>}
  {message&&<p className="success" role="status">{message}</p>}
  <Tabs value={section} onValueChange={setSection} className="control-center-tabs">
   <TabsList variant="line" aria-label="Control center sections">
    {centerTabs.map(([value,label])=><TabsTrigger key={value} value={value} disabled={busy}>{label}</TabsTrigger>)}
   </TabsList>
   <TabsContent value="community">
    <Tabs value={community} onValueChange={setCommunity} className="control-center-subtabs">
     <TabsList aria-label="Community tools">
      <TabsTrigger value="taxonomy" disabled={busy}>Taxonomy</TabsTrigger>
      <TabsTrigger value="accounts" disabled={busy}>Accounts</TabsTrigger>
      <TabsTrigger value="inquiries" disabled={busy}>Inquiry restrictions</TabsTrigger>
     </TabsList>
     <TabsContent value="taxonomy">
      <section className="control-center-tool">
       <h3>Categories and technologies</h3>
       <p className="muted">Choose a record to edit the name people see in project search.</p>
       <div className="control-center-toolbar">
        <Tabs value={kind} onValueChange={value=>{currentKind.current=value;taxonomyVersion.current++;setTaxonomyReady(false);setTaxonomySearch('');setKind(value);}}>
         <TabsList aria-label="Taxonomy type">
          <TabsTrigger value="categories" disabled={busy||needsReload}>Categories</TabsTrigger>
          <TabsTrigger value="technologies" disabled={busy||needsReload}>Technologies</TabsTrigger>
         </TabsList>
        </Tabs>
        <label className="showcase-field">Search {kind}
         <input type="search" value={taxonomySearch} onChange={event=>setTaxonomySearch(event.target.value)}/>
        </label>
        <button type="button" className="button primary" disabled={blocked} onClick={event=>{editorButton.current=event.currentTarget;setError('');setEditor('new');}}>New {taxonomyTitle}</button>
       </div>
       <div className="control-center-table" role="region" aria-label={kind} tabIndex={0}>
        <table>
         <thead><tr><th scope="col">Name</th><th scope="col">Status</th><th scope="col">Action</th></tr></thead>
         <tbody>{visibleItems.map(item=><tr key={item.slug}>
          <td><strong>{item.name}</strong><small>{item.slug}</small></td>
          <td>{item.active?'Active':'Inactive'}</td>
          <td><button type="button" className="button" aria-label={'Edit '+item.name} disabled={blocked} onClick={event=>{editorButton.current=event.currentTarget;setError('');setEditor(item);}}>Edit</button></td>
         </tr>)}</tbody>
        </table>
       </div>
       {taxonomyReady&&!visibleItems.length&&<p className="muted">No matching {kind}.</p>}
       {!taxonomyReady&&!error&&<p role="status">Loading {kind}…</p>}
      </section>
     </TabsContent>
     <TabsContent value="accounts">
      <section id="admin-accounts" className="control-center-tool">
       <h3>Account enforcement</h3>
       <button type="button" className="button" onClick={()=>{setModeration('teams');setSection('moderation');}}>Open team moderation</button>
       <form className="form control-center-form" onSubmit={e=>{e.preventDefault();void findAccounts(String(new FormData(e.currentTarget).get('q')));}}>
        <label className="showcase-field">Find by email<input name="q" type="email" disabled={blocked||searching} required/></label>
        <button className="button" disabled={blocked||searching}>Find account</button>
       </form>
       {accounts.map(a=><form key={a.id} className="form panel control-center-form" onSubmit={async e=>{e.preventDefault();const body=Object.fromEntries(new FormData(e.currentTarget));if(await mutate('/admin/accounts/'+a.id+'/'+body.action,{reason:body.reason}))setAccounts([]);}}>
        <h4>{a.email}</h4><p>{a.account_status}{a.moderation_reason?' · '+a.moderation_reason:''}</p>
        <label className="showcase-field">Action<select name="action" disabled={blocked}><option value="suspend">Suspend account and revoke sessions</option><option value="restore">Restore account access</option></select></label>
        <label className="showcase-field">Reason<textarea name="reason" disabled={blocked} required minLength={3} maxLength={2000}/></label>
        <button className="button danger" disabled={blocked}>Apply account decision</button>
       </form>)}
      </section>
     </TabsContent>
     <TabsContent value="inquiries">
      <section className="control-center-tool">
       <h3>Inquiry restrictions</h3>
       <form className="form control-center-form" onSubmit={async e=>{e.preventDefault();const data=Object.fromEntries(new FormData(e.currentTarget));await mutate('/admin/inquiries/'+encodeURIComponent(String(data.id))+'/'+data.action,{reason:data.reason});}}>
        <label className="showcase-field">Inquiry ID<input name="id" disabled={blocked} required/></label>
        <label className="showcase-field">Action<select name="action" disabled={blocked}><option value="quarantine">Quarantine for investigation</option><option value="block">Block confirmed abuse</option><option value="restore">Restore after review</option></select></label>
        <label className="showcase-field">Reason<textarea name="reason" disabled={blocked} required minLength={3} maxLength={2000}/></label>
        <button className="button" disabled={blocked}>Record decision</button>
       </form>
      </section>
     </TabsContent>
    </Tabs>
   </TabsContent>
   <TabsContent value="moderation">
    <Tabs value={moderation} onValueChange={setModeration} className="control-center-subtabs">
     <TabsList aria-label="Moderation tools">
      {[['queue','Review queue'],['triage','Report triage'],['teams','Teams'],['appeals','Appeals and email'],['privacy','Privacy'],['history','Audit log']].map(([value,label])=><TabsTrigger key={value} value={value}>{label}</TabsTrigger>)}
     </TabsList>
     <TabsContent value="queue"><div className="control-center-queue">{queue}</div></TabsContent>
     <TabsContent value="triage"><AdminPanel title="Report priority and triage"><ReportTriage request={request} reload={reload}/></AdminPanel></TabsContent>
     <TabsContent value="teams"><AdminPanel title="Teams and studios"><TeamModeration request={request} reload={reload}/></AdminPanel></TabsContent>
     <TabsContent value="appeals"><AdminPanel title="Moderation appeals and delivery"><ModerationWorkflow admin request={request} reload={reload}/></AdminPanel></TabsContent>
     <TabsContent value="privacy"><AdminPanel title="Account deletion review"><PrivacyOperations request={request} reload={reload}/></AdminPanel></TabsContent>
     <TabsContent value="history"><section id="admin-audit" className="control-center-tool">
      <h3>Moderation history</h3>
      {history.map(h=><article className="row" key={h.id}><div><strong>{h.target_type} · {h.action}</strong><p>{h.reason}</p><p className="muted">{new Date(h.created_at).toLocaleString()} · {h.target_id}</p></div></article>)}
      {historyPage&&!busy&&!needsReload&&<MoreRecords<AuditEntry> request={request} key={historyEpoch} path="/admin/audit" firstPage={historyPage} label="Load more moderation history" onItems={rows=>{if(mounted.current&&renderAuthority===authority.current&&renderAudit===auditVersion.current)setHistory(old=>Array.from(new Map([...old,...rows].map(row=>[row.id,row])).values()));}} onPage={page=>{if(mounted.current&&renderAuthority===authority.current&&renderAudit===auditVersion.current)setHistoryPage(page);}} onError={problem=>{if(mounted.current&&renderAuthority===authority.current&&renderAudit===auditVersion.current)failed(problem);}}/>}
     </section></TabsContent>
    </Tabs>
   </TabsContent>
   <TabsContent value="payments">
    <Tabs value={payments} onValueChange={setPayments} className="control-center-subtabs">
     <TabsList aria-label="Payment tools"><TabsTrigger value="disputes">Disputes and payments</TabsTrigger><TabsTrigger value="maintenance">Maintenance billing</TabsTrigger></TabsList>
     <TabsContent value="disputes"><AdminPanel title="Delivery disputes and Razorpay operations"><DeliveryOperations request={request} reload={reload}/></AdminPanel></TabsContent>
     <TabsContent value="maintenance"><AdminPanel title="Maintenance billing and support"><MaintenanceOperations request={request} reload={reload}/></AdminPanel></TabsContent>
    </Tabs>
   </TabsContent>
   <TabsContent value="publishing">
    <Tabs value={publishing} onValueChange={setPublishing} className="control-center-subtabs">
     <TabsList aria-label="Publishing tools"><TabsTrigger value="source">Source marketplace</TabsTrigger><TabsTrigger value="hosting">Frontend hosting</TabsTrigger><TabsTrigger value="education">Education releases</TabsTrigger></TabsList>
     <TabsContent value="source"><AdminPanel title="Source marketplace review and recovery"><TemplateOperations request={request} onDenied={onDenied} reload={reload}/></AdminPanel></TabsContent>
     <TabsContent value="hosting"><AdminPanel title="Hosted frontend review and recovery" titleRef={hostingTitle}><HostingOperations request={request} onDenied={onDenied} reload={reload} fallbackFocus={hostingTitle}/></AdminPanel></TabsContent>
     <TabsContent value="education"><section className="control-center-tool"><h3>Education releases</h3><Link className="button" href="/admin/education">Review education releases</Link></section></TabsContent>
    </Tabs>
   </TabsContent>
   <TabsContent value="analytics">
    {metrics?.marketplace&&<AdminPanel title="Marketplace health and liquidity"><AdminMarketplaceMetrics metrics={metrics.marketplace}/></AdminPanel>}
    {metrics&&<section className="control-center-tool"><h3>Weekly marketplace outcomes</h3><p>Failed email jobs needing attention: {metrics.failedEmails}</p>
     <div className="control-center-table" role="region" aria-label="Weekly marketplace outcomes" tabIndex={0}>
      <table><thead><tr><th scope="col">Week</th><th scope="col">Qualified inquiries</th><th scope="col">Confirmed hires</th><th scope="col">Completed</th></tr></thead><tbody>{metrics.weekly.map(w=><tr key={w.week}><td>{new Date(w.week).toLocaleDateString()}</td><td>{w.qualified}</td><td>{w.hires}</td><td>{w.completed}</td></tr>)}</tbody></table>
     </div>
     {!!metrics.sources?.length&&<section><h4>Acquisition sources</h4>{metrics.sources.map(s=><p key={s.source}>{s.source.replaceAll('_',' ')} · {s.qualified} qualified inquiries · {s.hires} confirmed hires</p>)}</section>}
     {!!metrics.reportedValues?.length&&<section><h4>Reported proposal values</h4><p>Self-reported totals by currency.</p>{metrics.reportedValues.map(v=><p key={v.currency}>{v.currency} {Number(v.total).toLocaleString()} · {v.proposals} proposals</p>)}</section>}
    </section>}
   </TabsContent>
  </Tabs>
  <Dialog open={!!editor} onOpenChange={open=>{if(!open&&!busy)setEditor(null);}}>
   <DialogContent className="workspace-dialog control-center-dialog" onCloseAutoFocus={event=>{event.preventDefault();editorButton.current?.focus();}}>
    <DialogHeader>
     <DialogTitle>{editor==='new'?'New '+taxonomyTitle:'Edit '+(editor?.name||taxonomyTitle)}</DialogTitle>
     <DialogDescription>Choose how this {taxonomyTitle} appears in the catalog.</DialogDescription>
    </DialogHeader>
    {editor&&<form className="form control-center-form" key={kind+(editor==='new'?'new':editor.slug)} onSubmit={e=>{
     e.preventDefault();
     if(blocked)return;
     const values=new FormData(e.currentTarget);
     void saveTaxonomy({slug:editor==='new'?values.get('slug'):editor.slug,name:values.get('name'),active:values.get('active')==='on'});
    }}>
     {error&&<p className="error" role="alert">{error}</p>}
     {editor==='new'?<label className="showcase-field">Permanent slug<input name="slug" disabled={busy} pattern="[a-z0-9\-]+" minLength={2} maxLength={100} required/></label>:<p className="muted">Permanent slug · {editor.slug}</p>}
     <label className="showcase-field">Display name<input name="name" disabled={busy} defaultValue={editor==='new'?'':editor.name} required minLength={2} maxLength={100}/></label>
     <label className="control-center-checkbox"><input type="checkbox" name="active" disabled={busy} defaultChecked={editor==='new'||editor.active}/> Active in the catalog</label>
     <div className="stack control-center-dialog-actions">
      {needsReload&&<button type="button" className="button" disabled={busy} onClick={()=>void refreshControls()}>Refresh community controls</button>}
      <button type="button" className="button" disabled={busy} onClick={()=>setEditor(null)}>Cancel</button>
      <button type="submit" className="button primary" disabled={blocked}>{busy?'Saving…':'Save'}</button>
     </div>
    </form>}
   </DialogContent>
  </Dialog>
 </section>;
}
