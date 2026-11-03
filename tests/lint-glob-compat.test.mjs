import assert from "node:assert/strict";
import { createRequire } from "node:module";
import test from "node:test";
import { ESLint, Linter } from "eslint";

// Resolve the plugins from the same package that supplies the production config.
const requireNext = createRequire(import.meta.resolve("eslint-config-next"));
const importPlugin = requireNext("eslint-plugin-import");
const reactPlugin = requireNext("eslint-plugin-react");
const a11yPlugin = requireNext("eslint-plugin-jsx-a11y");

function lint(source, rules) {
  const messages = new Linter().verify(source, {
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    plugins: { import: importPlugin, react: reactPlugin, "jsx-a11y": a11yPlugin },
    settings: { react: { version: "19.2" } },
    rules,
  }, { filename: "lint-glob-fixture.js" });
  assert.equal(messages.filter((message) => message.fatal).length, 0, JSON.stringify(messages));
  return messages;
}

test("ESLint keeps file and ignore globs working with the patched matcher", async () => {
  const eslint = new ESLint({
    overrideConfigFile: true,
    overrideConfig: [
      { ignores: ["**/generated/**"] },
      { files: ["**/lint-fixtures/**/*.{js,jsx}"], rules: { "no-debugger": "error" } },
    ],
  });
  const [included] = await eslint.lintText("debugger;", { filePath: "lint-fixtures/nested/example.jsx" });
  assert.equal(included.errorCount, 1);
  assert.equal(included.messages[0].ruleId, "no-debugger");
  assert.equal(await eslint.isPathIgnored("lint-fixtures/generated/example.jsx"), true);
});

test("import/no-namespace matches ignored basenames and still rejects other imports", () => {
  const messages = lint('import * as allowed from "nested/allowed-library"; import * as blocked from "nested/blocked-library";', {
    "import/no-namespace": ["error", { ignore: ["allowed-*"] }],
  });
  assert.equal(messages.length, 1);
  assert.equal(messages[0].ruleId, "import/no-namespace");
  assert.equal(lint('import * as allowed from "nested/allowed-library";', {
    "import/no-namespace": ["error", { ignore: ["allowed-*"] }],
  }).length, 0);
});

test("import/no-internal-modules enforces both glob allow and forbid lists", () => {
  const source = 'import { jsx } from "react/jsx-runtime";';
  assert.equal(lint(source, { "import/no-internal-modules": ["error", { allow: ["react/**"] }] }).length, 0);
  const forbidden = lint(source, { "import/no-internal-modules": ["error", { forbid: ["react/**"] }] });
  assert.equal(forbidden.length, 1);
  assert.equal(forbidden[0].ruleId, "import/no-internal-modules");
  const disallowed = lint(source, { "import/no-internal-modules": ["error", { allow: ["other/**"] }] });
  assert.equal(disallowed.length, 1);
  assert.equal(disallowed[0].ruleId, "import/no-internal-modules");
});

test("React prop globs keep allowed components and forbidden components distinct", () => {
  const messages = lint('const example = <><AllowedControl className="x" /><OtherControl className="x" /></>;', {
    "react/forbid-component-props": ["error", { forbid: [{ propName: "className", allowedForPatterns: ["Allowed*"] }] }],
  });
  assert.equal(messages.length, 1);
  assert.equal(messages[0].ruleId, "react/forbid-component-props");
});

test("a11y nested control globs recognize custom controls without hiding missing controls", () => {
  const rules = { "jsx-a11y/label-has-associated-control": ["error", { assert: "nesting", controlComponents: ["Field*"] }] };
  assert.equal(lint('const example = <label>Username<FieldText /></label>;', rules).length, 0);
  const messages = lint('const example = <label>Username<span /></label>;', rules);
  assert.equal(messages.length, 1);
  assert.equal(messages[0].ruleId, "jsx-a11y/label-has-associated-control");
  assert.match(messages[0].message, /associated with a control/);
});

test("a11y accessible label globs distinguish empty controls from label components", () => {
  const rules = { "jsx-a11y/label-has-associated-control": ["error", { assert: "nesting", controlComponents: ["Field*"] }] };
  const messages = lint('const example = <label><FieldText /></label>;', rules);
  assert.equal(messages.length, 1);
  assert.match(messages[0].message, /accessible text/);
  assert.equal(lint('const example = <label><TextLabel /><FieldText /></label>;', rules).length, 0);
});
