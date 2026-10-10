/** Static source and original local evidence only. This module never starts execution. */
import catalogue from '../labs/data-ai/catalogue.json' with {type: 'json'};
import evidenceManifest from '../labs/data-ai/evidence-manifest.json' with {type: 'json'};
import type {LabInput} from './labs';

export const dataAiIds = ['revenue-summary', 'sensor-quality', 'sentiment-inference', 'equipment-inference'] as const;
export type DataAiId = typeof dataAiIds[number];
export type DataAiColumn = {key: string; label: string; unit: string; type: 'number' | 'string'};
export type DataAiTable = {id: string; title: string; columns: DataAiColumn[]; rows: Record<string, string | number>[]};
export type DataAiMetric = {key: string; label: string; value: number; unit: string};
export type DataAiResult = {tables: DataAiTable[]; metrics: DataAiMetric[]; prediction: {label: string; probability: number} | null; evaluation: {split: string; protocol: string; samples: number; correct: number; accuracy: number; limitations: string} | null};
export type DataAiMaterial = {id: DataAiId; kind: 'DATA_ANALYTICS' | 'AI_ML'; title: string; summary: string; outputSchema: 'getlancer-data-ai-result-v1'; inputs: LabInput[]; defaultInputs: Record<string, string>; changedInputs: Record<string, string>; dataLicense: string; dataProvenance: string; dataSha256: string; modelLicense: string | null; modelProvenance: string | null; modelSha256: string | null; modelFormat: 'JSON' | null; evaluationSplit: string | null; evaluationProtocol: string | null; evaluationSha256: string | null; limitations: string; recordingUrl: string};
export type DataAiIndex = {schemaVersion: 'getlancer-data-ai-index-v1'; sourceSha256: string; archiveSha256: string; sourceUrl: '/data-ai/source.tar.gz'; setup: string; items: DataAiMaterial[]};
export type DataAiRun = {schemaVersion: 'getlancer-data-ai-result-v1'; mode: 'LOCAL'; runId: string; scenarioId: DataAiId; sourceSha256: string; dataSha256: string; modelSha256: string | null; evaluationSha256: string | null; input: Record<string, string>; inputSha256: string; result: DataAiResult; resultSha256: string; startedAt: string; elapsedMs: number};
export type DataAiRecording = {schemaVersion: 'getlancer-data-ai-recording-v1'; mode: 'REPLAY'; scenarioId: DataAiId; sourceSha256: string; dataSha256: string; modelSha256: string | null; evaluationSha256: string | null; recordedAt: string; runs: DataAiRun[]};
type OutputContract = {tables: Omit<DataAiTable, 'rows'>[]; metrics: Omit<DataAiMetric, 'value'>[]; classes: string[] | null};
export const dataAiCatalogue = catalogue.items as unknown as DataAiMaterial[];
const outputContracts = catalogue.contracts as unknown as Record<DataAiId, OutputContract>;
export const DATA_AI_INDEX_LIMIT = 65536, DATA_AI_RECORDING_LIMIT = 16384, DATA_AI_RESULT_LIMIT = 4096;
const hashes = /^[0-9a-f]{64}$/, uuids = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
function invalid(message = 'Data/AI evidence contains unsupported or malformed fields.'): never {throw Error(message);}
function exact(value: unknown, keys: readonly string[]): Record<string, unknown> {if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length !== keys.length || keys.some(key => !Object.hasOwn(value, key))) invalid(); return value as Record<string, unknown>;}
function text(value: unknown, max = 4000, multiline = false): string {if (typeof value !== 'string' || !value.trim() || value.length > max || (multiline ? /[\u0000-\u0008\u000b-\u001f\u007f]/u : /[\u0000-\u001f\u007f]/u).test(value)) invalid(); return value;}
function hash(value: unknown): string {const result = text(value, 64); if (!hashes.test(result)) invalid('The evidence checksum is malformed.'); return result;}
function number(value: unknown, min = 0, max = 1e9): number {if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max || Number(value.toFixed(6)) !== value) invalid('Recorded numeric values are invalid or exceed their precision limit.'); return value;}
function integer(value: unknown, min = 0, max = 10000): number {const result = number(value, min, max); if (!Number.isSafeInteger(result)) invalid(); return result;}
function uuid(value: unknown): string {const result = text(value, 36); if (!uuids.test(result)) invalid('The original run identity is malformed.'); return result;}
function timestamp(value: unknown, now: number): string {const result = text(value, 24), instant = Date.parse(result); if (!Number.isFinite(instant) || new Date(instant).toISOString() !== result || instant > now) invalid('The evidence timestamp is malformed or in the future.'); return result;}
function close(left: number, right: number, tolerance = .000001) {if (Math.abs(left - right) > tolerance) invalid('Returned numeric values do not reconcile with their context.');}

