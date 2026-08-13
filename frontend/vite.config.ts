/// <reference types="vitest" />
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import eslint from '@nabla/vite-plugin-eslint'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), eslint()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
    exclude: ['node_modules', 'dist', '.idea', '.git', '.cache', 'e2e/**', 'e2e-live/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      exclude: [
        'node_modules/**',
        'dist/**',
        'e2e/**',
        '**/*.config.*',
        '**/setupTests.*',
        '**/*.d.ts',
        // Bootstrap entry point — mounts React to the DOM, nothing to unit test.
        'src/main.tsx',
      ],
      thresholds: {
        // Aligned to the real measured coverage after closing item 13's test
        // gaps (2026-07-28) — see CLAUDE.md and backup/DECISIONS.md §4.1 for
        // the rationale. `functions` dropped from 88 to 84 specifically
        // because App.tsx's ~20 React.lazy() route factories are only
        // "covered" when that exact route is visited in a test; App.test.tsx
        // exercises the auth/routing gate (its actual responsibility), not
        // every lazy-loaded page — that would just duplicate each page's own
        // test suite.
        statements: 90,
        branches: 80,
        functions: 84,
        lines: 92,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      // User management must go through the gateway (needs HMAC + role injection)
      '/api/v1/auth/users': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // Direct to auth-service for login/register/me (no gateway HMAC needed)
      '/api/v1/auth': {
        target: 'http://localhost:8087',
        changeOrigin: true
      },
      // All other API calls through the gateway
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
