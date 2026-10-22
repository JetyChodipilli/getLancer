# V5.2 operational evidence and handover

V5.2 software can merge after reviewed final-head CI. Production launch remains **UNMET** until the deployment supplies actual load, seven-day runtime, incident, provider and manual assessment evidence. These commands collect or verify evidence; they never enable runtime execution, publish a source package, charge a payment or modify a database.

Use Linux and Node 22.13+ for the descriptor-bound evidence verifier. The collector and verifier use Node standard libraries and the existing strict JSON parser. No new service or dependency is required. CI fixtures have `purpose: TEST`; even a trusted signature cannot turn them into production release evidence.

## Declare the environment before measuring

Pin the actual deployed Git commit, deployment environment and public HTTPS origin. Keep the same revision, immutable source/image pins, provider operator epoch and deployment profile throughout a soak. Record a new soak after a restoration, provider authority rotation, code change or deployment profile change. Evidence expires 24 hours after its final observation; perform the final load/drill review near the end of the soak.

Supply `--profile /restricted/evidence/profile.json` to both collectors. This file contains exactly the following fields, populated from the deployment inventory. These are **operator-declared server assumptions**, not a measurement of server hardware by a remote client:

```json
{
  "cpuCores": 4,
  "memoryMiB": 8192,
  "postgresMajor": 16,
  "replicas": 1,
  "storage": "SSD",
  "clientLocation": "SAME_REGION"
}
```

The values above illustrate the schema; they do not claim an existing deployment. Storage accepts `SSD`, `HDD` or `MANAGED`; client location accepts `SAME_REGION`, `OTHER_REGION` or `LOOPBACK`. Preserve the host/database inventory digest in the restricted operator trace. Public evidence contains only fixed metadata, counts, UTC times and hashes. Never add customer identities, institution names, session cookies, input text, signed capability URLs, provider credentials or raw private response bodies.

## Bounded catalogue load

The release profile schedules 20 read-only catalogue GETs per second for at least 60 seconds, with at most 10 outstanding requests and a finite timeout. It varies the first 50 pages of the unfiltered public catalogue. Every scheduled request has an outcome, including timeouts and capacity skips. The actual public response must report at least 10,000 **eligible publicly visible** rows for every successful request. A database with 10,000 hidden or draft rows cannot satisfy this gate. The tool does not seed fabricated catalogue rows; create a separately reviewed synthetic staging dataset outside production, then document its rights, visibility, distribution and PostgreSQL query plan.

```bash
task_revision=$(git rev-parse HEAD)
node scripts/v52-load.mjs --origin https://staging.example.test --catalogue components --environment staging --revision "$task_revision" --profile /restricted/evidence/profile.json --out /restricted/evidence/load-final
node scripts/v52-evidence.mjs inspect --file /restricted/evidence/load-final.json --environment staging --revision "$task_revision"
```

Use the commit actually serving the endpoint; `git rev-parse HEAD` is appropriate only when that exact checkout is deployed. The `.jsonl` is a hash chained raw observation journal; `.json` is its unsigned manifest. Output files are exclusive creations and have mode `0600`. Use new filenames for every run.

Both collectors accept only `--catalogue products|components|college-projects`, record that target in the journal, and keep the existing `products` default. Run a separate declared profile for each new catalogue. Older logs without a selector remain inspectable as product measurements; product-only load evidence cannot pass the new-catalogue release gate. Catalogue probes do not measure source-download availability, which needs separate operational evidence.

Client round-trip p95 and server application p95 are separate values. Successful catalogue responses provide `Server-Timing: app;dur=...`, measured by the Java server using a monotonic clock through metadata serialization; network transfer is outside that server measurement. The server gate requires p95 at most 300 ms and complete request-local timing. If an ingress removes the header, the tool reports server timing as **UNMET**. It does not substitute browser/network latency for server latency. Record observed failures rather than removing slow or failed samples. This finite profile does not certify arbitrary concurrency, a million users, source downloads, writes or provider execution capacity.

## Seven real elapsed days

The monitor probes the public catalogue at most once per minute by default and records actual UTC time together with monotonic elapsed time. It does not simulate time. To cover at least seven days of both public and backend-authoritative runtime observations, run for seven days plus one cadence interval:

```bash
node scripts/v52-monitor.mjs --origin https://staging.example.test --environment staging --revision "$task_revision" --profile /restricted/evidence/profile.json --out /restricted/evidence/soak-public --duration-ms 604860000
```

