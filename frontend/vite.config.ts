import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 默认端口避开被其他项目占用的 5173；可用环境变量 FRONTEND_PORT 覆盖
    port: Number(process.env.FRONTEND_PORT) || 15173,
    // 显式绑定 127.0.0.1：Vite 默认只监听 ::1，会导致 127.0.0.1 无法访问
    host: '127.0.0.1',
    // 后端默认 18080；可用环境变量 BACKEND_PORT 覆盖（需与 application.yml 的 SERVER_PORT 一致）
    proxy: {
      '/api': `http://localhost:${Number(process.env.BACKEND_PORT) || 18080}`,
    },
  },
})
