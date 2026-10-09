/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En desarrollo el backend corre en 8080; en Docker nginx hace el mismo proxy.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'happy-dom',
    setupFiles: ['./src/setupTests.ts'],
  },
})
