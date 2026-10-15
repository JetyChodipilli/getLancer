# Persistent synthetic campus poll

Loopback web UI and JSON API store synthetic ballots with retry identity and three choices. No voter identity checks or real election integrity.

## Reproduce from a clean extraction

Use Node.js 22 or newer with no third-party installation. In this package directory:

```sh
node test.mjs
node server.mjs
```

Open http://127.0.0.1:3102. Default poll-data.json stores only synthetic choice/retry keys. Reopening the page creates a fresh synthetic key; retries in one page keep the key. Tests use and remove a temporary file.

## What is measured

Two simultaneous identical ballots create one saved row; changed choice conflicts. Restart preserves counts [1,1,0] in the regression fixture, and the100-ballot cap blocks another record.

## Negative controls and bounds

Maximum100 ballots, 2048-byte JSON body, 8-64 alphanumeric/hyphen/underscore retry key, choice0-2, exact fields. Host/Origin checks restrict browser writes to local loopback. No real-person data should be entered.

## Rights and provenance

All choices and regression ballots are original synthetic examples; no real voter records. Code is original under MIT LICENSE. Keep all permission and copyright notices. No real personal records or external content is included.

## Limitations

No authentication, ballot secrecy, one-person-one-vote, tamper resistance, cryptographic election verification or campus endorsement. No authorship, academic marks or university certification is transferred. This source-only catalogue listing is illustrative; reproducing a local package does not prove a hosted runtime, real-world model accuracy or production readiness.
