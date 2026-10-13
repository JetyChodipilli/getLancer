# V5.1 C# / .NET native adapters

S07-B05 contributes three native .NET 8 labs: Redis cache, synthetic current-role
authorization and payment emulator. Primary source is `labs/dotnet/Program.cs`;
the original MIT license, native SDK project, offline restore config, isolated
per-run build paths, Release publish profile and setup are in the same directory.
No application runtime or NuGet dependency is added.

The implementation follows the full V4.8 HTTP/state/event contract. ASP.NET Core
listens on IPv4 loopback with finite connections, backlog, input/output and socket
deadlines. Process lifetime defaults to ten minutes and can be reduced by the
operator. Strict decoding and exact field allowlists precede effects. Internal
typed records and event enums preserve source/run/request identity and monotonic
event sequence; timestamps use fractional UTC `Z` and errors never invent events.
A semaphore serializes bounded state.

Cache uses actual Redis RESP2 and one 250 ms budget over all TCP operations.
Authorization verifies synthetic HMAC identity, expiry, tenant, identity role and
current policy. Payment verifies raw decoded body HMAC in constant time, preserves
confirmed facts over ambiguous timeout, deduplicates at capacity, conflicts on
changed event-ID bodies and converges refund-before-payment without entitlement.

Verification consists of the shared real Redis 7.2.16 matrix plus the native
`labs/dotnet/verify.mjs` HTTP/resource/concurrency regressions. No recording or
test result is generated without observing execution.

Observed local acceptance on 2026-10-13:

- Original source compiled with Microsoft's SHA512-verified .NET SDK 8.0.425
  Roslyn compiler, the installed .NET/ASP.NET reference assemblies, nullable
  checks and warnings treated as errors; no C# compiler warnings or errors.
- Actual ASP.NET execution with .NET/ASP.NET 8.0.31 passed the shared matrix:
  three labs, 156 requests, real Redis 7.2.16.
- Actual native HTTP/TCP regressions passed all 38 checks, including 16 concurrent
  retries with one effect, two-run policy isolation, strict literal/escaped UTF-8,
  bounded slow-body timeout and clean two-second process expiry.
- Three direct native starts were observed; the slowest readiness time was
  1085 ms on this shared executor. Process RSS inspection was unavailable, so no
  memory measurement or hosted startup certification is claimed. The test emits
  measured readiness/RSS availability for subsequent CI observations.
- Source SHA-256: `f97b492274eb332ab496f67c19dd196f75419d4844f18a57076828d0ab15c75a`.

Environment limitation: this executor rejects the SDK CLI/MSBuild process
metadata inspection in `System.Diagnostics.Process`. Local execution therefore
used the actual Roslyn-compiled assembly directly through the native .NET host;
HTTP, cache and security results were not simulated. Exact SDK project
restore/build, per-run SDK output isolation, Release publishing and the documented
`dotnet run` command must still be checked in current-head CI. Hosted certification
remains separate and disabled until signed provider evidence and operator
admission permit it.

`node labs/dotnet/verify.mjs --sdk` adds those exact SDK checks: Release publish,
published-assembly health/expiry, the documented `dotnet run` startup/expiry and
the UUID-scoped assets/assembly files. This mode requires a real working SDK CLI;
the restricted-host compiler wrapper does not implement or claim these checks.
The native event-format regression checks actual authorization/payment responses,
including concurrent retries, for fractional UTC `Z` timestamps. Source hash
changed when that canonical timestamp format was corrected; recordings must be
regenerated against the current primary source.
