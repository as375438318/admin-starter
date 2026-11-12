<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, type FormRules } from 'element-plus';
import { deptApi } from '@/api/dept';
import { demoTaskApi } from '@/api/demoTask';
import { userApi } from '@/api/user';
import { ApiError, saveBlob } from '@/api/http';
import type { DemoTaskItem, DemoTaskStatus, DeptOption, UserOption } from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import { createOptionReporter } from '@/utils/option-load';
import TableSkeleton from '@/components/TableSkeleton.vue';
import ImportDialog from '@/components/business/ImportDialog.vue';
import ExportTaskDrawer from '@/components/business/ExportTaskDrawer.vue';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useTableColumns, type ColumnDefinition } from '@/composables/use-table-columns';
import ColumnSettings from '@/components/ColumnSettings.vue';
import { useCrudDrawer } from '@/composables/use-crud-drawer';

interface DemoTaskFilters {
  keyword: string;
  deptId?: number;
  status?: DemoTaskStatus;
}

interface DemoTaskForm {
  title: string;
  status: DemoTaskStatus;
  deptId?: number;
  ownerId?: number;
}

const STATUS_LABELS: Record<DemoTaskStatus, string> = { TODO: '待处理', DOING: '进行中', DONE: '已完成' };
const STATUS_TYPES: Record<DemoTaskStatus, 'info' | 'warning' | 'success'> = {
  TODO: 'info',
  DOING: 'warning',
  DONE: 'success',
};

function count(value: number): string {
  return value.toLocaleString('zh-CN');
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
} = usePagedList<DemoTaskItem, DemoTaskFilters>({
  initialFilters: { keyword: '', deptId: undefined, status: undefined },
  fetcher: (params) =>
    demoTaskApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      deptId: params.deptId,
      status: params.status,
      sort: params.sort,
      order: params.order,
    }),
});

const deptOptions = ref<DeptOption[]>([]);
const ownerOptions = ref<UserOption[]>([]);
const reportOptionFailure = createOptionReporter();

const {
  visible: drawerVisible,
  isEdit,
  submitting,
  formRef,
  form,
  fieldError,
  openCreate,
  openEdit: openEditDrawer,
  close,
  submit: submitDrawer,
} = useCrudDrawer<DemoTaskForm>({
  emptyForm: () => ({ title: '', status: 'TODO', deptId: undefined, ownerId: undefined }),
  onOpen: () => {
    void loadOptions();
  },
});

const rules: FormRules = {
  title: [
    { required: true, message: '任务标题不能为空', trigger: 'blur' },
    { max: 128, message: '任务标题不能超过 128 字', trigger: 'blur' },
  ],
  status: [{ required: true, message: '任务状态不能为空', trigger: 'change' }],
  deptId: [{ required: true, message: '归属部门不能为空', trigger: 'change' }],
};

async function loadOptions(): Promise<void> {
  if (deptOptions.value.length === 0) {
    try {
      deptOptions.value = await deptApi.options();
    } catch (error) {
      reportOptionFailure('部门选项', error);
    }
  }
  if (ownerOptions.value.length === 0) {
    try {
      ownerOptions.value = await userApi.options();
    } catch (error) {
      reportOptionFailure('负责人选项', error);
    }
  }
}

async function searchOwners(keyword: string): Promise<void> {
  try {
    ownerOptions.value = await userApi.options(keyword || undefined);
  } catch {
    /* 搜索失败时保留原选项 */
  }
}

async function openEdit(row: DemoTaskItem): Promise<void> {
  openEditDrawer(row.id, {});
  try {
    const detail = await demoTaskApi.get(row.id);
    if (detail.ownerId) {
      const exists = ownerOptions.value.some((item) => item.id === detail.ownerId);
      if (!exists) {
        ownerOptions.value = [
          {
            id: detail.ownerId,
            username: '',
            nickname: detail.ownerName ?? `用户 ${detail.ownerId}`,
            deptId: null,
            deptName: detail.deptName,
          },
          ...ownerOptions.value,
        ];
      }
    }
    Object.assign(form, {
      title: detail.title,
      status: detail.status,
      deptId: detail.deptId,
      ownerId: detail.ownerId ?? undefined,
    });
  } catch {
    close();
  }
}

