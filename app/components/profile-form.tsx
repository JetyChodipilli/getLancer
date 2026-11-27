'use client';
import {useEffect, useId, useRef, useState, type FormEvent} from 'react';
import {accountApi, api, ApiError} from '@/lib/api';
import {PROFILE_AVATAR_CHANGED, profileAvatarUrl} from '@/lib/profile-avatar';
import FormErrors from './form-errors';
import ProfileAvatar from './profile-avatar';
import {Picker} from './ui';

function uncertain(problem:unknown){return !(problem instanceof ApiError)||problem.status===0||problem.status>=500;}
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
type ProfileFormProps={me:{id:string;avatarUrl?:string|null;profile:Record<string,string|null|undefined>};reload:()=>Promise<boolean>;setError:(message:string)=>void;request?:typeof api};
export default function ProfileForm(props:ProfileFormProps) {
  return <ProfileForIdentity key={props.me.id} {...props}/>;
}
function ProfileForIdentity({me,reload,setError,request=api}:ProfileFormProps) {
  const p=me.profile;
  const [availability,setAvailability]=useState(p.availability_status||'AVAILABLE_NOW');
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState('');
  const [failure,setFailure]=useState<ApiError|null>(null);
  const [refreshFailed,setRefreshFailed]=useState(false);
  const [dirty,setDirty]=useState(false);
  const savedAvatar=profileAvatarUrl(me.avatarUrl);
  const [pictureChange,setPictureChange]=useState<{previous?:string;url?:string}>();
  const avatar=pictureChange&&pictureChange.previous===savedAvatar?pictureChange.url:savedAvatar;
  const [pictureError,setPictureError]=useState('');
  const active=useRef(true),inFlight=useRef(false);
  useEffect(()=>{active.current=true;return()=>{active.current=false}},[]);
  async function refresh(success:string,pictureChanged=false){
    let refreshed=false;
    try{refreshed=await reload()}catch{/* The mutation already succeeded; offer a refresh-only retry. */}
    if(active.current){
      if((refreshed||pictureChanged)&&typeof window!=='undefined')window.dispatchEvent(new Event(PROFILE_AVATAR_CHANGED));
      setRefreshFailed(!refreshed);setMessage(refreshed?success:success+' Current profile details could not be refreshed. Retry the refresh before another profile action.');
    }
  }
  async function changePicture(file:File|null){
    if(inFlight.current||refreshFailed)return false;
    inFlight.current=true;
    setBusy(true);setPictureError('');setError('');setMessage('');
    try{
      const form=file?new FormData():undefined;
      if(form&&file)form.set('file',file);
      const init={method:file?'PUT':'DELETE',body:form};
      const result=request===api?await accountApi(me.id,'/me/avatar',init):await request('/me/avatar',init);
      if(!active.current)return false;
      const next=profileAvatarUrl(result?.avatarUrl);
      if(file&&!next)throw new ApiError('The uploaded picture could not be confirmed.','INVALID_RESPONSE',502);
      setPictureChange({previous:savedAvatar,url:next});
      await refresh(file?'Profile picture saved.':'Profile picture removed.',true);
      return true;
    }catch(problem){
      if(active.current){
        setPictureError(problem instanceof Error?problem.message:'The picture could not be updated.');
        if(uncertain(problem)){
          setRefreshFailed(true);
          setMessage('This picture change could not be confirmed. Refresh current profile details before trying again.');
        }
      }
      return false;
    }finally{inFlight.current=false;if(active.current)setBusy(false)}
  }
  async function save(e:FormEvent<HTMLFormElement>) {
    e.preventDefault(); if(inFlight.current||refreshFailed)return;
    inFlight.current=true;
    setBusy(true);setFailure(null);setError('');setMessage('');
    try {
      await request('/developer/profile',{method:'PUT',body:JSON.stringify({...Object.fromEntries(new FormData(e.currentTarget)),availabilityStatus:availability})});
      if(!active.current)return;
      setDirty(false);await refresh('Profile saved.');
    }catch(e){if(active.current){setError((e as Error).message);if(e instanceof ApiError)setFailure(e);if(uncertain(e)){setRefreshFailed(true);setMessage('This profile change could not be confirmed. Refresh current profile details before another profile action.');}}}finally{inFlight.current=false;if(active.current)setBusy(false)}
  }
  return <div className="panel form profile-editor"><h2>Your builder profile</h2>
    <ProfilePictureEditor name={p.display_name||'Member'} avatar={avatar} disabled={busy||refreshFailed}
      error={pictureError} clearError={()=>setPictureError('')} onChange={changePicture}/>
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
      <p className="muted">{p.approval_status==='APPROVED' ? 'Changing public profile details hides your showcases from Explore and search until your profile is approved again. Availability changes take effect immediately.' : 'Your completed profile fields are public after approval. Availability changes take effect immediately.'}</p>
      <fieldset disabled={busy} data-field="availabilityStatus"><legend>Availability for client work</legend><Picker id="availabilityStatus" invalid={!!failure?.fieldErrors.availabilityStatus} describedBy={failure?.fieldErrors.availabilityStatus?'availabilityStatus-error':undefined} label="Availability" value={availability} onChange={value=>{setAvailability(value);setDirty(true);setMessage('')}} options={['AVAILABLE_NOW','ONE_SLOT_LEFT','LIMITED','BOOKED_UNTIL','NOT_ACCEPTING']}/>{failure?.fieldErrors.availabilityStatus&&<p id="availabilityStatus-error" className="field-error">{failure.fieldErrors.availabilityStatus}</p>}
      {availability==='BOOKED_UNTIL'&&<><label htmlFor="bookedUntil">Booked until</label><input id="bookedUntil" name="bookedUntil" type="date" defaultValue={p.booked_until||''} required aria-invalid={!!failure?.fieldErrors.bookedUntil} aria-describedby={failure?.fieldErrors.bookedUntil?'bookedUntil-error':undefined}/>{failure?.fieldErrors.bookedUntil&&<p id="bookedUntil-error" className="field-error">{failure.fieldErrors.bookedUntil}</p>}</>}
      </fieldset>
      <button className="button primary" disabled={busy||refreshFailed}>{busy?'Saving…':'Save profile'}</button>
    </form>
    {message&&<p className="success" role="status">{message}</p>}
    {refreshFailed&&<button className="button" disabled={busy} onClick={async()=>{if(inFlight.current)return;inFlight.current=true;setBusy(true);setError('');setPictureError('');try{await refresh('Profile details refreshed.')}finally{inFlight.current=false;if(active.current)setBusy(false)}}}>Retry profile refresh</button>}
    {['DRAFT','CHANGES_REQUESTED'].includes(p.approval_status||'')&&<><button className="button" disabled={busy||refreshFailed||dirty} style={{marginTop:15}} onClick={async()=>{
      if(inFlight.current||refreshFailed||dirty)return;inFlight.current=true;setBusy(true);setError('');setFailure(null);setMessage('');
      try{await request('/developer/profile/submit',{method:'POST'});if(active.current)await refresh('Profile sent for approval.')}catch(e){if(active.current){setError((e as Error).message);if(e instanceof ApiError)setFailure(e);if(uncertain(e)){setRefreshFailed(true);setMessage('This profile change could not be confirmed. Refresh current profile details before another profile action.');}}}finally{inFlight.current=false;if(active.current)setBusy(false)}
    }}>Submit profile for approval</button>{dirty&&<p className="muted">Save your changes before submitting the profile for approval.</p>}</>}
  </div>;
}

