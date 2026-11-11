import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';

/**
 * 单元/组件测试配置（ADR-006）。
 * - 与 vite.config.ts 共用同一套 alias，避免测试里出现第二份路径映射。
 * - 只扫 `src/**\/*.spec.ts`，`e2e/**` 归 Playwright，两者互不包含。
 * - 版本约束：vitest 锁在 3.x（vitest 5 的 peer 要求 vite ≥6，会倒逼升级 Vite，见 ADR-006）。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@admin-starter/shared': fileURLToPath(new URL('../shared/src/index.ts', import.meta.url)),
      '@admin-starter/ui-tokens': fileURLToPath(new URL('../../packages/ui-tokens', import.meta.url)),
    },
  },
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.spec.ts'],
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
    restoreMocks: true,
    clearMocks: true,
    // 覆盖率按 frontend-spec §15.2 分层推进，先只统计 src，不含测试与入口
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**/*.{ts,vue}'],
      exclude: ['src/**/*.spec.ts', 'src/main.ts', 'src/api/types.ts'],
    },
  },
});
