import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { authApi } from '@/api/auth';
import type { MenuRoute, ProfileInfo } from '@/api/types';

export const useAuthStore = defineStore('auth', () => {
  const profile = ref<ProfileInfo | null>(null);
  const menuRoutes = ref<MenuRoute[]>([]);
  const permissions = ref<Set<string>>(new Set());
  const loaded = ref(false);

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
    await authApi.login(username, password);
    await bootstrap();
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
    menuRoutes.value = [];
    permissions.value = new Set();
    loaded.value = false;
  }

  return {
    profile,
    menuRoutes,
    permissions,
    loaded,
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
