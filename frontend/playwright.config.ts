import { defineConfig, devices } from '@playwright/test'

/**
 * T403: 真实浏览器测试运行配置。
 *
 * <p>验收跑在真实浏览器与真实后端上：不 mock 整个后端，也不用 H2 之类的替身数据库。每个用例
 * 使用独立的浏览器上下文，避免会话与 Cookie 互相污染；失败时保留截图与追踪文件
 * （plan §10）。</p>
 *
 * <p>就绪等待用真实条件（页面元素与接口响应），不用固定 sleep 掩盖启动慢的问题。</p>
 */
export default defineConfig({
  testDir: './tests/e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  outputDir: '../test-results',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:8081',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [
    {
      name: 'chromium',
      // CI 安装 Playwright 自带的 chromium；本地可用 PLAYWRIGHT_CHANNEL=chrome 复用系统浏览器。
      use: { ...devices['Desktop Chrome'], channel: process.env.PLAYWRIGHT_CHANNEL },
    },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: 'npm run dev -- --port 5173',
        url: 'http://127.0.0.1:5173',
        reuseExistingServer: false,
        timeout: 120_000,
      },
})
