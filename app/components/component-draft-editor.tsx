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
  function next(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (step === 0) { setStep(1); return; }
    onSave(event);
  }
  return <form className="form" onSubmit={next} aria-busy={busy}>
    <ol className="draft-steps" aria-label="Draft steps"><li aria-current={step === 0 ? 'step' : undefined}>1 · Component details</li><li aria-current={step === 1 ? 'step' : undefined}>2 · Contribution & rights</li></ol>
    <fieldset hidden={step !== 0} disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
      <label htmlFor="recipeSlug">Base component</label><select id="recipeSlug" name="recipeSlug" value={recipe} onChange={e => setRecipe(e.target.value)}>{componentSeeds.map(c => <option key={c.slug} value={c.slug}>{c.title}</option>)}</select>
      {base && <p className="draft-summary"><strong>{base.title}</strong><span>{base.summary}</span><small>Free source · MIT · {base.framework}</small></p>}
      <label htmlFor="title">Component title</label><input id="title" ref={title} name="title" defaultValue={entry?.title} required={step === 0} minLength={3} maxLength={100} />
      <label htmlFor="summary">Summary</label><input id="summary" name="summary" defaultValue={entry?.summary} required={step === 0} minLength={10} maxLength={240} />
    </fieldset>
    <fieldset hidden={step !== 1} disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
      <label htmlFor="contribution">Your contribution & intended use</label><textarea id="contribution" ref={contribution} name="contribution" defaultValue={entry?.contribution} required={step === 1} minLength={20} maxLength={2000} />
      <p className="muted">Describe your actual contribution. A recipe selection alone does not imply authorship of the original source.</p>
      <label className="kit-consent"><input type="checkbox" name="rightsConsent" required={step === 1} />I preserve the original MIT attribution and accurately describe my contribution.</label>
    </fieldset>
    <FormErrors error={error} />
    <div className="draft-actions">{step === 1 && <button type="button" className="button" disabled={busy} onClick={() => setStep(0)}>Back</button>}<button className="button primary" disabled={busy}>{busy ? 'Saving draft…' : step === 0 ? 'Continue to contribution' : 'Save component draft'}</button></div>
  </form>;
}
