<script setup lang="ts">
import { onMounted } from 'vue';
import { ElMessage, type FormRules } from 'element-plus';
import { configApi } from '@/api/config';
import type { ConfigItem } from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useCrudDrawer } from '@/composables/use-crud-drawer';

interface ConfigFilters {
  keyword: string;
  isSensitive?: boolean;
}

interface ConfigForm {
  configKey: string;
  configValue: string;
  name: string;
  remark: string;
  isSensitive: boolean;
  isEnabled: boolean;
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
} = usePagedList<ConfigItem, ConfigFilters>({
  initialFilters: { keyword: '', isSensitive: undefined },
  fetcher: (params) =>
    configApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      isSensitive: params.isSensitive,
    }),
});

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
} = useCrudDrawer<ConfigForm>({
  emptyForm: () => ({ configKey: '', configValue: '', name: '', remark: '', isSensitive: false, isEnabled: true }),
});

const rules: FormRules = {
  configKey: [
    { required: true, message: '参数键不能为空', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_.-]{2,128}$/,
      message: '参数键需为 2-128 位字母、数字、点、下划线或短横线',
      trigger: 'blur',
    },
  ],
  name: [
    { required: true, message: '参数名称不能为空', trigger: 'blur' },
    { max: 64, message: '参数名称不能超过 64 字', trigger: 'blur' },
  ],
  configValue: [{ required: true, message: '参数值不能为空', trigger: 'blur' }],
};

function openEdit(row: ConfigItem): void {
  openEditDrawer(row.id, {
    configKey: row.configKey,
    configValue: row.configValue,
    name: row.name,
    remark: row.remark ?? '',
    isSensitive: row.isSensitive,
    isEnabled: row.isEnabled,
  });
}

async function submit(): Promise<void> {
  const saved = await submitDrawer(async ({ isEdit: editing, editingId: id, form: values }) => {
    const payload = {
      configKey: values.configKey,
      configValue: values.configValue,
      name: values.name,
      remark: values.remark || undefined,
      isSensitive: values.isSensitive,
      isEnabled: values.isEnabled,
    };
    if (editing && id !== null) {
      await configApi.update(id, payload);
      ElMessage.success('参数已更新');
    } else {
      await configApi.create(payload);
      ElMessage.success('参数已创建');
    }
  });
  if (saved) {
    await load();
  }
}

async function remove(row: ConfigItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确定删除参数「${row.name}」吗？引用该键的业务读取将失败，且不可恢复。`,
    title: '删除参数',
    confirmText: '删除',
    customClass: 'confirm-dialog-440',
  });
  if (!confirmed) {
    return;
  }
  await configApi.remove(row.id);
  ElMessage.success('参数已删除');
  if (rows.value.length === 1 && page.page > 1) {
    page.page -= 1;
  }
  await load();
}

function displayValue(row: ConfigItem): string {
  if (row.isSensitive) {
    return '••••••••';
  }
  return row.configValue;
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
          placeholder="搜索参数名称或键"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="filters.isSensitive" class="filter-status" placeholder="敏感性" clearable @change="search">
          <el-option label="敏感参数" :value="true" />
          <el-option label="普通参数" :value="false" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <el-button v-permission="'system:config:create'" type="primary" @click="openCreate">新增参数</el-button>
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
            <p class="empty-title">{{ hasFilter ? '没有匹配的参数' : '暂无系统参数' }}</p>
            <p class="empty-desc">
              {{ hasFilter ? '调整关键词或敏感性后再试。' : '新增参数后即可集中管理系统配置。' }}
            </p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
            <el-button v-else v-permission="'system:config:create'" size="small" type="primary" @click="openCreate">
              新增参数
            </el-button>
          </div>
        </template>
        <el-table-column prop="name" label="参数名称" width="200" fixed="left" show-overflow-tooltip />
        <el-table-column prop="configKey" label="参数键" width="220" show-overflow-tooltip />
        <el-table-column label="参数值" width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'sensitive-value': row.isSensitive }">{{ displayValue(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="敏感性" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isSensitive" type="warning" size="small">敏感</el-tag>
            <el-tag v-else type="info" size="small">普通</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
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
              <el-button v-permission="'system:config:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-dropdown v-permission="'system:config:delete'" trigger="click">
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

    <el-drawer v-model="drawerVisible" :title="isEdit ? '编辑参数' : '新增参数'" size="480px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" label-position="left">
        <el-form-item label="参数名称" prop="name" :error="fieldError('name')">
          <el-input v-model="form.name" maxlength="64" placeholder="请输入参数名称" />
        </el-form-item>
        <el-form-item label="参数键" prop="configKey" :error="fieldError('configKey')">
          <el-input v-model="form.configKey" maxlength="128" placeholder="如 system.name" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="参数值" prop="configValue" :error="fieldError('configValue')">
          <el-input v-model="form.configValue" type="textarea" :rows="4" placeholder="请输入参数值" />
        </el-form-item>
        <el-form-item label="敏感性" prop="isSensitive">
          <el-switch v-model="form.isSensitive" active-text="敏感" inactive-text="普通" />
          <span class="field-hint">敏感参数的列表值会打码展示</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark" :error="fieldError('remark')">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" placeholder="可选" />
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

.sensitive-value {
  color: var(--color-text-tertiary);
  letter-spacing: 1px;
}

.field-hint {
  margin-left: var(--space-3);
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
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
