import {test, expect} from '@playwright/test';
import {verifyComponentSetup} from '../../scripts/verify-component-setup.mjs';

test('all24 downloaded component ZIPs reproduce their README file and Python HTTP setup', async ({browser}, testInfo) => {
  test.setTimeout(180_000);
  const reportPath = testInfo.outputPath('component-setup-report.json');
  const report = await verifyComponentSetup({browser, reportPath});
  expect(report.packages).toHaveLength(24);
  expect(new Set(report.packages.map(item => item.slug)).size).toBe(24);
  await testInfo.attach('component-setup-runtime-evidence', {path: reportPath, contentType: 'application/json'});
});
