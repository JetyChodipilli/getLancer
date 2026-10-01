import { backendOrigin } from '@/lib/server';
const MAX_BODY = 6 * 1024 * 1024;
async function readBody(request: Request) {
  if (Number(request.headers.get('content-length') || 0) > MAX_BODY) throw new RangeError('Request too large');
  const reader = request.body?.getReader();
  if (!reader) return undefined;
  let size = 0; const chunks: Uint8Array[] = [];
  try {
    for (;;) {
      const {done, value} = await reader.read(); if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY) { await reader.cancel(); throw new RangeError('Request too large'); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  return bytes;
}
async function proxy(req: Request, {params}: {params: Promise<{path: string[]}>}) {
  const origin = await backendOrigin();
  if (!origin) return Response.json({error: {code: 'BACKEND_NOT_CONFIGURED', message: 'Account and inquiry services are not available in this design preview.'}}, {status: 503});
  const {path} = await params; const url = new URL(req.url);
  if (!['GET','HEAD'].includes(req.method) && (req.headers.get('origin') !== url.origin || req.headers.get('x-requested-with') !== 'getlancer')) return Response.json({error: {code: 'FORBIDDEN', message: 'Invalid request origin.'}}, {status: 403});
  const headers = new Headers({'Content-Type': req.headers.get('content-type') || 'application/json', 'X-Requested-With': 'getlancer', 'Origin': url.origin});
  // Trust only the platform-supplied client address; never relay X-Forwarded-For.
  let proxySecret=process.env.BACKEND_PROXY_SECRET;
  try{const {env}=await import('cloudflare:workers');proxySecret=(env as {BACKEND_PROXY_SECRET?:string}).BACKEND_PROXY_SECRET||proxySecret}catch{}
  const clientIp=req.headers.get('cf-connecting-ip');
  if(proxySecret&&clientIp&&/^[0-9a-fA-F:.]{3,45}$/.test(clientIp)){headers.set('X-GetLancer-Proxy',proxySecret);headers.set('X-GetLancer-Client-IP',clientIp)}
  for (const key of ['cookie', 'idempotency-key']) { const value = req.headers.get(key); if (value) headers.set(key, value); }
  try {
    const response = await fetch(origin + '/api/v1/' + path.map(encodeURIComponent).join('/') + url.search, {
      method: req.method, headers, body: ['GET', 'HEAD'].includes(req.method) ? undefined : await readBody(req),
      redirect: 'manual', signal: AbortSignal.timeout(20000),
    });
    const result = new Headers({'Content-Type': response.headers.get('content-type') || 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff'});
    for (const cookie of response.headers.getSetCookie()) result.append('Set-Cookie', cookie);
    for (const key of ['retry-after','x-request-id','content-disposition']) { const value = response.headers.get(key); if (value) result.set(key,value); }
    // Only the OAuth callback may redirect, and only to these app-owned destinations.
    const location=response.headers.get('location');
    if(['auth/google/callback','auth/github/callback'].includes(path.join('/'))&&response.status===303&&location&&/^\/(?:workspace|(?:login|signup)\?auth_error=[a-z_]+(?:&provider=github)?)$/.test(location))result.set('Location',location);
    return new Response(response.body, {status: response.status, headers: result});
  } catch (error) {
    return Response.json({error: {code: error instanceof RangeError ? 'PAYLOAD_TOO_LARGE' : 'SERVICE_UNAVAILABLE', message: error instanceof RangeError ? 'Choose a file smaller than 5 MB.' : 'The marketplace is temporarily unavailable. Please try again.'}}, {status: error instanceof RangeError ? 413 : 503});
  }
}
export {proxy as GET, proxy as POST, proxy as PUT, proxy as PATCH, proxy as DELETE};
