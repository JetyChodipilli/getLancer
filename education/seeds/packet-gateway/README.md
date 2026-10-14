# Simulated binary packet gateway

Original simulated six-byte frames demonstrate checksum, fixed payload decoding and per-sensor replay/stale rejection. No physical transport or cryptographic integrity.

## Reproduce from a clean extraction

Use Node.js 22 or newer with no third-party installation. In this package directory:

```sh
node test.mjs
node gateway.mjs
```

gateway.mjs loads adjacent synthetic.json and reports bounded local decoding. Regression includes negative temperatures, buffer offsets, stale frames and invalid payloads.

## What is measured

Fixture accepts2 readings, counts1 duplicate and1 corrupt frame. Per-sensor monotonic sequence checks count old values as stale. A255-to1 wrap is rejected by design.

## Negative controls and bounds

Six-byte frames, version1, sequence1-255, two sensor IDs, temperature-40 to125C, <=1000 frames, fixture<=64KiB. XOR is not authentication; gateway state resets every invocation.

## Rights and provenance

Four hand-written original synthetic hexadecimal frames; XOR checksum detects accidental corruption only. Code is original under MIT LICENSE. Original synthetic data has its own DATA_LICENSE. Keep all permission and copyright notices. No real personal records or external content is included.

## Limitations

No physical transport, cryptographic authenticity, replay window across restarts, sequence wrapping, firmware or hardware verification. No authorship, academic marks or university certification is transferred. This source-only catalogue listing is illustrative; reproducing a local package does not prove a hosted runtime, real-world model accuracy or production readiness.
