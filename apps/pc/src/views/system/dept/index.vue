<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { deptApi } from '@/api/dept';
import { ApiError } from '@/api/http';
import type { DeptItem, DeptOption } from '@/api/types';
import { formatDateTime } from '@/utils/datetime';
import { scrollToFirstError } from '@/utils/form';
import { createOptionReporter } from '@/utils/option-load';
import { useDelayedSkeleton } from '@/utils/skeleton';
import TableSkeleton from '@/components/TableSkeleton.vue';

const rows = ref<DeptItem[]>([]);
const loading = ref(false);
const skeletonVisible = useDelayedSkeleton(loading);
const loadError = ref('');
const keyword = ref('');
const statusFilter = ref<boolean | undefined>(undefined);
const reportOptionFailure = createOptionReporter();

const hasFilter = computed(() => Boolean(keyword.value || statusFilter.value !== undefined));

const filteredRows = computed(() => {
  const name = keyword.value.trim();
  if (!name && statusFilter.value === undefined) {
    return rows.value;
  }
  function walk(list: DeptItem[]): DeptItem[] {
    const result: DeptItem[] = [];
    for (const item of list) {
      const children = item.children ? walk(item.children) : [];
      const nameMatch = !name || item.name.includes(name);
      const statusMatch = statusFilter.value === undefined || item.isEnabled === statusFilter.value;
      if ((nameMatch && statusMatch) || children.length > 0) {
        result.push({ ...item, children });
      }
    }
    return result;
  }
  return walk(rows.value);
});

const optionSource = ref<DeptOption[]>([]);

const drawerVisible = ref(false);
const isEdit = ref(false);
const editingId = ref<number | null>(null);
const submitting = ref(false);
const formRef = ref<FormInstance>();
const fieldErrors = ref<Record<string, string>>({});
const form = reactive<{
  parentId: number;
  name: string;
  sort: number;
  leader: string;
  phone: string;
  isEnabled: boolean;
}>({
  parentId: 0,
  name: '',
  sort: 0,
  leader: '',
  phone: '',
  isEnabled: true,
});

const rules: FormRules = {
  name: [
    { required: true, message: '部门名称不能为空', trigger: 'blur' },
    { max: 64, message: '部门名称不能超过 64 字', trigger: 'blur' },
  ],
  phone: [{ max: 32, message: '联系电话不能超过 32 位', trigger: 'blur' }],
};

const parentOptions = computed<DeptOption[]>(() => {
  if (!isEdit.value || editingId.value === null) {
    return optionSource.value;
  }
  const excluded = new Set<number>([editingId.value]);
  function mark(list: DeptOption[]): void {
    for (const item of list) {
      if (excluded.has(item.id)) {
        collect(item);
      }
      if (item.children) {
        mark(item.children);
      }
    }
  }
  function collect(item: DeptOption): void {
    excluded.add(item.id);
    item.children?.forEach(collect);
  }
  mark(optionSource.value);
  function prune(list: DeptOption[]): DeptOption[] {
    return list
      .filter((item) => !excluded.has(item.id))
      .map((item) => ({ ...item, children: item.children ? prune(item.children) : undefined }));
  }
  return prune(optionSource.value);
});

function fieldError(field: string): string {
  return fieldErrors.value[field] ?? '';
}

function collectErrors(error: unknown): void {
  fieldErrors.value = {};
  if (error instanceof ApiError && error.status === 422) {
    for (const item of error.errors) {
      fieldErrors.value[item.field] = item.message;
    }
    return;
  }
  if (error instanceof Error && !(error instanceof ApiError)) {
    ElMessage.error(error.message);
  }
}

