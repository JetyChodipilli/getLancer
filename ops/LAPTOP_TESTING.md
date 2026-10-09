# Laptop testing without a cloud provider

Use Docker Desktop with the WSL 2 backend on Windows, or Docker Desktop on macOS / Docker Engine with Compose v2 on Linux. Docker is the local execution environment; no Fly.io, AWS, Supabase or hosted sandbox account is needed. Install Node 22.18+ for the frontend. Docker Desktop installation: https://docs.docker.com/desktop/setup/install/windows-install/; WSL 2 setup: https://docs.docker.com/desktop/features/wsl/.

## Run the application

From the repository root:

```sh
npm ci
npm run setup:docker
npm run services:docker
npm run dev:local
```

Open http://localhost:3000. Docker runs Java/Spring Boot, PostgreSQL on localhost:5433, a local email inbox at http://localhost:8025 and private object storage. The frontend runs on your laptop. The first build needs internet access to download dependencies and images; the application services run locally afterward. Follow [DOCKER_LOCAL.md](DOCKER_LOCAL.md) for existing passwords, administrator setup, optional OAuth and preserving database volumes. `npm run stop:docker` stops these services without deleting their data.

## Execute all nine learning labs

Run this separate command from the repository root:

```sh
npm run labs:docker
```

It builds a local image containing JDK 17, Node 22, Python 3.12 and actual Redis 7.2.16 from the pinned reviewed source commit. It then executes the existing real HTTP matrix: Java, TypeScript and Python each test Redis caching, synthetic authorization and the payment emulator. Successful execution prints three `SCENARIO_MATRIX_OK` lines followed by `NINE_LAB_MATRIX_OK`; a failure returns a nonzero exit code. A fresh run gets new UUIDs and disposable Redis state. No merchant or account credentials are used and no payments occur.

The test container has no external network, published ports, host directory mounts or Docker socket. It runs as an unprivileged user with a read-only filesystem, bounded temporary storage and resource limits, and is removed when the command finishes. It neither connects to nor resets the application's database. Initial image construction needs internet access; subsequent cached runs need no cloud connection. To inspect or modify individual examples, follow [the native lab setup](../labs/README.md).

Open http://localhost:3000/labs/scenarios to inspect the source downloads and recorded evidence. This browser workbench is a replay inspector. The Docker command runs real scenarios in its terminal; it does not wire the browser's live hosted workbench to Docker or produce an isolation certificate. `APP_LABS_ENABLED=false` remains the default. Full hosted execution still requires the provider gateway, immutable images, operational checks and user research in [the acceptance handoff](../docs/v48/ACCEPTANCE_HANDOFF.md).

## Future hosted provider

For an initial hosted lab deployment, evaluate Fly.io Machines: its VM lifecycle API is a useful basis for disposable per-run environments. See https://docs.fly.io/machines/overview and https://docs.fly.io/machines/guides-examples/network-policies. This is a recommendation for later integration, not a configured or admitted provider. No paid resources are created by this setup. Provider selection does not replace getLancer's gateway, network controls, lease cleanup, image review or actual startup measurements.
