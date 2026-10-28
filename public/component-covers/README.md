# Original component preview covers

These JPEGs show the original MIT-licensed getLancer component recipes in `backend/src/main/resources/catalog/components.json`. They are rendered UI captures, not customer screenshots or proof of real accounts.

Reproduce with `node scripts/capture-component-covers.mjs` after installing the Playwright Chromium browser. Set `COVER_BROWSER` only when using a separately installed compatible headless shell. Every request is blocked while capturing; only the repository's source-pinned HTML is rendered.

`index.json` binds each capture to the original HTML SHA-256 and the exact JPEG SHA-256. `node scripts/verify-component-covers.mjs` validates all bindings in CI. Uploaded and changed source never borrows an unrelated recipe cover.
