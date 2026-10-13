/** Original MIT account-free local HTTP client. No arbitrary network destinations. */
import {UUID, validateReading} from './runtime.mjs';
const [runId, operationId = 'publish', temperatureC = '24', humidityPercent = '45', thresholdC = '30'] = process.argv.slice(2);
try {
  if (!UUID.test(runId ?? '') || !['publish', 'state', 'disconnect', 'connect', 'reset'].includes(operationId) || process.argv.length > 7) throw Error('Expected a run UUID and a supported operation.');
  if (operationId !== 'publish' && process.argv.length > 4) throw Error('Sensor arguments apply only to publish.');
  const portText = process.env.LAB_HTTP_PORT ?? '8106'; if (!/^\d{1,5}$/.test(portText) || Number(portText) < 1 || Number(portText) > 65535) throw Error('Invalid local port.');
  const fields = {runId, operationId};
  if (operationId === 'publish') {
    const values = {temperatureC, humidityPercent, thresholdC}; for (const value of Object.values(values)) if (!/^-?(?:0|[1-9]\d*)(?:\.\d{1,6})?$/.test(value) || value.length > 12) throw Error('Invalid sensor number.'); validateReading(Object.fromEntries(Object.entries(values).map(([key, value]) => [key, Number(value)]))); Object.assign(fields, values);
  }
  const response = await fetch(`http://127.0.0.1:${portText}/request`, {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: new URLSearchParams(fields), signal: AbortSignal.timeout(2000)});
  const bytes = await response.text(); if (bytes.length > 20000) throw Error('Response exceeded the local budget.'); if (!response.ok) throw Error(JSON.parse(bytes).error); console.log(bytes);
} catch (error) {console.error('IOT_CLIENT_REJECTED ' + error.message); process.exitCode = 1;}
