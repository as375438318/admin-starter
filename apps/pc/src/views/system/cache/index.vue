<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { cacheApi } from '@/api/cache';
import { ApiError } from '@/api/http';
import type { CacheCleanLog, CacheKeyItem, CacheNamespace, CacheOverview } from '@/api/types';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useTableColumns, type ColumnDefinition } from '@/composables/use-table-columns';
import ColumnSettings from '@/components/ColumnSettings.vue';

interface CacheKeyFilters {
  keyword: string;
}

const overview = ref<CacheOverview | null>(null);
const namespaces = ref<CacheNamespace[]>([]);
const loading = ref(false);
const loadError = ref('');

const keyDrawer = reactive({
  visible: false,
  namespace: null as CacheNamespace | null,
  truncated: false,
});

const connected = computed(() => overview.value?.connected === true);

const {
  page: keyPage,
  filters: keyFilters,
  rows: keyRows,
  loading: keyLoading,
  load: loadKeys,
  search: searchKeys,
  onPageChange: onKeyPageChange,
  onPageSizeChange: onKeySizeChange,
} = usePagedList<CacheKeyItem, CacheKeyFilters>({
  initialFilters: { keyword: '' },
  fetcher: async (params) => {
    const data = await cacheApi.keys({
      namespace: keyDrawer.namespace?.code ?? '',
      keyword: params.keyword || undefined,
      page: params.page,
      pageSize: params.pageSize,
    });
    keyDrawer.truncated = data.truncated;
    return data;
  },
  onError: (error) => {
    ElMessage.error(error instanceof ApiError ? error.message : '缓存明细加载失败');
  },
});

const {
  page: logPage,
  rows: logs,
  load: loadLogs,
} = usePagedList<CacheCleanLog, Record<string, unknown>>({
  initialFilters: {},
  fetcher: (params) => cacheApi.cleanLogs({ page: params.page, pageSize: params.pageSize }),
  onError: () => undefined,
});

/**
 * 三张表的列定义各自独立：本页不接排序（缓存数据的排序语义与后端白名单不匹配），
 * 因此只用于列显隐，不给任何列写 `sortKey`，也不传 `onSort`。
 */
const NAMESPACE_COLUMNS: ColumnDefinition[] = [
  { key: 'name', label: '名称' },
  { key: 'prefix', label: '前缀' },
  { key: 'purpose', label: '用途' },
  // 派生列（负数转「不可用」文案），不参与导出
  { key: 'keyCount', label: 'Key 数', exportable: false },
  // 清除后果是给人看的完整句子（含不可清理原因），不参与导出
  { key: 'clearEffect', label: '清除后果', exportable: false },
  { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },
];

const namespaceTable = useTableColumns({
  storageKey: 'system-cache-namespace',
  columns: NAMESPACE_COLUMNS,
});

const LOG_COLUMNS: ColumnDefinition[] = [
  { key: 'action', label: '操作' },
  { key: 'namespaceCode', label: '命名空间' },
  { key: 'keyName', label: 'Key' },
  { key: 'deletedCount', label: '删除数量' },
  { key: 'username', label: '操作人' },
  { key: 'ip', label: 'IP' },
  { key: 'createdAt', label: '时间' },
];

const logTable = useTableColumns({
  storageKey: 'system-cache-log',
  columns: LOG_COLUMNS,
});

const KEY_COLUMNS: ColumnDefinition[] = [
  { key: 'name', label: 'Key 名' },
  // TTL 是 ttlSeconds 的派生文案，不参与导出
  { key: 'ttlSeconds', label: 'TTL', exportable: false },
  { key: 'type', label: '类型' },
  { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },
];

const keyTable = useTableColumns({
  storageKey: 'system-cache-key',
  columns: KEY_COLUMNS,
});

