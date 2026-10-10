'use client';

import {useEffect, useRef, useState} from 'react';
import Link from 'next/link';
import {ArrowDownToLine, ArrowLeft, ArrowRight, BookOpen, FlaskConical, RefreshCw, Shield} from 'lucide-react';
import {Card, CardContent, CardHeader} from '@/components/ui/card';
import {Badge} from '@/components/ui/badge';
import catalogue from '@/labs/catalogue.json';
import {MATERIAL_BYTE_LIMIT, RECORDING_BYTE_LIMIT, parseScenarioMaterials, parseScenarioRecording, readScenarioFile, scenarioLanguages, scenarioPatterns, type ScenarioEdge, type ScenarioEvent, type ScenarioLanguage, type ScenarioMaterial, type ScenarioPattern, type ScenarioRecording} from '@/lib/scenario-evidence';
import './scenarios.css';

type SourceCard = Omit<ScenarioMaterial, 'sourceHash' | 'archiveHash'> & {sourceHash: string | null; archiveHash: string | null};
const languages = Object.keys(scenarioLanguages) as ScenarioLanguage[];
const patterns = Object.keys(scenarioPatterns) as ScenarioPattern[];
// Source/setup metadata is build-time catalogue data, never execution evidence.
const sourceCatalogue: SourceCard[] = languages.flatMap(language => patterns.map(pattern => {
  const languageInfo = catalogue.languages[language], patternInfo = catalogue.patterns[pattern];
  return {id: `${language}-${pattern}`, language, pattern, title: `${languageInfo.label} · ${patternInfo.title}`, summary: patternInfo.summary, sourceHash: null, archiveHash: null, sourceUrl: `/labs/sources/${language}.tar.gz`, recordingUrl: null, setup: `Requires ${languageInfo.requires}${pattern === 'cache' ? ' and Redis 7.2.16 on 127.0.0.1' : ''}. Set LAB_RUN_ID to a new UUID and LAB_PORT=8100. Run ${languageInfo.command}. See the source package README for fixture setup and verification.`, scenarios: patternInfo.scenarios, edges: patternInfo.edges as ScenarioEdge[]};
}));
const problemText = (problem: unknown) => problem instanceof Error ? problem.message : 'The evidence could not be read. Retry to check again.';

