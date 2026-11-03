import { fileURLToPath } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * T172: component test configuration.
 *
 * jsdom provides the DOM, the Vue plugin compiles single-file components and the `@` alias
 * mirrors the TypeScript path mapping. No business mocks live here.
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: false,
    // 浏览器端到端用例由 Playwright 运行（tests/e2e），不在单元测试里执行。
    include: ['src/**/*.{test,spec}.ts'],
    exclude: ['tests/e2e/**', 'node_modules/**'],
    clearMocks: true,
    restoreMocks: true,
  },
})