async function submit(): Promise<void> {
  const saved = await submitDrawer(async ({ isEdit: editing, editingId: id, form: values }) => {
    const payload = {
      title: values.title,
      status: values.status,
      deptId: values.deptId as number,
      ownerId: values.ownerId ?? null,
    };
    if (editing && id !== null) {
      await demoTaskApi.update(id, payload);
      ElMessage.success('任务已更新');
    } else {
      await demoTaskApi.create(payload);
      ElMessage.success('任务已创建');
    }
  });
  if (saved) {
    await load();
  }
}

async function remove(row: DemoTaskItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确定删除任务「${row.title}」吗？删除后不可恢复。`,
    title: '删除任务',
    customClass: 'confirm-dialog-440',
  });
  if (!confirmed) {
    return;
  }
  await demoTaskApi.remove(row.id);
  ElMessage.success('任务已删除');
  if (rows.value.length === 1 && page.page > 1) {
    page.page -= 1;
  }
  await load();
}

onMounted(() => {
  void load();
  void loadOptions();
});

/* ------------------------------ 模板下载 / 导入 ------------------------------ */

const importVisible = ref(false);
const importDialogRef = ref<InstanceType<typeof ImportDialog>>();

/** 工具栏按钮复用对话框内的模板下载实现，避免第二份逻辑。 */
function downloadTemplate(): void {
  importDialogRef.value?.downloadTemplate();
}

function openImport(): void {
  importVisible.value = true;
}

/* ------------------------------ 导出 / 导出任务 ------------------------------ */

/** 列定义（唯一定义处）：sortKey 取自 constants/sort-whitelist.ts 的 demoTask 白名单 */
const DEMO_TASK_COLUMNS: ColumnDefinition[] = [
  { key: 'title', label: '任务标题', sortKey: 'title', alwaysVisible: true },
  { key: 'status', label: '状态', sortKey: 'status' },
  { key: 'deptName', label: '归属部门' },
  { key: 'ownerName', label: '负责人' },
  { key: 'createdAt', label: '创建时间', sortKey: 'createdAt' },
  { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },
];

const table = useTableColumns({
  storageKey: 'demo-task',
  columns: DEMO_TASK_COLUMNS,
  onSort: (sort, order) => onSortChange(sort, order),
});

const exporting = ref(false);
const exportTasksVisible = ref(false);
const exportTasksRef = ref<InstanceType<typeof ExportTaskDrawer>>();

async function startExport(): Promise<void> {
  if (exporting.value) {
    return;
  }
  exporting.value = true;
  try {
    const result = await demoTaskApi.startExport({
      keyword: filters.keyword || undefined,
      status: filters.status,
      deptId: filters.deptId,
    });
    if (result.kind === 'file') {
      saveBlob(result.blob, result.fileName ?? '示例任务.xlsx');
      ElMessage.success('导出完成');
    } else {
      ElMessage.success(
        `数据量较大（${count(result.data.totalRows)} 行），已转入后台导出，可在「导出任务」中查看`,
      );
      if (exportTasksVisible.value) {
        exportTasksRef.value?.reload();
      } else {
        exportTasksVisible.value = true;
      }
    }
  } catch (error) {
    if (error instanceof ApiError) {
      ElMessage.error(error.message);
    }
  } finally {
    exporting.value = false;
  }
}

function openExportTasks(): void {
  exportTasksVisible.value = true;
}
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-input
          v-model="filters.keyword"
          class="filter-keyword"
          placeholder="搜索任务标题"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-tree-select
          v-model="filters.deptId"
          class="filter-dept"
          :data="deptOptions"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="归属部门"
          clearable
          check-strictly
          @change="search"
        />
        <el-select v-model="filters.status" class="filter-status" placeholder="状态" clearable @change="search">
          <el-option v-for="(label, value) in STATUS_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <ColumnSettings
          :columns="DEMO_TASK_COLUMNS"
          :is-visible="table.isVisible"
          :hidden-count="table.hiddenCount.value"
          @toggle="table.toggleColumn"
          @show-all="table.showAllColumns"
          @invert="table.invertColumns"
          @reset="table.resetColumns"
        />
        <el-button
          v-permission="'demo:task:import'"
          :loading="importDialogRef?.templateLoading"
          :disabled="importDialogRef?.templateLoading"
          @click="downloadTemplate"
        >
          下载模板
        </el-button>
        <el-button v-permission="'demo:task:import'" @click="openImport">导入</el-button>
        <el-button
          v-permission="'demo:task:export'"
          :loading="exporting"
          :disabled="exporting"
          @click="startExport"
        >
          导出
        </el-button>
        <el-button v-permission="'demo:task:export'" @click="openExportTasks">导出任务</el-button>
        <el-button v-permission="'demo:task:create'" type="primary" @click="openCreate">新增任务</el-button>
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
            <p class="empty-title">{{ hasFilter ? '没有匹配的任务' : '暂无任务' }}</p>
            <p class="empty-desc">
              {{ hasFilter ? '调整关键词、部门或状态后再试。' : '新增任务后即可在这里维护。' }}
            </p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
            <el-button v-else v-permission="'demo:task:create'" size="small" type="primary" @click="openCreate">
              新增任务
            </el-button>
          </div>
        </template>
        <el-table-column
          v-if="table.isVisible('title')"
          prop="title"
          label="任务标题"
          min-width="240"
          fixed="left"
          show-overflow-tooltip
          :sortable="table.sortableOf('title')"
        />
        <el-table-column v-if="table.isVisible('status')" prop="status" label="状态" width="100" :sortable="table.sortableOf('status')">
          <template #default="{ row }">
            <el-tag :type="STATUS_TYPES[row.status as DemoTaskStatus]" size="small">
              {{ STATUS_LABELS[row.status as DemoTaskStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('deptName')" prop="deptName" label="归属部门" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('ownerName')" prop="ownerName" label="负责人" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ownerName || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('createdAt')" prop="createdAt" label="创建时间" width="160" :sortable="table.sortableOf('createdAt')">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('ops')" prop="ops" label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-permission="'demo:task:update'" link type="primary" @click="openEdit(row)">
                编辑
              </el-button>
              <el-button v-permission="'demo:task:delete'" link type="primary" @click="remove(row)">
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

    <el-drawer v-model="drawerVisible" :title="isEdit ? '编辑任务' : '新增任务'" size="480px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" label-position="left">
        <el-form-item label="任务标题" prop="title" :error="fieldError('title')">
          <el-input v-model="form.title" maxlength="128" placeholder="请输入任务标题" />
        </el-form-item>
        <el-form-item label="任务状态" prop="status" :error="fieldError('status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(label, value) in STATUS_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属部门" prop="deptId" :error="fieldError('deptId')">
          <el-tree-select
            v-model="form.deptId"
            :data="deptOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="请选择部门"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="负责人" prop="ownerId" :error="fieldError('ownerId')">
          <el-select
            v-model="form.ownerId"
            filterable
            remote
            clearable
            placeholder="按姓名或账号搜索"
            :remote-method="searchOwners"
            style="width: 100%"
          >
            <el-option
              v-for="item in ownerOptions"
              :key="item.id"
              :label="item.deptName ? `${item.nickname}（${item.deptName}）` : item.nickname"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">保存</el-button>
      </template>
    </el-drawer>

    <ImportDialog
      ref="importDialogRef"
      v-model="importVisible"
      title="导入示例任务"
      subject="示例任务"
      template-file-name="示例任务导入模板.xlsx"
      :load-template="demoTaskApi.importTemplate"
      :import-file="demoTaskApi.importFile"
      @imported="load"
    />

    <ExportTaskDrawer
      ref="exportTasksRef"
      v-model="exportTasksVisible"
      :page-tasks="demoTaskApi.exportTaskPage"
      :cancel-task="demoTaskApi.cancelExportTask"
      :download-task="demoTaskApi.downloadExportFile"
    />
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

.filter-dept {
  width: 200px;
}

.filter-status {
  width: 140px;
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
