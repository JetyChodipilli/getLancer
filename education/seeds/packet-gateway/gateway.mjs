import { readFileSync } from 'node:fs'; import { fileURLToPath } from 'node:url';
/** Toy six-byte local protocol. XOR detects accidental corruption, not tampering. */
export function encode({ sequence, sensor, temperatureTenths }) {
  if (!Number.isInteger(sequence) || sequence < 1 || sequence > 255 || ![0, 1].includes(sensor) || !Number.isInteger(temperatureTenths) || temperatureTenths < -400 || temperatureTenths > 1250) throw Error('Invalid simulated payload');
  const bytes = new Uint8Array(6); bytes[0] = 1; bytes[1] = sequence; bytes[2] = sensor;
  new DataView(bytes.buffer).setInt16(3, temperatureTenths, false);
  bytes[5] = bytes.slice(0, 5).reduce((checksum, byte) => checksum ^ byte, 0); return bytes;
}
export function decode(bytes) {
  if (!(bytes instanceof Uint8Array) || bytes.length !== 6 || bytes[0] !== 1 || bytes[5] !== bytes.slice(0, 5).reduce((checksum, byte) => checksum ^ byte, 0)) throw Error('Invalid frame or checksum');
  const temperatureTenths = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getInt16(3, false);
  const payload = { sequence: bytes[1], sensor: bytes[2], temperatureTenths }; encode(payload);
  return { ...payload, temperatureC: temperatureTenths / 10, simulated: true };
}
export function gateway(frames) {
  if (!Array.isArray(frames) || frames.length > 1000) throw Error('At most 1000 simulated frames are permitted');
  const last = new Map(), readings = []; let invalid = 0, duplicates = 0, stale = 0;
  for (const frame of frames) {
    let reading;
    try { reading = decode(frame); } catch { invalid++; continue; }
    const previous = last.get(reading.sensor) || 0;
    if (reading.sequence === previous) { duplicates++; continue; }
    if (reading.sequence < previous) { stale++; continue; }
    last.set(reading.sensor, reading.sequence); readings.push(reading);
  }
  return { accepted: readings.length, invalid, duplicates, stale, readings, simulated: true, hardwareVerified: false };
}
export function fixtureFrames(path) {
  const data = readFileSync(path); if (data.length > 65536) throw Error('Fixture exceeds 64 KiB');
  const values = JSON.parse(data); if (!Array.isArray(values) || values.length > 1000 || values.some(value => typeof value !== 'string' || !/^[0-9a-f]{12}$/.test(value))) throw Error('Invalid fixture');
  return values.map(value => Uint8Array.from(Buffer.from(value, 'hex')));
}
if (process.argv[1] === fileURLToPath(import.meta.url)) console.log(JSON.stringify(gateway(fixtureFrames(new URL('synthetic.json', import.meta.url)))));
