'use client';

import {FormEvent, useEffect, useRef, useState} from 'react';
import Link from 'next/link';
import {ArrowLeft, ArrowRight, Eye, EyeOff, LoaderCircle, Mail} from 'lucide-react';
import {LockKeyhole, UserRound} from 'lucide-react';
import {InputOTP, InputOTPGroup, InputOTPSlot} from '@/components/ui/input-otp';
import {api, ApiError} from '@/lib/api';


type Mode='login'|'signup'|'reset'|'mfa';
const googleErrors:Record<string,string>={
 cancelled:'Google sign-in was cancelled. You can try again or use your email.',
 expired:'That sign-in attempt expired. Please start again.',
 failed:'Google sign-in could not be completed. Please try again.',
 unavailable:'Google sign-in is not available yet. Please use email and password.',
 signup_required:'Create your getLancer account first. Continue with Google below to join.',
 email_account:'This email already has an account. Please use the sign-in method you originally chose.',
 admin_password:'Administrator sign-in requires your password and authenticator code.',
 account_unavailable:'This account cannot sign in. Please use your existing account or contact support.',
};

export function AuthForm({signup=false,preview=false}:{signup?:boolean;preview?:boolean}){
 const [mode,setMode]=useState<Mode>(signup?'signup':'login');
 const [email,setEmail]=useState('');const [password,setPassword]=useState('');const [name,setName]=useState('');
 const [showPassword,setShowPassword]=useState(false);const [remember,setRemember]=useState(false);const [totp,setTotp]=useState('');
 const [errors,setErrors]=useState<Record<string,string>>({});const [error,setError]=useState('');
 const [success,setSuccess]=useState('');const [busy,setBusy]=useState(false);const [googleBusy,setGoogleBusy]=useState(false);
 const [githubReady,setGithubReady]=useState(false);const [providerBusy,setProviderBusy]=useState('');
 const [googleReady,setGoogleReady]=useState<boolean|null>(null);const [servicesReady,setServicesReady]=useState<boolean|null>(null);
 const [serviceMessage,setServiceMessage]=useState('');const [checkAttempt,setCheckAttempt]=useState(0);
 const notice=useRef<HTMLDivElement>(null);const heading=useRef<HTMLHeadingElement>(null);
 useEffect(()=>{const controller=new AbortController();api('/auth/providers',{signal:controller.signal}).then(r=>{setGoogleReady(Boolean(r.google));setGithubReady(Boolean(r.github));setServicesReady(true)}).catch(e=>{if(controller.signal.aborted)return;setGoogleReady(false);setGithubReady(false);setServicesReady(false);setServiceMessage(e instanceof ApiError&&e.code==='BACKEND_NOT_CONFIGURED'?'Sign-in is unavailable in this preview.':'Account services are temporarily unavailable. Please check your connection and try again.')});return()=>controller.abort()},[checkAttempt]);
 useEffect(()=>{const query=new URLSearchParams(window.location.search);const code=query.get('auth_error');const queryFrame=requestAnimationFrame(()=>{if(code)setError(code==='email_unverified'?'Verify your primary GitHub email before continuing.':(googleErrors[code]||'Sign-in could not be completed. Please try again.').replaceAll('Google',query.get('provider')==='github'?'GitHub':'Google'))});return()=>cancelAnimationFrame(queryFrame)},[]);
 useEffect(()=>{if(error)notice.current?.focus()},[error]);
 function switchMode(next:Mode){setMode(next);setError('');setErrors({});setSuccess('');setPassword('');setTotp('');setShowPassword(false);requestAnimationFrame(()=>heading.current?.focus())}
 function validate(field:string,value:string){let message='';if(field==='email'&&!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim()))message='Enter a valid email address.';if(field==='name'&&mode==='signup'&&(value.trim().length<2||value.trim().length>100))message='Enter your name (2–100 characters).';if(field==='password'&&(!value||(mode==='signup'&&(value.length<12||new TextEncoder().encode(value).length>72))))message=mode==='signup'?'Use at least 12 characters and at most 72 bytes.':'Enter your password.';setErrors(old=>({...old,[field]:message}));return !message}
 async function submit(event:FormEvent<HTMLFormElement>){
  event.preventDefault();if(busy||googleBusy||servicesReady!==true)return;setError('');
  if(mode!=='mfa'){
   const validEmail=validate('email',email),validName=mode!=='signup'||validate('name',name),validPassword=mode==='reset'||validate('password',password);
   if(!validEmail||!validName||!validPassword){document.getElementById(!validName?'auth-name':!validEmail?'auth-email':'auth-password')?.focus();return}
  }else if(!/^\d{6}$/.test(totp)){setErrors({totp:'Enter the 6-digit code from your authenticator.'});return}
  setBusy(true);
  try{
   const path=mode==='mfa'?'/auth/login/mfa':mode==='reset'?'/auth/password-reset/request':'/auth/'+mode;
   const result=await api(path,{method:'POST',body:JSON.stringify(mode==='mfa'?{totp}:{email:email.trim(),rememberMe:remember,...(mode!=='reset'?{password}:{}),...(mode==='signup'?{displayName:name.trim(),acceptedTerms:true}:{})})});
   if(result.mfaRequired){switchMode('mfa');return}
   if(mode==='login'||mode==='mfa'){setPassword('');window.location.assign('/workspace');return}
   setPassword('');setSuccess(mode==='reset'?'If an account exists for this email, you’ll receive a link to reset your password.':'Your account is created. Open the confirmation email to verify your address and get started.');
  }catch(e){const failure=e as ApiError;if(failure.code==='MFA_EXPIRED'){switchMode('login');setError('Your verification step expired. Please sign in again.')}else setError(failure.message)}finally{setBusy(false)}
 }
 async function oauth(provider:'google'|'github'){
  if(googleBusy||busy||!(provider==='google'?googleReady:githubReady))return;setError('');setGoogleBusy(true);setProviderBusy(provider);
  try{const r=await api('/auth/'+provider+'/start',{method:'POST',body:JSON.stringify({intent:mode,acceptedTerms:mode==='signup',rememberMe:remember})});const url=new URL(r.authorizationUrl);if(url.origin!==(provider==='google'?'https://accounts.google.com':'https://github.com'))throw Error('Sign-in could not be started.');window.location.assign(url.href)}catch(e){setError((e as Error).message);setGoogleBusy(false)}
 }
 const isSignup=mode==='signup',isMfa=mode==='mfa',isReset=mode==='reset';
 const title=success?'Check your inbox':isMfa?'One more step':isReset?'Reset your password':isSignup?'Create your account':'Welcome back';
 return <section className="icy-form" aria-labelledby="auth-title">
  <Link href="/" className="icy-browse">Browse projects <ArrowRight size={18} aria-hidden="true"/></Link>
  <div className="icy-form-content" key={mode}>
   {(isReset||isMfa)&&<button className="auth-back" onClick={()=>switchMode('login')} disabled={busy}><ArrowLeft size={16}/> Back to login</button>}
   <h1 id="auth-title" ref={heading} tabIndex={-1}>{title}</h1>
   <p className="icy-description">{success?email:isMfa?'Enter the code from your authenticator app to protect administrator access.':isReset?'We’ll email you a link to get back into your account.':isSignup?'Show your work. Find your people.':'Your next opportunity starts here.'}</p>
   {success?<div className="auth-success" role="status"><Mail size={26}/><p>{success}</p><p>Can’t see it? Check your spam folder and the email above.</p><button className="auth-submit" onClick={()=>switchMode(signup?'signup':'login')}>Back to {signup?'signup':'login'}</button></div>:<>
    {servicesReady!==true&&<div className="auth-connection" role="status" aria-live="polite"><p>{servicesReady===null?'Checking account services…':serviceMessage}</p>{servicesReady===false&&<>{preview&&<Link href="/preview/workspace">Try demo →</Link>}<button type="button" onClick={()=>{setServicesReady(null);setCheckAttempt(n=>n+1)}} aria-label="Check again">Retry</button></>}</div>}
    {!isReset&&!isMfa&&<><div className="icy-providers">{(['google','github'] as const).map(provider=><button key={provider} type="button" className="icy-provider" aria-label={'Continue with '+(provider==='google'?'Google':'GitHub')} disabled={(provider==='google'?googleReady!==true:!githubReady)||busy||googleBusy} onClick={()=>oauth(provider)}>{googleBusy&&providerBusy===provider?<LoaderCircle size={23} className="auth-spin"/>:<img src={'/brand/'+(provider==='google'?'google-signin-icon.svg':'github-mark.png')} width={24} height={24} alt=""/>}{googleBusy&&providerBusy===provider?'Connecting…':(provider==='google'?'Google':'GitHub')}</button>)}</div><div className="icy-divider"><span>or continue with email</span></div></>}
    <form className="icy-fields" onSubmit={submit} noValidate aria-busy={busy||googleBusy}>
     <fieldset disabled={busy||googleBusy}>
      {isMfa?<div className="icy-field"><label htmlFor="auth-totp">Authenticator code</label><InputOTP id="auth-totp" maxLength={6} pattern="[0-9]*" inputMode="numeric" autoComplete="one-time-code" value={totp} onChange={value=>{setTotp(value);setErrors({})}} aria-invalid={Boolean(errors.totp)} aria-describedby="totp-help" autoFocus><InputOTPGroup>{[0,1,2,3,4,5].map(index=><InputOTPSlot key={index} index={index}/>)}</InputOTPGroup></InputOTP><p id="totp-help" className="icy-helper">{errors.totp||'Paste the full code. This step expires after 5 minutes.'}</p></div>:<>
       {isSignup&&<div className="icy-field"><label htmlFor="auth-name">Full name</label><div className="icy-input"><UserRound size={20}/><input id="auth-name" name="displayName" autoComplete="name" placeholder="Your name" required maxLength={100} value={name} onChange={e=>setName(e.target.value)} onBlur={()=>validate('name',name)} aria-invalid={Boolean(errors.name)} aria-describedby={errors.name?'name-error':undefined}/></div>{errors.name&&<p id="name-error" className="auth-field-error">{errors.name}</p>}</div>}
       <div className="icy-field"><label htmlFor="auth-email">Email address</label><div className="icy-input"><Mail size={20}/><input id="auth-email" name="email" type="email" autoComplete="email" autoCapitalize="none" spellCheck={false} placeholder="you@example.com" required maxLength={254} value={email} onChange={e=>setEmail(e.target.value)} onBlur={()=>validate('email',email)} aria-invalid={Boolean(errors.email)} aria-describedby={errors.email?'email-error':undefined}/></div>{errors.email&&<p id="email-error" className="auth-field-error">{errors.email}</p>}</div>
       {!isReset&&<div className="icy-field"><label htmlFor="auth-password">Password</label><div className="icy-input"><LockKeyhole size={20}/><input id="auth-password" name="password" type={showPassword?'text':'password'} autoComplete={isSignup?'new-password':'current-password'} placeholder="••••••••••••" required maxLength={128} value={password} onChange={e=>setPassword(e.target.value)} onBlur={()=>validate('password',password)} aria-invalid={Boolean(errors.password)} aria-describedby={errors.password?'password-error':isSignup?'password-help':undefined}/><button type="button" onClick={()=>setShowPassword(!showPassword)} aria-label={showPassword?'Hide password':'Show password'} aria-pressed={showPassword}>{showPassword?<EyeOff size={20}/>:<Eye size={20}/>}</button></div>{errors.password?<p id="password-error" className="auth-field-error">{errors.password}</p>:isSignup&&<p id="password-help" className="icy-helper">Use a unique password with at least 12 characters.</p>}</div>}
       {!isSignup&&!isReset&&<div className="icy-options"><label><input type="checkbox" checked={remember} onChange={e=>setRemember(e.target.checked)}/> Keep me signed in</label><button type="button" onClick={()=>switchMode('reset')}>Forgot password?</button></div>}
      </>}
     </fieldset>
     {error&&<div className="auth-error" ref={notice} role="alert" tabIndex={-1}>{error}</div>}
     <button className="auth-submit" disabled={busy||googleBusy||servicesReady!==true} type="submit">{busy?<><LoaderCircle size={18} className="auth-spin"/>{isSignup?'Creating account…':isReset?'Sending link…':'Logging in…'}</>:isMfa?'Verify and log in':isReset?'Send reset link':isSignup?'Create account':'Log in'}</button>
    </form>
    {isSignup&&<p className="icy-legal">By creating an account, you accept our <Link href="/policies#terms">Terms</Link> and <Link href="/policies#privacy">Privacy Policy</Link>.</p>}
    {!isReset&&!isMfa&&<p className="icy-switch">{isSignup?'Already a member?':'New to getLancer?'} <a href={isSignup?'/login':'/signup'}>{isSignup?'Log in':'Create account'}</a></p>}
    {servicesReady===true&&(googleReady===false||!githubReady)&&<p className="icy-service-note">Unavailable sign-in providers are being connected. You can use email and password.</p>}
   </>}
  </div>
 </section>;
}
