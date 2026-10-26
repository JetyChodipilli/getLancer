import {test, expect} from '@playwright/test';
import {recipes, validateStateMatrix, verifyComponentStates, verifyKeyboardFocus} from '../../scripts/component-state-contract.mjs';

const catalog = recipes();
validateStateMatrix(catalog);
for (const recipe of catalog) test('component state contract: ' + recipe.slug, async ({page}) => {
  // Render the actual exported package HTML, preserving its own CSP and local script.
  await verifyComponentStates(page, recipe);
});

test('keyboard oracle rejects a visible control excluded from Tab navigation', async ({page}) => {
  await page.goto('about:blank');
  await page.setContent('<style>button:focus-visible{outline:2px solid #18202d}</style><button tabindex="-1">Skipped control</button><button>Reachable control</button>');
  await expect(verifyKeyboardFocus(page, 'negative keyboard control')).rejects.toThrow(/keyboard traversal/);
});
