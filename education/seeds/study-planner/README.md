# Persistent local study planner

Loopback web UI and JSON API plan study minutes, mark tasks done and persist a bounded local task file. No account system or hosted deployment.

## Reproduce from a clean extraction

Use Node.js 22 or newer with no third-party installation. In this package directory:

```sh
node test.mjs
node server.mjs
```

Open http://127.0.0.1:3101. The default planner-data.json is created with private mode0600 on supporting systems; stop the server before editing it. Regression uses a separate temporary file, closes the server and removes the file.

## What is measured

Two concurrent writes create distinct tasks, marking one done changes remaining study time, and a restart keeps the data. Tests include positive/negative HTTP responses. No actual study productivity is measured.

## Negative controls and bounds

One process per file, maximum100 tasks, 4096-byte JSON body, 100-character title, minutes1-600. Cross-origin mutations, unexpected fields and corrupt persisted records fail closed. The browser renders input through textContent.

## Rights and provenance

Task records entered by the learner remain in their private local file and are not bundled. Code is original under MIT LICENSE. Keep all permission and copyright notices. No real personal records or external content is included.

## Limitations

No authentication, concurrent multi-process writes, database, backups, multi-user isolation or hosted production deployment. No authorship, academic marks or university certification is transferred. This source-only catalogue listing is illustrative; reproducing a local package does not prove a hosted runtime, real-world model accuracy or production readiness.
