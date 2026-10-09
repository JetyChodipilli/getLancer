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
  const expires = Date.parse(value);
  if (!Number.isFinite(expires) || expires <= now || expires > now + 31 * 86400000) fail('Approval must be current and expire within 31 days.');
}

function evidencePath(root, name) {
  if (typeof name !== 'string' || !name || name.length > 200 || isAbsolute(name) || name.includes('\\') || name.split('/').some(p => !p || p === '.' || p === '..')) fail('Evidence path must stay inside its bundle.');
  return resolve(root, name);
}

export function verifyBundle({admissionPath, inventoryPath, publicKeyPath, operatorEpoch, now = Date.now()}) {
  if (!UUID.test(operatorEpoch || '')) fail('Supply the external restore epoch.');
  const inventoryBytes = bounded(inventoryPath), key = bounded(publicKeyPath);
  const admission = verifiedPayload(bounded(admissionPath), key);
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

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const [admissionPath, inventoryPath, publicKeyPath, operatorEpoch] = process.argv.slice(2);
    if (!admissionPath || !inventoryPath || !publicKeyPath || !operatorEpoch || process.argv.length !== 6) fail('Usage: node ops/labs/certify.mjs admission-envelope.json evidence-inventory.json operator-public.pem restore-epoch');
    const result = verifyBundle({admissionPath, inventoryPath, publicKeyPath, operatorEpoch});
    process.stdout.write(JSON.stringify({result:'ADMISSION_ARTIFACTS_VERIFIED', ...result})+'\n');
  } catch {process.stderr.write('Admission artifacts rejected. Check the signature, epoch, dated inventory and artifact digests.\n'); process.exitCode = 1;}
}
