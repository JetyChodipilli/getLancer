# V5.1 Go pattern implementation

S07-B04 is implemented in `labs/go/server.go`, with original MIT rights in
`labs/go/LICENSE`, exact setup and limits in `labs/go/README.md`, and focused
regressions in `labs/go/server_test.go`. No application dependency or external Go
package was introduced. The shared catalogue declares Go's primary source and
native `go run labs/go/server.go` launcher.

The adapter implements all three existing pattern contracts: real Redis cache
miss/hit/expiry and deadline fallback; synthetic signed identity with current
tenant/role/expiry/revocation checks; and an exact-body-HMAC payment emulator
with duplicate/conflict/capacity handling and out-of-order refund reconciliation.
Responses bind UUID run/request IDs and monotonic typed events to the actual
primary source SHA-256. Form and protocol denial never fabricate execution
events. State is serialized under one mutex, and bounded concurrent retries
retain a single payment effect.

The local run expires after 15 minutes. HTTP is loopback-only, with eight active
connections, finite protocol/read/write deadlines, bounded bodies/headers and
responses. Redis has one absolute 250 ms deadline and finite reply parsing. The
ledger retains at most 100 event bodies; counters fail closed before overflow.
Shutdown drains active requests before deleting only the current run's Redis key.
The key's 5000 ms TTL is the cleanup fallback when Redis is offline. Go runtime
memory is given a 64 MiB GC target; it is not a claimed OS sandbox or hard RSS cap.

## Observed local evidence

Local execution used official Go 1.24.13 on Linux amd64, whose downloaded archive
was verified against the official Go release JSON checksum
`1fc94b57134d51669c72173ad5d49fd62afb0f1db9bf3f798fd98ee423f8d730`, and separately
built **real Redis 7.2.16**. Native source compilation and `go vet` succeeded.

Nine focused tests passed with `-race`: strict form encoding; admission without
effects; current-role and identity checks; exact raw-body signatures and signed
grammar rejection; out-of-order refund convergence; overflow plus retry at
capacity; 32 concurrent deliveries with one effect and strictly ordered unique
events; run/counter expiry; and HTTP denial/redacted-response rules.

An actual native process shutdown probe interrupted a real blackhole Redis read,
then verified that the request still returned bounded fallback, the process exited
with code 0, and its Redis key was deleted. The same single-run probe observed:

| Measurement | Observed value |
| --- | --- |
| Compiled native startup to health line | 14.35 ms |
| Child peak resident set, measured with wait4 | 13,952 KiB |
| Actual blackhole Redis operation | 250 ms |
| SIGTERM to drained exit and cleanup | 280.53 ms |

These are one local observation, not performance guarantees. Native startup
excludes the `go run` compilation phase. No hosted memory/CPU isolation is claimed.

The final current-source shared real-HTTP matrix passed all three Go labs with 156 requests,
two-run cache/payment isolation, genuine Redis deadline expiry, raw signature
failure, duplicate/conflict/capacity behavior, role revocation and typed response
projections after shutdown review. The observed output was
`SCENARIO_MATRIX_OK language=go labs=3 requests=156 redis=7.2.16 mode=local`.
The source SHA-256 observed by this final run and the shutdown probe was
`8ded84a95035e048d6b8c0f2b4a5a987ca78239e9dc4932167909e83349a7ef8`.

## Acceptance boundary

Parent completion gates remain pending until independent root review, final
current-source matrix/source-package reproduction and CI acceptance. Local
source and native results do not activate hosted Go execution; certification and
the existing operator/provider authority remain separate requirements.