/** Same compact, recursively sorted, bounded six-decimal representation as the curated Python source. */
function sortedDataAiJson(value: unknown, normalizeNumbers: boolean): string {
  function sorted(item: unknown, depth: number): unknown {
    if (depth > 16) invalid('The evidence exceeds its nesting limit.');
    if (typeof item === 'number') {if (!Number.isFinite(item)) invalid(); return normalizeNumbers ? Number(item.toFixed(6)) : item;}
    if (item === null || typeof item === 'string' || typeof item === 'boolean') return item;
    if (Array.isArray(item)) return item.map(child => sorted(child, depth + 1));
    if (item && typeof item === 'object') return Object.fromEntries(Object.keys(item).sort().map(key => [key, sorted((item as Record<string, unknown>)[key], depth + 1)]));
    return invalid();
  }
  return JSON.stringify(sorted(value, 0));
}
export function canonicalDataAiJson(value: unknown): string {return sortedDataAiJson(value, true);}
export async function dataAiHash(value: unknown): Promise<string> {const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonicalDataAiJson(value))); return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('');}
function equal(left: unknown, right: unknown, message = 'The evidence does not match the curated source contract.') {if (sortedDataAiJson(left, false) !== sortedDataAiJson(right, false)) invalid(message);}

/** JSON.parse erases duplicate member names. Scan the valid original bytes before accepting them. */
function rejectDuplicates(source: string) {
  let cursor = 0; const space = () => {while (cursor < source.length && /\s/.test(source[cursor])) cursor++;};
  function string(): string {const start = cursor++; while (cursor < source.length) {if (source[cursor] === '\\') {cursor += 2; continue;} if (source[cursor++] === '"') return JSON.parse(source.slice(start, cursor)) as string;} return invalid();}
  function value(depth: number) {
    if (depth > 16) invalid(); space();
    if (source[cursor] === '{') {cursor++; space(); const seen = new Set<string>(); if (source[cursor] === '}') {cursor++; return;} while (cursor < source.length) {space(); const key = string(); if (seen.has(key)) invalid('The evidence contains duplicate fields.'); seen.add(key); space(); cursor++; value(depth + 1); space(); if (source[cursor++] === '}') return;}}
    else if (source[cursor] === '[') {cursor++; space(); if (source[cursor] === ']') {cursor++; return;} while (cursor < source.length) {value(depth + 1); space(); if (source[cursor++] === ']') return;}}
    else if (source[cursor] === '"') {string(); return;}
    else {while (cursor < source.length && !/[\s,\]}]/.test(source[cursor])) cursor++; return;}
    invalid();
  }
  value(0);
}
function bounded(value: unknown, limit: number): unknown {
  let source: string;
  try {source = typeof value === 'string' ? value : JSON.stringify(value);} catch {return invalid();}
  if (typeof source !== 'string' || new TextEncoder().encode(source).byteLength > limit) invalid('The evidence exceeds its byte limit.');
  let parsed: unknown; try {parsed = JSON.parse(source);} catch {return invalid('The evidence is not valid JSON.');} rejectDuplicates(source); return parsed;
}
export async function readDataAiFile(response: Response, limit: number): Promise<string> {
  if (!response.ok) invalid(`Evidence is unavailable (HTTP ${response.status}). Retry to check again.`);
  if (!/^application\/json(?:\s*;|$)/i.test(response.headers.get('Content-Type') || '')) invalid('The evidence response did not return JSON.');
  const reader = response.body?.getReader(); if (!reader) invalid(); const decoder = new TextDecoder('utf-8', {fatal: true}); let bytes = 0, result = '';
  try {while (true) {const chunk = await reader.read(); if (chunk.done) break; bytes += chunk.value.byteLength; if (bytes > limit) invalid('The evidence exceeds its byte limit.'); result += decoder.decode(chunk.value, {stream: true});} return result + decoder.decode();} finally {await reader.cancel().catch(() => {}); reader.releaseLock();}
}

