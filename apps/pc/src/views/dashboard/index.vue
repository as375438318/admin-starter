<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { dashboardApi } from '@/api/dashboard';
import type { DashboardOverview } from '@/api/types';

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

const overview = ref<DashboardOverview | null>(null);
const loading = ref(true);
/**
 * 403 与"没有数据"必须区分：
 * 统计接口由 system:dashboard:view 把关，没权限时后端返回 403（不是空对象），
 * 页面据此隐藏整块并提示原因，而不是渲染一排 0 让人以为系统是空的。
 */
const forbidden = ref(false);
const loadError = ref('');

async function loadOverview() {
  loading.value = true;
  loadError.value = '';
  try {
    overview.value = await dashboardApi.overview();
  } catch (error) {
    const status = (error as { status?: number }).status;
    if (status === 403) {
      forbidden.value = true;
    } else {
      loadError.value = (error as Error).message || '统计加载失败';
    }
  } finally {
    loading.value = false;
  }
}

onMounted(loadOverview);

const kpiCards = computed(() => {
  const data = overview.value;
  if (!data) {
    return [];
  }
  return [
    { label: '用户', value: data.counts.users, hint: `启用 ${data.counts.enabledUsers}` },
    { label: '角色', value: data.counts.roles, hint: `部门 ${data.counts.depts}` },
    { label: '在线会话', value: data.counts.activeSessions, hint: '30 分钟内有活跃' },
    { label: '今日操作', value: data.today.operations, hint: `登录成功 ${data.today.loginSuccess}` },
  ];
});

const secondaryCards = computed(() => {
  const data = overview.value;
  if (!data) {
    return [];
  }
  return [
    { label: '参数', value: data.counts.configs },
    { label: '字典类型', value: data.counts.dictTypes },
    { label: '字典项', value: data.counts.dictItems },
    { label: '岗位', value: data.counts.posts },
    { label: '生效通知', value: data.counts.notices },
    { label: '定时任务', value: data.counts.jobs, hint: `启用 ${data.counts.enabledJobs}` },
    { label: '文件', value: data.storage.files, hint: formatBytes(data.storage.totalBytes) },
    { label: '今日登录失败', value: data.today.loginFailure },
  ];
});

const trendMax = computed(() => {
  const points = overview.value?.trends ?? [];
  return Math.max(1, ...points.map((p) => Math.max(p.logins, p.operations)));
});

const taskRows = computed(() => {
  const rows = overview.value?.taskStatus ?? [];
  const labels: Record<string, string> = { TODO: '待处理', DOING: '进行中', DONE: '已完成' };
  const total = rows.reduce((sum, row) => sum + row.count, 0);
  return rows.map((row) => ({
    label: labels[row.status] ?? row.status,
    count: row.count,
    percent: total === 0 ? 0 : Math.round((row.count / total) * 100),
  }));
});

function formatBytes(bytes: number): string {
  if (!bytes) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
}

function barHeight(value: number): string {
  return `${Math.max(4, Math.round((value / trendMax.value) * 100))}%`;
}

function shortDate(date: string): string {
  return date.slice(5);
}
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

    <section v-if="loading" class="page-card">
      <el-skeleton :rows="4" animated />
    </section>

    <!-- 有权限：真实统计 -->
    <template v-else-if="overview">
      <section class="stats">
        <div class="stat-grid">
          <article v-for="card in kpiCards" :key="card.label" class="stat-card">
            <p class="stat-label">{{ card.label }}</p>
            <p class="stat-value">{{ card.value }}</p>
            <p class="stat-hint">{{ card.hint }}</p>
          </article>
        </div>
        <div class="stat-grid stat-grid--compact">
          <article v-for="card in secondaryCards" :key="card.label" class="stat-card stat-card--compact">
            <p class="stat-label">{{ card.label }}</p>
            <p class="stat-value-sm">{{ card.value }}</p>
            <p v-if="card.hint" class="stat-hint">{{ card.hint }}</p>
          </article>
        </div>
      </section>

      <section class="panel-grid">
        <article class="page-card panel">
          <h2 class="section-title">近 7 天活跃</h2>
          <div class="trend" role="img" aria-label="近 7 天登录与操作趋势">
            <div v-for="point in overview.trends" :key="point.date" class="trend-col">
              <div class="trend-bars">
                <span
                  class="trend-bar trend-bar--login"
                  :style="{ height: barHeight(point.logins) }"
                  :title="`登录 ${point.logins}`"
                />
                <span
                  class="trend-bar trend-bar--oper"
                  :style="{ height: barHeight(point.operations) }"
                  :title="`操作 ${point.operations}`"
                />
              </div>
              <span class="trend-date">{{ shortDate(point.date) }}</span>
            </div>
          </div>
          <p class="legend">
            <span class="legend-item"><i class="dot dot--login" />登录</span>
            <span class="legend-item"><i class="dot dot--oper" />操作</span>
          </p>
        </article>

        <article class="page-card panel">
          <h2 class="section-title">示例任务状态</h2>
          <p v-if="!taskRows.length" class="empty">暂无任务</p>
          <div v-for="row in taskRows" :key="row.label" class="task-row">
            <span class="task-label">{{ row.label }}</span>
            <el-progress :percentage="row.percent" :stroke-width="10" class="task-bar" />
            <span class="task-count">{{ row.count }}</span>
          </div>
        </article>
      </section>
    </template>

    <!-- 没权限：说明原因，不要渲染成空数据 -->
    <section v-else-if="forbidden" class="page-card notice-card">
      <el-alert type="info" :closable="false" show-icon title="统计区需要「查看仪表盘统计」权限">
        当前账号没有 <code>system:dashboard:view</code> 权限，因此不显示统计数字。
        如需查看，请让管理员在「角色管理」中为该角色勾选「查看仪表盘统计」。
      </el-alert>
    </section>

    <section v-else class="page-card notice-card">
      <el-alert type="warning" :closable="false" show-icon title="统计加载失败" :description="loadError">
        <el-button size="small" @click="loadOverview">重试</el-button>
      </el-alert>
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

.stats {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-4);
}

.stat-grid--compact {
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--space-3);
}

.stat-card {
  padding: var(--space-4) var(--space-5);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.stat-label {
  margin: 0 0 var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.stat-value {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-2xl);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.stat-value-sm {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.stat-hint {
  margin: var(--space-1) 0 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.panel-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: var(--space-4);
}

@media (max-width: 960px) {
  .panel-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.panel {
  padding: var(--space-5) var(--space-6);
}

.trend {
  display: flex;
  align-items: flex-end;
  gap: var(--space-3);
  height: 140px;
  padding-top: var(--space-3);
}

.trend-col {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  height: 100%;
}

.trend-bars {
  display: flex;
  flex: 1;
  align-items: flex-end;
  gap: 3px;
  width: 100%;
}

.trend-bar {
  flex: 1;
  min-height: 4px;
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
}

.trend-bar--login {
  background: var(--color-primary);
}

.trend-bar--oper {
  background: var(--color-primary-soft);
}

.trend-date {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.legend {
  display: flex;
  gap: var(--space-4);
  margin: var(--space-3) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot--login {
  background: var(--color-primary);
}

.dot--oper {
  background: var(--color-primary-soft);
}

.task-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.task-label {
  width: 60px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.task-bar {
  flex: 1;
}

.task-count {
  width: 40px;
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.empty {
  margin: 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.notice-card {
  padding: var(--space-4) var(--space-5);
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
