<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fileApi } from '@/api/file';
import { ApiError } from '@/api/http';
import type { FileItem } from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useBlobPreview } from '@/composables/use-blob-preview';
import { useChunkUpload } from '@/composables/use-chunk-upload';
import { useTableColumns, type ColumnDefinition } from '@/composables/use-table-columns';
import TableSkeleton from '@/components/TableSkeleton.vue';
import ColumnSettings from '@/components/ColumnSettings.vue';
import { useAppConfigStore } from '@/stores/appConfig';

const appConfig = useAppConfigStore();

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
} = usePagedList<FileItem, { keyword: string }>({
  initialFilters: { keyword: '' },
  fetcher: (params) =>
    fileApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      // 排序参数必须显式转发：fetcher 是逐字段映射的，漏一行就等于「点了表头没反应」
      sort: params.sort,
      order: params.order,
    }),
});

const recycleVisible = ref(false);
const {
  page: recyclePage,
  filters: recycleFilters,
  rows: recycleRows,
  loading: recycleLoading,
  load: loadRecycle,
  onPageSizeChange: onRecyclePageSizeChange,
} = usePagedList<FileItem, { keyword: string }>({
  initialFilters: { keyword: '' },
  onError: (error) => {
    ElMessage.error(error instanceof ApiError ? error.message : '回收站加载失败');
  },
  fetcher: (params) =>
    fileApi.recycle({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
    }),
});
/**
 * 文件列表列定义（唯一定义处）：同时驱动表格渲染、列勾选、点表头排序、导出字段。
 * `sortKey` 只能取 `constants/sort-whitelist.ts` 的 file 白名单（后端对白名单外字段静默回落）。
 */
const FILE_COLUMNS: ColumnDefinition[] = [
  { key: 'originalName', label: '文件名', sortKey: 'originalName', alwaysVisible: true },
  { key: 'contentType', label: '类型' },
  { key: 'size', label: '大小', sortKey: 'size' },
  // 派生列（由 uploaderId 渲染），不可导出
  { key: 'uploaderName', label: '上传人', exportable: false },
  { key: 'createdAt', label: '上传时间', sortKey: 'createdAt' },
  { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },
];

const fileTable = useTableColumns({
  storageKey: 'system-file',
  columns: FILE_COLUMNS,
  onSort: (sort, order) => onSortChange(sort, order),
});

/**
 * 回收站表列定义：后端只给文件主列表加了排序白名单，回收站**不接排序**，只接列设置。
 */
const FILE_RECYCLE_COLUMNS: ColumnDefinition[] = [
  { key: 'originalName', label: '文件名', alwaysVisible: true },
  { key: 'size', label: '大小' },
  { key: 'deletedAt', label: '删除时间' },
  { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },
];

const recycleTable = useTableColumns({
  storageKey: 'system-file-recycle',
  columns: FILE_RECYCLE_COLUMNS,
});

const recycleKeyword = computed({
  get: () => recycleFilters.keyword,
  set: (value: string) => {
    recycleFilters.keyword = value;
  },
});

const {
  visible: previewVisible,
  row: previewRow,
  blobUrl: previewBlobUrl,
  loading: previewLoading,
  isImage: previewIsImage,
  open: preview,
  revoke: revokePreviewBlob,
} = useBlobPreview({ previewUrl: fileApi.previewUrl });

const uploadLimitText = computed(() => {
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = appConfig.config.uploadMaxSize;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  const rounded = Math.round(value * 10) / 10;
  return `${Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1)}${units[index]}`;
});

