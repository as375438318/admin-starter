<script setup lang="ts">
import { onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { sessionApi } from '@/api/session';
import type { SessionDeviceType, SessionInvalidateReason, SessionItem } from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';

interface SessionFilters {
  keyword: string;
  online?: boolean;
}

const DEVICE_LABELS: Record<SessionDeviceType, string> = {
  PC: '电脑',
  MOBILE: '手机',
  TABLET: '平板',
  UNKNOWN: '未知',
};
const REASON_LABELS: Record<SessionInvalidateReason, string> = {
  LOGOUT: '主动退出',
  KICKED_BY_ADMIN: '管理员下线',
  PASSWORD_CHANGED: '密码变更',
  ACCOUNT_DISABLED: '账号停用',
  USER_DELETED: '账号删除',
};
const ONLINE_OPTIONS = [
  { label: '在线', value: true },
  { label: '离线 / 已失效', value: false },
];

const {
  page,
  filters,
  rows,
  loading,
  loadError,
  skeletonVisible,
  hasFilter,
  load,
  search,
  resetFilters,
  onPageSizeChange,
} = usePagedList<SessionItem, SessionFilters>({
  initialFilters: { keyword: '', online: undefined },
  fetcher: (params) =>
    sessionApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      online: params.online,
    }),
});

async function kick(row: SessionItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确认强制下线「${row.username}」在 ${row.browser || '未知浏览器'}（${row.ip || '未知 IP'}）的会话？该用户其他设备不受影响。`,
    title: '强制下线',
    confirmText: '下线',
  });
  if (!confirmed) {
    return;
  }
  const result = await sessionApi.kick(row.id);
  ElMessage.success(result.affected > 0 ? '会话已下线' : '会话已失效，无需重复操作');
  await load();
}

async function kickUser(row: SessionItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确认强制下线「${row.username}」的全部活跃会话？若操作者也是该用户，自己的会话也会被下线。`,
    title: '按用户强制下线',
    confirmText: '全部下线',
  });
  if (!confirmed) {
    return;
  }
  const result = await sessionApi.kickUser(row.userId);
  ElMessage.success(`已下线 ${result.affected} 条会话`);
  await load();
}

async function cleanInvalidated(): Promise<void> {
  const confirmed = await confirmDelete({
    message: '确认清理失效超过 30 天的会话记录？该操作为物理删除，不可恢复。',
    title: '清理失效记录',
    confirmText: '清理',
  });
  if (!confirmed) {
    return;
  }
  const result = await sessionApi.cleanInvalidated();
  ElMessage.success(`已清理 ${result.affected} 条失效记录`);
  await load();
}

function statusText(row: SessionItem): string {
  if (row.invalidatedAt) {
    return '已失效';
  }
  return row.online ? '在线' : '离线';
}

function statusTagType(row: SessionItem): 'success' | 'info' | 'warning' {
  if (row.invalidatedAt) {
    return 'info';
  }
  return row.online ? 'success' : 'warning';
}

function deviceText(row: SessionItem): string {
  const parts = [DEVICE_LABELS[row.deviceType], row.browser, row.os].filter(Boolean);
  return parts.join(' · ') || '未知设备';
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-input
          v-model="filters.keyword"
          class="filter-keyword"
          placeholder="搜索账号或昵称"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="filters.online" class="filter-select" placeholder="在线状态" clearable @change="search">
          <el-option v-for="item in ONLINE_OPTIONS" :key="String(item.value)" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <el-button v-permission="'system:session:clean'" @click="cleanInvalidated">清理失效记录</el-button>
      </div>
    </div>

    <div class="page-card">
      <el-alert v-if="loadError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>列表加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table :data="skeletonVisible ? [] : rows" row-key="id" style="width: 100%">
        <template #empty>
          <TableSkeleton v-if="skeletonVisible" :rows="6" />
          <div v-else-if="!loading" class="table-empty">
            <p class="empty-title">{{ hasFilter ? '没有匹配的会话' : '暂无会话记录' }}</p>
            <p class="empty-desc">{{ hasFilter ? '调整筛选条件后再试。' : '用户登录后会自动出现在这里。' }}</p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
          </div>
        </template>
        <el-table-column label="用户" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.nickname || row.username }}</span>
            <span class="mono muted">（{{ row.username }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="设备 / 浏览器" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ deviceText(row) }}</template>
        </el-table-column>
        <el-table-column label="IP" width="140" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.ip || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="登录时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.loginAt) }}</template>
        </el-table-column>
        <el-table-column label="最近活跃" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastActiveAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row)" size="small">{{ statusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="失效原因" min-width="150">
          <template #default="{ row }">
            <template v-if="row.invalidatedAt">
              <el-tag type="info" size="small">
                {{ row.invalidateReason ? REASON_LABELS[row.invalidateReason as SessionInvalidateReason] : '已失效' }}
              </el-tag>
              <span class="muted"> {{ formatDateTime(row.invalidatedAt) }}</span>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                v-permission="'system:session:kick'"
                link
                type="primary"
                :disabled="Boolean(row.invalidatedAt)"
                @click="kick(row)"
              >
                踢下线
              </el-button>
              <el-button v-permission="'system:session:kick'" link type="danger" @click="kickUser(row)">
                踢该用户全部
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          v-model:current-page="page.page"
          :page-size="page.pageSize"
          :total="page.total"
          :page-sizes="PAGE_SIZES"
          layout="total, prev, pager, next, sizes"
          background
          @current-change="load"
          @size-change="onPageSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-keyword {
  width: 240px;
}

.filter-select {
  width: 160px;
}

.muted {
  color: var(--color-text-tertiary);
}
</style>
