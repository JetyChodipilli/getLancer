# Curated component setup reproduction

All 24 curated source packages are released as version 1.0.1. The source catalogue and metadata contain the new HTML digests; historical database release snapshots and their source, versions and hashes are not rewritten. Version 1.0.1 adds visible hover/pressed feedback and complete keyboard-focus selectors, corrects the required screen-count field in Estimate form, and supplies concrete startup instructions in each ZIP.

The package verification baseline is Chromium 145.0.7632.6 from locked Playwright revision 1208 and Python 3.12 for the optional loopback HTTP server. Exact browser and Python patch versions, the HTML SHA-256 and the actual downloaded ZIP SHA-256 for every package are recorded in `component-setup-report.json` by the real passing CI execution. A declaration of this baseline does not itself establish a successful run.

`tests/browser/component-setup.spec.ts` visits every public component page, clicks Download source, verifies the filename and reads the actual completed download bytes. It independently decodes and CRC-checks each ZIP with Python's standard `zipfile`, compares all three extracted files byte-for-byte, and opens the extracted HTML in a fresh browser context through both a `file:` URL and the README's Python HTTP server. It verifies the rendered document, working controls, no JavaScript errors, no external dependencies and cleanup. The server binds to 127.0.0.1 and uses a free port as explicitly permitted by the README. The browser test attaches the exact runtime/package report to each project result. Interaction-state coverage is a separate test; these setup checks do not replace human accessibility acceptance.

Run static package validation with:

```sh
node --test tests/component-setup.test.mjs tests/components.test.mjs
```

Run the complete browser reproduction with the repository's installed browser:

```sh
npx playwright test tests/browser/component-setup.spec.ts --project=desktop --workers=1
```

The standalone runner generates archives from reviewed source and labels that acquisition in its report; it does not prove public UI downloads. For a standalone run, `node scripts/verify-component-setup.mjs` launches the installed Playwright Chromium. `COMPONENT_SETUP_REPORT` selects an output JSON path. A separately installed compatible executable can be selected with `COMPONENT_SETUP_BROWSER_EXECUTABLE`; its actual version must still match the documented browser baseline.

The local editing environment contains Python 3.12.14. Its downloaded Google Chrome 156.0.8078.47 could report its version, but browser launch was denied by the host's socket restriction. It is not recorded as successful browser reproduction. Current-source CI must supply the actual browser result before software acceptance is claimed.
