# Reviewed bounded telemetry contract

One lowercase RFC-variant UUID identifies one disposable run. The sole topic is
`getlancer/<UUID>/telemetry`; filters, wildcards, alternate paths, other runs,
URLs and arbitrary broker destinations are rejected.

Payload is one UTF-8 JSON object containing exactly three numeric fields:
`temperatureC` (-20 to 80 °C), `humidityPercent` (0 to 100 %), and `thresholdC`
(0 to 60 °C), each finite with at most six decimal places. Duplicate fields,
escaped duplicate aliases, arrays, extra fields, non-finite values and invalid
UTF-8 are rejected. The device sets the simulated flag when
`temperatureC >= thresholdC`, including exact equality.

The actual local broker implements a strict MQTT 3.1.1 subset: clean-session
CONNECT, QoS0 non-retained non-duplicate PUBLISH, the internal device's exact
single-topic QoS0 SUBSCRIBE/SUBACK, PINGREQ/PINGRESP, and DISCONNECT. CONNACK
uses return code zero only after a valid identity. Client IDs are
`getlancer-publisher-<UUID>` and `getlancer-device-<UUID>`. The internal device
is unique; external publishers cannot take over its live connection. Maximum
keep-alive is 60 seconds. QoS1/2, retained data, credentials, wills, extensions
and unsupported packet flags are rejected by closing the socket. This small
local subset is not a general-purpose production broker or hosted isolation.

The HTTP bridge is loopback-only. `GET /health` returns source identity and
`physicalProof: null`. `POST /request` requires Host `127.0.0.1:<port>`, no
Origin, exactly `application/x-www-form-urlencoded` and Content-Length.
Fields: `runId`, `operationId`; publish additionally needs the three readings.
Operations are `publish`, `state`, `disconnect`, `connect`, `reset`. Publish
uses real TCP MQTT packets through the broker and waits for the subscribed
device to apply the payload. Source-hash-pinned responses use mode `LOCAL_MQTT`.
The browser route uses a separate pure state reducer labelled Browser local.

Disconnect closes the actual subscriber connection and rejects publication.
Reconnect retains sensor state. Reset reconnects and restores 24 °C / 45 % /
30 °C threshold, clears messages, retains the same run/topic and advances the
sequence. State-changing actions have monotonically increasing sequence;
reset never reuses earlier sequence numbers during the live run.

Limits: 1,024-byte MQTT body, 4,096-byte buffered wire fragments, 512-byte JSON,
1,024-byte HTTP body, 4,096-byte HTTP headers, 4 concurrent broker sockets and
4 HTTP sockets, 100 state changes, 200 protocol/HTTP attempts, and the last 20
events/messages/logs. Partial packets and HTTP bodies have an absolute 1-second
deadline. HTTP headers have a separate absolute 1-second deadline; each HTTP
socket accepts one request. Initial MQTT handshakes have a 2-second deadline. Default lifetime
60 seconds, idle lifetime 30 seconds; configurable total 500–120,000 ms and
idle 500–60,000 ms. Valid traffic cannot extend total lifetime. Expiry/shutdown
destroys sockets, clears logs/state, closes ports, and writes no runtime files.

Ports are ephemeral in automated verification; CLI defaults are MQTT 18830
and HTTP 8106. Runtime requires stable Node 22.23.4+ in Node 22, standard library only. It makes no
outbound internet requests and accepts no arbitrary commands or uploaded code.
