<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { useAppStore } from '@/stores/app';
import { useAuthStore } from '@/stores/auth';
import { useAppConfigStore } from '@/stores/appConfig';
import { useNoticeStore } from '@/stores/notice';
import type { MenuRoute } from '@/api/types';
import SideMenu from './SideMenu.vue';

const appStore = useAppStore();
const authStore = useAuthStore();
const appConfig = useAppConfigStore();
const noticeStore = useNoticeStore();
const route = useRoute();
const router = useRouter();

/** 未读数轮询间隔：站内信对延迟不敏感，30 秒足够；页面隐藏时暂停。 */
const POLL_INTERVAL_MS = 30_000;
let pollTimer: number | undefined;

const collapsed = computed(() => appStore.sidebarCollapsed);

function onVisibilityChange(): void {
  if (document.visibilityState === 'visible') {
    void noticeStore.refresh();
  }
}

onMounted(() => {
  void noticeStore.refresh();
  pollTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      void noticeStore.refresh();
    }
  }, POLL_INTERVAL_MS);
  document.addEventListener('visibilitychange', onVisibilityChange);
});

onBeforeUnmount(() => {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer);
  }
  document.removeEventListener('visibilitychange', onVisibilityChange);
});

function findChain(items: MenuRoute[], path: string, trail: MenuRoute[] = []): MenuRoute[] | null {
  for (const item of items) {
    const next = [...trail, item];
    if (item.path === path && item.component !== 'Layout') {
      return next;
    }
    if (item.children?.length) {
      const found = findChain(item.children, path, next);
      if (found) {
        return found;
      }
    }
  }
  return null;
}

const breadcrumbs = computed<MenuRoute[]>(() => {
  if (route.path === '/dashboard') {
    return [];
  }
  return findChain(authStore.menuRoutes, route.path) ?? [];
});

const pageTitle = computed(() => {
  const last = breadcrumbs.value[breadcrumbs.value.length - 1];
  return last?.meta?.title ?? String(route.meta.title ?? '');
});

const sidebarMenus = computed(() => authStore.menuRoutes.filter((item) => !item.meta?.hidden));

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认退出当前账号？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
      closeOnClickModal: false,
      closeOnPressEscape: true,
    });
  } catch {
    return;
  }
  await authStore.logout();
  noticeStore.reset();
  await router.replace('/login');
}

async function goProfile(): Promise<void> {
  await router.push('/profile');
}

async function goMessages(): Promise<void> {
  await router.push('/messages');
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar" :class="{ 'is-collapsed': collapsed }">
      <div class="brand">
        <div class="brand-logo">A</div>
        <span v-show="!collapsed" class="brand-text">{{ appConfig.config.name }}</span>
      </div>
      <el-scrollbar class="sidebar-scroll">
        <el-menu
          :default-active="route.path"
          :collapse="collapsed"
          :collapse-transition="false"
          router
          class="sidebar-menu"
        >
          <SideMenu :items="sidebarMenus" />
        </el-menu>
      </el-scrollbar>
    </aside>

    <div class="main">
      <header class="header">
        <div class="header-left">
          <button class="collapse-button" type="button" aria-label="折叠侧栏" @click="appStore.toggleSidebar()">
            <el-icon :size="18">
              <Expand v-if="collapsed" />
              <Fold v-else />
            </el-icon>
          </button>
          <el-breadcrumb v-if="breadcrumbs.length" separator="/" class="breadcrumb">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.id">
              <span class="breadcrumb-text" :title="item.meta?.title ?? item.name">
                {{ item.meta?.title ?? item.name }}
              </span>
            </el-breadcrumb-item>
          </el-breadcrumb>
          <span v-else class="page-heading" :title="pageTitle">{{ pageTitle }}</span>
        </div>

        <div class="header-right">
          <button class="bell-button" type="button" aria-label="我的消息" @click="goMessages">
            <el-badge
              :value="noticeStore.unreadCount"
              :max="99"
              :hidden="noticeStore.unreadCount === 0"
              :offset="[2, 2]"
            >
              <el-icon :size="18"><Bell /></el-icon>
            </el-badge>
          </button>
          <el-dropdown trigger="click">
            <button class="user-button" type="button">
              <el-avatar :size="30" class="user-avatar">{{ authStore.initial }}</el-avatar>
              <span class="user-name" :title="authStore.displayName">{{ authStore.displayName }}</span>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goProfile">
                  <el-icon><User /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--color-canvas);
}

.sidebar {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  width: var(--layout-sidebar-width);
  overflow: hidden;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  transition: width var(--motion-base) var(--ease-enter);
}

.sidebar.is-collapsed {
  width: var(--layout-sidebar-collapsed-width);
}

.brand {
  display: flex;
  flex: 0 0 var(--layout-header-height);
  align-items: center;
  gap: var(--space-3);
  height: var(--layout-header-height);
  padding: 0 var(--space-4);
  overflow: hidden;
  white-space: nowrap;
  border-bottom: 1px solid var(--color-divider);
}

.sidebar.is-collapsed .brand {
  justify-content: center;
  padding: 0;
}

.brand-logo {
  display: flex;
  flex: 0 0 32px;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--color-text-inverse);
  font-size: var(--font-size-lg);
  font-weight: 700;
  background: var(--color-primary);
  border-radius: var(--radius-md);
}

.brand-text {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: 600;
  text-overflow: ellipsis;
}

.sidebar-scroll {
  flex: 1;
  min-height: 0;
}

.sidebar-menu {
  width: 100%;
  border-right: none;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: var(--layout-sidebar-width);
}

:deep(.el-menu-item) {
  height: 44px;
  margin: 2px var(--space-2);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  line-height: 44px;
}

:deep(.el-menu-item:hover) {
  background: var(--color-surface-subtle);
  color: var(--color-text-primary);
}

:deep(.el-menu-item.is-active) {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 500;
}

:deep(.el-sub-menu__title) {
  height: 44px;
  margin: 2px var(--space-2);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  line-height: 44px;
}

:deep(.el-sub-menu__title:hover) {
  background: var(--color-surface-subtle);
  color: var(--color-text-primary);
}

:deep(.el-sub-menu .el-menu) {
  background: transparent;
}

.main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.header {
  display: flex;
  flex: 0 0 var(--layout-header-height);
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  height: var(--layout-header-height);
  padding: 0 var(--space-5);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.collapse-button {
  display: flex;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  color: var(--color-text-secondary);
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--motion-base) var(--ease-enter);
}

.collapse-button:hover {
  background: var(--color-surface-muted);
}

.breadcrumb {
  min-width: 0;
}

.breadcrumb-text {
  display: inline-block;
  max-width: 160px;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.page-heading {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-right {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}

.bell-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  color: var(--color-text-secondary);
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--motion-base) var(--ease-enter);
}

.bell-button:hover {
  background: var(--color-surface-muted);
}

.user-button {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 40px;
  padding: 0 var(--space-2);
  color: var(--color-text-secondary);
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--motion-base) var(--ease-enter);
}

.user-button:hover {
  background: var(--color-surface-muted);
}

.user-avatar {
  background: var(--color-primary);
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  flex: 1;
  min-height: 0;
  padding: var(--space-5);
  overflow: auto;
}
</style>
