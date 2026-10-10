'use client';

import {useState} from 'react';
import {dataAiEvidenceIssues, educationDataAiChecks, educationDataAiFields, isDataAiCategory, type EducationDataAiEvidence} from '@/lib/education';

export function EducationDataAiEditor({category, value, onChange}: {category: string; value?: EducationDataAiEvidence; onChange: (value: EducationDataAiEvidence) => void}) {
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  if (!isDataAiCategory(category)) return null;
  const issues = dataAiEvidenceIssues(category, value);
  return <section aria-labelledby="education-data-ai-heading"><h4 id="education-data-ai-heading">Dataset, model and evaluation evidence</h4><p>Describe the code, dataset and model rights separately. Save partial work privately; complete provenance and synthetic rights confirmations are required before a new submission.</p>
    {educationDataAiFields.filter(([key]) => category === 'AI_ML' || !key.startsWith('model')).map(([key, label]) => {
      const id = 'education-data-ai-' + key, error = touched[key] ? issues[key] : undefined;
      const common = {id, value: value?.[key] ?? '', maxLength: key.endsWith('Sha256') ? 64 : key === 'modelFormat' ? 30 : 4000, 'aria-invalid': error ? 'true' as const : undefined, 'aria-describedby': id + '-hint' + (error ? ' ' + id + '-error' : ''), onBlur: () => setTouched(old => ({...old, [key]: true})), onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => onChange({...value, [key]: event.target.value})};
      const hint = key.endsWith('Sha256') ? 'Exact artifact checksum: 64 lowercase hexadecimal characters.' : key === 'modelFormat' ? 'Frozen JSON only. Pickle, joblib, executable and remote loaders are not accepted.' : key === 'evaluationSplit' ? 'Describe the separate held-out synthetic fixture and its size.' : key === 'evaluationProtocol' ? 'Describe the observed evaluation method and reproduction steps.' : key === 'outputSchema' ? 'Name every returned table, metric, field, type and unit; exclude private details.' : key.endsWith('License') ? 'State this artifact’s license and required notices.' : 'Describe the original source, scope and limitations without private academic information.';
      return <div key={key} style={{marginBlock: 16}}><label htmlFor={id}>{label}{category === 'DATA_ANALYTICS' && key.startsWith('evaluation') ? ' (optional for analytics)' : ''}</label>{key === 'modelFormat' ? <select {...common}><option value="">Choose a safe model format</option><option value="JSON">Frozen JSON model</option></select> : key.endsWith('Sha256') ? <input {...common} type="text" autoComplete="off" autoCapitalize="none" spellCheck={false}/> : <textarea {...common} rows={3}/>}<p id={id + '-hint'} style={{fontSize: 14, marginBlock: 8}}>{hint}</p>{error && <p className="error" id={id + '-error'}>{error}</p>}</div>;
    })}
    {educationDataAiChecks.map(([key, label]) => <label key={key} className="kit-consent" style={{minHeight: 44, alignItems: 'center', marginBlock: 12}}><input type="checkbox" checked={value?.[key] === true} onChange={event => onChange({...value, [key]: event.target.checked})}/>{label}</label>)}<p>Synthetic evaluation metrics describe the included fixture. They do not establish real-world accuracy, hosted certification or academic marks.</p></section>;
}

export function EducationDataAiDetails({evidence, category}: {evidence?: EducationDataAiEvidence; category?: string}) {
  if (!evidence && !isDataAiCategory(category || '')) return null;
  return <section aria-label="Dataset and model disclosures"><h3>Dataset, model and evaluation evidence</h3>{!evidence ? <p>This historical release has no structured data/AI evidence. Its original approved terms remain available; new submissions require these disclosures.</p> : <><dl>{educationDataAiFields.filter(([key]) => category === 'AI_ML' || !key.startsWith('model') || Boolean(evidence[key])).map(([key, label]) => <div key={key} style={{marginBottom: 16}}><dt style={{fontWeight: 600}}>{label}</dt><dd style={{margin: 0, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}>{evidence[key] || 'Not supplied.'}</dd></div>)}</dl><ul>{educationDataAiChecks.map(([key, label]) => <li key={key}>{label.replace(/^I have verified /, 'Verified ')} <strong>{evidence[key] === true ? 'Confirmed' : evidence[key] === false ? 'Not confirmed' : 'Not supplied'}</strong></li>)}</ul></>}<p>Code, data and model rights remain separate. Tiny synthetic fixture metrics do not establish real-world accuracy. Hosted admission is checked separately.</p></section>;
}
