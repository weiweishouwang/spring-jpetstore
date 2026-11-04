import { fileURLToPath } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

/**
 * T396: Vite 开发与构建配置。
 *
 * <p>开发时 /api 由 Vite 代理到本地后端，浏览器看到的始终是同源请求，因此 Cookie 与 CSRF
 * 语义与生产一致（plan §3.1、§5.2）。构建产物输出到 dist，后端打包时复制进静态资源
 * （plan §9.3）。</p>
 *
 * <p>公开目录只引用受控的展示资产，不引入任何 XML 或 SQL 文件（NFR-05）。</p>
 */

// 后端端口沿用 .env.example 的约定（BACKEND_PORT/FRONTEND_PORT）；默认 8080。
const backendPort = process.env.BACKEND_PORT ?? '8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: `http://127.0.0.1:${backendPort}`,
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: false,
  },
})
