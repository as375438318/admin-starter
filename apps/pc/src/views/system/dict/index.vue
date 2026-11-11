<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { ElMessage, type FormRules } from 'element-plus';
import { dictApi } from '@/api/dict';
import { ApiError } from '@/api/http';
import type { DictItemRecord, DictTypeItem } from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { useDelayedSkeleton } from '@/utils/skeleton';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useCrudDrawer } from '@/composables/use-crud-drawer';

interface DictFilters {
  keyword: string;
  isEnabled?: boolean;
}

interface DictTypeForm {
  name: string;
  type: string;
  remark: string;
  isEnabled: boolean;
}

interface DictItemForm {
  label: string;
  value: string;
  sort: number;
  isEnabled: boolean;
  remark: string;
}

const selectedType = ref<DictTypeItem | null>(null);

const {
  page,
  filters,
  rows: types,
  loading: loadingTypes,
  loadError: typesError,
  skeletonVisible: typesSkeletonVisible,
  hasFilter,
  load: loadTypes,
  search,
  resetFilters,
  onPageSizeChange,
} = usePagedList<DictTypeItem, DictFilters>({
  initialFilters: { keyword: '', isEnabled: undefined },
  fetcher: async (params) => {
    const data = await dictApi.pageTypes({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      isEnabled: params.isEnabled,
    });
    // 选中项优先保持，不存在时回落到首行
    const stillExists = selectedType.value
      ? data.list.find((item) => item.id === selectedType.value?.id)
      : undefined;
    selectedType.value = stillExists ?? data.list[0] ?? null;
    return data;
  },
});

const items = ref<DictItemRecord[]>([]);
const loadingItems = ref(false);
const itemsSkeletonVisible = useDelayedSkeleton(loadingItems);
const itemsError = ref('');

const typeRules: FormRules = {
  name: [
    { required: true, message: '字典名称不能为空', trigger: 'blur' },
    { max: 64, message: '字典名称不能超过 64 字', trigger: 'blur' },
  ],
  type: [
    { required: true, message: '字典类型不能为空', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]{2,64}$/, message: '字典类型需为 2-64 位字母、数字、下划线或短横线', trigger: 'blur' },
  ],
};

const {
  visible: itemDrawerVisible,
  isEdit: isEditItem,
  submitting: itemSubmitting,
  formRef: itemFormRef,
  form: itemForm,
  fieldError: itemError,
  openCreate: openCreateItemDrawer,
  openEdit: openEditItemDrawer,
  submit: submitItemDrawer,
} = useCrudDrawer<DictItemForm>({
  emptyForm: () => ({ label: '', value: '', sort: 0, isEnabled: true, remark: '' }),
});

const itemRules: FormRules = {
  label: [
    { required: true, message: '字典标签不能为空', trigger: 'blur' },
    { max: 64, message: '字典标签不能超过 64 字', trigger: 'blur' },
  ],
  value: [
    { required: true, message: '字典值不能为空', trigger: 'blur' },
    { max: 64, message: '字典值不能超过 64 字', trigger: 'blur' },
  ],
};

const {
  visible: typeDrawerVisible,
  isEdit: isEditType,
  submitting: typeSubmitting,
  formRef: typeFormRef,
  form: typeForm,
  fieldError: typeError,
  openCreate: openCreateType,
  openEdit: openEditTypeDrawer,
  submit: submitTypeDrawer,
} = useCrudDrawer<DictTypeForm>({
  emptyForm: () => ({ name: '', type: '', remark: '', isEnabled: true }),
});

async function loadItems(): Promise<void> {
  if (!selectedType.value) {
    items.value = [];
    return;
  }
  loadingItems.value = true;
  itemsError.value = '';
  try {
    items.value = await dictApi.items(selectedType.value.id);
  } catch (error) {
    items.value = [];
    itemsError.value = error instanceof ApiError ? error.message : '字典项加载失败';
  } finally {
    loadingItems.value = false;
  }
}

function selectType(row: DictTypeItem): void {
  selectedType.value = row;
}

function rowClassName({ row }: { row: DictTypeItem }): string {
  return row.id === selectedType.value?.id ? 'row-current' : '';
}

function openEditType(row: DictTypeItem): void {
  openEditTypeDrawer(row.id, {
    name: row.name,
    type: row.type,
    remark: row.remark ?? '',
    isEnabled: row.isEnabled,
  });
}

async function submitType(): Promise<void> {
  const saved = await submitTypeDrawer(async ({ isEdit: editing, editingId: id }) => {
    const payload = {
      name: typeForm.name,
      type: typeForm.type,
      remark: typeForm.remark || undefined,
      isEnabled: typeForm.isEnabled,
    };
    if (editing && id !== null) {
      await dictApi.updateType(id, payload);
      ElMessage.success('字典类型已更新');
    } else {
      await dictApi.createType(payload);
      ElMessage.success('字典类型已创建');
    }
  });
  if (saved) {
    await loadTypes();
    await loadItems();
  }
}

