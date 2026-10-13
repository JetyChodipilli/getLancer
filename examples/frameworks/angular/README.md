# getLancer original Angular component

Original MIT source. Synthetic local state only; no account, network, payment, analytics or storage integration. Framework names identify the runtime; they do not imply endorsement.

## Local setup
Node 22.13+ and npm are required. From this extracted directory:

    npm ci --ignore-scripts --no-audit --no-fund
    npm run build

Open `dist/native.js` only through an HTTP page that mounts it; the application builder wraps the reviewed bundle in a self-contained document. A local HTTP server can mount `<div id="app"></div>` (Angular uses `<native-preview></native-preview>`), load `dist/native.css`, then `<script type="module" src="./dist/native.js"></script>`. Native framework compilation occurs during the trusted CI job, never on user upload or page request. The parent script rebuilds and checks deterministic preview and source checksums.

## Rights and dependency costs
`LICENSE` applies to this original component and build script. Frameworks, compiler, TypeScript, esbuild and their transitive libraries retain their own licenses; see `THIRD_PARTY_NOTICES.md`, `THIRD_PARTY_LICENSES.md` and the exact versions/integrity in `package-lock.json`. npm installation downloads those dependencies; no remote runtime CDN is used. Node/npm installation, internet bandwidth and your local computer costs are excluded. Sources are free; no subscription or paid service is required.

## Boundaries
The published iframe is an opaque-origin sandbox with only script execution. The exact generated script and style hashes are allowed; network, forms, popups, downloads, workers, frames and top navigation are denied. Saving and changing controls demonstrates local state only. Reloading discards that state. The static build is reviewed code, not arbitrary uploaded code or a hosted backend.