function ttlText(seconds: number): string {
  if (seconds === -1) {
    return '永久';
  }
  if (seconds === -2 || seconds === -3) {
    return '已不存在';
  }
  if (seconds < 60) {
    return `${seconds} 秒`;
  }
  if (seconds < 3600) {
    return `${Math.floor(seconds / 60)} 分钟`;
  }
  if (seconds < 86400) {
    return `${Math.floor(seconds / 3600)} 小时`;
  }
  return `${Math.floor(seconds / 86400)} 天`;
}

function countText(value: number): string {
  return value < 0 ? '不可用' : String(value);
}

async function loadOverview(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  try {
    const [data, list] = await Promise.all([cacheApi.overview(), cacheApi.namespaces()]);
    overview.value = data;
    namespaces.value = list;
  } catch (error) {
    overview.value = null;
    namespaces.value = [];
    loadError.value = error instanceof ApiError ? error.message : '缓存概览加载失败';
  } finally {
    loading.value = false;
  }
}

function openKeys(namespace: CacheNamespace): void {
  keyDrawer.namespace = namespace;
  keyDrawer.truncated = false;
  keyFilters.keyword = '';
  keyPage.page = 1;
  keyPage.pageSize = DEFAULT_PAGE_SIZE;
  keyPage.total = 0;
  keyRows.value = [];
  keyDrawer.visible = true;
  void loadKeys();
}

async function deleteKey(row: CacheKeyItem): Promise<void> {
  const namespace = keyDrawer.namespace;
  if (!namespace) {
    return;
  }
  const confirmed = await confirmDelete({
    message: `确认删除 key「${row.name}」？该操作不可撤销。`,
    title: '删除单个 key',
    confirmText: '删除',
  });
  if (!confirmed) {
    return;
  }
  const result = await cacheApi.deleteKey(namespace.code, row.name);
  ElMessage.success(result.deleted > 0 ? 'key 已删除' : 'key 已不存在');
  await Promise.all([loadKeys(), loadOverview(), loadLogs()]);
}