function ProfilePictureEditor({name,avatar,disabled,error,clearError,onChange}:{
  name:string;avatar?:string;disabled:boolean;error:string;clearError:()=>void;onChange:(file:File|null)=>Promise<boolean>;
}){
  const id=useId(), input=useRef<HTMLInputElement>(null);
  const [selection,setSelection]=useState<{file:File;url:string}|null>(null);
  const [validation,setValidation]=useState('');
  useEffect(()=>{
    return()=>{if(selection)URL.revokeObjectURL(selection.url);};
  },[selection]);
  function reset(){
    setSelection(null);
    if(input.current)input.current.value='';
  }
  async function save(event:FormEvent<HTMLFormElement>){
    event.preventDefault();
    if(selection&&!disabled&&await onChange(selection.file))reset();
  }
  return <form className="profile-picture-editor" onSubmit={save} aria-labelledby={id+'-heading'}>
    <h3 id={id+'-heading'}>Profile picture</h3>
    <div className="profile-picture-field">
      <ProfileAvatar className="profile-avatar-large" name={name} src={selection?.url??avatar}/>
      <div className="profile-picture-input">
        <label htmlFor={id}>{avatar?'Choose a replacement picture':'Choose a profile picture'}</label>
        <input ref={input} id={id} type="file" accept="image/png,image/jpeg,.png,.jpg,.jpeg" disabled={disabled}
          aria-describedby={id+'-hint'}
          onChange={event=>{
            const next=event.currentTarget.files?.[0]??null;
            setValidation('');clearError();
            if(next&&(next.size===0||next.size>5*1024*1024||next.type&&!['image/png','image/jpeg'].includes(next.type))){
              reset();setValidation('Choose a PNG or JPEG file up to 5 MB.');return;
            }
            setSelection(next?{file:next,url:URL.createObjectURL(next)}:null);
          }}/>
        <p id={id+'-hint'} className="muted">PNG or JPEG, up to 5 MB. Your picture is public when your builder profile is approved.</p>
      </div>
    </div>
    {(selection||avatar)&&<div className="profile-picture-actions">
      {selection&&<><button className="button primary" disabled={disabled}>Save picture</button>
        <button className="button" type="button" disabled={disabled} onClick={reset}>Cancel selection</button></>}
      {avatar&&<button className="button" type="button" disabled={disabled}
        onClick={async()=>{if(await onChange(null))reset();}}>Remove picture</button>}
    </div>}
    {(validation||error)&&<p className="error" role="alert">{validation||error}</p>}
  </form>;
}
