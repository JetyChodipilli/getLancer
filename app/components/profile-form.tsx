'use client';
import {useEffect, useRef, useState, type FormEvent} from 'react';
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
type ProfileFormProps={me:{id:string;profile:Record<string,string|null|undefined>};reload:()=>Promise<boolean>;setError:(message:string)=>void};
export default function ProfileForm(props:ProfileFormProps) {
  return <ProfileForIdentity key={props.me.id} {...props}/>;
}
function ProfileForIdentity({me,reload,setError}:ProfileFormProps) {
  const p=me.profile;
  const [availability,setAvailability]=useState(p.availability_status||'AVAILABLE_NOW');
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState('');
  const [failure,setFailure]=useState<ApiError|null>(null);
  const [refreshFailed,setRefreshFailed]=useState(false);
  const [dirty,setDirty]=useState(false);
  const active=useRef(true),inFlight=useRef(false);
  useEffect(()=>{active.current=true;return()=>{active.current=false}},[]);
  async function refresh(success:string){
    let refreshed=false;
    try{refreshed=await reload()}catch{/* The mutation already succeeded; offer a refresh-only retry. */}
    if(active.current){setRefreshFailed(!refreshed);setMessage(refreshed?success:success+' Current profile details could not be refreshed. Retry the refresh before another profile action.');}
  }
  async function save(e:FormEvent<HTMLFormElement>) {
    e.preventDefault(); if(inFlight.current||refreshFailed)return;
    inFlight.current=true;
    setBusy(true);setFailure(null);setError('');setMessage('');
    try {
      await api('/developer/profile',{method:'PUT',body:JSON.stringify({...Object.fromEntries(new FormData(e.currentTarget)),availabilityStatus:availability})});
      if(!active.current)return;
      setDirty(false);await refresh('Profile saved.');
    }catch(e){if(active.current){setError((e as Error).message);if(e instanceof ApiError)setFailure(e)}}finally{inFlight.current=false;if(active.current)setBusy(false)}
  }
  return <div className="panel form profile-editor"><h2>Your builder profile</h2>
    <form className="form" onSubmit={save} onChange={()=>{setDirty(true);setMessage('')}} aria-busy={busy}>
      <FormErrors error={failure}/>
      {fieldGroups.map(({heading,description,fields:group})=><fieldset className="profile-fields" key={heading} disabled={busy}><legend>{heading}</legend><p className="profile-group-help">{description}</p><div className="profile-field-grid">{group.map(([key,source,title,min,max])=><div key={key} data-field={key}>
        <label htmlFor={key}>{title}</label>
        {key==='bio'?<textarea id={key} name={key} defaultValue={p[source]||''} required minLength={min} maxLength={max} aria-invalid={!!failure?.fieldErrors[key]} aria-describedby={failure?.fieldErrors[key]?key+'-error':undefined}/>:
          <input id={key} name={key} defaultValue={p[source]||''} required={min>0} minLength={min} maxLength={max} type={key.endsWith('Url')?'url':'text'} aria-invalid={!!failure?.fieldErrors[key]} aria-describedby={[['country','timeZone','languages'].includes(key)?key+'-hint':'',failure?.fieldErrors[key]?key+'-error':''].filter(Boolean).join(' ')||undefined}/>}
        {key==='country'&&<p id="country-hint" className="muted">Two-letter country code, such as IN.</p>}
        {key==='timeZone'&&<p id="timeZone-hint" className="muted">For example, Asia/Kolkata. Used to interpret your booked-until date.</p>}
        {key==='languages'&&<p id="languages-hint" className="muted">For example, English, Telugu.</p>}
        {failure?.fieldErrors[key]&&<p id={key+'-error'} className="field-error">{failure.fieldErrors[key]}</p>}
      </div>)}</div></fieldset>)}
      {p.moderation_reason&&<p className="samplebar">{p.moderation_reason}</p>}
      <p className="muted">Your completed profile fields are public after approval. Changing public details requires a fresh review. Availability changes take effect immediately.</p>
      <fieldset disabled={busy} data-field="availabilityStatus"><legend>Availability for client work</legend><Picker id="availabilityStatus" invalid={!!failure?.fieldErrors.availabilityStatus} describedBy={failure?.fieldErrors.availabilityStatus?'availabilityStatus-error':undefined} label="Availability" value={availability} onChange={value=>{setAvailability(value);setDirty(true);setMessage('')}} options={['AVAILABLE_NOW','ONE_SLOT_LEFT','LIMITED','BOOKED_UNTIL','NOT_ACCEPTING']}/>{failure?.fieldErrors.availabilityStatus&&<p id="availabilityStatus-error" className="field-error">{failure.fieldErrors.availabilityStatus}</p>}
      {availability==='BOOKED_UNTIL'&&<><label htmlFor="bookedUntil">Booked until</label><input id="bookedUntil" name="bookedUntil" type="date" defaultValue={p.booked_until||''} required aria-invalid={!!failure?.fieldErrors.bookedUntil} aria-describedby={failure?.fieldErrors.bookedUntil?'bookedUntil-error':undefined}/>{failure?.fieldErrors.bookedUntil&&<p id="bookedUntil-error" className="field-error">{failure.fieldErrors.bookedUntil}</p>}</>}
      </fieldset>
      <button className="button primary" disabled={busy||refreshFailed}>{busy?'Saving…':'Save profile'}</button>
    </form>
    {message&&<p className="success" role="status">{message}</p>}
    {refreshFailed&&<button className="button" disabled={busy} onClick={async()=>{if(inFlight.current)return;inFlight.current=true;setBusy(true);setError('');try{await refresh('Profile details refreshed.')}finally{inFlight.current=false;if(active.current)setBusy(false)}}}>Retry profile refresh</button>}
    {['DRAFT','CHANGES_REQUESTED'].includes(p.approval_status||'')&&<><button className="button" disabled={busy||refreshFailed||dirty} style={{marginTop:15}} onClick={async()=>{
      if(inFlight.current||refreshFailed||dirty)return;inFlight.current=true;setBusy(true);setError('');setFailure(null);setMessage('');
      try{await api('/developer/profile/submit',{method:'POST'});if(active.current)await refresh('Profile sent for approval.')}catch(e){if(active.current){setError((e as Error).message);if(e instanceof ApiError)setFailure(e)}}finally{inFlight.current=false;if(active.current)setBusy(false)}
    }}>Submit profile for approval</button>{dirty&&<p className="muted">Save your changes before submitting the profile for approval.</p>}</>}
  </div>;
}
