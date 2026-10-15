# Frozen nearest-centroid intent exercise

Original six-word JSON centroids classify three held-out synthetic phrases, with explicit unknown/tie abstention. No real support routing accuracy.

## Reproduce from a clean extraction

Use Python 3.11 or newer with no third-party installation. In this package directory:

```sh
python3 test.py
python3 classifier.py
```

classifier.py emits the three-row fixed evaluation. test.py also tests abstention, model schema and invalid inputs using temporary local files.

## What is measured

3 of3 tiny held-out strings match toy labels. Unknown vocabulary and tied account/billing evidence abstain. The vocabulary is already deliberately aligned with these synthetic labels.

## Negative controls and bounds

Text1-500 characters; model<=64KiB; <=20 unique words and <=10 unique binary centroids. Remote-loader fields, booleans as vector entries and wrong dimensions reject.

## Rights and provenance

All six reference/evaluation strings and centroid coefficients are original hand-written synthetic material. Code is original under MIT LICENSE. Original synthetic data has its own DATA_LICENSE. Frozen JSON coefficients have their own MODEL_LICENSE. No training or remote loader is performed. Keep all permission and copyright notices. No real personal records or external content is included.

## Limitations

No model training, remote loader, pretrained model, real support data, population accuracy, fairness or safety claim. No authorship, academic marks or university certification is transferred. This source-only catalogue listing is illustrative; reproducing a local package does not prove a hosted runtime, real-world model accuracy or production readiness.
