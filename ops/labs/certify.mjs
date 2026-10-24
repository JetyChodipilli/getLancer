import {openSync, closeSync, fstatSync, readSync, constants} from 'node:fs';
import {resolve, dirname, isAbsolute} from 'node:path';
import {createHash, createPublicKey, verify} from 'node:crypto';
import {pathToFileURL} from 'node:url';

const MAX_BYTES = 1024 * 1024;
const HASH = /^[a-f0-9]{64}$/;
const UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;
const CONTROLS = ['isolation', 'network', 'cleanup', 'restore', 'licence', 'cost', 'build'];
const fail = message => {throw new Error(message);};
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

function openUnlinked(path) {
  if (process.platform !== 'linux') fail('Verify evidence on Linux with descriptor-relative file access.');
  const parts = resolve(path).split('/').filter(Boolean);
  const flags = constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK;
  let descriptor = openSync('/', flags | constants.O_DIRECTORY);
  try {
    for (let i = 0; i < parts.length; i++) {
      // Anchor each component to its open parent; renamed directories cannot redirect the walk.
      const next = openSync('/proc/self/fd/' + descriptor + '/' + parts[i], flags | (i < parts.length - 1 ? constants.O_DIRECTORY : 0));
      closeSync(descriptor); descriptor = next;
    }
    return descriptor;
  } catch (error) {closeSync(descriptor); throw error;}
}

function bounded(path) {
  let descriptor;
  try {
    descriptor = openUnlinked(path);
    const info = fstatSync(descriptor);
    if (!info.isFile() || info.size > MAX_BYTES || info.size === 0) fail('Use bounded regular evidence files.');
    const bytes = Buffer.alloc(MAX_BYTES + 1); let count = 0;
    while (count < bytes.length) {
      const length = readSync(descriptor, bytes, count, bytes.length - count, null);
      if (length === 0) break;
      count += length;
    }
    if (count === 0) fail('Use bounded regular evidence files.');
    if (count > MAX_BYTES) fail('Evidence exceeded the size limit.');
    return bytes.subarray(0, count);
  } catch (error) {
    if (error.code === 'ELOOP' || error.code === 'ENOTDIR') fail('Linked evidence is not admitted.');
    throw error;
  } finally {if (descriptor !== undefined) closeSync(descriptor);}
}

// Reuse the same descriptor-bound reader for independently signed phase evidence.
export {bounded as readEvidence};

function base64(value) {
  if (typeof value !== 'string' || !value || value.length > MAX_BYTES * 2 || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(value)) fail('Invalid signed envelope.');
  const bytes = Buffer.from(value, 'base64');
  if (bytes.toString('base64') !== value || bytes.length > MAX_BYTES) fail('Invalid signed envelope.');
  return bytes;
}

export function verifiedPayload(envelopeBytes, keyBytes) {
  if (envelopeBytes.length > MAX_BYTES || keyBytes.length > 16384) fail('Signed input exceeded the size limit.');
  const envelope = JSON.parse(new TextDecoder('utf-8', {fatal:true}).decode(envelopeBytes));
  if (Object.keys(envelope).sort().join(',') !== 'payload,signature') fail('Unexpected signed envelope fields.');
  const payload = base64(envelope.payload), signature = base64(envelope.signature);
  const key = createPublicKey(keyBytes);
  if (key.asymmetricKeyType !== 'ed25519' || signature.length !== 64 || !verify(null, payload, key, signature)) fail('Operator signature did not verify.');
  return JSON.parse(new TextDecoder('utf-8', {fatal:true}).decode(payload));
}

function currentApproval(value, now) {
  // Jackson Instant fields require an ISO instant, not Date.parse's permissive English dates.
  const parts = typeof value === 'string' && /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$/.exec(value);
  if (!parts) fail('Approval must be a current ISO instant and expire within 31 days.');
  const [year, month, day, hour, minute, second] = parts.slice(1, 7).map(Number);
  const calendar = new Date(Date.UTC(year, month - 1, day, hour, minute, second)).toISOString().slice(0, 19) === value.slice(0, 19);
  const offset = parts[8], offsetHours = Number(offset.slice(1, 3)), offsetMinutes = Number(offset.slice(4, 6));
  const validOffset = offset === 'Z' || offsetHours <= 18 && offsetMinutes <= 59 && (offsetHours < 18 || offsetMinutes === 0);
  const wholeSeconds = Date.parse(value.slice(0, 19) + offset);
  if (!calendar || !validOffset || !Number.isFinite(wholeSeconds)) fail('Approval must be a current ISO instant and expire within 31 days.');
  // Preserve all Instant precision for host/manifest ordering and the approval horizon.
  const expires = BigInt(wholeSeconds) * 1000000n + BigInt((parts[7] || '').padEnd(9, '0'));
  const current = BigInt(now) * 1000000n;
  if (expires <= current || expires > current + 31n * 86400000000000n) fail('Approval must be a current ISO instant and expire within 31 days.');
  return expires;
}

