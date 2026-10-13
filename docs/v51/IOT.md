# V5.1 IoT implementation and evidence

S07-B01/B02 include original MIT firmware in C++ and MicroPython, complete
optional BOM and signal-level wiring, exact per-run MQTT topic and numeric
input contract, free deterministic source extraction, and two honest modes.

The browser reducer (`lib/iot.ts`) is **Browser local** synthetic state. The
account-free source runtime (`labs/iot/runtime.mjs`) is **Local MQTT**: a real
loopback TCP broker forwards QoS0 packets to a real subscribed device socket;
the HTTP bridge waits for device state before replying. It is original teaching
source and accepts no arbitrary destination, paid embed or physical control.
The browser never treats local state as signed hosted certification evidence.

Run `node scripts/verify-iot.mjs` to exercise default and changed measurements,
disconnect/reconnect/reset, independent expected device state, and shutdown
for current source and clean extracted source. Run
`node --test tests/iot.test.mjs tests/iot-runtime.test.mjs` for actual-wire
negative cases, HTTP framing/origin/UUID/input limits, history/action/attempt
budgets, parser alias rejection, immutable browser state, expiry and source
drift. The material builder executes verification before any artifact write;
`node scripts/build-iot-material.mjs --verify` checks public archive/index/docs
against current immutable checked bytes. Root build generates the public files.

See source README.md, BOM.md, WIRING.md and PROTOCOL.md for full commands,
pinned optional firmware dependencies and power/setup exclusions. No on-device
execution, hardware build or dated physical evidence was supplied as of
2026-10-13. Source metadata explicitly has `physicalProof: null` and firmware
`physicalExecutionVerified: false`; local protocol success is not a physical
device claim. Upstream packages are installed separately and not redistributed.

The independent hosted IOT_SIMULATION policy remains disabled until the
server's existing signed evidence, current source/image identity, operator
authority, budget and cleanup gates pass. This local runtime does not itself
certify an execution image or activate a hosted provider.

Local leaf verification on 2026-10-13: 21 focused tests passed, with no skips;
the standalone protocol verifier executed five state changes against current
source and five more against its clean extracted archive, with complete socket,
log and state cleanup. The standalone strict TypeScript check for lib/iot.ts
passed. Four passes covered source/contract completeness, adversarial protocol
and resource review, independent runtime/state/source reproduction, and final
documentation/dependency/verification reconciliation. Full application browser,
backend, security and current-head CI acceptance remain root integration gates.
