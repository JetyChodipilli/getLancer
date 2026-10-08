'use client';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { componentSeeds, type ComponentEntry } from '@/lib/components';
import { ApiError } from '@/lib/api';
import FormErrors from './form-errors';

export default function ComponentDraftEditor({ entry, busy, error, onSave }: { entry: ComponentEntry | null; busy: boolean; error: ApiError | null; onSave: (event: FormEvent<HTMLFormElement>) => void }) {
  const [step, setStep] = useState(0);
  const contribution = useRef<HTMLTextAreaElement>(null);
  const title = useRef<HTMLInputElement>(null);
  const moved = useRef(false);
  useEffect(() => { if (!moved.current) { moved.current = true; return; } if (step === 1) contribution.current?.focus(); else title.current?.focus(); }, [step]);
  const [recipe, setRecipe] = useState(entry?.recipeSlug || entry?.slug || componentSeeds[0].slug);
  const base = componentSeeds.find(item => item.slug === recipe);
  const [upload,setUpload]=useState(false);
  function next(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (step === 0) { setStep(1); return; }
    onSave(event);
  }
  return <form className="form" onSubmit={next} aria-busy={busy}>
    <ol className="draft-steps" aria-label="Draft steps"><li aria-current={step === 0 ? 'step' : undefined}>1 · Component details</li><li aria-current={step === 1 ? 'step' : undefined}>2 · Contribution & rights</li></ol>
    <fieldset hidden={step !== 0} disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
      <label htmlFor="recipeSlug">Base component</label><select id="recipeSlug" name="recipeSlug" value={recipe} onChange={e => setRecipe(e.target.value)} disabled={!!(entry?.published || entry?.uploaded)}>{componentSeeds.map(c => <option key={c.slug} value={c.slug}>{c.title}</option>)}</select>{(entry?.published || entry?.uploaded)&&<input type="hidden" name="recipeSlug" value={recipe}/>}
      {base && <p className="draft-summary"><strong>{base.title}</strong><span>{base.summary}</span><small>Free source · MIT · {base.framework}</small></p>}
      <label htmlFor="title">Component title</label><input id="title" ref={title} name="title" defaultValue={entry?.title} required={step === 0} minLength={3} maxLength={100} />
      <label htmlFor="summary">Summary</label><input id="summary" name="summary" defaultValue={entry?.summary} required={step === 0} minLength={10} maxLength={240} />
    </fieldset>
    <fieldset hidden={step !== 1} disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
      <label htmlFor="contribution">Your contribution & intended use</label><textarea id="contribution" ref={contribution} name="contribution" defaultValue={entry?.contribution} required={step === 1} minLength={20} maxLength={2000} />
      <p className="muted">Describe your actual contribution. A recipe selection alone does not imply authorship of the original source.</p>
      {entry?.uploaded&&<p className="kit-source-hash">Saved source v{entry.version} · SHA-256 <code>{entry.sha256}</code>. Upload a new version to replace it.</p>}
      <label className="kit-consent"><input type="checkbox" checked={upload} onChange={e=>setUpload(e.target.checked)}/>Upload my self-contained frontend source</label>
      {upload&&<><p className="muted">ZIP up to 5 MiB, containing only index.html, README.md and LICENSE (up to 100 kB each). Inline CSS and JavaScript. Include the MIT notice, attribution and local setup. No secrets or external dependencies. Source is inspected without execution.</p><label htmlFor="component-file">Source ZIP</label><input id="component-file" name="file" type="file" accept=".zip,application/zip" required={step===1}/><label htmlFor="component-version">Release version</label><input id="component-version" name="version" defaultValue={entry?.published?'':entry?.version||'1.0.0'} pattern={'[A-Za-z0-9][A-Za-z0-9._+\\-]{0,39}'} required={step===1} maxLength={40}/><label htmlFor="component-scenario">Synthetic preview interaction</label><textarea id="component-scenario" name="scenario" defaultValue={entry?.uploaded?entry.scenario:undefined} required={step===1} maxLength={500}/></>}
      <label className="kit-consent"><input type="checkbox" name="rightsConsent" required={step === 1} />I preserve the original MIT attribution and accurately describe my contribution.</label>
    </fieldset>
    <FormErrors error={error} />
    <div className="draft-actions">{step === 1 && <button type="button" className="button" disabled={busy} onClick={() => setStep(0)}>Back</button>}<button className="button primary" disabled={busy}>{busy ? 'Saving draft…' : step === 0 ? 'Continue to contribution' : 'Save component draft'}</button></div>
  </form>;
}
