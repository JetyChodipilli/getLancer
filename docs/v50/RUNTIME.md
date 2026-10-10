# V5.0 runtime rebuild

The fixed source/runtime interface is docs/v50/CONTRACT.md revision 1. Four original synthetic MIT scenarios run on Python 3.12+ standard library only, with separate code, data and model grants. No request selects a file, URL, model loader, command or import. Source downloads and measured recorded evidence are available independently of hosted admission.

## Scenario inputs and units

All inputs in envelopes/defaults/changedInputs are strings. Form identity fields are `runId`, `scenarioId`, `operationId=execute`; these are excluded from the canonical input hash. Every operation requires exactly its scenario fields. Runtime startup uses `LAB_RUN_ID` and `LAB_PORT`.

| Scenario | Exact input fields | Default | Changed |
| --- | --- | --- | --- |
| revenue-summary | region all/north/south; upliftPercent canonical integer -20..20 | all; 0 | south; 10 |
| sensor-quality | channel all/temperature/vibration; tolerance canonical integer 1..10 | all; 3 | temperature; 1 |
| sentiment-inference | text printable ASCII, contains a letter, maximum 160 UTF-8 bytes | excellent reliable service | terrible broken service |
| equipment-inference | temperature canonical integer 0..100 Celsius; vibration canonical integer 0..20 mm/s | 35; 2 | 90; 16 |

Revenue analyses six synthetic orders in integer INR. Monthly table columns are month/string/none, revenue/number/INR, orders/number/orders. Metrics are revenue/INR, orders/orders and averageOrder/INR (rounded integer). Sensor analyses eight synthetic deviations; its per-channel table columns are channel/string/none, accepted/number/readings, rejected/number/readings and total/number/readings. Metrics are accepted/readings, rejected/readings and acceptance/percent. Filtering changes measured row counts and chart/table values.

Both inference outputs contain a class/string/none and probability/number/probability table in fixed model class order. Prediction is the class with greatest probability; tie resolves to first class. Metrics are confidence/probability and evaluationAccuracy/ratio. Sentiment classes are negative/positive; equipment classes normal/attention. Original hand-authored fixed logistic coefficients are illustrative models, not trained checkpoints. Separate evaluation fixtures contain exactly eight held-out synthetic examples, not the public default/changed examples. Evaluation reports samples, correct and accuracy with explicit synthetic/no-training limitations.

## Protocol and evidence

The exact successful LOCAL envelope, index item and REPLAY envelope schemas are the shared contract. Canonical JSON sorts keys recursively, normalizes integral floats to integers and emits bounded numbers at most six decimals in decimal notation; tiny probabilities therefore match JavaScript JSON.stringify. Input/result hashes and source/data/model/evaluation hashes come from actual bytes/values. Actual UTC startedAt and monotonic elapsedMs are measured. Input echo exists only in the bounded LOCAL envelope required by the contract; no request text appears in logs. Published recordings contain only the fixed synthetic examples.

Local server binds 127.0.0.1, rejects foreign Host and any Origin/Transfer-Encoding header including empty values, conflicting or duplicate framing, invalid UTF-8/percent encoding, queries/path/method/content-type violations and unknown/duplicate fields. It limits total header and body reads to one absolute second, further capped by remaining run lifetime/idle expiry. 8 KiB request bodies, 4 KiB JSON responses, 100 attempts, 300-second lifetime and 90-second idle cleanup apply. `LAB_LIFETIME_SECONDS`/`LAB_IDLE_SECONDS` can only lower expiry limits for tests. Attempts are counted before parsing, including rejected framing. GET health reports actual limits and source/run identity. Errors have only error/message fields, fixed public messages and no user data/traceback.

Supported Unix systems receive address-space limit 256 MiB, CPU limit 10 seconds, 64 descriptors, 512 KiB file output and zero core-dump limit. On systems without the standard-library resource module, health explicitly reports osLimits=false. This Linux evidence does not certify Windows or hosted isolation. State stays in memory and shutdown clears fixture/model/catalogue dictionaries; process termination releases all process memory.

The source archive has a fixed fourteen-file inventory, zero timestamps/uid/gid and only regular 0644 files. Neither recordings, logs, caches, environment files nor executable model formats are packaged. Catalogue-pinned data/model/evaluation bytes are verified at runtime. Builder and verifier reproduce from a clean extraction and compare actual default/changed execution. Measured timestamps in recordings remain honest observations; `--verify` verifies semantic reproducibility without pretending timestamps are deterministic. Publication occurs only after all relevant checks pass.

Hosted activation remains disabled until independently certified provider/image/network/budget/cleanup/restore evidence passes. Local source, recordings and resource limits do not satisfy that separate gate.

## Rebuild verification evidence

This rebuild used Python 3.12.14 and Node 24.19.0 in Linux. Actual independent JavaScript oracles recomputed each full result from pinned source fixture/model/evaluation bytes; both eight-sample synthetic evaluation splits produced correct=8 and accuracy=1. These values demonstrate the frozen illustrative processing example only. All four scenarios changed input and result hashes, repeated identical inputs reproduced result hashes, and all eight default/changed jobs reproduced from a fresh extraction.

`node scripts/verify-data-ai.mjs --verify` passed (DATA_AI_VERIFY_OK). `node scripts/build-data-ai-material.mjs --verify` passed (DATA_AI_MATERIAL_OK), checking the original public/index/recording/archive bytes while running new jobs to verify semantic reproduction. The material-check checkpoint measured 2,000 jobs in 676.70 ms, 15,744 KiB peak RSS and 0.7524 CPU seconds; maximum actual LOCAL envelope was 1,536 bytes. It confirmed 268,435,456-byte RLIMIT_AS and 10-second RLIMIT_CPU, and all in-memory dictionaries empty after cleanup. Measurements vary with host load. Unsupported-resource platforms are reported as osLimits=false and have no Unix resource-certification claim.

`node --test tests/data-ai-runtime.test.mjs`: 11 tests passed, zero failures. Original actual recordings serve as positive controls for altered source/input/result hashes, identities, future times, unknown fields, forged contextual metrics and even internally self-consistent fabricated revenue values (which fail the pinned-byte oracle). Tests also exercise duplicate raw JSON aliases/prototype safety, foreign/unbounded catalogue fields, exact deterministic fourteen-file inventory, archive corruption/omissions, changed data/executable model formats/absolute and parent-symlink paths, framing/Host/Origin/method/type/query/UTF-8/percent/body denials, extreme valid tiny logistic probabilities, malformed inputs and pre-parser budget exhaustion. A real dribbled-header connection gets REQUEST_TIMEOUT within the absolute deadline; actual one-second lowered total and idle expiry shut the disposable process down. No request values appear in logs.

Four passes are complete: implementation; expert review of fixed interface, data/model/evaluation separation, integer units and synthetic accuracy limitations; defect hunt of parser deadlines/accounting/duplicate JSON, publication-before-verification and cross-language tiny-probability hashing; low-cost source/inventory/privacy polish and final focused re-check. The existing nine labs were not edited by this leaf.

Source SHA-256: `62944222476a16bc5a742b503bcaf2ebede544bc4a9bfe231563eef215cae1e8`. Deterministic 12,236-byte source archive SHA-256: `f7232dbfb60d6e62ec1e7634ca81cd05ae7f89b9883ef17f7b437b5d6f330cf5`. The owned `evidence-manifest.json` pins these values for the compiled frontend and is checked by the builder; it is excluded from the archive to avoid circular hashing. Root performs independent parent verification and joined security/QA review before any release claim. No commit, push, provider activation or production deployment was performed by this leaf.