export function parseDataAiIndex(value: unknown): DataAiIndex {
  const root = exact(bounded(value, DATA_AI_INDEX_LIMIT), ['schemaVersion', 'sourceSha256', 'archiveSha256', 'sourceUrl', 'setup', 'items']);
  if (root.schemaVersion !== 'getlancer-data-ai-index-v1' || root.sourceUrl !== '/data-ai/source.tar.gz') invalid(); hash(root.sourceSha256); hash(root.archiveSha256); if (root.sourceSha256 !== evidenceManifest.sourceSha256 || root.archiveSha256 !== evidenceManifest.archiveSha256) invalid('The source index differs from this compiled source/archive version.'); text(root.setup, 4000, true);
  if (!Array.isArray(root.items) || root.items.length !== 4) invalid('The index must contain the four curated scenarios.'); const seen = new Set<DataAiId>();
  for (const item of root.items) {const raw = exact(item, ['id', 'kind', 'title', 'summary', 'outputSchema', 'inputs', 'defaultInputs', 'changedInputs', 'dataLicense', 'dataProvenance', 'dataSha256', 'modelLicense', 'modelProvenance', 'modelSha256', 'modelFormat', 'evaluationSplit', 'evaluationProtocol', 'evaluationSha256', 'limitations', 'recordingUrl']), id = raw.id as DataAiId;
    const original = dataAiCatalogue.find(entry => entry.id === id); if (!original || seen.has(id)) invalid('The index contains a foreign or repeated scenario.'); seen.add(id); equal(raw, original, 'The index is stale or does not match the pinned source catalogue.'); hash(raw.dataSha256); if (raw.kind === 'AI_ML') {hash(raw.modelSha256); hash(raw.evaluationSha256);}
  }
  return root as unknown as DataAiIndex;
}

function input(value: unknown, material: DataAiMaterial): Record<string, string> {
  const raw = exact(value, material.inputs.map(field => field.name));
  for (const field of material.inputs) {const item = text(raw[field.name], field.maxLength); if (field.type === 'ENUM' && !field.choices.includes(item)) invalid(); if (field.type === 'INTEGER' && (!/^-?(?:0|[1-9][0-9]*)$/.test(item) || String(Number(item)) !== item || !Number.isSafeInteger(Number(item)) || Number(item) < field.min || Number(item) > field.max)) invalid(); if (field.type === 'TEXT' && !/^[\x20-\x7e]*[A-Za-z][\x20-\x7e]*$/.test(item)) invalid();}
  return raw as Record<string, string>;
}
function result(value: unknown, material: DataAiMaterial, inputs: Record<string, string>): DataAiResult {
  const raw = exact(value, ['tables', 'metrics', 'prediction', 'evaluation']), contract = outputContracts[material.id];
  if (!Array.isArray(raw.tables) || raw.tables.length !== contract.tables.length || raw.tables.length > 2 || !Array.isArray(raw.metrics) || raw.metrics.length !== contract.metrics.length || raw.metrics.length > 20) invalid();
  for (const [index, table] of raw.tables.entries()) {const item = exact(table, ['id', 'title', 'columns', 'rows']), expected = contract.tables[index]; if (item.id !== expected.id || item.title !== expected.title) invalid(); equal(item.columns, expected.columns); if (!Array.isArray(item.rows) || !item.rows.length || item.rows.length > 32 || expected.columns.length > 8) invalid(); for (const row of item.rows) {const cells = exact(row, expected.columns.map(column => column.key)); for (const column of expected.columns) {if (column.type === 'number') number(cells[column.key]); else text(cells[column.key], 160);}}}
  for (const [index, metric] of raw.metrics.entries()) {const item = exact(metric, ['key', 'label', 'value', 'unit']), expected = contract.metrics[index]; if (item.key !== expected.key || item.label !== expected.label || item.unit !== expected.unit) invalid(); number(item.value);}
  const output = raw as unknown as DataAiResult, rows = output.tables[0].rows, metrics = Object.fromEntries(output.metrics.map(item => [item.key, item.value]));
  if (material.kind === 'DATA_ANALYTICS') {
    if (raw.prediction !== null || raw.evaluation !== null) invalid();
    if (material.id === 'revenue-summary') {if (rows.length !== 3) invalid(); equal(rows.map(row => row.month), ['Jan', 'Feb', 'Mar']); const orders = rows.reduce((sum, row) => sum + integer(row.orders, 1, 2), 0), revenue = rows.reduce((sum, row) => sum + integer(row.revenue, 0, 1000000), 0); if (orders !== (inputs.region === 'all' ? 6 : 3)) invalid(); close(metrics.orders, orders); close(metrics.revenue, revenue); if (Math.abs(metrics.averageOrder - revenue / orders) > .500001 || !Number.isSafeInteger(metrics.averageOrder)) invalid('Average revenue must use the declared integer INR rounding.');}
    else {equal(rows.map(row => row.channel), inputs.channel === 'all' ? ['temperature', 'vibration'] : [inputs.channel]); for (const row of rows) {integer(row.accepted, 0, 4); integer(row.rejected, 0, 4); if (integer(row.total, 0, 4) !== 4 || Number(row.accepted) + Number(row.rejected) !== Number(row.total)) invalid();} const accepted = rows.reduce((sum, row) => sum + Number(row.accepted), 0), rejected = rows.reduce((sum, row) => sum + Number(row.rejected), 0); close(metrics.accepted, accepted); close(metrics.rejected, rejected); close(metrics.acceptance, accepted / (accepted + rejected) * 100);}
  } else {
    const classes = contract.classes!; if (rows.length !== 2) invalid(); equal(rows.map(row => row.class), classes); const probabilities = rows.map(row => number(row.probability, 0, 1)); close(probabilities[0] + probabilities[1], 1, .000002);
    const prediction = exact(raw.prediction, ['label', 'probability']), winner = probabilities[1] > probabilities[0] ? 1 : 0; if (prediction.label !== classes[winner]) invalid('The predicted class disagrees with its returned probabilities.'); close(number(prediction.probability, 0, 1), probabilities[winner]); close(metrics.confidence, probabilities[winner]);
    const evaluation = exact(raw.evaluation, ['split', 'protocol', 'samples', 'correct', 'accuracy', 'limitations']); if (evaluation.split !== material.evaluationSplit || evaluation.protocol !== material.evaluationProtocol || evaluation.limitations !== material.limitations || integer(evaluation.samples, 1, 8) !== 8) invalid(); const correct = integer(evaluation.correct, 0, 8); close(number(evaluation.accuracy, 0, 1), correct / 8); close(metrics.evaluationAccuracy, correct / 8);
  }
  return output;
}

