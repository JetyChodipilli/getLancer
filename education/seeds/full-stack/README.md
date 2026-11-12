# Task-board API

Original dependency-free educational exercise. It is source only; no live platform review, hosted runtime or production purchase is implied.

## Setup

Install stable Node.js 24.22.2 or later 24.x. No packages or credentials are required. Approximate setup: five minutes after the runtime is installed.

Run the regression first:

```sh
node test.mjs
node server.mjs
```

## Included and excluded

HTTP task creation and completion in local memory. No database, authentication, frontend or hosted production deployment.

Requests accept at most 4096 UTF-8 bytes. The regression checks split multibyte titles, invalid UTF-8, exact body bounds and startup from a directory containing spaces.

The CLI defaults to 127.0.0.1:3100. Set the PORT environment variable to an integer from 0 to 65535 to choose another port; 0 asks the operating system for a free port. The CLI prints the actual bound URL. Its regression uses PORT=0 only in the child process, allowing parallel source and extracted-package checks.

## License and support

MIT, preserving LICENSE and NOTICE. This self-guided example includes no maintenance contract, paid support, university certification, marks or authorship transfer.
