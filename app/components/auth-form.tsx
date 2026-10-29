'use client';

import {FormEvent,useEffect,useRef,useState} from 'react';
import Link from 'next/link';
import {ArrowLeft,ArrowRight,ArrowUpRight,CheckCircle2,Eye,EyeOff,LoaderCircle,LockKeyhole,Mail,UserRound} from 'lucide-react';
import {InputOTP,InputOTPGroup,InputOTPSlot} from '@/components/ui/input-otp';
import {api,ApiError} from '@/lib/api';

type Mode='login'|'signup'|'reset'|'mfa';
const googleErrors:Record<string,string>={
 cancelled:'Google sign-in was cancelled. You can try again or use your email.',
 expired:'That sign-in attempt expired. Please start again.',
 failed:'Google sign-in could not be completed. Please try again.',
 unavailable:'Google sign-in is unavailable. Please use email and password.',
 signup_required:'Create your getLancer account first. Continue with Google below to join.',
 email_account:'This email already has an account. Use the sign-in method you originally chose.',
 admin_password:'Administrator sign-in requires your password and authenticator code.',
 account_unavailable:'This account cannot sign in. Use your existing account or contact support.',
};
const fieldIds:Record<string,string>={name:'auth-name',displayName:'auth-name',email:'auth-email',password:'auth-password',totp:'auth-totp'};

function validationMessage(field:string,value:string,mode:Mode){
 if(field==='email'&&!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim()))return 'Enter a valid email address.';
 if(field==='name'&&mode==='signup'&&(value.trim().length<2||value.trim().length>100))return 'Enter your name (2–100 characters).';
 if(field==='password'&&(!value||(mode==='signup'&&(value.length<12||new TextEncoder().encode(value).length>72))))return mode==='signup'?'Use at least 12 characters and at most 72 bytes.':'Enter your password.';
 return '';
}

