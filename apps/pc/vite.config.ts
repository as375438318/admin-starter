import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 前端构建配置。
 *
 * 分包策略（2026-11-12）：把第三方依赖按「变更频率」拆开，而不是全部塞进 index.js。
 * - `vendor-element-plus`：Element Plus + 图标。体积最大但版本升级频率最低，单独一个 chunk 便于长期缓存。
 * - `vendor-vue`：Vue 运行时 + Router + Pinia。与业务代码一起变，独立后业务发版不必让浏览器重下框架。
 * - `vendor`：其余第三方（axios 等），零散但稳定。
 *
 * 注意：这只改善**缓存命中与并行下载**，不减少总体下载量。真正瘦身（Element Plus 按需引入、
 * 图标按需注册）会改变组件注册方式与新依赖，属需要单独评估的变更，不在本次范围。
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
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // 单个 vendor chunk 必然超过默认 500KB 阈值；这里按拆分后的实际体积设阈值，
    // 让警告继续对「业务代码 chunk 变大」保持敏感，而不是被框架体积淹没。
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id: string): string | undefined {
          if (!id.includes('node_modules')) {
            return undefined;
          }
          if (id.includes('element-plus') || id.includes('@element-plus')) {
            return 'vendor-element-plus';
          }
          if (
            id.includes('/vue/') ||
            id.includes('vue-router') ||
            id.includes('/pinia/') ||
            id.includes('@vue/')
          ) {
            return 'vendor-vue';
          }
          return 'vendor';
        },
      },
    },
  },
});
