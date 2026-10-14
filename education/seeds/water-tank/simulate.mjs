import { fileURLToPath } from 'node:url';
/** Hysteresis and fail-closed behavior for an entirely simulated water tank. */
export function simulate(readings = [10, 35, 80, 70, null, 15], { low = 20, high = 80 } = {}) {
  if (!Number.isFinite(low) || !Number.isFinite(high) || low < 0 || high > 100 || low >= high) throw Error('Invalid hysteresis thresholds');
  if (!Array.isArray(readings) || readings.length > 1000) throw Error('At most 1000 simulated readings are permitted');
  let pump = false;
  return readings.map((level, tick) => {
    if (level !== null && (typeof level !== 'number' || !Number.isFinite(level) || level < 0 || level > 100)) throw Error('Invalid simulated level');
    if (level === null) pump = false;
    else if (level <= low) pump = true;
    else if (level >= high) pump = false;
    return { tick, levelPercent: level, pump, alarm: level === null, simulated: true };
  });
}
if (process.argv[1] === fileURLToPath(import.meta.url)) console.log(JSON.stringify({ topic: 'simulated/water-tank', readings: simulate(), hardwareVerified: false }));