function evidencePath(root, name) {
  if (typeof name !== 'string' || !name || name.length > 200 || isAbsolute(name) || name.includes('\\') || name.split('/').some(p => !p || p === '.' || p === '..')) fail('Evidence path must stay inside its bundle.');
  return resolve(root, name);
}

export function verifyBundle({admissionPath, inventoryPath, publicKeyPath, publicKeyBytes, operatorEpoch, expectedAdmissionSha256, expectedInventorySha256, now = Date.now()}) {
  if (!UUID.test(operatorEpoch || '')) fail('Supply the external restore epoch.');
  const inventoryBytes = bounded(inventoryPath), admissionBytes = bounded(admissionPath), key = publicKeyBytes ?? bounded(publicKeyPath);
  pinned(inventoryBytes,expectedInventorySha256); pinned(admissionBytes,expectedAdmissionSha256);
  if (!Buffer.isBuffer(key) || key.length === 0 || key.length > 16384) fail('Use bounded operator public-key bytes.');
  const admission = verifiedPayload(admissionBytes, key);
  if (admission.operatorEpoch !== operatorEpoch || !UUID.test(admission.operatorEpoch)) fail('Admission belongs to another restore epoch.');
  if (typeof admission.provider !== 'string' || admission.provider.length < 2 || admission.provider.length > 100 || admission.isolation !== 'KVM') fail('A named independently verified KVM provider is required.');
  currentApproval(admission.approvedUntil, now);
  for (const field of ['licenceReviewed','budgetApproved','networkVerified','cleanupVerified','restoreVerified']) if (admission[field] !== true) fail('Every admission approval is required.');
  if (!HASH.test(admission.evidenceSha256 || '') || admission.evidenceSha256 !== sha256(inventoryBytes)) fail('Admission evidence inventory changed.');
  const inventory = JSON.parse(new TextDecoder('utf-8', {fatal:true}).decode(inventoryBytes));
  if (inventory.version !== 1 || inventory.mode !== 'PRODUCTION' || inventory.operatorEpoch !== operatorEpoch || inventory.provider !== admission.provider || !Array.isArray(inventory.records)) fail('Use a production evidence inventory bound to this provider and epoch.');
  if (inventory.records.length !== CONTROLS.length || new Set(inventory.records.map(r=>r.control)).size !== CONTROLS.length) fail('Every independent admission control must be recorded once.');
  const root = dirname(resolve(inventoryPath));
  for (const control of CONTROLS) {
    const record = inventory.records.find(r=>r.control === control);
    if (!record || record.result !== 'PASS' || !HASH.test(record.artifactSha256 || '')) fail('Missing passing admission control.');
    const recorded = Date.parse(record.recordedAt);
    if (!Number.isFinite(recorded) || recorded > now || recorded < now - 31 * 86400000) fail('Admission evidence must be dated and current.');
    if (sha256(bounded(evidencePath(root, record.evidencePath))) !== record.artifactSha256) fail('Admission evidence artifact changed.');
  }
  return {provider:admission.provider, operatorEpoch, approvedUntil:admission.approvedUntil, evidenceSha256:admission.evidenceSha256, controls:CONTROLS};
}

function pinned(bytes,expected) {
  if(expected!==undefined&&(!HASH.test(expected)||sha256(bytes)!==expected))fail('Captured evidence does not match its expected digest.');
}