function Architecture({edges, event}: {edges: ScenarioEdge[]; event?: ScenarioEvent}) {
  return <figure className="scenario-architecture">
    <figcaption>{event ? 'Observed transition' : 'Source architecture preview'}</figcaption>
    <div className="scenario-edges" aria-hidden="true">{edges.map(edge => <div key={edge.id} className={'scenario-edge' + (event?.edge === edge.id ? ' observed' : '')} data-observed={event?.edge === edge.id ? 'true' : 'false'}>
      <span className="scenario-node">{edge.from}</span><span className="scenario-connector"><ArrowRight size={20} /><span>{edge.label}</span>{event?.edge === edge.id && <strong>Observed · #{event.sequence}</strong>}</span><span className="scenario-node">{edge.to}</span>
    </div>)}</div>
    <ul className="scenario-architecture-text">{edges.map(edge => <li key={edge.id}><strong>{edge.from} → {edge.to}:</strong> {edge.label}{event?.edge === edge.id ? ` · Observed in event #${event.sequence}: ${event.summary}` : ' · No selected observation'}</li>)}</ul>
  </figure>;
}

function RecordingDetails({recording, material}: {recording: ScenarioRecording; material: SourceCard}) {
  const [responseIndex, setResponseIndex] = useState(0), [eventIndex, setEventIndex] = useState(0);
  const response = recording.responses[responseIndex], event = response.events[eventIndex];
  function chooseResponse(index: number) {setResponseIndex(index); setEventIndex(0);}
  return <div className="scenario-replay">
    <div className="scenario-notice"><BookOpen size={20} aria-hidden="true"/><p><strong>Replay · Recorded local execution</strong><br/>These controls inspect the original recording. No request or hosted run starts here.</p></div>
    <dl className="scenario-metadata"><div><dt>Original recording time</dt><dd><time dateTime={recording.recordedAt}>{recording.recordedAt}</time></dd></div><div><dt>Local run ID</dt><dd><code>{recording.runId}</code></dd></div><div><dt>Recorded source SHA-256</dt><dd><code>{recording.sourceHash}</code></dd></div></dl>
    <div className="scenario-field"><label htmlFor="scenario-response">Recorded response</label><select id="scenario-response" value={responseIndex} onChange={e => chooseResponse(Number(e.target.value))}>{recording.responses.map((item, index) => <option key={item.requestId} value={index}>{index + 1} of {recording.responses.length} · HTTP {item.status} · {item.events[0].type}</option>)}</select></div>
    <div className="scenario-actions"><button className="scenario-button" disabled={responseIndex === 0} onClick={() => chooseResponse(responseIndex - 1)}><ArrowLeft size={18} aria-hidden="true"/>Previous response</button><button className="scenario-button" disabled={responseIndex === recording.responses.length - 1} onClick={() => chooseResponse(responseIndex + 1)}>Next response<ArrowRight size={18} aria-hidden="true"/></button></div>
    <div className="scenario-response-summary" role="status" aria-live="polite"><strong>Response {responseIndex + 1} of {recording.responses.length}</strong><span>HTTP {response.status}</span><span>Recorded duration: {response.durationMs} ms</span></div>
    <p className="scenario-muted">Request ID: <code>{response.requestId}</code></p>
    <div className="scenario-state"><h3>Actual returned state</h3><dl>{Object.entries(response.state).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl></div>
    <div className="scenario-field"><label htmlFor="scenario-event">Observed event</label><select id="scenario-event" value={eventIndex} onChange={e => setEventIndex(Number(e.target.value))}>{response.events.map((item, index) => <option key={item.sequence} value={index}>#{item.sequence} · {item.type}</option>)}</select></div>
    <div className="scenario-actions"><button className="scenario-button" disabled={eventIndex === 0} onClick={() => setEventIndex(eventIndex - 1)}><ArrowLeft size={18} aria-hidden="true"/>Previous event</button><button className="scenario-button" disabled={eventIndex === response.events.length - 1} onClick={() => setEventIndex(eventIndex + 1)}>Next event<ArrowRight size={18} aria-hidden="true"/></button></div>
    <div className="scenario-observation" role="status" aria-live="polite"><strong>#{event.sequence} · {event.type}</strong><p>{event.summary}</p><time dateTime={event.recordedAt}>{event.recordedAt}</time></div>
    <Architecture edges={material.edges} event={event}/>
    <h3>Every recorded transition</h3>
    <ol className="scenario-transitions">{recording.responses.flatMap((item, index) => item.events.map(transition => {
      const edge = material.edges.find(candidate => candidate.id === transition.edge)!;
      return <li key={transition.sequence} aria-current={event.sequence === transition.sequence ? 'step' : undefined}><strong>#{transition.sequence} · {transition.type}</strong><p>{edge.from} → {edge.to} ({edge.label}). {transition.summary}</p><span>Response {index + 1} · HTTP {item.status} · {item.durationMs} ms · <time dateTime={transition.recordedAt}>{transition.recordedAt}</time></span></li>;
    }))}</ol>
    <details className="scenario-raw"><summary>Actual recorded response JSON</summary><pre>{JSON.stringify(response, null, 2)}</pre></details>
  </div>;
}

/** Header and responsive dashboard grid adapted from pinned ShadcnStore dashboard-2. MIT: docs/v48/License.md. */
export default function ScenarioDashboard() {
  const [materials, setMaterials] = useState<ScenarioMaterial[] | null>(null), [loading, setLoading] = useState(true), [indexError, setIndexError] = useState('');
  const [language, setLanguage] = useState('all'), [pattern, setPattern] = useState('all'), [selected, setSelected] = useState('java-cache'), [view, setView] = useState<'source' | 'recording'>('source');
  const [recording, setRecording] = useState<ScenarioRecording | null>(null), [recordingError, setRecordingError] = useState(''), [indexRetry, setIndexRetry] = useState(0), [recordingRetry, setRecordingRetry] = useState(0);
  const items = materials?.length ? materials : sourceCatalogue;
  const filtered = items.filter(item => (language === 'all' || item.language === language) && (pattern === 'all' || item.pattern === pattern));
  const material = filtered.find(item => item.id === selected) ?? filtered[0];
  const currentRecording = recording?.labId === material?.id && recording?.sourceHash === material?.sourceHash ? recording : null;
  const recordingLoading = view === 'recording' && !!material?.recordingUrl && !currentRecording && !recordingError;
  const indexAlert = useRef<HTMLDivElement>(null), recordingAlert = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    async function load() {
      try {
        const response = await fetch('/labs/material-index.json', {signal: AbortSignal.any([controller.signal, AbortSignal.timeout(15000)]), cache: 'no-store'});
        const next = parseScenarioMaterials(await readScenarioFile(response, MATERIAL_BYTE_LIMIT));
        if (active) setMaterials(next);
      } catch (problem) {if (active) {setMaterials(null); setIndexError(problemText(problem));}}
      finally {if (active) setLoading(false);}
    }
    void load(); return () => {active = false; controller.abort();};
  }, [indexRetry]);
  useEffect(() => {if (indexError && indexRetry > 0) indexAlert.current?.focus();}, [indexError, indexRetry]);
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    if (view !== 'recording' || !material?.recordingUrl || !material.sourceHash) return () => controller.abort();
    const current = material as ScenarioMaterial;
    async function load() {
      try {
        const response = await fetch(current.recordingUrl!, {signal: AbortSignal.any([controller.signal, AbortSignal.timeout(15000)]), cache: 'no-store'});
        const next = parseScenarioRecording(await readScenarioFile(response, RECORDING_BYTE_LIMIT), current);
        if (active) {setRecording(next); setRecordingError('');}
      } catch (problem) {if (active) setRecordingError(problemText(problem));}
    }
    void load(); return () => {active = false; controller.abort();};
  }, [material, view, recordingRetry]);
  useEffect(() => {if (recordingError) recordingAlert.current?.focus();}, [recordingError]);
  function choose(id: string) {setSelected(id); setView('source'); setRecording(null); setRecordingError('');}
  function filter(kind: 'language' | 'pattern', value: string) {if (kind === 'language') setLanguage(value); else setPattern(value); setView('source'); setRecording(null); setRecordingError('');}
  function retryIndex() {setLoading(true); setIndexError(''); setRecording(null); setRecordingError(''); setIndexRetry(value => value + 1);}
  function openRecording() {if (view === 'recording') return; setRecording(null); setRecordingError(''); setView('recording');}
  function retryRecording() {setRecording(null); setRecordingError(''); setRecordingRetry(value => value + 1);}
  const available = materials?.filter(item => item.recordingUrl).length ?? 0;
  return <main id="main" className="scenario-shell">
    <header className="scenario-page-header"><div><span className="scenario-kicker"><FlaskConical size={18} aria-hidden="true"/>Backend scenarios</span><h1>Read the source. Inspect the evidence.</h1><p>Nine free Java, TypeScript and Python labs for Redis, authorization and a payment emulator. Recorded evidence comes from local verifier runs.</p></div><Link className="scenario-button" href="/labs"><ArrowLeft size={18} aria-hidden="true"/>Lab workbench</Link></header>
    <div className="scenario-notice"><Shield size={22} aria-hidden="true"/><p><strong>Hosted runtime unavailable</strong><br/>Local recordings show observed fixture behavior. Hosted isolation and readiness gates remain open. Synthetic authorization uses no platform identity; the payment emulator never moves money.</p></div>
    <div className="scenario-overview"><Card><CardContent><span>Free source labs</span><strong>{sourceCatalogue.length}</strong><p>{languages.length} languages × {patterns.length} patterns</p></CardContent></Card><Card><CardContent><span>Build-listed recordings</span><strong>{loading ? '…' : available}</strong><p>{loading ? 'Checking material index' : 'Recorded local execution only'}</p></CardContent></Card><Card><CardContent><span>Hosted runtime</span><strong>Unavailable</strong><p>Source and setup stay free</p></CardContent></Card></div>
    {loading && <p className="scenario-notice" role="status">Loading build-generated source versions and recording availability…</p>}
    {indexError && <div ref={indexAlert} className="scenario-notice scenario-error" role="alert" tabIndex={-1}><div><strong>Material index unavailable</strong><p>{indexError} Source catalogue and local setup remain available below; package versions are unconfirmed.</p><button className="scenario-button" disabled={loading} onClick={retryIndex}><RefreshCw size={18} aria-hidden="true"/>Retry material index</button></div></div>}
    {!loading && materials?.length === 0 && <p className="scenario-notice" role="status">No built materials are listed. The source catalogue and setup remain available; no recordings are shown.</p>}
    <div className="scenario-filters"><div className="scenario-field"><label htmlFor="scenario-language">Language</label><select id="scenario-language" value={language} onChange={e => filter('language', e.target.value)}><option value="all">All languages</option>{languages.map(key => <option key={key} value={key}>{scenarioLanguages[key]}</option>)}</select></div><div className="scenario-field"><label htmlFor="scenario-pattern">Pattern</label><select id="scenario-pattern" value={pattern} onChange={e => filter('pattern', e.target.value)}><option value="all">All patterns</option>{patterns.map(key => <option key={key} value={key}>{scenarioPatterns[key]}</option>)}</select></div><p role="status" aria-live="polite">{filtered.length} source {filtered.length === 1 ? 'lab' : 'labs'}</p><button className="scenario-button" disabled={language === 'all' && pattern === 'all'} onClick={() => {setLanguage('all'); setPattern('all'); setView('source');}}>Clear filters</button></div>
    <div className="scenario-dashboard-grid"><section aria-labelledby="scenario-catalogue-heading"><h2 id="scenario-catalogue-heading">Source catalogue</h2><div className="scenario-cards">{filtered.map(item => <Card key={item.id} className={'scenario-lab-card' + (material?.id === item.id ? ' selected' : '')}><CardContent><div className="scenario-card-body"><span className="scenario-card-language">{scenarioLanguages[item.language]}</span><h3><button className="scenario-card-select" aria-pressed={material?.id === item.id} onClick={() => choose(item.id)}>{item.title}<ArrowRight size={18} aria-hidden="true"/></button></h3><p>{item.summary}</p><Badge variant="outline">{item.recordingUrl && !(item.id === material?.id && recordingError) ? 'Recorded local execution' : 'Source only'}</Badge></div><a className="scenario-download" href={item.sourceUrl} download><ArrowDownToLine size={17} aria-hidden="true"/>Free {scenarioLanguages[item.language]} source</a></CardContent></Card>)}</div>{!filtered.length && <div className="scenario-empty"><h3>No labs match these filters</h3><p>Clear the language or pattern filter to explore the source catalogue.</p></div>}</section>
    <section className="scenario-inspector" aria-labelledby="scenario-inspector-heading"><Card><CardHeader><div className="scenario-detail-heading"><div><span className="scenario-kicker">Evidence inspector</span><h2 id="scenario-inspector-heading">{material?.title ?? 'Choose a source lab'}</h2></div><Badge variant="outline">Hosted runtime unavailable</Badge></div>{material && <div className="scenario-view-controls" aria-label="Inspector view"><button className="scenario-button" aria-pressed={view === 'source'} onClick={() => setView('source')}>Source & setup</button><button className="scenario-button" aria-pressed={view === 'recording'} onClick={openRecording}>Recorded evidence</button></div>}</CardHeader><CardContent>{material ? <>
      {view === 'source' ? <div className="scenario-source"><p className="scenario-muted">{material.summary}</p><Badge variant="outline">{material.recordingUrl && !recordingError ? 'Recorded local execution available' : 'Source only'}</Badge><h3>Run the fixture locally</h3><p className="scenario-setup">{material.setup}</p><a className="scenario-button scenario-primary" href={material.sourceUrl} download><ArrowDownToLine size={18} aria-hidden="true"/>Download free source & setup</a><p className="scenario-muted">Each language archive includes all three patterns, fixtures and verification scripts. Execution uses your local environment.</p><dl className="scenario-metadata"><div><dt>Source SHA-256</dt><dd>{material.sourceHash ? <code>{material.sourceHash}</code> : 'Unavailable until the material index loads'}</dd></div><div><dt>Archive SHA-256</dt><dd>{material.archiveHash ? <code>{material.archiveHash}</code> : 'Unavailable until the material index loads'}</dd></div></dl><h3>Included scenarios</h3><ul className="scenario-scenarios">{material.scenarios.map(name => <li key={name}>{name}</li>)}</ul><Architecture edges={material.edges}/></div> : <>
        {recordingLoading && <p className="scenario-notice" role="status">Loading original recorded evidence…</p>}
        {recordingError && <div ref={recordingAlert} className="scenario-notice scenario-error" role="alert" tabIndex={-1}><div><strong>Recording rejected or unavailable</strong><p>{recordingError} No event is highlighted. Source and setup remain free.</p><button className="scenario-button" onClick={retryRecording}><RefreshCw size={18} aria-hidden="true"/>Retry recording</button></div></div>}
        {currentRecording && !recordingLoading && !recordingError && <RecordingDetails key={material.id + currentRecording.runId} recording={currentRecording} material={material}/>}
        {!material.recordingUrl && <div className="scenario-empty"><h3>Source only</h3><p>No matching verifier recording is available for this source version. Run the included local verification scripts to inspect the behavior.</p><button className="scenario-button" onClick={() => setView('source')}>Open source & setup</button></div>}
      </>}
    </> : <p className="scenario-muted">Clear the filters to select a lab and inspect its source architecture or original local recording.</p>}</CardContent></Card></section></div>
  </main>;
}
