import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {componentStates, recipes, stateNames, validateStateMatrix} from '../scripts/component-state-contract.mjs';

test('current component catalog has an explicit eight-state contract with no extra or omitted seeds', () => {
  assert.equal(validateStateMatrix(recipes()), 24);
  const source = readFileSync('scripts/component-state-contract.mjs', 'utf8');
  for (const recipe of recipes()) assert.ok(source.includes("case '" + recipe.slug + "':"), recipe.slug + ' has a runnable behavioral oracle');
  for (const state of stateNames) assert.ok(readFileSync('docs/v46/COMPONENT_STATES.md', 'utf8').includes(state), state + ' documented');
});

test('matrix rejects omitted states, omitted components, duplicated rows and unexplained N/A', () => {
  assert.throws(() => validateStateMatrix(recipes(), componentStates.slice(1)), /exactly the current catalog/);
  assert.throws(() => validateStateMatrix(recipes(), [...componentStates, componentStates[0]]), /Duplicate/);
  const missing = structuredClone(componentStates); delete missing[0].states.error;
  assert.throws(() => validateStateMatrix(recipes(), missing), /eight states/);
  const vague = structuredClone(componentStates); vague[0].states.error = {notApplicable: 'N/A'};
  assert.throws(() => validateStateMatrix(recipes(), vague), /concrete assertion/);
});