Public-only monitoring establishes public sampled coverage. It cannot certify an admitted runtime denominator. To capture runtime authority, an authorized administrator explicitly configures a restricted cookie file and opts in:

```bash
export V52_ADMIN_COOKIE_FILE=/restricted/auth/current-admin-cookie
node scripts/v52-monitor.mjs --origin https://staging.example.test --environment staging --revision "$task_revision" --profile /restricted/evidence/profile.json --out /restricted/evidence/soak-runtime --duration-ms 604860000 --admin configured
```

Put only the current cookie header value in the file; do not put credentials in command arguments or shell history. This is not a login tool. The operator must authenticate normally, complete fresh MFA and rotate the file as required. Cookie contents are read only after explicit configuration, sent only to the exact configured origin with redirects refused, and never written to the journal or console. Authentication expiry or unavailable admin snapshots are retained as failed observations. The tool does not bypass fresh MFA, mint a monitoring role or grant new authority.

`GET /api/v1/admin/operations` supplies durable cumulative counters since `counterEpoch`/`measuredFrom`, plus the current provider `operatorEpoch`. The recorder never subtracts rolling 30-day windows. The baseline and endpoint must have zero pending admissions, share a non-null provider epoch, span at least seven actual days and be close to the public endpoints. A changed provider epoch invalidates the cohort even when restored database counters look unchanged. All new admitted reservations remain in the denominator; healthy provisioning is the numerator. Prehealthy terminal cancellations, revocations and infrastructure failures remain infrastructure failures. Scenario failures, validation denials and quota denials are reported separately and do not inflate that denominator. A zero-admission or unsettled cohort has no infrastructure success percentage. A settled cohort must reach at least 99% admitted provisioning success.

Public failures retain their status and timing. The observed outage estimate conservatively charges intervals bordering failed probes. It is a sampled interval SLI, not proof of monthly 99.5% availability or a continuous network trace. Preserve actual incident intervals and the separate monthly ingress/server SLI in the restricted operational system. Seven-day prelaunch evidence cannot establish a full month.

For rotation or restart, use distinct segment filenames and collect them in chronological order, without gaps exceeding one minute. Every segment must have the same environment, revision, origin, declared profile and sampling configuration. Logs and the combined report must be sibling regular files; links, traversal, duplicates, missing intervals and mixed provenance are rejected.

```bash
node scripts/v52-evidence.mjs collect --logs /restricted/evidence/soak-01.jsonl,/restricted/evidence/soak-02.jsonl --out /restricted/evidence/soak-combined.json --environment staging --revision "$task_revision"
```

The maximum cohort is 25,000 observations and 32 MiB, with at most 100 segments. Requests time out within 10 seconds, JSON responses are bounded to 64 KiB, load concurrency is capped at 10, and collector duration is explicit. A process crash leaves its raw journal; a torn final line is rejected. Preserve it for investigation and start a new evidence sequence rather than editing failed observations away. Graceful completion produces a manifest; the `collect` command can recover a fully written, untruncated journal.

## Review, sign and verify

`inspect` produces `MEASUREMENT_ONLY` and reports met/unmet gates. A successful inspect or successful signing action is not launch approval. An accountable operator reviews raw traces, actual environment and immutable source pins before signing with their protected Ed25519 key. The verifier receives the already trusted public key from the operator's deployment policy, never a key supplied by the evidence author inside the report.

```bash
node scripts/v52-evidence.mjs sign --file /restricted/evidence/soak-combined.json --out /restricted/evidence/soak-signed.json --private-key /restricted/keys/operator-ed25519.pem --environment staging --revision "$task_revision"
node scripts/v52-evidence.mjs verify --file /restricted/evidence/soak-signed.json --public-key /restricted/policy/trusted-operator.pem --environment staging --revision "$task_revision"
```

Verification rejects altered hashes/signatures, unknown fields, duplicate JSON keys, future/stale dates, linked files, conflicting times, compressed wall/monotonic clocks, incomplete denominator coverage, missing provider authority and unmet release gates. A signature proves that the trusted operator attested the captured provenance. It cannot mathematically prove that a dishonest signer did not fabricate every timestamp and trace. The operator must retain independent provider/ingress/CI logs and reconcile their digests before signing; unit-test fixtures are explicitly excluded.

See [INCIDENTS.md](INCIDENTS.md) for the drill procedures and [RELEASE_EVIDENCE.md](RELEASE_EVIDENCE.md) for the fixed proof schema, independent activation lanes, accountable ownership and launch decision.
