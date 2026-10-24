'use client';

import {useState} from 'react';
import {ArrowRight} from 'lucide-react';
import catalogue from '@/labs/catalogue.json';
import type {LabObservation,LabRun} from '@/lib/labs';

/** Static reviewed topology; only an actual bound event can highlight its corresponding edge. */
export default function LiveRunEvidence({run,observations,live}:{run:LabRun;observations:LabObservation[];live:boolean}){
 const [selection,setSelection]=useState('');
 const transitions=observations.flatMap(response=>response.events.map(event=>({event,response})));
 const selected=transitions.find(item=>item.event.runId===run.id&&item.event.requestId+':'+item.event.sequence===selection)??transitions.at(-1);
 const pattern=selected?.response.pattern??run.scenarioId;
 const edges=Object.hasOwn(catalogue.patterns,pattern)?catalogue.patterns[pattern as keyof typeof catalogue.patterns].edges:[];
 return <section className="lab-evidence" aria-labelledby="lab-evidence-title">
  <h3 id="lab-evidence-title">Run architecture & observed state</h3>
  <p className="lab-hint" role="status">{live?'Live run · current isolated execution':'Recorded activity from this run · execution is not currently verified'}. This view uses responses from run {run.id}; it does not load a Replay recording.</p>
  {transitions.length?<>
   <div className="lab-field"><label htmlFor="lab-observed-transition">Observed transition</label><select id="lab-observed-transition" value={selected!.event.requestId+':'+selected!.event.sequence} onChange={event=>setSelection(event.target.value)}>{transitions.map(item=><option key={item.event.requestId+':'+item.event.sequence} value={item.event.requestId+':'+item.event.sequence}>#{item.event.sequence} · {item.event.type}</option>)}</select></div>
   <div className="lab-observed-status" role="status" aria-live="polite"><strong>Observed #{selected!.event.sequence} · {selected!.event.type}</strong><p>{selected!.event.summary}</p><time dateTime={selected!.event.recordedAt}>{selected!.event.recordedAt}</time></div>
   <dl className="lab-observed-state" aria-label="Actual returned state">{Object.entries(selected!.response.state).map(([key,value])=><div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl>
   <p className="lab-hint">HTTP {selected!.response.status} · {selected!.response.durationMs} ms · Source <code>{selected!.response.sourceHash}</code></p>
  </>:<p className="lab-muted">No native transition has been observed yet. Send a certified request or refresh activity. Unobserved architecture edges remain unhighlighted.</p>}
  {edges.length?<figure className="lab-observed-architecture"><figcaption>{selected?'Architecture with selected observed transition':'Architecture preview · no observed transition'}</figcaption><div className="lab-observed-edges" aria-hidden="true">{edges.map(edge=><div className="lab-observed-edge" key={edge.id} data-observed={selected?.event.edge===edge.id?'true':'false'}><span>{edge.from}</span><span><ArrowRight size={18}/>{edge.label}{selected?.event.edge===edge.id&&<strong>Observed · #{selected.event.sequence}</strong>}</span><span>{edge.to}</span></div>)}</div><ul>{edges.map(edge=><li key={edge.id}><strong>{edge.from} → {edge.to}:</strong> {edge.label}{selected?.event.edge===edge.id?' · Observed in event #'+selected.event.sequence+': '+selected.event.summary:' · No selected observation'}</li>)}</ul></figure>:<p className="lab-hint">This scenario has no reviewed native architecture map. Control-plane status is available below.</p>}
 </section>;
}
