'use client';
import {useState, type FormEvent} from 'react';
import {api, ApiError} from '@/lib/api';
import FormErrors from './form-errors';
import {Picker} from './ui';

const fields = [
  ['displayName','display_name','Display name',2,100],
  ['headline','headline','Professional headline',5,160],
  ['bio','bio','About your work',20,3000],
  ['technology','technology','Primary technologies',1,300],
  ['category','category','Business categories',1,200],
  ['githubUrl','github_url','GitHub URL (optional)',0,500],
  ['linkedinUrl','linkedin_url','LinkedIn URL (optional)',0,500],
  ['websiteUrl','website_url','Personal website (optional)',0,500],
  ['country','country','Country code (optional)',0,2],
  ['timeZone','time_zone','Time zone (optional)',0,100],
  ['languages','languages','Languages (optional)',0,200],
] as const;
const fieldGroups = [
  {heading: 'Your work', description: 'The essentials clients need to understand your expertise.', fields: fields.slice(0, 5)},
  {heading: 'On the web', description: 'Optional links that show more of what you do.', fields: fields.slice(5, 8)},
  {heading: 'Location & communication', description: 'Help clients plan a conversation with you.', fields: fields.slice(8)},
];
export default function ProfileForm({me,reload,setError}: {me:any;reload:()=>void;setError:(message:string)=>void}) {
  const p=me.profile;
  const [availability,setAvailability]=useState(p.availability_status);
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState('');
  const [failure,setFailure]=useState<ApiError|null>(null);
  async function save(e:FormEvent<HTMLFormElement>) {
    e.preventDefault(); if(busy)return;
    setBusy(true);setFailure(null);setError('');setMessage('');
    try {
      await api('/developer/profile',{method:'PUT',body:JSON.stringify({...Object.fromEntries(new FormData(e.currentTarget)),availabilityStatus:availability})});
      setMessage('Profile saved.');reload();
    }catch(e){setError((e as Error).message);if(e instanceof ApiError)setFailure(e)}finally{setBusy(false)}
  }
  return <div className="panel form profile-editor"><h2>Your builder profile</h2>
    <form className="form" onSubmit={save} aria-busy={busy}>
      <FormErrors error={failure}/>
      {fieldGroups.map(({heading,description,fields:group})=><fieldset className="profile-fields" key={heading} disabled={busy}><legend>{heading}</legend><p className="profile-group-help">{description}</p><div className="profile-field-grid">{group.map(([key,source,title,min,max])=><div key={key} data-field={key}>
        <label htmlFor={key}>{title}</label>
        {key==='bio'?<textarea id={key} name={key} defaultValue={p[source]||''} required minLength={min} maxLength={max} aria-invalid={!!failure?.fieldErrors[key]}/>:
          <input id={key} name={key} defaultValue={p[source]||''} required={min>0} minLength={min} maxLength={max} type={key.endsWith('Url')?'url':'text'} aria-invalid={!!failure?.fieldErrors[key]} aria-describedby={['country','timeZone','languages'].includes(key)?key+'-hint':undefined}/>}
        {key==='country'&&<p id="country-hint" className="muted">Two-letter country code, such as IN.</p>}
        {key==='timeZone'&&<p id="timeZone-hint" className="muted">For example, Asia/Kolkata. Used to interpret your booked-until date.</p>}
        {key==='languages'&&<p id="languages-hint" className="muted">For example, English, Telugu.</p>}
        {failure?.fieldErrors[key]&&<p className="error">{failure.fieldErrors[key]}</p>}
      </div>)}</div></fieldset>)}
      {p.moderation_reason&&<p className="samplebar">{p.moderation_reason}</p>}
      <p className="muted">Your completed profile fields are public after approval. Changing public details requires a fresh review. Availability changes take effect immediately.</p>
      <label>Availability for client work</label><Picker label="Availability" value={availability} onChange={setAvailability} options={['AVAILABLE_NOW','ONE_SLOT_LEFT','LIMITED','BOOKED_UNTIL','NOT_ACCEPTING']}/>
      {availability==='BOOKED_UNTIL'&&<><label htmlFor="bookedUntil">Booked until</label><input id="bookedUntil" name="bookedUntil" type="date" defaultValue={p.booked_until||''} required aria-invalid={!!failure?.fieldErrors.bookedUntil}/>{failure?.fieldErrors.bookedUntil&&<p className="error">{failure.fieldErrors.bookedUntil}</p>}</>}
      <button className="button primary" disabled={busy}>{busy?'Saving…':'Save profile'}</button>
    </form>
    {message&&<p className="success" role="status">{message}</p>}
    {['DRAFT','CHANGES_REQUESTED'].includes(p.approval_status)&&<button className="button" disabled={busy} style={{marginTop:15}} onClick={async()=>{
      if(busy)return;setBusy(true);setError('');
      try{await api('/developer/profile/submit',{method:'POST'});setMessage('Profile sent for approval.');reload()}catch(e){setError((e as Error).message)}finally{setBusy(false)}
    }}>Submit profile for approval</button>}
  </div>;
}
