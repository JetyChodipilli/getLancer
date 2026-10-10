/** Static source material and recorded local evidence. This module never executes a lab. */
export type ScenarioLanguage = 'java' | 'typescript' | 'python';
export type ScenarioPattern = 'cache' | 'security' | 'payment';
export type ScenarioEdgeId = 'client-service' | 'service-redis' | 'service-store' | 'service-policy' | 'policy-store' | 'service-emulator' | 'emulator-ledger';
export type ScenarioEdge = {id: ScenarioEdgeId; from: string; to: string; label: string};
export type ScenarioMaterial = {
  id: string; language: ScenarioLanguage; pattern: ScenarioPattern; title: string; summary: string;
  sourceHash: string; archiveHash: string; sourceUrl: string; recordingUrl: string | null;
  setup: string; scenarios: string[]; edges: ScenarioEdge[];
};
export type ScenarioEvent = {
  runId: string; sourceHash: string; requestId: string; sequence: number; type: string;
  edge: ScenarioEdgeId; recordedAt: string; summary: string;
};
export type ScenarioState =
  | {cache: 'MISS' | 'HIT' | 'FALLBACK' | 'EXPIRED' | 'RESET'; storeReads: number; ttlMs: number}
  | {decision: 'ALLOW' | 'DENY' | 'RESET'; reason: 'ALLOW' | 'ROLE' | 'TENANT' | 'EXPIRED' | 'SIGNATURE' | 'REVOKED' | 'RESET'; currentRole: 'editor' | 'viewer'}
  | {payment: 'PENDING' | 'PAID' | 'REFUNDED'; entitlements: 0 | 1; processedEvents: number};
export type ScenarioResponse = {
  runId: string; sourceHash: string; requestId: string; pattern: ScenarioPattern;
  status: number; durationMs: number; state: ScenarioState; events: ScenarioEvent[];
};
export type ScenarioRecording = {
  mode: 'REPLAY'; labId: string; language: ScenarioLanguage; pattern: ScenarioPattern;
  runId: string; sourceHash: string; recordedAt: string; responses: ScenarioResponse[];
};
export const scenarioLanguages: Record<ScenarioLanguage, string> = {java: 'Java', typescript: 'TypeScript', python: 'Python'};
export const scenarioPatterns: Record<ScenarioPattern, string> = {cache: 'Redis cache', security: 'Authorization', payment: 'Payment emulator'};
export const MATERIAL_BYTE_LIMIT = 65536;
export const RECORDING_BYTE_LIMIT = 524288;

const hashes = /^[a-f0-9]{64}$/;
const uuids = /^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
const types: Record<ScenarioPattern, readonly string[]> = {
  cache: ['CACHE_MISS', 'CACHE_HIT', 'CACHE_EXPIRED', 'CACHE_FALLBACK', 'CACHE_RESET', 'STORE_READ'],
  security: ['AUTH_ALLOWED', 'AUTH_DENIED', 'AUTH_REVOKED', 'AUTH_RESET'],
  payment: ['PAYMENT_APPLIED', 'PAYMENT_DUPLICATE', 'PAYMENT_SIGNATURE_DENIED', 'PAYMENT_CONFLICT', 'PAYMENT_PENDING', 'PAYMENT_RESET', 'REFUND_PENDING', 'REFUND_APPLIED'],
};
const edges: Record<ScenarioPattern, readonly ScenarioEdgeId[]> = {
  cache: ['client-service', 'service-redis', 'service-store'],
  security: ['client-service', 'service-policy', 'policy-store'],
  payment: ['client-service', 'service-emulator', 'emulator-ledger'],
};
function observedEdge(type: string, edge: ScenarioEdgeId): boolean {
  if (type === 'STORE_READ') return edge === 'service-store';
  if (type.startsWith('CACHE_')) return edge === 'service-redis';
  if (type === 'AUTH_REVOKED' || type === 'AUTH_RESET') return edge === 'policy-store';
  if (type === 'AUTH_ALLOWED' || type === 'AUTH_DENIED') return edge === 'service-policy' || edge === 'policy-store';
  if (type === 'PAYMENT_PENDING' || type === 'PAYMENT_SIGNATURE_DENIED') return edge === 'service-emulator';
  return edge === 'emulator-ledger';
}
function invalid(message = 'The evidence contains unsupported or malformed fields.'): never {throw new Error(message);}
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) invalid();
  return value as Record<string, unknown>;
}
function exact(value: unknown, keys: readonly string[]): Record<string, unknown> {
  const result = object(value);
  if (Object.keys(result).length !== keys.length || keys.some(key => !Object.hasOwn(result, key))) invalid();
  return result;
}
function text(value: unknown, max: number, multiline = false): string {
  if (typeof value !== 'string' || !value.length || value.length > max || (multiline ? /[\u0000-\u0008\u000b-\u001f\u007f]/u : /[\u0000-\u001f\u007f]/u).test(value)) invalid();
  return value;
}
function oneOf<T extends string>(value: unknown, choices: readonly T[]): T {
  if (typeof value !== 'string' || !choices.includes(value as T)) invalid();
  return value as T;
}
function integer(value: unknown, min: number, max: number): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < min || value > max) invalid();
  return value;
}
function hash(value: unknown): string {const result = text(value, 64); if (!hashes.test(result)) invalid(); return result;}
function uuid(value: unknown): string {const result = text(value, 36); if (!uuids.test(result)) invalid(); return result;}
function timestamp(value: unknown): string {
  const result = text(value, 40);
  const time = Date.parse(result);
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(result) || !Number.isFinite(time) || new Date(time).toISOString().slice(0, 19) !== result.slice(0, 19)) invalid();
  return result;
}
const bytes = (value: string) => new TextEncoder().encode(value).byteLength;

