import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";
import jsxA11y from "eslint-plugin-jsx-a11y";
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
]);