// Keep required artifact bounds aligned with LabManifest and the reviewed native policies.
function nativeContract(manifest) {
  const reject=()=>fail('Native manifest resource and scenario contract is invalid.');
  const integer=(value,min,max)=>Number.isSafeInteger(value)&&value>=min&&value<=max;
  const text=(value,min,max)=>typeof value==='string'&&value.length>=min&&value.length<=max;
  const slug=value=>typeof value==='string'&&/^[a-z0-9][a-z0-9-]{0,59}$/.test(value);
  if(!manifest||typeof manifest!=='object'||Array.isArray(manifest)||!text(manifest.id,1,80)||!/^([a-z0-9][a-z0-9-]{0,79})$/.test(manifest.id)
      ||!text(manifest.title,3,100)||!text(manifest.summary,10,500)||!text(manifest.setup,10,6000)
      ||!text(manifest.language,1,50)||!text(manifest.framework,1,80)||!integer(manifest.memoryMiB,64,512)||!integer(manifest.maxCostMicros,1,1000000)
      ||manifest.resourceClass!=null&&!['GENERAL','DATA_ANALYTICS','AI_ML','IOT_SIMULATION'].includes(manifest.resourceClass)
      ||!Array.isArray(manifest.scenarios)||manifest.scenarios.length<1||manifest.scenarios.length>20)reject();
  const seen=new Set();
  for(const scenario of manifest.scenarios){
    if(!scenario||!slug(scenario.id)||seen.has(scenario.id)||!text(scenario.title,3,100)||!text(scenario.description,10,1000)
        ||!Array.isArray(scenario.inputs)||scenario.inputs.length>20||!Array.isArray(scenario.operations)||scenario.operations.length<1||scenario.operations.length>10)reject();
    seen.add(scenario.id);const names=new Set(),operations=new Set();
    for(const input of scenario.inputs){
      if(!input||typeof input.name!=='string'||!/^[a-z][a-zA-Z0-9]{0,39}$/.test(input.name)||names.has(input.name)||!text(input.label,1,80)
          ||!['TEXT','INTEGER','BOOLEAN','ENUM'].includes(input.type)||typeof input.required!=='boolean'||!integer(input.maxLength,1,4096)
          ||!Number.isSafeInteger(input.min)||!Number.isSafeInteger(input.max)||input.min>input.max||!Array.isArray(input.choices)||input.choices.length>30
          ||input.choices.some(choice=>!text(choice,1,input.maxLength))||input.type==='ENUM'&&input.choices.length===0)reject();
      names.add(input.name);
    }
    for(const operation of scenario.operations){if(!operation||!slug(operation.id)||operations.has(operation.id)||!text(operation.label,1,80))reject();operations.add(operation.id);}
  }
  const iot=manifest.resourceClass==='IOT_SIMULATION'||manifest.id.startsWith('iot-')||seen.has('greenhouse-monitor');
  if(iot){
    if(manifest.resourceClass!=null&&!['GENERAL','IOT_SIMULATION'].includes(manifest.resourceClass)||manifest.memoryMiB>128||manifest.scenarios.length!==1||!seen.has('greenhouse-monitor'))reject();
    const scenario=manifest.scenarios[0],bounds={temperatureC:[-20,80],humidityPercent:[0,100],thresholdC:[0,60]};
    if(scenario.inputs.length<3||scenario.inputs.length>4)reject();
    for(const [name,[min,max]] of Object.entries(bounds)){const field=scenario.inputs.find(item=>item.name===name);if(!field||!field.required||field.type!=='INTEGER'||field.min!==min||field.max!==max||field.maxLength<(name==='thresholdC'?2:3)||field.maxLength>4||field.choices.length)reject();}
    for(const field of scenario.inputs)if(!Object.hasOwn(bounds,field.name)&&(field.name!=='topic'||field.required||field.type!=='TEXT'||field.maxLength>80||field.choices.length))reject();
    if(scenario.operations.some(item=>!['publish','disconnect','connect','reset'].includes(item.id)))reject();
  }else if(manifest.resourceClass!=null&&manifest.resourceClass!=='GENERAL'||manifest.scenarios.some(item=>!['cache','security','payment'].includes(item.id)))reject();
  return iot;
}

