# V4 static frontend demo operations

V4 includes recurring maintenance/support and reviewed static frontend hosting. Hosting accepts a built HTML/CSS/JavaScript ZIP; it never installs packages, compiles submitted source, clones repositories or runs server code. The company frontend preview exercises labelled local metadata. A live publisher is a separate deployment and is disabled by default.

## Deployment boundary

Deploy `ops/demo-publisher` as one non-root writer on its own persistent volume. Use an independent registrable domain for demos, such as `https://{id}.example-demos.net`, with wildcard DNS and TLS. Never use the getLancer application origin or a parent domain receiving application cookies. Do not share application secrets, authenticated proxy rules, object-storage credentials or billing credentials with demo content. Browser JavaScript remains untrusted.

The publisher has two ingress rules: the configured internal admin Host for private Java-to-publisher HTTP, and exact UUID public demo Hosts. The private ingress must be unreachable publicly; reverse proxies must preserve the validated Host and must not rewrite public requests to the admin Host. Only allow GET/HEAD at public ingress. Do not strip CSP or enable intermediary response caching. The public server ignores cookies, authorization and query credentials. It authorizes each file request through the fixed Java gateway and denies access when the gateway is unavailable. Existing pages in a browser cannot be recalled; withdrawal denies subsequent requests.

Set backend-only `HOSTED_DEMOS_ENABLED=true`, `DEMO_PUBLISHER_URL`, a random `DEMO_PUBLISHER_SECRET` of at least 32 characters, a different random `DEMO_GATEWAY_SECRET` of at least 32 characters, and `DEMO_PUBLIC_URL_TEMPLATE`. Set the publisher's corresponding secrets/template, `DEMO_PUBLISHER_ADMIN_HOST`, fixed `DEMO_GATEWAY_URL` ending `/api/v1/hosting/gateway`, and `DEMO_PUBLISHER_DATA_DIR=/data`. Use HTTPS in production. HTTP is restricted to local/internal development addresses. Never expose these values through `NEXT_PUBLIC_*`.

For local Docker, set `COMPOSE_PROFILES=hosting`, publisher URL `http://demo-publisher:8090`, template `http://{id}.demo.localhost:8090`, and the two secrets in the ignored `.env`. Then run `docker compose up -d --build --wait`. Public ports are loopback-bound. The image creates `/data` as UID/GID 1000; bind-mounted directories must have that owner. The named volume preserves ownership. Root filesystem is read-only, capabilities are dropped, no-new-privileges is enabled, and memory/CPU/process limits are applied.

## Review, publication and cost limits

An active approved individual builder selects a currently approved public proof with matching verified repository evidence. Upload requires separate rights consent. The immutable archive is private in the existing S3 bucket. Submission records renewed rights consent. An MFA operator must download the actual ZIP, inspect its files and rights evidence, and record both review checks plus a reason. Approval permits a separate builder publication action; it does not establish a safety or ownership guarantee.

Limits are 5 MiB compressed, 10 MiB expanded, 256 files and 5 MiB per file, with root `index.html`. Paths are safe ASCII and case-unique; private files, credentials, server source, source maps, nested archives, links and unsupported types are rejected. Each owner retains at most 10 records and at most 3 unresolved deployment reservations. Default expiry is 7 days; the hard maximum is 30 days. This first hosting release is free and bounded; it creates no hosting charge or paid entitlement.

The publisher permits at most 500 MiB of bundle data and 1000 permanent identities, 240 requests per minute per demo, one upload and eight public responses in flight until finish or close. Each public response has a ten-second deadline, including gateway authorization and file reads; stalled clients are disconnected. Rejected gateway responses are destroyed immediately rather than left draining without a deadline. Storage/identity/traffic caps can be configured downward only. A 512 MiB container cap accommodates the maximum JSON/base64 upload and bounded file buffers. Domain, TLS, compute, network and backups still have infrastructure costs; provider budget/egress quotas and alerting must be set before live activation. Do not present these software caps as a monetary spending guarantee.

## Recovery and withdrawal

Each publication reserves one fixed UUID, frozen archive/manifest hashes and expiry in PostgreSQL before publisher HTTP. Lost responses become UNKNOWN. Reconcile the same identity; an absent reservation can retry only the same reviewed immutable payload after current permission checks. No ambiguous request allocates a new identity.

Withdrawal commits WITHDRAWN/DELETE_PENDING before sending DELETE. The gateway immediately denies new access. Permanent publisher tombstones prevent delayed PUTs resurrecting a removed UUID. Repeat deletion/reconciliation until Java records DELETED. Account closure is blocked until existing publisher reservations are confirmed removed. A disabled feature or revoked builder approval must not prevent recording withdrawal intent; operator recovery resolves uncertain deletion when infrastructure is available.

If identity capacity is exhausted, deleting an unknown reservation permanently fences the unused UUID namespace on that volume before confirming DELETED. The fence survives restart and later cap increases. Existing identities remain manageable; new publication on that volume stays closed. Never remove the fence or old tombstones to regain capacity.

Expiry removes served bundle files while retaining tombstone metadata. Profile/account/proof/repository/approval changes are checked per request, without an entitlement cache. PostgreSQL or gateway outage denies static responses. Archive hashes, safe projections and append-only hosting audit rows are included in account export and encrypted database recovery checks.

Back up PostgreSQL, the private object store and the publisher volume separately. Restore all three to a private environment before changing public DNS. Keep the publisher stopped during a volume restore; retain tombstones so old identities cannot return. Do not run multiple publisher replicas against the same volume. A process-held Linux `flock` lock excludes overlapping local startups, including stale metadata recovery. The lock inode is permanent and must not be deleted or replaced while a writer runs. The Alpine image includes the lock utility; native local runs require `flock`. This is a local-volume lock, not a distributed storage lease. The CI restore rehearsal restores the database and verifies retained rows; a production publisher-volume restore drill remains an operator responsibility.

## Verification and primary references

CI tests the Java/PostgreSQL control plane, real private MinIO archive bytes, the real separate Node publisher, approval/download consent, isolated browser JavaScript, public CSP, immediate revocation, database outage, withdrawal and encrypted database recovery. It does not provision live DNS/TLS or assert provider/merchant approval.

The sandbox and HTTP settings follow [MDN's CSP sandbox reference](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/sandbox) and [Node's HTTP server API](https://nodejs.org/api/http.html). Origin separation remains necessary because the demo intentionally permits its own browser scripts. Runtime image provenance is the [official Node Docker image](https://github.com/nodejs/docker-node), with third-party notices retained in the repository.
