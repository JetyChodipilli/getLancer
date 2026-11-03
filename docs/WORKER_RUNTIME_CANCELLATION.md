# Worker response cancellation

The native browser-test server uses the actual built Worker through Miniflare.
A browser may leave while HTML, RSC or an asset response is streaming; response
producers must stop when that consumer disconnects.

The previous Miniflare `5.20260930.0-alpha` / workerd `1.20260930.2` pair
failed a raw TCP disconnect regression: asynchronous JavaScript response sources
never received `cancel()`, even though the request signal observed the disconnect.
The same bounded direct and service-binding cases pass with Miniflare
`5.20261101.0-beta` / workerd `1.20261101.1`. Normal response completion still
closes without invoking cancellation, and a subsequent health response is fully
consumed successfully.

The dependency update keeps the publisher's exact matching set: Cloudflare Vite
plugin `1.67.0`, Wrangler `4.156.1` and Miniflare `5.20261101.0-beta`. Vite 8
and the project's supported Node versions meet those packages' requirements.
Miniflare 5 remains a prerelease, as was the already installed alpha; this update
uses the set selected by the official Vite plugin rather than mixing runtime
versions. The application compatibility date and flags are unchanged.

The upstream stream cancellation repair is
[workerd #7600](https://github.com/cloudflare/workerd/pull/7600), which fixes
[workerd #6832](https://github.com/cloudflare/workerd/issues/6832). The regression
in `tests/worker-stream-cancel.test.mjs` uses the real HTTP listener, source
callbacks and TCP reset; it does not mock the runtime or suppress its output.
Run it with `node --test tests/worker-stream-cancel.test.mjs`. It is also part of
the existing complete Node test suite in CI.

The earlier CI signature `rust/cxx/kj-rs-io/ffi.rs:164 ... Broken pipe` is a native
socket write reporting a disconnected peer. The isolated cancellation controls
did not reproduce that exact log signature on either runtime. The upgrade fixes
the demonstrated cancellation defect; fresh unfiltered browser CI must establish
whether the previously observed socket messages also stop. They must not be
hidden with log filtering, changed logging levels or a replacement server.
