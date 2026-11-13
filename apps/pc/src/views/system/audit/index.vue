<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { changeAuditApi, type ChangeAuditItem, type ChangeAction, type ChangeTargetType } from '@/api/changeAudit';
import { usePagedList } from '@/composables/use-paged-list';
import { useTableColumns, type ColumnDefinition } from '@/composables/use-table-columns';
import ColumnSettings from '@/components/ColumnSettings.vue';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { formatDateTime } from '@/utils/datetime';

/**
 * 配置面变更审计（`CR-20261114-042`）。
 *
 * <p>回答的是"某个配置是谁在什么时候从什么改成什么"——操作日志只有请求级信息（谁点了保存），
 * 没有旧值。这里按对象维度看轨迹。</p>
 *
 * <p>两个刻意的取舍：① **只读**，页面没有新增/编辑/删除入口，审计记录不可被改；
 * ② 快照是 JSON 字符串，展开后按字段列出"改前 → 改后"，而不是直接糊一坨 JSON 给用户看。</p>
 */
const route = useRoute();

const TARGET_LABELS: Record<ChangeTargetType, string> = {
  CONFIG: '参数',
  DICT_TYPE: '字典类型',
  DICT_ITEM: '字典项',
  MENU: '菜单',
  ROLE_GRANT: '角色授权',
};

const ACTION_LABELS: Record<ChangeAction, string> = {
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '删除',
  GRANT: '授权',
};

const ACTION_TAG: Record<ChangeAction, 'success' | 'primary' | 'danger' | 'warning'> = {
  CREATE: 'success',
  UPDATE: 'primary',
  DELETE: 'danger',
  GRANT: 'warning',
};

interface Filters {
  keyword: string;
  targetType: string;
  targetKey: string;
  operator: string;
  action: string;
}

const { page, filters, rows, loading, loadError, skeletonVisible, hasFilter, load, search, resetFilters,
  onPageSizeChange, onPageChange, onSortChange } =
  usePagedList<ChangeAuditItem, Filters>({
    initialFilters: {
      keyword: '',
      // 从参数页「历史」跳进来时带上对象键，落地即过滤
      targetType: String(route.query.targetType ?? ''),
      targetKey: String(route.query.targetKey ?? ''),
      operator: '',
      action: '',
    },
    fetcher: (params) => changeAuditApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      targetType: params.targetType || undefined,
      targetKey: params.targetKey || undefined,
      operator: params.operator || undefined,
      action: params.action || undefined,
      sort: params.sort,
      order: params.order,
    }),
  });

const COLUMNS: ColumnDefinition[] = [
  { key: 'createdAt', label: '时间', sortKey: 'createdAt', alwaysVisible: true },
  { key: 'targetType', label: '对象类型', sortKey: 'targetType' },
  { key: 'targetName', label: '对象', sortKey: 'targetKey' },
  { key: 'action', label: '动作' },
  { key: 'change', label: '变更内容', alwaysVisible: true, exportable: false },
  { key: 'operatorName', label: '操作人', sortKey: 'operatorName' },
  { key: 'ip', label: 'IP' },
];

const table = useTableColumns({
  storageKey: 'system-audit',
  columns: COLUMNS,
  onSort: (sort, order) => onSortChange(sort, order),
});

const expandedId = ref<number | null>(null);

const filterHint = computed(() => {
  if (filters.targetKey) {
    return `${TARGET_LABELS[filters.targetType as ChangeTargetType] ?? filters.targetType}：${filters.targetKey}`;
  }
  return '';
});

function parseSnapshot(raw: string | null): Record<string, unknown> | null {
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return { '(无法解析)': raw };
  }
}

/** 把 before/after 合并成"字段 → 改前/改后"的行，只展示真正变化的字段 */
function diffRows(row: ChangeAuditItem): Array<{ field: string; before: string; after: string }> {
  const before = parseSnapshot(row.beforeValue) ?? {};
  const after = parseSnapshot(row.afterValue) ?? {};
  const fields = new Set([...Object.keys(before), ...Object.keys(after)]);
  return [...fields].map((field) => ({
    field,
    before: format(before[field]),
    after: format(after[field]),
  }));
}

function format(value: unknown): string {
  if (value === undefined || value === null) {
    return '—';
  }
  if (typeof value === 'object') {
    const array = value as unknown[];
    if (Array.isArray(array)) {
      const shown = array.slice(0, 6).join(', ');
      return array.length > 6 ? `[${shown} …共 ${array.length} 项]` : `[${shown}]`;
    }
    return JSON.stringify(value);
  }
  return String(value);
}

