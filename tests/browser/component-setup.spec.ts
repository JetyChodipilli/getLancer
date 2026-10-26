import {test, expect} from '@playwright/test';
import {readFile} from 'node:fs/promises';
import {verifyComponentSetup} from '../../scripts/verify-component-setup.mjs';

test('all24 downloaded component ZIPs reproduce their README file and Python HTTP setup', async ({browser, page}, testInfo) => {
  test.setTimeout(240_000);
  const reportPath = testInfo.outputPath('component-setup-report.json');
  const downloads: string[] = [];
  const report = await verifyComponentSetup({browser, reportPath, archiveProvider: async (item: {slug: string; title: string; version: string}) => {
    await page.goto('/components/' + encodeURIComponent(item.slug));
    await expect(page.getByRole('heading', {name: item.title, exact: true})).toBeVisible();
    const pending = page.waitForEvent('download');
    await page.getByRole('button', {name: 'Download source', exact: true}).click();
    const download = await pending;
    expect(await download.failure()).toBeNull();
    expect(download.suggestedFilename()).toBe(item.slug + '-' + item.version + '.zip');
    const downloadedPath = await download.path();
    expect(downloadedPath).not.toBeNull();
    downloads.push(item.slug);
    return readFile(downloadedPath!);
  }});
  expect(report.archiveSource).toBe('supplied-archive');
  expect(report.packages).toHaveLength(24);
  expect(new Set(report.packages.map(item => item.slug)).size).toBe(24);
  expect(downloads).toEqual(report.packages.map(item => item.slug));
  await testInfo.attach('component-setup-runtime-evidence', {path: reportPath, contentType: 'application/json'});
});