async function clearNamespace(namespace: CacheNamespace): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确认清空命名空间「${namespace.name}」的 ${namespace.keyCount} 个 key？\n\n清除后果：${namespace.clearEffect}`,
    title: '清空命名空间',
    confirmText: '清空',
  });
  if (!confirmed) {
    return;
  }
  const result = await cacheApi.clearNamespace(namespace.code);
  ElMessage.success(`已删除 ${result.deleted} 个 key`);
  await Promise.all([loadOverview(), loadLogs()]);
  if (keyDrawer.visible && keyDrawer.namespace?.code === namespace.code) {
    keyPage.page = 1;
    await loadKeys();
  }
}

async function cleanSessionCache(): Promise<void> {
  const confirmed = await confirmDelete({
    message: '确认清理过期会话缓存（会话活跃节流键）？不影响登录态，下一次请求会重新记录活跃时间。',
    title: '清理过期会话缓存',
    confirmText: '清理',
  });
  if (!confirmed) {
    return;
  }
  const result = await cacheApi.cleanSessionCache();
  ElMessage.success(`已清理 ${result.deleted} 个会话节流键`);
  await Promise.all([loadOverview(), loadLogs()]);
}

onMounted(async () => {
  await loadOverview();
  await loadLogs();
});
</script>

<template>
  <div class="cache-page">
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
      class="cache-alert"
    />

    <el-card shadow="never" class="cache-card">
      <template #header>
        <div class="cache-header">
          <span class="cache-title">缓存概览</span>
          <div class="cache-actions">
            <el-button v-permission="'system:cache:clear'" type="warning" plain @click="cleanSessionCache">
              清理过期会话缓存
            </el-button>
            <el-button :loading="loading" @click="loadOverview">刷新</el-button>
          </div>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="stat">
            <div class="stat-label">Redis 连接</div>
            <div class="stat-value">
              <el-tag :type="connected ? 'success' : 'danger'" effect="dark">
                {{ connected ? '已连接' : '未连接' }}
              </el-tag>
            </div>
            <div class="stat-extra">缓存实现：{{ overview?.cacheType || '未知' }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat">
            <div class="stat-label">内存占用</div>
            <div class="stat-value">{{ overview?.usedMemoryHuman || '—' }}</div>
            <div class="stat-extra">
              上限：{{ overview?.maxMemoryHuman || '未设置' }}
              <span v-if="overview?.maxMemoryPolicy"> · {{ overview.maxMemoryPolicy }}</span>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat">
            <div class="stat-label">Key 总数（DBSIZE）</div>
            <div class="stat-value">{{ overview?.totalKeys ?? '—' }}</div>
            <div class="stat-extra">与 Redis DBSIZE 一致</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat">
            <div class="stat-label">未登记键</div>
            <div class="stat-value">{{ overview?.unregisteredKeys ?? '—' }}</div>
            <div class="stat-extra">不属于任何白名单命名空间，只读</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" class="cache-card">
      <template #header>
        <div class="cache-header">
          <span class="cache-title">命名空间</span>
          <div class="cache-actions">
            <span class="cache-tip">只允许按登记命名空间操作，未登记前缀不会被接受</span>
            <ColumnSettings
              :columns="NAMESPACE_COLUMNS"
              :is-visible="namespaceTable.isVisible"
              :hidden-count="namespaceTable.hiddenCount.value"
              @toggle="namespaceTable.toggleColumn"
              @show-all="namespaceTable.showAllColumns"
              @invert="namespaceTable.invertColumns"
              @reset="namespaceTable.resetColumns"
            />
          </div>
        </div>
      </template>
      <el-table :data="namespaces" stripe>
        <el-table-column v-if="namespaceTable.isVisible('name')" prop="name" label="名称" min-width="130">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.sensitive" size="small" type="warning" class="tag-gap">敏感</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="namespaceTable.isVisible('prefix')"
          prop="prefix"
          label="前缀"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column
          v-if="namespaceTable.isVisible('purpose')"
          prop="purpose"
          label="用途"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column v-if="namespaceTable.isVisible('keyCount')" prop="keyCount" label="Key 数" width="90" align="right">
          <template #default="{ row }">{{ countText(row.keyCount) }}</template>
        </el-table-column>
        <el-table-column v-if="namespaceTable.isVisible('clearEffect')" prop="clearEffect" label="清除后果" min-width="260">
          <template #default="{ row }">
            <span :class="{ 'danger-text': !row.clearable }">{{ row.clearEffect }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="namespaceTable.isVisible('ops')" prop="ops" label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openKeys(row)">浏览</el-button>
            <el-tooltip v-if="!row.clearable" :content="row.clearEffect" placement="top">
              <span class="disabled-wrap">
                <el-button link type="danger" disabled>清空</el-button>
              </span>
            </el-tooltip>
            <el-button v-else v-permission="'system:cache:clear'" link type="danger" @click="clearNamespace(row)">
              清空
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="cache-card">
      <template #header>
        <div class="cache-header">
          <span class="cache-title">清理记录</span>
          <div class="cache-actions">
            <span class="cache-tip">来自操作日志（模块「缓存管理」），含操作人、命名空间与删除数量</span>
            <ColumnSettings
              :columns="LOG_COLUMNS"
              :is-visible="logTable.isVisible"
              :hidden-count="logTable.hiddenCount.value"
              @toggle="logTable.toggleColumn"
              @show-all="logTable.showAllColumns"
              @invert="logTable.invertColumns"
              @reset="logTable.resetColumns"
            />
          </div>
        </div>
      </template>
      <el-table :data="logs" stripe>
        <el-table-column v-if="logTable.isVisible('action')" prop="action" label="操作" width="170" />
        <el-table-column v-if="logTable.isVisible('namespaceCode')" prop="namespaceCode" label="命名空间" min-width="150">
          <template #default="{ row }">{{ row.namespaceCode || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="logTable.isVisible('keyName')" prop="keyName" label="Key" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.keyName || '—' }}</template>
        </el-table-column>
        <el-table-column
          v-if="logTable.isVisible('deletedCount')"
          prop="deletedCount"
          label="删除数量"
          width="100"
          align="right"
        />
        <el-table-column v-if="logTable.isVisible('username')" prop="username" label="操作人" width="120">
          <template #default="{ row }">{{ row.username || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="logTable.isVisible('ip')" prop="ip" label="IP" width="140">
          <template #default="{ row }">{{ row.ip || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="logTable.isVisible('createdAt')" prop="createdAt" label="时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="logPage.page"
          v-model:page-size="logPage.pageSize"
          :page-sizes="PAGE_SIZES"
          :total="logPage.total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadLogs"
          @size-change="loadLogs"
        />
      </div>
    </el-card>

    <el-drawer v-model="keyDrawer.visible" size="720px" :title="`缓存明细 · ${keyDrawer.namespace?.name || ''}`">
      <div class="drawer-body">
        <el-alert
          v-if="keyDrawer.namespace?.sensitive"
          type="warning"
          show-icon
          :closable="false"
          title="该命名空间含令牌/会话标识，只展示 key 名与 TTL，不读取任何值内容"
          class="drawer-alert"
        />
        <div class="drawer-toolbar">
          <el-input
            v-model="keyFilters.keyword"
            placeholder="按 key 名过滤（不改变命名空间边界）"
            clearable
            class="drawer-search"
            @keyup.enter="searchKeys"
            @clear="searchKeys"
          />
          <el-button @click="searchKeys">查询</el-button>
          <ColumnSettings
            :columns="KEY_COLUMNS"
            :is-visible="keyTable.isVisible"
            :hidden-count="keyTable.hiddenCount.value"
            @toggle="keyTable.toggleColumn"
            @show-all="keyTable.showAllColumns"
            @invert="keyTable.invertColumns"
            @reset="keyTable.resetColumns"
          />
        </div>
        <el-alert
          v-if="keyDrawer.truncated"
          type="warning"
          show-icon
          :closable="false"
          title="已达到扫描批次上限，总数可能偏小；请缩小关键字范围"
        />
        <el-table v-loading="keyLoading" :data="keyRows" stripe>
          <el-table-column
            v-if="keyTable.isVisible('name')"
            prop="name"
            label="Key 名"
            min-width="300"
            show-overflow-tooltip
          />
          <el-table-column v-if="keyTable.isVisible('ttlSeconds')" prop="ttlSeconds" label="TTL" width="110">
            <template #default="{ row }">{{ ttlText(row.ttlSeconds) }}</template>
          </el-table-column>
          <el-table-column v-if="keyTable.isVisible('type')" prop="type" label="类型" width="100" />
          <el-table-column v-if="keyTable.isVisible('ops')" prop="ops" label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="keyDrawer.namespace?.clearable"
                v-permission="'system:cache:clear'"
                link
                type="danger"
                @click="deleteKey(row)"
              >
                删除
              </el-button>
              <span v-else class="disabled-text">不可清理</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="keyPage.page"
            v-model:page-size="keyPage.pageSize"
            :page-sizes="PAGE_SIZES"
            :total="keyPage.total"
            layout="total, sizes, prev, pager, next"
            @current-change="onKeyPageChange"
            @size-change="onKeySizeChange"
          />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.cache-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cache-alert {
  margin-bottom: 0;
}

.cache-card {
  border-radius: 8px;
}

.cache-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cache-title {
  font-size: 16px;
  font-weight: 600;
}

.cache-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cache-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.stat {
  padding: 8px 4px;
}

.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stat-extra {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.tag-gap {
  margin-left: 6px;
}

.danger-text {
  color: var(--el-color-danger);
}

.disabled-wrap {
  display: inline-block;
}

.disabled-text {
  color: var(--el-text-color-disabled);
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drawer-alert {
  margin-bottom: 0;
}

.drawer-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.drawer-search {
  max-width: 420px;
}
</style>
