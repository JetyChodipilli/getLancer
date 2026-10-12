# getLancer V5.0 local analytics and CPU inference

Python 3.12+ standard library only; no pip installation, account, training or external provider is needed. The source is free. Code, synthetic fixtures and original frozen JSON coefficients have separate MIT grants in License.md, DATA_LICENSE.md and MODEL_LICENSE.md. The tiny held-out synthetic evaluation establishes reproducibility, not real-world accuracy.

From the extracted package root:

```sh
LAB_RUN_ID=00000000-0000-4000-8000-000000000001 LAB_PORT=8105 python3 runtime.py
```

GET `http://127.0.0.1:8105/health`. POST exact form fields:

```sh
curl -H 'Content-Type: application/x-www-form-urlencoded' --data 'runId=00000000-0000-4000-8000-000000000001&scenarioId=revenue-summary&operationId=execute&region=all&upliftPercent=0' http://127.0.0.1:8105/request
```

catalogue.json specifies the other three scenarios, input definitions, default/changed examples, schema-labelled tables/metrics and pinned asset hashes. Change input values to run another actual local computation. Original UTC startedAt, measured elapsedMs and byte/canonical JSON hashes accompany results. Recorded website evidence is REPLAY; selecting a recorded example does not execute an input. Hosted execution has separate operator/provider certification.

Only exact 127.0.0.1 Host requests are accepted. No Origin/CORS, query, chunked/conflicting framing, URL/file/dynamic loader, upload, arbitrary command or model selection is supported. The server uses 8 KiB body / 4 KiB output caps, 100 attempts including rejected parsing, one absolute second per connection, 300-second total and 90-second idle expiry. LAB_LIFETIME_SECONDS/LAB_IDLE_SECONDS may lower expiry only. On supported Unix platforms it applies 256 MiB address space, 10 CPU seconds, 64 descriptors, 512 KiB file output and zero core limits. Unsupported platforms report osLimits=false. Local process limits do not certify hosted or Windows isolation. No inputs are logged; all source examples are synthetic. In-memory fixtures/models are cleared on normal shutdown.

Repository verification: `node scripts/verify-data-ai.mjs --verify`; build measured recordings/material with `node scripts/build-data-ai-material.mjs`; check existing published evidence with `node scripts/build-data-ai-material.mjs --verify`. Source archives are deterministic over a fixed fourteen-file inventory; measured timestamps stay as original observations.
