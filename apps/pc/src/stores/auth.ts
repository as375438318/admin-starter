import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { authApi } from '@/api/auth';
import type { MenuRoute, ProfileInfo } from '@/api/types';

export const useAuthStore = defineStore('auth', () => {
  const profile = ref<ProfileInfo | null>(null);
  const menuRoutes = ref<MenuRoute[]>([]);
  const permissions = ref<Set<string>>(new Set());
  const loaded = ref(false);
  // 必须改密标记：登录响应带来；刷新页面后会丢，但下一次业务请求拿到 403+20302 时会重新置位并跳转
  const mustChangePassword = ref(false);
  /** 口令是否已过期（与 mustChangePassword 分开存：提示语不同） */
  const passwordExpired = ref(false);

  const isLoggedIn = computed(() => profile.value !== null);
  const displayName = computed(() => profile.value?.nickname || profile.value?.username || '');
  const initial = computed(() => displayName.value.slice(0, 1).toUpperCase());

  function hasPermission(code?: string | null): boolean {
    if (!code) {
      return true;
    }
    return permissions.value.has(code);
  }

  async function bootstrap(): Promise<void> {
    const [me, routes, codes] = await Promise.all([authApi.me(), authApi.routes(), authApi.permissions()]);
    profile.value = me;
    menuRoutes.value = routes;
    permissions.value = new Set(codes);
    loaded.value = true;
  }

  async function login(username: string, password: string): Promise<void> {
    const result = await authApi.login(username, password);
    mustChangePassword.value = result.mustChangePassword === true || result.passwordExpired === true;
    passwordExpired.value = result.passwordExpired === true;
    await bootstrap();
  }

  function setMustChangePassword(value: boolean): void {
    mustChangePassword.value = value;
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout();
    } finally {
      reset();
    }
  }

  function reset(): void {
    profile.value = null;
    mustChangePassword.value = false;
    passwordExpired.value = false;
    menuRoutes.value = [];
    permissions.value = new Set();
    loaded.value = false;
  }

  return {
    profile,
    menuRoutes,
    permissions,
    loaded,
    mustChangePassword,
    passwordExpired,
    setMustChangePassword,
    isLoggedIn,
    displayName,
    initial,
    hasPermission,
    bootstrap,
    login,
    logout,
    reset,
  };
});
