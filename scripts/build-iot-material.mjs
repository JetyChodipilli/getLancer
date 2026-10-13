/** Fixed original MIT inventory; verification runs before any public write. */
import assert from 'node:assert/strict';
import {readFile, writeFile, mkdir, lstat} from 'node:fs/promises';
import {resolve, dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {readSourceFile, archiveFor, unpackArchive, sha} from './build-data-ai-material.mjs';
export {readSourceFile, archiveFor, unpackArchive, sha};
export const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export const base = join(root, 'labs/iot');
export const publicBase = join(root, 'public/iot');
export const inventory = ['BOM.md', 'License.md', 'PROTOCOL.md', 'README.md', 'WIRING.md', 'client.mjs', 'firmware/cpp/platformio.ini', 'firmware/cpp/telemetry.ino', 'firmware/micropython/main.py', 'firmware/micropython/requirements.txt', 'runtime.mjs'].sort();
export const setup = 'Node 22.18+ standard library only. Extract free source.tar.gz; from its root run LAB_RUN_ID=00000000-0000-4000-8000-000000000001 node runtime.mjs. In a second terminal run node client.mjs 00000000-0000-4000-8000-000000000001 publish 40 70 30. Browser local and local MQTT are synthetic, with no physical proof or hardware control.';
export async function sourceFiles() {
  for (const path of [base, join(base, 'firmware'), join(base, 'firmware/cpp'), join(base, 'firmware/micropython')]) {const stat = await lstat(path); assert(stat.isDirectory() && !stat.isSymbolicLink(), 'Unsafe IoT source directory');}
  const files = []; for (const name of inventory) files.push([name, await readSourceFile(join(base, name))]);
  assert(files.find(([name]) => name === 'License.md')[1].toString().includes('MIT License'), 'Original source license missing');
  return {files};
}
export function materialIndex(files, archive) {
  return {schemaVersion: 'getlancer-iot-index-v1', sourceSha256: sha(files.find(([name]) => name === 'runtime.mjs')[1]), archiveSha256: sha(archive), sourceUrl: '/iot/source.tar.gz', sourceLicense: 'MIT', mode: 'LOCAL_MQTT', physicalProof: null, physicalReviewDate: '2026-10-13', setup, inventory,
    firmware: [{language: 'C++', path: 'firmware/cpp/telemetry.ino', dependencies: 'PlatformIO Core 6.1.18; Espressif32 6.10.0; DHT 1.4.6; Unified Sensor 1.1.15; PubSubClient 2.8', physicalExecutionVerified: false}, {language: 'MicroPython', path: 'firmware/micropython/main.py', dependencies: 'ESP32_GENERIC MicroPython 1.25.0; built-in modules only', physicalExecutionVerified: false}],
    bounds: {temperatureC: [-20, 80], humidityPercent: [0, 100], thresholdC: [0, 60], actions: 100, history: 20}, exclusions: ['hardware', 'WiFi access', 'separate LAN broker', 'dependency downloads', 'provider charges', 'physical performance certification']};
}
export async function buildMaterial(verify = false, outputDirectory = publicBase) {
  const {verifyIot} = await import('./verify-iot.mjs');
  const checked = await verifyIot(), {files} = await sourceFiles(), archive = archiveFor(files); unpackArchive(archive, files);
  const index = materialIndex(files, archive); assert.equal(index.sourceSha256, checked.sourceSha256, 'IoT source changed during verification'); assert.equal(index.archiveSha256, checked.archiveSha256, 'IoT source archive changed during verification');
  const outputs = [['source.tar.gz', archive], ['material-index.json', Buffer.from(JSON.stringify(index, null, 2) + '\n')], ...files.filter(([name]) => ['License.md', 'BOM.md', 'WIRING.md', 'PROTOCOL.md', 'README.md'].includes(name))];
  for (const [name, bytes] of outputs) {const path = join(outputDirectory, name); if (verify) assert((await readFile(path)).equals(bytes), 'IoT material drift: ' + name); else {await mkdir(dirname(path), {recursive: true}); await writeFile(path, bytes);}}
  return {sourceSha256: index.sourceSha256, archiveSha256: index.archiveSha256, archiveBytes: archive.length, files: files.length, ...checked.measurements};
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  // Finish this module's evaluation before the verifier imports its fixed-inventory exports.
  try {assert(process.argv.slice(2).every(value => value === '--verify'), 'Unknown IoT builder argument'); buildMaterial(process.argv.includes('--verify')).then(result => console.log('IOT_MATERIAL_OK ' + JSON.stringify(result))).catch(error => {console.error('IOT_MATERIAL_REJECTED ' + error.message); process.exitCode = 1;});} catch (error) {console.error('IOT_MATERIAL_REJECTED ' + error.message); process.exitCode = 1;}
}
