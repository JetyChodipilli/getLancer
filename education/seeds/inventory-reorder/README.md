# Synthetic inventory reorder worksheet

Four original synthetic stock rows demonstrate reserved stock, deterministic reorder points and exact Decimal purchasing totals.

## Reproduce from a clean extraction

Use Python 3.11 or newer with no third-party installation. In this package directory:

```sh
python3 test.py
python3 analysis.py
```

analysis.py reads adjacent synthetic.csv and emits a sorted exact cost table. Tests create/remove temporary invalid CSV fixtures.

## What is measured

Pencil available20, reorderPoint35, suggested15 at1.25 gives18.75. Notebook needs0. Full toy total37.55 synthetic units.

## Negative controls and bounds

Maximum5000 rows; quantities0-1000000, lead1-30days, stock>=reserved, strict synthetic SKU and fixed2-decimal currency. Duplicate/missing/malformed rows reject.

## Rights and provenance

Four hand-written synthetic SKUs and synthetic currency values; no external inventory data. Code is original under MIT LICENSE. Original synthetic data has its own DATA_LICENSE. Keep all permission and copyright notices. No real personal records or external content is included.

## Limitations

No supplier service, real SKU or financial data, demand forecasting, replenishment guarantee or purchasing advice. No authorship, academic marks or university certification is transferred. This source-only catalogue listing is illustrative; reproducing a local package does not prove a hosted runtime, real-world model accuracy or production readiness.
