import {test} from '@playwright/test';
import {recipes, validateStateMatrix, verifyComponentStates} from '../../scripts/component-state-contract.mjs';

const catalog = recipes();
validateStateMatrix(catalog);
for (const recipe of catalog) test('component state contract: ' + recipe.slug, async ({page}) => {
  // Render the actual exported package HTML, preserving its own CSP and local script.
  await verifyComponentStates(page, recipe);
});
