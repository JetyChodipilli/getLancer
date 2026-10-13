# V5.1: protocol simulation and native language coverage

V5.1 implements all eight S07 backlog items from the full V4.5–V5.2 roadmap.
The implementation contract is [CONTRACT.md](CONTRACT.md); observed acceptance is
recorded in [VALIDATION.md](VALIDATION.md).

| Slice | Delivered behavior | Source and evidence |
| --- | --- | --- |
| S07-B01 | Original C++/MicroPython firmware, BOM, signal wiring and per-run protocol contract | `labs/iot`; [IoT](IOT.md) |
| S07-B02 | Real local MQTT broker/device and HTTP bridge; bounded packets, run isolation, disconnect/recovery and cleanup | `labs/iot/runtime.mjs`; `scripts/verify-iot.mjs` |
| S07-B03 | Interactive browser-local telemetry, returned actuator state, raw messages and session history | `/labs/iot`; [Frontend](FRONTEND.md) |
| S07-B04 | Native Go cache, authorization and payment emulator | `labs/go`; [Go](GO.md) |
| S07-B05 | Native ASP.NET Core cache, authorization and payment emulator | `labs/dotnet`; [C#/.NET](DOTNET.md) |
| S07-B06 | Persistent native PHP cache, authorization and payment emulator | `labs/php`; [PHP](PHP.md) |
| S07-B07 | Original Vue, Svelte and Angular components, trusted native builds and isolated static previews | `/components/frameworks`; `examples/frameworks`; [Frontend](FRONTEND.md) |
| S07-B08 | Eighteen core labs across six languages; explicit signed adapter/version/retirement policy and source-only availability | `labs/catalogue.json`; [Backend](BACKEND.md) |

Each core language implements the same three reviewed contracts, using real
Redis 7.2.16 for cache and public synthetic identities/payment signatures. Source
downloads are reconstructed from an explicit bounded inventory and executed from
fresh extraction directories. The shared verifier compiles Go/.NET once and
directly supervises the native process for reliable termination.
The read-only local container has a bounded executable `/native` tmpfs for these
trusted compiled fixtures; ordinary `/tmp` remains non-executable. Restore,
compiler caches and assemblies stay in disposable per-verification directories.

The IoT browser model and local MQTT runtime are labelled separately. Local MQTT
uses real loopback wire packets and a software device; the firmware is free
source for optional physical work. No dated physical-device proof was supplied.
The original examples neither operate physical actuators nor accept arbitrary
network destinations. Hardware and optional dependency costs are excluded.

Framework examples compile with the actual Vue, Svelte and Angular toolchains.
Source archives include pinned lockfiles and complete runtime notices. The app
does not ship compilers or enable arbitrary uploaded code. Each preview retains
an exact script/style hash CSP and an opaque sandbox origin with no network,
account storage, parent access, popups or unreviewed scripts. The ordinary app
continues using its request nonce policy.

Hosted admission remains a separate operator-controlled deployment gate.
`APP_IOT_ENABLED=false` is independent of `APP_LABS_ENABLED`; supported native
metadata does not replace signed provider evidence, source/image binding,
reservations or confirmed cleanup. Unsupported, retired, disabled and expired
execution remains unavailable while eligible signed source can remain visible.

The existing Spectral Studio design is preserved. New forms retain visible
labels, 16px inputs, 44px controls, linked validation, keyboard focus and reduced
motion. Current acceptance covers automated Chromium/WCAG checks at desktop,
phone and tablet widths; it does not claim a manual screen-reader or physical
hardware evaluation.
