# Frozen synthetic energy forecast

Original fixed Decimal coefficients produce exact predictions for three held-out synthetic rows. Zero toy error is a formula check, not building accuracy.

## Reproduce from a clean extraction

Use Python 3.11 or newer with no third-party installation. In this package directory:

```sh
python3 test.py
python3 forecast.py
```

forecast.py emits3 deterministic predictions and synthetic MAE. Tests reject NaN/Infinity/bool/range inputs and unsafe model schemas.

## What is measured

Held-out predictions4.00,9.00,14.00 synthetic kWh; MAE0.00 only because toy rows were generated from the same illustrative coefficients. No measured building data exists.

## Negative controls and bounds

Temperature-10 to45C, whole occupied hours0-24; nonfinite values and booleans reject. Model<=64KiB with exact3 bounded nonnegative two-decimal string coefficients.

## Rights and provenance

All temperatures, occupied hours, synthetic kWh and fixed coefficients are original fabricated examples. Code is original under MIT LICENSE. Original synthetic data has its own DATA_LICENSE. Frozen JSON coefficients have their own MODEL_LICENSE. No training or remote loader is performed. Keep all permission and copyright notices. No real personal records or external content is included.

## Limitations

No fitted model, real energy-meter data, weather feed, building calibration, uncertainty, savings or forecast accuracy claim. No authorship, academic marks or university certification is transferred. This source-only catalogue listing is illustrative; reproducing a local package does not prove a hosted runtime, real-world model accuracy or production readiness.
