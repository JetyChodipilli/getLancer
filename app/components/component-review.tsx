'use client';
import { componentReviewCriteria } from '@/lib/components';
const criteria = {
  originality: ['Originality', 'Describe attribution, contribution and any reuse checked.'],
  quality: ['Quality', 'Record usability, accessibility and interaction checks.'],
  license: ['Licence', 'Record licence, source permissions and attribution checks.'],
  execution: ['Execution', 'Record the tested setup, runtime and observed result; explain any not-applicable result.'],
};
export default function ComponentAssessmentFields({ id, busy }: { id: string; busy: boolean }) {
  return <fieldset disabled={busy}><legend>Private component assessments</legend><p>Record evidence for this exact submitted source and context. These notes are visible only to administrators. Publication requires passing checks; executable examples must be tested.</p>{componentReviewCriteria.map(key => <div key={key}>
    <label htmlFor={id + '-' + key + '-result'}>{criteria[key][0]} result</label>
    <select id={id + '-' + key + '-result'} name={key + 'Result'} required defaultValue=""><option value="" disabled>Choose a result</option><option value="PASS">Pass</option><option value="FAIL">Fail</option><option value="NOT_APPLICABLE">Not applicable</option></select>
    <label htmlFor={id + '-' + key + '-note'}>{criteria[key][0]} evidence</label>
    <textarea id={id + '-' + key + '-note'} name={key + 'Note'} required minLength={20} maxLength={1000} aria-describedby={id + '-' + key + '-help'} />
    <p id={id + '-' + key + '-help'}>{criteria[key][1]}</p>
  </div>)}</fieldset>;
}