function formatSize(size: number): string {
  if (!size) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB'];
  let index = 0;
  let value = size;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

// ---------------------------------------------------------------- 上传

const {
  tasks,
  activeUploads,
  beforeUpload,
  handleUpload,
  uploadFiles,
  clearTasks,
} = useChunkUpload({
  maxSize: () => appConfig.config.uploadMaxSize,
  limitText: () => uploadLimitText.value,
  onMaxSizeUpdate: (maxSize) => {
    appConfig.config.uploadMaxSize = maxSize;
  },
  onUploaded: async () => {
    page.page = 1;
    await load();
  },
});

function onPaste(event: ClipboardEvent): void {
  const target = event.target as HTMLElement | null;
  const tag = target?.tagName?.toLowerCase();
  if (tag === 'input' || tag === 'textarea' || target?.isContentEditable) {
    return;
  }
  const files = Array.from(event.clipboardData?.files ?? []);
  if (files.length === 0) {
    return;
  }
  event.preventDefault();
  void uploadFiles(files);
}

// ---------------------------------------------------------------- 列表操作

function download(row: FileItem): void {
  window.open(fileApi.downloadUrl(row.id), '_blank');
}

async function removeFile(row: FileItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确定将「${row.originalName}」移入回收站？`,
    title: '删除文件',
    confirmText: '移入回收站',
  });
  if (!confirmed) {
    return;
  }
  await fileApi.remove(row.id);
  ElMessage.success('已移入回收站');
  await load();
}

// ---------------------------------------------------------------- 回收站

async function openRecycle(): Promise<void> {
  recycleVisible.value = true;
  recyclePage.page = 1;
  recycleFilters.keyword = '';
  await loadRecycle();
}

async function restoreFile(row: FileItem): Promise<void> {
  await fileApi.restore(row.id);
  ElMessage.success('已恢复');
  await loadRecycle();
  await load();
}

async function purgeFile(row: FileItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `彻底删除「${row.originalName}」后不可恢复，确定继续？`,
    title: '彻底删除',
    confirmText: '彻底删除',
  });
  if (!confirmed) {
    return;
  }
  await fileApi.purge(row.id);
  ElMessage.success('已彻底删除');
  await loadRecycle();
}

onMounted(() => {
  void load();
  document.addEventListener('paste', onPaste);
});

onBeforeUnmount(() => {
  document.removeEventListener('paste', onPaste);
  revokePreviewBlob();
});
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-input
          v-model="filters.keyword"
          class="filter-keyword"
          placeholder="搜索文件名"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <ColumnSettings
          :columns="FILE_COLUMNS"
          :is-visible="fileTable.isVisible"
          :hidden-count="fileTable.hiddenCount.value"
          @toggle="fileTable.toggleColumn"
          @show-all="fileTable.showAllColumns"
          @invert="fileTable.invertColumns"
          @reset="fileTable.resetColumns"
        />
        <el-button v-permission="'system:file:list'" @click="openRecycle">回收站</el-button>
      </div>
    </div>

    <div class="page-card">
      <el-upload
        v-permission="'system:file:upload'"
        class="upload-drag"
        drag
        multiple
        :show-file-list="false"
        :before-upload="beforeUpload"
        :http-request="handleUpload"
      >
        <div class="upload-drag-inner">
          <p class="upload-drag-title">拖拽文件到此处，或 <em>点击选择</em></p>
          <p class="upload-drag-desc">支持多选与剪贴板粘贴；单个文件最大 {{ uploadLimitText }}</p>
        </div>
      </el-upload>

      <div v-if="tasks.length" class="upload-tasks">
        <div class="upload-tasks-head">
          <span>上传进度（{{ activeUploads }} 个进行中）</span>
          <el-button link type="primary" @click="clearTasks">清空</el-button>
        </div>
        <div v-for="task in tasks" :key="task.id" class="upload-task">
          <span class="task-name" :title="task.name">{{ task.name }}</span>
          <el-progress
            class="task-progress"
            :percentage="task.percent"
            :status="task.status || undefined"
            :stroke-width="10"
          />
          <span class="task-hint">{{ task.hint }}</span>
        </div>
      </div>

      <el-alert v-if="loadError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>列表加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table
        :data="skeletonVisible ? [] : rows"
        row-key="id"
        style="width: 100%"
        @sort-change="fileTable.onSortChange"
      >
        <template #empty>
          <TableSkeleton v-if="skeletonVisible" :rows="6" />
          <div v-else-if="!loading" class="table-empty">
            <p class="empty-title">{{ hasFilter ? '没有匹配的文件' : '暂无文件' }}</p>
            <p class="empty-desc">
              {{ hasFilter ? '调整文件名后再试。' : '拖拽、粘贴或点击上方区域上传文件。' }}
            </p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
          </div>
        </template>
        <el-table-column
          v-if="fileTable.isVisible('originalName')"
          prop="originalName"
          label="文件名"
          width="300"
          fixed="left"
          show-overflow-tooltip
          :sortable="fileTable.sortableOf('originalName')"
        />
        <el-table-column
          v-if="fileTable.isVisible('contentType')"
          prop="contentType"
          label="类型"
          width="170"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row.contentType || '—' }}</template>
        </el-table-column>
        <el-table-column
          v-if="fileTable.isVisible('size')"
          prop="size"
          label="大小"
          width="100"
          align="right"
          class-name="tabular-nums"
          :sortable="fileTable.sortableOf('size')"
        >
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column v-if="fileTable.isVisible('uploaderName')" prop="uploaderName" label="上传人" width="100">
          <template #default="{ row }">{{ row.uploaderId ? `#${row.uploaderId}` : '—' }}</template>
        </el-table-column>
        <el-table-column
          v-if="fileTable.isVisible('createdAt')"
          prop="createdAt"
          label="上传时间"
          width="170"
          :sortable="fileTable.sortableOf('createdAt')"
        >
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="fileTable.isVisible('ops')" prop="ops" label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="download(row)">下载</el-button>
              <el-button v-if="row.previewUrl" link type="primary" @click="preview(row)">预览</el-button>
              <el-button v-permission="'system:file:delete'" link type="danger" @click="removeFile(row)">
                删除
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

    <el-dialog v-model="recycleVisible" title="回收站" width="900px" destroy-on-close>
      <div class="recycle-toolbar">
        <el-input
          v-model="recycleKeyword"
          class="filter-keyword"
          placeholder="搜索文件名"
          clearable
          @keyup.enter="loadRecycle"
          @clear="loadRecycle"
        />
        <el-button type="primary" @click="loadRecycle">查询</el-button>
        <ColumnSettings
          :columns="FILE_RECYCLE_COLUMNS"
          :is-visible="recycleTable.isVisible"
          :hidden-count="recycleTable.hiddenCount.value"
          @toggle="recycleTable.toggleColumn"
          @show-all="recycleTable.showAllColumns"
          @invert="recycleTable.invertColumns"
          @reset="recycleTable.resetColumns"
        />
      </div>
      <el-table :data="recycleRows" v-loading="recycleLoading" row-key="id" style="width: 100%">
        <el-table-column
          v-if="recycleTable.isVisible('originalName')"
          prop="originalName"
          label="文件名"
          min-width="240"
          show-overflow-tooltip
        />
        <el-table-column
          v-if="recycleTable.isVisible('size')"
          prop="size"
          label="大小"
          width="100"
          align="right"
          class-name="tabular-nums"
        >
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column v-if="recycleTable.isVisible('deletedAt')" prop="deletedAt" label="删除时间" width="170">
          <template #default="{ row }">{{ row.deletedAt ? formatDateTime(row.deletedAt) : '—' }}</template>
        </el-table-column>
        <el-table-column v-if="recycleTable.isVisible('ops')" prop="ops" label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-permission="'system:file:restore'" link type="primary" @click="restoreFile(row)">
                恢复
              </el-button>
              <el-button v-permission="'system:file:purge'" link type="danger" @click="purgeFile(row)">
                彻底删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-pagination">
        <el-pagination
          v-model:current-page="recyclePage.page"
          :page-size="recyclePage.pageSize"
          :total="recyclePage.total"
          :page-sizes="PAGE_SIZES"
          layout="total, prev, pager, next, sizes"
          background
          @current-change="loadRecycle"
          @size-change="onRecyclePageSizeChange"
        />
      </div>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      :title="previewRow?.originalName || '预览'"
      width="920px"
      destroy-on-close
      @closed="revokePreviewBlob"
    >
      <div v-loading="previewLoading" class="preview-body">
        <img
          v-if="previewRow && previewIsImage"
          class="preview-image"
          :src="fileApi.previewUrl(previewRow.id)"
          :alt="previewRow.originalName"
        />
        <iframe
          v-else-if="previewRow && previewBlobUrl"
          class="preview-pdf"
          :src="previewBlobUrl"
          title="PDF 预览"
        />
      </div>
    </el-dialog>
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

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.upload-drag {
  display: block;
  margin: var(--space-4);
}

.upload-drag :deep(.el-upload-dragger) {
  padding: var(--space-6);
}

.upload-drag-inner {
  text-align: center;
  color: var(--color-text-secondary);
}

.upload-drag-title {
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
}

.upload-drag-title em {
  color: var(--color-primary);
  font-style: normal;
}

.upload-drag-desc {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.upload-tasks {
  margin: 0 var(--space-4) var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
}

.upload-tasks-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.upload-task {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-2);
}

.task-name {
  width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
}

.task-progress {
  flex: 1;
}

.task-hint {
  width: 180px;
  text-align: right;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.recycle-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.preview-body {
  display: flex;
  justify-content: center;
  align-items: center;
  max-height: 70vh;
  overflow: auto;
}

.preview-image {
  max-width: 100%;
  max-height: 70vh;
}

.preview-pdf {
  width: 100%;
  height: 70vh;
  border: 0;
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