/** Bind exact native review facts to the same trusted host inventory and reproduction artifact. */
export function verifyManifestBundle({manifestPath,expectedManifestSha256,...options}) {
  const key=options.publicKeyBytes??bounded(options.publicKeyPath);
  const host=verifyBundle({...options,publicKeyBytes:key});
  const bytes=bounded(manifestPath); pinned(bytes,expectedManifestSha256);
  const manifest=verifiedPayload(bytes,key), now=options.now??Date.now();
  const iot=nativeContract(manifest);
  if(manifest.evidenceSha256!==host.evidenceSha256)fail('Manifest host inventory changed.');
  const manifestExpiry=currentApproval(manifest.approvedUntil,now);
  if(manifestExpiry>currentApproval(host.approvedUntil,now))fail('Manifest approval exceeds host approval.');
  if(!UUID.test(manifest.componentId||'')||!Number.isSafeInteger(manifest.revision)||manifest.revision<1)fail('Manifest component revision is invalid.');
  for(const field of ['sourceSha256','archiveSha256','manifestSha256','buildSourceSha256'])if(!HASH.test(manifest[field]||''))fail('Manifest source digest is invalid.');
  if(manifest.archiveSha256!==manifest.buildSourceSha256||!/^sha256:[a-f0-9]{64}$/.test(manifest.imageDigest||'')||manifest.protocolVersion!=='getlancer-lab-v1')fail('Manifest build or image identity is invalid.');
  const adapter=manifest.adapter, certificate=manifest.certification;
  if(!adapter||Object.keys(adapter).sort().join(',')!=='runtime,state,version'||adapter.state!=='ACTIVE'||!/^\d{1,4}\.\d{1,4}\.\d{1,4}$/.test(adapter.version||''))fail('Exact active native adapter is required.');
  const runtime=new Map([['java','jdk'],['typescript','node'],['javascript','node'],['python','python'],['go','go'],['c#','dotnet'],['.net','dotnet'],['c# / .net','dotnet'],['dotnet','dotnet'],['php','php']]).get(String(manifest.language).toLowerCase());
  const [major,minor]=adapter.version.split('.').map(Number);
  const family=runtime==='jdk'?major===17:runtime==='node'?major===22&&minor>=18:runtime==='python'?major===3&&minor===12:runtime==='go'?major===1&&minor===24:runtime==='dotnet'?major===8:runtime==='php'?major===8&&minor===3:false;
  if(adapter.runtime!==runtime||!family||iot&&runtime!=='node'||manifest.framework!==(iot?'Node MQTT protocol':'Standard library'))fail('Framework and runtime combination is not reviewed.');
  if(!certificate||Object.keys(certificate).sort().join(',')!=='frameworkVersion,isolationVerified,licenceReviewed,maintainer,reproductionSha256,reproductionVerified,resourcesVerified,reviewedAt'||certificate.frameworkVersion!==adapter.version||!/^([A-Za-z0-9][A-Za-z0-9._@+-]{2,99})$/.test(certificate.maintainer||'')||!HASH.test(certificate.reproductionSha256||''))fail('Exact maintainer and reproduction certification is required.');
  for(const field of ['licenceReviewed','reproductionVerified','resourcesVerified','isolationVerified'])if(certificate[field]!==true)fail('Every certification attestation is required.');
  const reviewed=Date.parse(certificate.reviewedAt);
  if(!Number.isFinite(reviewed)||new Date(reviewed).toISOString()!==certificate.reviewedAt||reviewed>now||reviewed<now-31*86400000)fail('Certification review must be canonical and current.');
  // Inventory was already captured and hashed by verifyBundle. Re-read through its pinned digest
  // so a replacement cannot introduce a different reproduction artifact.
  const inventoryBytes=bounded(options.inventoryPath);pinned(inventoryBytes,host.evidenceSha256);
  const inventory=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(inventoryBytes));
  const build=inventory.records.find(x=>x.control==='build');
  if(!build||certificate.reproductionSha256!==build.artifactSha256||reviewed<Date.parse(build.recordedAt)||sha256(bounded(evidencePath(dirname(resolve(options.inventoryPath)),build.evidencePath)))!==certificate.reproductionSha256)fail('Reviewed reproduction artifact changed.');
  return {...host,manifestId:manifest.id,componentId:manifest.componentId,revision:manifest.revision,language:manifest.language,runtime:adapter.runtime,runtimeVersion:adapter.version,sourceSha256:manifest.sourceSha256,imageDigest:manifest.imageDigest,certification:certificate};
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const [admissionPath, inventoryPath, publicKeyPath, operatorEpoch, manifestPath] = process.argv.slice(2);
    if (!admissionPath || !inventoryPath || !publicKeyPath || !operatorEpoch || ![6,7].includes(process.argv.length)) fail('Usage: node ops/labs/certify.mjs admission-envelope.json evidence-inventory.json operator-public.pem restore-epoch [manifest-envelope.json]');
    const options={admissionPath, inventoryPath, publicKeyPath, operatorEpoch};
    const result = manifestPath?verifyManifestBundle({...options,manifestPath}):verifyBundle(options);
    process.stdout.write(JSON.stringify({result:manifestPath?'NATIVE_MANIFEST_ARTIFACTS_VERIFIED':'ADMISSION_ARTIFACTS_VERIFIED', ...result})+'\n');
  } catch {process.stderr.write('Admission artifacts rejected. Check the signature, epoch, dated inventory and artifact digests.\n'); process.exitCode = 1;}
}