async function removeType(row: DictTypeItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确定删除字典类型「${row.name}」吗？其下全部字典项会一并删除，且不可恢复。`,
    title: '删除字典类型',
    customClass: 'confirm-dialog-440',
  });
  if (!confirmed) {
    return;
  }
  await dictApi.removeType(row.id);
  ElMessage.success('字典类型已删除');
  await loadTypes();
  await loadItems();
}

function openCreateItem(): void {
  if (!selectedType.value) {
    ElMessage.warning('请先选择字典类型');
    return;
  }
  openCreateItemDrawer();
}

function openEditItem(row: DictItemRecord): void {
  openEditItemDrawer(row.id, {
    label: row.label,
    value: row.value,
    sort: row.sort,
    isEnabled: row.isEnabled,
    remark: row.remark ?? '',
  });
}

async function submitItem(): Promise<void> {
  if (!selectedType.value) {
    return;
  }
  const dictTypeId = selectedType.value.id;
  const saved = await submitItemDrawer(async ({ isEdit: editing, editingId: id }) => {
    const payload = {
      dictTypeId,
      label: itemForm.label,
      value: itemForm.value,
      sort: itemForm.sort,
      isEnabled: itemForm.isEnabled,
      remark: itemForm.remark || undefined,
    };
    if (editing && id !== null) {
      await dictApi.updateItem(id, payload);
      ElMessage.success('字典项已更新');
    } else {
      await dictApi.createItem(payload);
      ElMessage.success('字典项已创建');
    }
  });
  if (saved) {
    await loadItems();
  }
}

async function removeItem(row: DictItemRecord): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确定删除字典项「${row.label}」吗？使用该值的页面可能无法继续解析，且不可恢复。`,
    title: '删除字典项',
    customClass: 'confirm-dialog-440',
  });
  if (!confirmed) {
    return;
  }
  await dictApi.removeItem(row.id);
  ElMessage.success('字典项已删除');
  await loadItems();
}

watch(selectedType, () => {
  void loadItems();
});

onMounted(() => {
  void loadTypes();
});
</script>

