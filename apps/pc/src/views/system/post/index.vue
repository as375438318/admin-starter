<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, type FormRules } from 'element-plus';
import { deptApi } from '@/api/dept';
import { postApi } from '@/api/post';
import { ApiError, saveBlob } from '@/api/http';
import type { DeptOption, PostItem } from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import { createOptionReporter } from '@/utils/option-load';
import TableSkeleton from '@/components/TableSkeleton.vue';
import ImportDialog from '@/components/business/ImportDialog.vue';
import ExportTaskDrawer from '@/components/business/ExportTaskDrawer.vue';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useCrudDrawer } from '@/composables/use-crud-drawer';

interface PostFilters {
  keyword: string;
  deptId?: number;
  isEnabled?: boolean;
}

interface PostForm {
  deptId?: number;
  code: string;
  name: string;
  sort: number;
  isEnabled: boolean;
}

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
} = usePagedList<PostItem, PostFilters>({
  initialFilters: { keyword: '', deptId: undefined, isEnabled: undefined },
  fetcher: (params) =>
    postApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      deptId: params.deptId,
      isEnabled: params.isEnabled,
    }),
});

const deptOptions = ref<DeptOption[]>([]);
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
  submit: submitDrawer,
} = useCrudDrawer<PostForm>({
  emptyForm: () => ({ deptId: undefined, code: '', name: '', sort: 0, isEnabled: true }),
  onOpen: () => {
    void loadOptions();
  },
});

const rules: FormRules = {
  deptId: [{ required: true, message: '所属部门不能为空', trigger: 'change' }],
  code: [
    { required: true, message: '岗位编码不能为空', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{2,64}$/, message: '岗位编码需为 2-64 位字母、数字或下划线', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '岗位名称不能为空', trigger: 'blur' },
    { max: 64, message: '岗位名称不能超过 64 字', trigger: 'blur' },
  ],
};

function openEdit(row: PostItem): void {
  openEditDrawer(row.id, {
    deptId: row.deptId,
    code: row.code,
    name: row.name,
    sort: row.sort,
    isEnabled: row.isEnabled,
  });
}

async function loadOptions(): Promise<void> {
  if (deptOptions.value.length > 0) {
    return;
  }
  try {
    deptOptions.value = await deptApi.options();
  } catch (error) {
    reportOptionFailure('部门选项', error);
  }
}

async function submit(): Promise<void> {
  const saved = await submitDrawer(async ({ isEdit: editing, editingId: id, form: values }) => {
    const payload = {
      deptId: values.deptId as number,
      code: values.code,
      name: values.name,
      sort: values.sort,
      isEnabled: values.isEnabled,
    };
    if (editing && id !== null) {
      await postApi.update(id, payload);
      ElMessage.success('岗位已更新');
    } else {
      await postApi.create(payload);
      ElMessage.success('岗位已创建');
    }
  });
  if (saved) {
    await load();
  }
}

