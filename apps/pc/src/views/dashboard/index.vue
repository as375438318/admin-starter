<script setup lang="ts">
import { computed } from 'vue';
import { useAuthStore } from '@/stores/auth';

const authStore = useAuthStore();

const quickEntries = computed(() => {
  const entries = [
    { title: '用户管理', path: '/system/users', permission: 'system:user:list', icon: 'User' },
    { title: '角色管理', path: '/system/roles', permission: 'system:role:list', icon: 'Avatar' },
    { title: '部门管理', path: '/system/depts', permission: 'system:dept:list', icon: 'OfficeBuilding' },
    { title: '操作日志', path: '/system/logs', permission: 'system:log:list', icon: 'Document' },
  ];
  return entries.filter((item) => authStore.hasPermission(item.permission));
});
</script>

<template>
  <div class="page-container dashboard">
    <section class="welcome page-card">
      <div>
        <p class="welcome-eyebrow">工作台</p>
        <h1 class="welcome-title">你好，{{ authStore.displayName }}</h1>
        <p class="welcome-description">
          当前账号：{{ authStore.profile?.username }} · 所属部门：{{ authStore.profile?.deptName || '未分配' }}
        </p>
      </div>
      <el-tag effect="plain" type="primary" round>已登录</el-tag>
    </section>

    <section v-if="quickEntries.length" class="quick page-card">
      <h2 class="section-title">快捷入口</h2>
      <div class="quick-grid">
        <router-link v-for="entry in quickEntries" :key="entry.path" :to="entry.path" class="quick-item">
          <el-icon :size="20"><component :is="entry.icon" /></el-icon>
          <span>{{ entry.title }}</span>
        </router-link>
      </div>
    </section>

    <section class="tips page-card">
      <h2 class="section-title">开始使用</h2>
      <ul class="tips-list">
        <li>在「用户管理」创建账号，并通过「角色管理」分配菜单与数据范围。</li>
        <li>菜单与按钮权限由后端下发，前端路由与按钮自动按权限渲染。</li>
        <li>首次登录请在「个人中心」修改初始密码。</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.welcome {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-6);
}

.welcome-eyebrow {
  margin: 0 0 var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.welcome-title {
  margin: 0 0 var(--space-2);
  color: var(--color-text-primary);
  font-size: var(--font-size-2xl);
  font-weight: 600;
}

.welcome-description {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.quick,
.tips {
  padding: var(--space-5) var(--space-6);
}

.section-title {
  margin: 0 0 var(--space-4);
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--space-3);
}

.quick-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition: border-color var(--motion-base) var(--ease-enter),
    background-color var(--motion-base) var(--ease-enter);
}

.quick-item:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.tips-list {
  margin: 0;
  padding-left: var(--space-5);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 2;
}
</style>