<template>
  <div class="page-container">
    <div class="dict-layout">
      <div class="dict-left">
        <div class="page-toolbar dict-toolbar">
          <div class="toolbar-filters">
            <el-input
              v-model="filters.keyword"
              class="filter-keyword"
              placeholder="搜索字典名称或类型"
              clearable
              @keyup.enter="search"
              @clear="search"
            />
            <el-select v-model="filters.isEnabled" class="filter-status" placeholder="状态" clearable @change="search">
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
            </el-select>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </div>
          <div class="toolbar-actions">
            <el-button v-permission="'system:dict:create'" type="primary" @click="openCreateType">新增类型</el-button>
          </div>
        </div>

        <div class="page-card">
          <el-alert v-if="typesError" class="table-error" type="error" :closable="false" show-icon>
            <template #title>字典类型加载失败：{{ typesError }}</template>
            <el-button class="error-retry" size="small" @click="loadTypes">重试</el-button>
          </el-alert>

          <el-table
            :data="typesSkeletonVisible ? [] : types"
            row-key="id"
            highlight-current-row
            :row-class-name="rowClassName"
            style="width: 100%"
            @row-click="selectType"
          >
            <template #empty>
              <TableSkeleton v-if="typesSkeletonVisible" :rows="6" />
              <div v-else-if="!loadingTypes" class="table-empty">
                <p class="empty-title">{{ hasFilter ? '没有匹配的字典类型' : '暂无字典类型' }}</p>
                <p class="empty-desc">
                  {{ hasFilter ? '调整关键词或状态后再试。' : '新增字典类型后即可维护字典项。' }}
                </p>
                <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
                <el-button v-else v-permission="'system:dict:create'" size="small" type="primary" @click="openCreateType">
                  新增类型
                </el-button>
              </div>
            </template>
            <el-table-column prop="name" label="字典名称" width="150" fixed="left" show-overflow-tooltip />
            <el-table-column prop="type" label="字典类型" width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
                  {{ row.isEnabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <div class="row-actions" @click.stop>
                  <el-button v-permission="'system:dict:update'" link type="primary" @click="openEditType(row)">编辑</el-button>
                  <el-dropdown v-permission="'system:dict:delete'" trigger="click">
                    <el-button link type="primary">更多</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item @click="removeType(row)">删除</el-dropdown-item>
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
              small
              @current-change="loadTypes"
              @size-change="onPageSizeChange"
            />
          </div>
        </div>
      </div>

      <div class="dict-right">
        <div class="page-card dict-items-card">
          <div class="items-header">
            <div>
              <h3 class="items-title">字典项</h3>
              <p class="items-subtitle">
                {{ selectedType ? `${selectedType.name} · ${selectedType.type}` : '请从左侧选择字典类型' }}
              </p>
            </div>
            <el-button
              v-permission="'system:dict:create'"
              type="primary"
              :disabled="!selectedType"
              @click="openCreateItem"
            >
              新增字典项
            </el-button>
          </div>

          <el-alert v-if="itemsError" class="table-error" type="error" :closable="false" show-icon>
            <template #title>字典项加载失败：{{ itemsError }}</template>
            <el-button class="error-retry" size="small" @click="loadItems">重试</el-button>
          </el-alert>

          <el-table v-if="selectedType" :data="itemsSkeletonVisible ? [] : items" row-key="id" style="width: 100%">
            <template #empty>
              <TableSkeleton v-if="itemsSkeletonVisible" :rows="5" />
              <div v-else-if="!loadingItems" class="table-empty">
                <p class="empty-title">该字典暂无字典项</p>
                <p class="empty-desc">新增字典项后，业务页面即可按值解析。</p>
                <el-button v-permission="'system:dict:create'" size="small" type="primary" @click="openCreateItem">
                  新增字典项
                </el-button>
              </div>
            </template>
            <el-table-column prop="label" label="标签" width="120" fixed="left" show-overflow-tooltip />
            <el-table-column prop="value" label="值" width="120" show-overflow-tooltip />
            <el-table-column prop="sort" label="排序" width="70" align="right" class-name="tabular-nums" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">
                  {{ row.isEnabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button v-permission="'system:dict:update'" link type="primary" @click="openEditItem(row)">编辑</el-button>
                  <el-dropdown v-permission="'system:dict:delete'" trigger="click">
                    <el-button link type="primary">更多</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item @click="removeItem(row)">删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div v-else class="items-placeholder">
            <p class="empty-title">尚未选择字典类型</p>
            <p class="empty-desc">在左侧点击一行字典类型，右侧会显示其字典项。</p>
          </div>
        </div>
      </div>
    </div>

    <el-drawer
      v-model="typeDrawerVisible"
      :title="isEditType ? '编辑字典类型' : '新增字典类型'"
      size="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="96px" label-position="left">
        <el-form-item label="字典名称" prop="name" :error="typeError('name')">
          <el-input v-model="typeForm.name" maxlength="64" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="字典类型" prop="type" :error="typeError('type')">
          <el-input v-model="typeForm.type" maxlength="64" placeholder="如 user_status" :disabled="isEditType" />
        </el-form-item>
        <el-form-item label="备注" prop="remark" :error="typeError('remark')">
          <el-input v-model="typeForm.remark" type="textarea" :rows="3" maxlength="255" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态" prop="isEnabled">
          <el-switch v-model="typeForm.isEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSubmitting" :disabled="typeSubmitting" @click="submitType">保存</el-button>
      </template>
    </el-drawer>

    <el-drawer
      v-model="itemDrawerVisible"
      :title="isEditItem ? '编辑字典项' : '新增字典项'"
      size="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="96px" label-position="left">
        <el-form-item label="字典标签" prop="label" :error="itemError('label')">
          <el-input v-model="itemForm.label" maxlength="64" placeholder="展示给用户的名称" />
        </el-form-item>
        <el-form-item label="字典值" prop="value" :error="itemError('value')">
          <el-input v-model="itemForm.value" maxlength="64" placeholder="存储与业务使用的值" />
        </el-form-item>
        <el-form-item label="排序" prop="sort" :error="itemError('sort')">
          <el-input-number v-model="itemForm.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="备注" prop="remark" :error="itemError('remark')">
          <el-input v-model="itemForm.remark" type="textarea" :rows="3" maxlength="255" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态" prop="isEnabled">
          <el-switch v-model="itemForm.isEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemSubmitting" :disabled="itemSubmitting" @click="submitItem">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.dict-layout {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: var(--space-4);
  align-items: start;
}

.dict-toolbar {
  margin-bottom: var(--space-3);
}

.toolbar-filters {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.filter-keyword {
  width: 200px;
}

.filter-status {
  width: 120px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.dict-items-card {
  display: flex;
  flex-direction: column;
  min-height: 320px;
}

.items-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-divider);
}

.items-title {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.items-subtitle {
  margin: var(--space-1) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.items-placeholder {
  padding: var(--space-10) var(--space-5);
  text-align: center;
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

:deep(.row-current) {
  background: var(--color-primary-light-9);
}

@media (max-width: 1100px) {
  .dict-layout {
    grid-template-columns: 1fr;
  }
}
</style>