async function remove(row: PostItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确定删除岗位「${row.name}」吗？删除后关联人员将不再显示该岗位，且不可恢复。`,
    title: '删除岗位',
    customClass: 'confirm-dialog-440',
  });
  if (!confirmed) {
    return;
  }
  await postApi.remove(row.id);
  ElMessage.success('岗位已删除');
  if (rows.value.length === 1 && page.page > 1) {
    page.page -= 1;
  }
  await load();
}

/* ------------------------------ 模板下载 / 导入 ------------------------------ */

const importVisible = ref(false);
const importDialogRef = ref<InstanceType<typeof ImportDialog>>();
const exportTasksVisible = ref(false);
const exportTasksRef = ref<InstanceType<typeof ExportTaskDrawer>>();
const exporting = ref(false);

/** 工具栏按钮复用对话框内的模板下载实现，避免第二份逻辑。 */
function downloadTemplate(): void {
  importDialogRef.value?.downloadTemplate();
}

function openImport(): void {
  importVisible.value = true;
}

/* ------------------------------ 导出 / 导出任务 ------------------------------ */

async function startExport(): Promise<void> {
  if (exporting.value) {
    return;
  }
  exporting.value = true;
  try {
    const result = await postApi.startExport({
      keyword: filters.keyword || undefined,
      deptId: filters.deptId,
      isEnabled: filters.isEnabled,
    });
    if (result.kind === 'file') {
      saveBlob(result.blob, result.fileName ?? '岗位.xlsx');
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

onMounted(() => {
  void load();
  void loadOptions();
});
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-input
          v-model="filters.keyword"
          class="filter-keyword"
          placeholder="搜索岗位名称或编码"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-tree-select
          v-model="filters.deptId"
          class="filter-dept"
          :data="deptOptions"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="所属部门"
          clearable
          check-strictly
          @change="search"
        />
        <el-select v-model="filters.isEnabled" class="filter-status" placeholder="状态" clearable @change="search">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <el-button
          v-permission="'system:post:import'"
          :loading="importDialogRef?.templateLoading"
          :disabled="importDialogRef?.templateLoading"
          @click="downloadTemplate"
        >
          下载模板
        </el-button>
        <el-button v-permission="'system:post:import'" @click="openImport">导入</el-button>
        <el-button
          v-permission="'system:post:export'"
          :loading="exporting"
          :disabled="exporting"
          @click="startExport"
        >
          导出
        </el-button>
        <el-button v-permission="'system:post:export'" @click="openExportTasks">导出任务</el-button>
        <el-button v-permission="'system:post:create'" type="primary" @click="openCreate">新增岗位</el-button>
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
            <p class="empty-title">{{ hasFilter ? '没有匹配的岗位' : '暂无岗位' }}</p>
            <p class="empty-desc">
              {{ hasFilter ? '调整关键词、部门或状态后再试。' : '新增岗位后即可在这里维护。' }}
            </p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
            <el-button v-else v-permission="'system:post:create'" size="small" type="primary" @click="openCreate">
              新增岗位
            </el-button>
          </div>
        </template>
        <el-table-column prop="name" label="岗位名称" width="200" fixed="left" show-overflow-tooltip />
        <el-table-column prop="code" label="岗位编码" width="150" show-overflow-tooltip />
        <el-table-column prop="deptName" label="所属部门" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="right" class-name="tabular-nums" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
              {{ row.isEnabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-permission="'system:post:update'" link type="primary" @click="openEdit(row)">
                编辑
              </el-button>
              <el-dropdown v-permission="'system:post:delete'" trigger="click">
                <el-button link type="primary">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="remove(row)">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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

    <el-drawer v-model="drawerVisible" :title="isEdit ? '编辑岗位' : '新增岗位'" size="480px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" label-position="left">
        <el-form-item label="所属部门" prop="deptId" :error="fieldError('deptId')">
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
        <el-form-item label="岗位编码" prop="code" :error="fieldError('code')">
          <el-input v-model="form.code" maxlength="64" placeholder="请输入岗位编码" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="岗位名称" prop="name" :error="fieldError('name')">
          <el-input v-model="form.name" maxlength="64" placeholder="请输入岗位名称" />
        </el-form-item>
        <el-form-item label="排序" prop="sort" :error="fieldError('sort')">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态" prop="isEnabled">
          <el-switch v-model="form.isEnabled" active-text="启用" inactive-text="停用" />
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
      title="导入岗位"
      subject="岗位"
      template-file-name="岗位导入模板.xlsx"
      :load-template="postApi.importTemplate"
      :import-file="postApi.importFile"
      @imported="load"
    />

    <ExportTaskDrawer
      ref="exportTasksRef"
      v-model="exportTasksVisible"
      :page-tasks="postApi.exportTaskPage"
      :cancel-task="postApi.cancelExportTask"
      :download-task="postApi.downloadExportFile"
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
