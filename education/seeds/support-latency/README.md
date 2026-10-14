# Synthetic support latency percentiles

Six original synthetic tickets demonstrate bounded timestamp cleaning, nearest-rank percentiles and explicit per-priority threshold denominators.

## Reproduce from a clean extraction

Use Python 3.11 or newer with no third-party installation. In this package directory:

```sh
python3 test.py
python3 analysis.py
```

analysis.py reads synthetic.csv beside the program and emits deterministic JSON. Tests exercise isolated temporary CSV files and remove them.

## What is measured

High-priority p50=30.00, p95=90.00, aboveTarget1/3. Normal p95=180.00, aboveTarget1/3. Percentiles use nearest-rank; empty groups are null, not zero.

## Negative controls and bounds

Exact columns, maximum10000 tickets, unique identifiers<=40 characters, UTC timestamps, elapsed0-31days. Invalid schemas, duplicate IDs, negative intervals and extra/missing fields reject.

## Rights and provenance

Six hand-written synthetic tickets use fictional identifiers and dates; no external logs or real people. Code is original under MIT LICENSE. Original synthetic data has its own DATA_LICENSE. Keep all permission and copyright notices. No real personal records or external content is included.

## Limitations

No real support logs, customer data, production SLA, interactive dashboard, business prediction or sampling inference. No authorship, academic marks or university certification is transferred. This source-only catalogue listing is illustrative; reproducing a local package does not prove a hosted runtime, real-world model accuracy or production readiness.
