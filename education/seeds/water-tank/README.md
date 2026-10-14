# Simulated water-tank hysteresis

Six deterministic simulated levels demonstrate pump hysteresis and fail-closed sensor faults. No pump, firmware, board or physical water control.

## Reproduce from a clean extraction

Use Node.js 22 or newer with no third-party installation. In this package directory:

```sh
node test.mjs
node simulate.mjs
```

simulate.mjs prints six synthetic records with hardwareVerified:false. Tests exercise state transitions and input bounds without any network or hardware.

## What is measured

Pump sequence true,true,false,false,false,true for levels10,35,80,70,null,15. A missing sensor reading disables the simulated pump; it stays off until the low threshold.

## Negative controls and bounds

<=1000 readings, finite levels0-100 or null fault, low<high within0-100. Unknown/NaN/infinite readings and invalid thresholds reject. This is not a physical safety controller.

## Rights and provenance

Six fixed original synthetic level readings; no hardware trace or real device records. Code is original under MIT LICENSE. Keep all permission and copyright notices. No real personal records or external content is included.

## Limitations

No physical tank, pump, sensor, actuator, firmware, electrical safety, calibration or hardware verification. No authorship, academic marks or university certification is transferred. This source-only catalogue listing is illustrative; reproducing a local package does not prove a hosted runtime, real-world model accuracy or production readiness.
