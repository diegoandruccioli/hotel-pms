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
    exclude: ['node_modules', 'dist', '.idea', '.git', '.cache', 'e2e/**'],
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
      ],
      thresholds: {
        statements: 90,
        branches: 80,
        functions: 88,
        lines: 92,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
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
