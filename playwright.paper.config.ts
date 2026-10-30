import {defineConfig} from '@playwright/test';

const baseURL = process.env.PAPER_BASE_URL || 'http://127.0.0.1:3000';
const port = new URL(baseURL).port || '3000';
const localChromium = !process.env.CI && process.env.PAPER_CHROMIUM_EXECUTABLE;
const localFirefox = !process.env.CI && process.env.PAPER_FIREFOX_EXECUTABLE;
const firefoxOptions = localFirefox ? {launchOptions:{executablePath:localFirefox}} : {};
const chromiumOptions = localChromium ? {launchOptions: {
  executablePath: localChromium,
  args: ['--no-sandbox', '--use-gl=angle', '--use-angle=swiftshader', '--disable-dev-shm-usage'],
}} : {};

// A bounded critical-path suite complements the full historical Chromium suite.
// Firefox and WebKit run the same public/auth/layout checks without pretending
// that browser mocks exercise the Java service or external OAuth providers.
export default defineConfig({
  testDir: './tests/browser',
  testMatch: process.env.PAPER_REVIEW_LEGACY === 'true'
    ? /(?:paper-pixel|spectral|liquid-design)\.spec\.ts/
    : 'paper-pixel.spec.ts',
  fullyParallel: false,
  workers: process.env.CI ? 2 : 1,
  retries: 0,
  timeout: 45000,
  expect: {timeout: 10000},
  outputDir: 'test-results/paper-pixel',
  reporter: [['list'], ['html', {outputFolder: 'playwright-report/paper-pixel', open: 'never'}]],
  use: {baseURL, trace: 'retain-on-failure', screenshot: 'only-on-failure'},
  projects: [
    {name: 'paper-desktop', use: {browserName: 'chromium', ...chromiumOptions, viewport: {width: 1440, height: 1000}}},
    {name: 'paper-tablet', use: {browserName: 'chromium', ...chromiumOptions, viewport: {width: 768, height: 1024}, hasTouch: true}},
    {name: 'paper-phone', use: {browserName: 'chromium', ...chromiumOptions, viewport: {width: 375, height: 812}, isMobile: true, hasTouch: true}},
    {name: 'paper-firefox', grep: /@cross-browser/, use: {browserName: 'firefox', ...firefoxOptions, viewport: {width: 1440, height: 1000}}},
    {name: 'paper-webkit', grep: /@cross-browser/, use: {browserName: 'webkit', viewport: {width: 375, height: 812}, isMobile: true, hasTouch: true}},
  ],
  webServer: {
    command: `npm run start -- --port ${port}`,
    url: baseURL,
    reuseExistingServer: !process.env.CI && process.env.PAPER_REUSE_SERVER === 'true',
    timeout: 120000,
    env: {DEMO_MODE: 'true', BACKEND_URL: ''},
  },
});
