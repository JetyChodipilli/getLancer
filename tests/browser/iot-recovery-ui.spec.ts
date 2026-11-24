import { expect, test } from '@playwright/test';

test('IoT validation reveals a collapsed topic and supports correcting and retrying telemetry', async ({ page }) => {
  await page.goto('/labs/iot');
  const publish = page.getByRole('button', { name: 'Publish readings' });
  const topicDisclosure = page.getByText('Session topic', { exact: true });
  const topic = page.getByLabel('Publish topic');
  const sequence = page.getByTestId('iot-sequence');

  await expect(publish).toBeEnabled();
  const assigned = await page.getByTestId('iot-assigned-topic').innerText();
  await topicDisclosure.click();
  await topic.fill('another-session/telemetry');
  await topicDisclosure.click();
  await expect(topic).toBeHidden();

  await publish.click();
  const summary = page.getByRole('alert');
  await expect(summary).toBeFocused();
  await expect(topic).toBeVisible();
  await expect(topic).toHaveAttribute('aria-invalid', 'true');
  await expect(sequence).toHaveText('0');
  await summary.getByRole('link', { name: 'Use the exact topic assigned to this session.' }).click();
  await expect(topic).toBeFocused();

  await topic.fill(assigned);
  await page.getByLabel('Temperature').fill('34.5');
  await publish.click();
  await expect(summary).toHaveCount(0);
  await expect(sequence).toHaveText('1');
  await expect(page.getByTestId('iot-state-temperatureC')).toContainText('34.5');
  await expect(page.getByTestId('iot-actuator')).toHaveText('ON');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
