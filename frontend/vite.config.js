import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { mockPlugin } from './vite.mocks.js'

const isMock = process.env.VITE_MOCK === 'true'

export default defineConfig({
  plugins: [react(), ...(isMock ? [mockPlugin()] : [])],
  server: {
    port: 5173,
    strictPort: true,
    proxy: isMock ? {} : {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
})