/** JSON.parse alone discards duplicate keys. Scan the already-valid JSON before accepting it. */
function rejectDuplicateKeys(source: string) {
  let cursor = 0;
  function space() {while (/\s/.test(source[cursor] ?? '') && cursor < source.length) cursor++;}
  function string(): string {
    const start = cursor++;
    while (cursor < source.length) {
      if (source[cursor] === '\\') {cursor += 2; continue;}
      if (source[cursor++] === '"') return JSON.parse(source.slice(start, cursor)) as string;
    }
    invalid();
  }
  function value(depth: number) {
    if (depth > 16) invalid();
    space();
    if (source[cursor] === '{') {
      cursor++; space(); const keys = new Set<string>();
      if (source[cursor] === '}') {cursor++; return;}
      while (cursor < source.length) {
        space(); const key = string();
        if (keys.has(key)) invalid('The evidence contains duplicate fields.');
        keys.add(key); space(); cursor++; value(depth + 1); space();
        if (source[cursor++] === '}') return;
      }
    } else if (source[cursor] === '[') {
      cursor++; space(); if (source[cursor] === ']') {cursor++; return;}
      while (cursor < source.length) {value(depth + 1); space(); if (source[cursor++] === ']') return;}
    } else if (source[cursor] === '"') {string(); return;}
    else {while (cursor < source.length && !/[\s,\]}]/.test(source[cursor])) cursor++; return;}
    invalid();
  }
  value(0);
}
function boundedJson(source: string, limit: number): unknown {
  if (typeof source !== 'string' || bytes(source) > limit) invalid('The evidence exceeds its size limit.');
  let result: unknown;
  try {result = JSON.parse(source);} catch {invalid('The evidence is not valid JSON.');}
  rejectDuplicateKeys(source);
  return result;
}
function boundedValue(value: unknown, limit: number): unknown {
  if (typeof value === 'string') return boundedJson(value, limit);
  let encoded: string | undefined;
  try {encoded = JSON.stringify(value);} catch {invalid();}
  if (!encoded) invalid();
  return boundedJson(encoded, limit);
}

export function parseMaterialIndex(value: unknown): {items: ScenarioMaterial[]} {
  const root = exact(boundedValue(value, MATERIAL_BYTE_LIMIT), ['items']);
  if (!Array.isArray(root.items) || root.items.length > 9) invalid();
  const ids = new Set<string>();
  const items = root.items.map(raw => {
    const item = exact(raw, ['id', 'language', 'pattern', 'title', 'summary', 'sourceHash', 'archiveHash', 'sourceUrl', 'recordingUrl', 'setup', 'scenarios', 'edges']);
    const language = oneOf(item.language, ['java', 'typescript', 'python'] as const);
    const pattern = oneOf(item.pattern, ['cache', 'security', 'payment'] as const);
    const id = text(item.id, 32);
    if (id !== `${language}-${pattern}` || ids.has(id)) invalid();
    ids.add(id);
    if (item.sourceUrl !== `/labs/sources/${language}.tar.gz` || (item.recordingUrl !== null && item.recordingUrl !== `/labs/recordings/${id}.json`)) invalid();
    if (!Array.isArray(item.scenarios) || !item.scenarios.length || item.scenarios.length > 12 || !Array.isArray(item.edges) || item.edges.length !== 3) invalid();
    const seenEdges = new Set<string>();
    const parsedEdges = item.edges.map(rawEdge => {
      const edge = exact(rawEdge, ['id', 'from', 'to', 'label']);
      const edgeId = oneOf(edge.id, edges[pattern]);
      if (seenEdges.has(edgeId)) invalid(); seenEdges.add(edgeId);
      return {id: edgeId, from: text(edge.from, 40), to: text(edge.to, 40), label: text(edge.label, 100)};
    });
    return {id, language, pattern, title: text(item.title, 120), summary: text(item.summary, 500), sourceHash: hash(item.sourceHash), archiveHash: hash(item.archiveHash), sourceUrl: item.sourceUrl as string, recordingUrl: item.recordingUrl as string | null, setup: text(item.setup, 2000, true), scenarios: item.scenarios.map(value => text(value, 100)), edges: parsedEdges};
  });
  return {items};
}
export function parseScenarioMaterials(source: string): ScenarioMaterial[] {return parseMaterialIndex(source).items;}

