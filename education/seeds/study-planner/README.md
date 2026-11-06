# Persistent local study planner

Loopback web UI and JSON API plan study minutes, mark tasks done and persist a bounded local task file. No account system or hosted deployment.

## Reproduce from a clean extraction

Use stable Node.js 24.22.2 or later 24.x with no third-party installation. In this package directory:

```sh
node test.mjs
node server.mjs
```

Open http://127.0.0.1:3101. The default planner-data.json is created with private mode0600 on supporting systems; stop the server before editing it. Regression uses a separate temporary file, closes the server and removes the file.

## What is measured

Two concurrent writes create distinct tasks, marking one done changes remaining study time, and a restart keeps the data. Tests include positive/negative HTTP responses. No actual study productivity is measured.

Regression also splits UTF-8 titles at every interior byte of two-byte and four-byte characters, checks malformed text and the exact 4096-byte bound, and reopens the persisted titles. New tasks take the first unused ID from 1-100, so a valid sparse file containing ID 100 remains usable after creation and restart. A full file rejects creation without changing its bytes.

## Negative controls and bounds

One process per file, maximum100 tasks, 4096-byte JSON body, 100-character title, minutes1-600. Cross-origin mutations, unexpected fields and corrupt persisted records fail closed. The browser renders input through textContent.

## Rights and provenance

Task records entered by the learner remain in their private local file and are not bundled. Code is original under MIT LICENSE. Keep all permission and copyright notices. No real personal records or external content is included.

## Limitations

No authentication, concurrent multi-process writes, database, backups, multi-user isolation or hosted production deployment. No authorship, academic marks or university certification is transferred. This source-only catalogue listing is illustrative; reproducing a local package does not prove a hosted runtime, real-world model accuracy or production readiness.
