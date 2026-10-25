# Greenhouse telemetry learning material

Original MIT source: ESP32-style C++ and MicroPython firmware, a bill of
materials, signal-level wiring, bounded protocol specification, and a working
Node standard-library MQTT broker/device/HTTP reproduction. All source is free.

## Account-free local reproduction

Install stable Node 22.23.4+ in Node 22 separately. CI and the local lab Docker image use Node 22.23.4. Extract `source.tar.gz`; in the extracted root:

```sh
LAB_RUN_ID=00000000-0000-4000-8000-000000000001 node runtime.mjs
```

In a second terminal, publish default and changed readings, then inspect the
actual device and protocol logs:

```sh
node client.mjs 00000000-0000-4000-8000-000000000001 publish 24 45 30
node client.mjs 00000000-0000-4000-8000-000000000001 publish 40 70 30
node client.mjs 00000000-0000-4000-8000-000000000001 disconnect
node client.mjs 00000000-0000-4000-8000-000000000001 connect
node client.mjs 00000000-0000-4000-8000-000000000001 reset
node client.mjs 00000000-0000-4000-8000-000000000001 state
```

The ready line contains the source SHA-256 and ports. The changed sample sets
the computed actuator flag; no physical output is driven. Publication while
disconnected fails. Stop using Ctrl+C; automatic expiry also cleans up.
`LAB_MQTT_PORT` and `LAB_HTTP_PORT` choose numeric local ports, including zero
for an ephemeral runtime port. `LAB_LIFETIME_MS` and `LAB_IDLE_MS` stay within
the reviewed bounds in PROTOCOL.md. The client needs the actual ready HTTP
port when the runtime uses zero. No account or dependencies beyond Node are
needed for this local simulation. Browser local is a separate pure simulation.

## Optional firmware sources

C++ lives in `firmware/cpp/telemetry.ino` and `platformio.ini`. Use the pinned
PlatformIO Core 6.1.18, Espressif32 platform 6.10.0, DHT library 1.4.6,
Adafruit Unified Sensor 1.1.15 and PubSubClient 2.8. Run `pio run -d firmware/cpp`
after installing these tools separately. No ArduinoJson dependency is used.
This archive does not contain those upstream downloads or their licenses.

MicroPython `firmware/micropython/main.py` targets the official ESP32_GENERIC
MicroPython 1.25.0 image and uses built-in machine, dht, network, socket, json
and time modules. `requirements.txt` records the exact image/runtime and the
absence of third-party Python package requirements. Upload the file with your
chosen board tool; adjust the placeholder WiFi and isolated LAN broker settings
locally. Never commit real credentials. The supplied Node broker remains
loopback-only; firmware cannot contact it from a physical ESP32.

Both original examples publish measurements only, use the exact run topic and
numeric bounds, cap samples/retries, and never accept remote commands. They
require optional hardware and a separate broker to run physically. Read BOM.md
and WIRING.md before any manual low-voltage exercise.

## Evidence and exclusions

As of 2026-10-13, no dated physical photo, video, calibration report, flash log,
hardware compile proof or on-device execution was supplied. Physical proof is
explicitly absent (`null`); local TCP execution does not establish hardware
operation. Firmware is original reference source, not a claim of a tested board.
No cloud/paid embed executes, and hosted activation stays independently gated.

Primary upstream references for the separately installed pinned dependencies:
https://github.com/platformio/platform-espressif32/releases/tag/v6.10.0
https://github.com/adafruit/DHT-sensor-library/releases/tag/1.4.6
https://github.com/adafruit/Adafruit_Sensor/releases/tag/1.1.15
https://github.com/knolleary/pubsubclient/releases/tag/v2.8
https://micropython.org/download/ESP32_GENERIC/
https://docs.micropython.org/en/v1.25.0/esp32/tutorial/intro.html

The deterministic package has a fixed source inventory. Material index pins
both runtime bytes and full archive hash; builder verification executes current
and clean extracted sources before publishing. Source tampering during checking
rejects publication. No credentials, private user data, generated dependency
tree, paid artifact or executable binary is included.