function parseState(value: unknown, pattern: ScenarioPattern): ScenarioState {
  if (pattern === 'cache') {
    const state = exact(value, ['cache', 'storeReads', 'ttlMs']);
    return {cache: oneOf(state.cache, ['MISS', 'HIT', 'FALLBACK', 'EXPIRED', 'RESET'] as const), storeReads: integer(state.storeReads, 0, 1000000), ttlMs: integer(state.ttlMs, -2, 5000)};
  }
  if (pattern === 'security') {
    const state = exact(value, ['decision', 'reason', 'currentRole']);
    const decision = oneOf(state.decision, ['ALLOW', 'DENY', 'RESET'] as const);
    const reason = oneOf(state.reason, ['ALLOW', 'ROLE', 'TENANT', 'EXPIRED', 'SIGNATURE', 'REVOKED', 'RESET'] as const);
    const currentRole = oneOf(state.currentRole, ['editor', 'viewer'] as const);
    if ((decision === 'ALLOW') !== (reason === 'ALLOW') || (decision === 'RESET') !== (reason === 'RESET') || (decision === 'ALLOW' && currentRole !== 'editor')) invalid();
    return {decision, reason, currentRole};
  }
  const state = exact(value, ['payment', 'entitlements', 'processedEvents']);
  const payment = oneOf(state.payment, ['PENDING', 'PAID', 'REFUNDED'] as const);
  const entitlements = integer(state.entitlements, 0, 1) as 0 | 1;
  if ((payment === 'PAID' ? 1 : 0) !== entitlements) invalid();
  return {payment, entitlements, processedEvents: integer(state.processedEvents, 0, 100)};
}

