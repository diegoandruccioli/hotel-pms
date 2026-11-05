/// <reference types="vitest" />
import { defineConfig } from 'vitest/config'
import path from 'path'

// Slim config used only by Stryker (see stryker.config.json): runs just the tests that cover
// src/lib and src/services, without the ESLint/Tailwind/React Vite plugins, so the initial
// dry run stays fast. Not used by `npm test`.
export default defineConfig({
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
    include: ['src/lib/**/*.test.{ts,tsx}', 'src/services/**/*.test.{ts,tsx}'],
  },
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
})