export function AuthForm({signup=false,preview=false}:{signup?:boolean;preview?:boolean}){
 const [mode,setMode]=useState<Mode>(signup?'signup':'login');
 const [email,setEmail]=useState(''),[password,setPassword]=useState(''),[name,setName]=useState('');
 const [showPassword,setShowPassword]=useState(false),[remember,setRemember]=useState(false),[totp,setTotp]=useState('');
 const [errors,setErrors]=useState<Record<string,string>>({}),[error,setError]=useState('');
 const [success,setSuccess]=useState(''),[busy,setBusy]=useState(false),[googleBusy,setGoogleBusy]=useState(false);
 const [githubReady,setGithubReady]=useState(false),[providerBusy,setProviderBusy]=useState('');
 const [googleReady,setGoogleReady]=useState<boolean|null>(null),[servicesReady,setServicesReady]=useState<boolean|null>(null);
 const [serviceMessage,setServiceMessage]=useState(''),[checkAttempt,setCheckAttempt]=useState(0);
 const notice=useRef<HTMLDivElement>(null),heading=useRef<HTMLHeadingElement>(null),inFlight=useRef(false);
 const isSignup=mode==='signup',isMfa=mode==='mfa',isReset=mode==='reset';

 useEffect(()=>{
  const controller=new AbortController();
  api('/auth/providers',{signal:controller.signal}).then(r=>{
   if(controller.signal.aborted)return;
   setGoogleReady(r.google===true);setGithubReady(r.github===true);setServicesReady(true);
  }).catch(e=>{
   if(controller.signal.aborted)return;
   setGoogleReady(false);setGithubReady(false);setServicesReady(false);
   setServiceMessage(e instanceof ApiError&&e.code==='BACKEND_NOT_CONFIGURED'?'Sign-in is unavailable in this preview.':'Account services are temporarily unavailable. Check your connection and try again.');
  });
  return()=>controller.abort();
 },[checkAttempt]);
 useEffect(()=>{
  const query=new URLSearchParams(window.location.search),code=query.get('auth_error');
  const frame=requestAnimationFrame(()=>{if(code)setError(code==='email_unverified'?'Verify your primary GitHub email before continuing.':(googleErrors[code]||'Sign-in could not be completed. Please try again.').replaceAll('Google',query.get('provider')==='github'?'GitHub':'Google'))});
  return()=>cancelAnimationFrame(frame);
 },[]);
 useEffect(()=>{if(error)notice.current?.focus()},[error]);
 useEffect(()=>{if(success)heading.current?.focus()},[success]);

 function switchMode(next:Mode){
  setMode(next);setError('');setErrors({});setSuccess('');setPassword('');setTotp('');setShowPassword(false);
  requestAnimationFrame(()=>heading.current?.focus());
 }
 function validate(field:string,value:string){
  const message=validationMessage(field,value,mode);setErrors(old=>({...old,[field]:message}));return !message;
 }
 function focusField(field:string){document.getElementById(fieldIds[field]||field)?.focus()}
 async function submit(event:FormEvent<HTMLFormElement>){
  event.preventDefault();
  if(inFlight.current||servicesReady!==true)return;
  setError('');
  const nextErrors:Record<string,string>={};
  if(isMfa){if(!/^\d{6}$/.test(totp))nextErrors.totp='Enter the 6-digit code from your authenticator.';}
  else{
   for(const [field,value] of [['email',email],...(isSignup?[['name',name]]:[]),...(!isReset?[['password',password]]:[])]){
    const message=validationMessage(field,value,mode);if(message)nextErrors[field]=message;
   }
  }
  setErrors(nextErrors);
  if(Object.keys(nextErrors).length){setError('Review the highlighted details, then try again.');requestAnimationFrame(()=>notice.current?.focus());return;}
  inFlight.current=true;setBusy(true);
  try{
   const path=isMfa?'/auth/login/mfa':isReset?'/auth/password-reset/request':'/auth/'+mode;
   const result=await api(path,{method:'POST',body:JSON.stringify(isMfa?{totp}:{email:email.trim(),...(mode==='login'?{rememberMe:remember}:{}),...(!isReset?{password}:{}),...(isSignup?{displayName:name.trim(),acceptedTerms:true}:{})})});
   if(!result||typeof result!=='object'||Array.isArray(result))throw new ApiError('The account response could not be confirmed. Please check your account before trying again.','INVALID_RESPONSE',502);
   if(mode==='login'&&result.mfaRequired===true){switchMode('mfa');return;}
   if((mode==='login'||isMfa)&&(typeof result.id!=='string'||!result.id))throw new ApiError('Sign-in could not be confirmed. Please try opening your workspace.','INVALID_RESPONSE',502);
   if((isSignup||isReset)&&(typeof result.message!=='string'||!result.message))throw new ApiError('The email response could not be confirmed. Check your inbox before trying again.','INVALID_RESPONSE',502);
   if(mode==='login'||isMfa){setPassword('');window.location.assign('/workspace');return;}
   setPassword('');
   setSuccess(isReset?'If an account exists for this email, you’ll receive a link to reset your password.':'Your account is created. Open the confirmation email to verify your address and get started.');
  }catch(e){
   const failure=e instanceof Error?e:new Error('Sign-in could not be completed. Please try again.');
   if(failure instanceof ApiError&&failure.code==='MFA_EXPIRED'){switchMode('login');setError('Your verification step expired. Please sign in again.');}
   else{
    if(failure instanceof ApiError){
     const fields=Object.fromEntries(Object.entries(failure.fieldErrors).filter(([field])=>fieldIds[field]).map(([field,message])=>[field==='displayName'?'name':field,message]));
     setErrors(fields);
    }
    setError(failure.message);
   }
  }finally{inFlight.current=false;setBusy(false);}
 }
 async function oauth(provider:'google'|'github'){
  if(inFlight.current||!(provider==='google'?googleReady:githubReady))return;
  inFlight.current=true;setError('');setGoogleBusy(true);setProviderBusy(provider);
  try{
   const result=await api('/auth/'+provider+'/start',{method:'POST',body:JSON.stringify({intent:mode,acceptedTerms:isSignup,rememberMe:remember})});
   if(typeof result.authorizationUrl!=='string')throw Error('Sign-in could not be started. Please try again.');
   const url=new URL(result.authorizationUrl);
   if(url.origin!==(provider==='google'?'https://accounts.google.com':'https://github.com')||url.username||url.password)throw Error('Sign-in could not be started. Please try again.');
   window.location.assign(url.href);
  }catch(e){setError(e instanceof Error?e.message:'Sign-in could not be started. Please try again.');setGoogleBusy(false);setProviderBusy('');inFlight.current=false;}
 }
 const title=success?'Check your inbox':isMfa?'One more step':isReset?'Reset your password':isSignup?'Make yourself at home.':'Welcome back';
 const errorFields=Object.entries(errors).filter(([,message])=>message);
 return <section className="icy-form" aria-labelledby="auth-title">
  <Link href="/" className="icy-browse">Browse projects <ArrowUpRight size={16} aria-hidden="true"/></Link>
  <div className="icy-form-content" key={mode}>
   {(isReset||isMfa)&&<button type="button" className="auth-back" onClick={()=>switchMode('login')} disabled={busy}><ArrowLeft size={16} aria-hidden="true"/> Back to login</button>}
   <span className="paper-auth-form-label">{isSignup?'Join getLancer':isMfa?'Account verification':isReset?'Account recovery':'Your getLancer account'}</span>
   <h1 id="auth-title" ref={heading} tabIndex={-1}>{title}</h1>
   <p className="icy-description">{success?email:isMfa?'Enter your authenticator code to protect administrator access.':isReset?'We’ll send you a link to get back into your account.':isSignup?'A space to share your work and discover what’s next.':'Log in to pick up where you left off.'}</p>
   {success?<div className="auth-success" role="status"><CheckCircle2 size={28} aria-hidden="true"/><p>{success}</p><p className="paper-auth-success-help">Can’t see it? Check your spam folder and the email above.</p><button type="button" className="auth-submit" onClick={()=>switchMode(signup?'signup':'login')}>Back to {signup?'signup':'login'} <ArrowRight size={18} aria-hidden="true"/></button></div>:<>
    {servicesReady!==true&&<div className="auth-connection" role="status" aria-live="polite"><p>{servicesReady===null?'Checking account services…':serviceMessage}</p>{servicesReady===false&&<>{preview&&<Link href="/preview/workspace">Explore the demo</Link>}<button type="button" onClick={()=>{setServicesReady(null);setCheckAttempt(n=>n+1)}} aria-label="Check again">Retry</button></>}</div>}
    {!isReset&&!isMfa&&<><div className="icy-providers">{(['google','github'] as const).map(provider=><button key={provider} type="button" className="icy-provider" aria-label={(googleBusy&&providerBusy===provider?'Opening sign-in with ':'Continue with ')+(provider==='google'?'Google':'GitHub')} disabled={(provider==='google'?googleReady!==true:!githubReady)||busy||googleBusy} onClick={()=>oauth(provider)}>{googleBusy&&providerBusy===provider?<LoaderCircle size={18} className="auth-spin" aria-hidden="true"/>:<img src={'/brand/'+(provider==='google'?'google-signin-icon.svg':'github-mark.png')} width={20} height={20} alt=""/>}{googleBusy&&providerBusy===provider?'Opening…':(provider==='google'?'Google':'GitHub')}</button>)}</div><div className="icy-divider"><span>or continue with email</span></div></>}
    <form className="icy-fields" onSubmit={submit} noValidate aria-busy={busy||googleBusy}>
     {error&&<div className="auth-error" ref={notice} role="alert" tabIndex={-1}>{errorFields.length>0&&<strong>Check these details</strong>}<p>{error}</p>{errorFields.length>0&&<ul>{errorFields.map(([field])=><li key={field}><a href={'#'+fieldIds[field]} onClick={event=>{event.preventDefault();focusField(field)}}>{{name:'Full name',email:'Email address',password:'Password',totp:'Authenticator code'}[field]||field}</a></li>)}</ul>}</div>}
     <fieldset disabled={busy||googleBusy}>
      {isMfa?<div className="icy-field"><label htmlFor="auth-totp">Authenticator code</label><InputOTP containerClassName="paper-auth-otp" id="auth-totp" maxLength={6} pattern="[0-9]*" inputMode="numeric" autoComplete="one-time-code" value={totp} onChange={value=>{setTotp(value);setErrors({})}} aria-invalid={Boolean(errors.totp)} aria-describedby="totp-help"><InputOTPGroup>{[0,1,2,3,4,5].map(index=><InputOTPSlot key={index} index={index}/>)}</InputOTPGroup></InputOTP><p id="totp-help" className={errors.totp?'auth-field-error':'icy-helper'}>{errors.totp||'Paste the full code. This step expires after 5 minutes.'}</p></div>:<>
       {isSignup&&<div className="icy-field"><label htmlFor="auth-name">Full name</label><div className="icy-input"><UserRound size={18} aria-hidden="true"/><input id="auth-name" name="displayName" autoComplete="name" placeholder="Your name" required minLength={2} maxLength={100} value={name} onChange={e=>{setName(e.target.value);if(errors.name)validate('name',e.target.value)}} onBlur={()=>validate('name',name)} aria-invalid={Boolean(errors.name)} aria-describedby={errors.name?'name-error':undefined}/></div>{errors.name&&<p id="name-error" className="auth-field-error">{errors.name}</p>}</div>}
       <div className="icy-field"><label htmlFor="auth-email">Email address</label><div className="icy-input"><Mail size={18} aria-hidden="true"/><input id="auth-email" name="email" type="email" autoComplete="email" autoCapitalize="none" spellCheck={false} placeholder="you@example.com" required maxLength={254} value={email} onChange={e=>{setEmail(e.target.value);if(errors.email)validate('email',e.target.value)}} onBlur={()=>validate('email',email)} aria-invalid={Boolean(errors.email)} aria-describedby={errors.email?'email-error':undefined}/></div>{errors.email&&<p id="email-error" className="auth-field-error">{errors.email}</p>}</div>
       {!isReset&&<div className="icy-field"><label htmlFor="auth-password">Password</label><div className="icy-input"><LockKeyhole size={18} aria-hidden="true"/><input id="auth-password" name="password" type={showPassword?'text':'password'} autoComplete={isSignup?'new-password':'current-password'} placeholder="Enter your password" required minLength={isSignup?12:undefined} maxLength={128} value={password} onChange={e=>{setPassword(e.target.value);if(errors.password)validate('password',e.target.value)}} onBlur={event=>{if(event.relatedTarget instanceof Node&&event.currentTarget.parentElement?.contains(event.relatedTarget))return;validate('password',password)}} aria-invalid={Boolean(errors.password)} aria-describedby={errors.password?'password-error':isSignup?'password-help':undefined}/><button type="button" onClick={()=>setShowPassword(!showPassword)} aria-label={showPassword?'Hide password':'Show password'} aria-pressed={showPassword}>{showPassword?<EyeOff size={18} aria-hidden="true"/>:<Eye size={18} aria-hidden="true"/>}</button></div>{errors.password?<p id="password-error" className="auth-field-error">{errors.password}</p>:isSignup&&<p id="password-help" className="icy-helper">Use a unique password with at least 12 characters.</p>}</div>}
       {!isSignup&&!isReset&&<div className="icy-options"><label><input type="checkbox" checked={remember} onChange={e=>setRemember(e.target.checked)}/> Keep me signed in</label><button type="button" onClick={()=>switchMode('reset')}>Forgot password?</button></div>}
      </>}
     </fieldset>
     <button className="auth-submit" disabled={busy||googleBusy||servicesReady!==true} type="submit">{busy?<><LoaderCircle size={18} className="auth-spin" aria-hidden="true"/>{isSignup?'Creating account…':isReset?'Sending link…':'Logging in…'}</>:<>{isMfa?'Verify and log in':isReset?'Send reset link':isSignup?'Create account':'Log in'}<ArrowRight size={18} aria-hidden="true"/></>}</button>
    </form>
    {isSignup&&<p className="icy-legal">By creating an account, you accept our <Link href="/policies#terms">Terms</Link> and <Link href="/policies#privacy">Privacy Policy</Link>.</p>}
    {!isReset&&!isMfa&&<p className="icy-switch">{isSignup?'Already have an account?':'New to getLancer?'} <Link href={isSignup?'/login':'/signup'}>{isSignup?'Log in':'Create an account'}</Link></p>}
    {servicesReady===true&&(googleReady===false||!githubReady)&&<p className="icy-service-note">You can sign in with your email and password.</p>}
   </>}
  </div>
  <p className="paper-auth-assistance">Need a hand? <Link href="/report">Contact support</Link></p>
 </section>;
}
