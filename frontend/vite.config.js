import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发态把 /api 与 /ws 代理到后端 8080，前端 5173 独立运行（前后端分离）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true }
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
})
