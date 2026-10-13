/** Synthetic browser-local state only; never connects to a broker or physical device. */
export const IOT_DEFAULTS = {temperatureC: 24, humidityPercent: 45, thresholdC: 30} as const;
export const IOT_LIMITS = {temperatureMin: -20, temperatureMax: 80, humidityMin: 0, humidityMax: 100, thresholdMin: 0, thresholdMax: 60, history: 20, actions: 100} as const;
export type IotAction = {type: 'publish'; temperatureC: number; humidityPercent: number; thresholdC: number; topic?: string} | {type: 'disconnect' | 'connect' | 'reset'};
export type IotEvent = {sequence: number; type: IotAction['type']; at: number; detail: string};
export type IotMessage = {sequence: number; topic: string; temperatureC: number; humidityPercent: number; thresholdC: number; actuatorOn: boolean; at: number};
export type IotSession = {runId: string; connected: boolean; temperatureC: number; humidityPercent: number; thresholdC: number; actuatorOn: boolean; sequence: number; topic: string; messages: IotMessage[]; events: IotEvent[]};
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
function reading(value: number, min: number, max: number) {if (!Number.isFinite(value) || value < min || value > max || Number(value.toFixed(6)) !== value) throw Error('Sensor values must be finite, within range, and at most six decimal places.');}
function time(now: number) {if (!Number.isSafeInteger(now) || now < 0 || now > 8640000000000000) throw Error('The local timestamp is invalid.');}
function exact(value: object, keys: string[]) {if (Object.keys(value).sort().join(',') !== keys.sort().join(',')) throw Error('The local state contains unsupported fields.');}
export function createIotSession(runId = crypto.randomUUID(), now = Date.now()): IotSession {
  if (!uuid.test(runId)) throw Error('A valid UUID run identity is required.'); time(now);
  return {runId, connected: true, ...IOT_DEFAULTS, actuatorOn: false, sequence: 0, topic: `getlancer/${runId}/telemetry`, messages: [], events: []};
}
export function iotAction(state: IotSession, action: IotAction, now = Date.now()): IotSession {
  time(now);
  if (!state || !uuid.test(state.runId) || state.topic !== `getlancer/${state.runId}/telemetry` || typeof state.connected !== 'boolean' || !Number.isSafeInteger(state.sequence) || state.sequence < 0 || state.sequence >= IOT_LIMITS.actions || !Array.isArray(state.messages) || !Array.isArray(state.events) || state.messages.length > IOT_LIMITS.history || state.events.length > IOT_LIMITS.history) throw Error('The local session is invalid or has reached its 100-action limit. Start a new session.');
  exact(state, ['runId', 'connected', 'temperatureC', 'humidityPercent', 'thresholdC', 'actuatorOn', 'sequence', 'topic', 'messages', 'events']);
  if (typeof state.actuatorOn !== 'boolean') throw Error('The local actuator flag is invalid.');
  for (const [history, keys] of [[state.messages, ['sequence', 'topic', 'temperatureC', 'humidityPercent', 'thresholdC', 'actuatorOn', 'at']], [state.events, ['sequence', 'type', 'at', 'detail']]] as const) {
    let sequence = 0, instant = 0;
    for (const item of history) {
      if (!item || typeof item !== 'object') throw Error('The local history is invalid.'); exact(item, [...keys]); time(item.at);
      if (!Number.isSafeInteger(item.sequence) || item.sequence <= sequence || item.sequence > state.sequence || item.at < instant || item.at > now) throw Error('The local history is out of sequence.'); sequence = item.sequence; instant = item.at;
    }
  }
  for (const item of state.messages) {reading(item.temperatureC, -20, 80); reading(item.humidityPercent, 0, 100); reading(item.thresholdC, 0, 60); if (item.topic !== state.topic || item.actuatorOn !== (item.temperatureC >= item.thresholdC)) throw Error('The local message is inconsistent.');}
  for (const item of state.events) if (!['publish', 'disconnect', 'connect', 'reset'].includes(item.type) || typeof item.detail !== 'string' || item.detail.length > 120 || /[\u0000-\u001f\u007f]/u.test(item.detail)) throw Error('The local event is invalid.');
  if ((state.sequence === 0 && state.events.length !== 0) || (state.sequence > 0 && state.events[state.events.length - 1]?.sequence !== state.sequence)) throw Error('The local event sequence is inconsistent.');
  if (state.events.length && now < state.events[state.events.length - 1].at) throw Error('Local event timestamps must remain monotonic.');
  reading(state.temperatureC, -20, 80); reading(state.humidityPercent, 0, 100); reading(state.thresholdC, 0, 60);
  if (!action || !['publish', 'disconnect', 'connect', 'reset'].includes(action.type)) throw Error('Unsupported local simulator action.');
  const allowed = action.type === 'publish' ? ['type', 'temperatureC', 'humidityPercent', 'thresholdC', 'topic'] : ['type'];
  if (Object.keys(action).some(key => !allowed.includes(key))) throw Error('The local action contains unsupported fields.');
  const next: IotSession = {...state, sequence: state.sequence + 1, messages: [...state.messages], events: [...state.events]};
  let detail: string;
  if (action.type === 'publish') {
    if (!state.connected) throw Error('Connect the simulated device before publishing.');
    if (action.topic !== undefined && action.topic !== state.topic) throw Error('Publish is restricted to this run’s exact telemetry topic.');
    reading(action.temperatureC, -20, 80); reading(action.humidityPercent, 0, 100); reading(action.thresholdC, 0, 60);
    next.temperatureC = action.temperatureC; next.humidityPercent = action.humidityPercent; next.thresholdC = action.thresholdC; next.actuatorOn = action.temperatureC >= action.thresholdC;
    next.messages.push({sequence: next.sequence, topic: state.topic, temperatureC: next.temperatureC, humidityPercent: next.humidityPercent, thresholdC: next.thresholdC, actuatorOn: next.actuatorOn, at: now});
    next.messages = next.messages.slice(-IOT_LIMITS.history); detail = `Telemetry accepted; simulated actuator ${next.actuatorOn ? 'on' : 'off'}.`;
  } else if (action.type === 'reset') {
    Object.assign(next, IOT_DEFAULTS, {connected: true, actuatorOn: false, messages: []}); detail = 'Synthetic readings reset; the same isolated run is connected.';
  } else {
    next.connected = action.type === 'connect'; detail = next.connected ? 'Simulated device connected.' : 'Simulated device disconnected.';
  }
  next.events.push({sequence: next.sequence, type: action.type, at: now, detail}); next.events = next.events.slice(-IOT_LIMITS.history); return next;
}
