import axios from 'axios';
import { defineStore } from 'pinia';
import { reactive, ref } from 'vue';
import type { ApiEnvelope } from '@/api/types';

/**
 * 公开外观配置。默认值必须与 index.html / 登录页源码保持一致，
 * 接口失败或超时时用默认值兜底，不允许阻塞页面渲染。
 */
export interface AppConfig {
  name: string;
  slogan: string;
  copyright: string;
  icp: string;
  uploadMaxSize: number;
}

export const DEFAULT_APP_CONFIG: AppConfig = {
  name: '后台管理系统',
  slogan: '通用底座 · 开箱即用',
  copyright: '© 2026 后台管理系统',
  icp: '京ICP备00000000号',
  uploadMaxSize: 10485760,
};

const LOAD_TIMEOUT_MS = 1500;

function normalize(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export const useAppConfigStore = defineStore('appConfig', () => {
  const config = reactive<AppConfig>({ ...DEFAULT_APP_CONFIG });
  const loaded = ref(false);

  /** 空串/纯空白回落默认文案；版权缺省时跟随系统名称。 */
  function apply(patch: Partial<AppConfig> | null | undefined): void {
    const name = normalize(patch?.name) ?? DEFAULT_APP_CONFIG.name;
    config.name = name;
    config.slogan = normalize(patch?.slogan) ?? DEFAULT_APP_CONFIG.slogan;
    config.copyright = normalize(patch?.copyright) ?? `© 2026 ${name}`;
    config.icp = normalize(patch?.icp) ?? DEFAULT_APP_CONFIG.icp;
    const size = patch?.uploadMaxSize;
    config.uploadMaxSize =
      typeof size === 'number' && Number.isFinite(size) && size > 0 ? size : DEFAULT_APP_CONFIG.uploadMaxSize;
    document.title = name;
    loaded.value = true;
  }

  async function load(): Promise<void> {
    try {
      // 直接走 axios：公开接口失败时不应触发全局错误提示或 401 跳转。
      const response = await axios.get<ApiEnvelope<Partial<AppConfig>>>('/api/v1/public/configs', {
        timeout: LOAD_TIMEOUT_MS,
      });
      const envelope = response.data;
      apply(envelope && envelope.code === 0 ? envelope.data : null);
    } catch {
      apply(null);
    }
  }

  return { config, loaded, apply, load };
});
