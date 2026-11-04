import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";
import jsxA11y from "eslint-plugin-jsx-a11y";
import boundaries from "eslint-plugin-boundaries";
import reactPerf from "eslint-plugin-react-perf";
import { defineConfig, globalIgnores } from "eslint/config";

export default defineConfig([
  globalIgnores(["dist", "coverage"]),
  jsxA11y.flatConfigs.strict,
  {
    files: ["**/*.{ts,tsx}"],
    plugins: {
      "react-perf": reactPerf,
    },
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    rules: {
      ...reactPerf.configs.recommended.rules,
      // T-FE-01: statically ban XSS sinks — React JSX escaping is the only safe output path
      'no-restricted-syntax': [
        'error',
        {
          selector: 'JSXAttribute[name.name="dangerouslySetInnerHTML"]',
          message: '[T-FE-01] dangerouslySetInnerHTML is forbidden — use React JSX expressions to prevent XSS.',
        },
        {
          selector: 'AssignmentExpression[left.property.name="innerHTML"]',
          message: '[T-FE-01] Direct innerHTML assignment is forbidden — use React JSX expressions to prevent XSS.',
        },
        {
          selector: 'AssignmentExpression[left.property.name="outerHTML"]',
          message: '[T-FE-01] Direct outerHTML assignment is forbidden — use React JSX expressions to prevent XSS.',
        },
      ],
    },
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
  },
  {
    // Structure rules (report-only: severity "warn"; raise to "error" once the
    // baseline is clean). Run on its own with `npm run lint:boundaries`.
    files: ["src/**/*.{ts,tsx}"],
    plugins: { boundaries },
    settings: {
      "import/resolver": {
        typescript: { alwaysTryTypes: true, project: "./tsconfig.app.json" },
      },
      "boundaries/ignore": [
        "**/*.test.{ts,tsx}",
        "src/test-utils/**",
        "src/setupTests.ts",
        "src/pages/index.ts",
      ],
      "boundaries/elements": [
        // A page is either pages/<Name>.tsx or the folder pages/<Name>/.
        { type: "page", mode: "full", capture: ["page"], pattern: ["src/pages/(*)/**/*", "src/pages/(*).{ts,tsx}"] },
        { type: "component", mode: "full", pattern: "src/components/**/*" },
        { type: "types", mode: "full", pattern: "src/types/**/*" },
        { type: "utils", mode: "full", pattern: "src/utils/**/*" },
        { type: "lib", mode: "full", pattern: "src/lib/**/*" },
        { type: "services", mode: "full", pattern: "src/services/**/*" },
        { type: "hooks", mode: "full", pattern: "src/hooks/**/*" },
        { type: "store", mode: "full", pattern: "src/store/**/*" },
      ],
    },
    rules: {
      "boundaries/dependencies": ["warn", {
        default: "allow",
        rules: [
          {
            from: { type: "page" },
            disallow: { to: { type: "page", captured: { page: "!{{ from.captured.page }}" } } },
            message: "Pages must not import other pages ({{from.captured.page}} -> {{to.captured.page}}): move the shared code to components/ or lib/",
          },
          {
            from: { type: "component" },
            disallow: { to: { type: "page" } },
            message: "components must not import pages",
          },
          {
            from: { type: ["types", "utils", "lib"] },
            disallow: { to: { type: ["page", "component", "services", "hooks", "store"] } },
            message: "types/utils/lib are leaves: they must not import pages, components, services, hooks or store",
          },
        ],
      }],
    },
  },
]);
