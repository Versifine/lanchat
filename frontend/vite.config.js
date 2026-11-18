import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发态把 /api 与 /ws 代理到后端 8081（8080 被本机 WeKnora 服务占用），
// 前端 5173 独立运行（前后端分离）。生产态由后端同端口托管 dist，无需代理。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8081', ws: true }
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
})