function toggle(id: number): void {
  expandedId.value = expandedId.value === id ? null : id;
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="page">
    <el-alert class="page-tip" type="info" :closable="false" show-icon>
      只记录配置面的变更（参数 / 字典 / 菜单 / 角色授权），<strong>只读不可修改</strong>。
      敏感参数的历史值同样打码，不落明文。
    </el-alert>

    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-input v-model="filters.keyword" class="filter-keyword" placeholder="搜索对象名称 / 业务键" clearable @change="search" />
        <el-select v-model="filters.targetType" class="filter-select" placeholder="对象类型" clearable @change="search">
          <el-option v-for="(label, value) in TARGET_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="filters.action" class="filter-select" placeholder="动作" clearable @change="search">
          <el-option v-for="(label, value) in ACTION_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
        <el-input v-model="filters.operator" class="filter-operator" placeholder="操作人" clearable @change="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <ColumnSettings
          :columns="COLUMNS"
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

    <el-tag v-if="filterHint" class="filter-hint" type="info" closable @close="filters.targetKey = ''; search()">
      正在看：{{ filterHint }}
    </el-tag>

    <div class="page-card">
      <el-alert v-if="loadError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table :data="skeletonVisible ? [] : rows" row-key="id" style="width: 100%" @sort-change="table.onSortChange">
        <template #empty>
          <TableSkeleton v-if="skeletonVisible" :rows="6" />
          <div v-else-if="!loading" class="table-empty">
            <p class="empty-title">{{ hasFilter ? '没有匹配的变更记录' : '暂无变更记录' }}</p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
          </div>
        </template>

        <el-table-column v-if="table.isVisible('createdAt')" prop="createdAt" label="时间" width="170" :sortable="table.sortableOf('createdAt')">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('targetType')" prop="targetType" label="对象类型" width="110" :sortable="table.sortableOf('targetType')">
          <template #default="{ row }">{{ TARGET_LABELS[row.targetType as ChangeTargetType] ?? row.targetType }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('targetName')" prop="targetName" label="对象" min-width="180" show-overflow-tooltip :sortable="table.sortableOf('targetKey')">
          <template #default="{ row }">
            <span>{{ row.targetName || '—' }}</span>
            <span class="target-key">{{ row.targetKey }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('action')" prop="action" label="动作" width="90">
          <template #default="{ row }">
            <el-tag :type="ACTION_TAG[row.action as ChangeAction]" disable-transitions>
              {{ ACTION_LABELS[row.action as ChangeAction] ?? row.action }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('change')" label="变更内容" min-width="320">
          <template #default="{ row }">
            <div class="change-summary">
              <el-button link type="primary" @click="toggle(row.id)">
                {{ expandedId === row.id ? '收起' : '查看差异' }}
              </el-button>
              <span class="change-fields">{{ diffRows(row).map((item) => item.field).join('、') }}</span>
              <el-tag v-if="row.isSensitive" size="small" type="warning" disable-transitions>敏感（已打码）</el-tag>
            </div>
            <div v-if="expandedId === row.id" class="change-detail">
              <div v-for="item in diffRows(row)" :key="item.field" class="change-row">
                <span class="change-label">{{ item.field }}</span>
                <span class="change-before">{{ item.before }}</span>
                <span class="change-arrow">→</span>
                <span class="change-after">{{ item.after }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('operatorName')" prop="operatorName" label="操作人" width="120" :sortable="table.sortableOf('operatorName')" />
        <el-table-column v-if="table.isVisible('ip')" prop="ip" label="IP" width="130" />
      </el-table>

      <div class="table-pagination">
        <el-pagination
          v-model:current-page="page.page"
          v-model:page-size="page.pageSize"
          :total="page.total"
          layout="total, sizes, prev, pager, next"
          @size-change="onPageSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-tip {
  margin-bottom: var(--space-3);
}

.filter-select {
  width: 130px;
}

.filter-operator {
  width: 130px;
}

.filter-hint {
  margin-bottom: var(--space-2);
}

.target-key {
  margin-left: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.change-summary {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.change-fields {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.change-detail {
  margin-top: var(--space-2);
  padding: var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--color-fill-light);
}

.change-row {
  display: flex;
  gap: var(--space-2);
  align-items: baseline;
  font-size: var(--font-size-xs);
  line-height: 1.8;
}

.change-label {
  min-width: 120px;
  color: var(--color-text-secondary);
  font-family: var(--font-family-mono);
}

.change-before {
  color: var(--color-text-tertiary);
  text-decoration: line-through;
  word-break: break-all;
}

.change-arrow {
  color: var(--color-text-tertiary);
}

.change-after {
  color: var(--color-primary);
  word-break: break-all;
}
</style>
