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
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      // Direct paths to known microservices (before API Gateway)
      '/api/v1/auth': {
        target: 'http://localhost:8087',
        changeOrigin: true
      },
      '/api/v1/fb': {
        target: 'http://localhost:8086',
        changeOrigin: true
      },
      // Fallback for an API Gateway or default port
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
