<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { logApi } from '@/api/log';
import type { OperLogItem } from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { usePagedList } from '@/composables/use-paged-list';
import { useTableColumns, type ColumnDefinition } from '@/composables/use-table-columns';
import ColumnSettings from '@/components/ColumnSettings.vue';

interface LogFilters {
  keyword: string;
  range: [string, string] | null;
}

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
  onSortChange,
} = usePagedList<OperLogItem, LogFilters>({
  initialFilters: { keyword: '', range: null },
  fetcher: (params) =>
    logApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      from: params.range ? toInstant(params.range[0], false) : undefined,
      to: params.range ? toInstant(params.range[1], true) : undefined,
      sort: params.sort,
      order: params.order,
    }),
});

/** 列定义（唯一定义处）：sortKey 取自 constants/sort-whitelist.ts 的 operLog 白名单 */
const LOG_COLUMNS: ColumnDefinition[] = [
  { key: 'module', label: '模块', alwaysVisible: true },
  { key: 'action', label: '操作' },
  { key: 'request', label: '请求', exportable: false },
  { key: 'result', label: '结果', exportable: false },
  { key: 'elapsedMs', label: '耗时(ms)', sortKey: 'elapsedMs' },
  { key: 'username', label: '操作人' },
  { key: 'deptName', label: '部门' },
  { key: 'ip', label: 'IP' },
  { key: 'createdAt', label: '时间', sortKey: 'createdAt' },
  { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },
];

const table = useTableColumns({
  storageKey: 'system-log',
  columns: LOG_COLUMNS,
  onSort: (sort, order) => onSortChange(sort, order),
});

const detailVisible = ref(false);
const detail = ref<OperLogItem | null>(null);

function toInstant(value: string, endOfDay: boolean): string {
  const suffix = endOfDay ? 'T23:59:59+08:00' : 'T00:00:00+08:00';
  return new Date(`${value}${suffix}`).toISOString();
}

function openDetail(row: OperLogItem): void {
  detail.value = row;
  detailVisible.value = true;
}

function isSuccess(row: OperLogItem): boolean {
  return row.resultCode === 0;
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
          placeholder="搜索模块、操作或操作人"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-date-picker
          v-model="filters.range"
          class="filter-range"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <ColumnSettings
          :columns="LOG_COLUMNS"
          :is-visible="table.isVisible"
          :hidden-count="table.hiddenCount.value"
          @toggle="table.toggleColumn"
          @show-all="table.showAllColumns"
          @invert="table.invertColumns"
          @reset="table.resetColumns"
        />
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div class="page-card">
      <el-alert v-if="loadError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>列表加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table
        :data="skeletonVisible ? [] : rows"
        row-key="id"
        style="width: 100%"
        @sort-change="table.onSortChange"
      >
        <template #empty>
          <TableSkeleton v-if="skeletonVisible" :rows="6" />
          <div v-else-if="!loading" class="table-empty">
            <p class="empty-title">{{ hasFilter ? '没有匹配的操作日志' : '暂无操作日志' }}</p>
            <p class="empty-desc">
              {{ hasFilter ? '调整关键词或日期范围后再试。' : '系统产生操作后会自动记录到这里。' }}
            </p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
          </div>
        </template>
        <el-table-column v-if="table.isVisible('module')" prop="module" label="模块" width="150" fixed="left" show-overflow-tooltip>
          <template #default="{ row }">{{ row.module || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('action')" prop="action" label="操作" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.action || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('request')" prop="request" label="请求" width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.requestMethod }} {{ row.requestUri }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('result')" prop="result" label="结果" width="120">
          <template #default="{ row }">
            <el-tag :type="isSuccess(row) ? 'success' : 'danger'" size="small">
              {{ isSuccess(row) ? '成功' : `失败 ${row.resultCode}` }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="table.isVisible('elapsedMs')"
          prop="elapsedMs"
          label="耗时(ms)"
          width="100"
          align="right"
          class-name="tabular-nums"
          :sortable="table.sortableOf('elapsedMs')"
        >
          <template #default="{ row }">{{ row.elapsedMs ?? '—' }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('username')" prop="username" label="操作人" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.username || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('deptName')" prop="deptName" label="部门" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('ip')" prop="ip" label="IP" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ip || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('createdAt')" prop="createdAt" label="时间" width="160" :sortable="table.sortableOf('createdAt')">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('ops')" prop="ops" label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <el-drawer v-model="detailVisible" title="操作日志详情" size="640px">
      <div v-if="detail" class="detail-body">
        <div class="detail-section">
          <h4 class="detail-title">基础信息</h4>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="模块">{{ detail.module || '—' }}</el-descriptions-item>
            <el-descriptions-item label="操作">{{ detail.action || '—' }}</el-descriptions-item>
            <el-descriptions-item label="操作人">{{ detail.username || '—' }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ detail.deptName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="IP">{{ detail.ip || '—' }}</el-descriptions-item>
            <el-descriptions-item label="时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="TraceId" :span="2">
              <span class="mono">{{ detail.traceId }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h4 class="detail-title">请求与响应</h4>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="请求方法">{{ detail.requestMethod }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ detail.elapsedMs ?? '—' }} ms</el-descriptions-item>
            <el-descriptions-item label="请求地址" :span="2">
              <span class="mono">{{ detail.requestUri }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="结果" :span="2">
              <el-tag :type="isSuccess(detail) ? 'success' : 'danger'" size="small">
                {{ isSuccess(detail) ? '成功' : '失败' }}（{{ detail.resultCode }}）
              </el-tag>
              <span class="result-message">{{ detail.resultMessage || '' }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="detail.params" class="detail-section">
          <h4 class="detail-title">请求参数</h4>
          <pre class="params-block">{{ detail.params }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.toolbar-filters {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.filter-keyword {
  width: 240px;
}

.filter-range {
  width: 260px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.table-error {
  margin: var(--space-4);
}

.error-retry {
  margin-top: var(--space-2);
}

.table-pagination {
  padding: var(--space-4) var(--space-5);
}

.row-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.detail-title {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.result-message {
  margin-left: var(--space-3);
  color: var(--color-text-secondary);
}

.params-block {
  margin: 0;
  padding: var(--space-4);
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  line-height: var(--line-height-normal);
  white-space: pre-wrap;
  word-break: break-all;
}

.table-empty {
  padding: var(--space-8) 0;
  text-align: center;
  color: var(--color-text-secondary);
}

.empty-title {
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
}

.empty-desc {
  margin: 0 0 var(--space-4);
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}
</style>
