import { createHash } from "node:crypto";
import { existsSync, readFileSync, realpathSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

// Temporary consumer adaptations until the three upstream plugins support
// minimatch 11. All matching uses the genuine integrity-pinned npm package.
// No matching algorithm, rule configuration, or package metadata is replaced.
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(import.meta.url);
const versions = {
  "eslint-plugin-import": "2.32.0",
  "eslint-plugin-react": "7.37.5",
  "eslint-plugin-jsx-a11y": "6.10.2",
};
const patches = [
  {
    "package": "eslint-plugin-import",
    "file": "lib/rules/extensions.js",
    "before": "fe98adecf33a02952d842227014535fc390f4d18c96a4944f4c90783d4af3945",
    "after": "3e408b095b8986e78f77261c2c5a3cc39e9194a81cc9099112333d00c68f70f4",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-import",
    "file": "lib/rules/no-extraneous-dependencies.js",
    "before": "58e174298161b31c7dc4f345543cb2707346f3072a9145f78a0fd5e914484b19",
    "after": "e833104f23cdcd6d98b8c24e574e5f4c188f5771d595dc228512526c2962db88",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-import",
    "file": "lib/rules/no-import-module-exports.js",
    "before": "d83581c24ae4fb0fc00dce02f3deec9e0bdf4cc624bf1b7e374e2aee79d3ba66",
    "after": "b6e666932f0623711504d65efc3e2b664c360ab1a842d795c95a80685f1f9155",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-import",
    "file": "lib/rules/no-internal-modules.js",
    "before": "f36ab9b9b97cc2d8a49f8fe474c4200875db4fa357c5061fe71538b01db696a4",
    "after": "4f2ba066d8199373fa83e7b43afdd888fb666ae45715d8c9c90883be3715c1ca",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ],
      [
        "_minimatch2['default'].makeRe(p)",
        "new _minimatch2['default'].Minimatch(p)",
        2
      ],
      [
        "re.test(importPath)",
        "re.match(importPath)",
        2
      ]
    ]
  },
  {
    "package": "eslint-plugin-import",
    "file": "lib/rules/no-namespace.js",
    "before": "39d2140a547b9825ef630e922b680dc938786258af5029b02f47eea625013d84",
    "after": "ad2cae576c241a4c88d0713c7318028343341ae0d63229c7bcfae6e6685d189d",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-import",
    "file": "lib/rules/no-unassigned-import.js",
    "before": "e565350ae91b62d9c104c51c73ae88d58f4a2ef7caafc83755cf2887bb86d58c",
    "after": "4af9d1677c409d74b1f201420f467c04a1a4ef6e0062095722caa73570e3dab3",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-import",
    "file": "lib/rules/order.js",
    "before": "83fd2c12da1f437288b006abe4f0a9ffc68eca385964e4c1ea014a053730cfe6",
    "after": "bed741750e6b05b131a14b395f89b6e4a97bd3e9d8f69bac1eb0b0d7ba07fdb9",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-react",
    "file": "lib/rules/forbid-component-props.js",
    "before": "2f928750174f7c93846598ba7939cdc0844ebda46954be2c990b056c9ec662b6",
    "after": "69aafb80260296b4a1ca64e6267d60d3797f07217194e41e250e4d1dbd87ca85",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-react",
    "file": "lib/rules/jsx-handler-names.js",
    "before": "2a0ffd32c997da581b9b7acecfec800b126774902fd06c09e08a8803e44155ca",
    "after": "379ddc1d3e762223adfd5e62f176580a70358dc7b8ae18c0a49671dfe478c388",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-react",
    "file": "lib/rules/jsx-pascal-case.js",
    "before": "dc9cdba6bd5897142976cf0ce68bc3cb134c45da8c2883c88c1db9ba933f0f17",
    "after": "e3669c0abc1d05a0be2cdc1cf864ccf3b507f1b1933f657cac48a4d415e756f3",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-react",
    "file": "lib/rules/no-danger.js",
    "before": "61f9fd97581e959d75bfb7ef030e9fb7b74c2df224980bed89f6463d05d2734a",
    "after": "a17bd303d725c422a1ee58af31fee9edd01ca7d2f16d9bd5ce21542362f5b541",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-react",
    "file": "lib/rules/no-unstable-nested-components.js",
    "before": "5ae81f553263438f6fc9e059872fb617519fa8de08777a78a06a738db12313e8",
    "after": "a6506804fe1bac53a6e0bd9ed5db6721079a4f2f3755ebdb8855a4a3d5761524",
    "replacements": [
      [
        "require('minimatch')",
        "require('minimatch').minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-jsx-a11y",
    "file": "lib/util/mayContainChildComponent.js",
    "before": "d2a7fd3e513a42ab781543d6529776950f240249f9367f94899dbc9258e198d0",
    "after": "f6b15d0d9d9d3958bcbe95426e93d53cdc657915f2b83c049c206b8c144ab595",
    "replacements": [
      [
        "require(\"minimatch\")",
        "require(\"minimatch\").minimatch",
        1
      ]
    ]
  },
  {
    "package": "eslint-plugin-jsx-a11y",
    "file": "lib/util/mayHaveAccessibleLabel.js",
    "before": "e765378d87cca48ac64e76967edfb33c52a9423c6822fd048024c88296ba9a2b",
    "after": "903fcc41c4712b87f7c92bb3989591ebb2285b6fbfef7c3ab5da41fdc2fd4839",
    "replacements": [
      [
        "require(\"minimatch\")",
        "require(\"minimatch\").minimatch",
        1
      ]
    ]
  }
];
const digest = (value) => createHash("sha256").update(value).digest("hex");
const json = (path) => JSON.parse(readFileSync(path, "utf8"));
const missingDevPackages = Object.keys(versions).every((name) => !existsSync(resolve(root, "node_modules", name)));
const explicitlyOmittedDev = (process.env.npm_config_omit || "").split(/\s+/).includes("dev");
const omittedDev = explicitlyOmittedDev || (process.env.NODE_ENV === "production" && process.env.CI !== "true");

if (missingDevPackages && omittedDev && process.env.CI !== "true") {
  console.log("Lint glob compatibility: development plugins omitted from production install.");
} else {
  // Resolve from Next's config, exactly as ESLint does. A partially installed or
  // unexpected development tree must fail, including when CI omits dev by mistake.
  const requireNext = createRequire(require.resolve("eslint-config-next"));
  const modulesRoot = `${realpathSync(resolve(root, "node_modules"))}${sep}`;
  const packageRoots = new Map();
  for (const [name, expected] of Object.entries(versions)) {
    const packageJson = requireNext.resolve(`${name}/package.json`);
    if (json(packageJson).version !== expected) {
      throw new Error(`Review lint glob adaptations before installing ${name}: expected ${expected}.`);
    }
    const packageRoot = dirname(realpathSync(packageJson));
    if (!`${packageRoot}${sep}`.startsWith(modulesRoot)) throw new Error(`Unexpected plugin location: ${name}`);
    packageRoots.set(name, packageRoot);
    const requirePlugin = createRequire(packageJson);
    if (json(requirePlugin.resolve("minimatch/package.json")).version !== "11.0.4") {
      throw new Error(`Expected patched minimatch 11.0.4 for ${name}.`);
    }
  }
  // Check every version, full source hash and hunk before writing any file.
  // Repeated npm installs accept only the exact reviewed patched hash.
  const pending = [];
  for (const patch of patches) {
    const path = resolve(packageRoots.get(patch.package), patch.file);
    if (!realpathSync(path).startsWith(`${packageRoots.get(patch.package)}${sep}`)) {
      throw new Error(`Unexpected lint glob source location: ${patch.file}`);
    }
    let source = readFileSync(path, "utf8");
    const current = digest(source);
    if (current === patch.after) continue;
    if (current !== patch.before) throw new Error(`Unreviewed lint glob source: ${patch.package}/${patch.file}`);
    for (const [before, after, count] of patch.replacements) {
      if (source.split(before).length - 1 !== count) throw new Error(`Lint glob hunk mismatch: ${patch.file}`);
      source = source.replaceAll(before, after);
    }
    if (digest(source) !== patch.after) throw new Error(`Lint glob patch hash mismatch: ${patch.file}`);
    pending.push({ path, source });
  }
  for (const { path, source } of pending) writeFileSync(path, source);
  console.log(`Lint glob compatibility: verified ${patches.length} consumer files; adapted ${pending.length}.`);
}