async function load(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  try {
    rows.value = await deptApi.tree();
  } catch (error) {
    rows.value = [];
    loadError.value = error instanceof ApiError ? error.message : '部门树加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadOptions(): Promise<void> {
  try {
    optionSource.value = await deptApi.options();
  } catch (error) {
    reportOptionFailure('上级部门选项', error);
  }
}

function search(): void {
  // 树形列表在前端过滤，无需请求
}

function resetFilters(): void {
  keyword.value = '';
  statusFilter.value = undefined;
}

function openCreate(parentId = 0): void {
  isEdit.value = false;
  editingId.value = null;
  Object.assign(form, { parentId, name: '', sort: 0, leader: '', phone: '', isEnabled: true });
  fieldErrors.value = {};
  drawerVisible.value = true;
  void loadOptions();
}

function openEdit(row: DeptItem): void {
  isEdit.value = true;
  editingId.value = row.id;
  Object.assign(form, {
    parentId: row.parentId ?? 0,
    name: row.name,
    sort: row.sort,
    leader: row.leader ?? '',
    phone: row.phone ?? '',
    isEnabled: row.isEnabled,
  });
  fieldErrors.value = {};
  drawerVisible.value = true;
  void loadOptions();
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    await scrollToFirstError(formRef.value);
    return;
  }
  submitting.value = true;
  fieldErrors.value = {};
  try {
    const payload = {
      parentId: form.parentId || 0,
      name: form.name,
      sort: form.sort,
      leader: form.leader || undefined,
      phone: form.phone || undefined,
      isEnabled: form.isEnabled,
    };
    if (isEdit.value && editingId.value !== null) {
      await deptApi.update(editingId.value, payload);
      ElMessage.success('部门已更新');
    } else {
      await deptApi.create(payload);
      ElMessage.success('部门已创建');
    }
    drawerVisible.value = false;
    await load();
  } catch (error) {
    collectErrors(error);
    void scrollToFirstError(formRef.value);
  } finally {
    submitting.value = false;
  }
}

async function remove(row: DeptItem): Promise<void> {
  if (row.children && row.children.length > 0) {
    ElMessage.warning('该部门下存在子部门，请先删除或调整子部门');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定删除部门「${row.name}」吗？删除后该部门下的人员需重新分配，且不可恢复。`,
      '删除部门',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        closeOnClickModal: false,
        customClass: 'confirm-dialog-440',
      },
    );
  } catch {
    return;
  }
  await deptApi.remove(row.id);
  ElMessage.success('部门已删除');
  await load();
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
          v-model="keyword"
          class="filter-keyword"
          placeholder="搜索部门名称"
          clearable
        />
        <el-select
          v-model="statusFilter"
          class="filter-status"
          placeholder="状态"
          clearable
        >
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <el-button v-permission="'system:dept:create'" type="primary" @click="openCreate(0)">
          新增部门
        </el-button>
      </div>
    </div>

    <div class="page-card">
      <el-alert v-if="loadError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>部门树加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table
        :data="skeletonVisible ? [] : filteredRows"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        style="width: 100%"
      >
        <template #empty>
          <TableSkeleton v-if="skeletonVisible" :rows="6" />
          <div v-else-if="!loading" class="table-empty">
            <p class="empty-title">{{ hasFilter ? '没有匹配的部门' : '暂无部门' }}</p>
            <p class="empty-desc">
              {{ hasFilter ? '调整关键词或状态后再试。' : '新增第一个部门后即可在这里维护层级。' }}
            </p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
            <el-button
              v-else
              v-permission="'system:dept:create'"
              size="small"
              type="primary"
              @click="openCreate(0)"
            >
              新增部门
            </el-button>
          </div>
        </template>
        <el-table-column prop="name" label="部门名称" width="260" fixed="left" show-overflow-tooltip />
        <el-table-column prop="leader" label="负责人" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.leader || '—' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" width="160">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
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
              <el-button v-permission="'system:dept:create'" link type="primary" @click="openCreate(row.id)">
                新增下级
              </el-button>
              <el-button v-permission="'system:dept:update'" link type="primary" @click="openEdit(row)">
                编辑
              </el-button>
              <el-dropdown v-permission="'system:dept:delete'" trigger="click">
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
    </div>

    <el-drawer v-model="drawerVisible" :title="isEdit ? '编辑部门' : '新增部门'" size="480px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" label-position="left">
        <el-form-item label="上级部门" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="不选表示顶级部门"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="name" :error="fieldError('name')">
          <el-input v-model="form.name" maxlength="64" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="负责人" prop="leader" :error="fieldError('leader')">
          <el-input v-model="form.leader" maxlength="64" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone" :error="fieldError('phone')">
          <el-input v-model="form.phone" maxlength="32" placeholder="请输入联系电话" />
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
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">
          保存
        </el-button>
      </template>
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
