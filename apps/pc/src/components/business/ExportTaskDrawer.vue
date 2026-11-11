<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ApiError, saveBlob, type FileDownload } from '@/api/http';
import type { ExportTaskItem, ExportTaskStatus, PageData } from '@/api/types';
import { formatDateTime } from '@/utils/datetime';

/**
 * 通用导出任务抽屉：列表、进度、取消、下载与 2 秒轮询。
 * 业务页面只传任务查询/取消/下载接口，不得在页面内重复实现状态映射与轮询。
 */
const props = defineProps<{
  modelValue: boolean;
  pageTasks: (params: { page: number; pageSize: number }) => Promise<PageData<ExportTaskItem>>;
  cancelTask: (id: number) => Promise<unknown>;
  downloadTask: (id: number) => Promise<FileDownload>;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const STATUS_LABELS: Record<ExportTaskStatus, string> = {
  PENDING: '排队中',
  RUNNING: '导出中',
  SUCCESS: '已完成',
  FAILED: '失败',
  CANCELED: '已取消',
  EXPIRED: '已过期',
};

const STATUS_TYPES: Record<ExportTaskStatus, 'info' | 'warning' | 'success' | 'danger'> = {
  PENDING: 'info',
  RUNNING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  CANCELED: 'info',
  EXPIRED: 'info',
};

const loading = ref(false);
const tasks = ref<ExportTaskItem[]>([]);
const pager = ref({ page: 1, pageSize: 10, total: 0 });
let pollTimer: number | null = null;

function count(value: number): string {
  return value.toLocaleString('zh-CN');
}

function formatSize(bytes: number | null): string {
  if (!bytes) {
    return '—';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await props.pageTasks({ page: pager.value.page, pageSize: pager.value.pageSize });
    tasks.value = data.list;
    pager.value.total = data.total;
  } catch {
    /* 列表加载失败由全局提示接管，保留旧数据 */
  } finally {
    loading.value = false;
  }
}

function hasActiveTasks(): boolean {
  return tasks.value.some((task) => task.status === 'PENDING' || task.status === 'RUNNING');
}

function stopPolling(): void {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
}

function startPolling(): void {
  stopPolling();
  pollTimer = window.setInterval(() => {
    if (props.modelValue && hasActiveTasks()) {
      void load();
    }
  }, 2000);
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      pager.value.page = 1;
      void load();
      startPolling();
    } else {
      stopPolling();
    }
  },
);

/** 抽屉已打开时重新从第一页加载（例如再次提交导出后）。 */
function reload(): void {
  pager.value.page = 1;
  void load();
}

defineExpose({ reload });

onBeforeUnmount(stopPolling);

async function cancel(task: ExportTaskItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定取消导出任务「${task.fileName}」吗？`, '取消导出', {
      type: 'warning',
      customClass: 'confirm-dialog-440',
    });
  } catch {
    return;
  }
  await props.cancelTask(task.id);
  ElMessage.success('已请求取消');
  await load();
}

async function download(task: ExportTaskItem): Promise<void> {
  try {
    const file = await props.downloadTask(task.id);
    saveBlob(file.blob, file.fileName ?? task.fileName);
  } catch (error) {
    if (error instanceof ApiError) {
      ElMessage.error(error.message);
    }
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="导出任务"
    size="760px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-table v-loading="loading" :data="tasks" row-key="id">
      <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS_TYPES[row.status as ExportTaskStatus]" size="small">
            {{ STATUS_LABELS[row.status as ExportTaskStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="140">
        <template #default="{ row }">
          <el-progress v-if="row.status === 'RUNNING'" :percentage="row.progress" :stroke-width="6" />
          <span v-else class="task-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="行数" width="90">
        <template #default="{ row }">{{ count(row.totalRows) }}</template>
      </el-table-column>
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'SUCCESS'" link type="primary" @click="download(row)">下载</el-button>
          <el-button
            v-else-if="row.status === 'PENDING' || row.status === 'RUNNING'"
            link
            type="primary"
            @click="cancel(row)"
          >
            取消
          </el-button>
          <span v-else-if="row.status === 'FAILED'" class="task-error" :title="row.errorMessage">
            {{ row.errorMessage || '导出失败' }}
          </span>
          <span v-else class="task-muted">—</span>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-pagination">
      <el-pagination
        v-model:current-page="pager.page"
        :page-size="pager.pageSize"
        :total="pager.total"
        layout="total, prev, pager, next"
        background
        @current-change="load"
      />
    </div>
  </el-drawer>
</template>

<style scoped>
.table-pagination {
  padding: var(--space-4) var(--space-5);
}

.task-muted {
  color: var(--color-text-tertiary);
}

.task-error {
  display: inline-block;
  max-width: 120px;
  overflow: hidden;
  color: var(--el-color-danger);
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