export async function parseDataAiRecording(value: unknown, index: DataAiIndex, material: DataAiMaterial, now = Date.now()): Promise<DataAiRecording> {
  if (index.sourceSha256 !== evidenceManifest.sourceSha256 || index.archiveSha256 !== evidenceManifest.archiveSha256) invalid('The source index differs from this compiled source/archive version.');
  const pinned = dataAiCatalogue.find(item => item.id === material.id); if (!pinned) invalid(); equal(material, pinned);
  const root = exact(bounded(value, DATA_AI_RECORDING_LIMIT), ['schemaVersion', 'mode', 'scenarioId', 'sourceSha256', 'dataSha256', 'modelSha256', 'evaluationSha256', 'recordedAt', 'runs']);
  if (root.schemaVersion !== 'getlancer-data-ai-recording-v1' || root.mode !== 'REPLAY' || root.scenarioId !== material.id) invalid('The evidence has a foreign scenario or execution mode.');
  if (root.sourceSha256 !== index.sourceSha256) invalid('The recording uses a stale source version.'); for (const key of ['dataSha256', 'modelSha256', 'evaluationSha256'] as const) if (root[key] !== material[key]) invalid('The recorded data/model/evaluation identity is stale.');
  const recorded = Date.parse(timestamp(root.recordedAt, now)); if (!Array.isArray(root.runs) || root.runs.length !== 2) invalid('Recordings must contain both original default and changed runs.');
  let runId: string | undefined, previousTime = 0; const inputHashes = new Set<string>(), resultHashes = new Set<string>();
  for (const [position, run] of root.runs.entries()) {
    const envelope = exact(bounded(run, DATA_AI_RESULT_LIMIT), ['schemaVersion', 'mode', 'runId', 'scenarioId', 'sourceSha256', 'dataSha256', 'modelSha256', 'evaluationSha256', 'input', 'inputSha256', 'result', 'resultSha256', 'startedAt', 'elapsedMs']);
    if (envelope.schemaVersion !== material.outputSchema || envelope.mode !== 'LOCAL' || envelope.scenarioId !== material.id) invalid('The original result is not a matching LOCAL execution.');
    const identity = uuid(envelope.runId); if (runId && identity !== runId) invalid('The recording contains a foreign run identity.'); runId = identity;
    if (envelope.sourceSha256 !== index.sourceSha256) invalid('The original source version is stale.'); for (const key of ['dataSha256', 'modelSha256', 'evaluationSha256'] as const) if (envelope[key] !== material[key]) invalid('The original data/model/evaluation identity is stale.');
    const started = Date.parse(timestamp(envelope.startedAt, now)), elapsed = number(envelope.elapsedMs, 0, 10000); if (started < previousTime || started + elapsed > recorded + 2) invalid('The original execution timing is inconsistent.'); previousTime = started + elapsed;
    const values = input(envelope.input, material); equal(values, position ? material.changedInputs : material.defaultInputs, 'The recorded input is foreign to its curated default/changed case.');
    const inputHash = hash(envelope.inputSha256), resultHash = hash(envelope.resultSha256); if (inputHashes.has(inputHash) || resultHashes.has(resultHash)) invalid('Default and changed recordings repeat the same input or result identity.'); inputHashes.add(inputHash); resultHashes.add(resultHash);
    if (inputHash !== await dataAiHash(values)) invalid('The original input checksum is inconsistent.'); const output = result(envelope.result, material, values); if (resultHash !== await dataAiHash(output)) invalid('The recorded result checksum is forged or corrupt.');
  }
  return root as unknown as DataAiRecording;
}
