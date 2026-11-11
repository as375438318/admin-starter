import { defineConfig, devices } from '@playwright/test';

/**
 * E2E 配置（ADR-006）。
 * - 只跑 `e2e/**`，单元测试归 Vitest，两者互不包含。
 * - 默认用本机已安装的 Chrome（`channel: 'chrome'`），不下载 Playwright 浏览器；
 *   CI 里如需自带浏览器，设 `E2E_CHANNEL=chromium` 并先 `npx playwright install chromium`。
 * - 前置：后端需在 8080 运行（E2E 会先探活，不通则整组 skip 并给出提示）。
 */
const PORT = Number(process.env.E2E_PORT ?? 5173);
const BASE_URL = process.env.E2E_BASE_URL ?? `http://127.0.0.1:${PORT}`;
const channel = process.env.E2E_CHANNEL ?? 'chrome';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: BASE_URL,
    channel: channel === 'none' ? undefined : (channel as 'chrome'),
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  webServer: process.env.E2E_NO_WEBSERVER
    ? undefined
    : {
        command: 'pnpm dev',
        url: BASE_URL,
        reuseExistingServer: true,
        timeout: 120_000,
      },
  projects: [{ name: 'chrome', use: { ...devices['Desktop Chrome'] } }],
});
