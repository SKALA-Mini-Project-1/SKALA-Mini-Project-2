import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const proxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8081'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: true
      }
    }
  }
});