export function parseScenarioRecording(value: unknown, material: Pick<ScenarioMaterial, 'id' | 'language' | 'pattern' | 'sourceHash'>): ScenarioRecording {
  const root = exact(boundedValue(value, RECORDING_BYTE_LIMIT), ['mode', 'labId', 'language', 'pattern', 'runId', 'sourceHash', 'recordedAt', 'responses']);
  if (root.mode !== 'REPLAY') invalid('Only recorded replay evidence is accepted.');
  if (root.labId !== material.id || root.language !== material.language || root.pattern !== material.pattern) invalid('The recording belongs to another lab.');
  const sourceHash = hash(root.sourceHash);
  if (sourceHash !== material.sourceHash) invalid('This recording is stale: its source hash does not match the current source package.');
  const runId = uuid(root.runId), recordedAt = timestamp(root.recordedAt), pattern = material.pattern;
  if (!Array.isArray(root.responses) || !root.responses.length || root.responses.length > 128) invalid();
  let sequence = 0;
  let observedTime = -Infinity;
  const requestIds = new Set<string>();
  const responses = root.responses.map(raw => {
    if (bytes(JSON.stringify(raw)) > 4096) invalid('A recorded response exceeds its size limit.');
    const response = exact(raw, ['runId', 'sourceHash', 'requestId', 'pattern', 'status', 'durationMs', 'state', 'events']);
    const requestId = uuid(response.requestId);
    if (requestIds.has(requestId) || response.runId !== runId || response.sourceHash !== sourceHash || response.pattern !== pattern) invalid('A recorded response has a foreign or repeated identity.');
    requestIds.add(requestId);
    const status = integer(response.status, 100, 599);
    if (!(pattern === 'cache' ? [200] : pattern === 'security' ? [200, 401, 403] : [200, 202, 401, 409]).includes(status)) invalid();
    const state = parseState(response.state, pattern);
    if ('decision' in state && ((status === 401 && !['EXPIRED', 'SIGNATURE'].includes(state.reason)) || (status === 403 && !['ROLE', 'TENANT', 'REVOKED'].includes(state.reason)) || (status === 200 && state.decision === 'DENY' && state.reason !== 'REVOKED'))) invalid();
    if ('payment' in state && status === 202 && state.payment !== 'PENDING') invalid();
    if (!Array.isArray(response.events) || !response.events.length || response.events.length > 4) invalid();
    const events = response.events.map(rawEvent => {
      const event = exact(rawEvent, ['runId', 'sourceHash', 'requestId', 'sequence', 'type', 'edge', 'recordedAt', 'summary']);
      if (event.runId !== runId || event.sourceHash !== sourceHash || event.requestId !== requestId) invalid('A recorded event has a foreign identity.');
      const nextSequence = integer(event.sequence, 1, Number.MAX_SAFE_INTEGER);
      if (nextSequence <= sequence) invalid('Recorded event sequences must increase across the entire recording.');
      sequence = nextSequence;
      const eventTime = timestamp(event.recordedAt);
      if (Date.parse(eventTime) < observedTime) invalid('Recorded observation timestamps must not move backwards.');
      observedTime = Date.parse(eventTime);
      const type = oneOf(event.type, types[pattern]), edge = oneOf(event.edge, edges[pattern]);
      if (!observedEdge(type, edge)) invalid('The observed action does not support its recorded architecture edge.');
      if ((type === 'PAYMENT_SIGNATURE_DENIED' && status !== 401) || (type === 'PAYMENT_CONFLICT' && status !== 409) || (type === 'PAYMENT_DUPLICATE' && status !== 200) || (type === 'REFUND_PENDING' && status !== 202)) invalid();
      if ('decision' in state && ((type === 'AUTH_ALLOWED' && state.decision !== 'ALLOW') || (type === 'AUTH_DENIED' && state.decision !== 'DENY') || (type === 'AUTH_REVOKED' && state.reason !== 'REVOKED') || (type === 'AUTH_RESET' && state.decision !== 'RESET'))) invalid();
      if ('decision' in state && status !== (type === 'AUTH_DENIED' ? (['EXPIRED', 'SIGNATURE'].includes(state.reason) ? 401 : 403) : 200)) invalid();
      if ('cache' in state && ((type === 'CACHE_MISS' && !['MISS', 'FALLBACK'].includes(state.cache)) || (type === 'STORE_READ' && (!['MISS', 'FALLBACK'].includes(state.cache) || state.storeReads === 0)) || (['CACHE_HIT', 'CACHE_EXPIRED', 'CACHE_FALLBACK', 'CACHE_RESET'].includes(type) && type.slice(6) !== state.cache))) invalid();
      if ('payment' in state && ((type === 'PAYMENT_APPLIED' && (status !== 200 || state.payment === 'PENDING' || state.processedEvents === 0)) || (type === 'REFUND_APPLIED' && (status !== 200 || state.payment !== 'REFUNDED' || state.processedEvents === 0)) || (type === 'REFUND_PENDING' && (state.payment !== 'PENDING' || state.processedEvents === 0)) || (type === 'PAYMENT_RESET' && (status !== 200 || state.payment !== 'PENDING' || state.processedEvents !== 0)) || (type === 'PAYMENT_DUPLICATE' && state.processedEvents === 0) || (type === 'PAYMENT_PENDING' && status !== (state.payment === 'PENDING' ? 202 : 200)))) invalid();
      return {runId, sourceHash, requestId, sequence, type, edge, recordedAt: eventTime, summary: text(event.summary, 300)};
    });
    return {runId, sourceHash, requestId, pattern, status, durationMs: integer(response.durationMs, 0, 30000), state, events};
  });
  if (recordedAt !== responses[0].events[0].recordedAt) invalid('The recording timestamp must match its first observed event.');
  return {mode: 'REPLAY', labId: material.id, language: material.language, pattern, runId, sourceHash, recordedAt, responses};
}

/** Bound streamed bytes before parsing; a Content-Length header is not trusted. */
export async function readScenarioFile(response: Response, limit: number): Promise<string> {
  if (!response.ok) throw new Error(`The file is unavailable (HTTP ${response.status}). Retry to check again.`);
  if (!response.headers.get('Content-Type')?.toLowerCase().includes('application/json')) invalid('The file did not return JSON.');
  const reader = response.body?.getReader();
  if (!reader) invalid('The file could not be read.');
  const decoder = new TextDecoder('utf-8', {fatal: true});
  let count = 0, result = '';
  try {
    while (true) {
      const chunk = await reader.read(); if (chunk.done) break;
      count += chunk.value.byteLength;
      if (count > limit) invalid('The evidence exceeds its size limit.');
      result += decoder.decode(chunk.value, {stream: true});
    }
    return result + decoder.decode();
  } finally {await reader.cancel().catch(() => {}); reader.releaseLock();}
}
