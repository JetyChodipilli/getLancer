import vinext from "vinext";
import { defineConfig } from "vite";
import hostingConfig from "./.openai/hosting.json";
import { sites } from "./build/sites-vite-plugin";

const SITE_CREATOR_PLACEHOLDER_DATABASE_ID =
  "00000000-0000-4000-8000-000000000000";

const { d1, r2 } = hostingConfig;

// macOS Seatbelt blocks FSEvents, so Codex previews need polling for HMR.
const isCodexSeatbeltSandbox = process.env.CODEX_SANDBOX === "seatbelt";

const localBindingConfig = {
  main: "./worker/index.ts",
  compatibility_flags: ["nodejs_compat"],
  d1_databases: d1
    ? [
        {
          binding: d1,
          database_name: "site-creator-d1",
          database_id: SITE_CREATOR_PLACEHOLDER_DATABASE_ID,
        },
      ]
    : [],
  r2_buckets: r2
    ? [
        {
          binding: r2,
          bucket_name: "site-creator-r2",
        },
      ]
    : [],
};

export default defineConfig(async ({ command }) => {
  const requestedRuntime = process.env.LOCAL_DEV_RUNTIME;
  if (requestedRuntime && !["node", "worker"].includes(requestedRuntime))
    throw new Error("LOCAL_DEV_RUNTIME must be node or worker.");
  const nodeDev = command === "serve" &&
    (requestedRuntime ?? (process.platform === "win32" ? "node" : "worker")) === "node";
  if (nodeDev && (d1 || r2))
    throw new Error("Cloudflare D1/R2 bindings require LOCAL_DEV_RUNTIME=worker.");
  // Keep Wrangler and Miniflare state project-local. These are non-secret tool
  // settings; application environment belongs in ignored `.env*` files.
  process.env.WRANGLER_WRITE_LOGS ??= "false";
  process.env.WRANGLER_LOG_PATH ??= ".wrangler/logs";
  process.env.MINIFLARE_REGISTRY_PATH ??= ".wrangler/registry";

  // Wrangler snapshots its log path while the Cloudflare plugin is imported.
  const workerPlugins = nodeDev ? [] : [
    (await import("@cloudflare/vite-plugin")).cloudflare({
      viteEnvironment: { name: "rsc", childEnvironments: ["ssr"] },
      inspectorPort: false,
      config: localBindingConfig,
    }),
  ];

  return {
    server: {
      host: nodeDev ? "127.0.0.1" : "0.0.0.0",
      allowedHosts: ["terminal.local"],
      watch: {
        ignored: ["**/.tooling/**", "**/backend/target/**", "**/.audit-npm-cache/**"],
        ...(isCodexSeatbeltSandbox ? { useFsEvents: false, usePolling: true } : {}),
      },
    },
    ...(nodeDev ? { optimizeDeps: { exclude: ["cloudflare:workers"] } } : {}),
    plugins: [
      // Existing server helpers catch this unavailable binding and use process.env.
      // Never fabricate a Worker binding in the local Node runtime.
      ...(nodeDev ? [{
        name: "getlancer-local-node-bindings",
        enforce: "pre" as const,
        resolveId(id: string) {
          if (id === "cloudflare:workers") return "\0getlancer-local-node-bindings";
        },
        load(id: string) {
          if (id === "\0getlancer-local-node-bindings")
            return 'throw new Error("Cloudflare bindings require the Worker runtime."); export const env = {};';
        },
      }] : []),
      vinext(),
      sites(),
      ...workerPlugins,
    ],
  };
});
