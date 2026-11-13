<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, type FormRules } from 'element-plus';
import { customerApi, type CustomerItem, type CustomerSavePayload } from '@/api/demoCustomer';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useTableColumns, type ColumnDefinition } from '@/composables/use-table-columns';
import ColumnSettings from '@/components/ColumnSettings.vue';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { formatDateTime } from '@/utils/datetime';

interface Filters {
  keyword: string;
}

const { page, filters, rows, loading, loadError, skeletonVisible, hasFilter, load, search, resetFilters,
  onPageSizeChange, onPageChange, onSortChange } =
  usePagedList<CustomerItem, Filters>({
    initialFilters: { keyword: '' },
    fetcher: (params) => customerApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      // 排序参数必须显式转发，否则排序不生效（断言 #101 第 ② 条）
      sort: params.sort,
      order: params.order,
    }),
  });

const COLUMNS: ColumnDefinition[] = [
  { key: 'name', label: '客户名称', alwaysVisible: true, sortKey: 'name' },
  { key: 'level', label: '客户等级', sortKey: 'level' },
  { key: 'contact', label: '联系人' },
  { key: 'phone', label: '联系电话' },
  { key: 'isEnabled', label: '状态' },
  { key: 'remark', label: '备注' },
  { key: 'createdAt', label: '创建时间', sortKey: 'createdAt' },
  { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },
];

const table = useTableColumns({
  storageKey: 'demo-customer',
  columns: COLUMNS,
  onSort: (sort, order) => onSortChange(sort, order),
});

const drawerVisible = ref(false);
const saving = ref(false);
const editingId = ref<number | null>(null);
const formRef = ref<{ validate: () => Promise<void> }>();
const form = reactive<CustomerSavePayload>({
  name: '',
  level: '',
  contact: '',
  phone: '',
  isEnabled: true,
  remark: '',
});
const formRules: FormRules = {};

function openCreate(): void {
  editingId.value = null;
  Object.assign(form, {
  name: '',
  level: '',
  contact: '',
  phone: '',
  isEnabled: true,
  remark: '',
  });
  drawerVisible.value = true;
}

function openEdit(row: CustomerItem): void {
  editingId.value = row.id;
  Object.assign(form, {
    name: row.name,
    level: row.level,
    contact: row.contact,
    phone: row.phone,
    isEnabled: row.isEnabled,
    remark: row.remark,
  });
  drawerVisible.value = true;
}

async function save(): Promise<void> {
  await formRef.value?.validate();
  saving.value = true;
  try {
    if (editingId.value === null) {
      await customerApi.create({ ...form });
    } else {
      await customerApi.update(editingId.value, { ...form });
    }
    ElMessage.success('保存成功');
    drawerVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function remove(row: CustomerItem): Promise<void> {
  const confirmed = await confirmDelete({
    title: '删除客户档案',
    message: '确认删除该客户档案？此操作不可恢复。',
  });
  if (!confirmed) {
    return;
  }
  await customerApi.remove(row.id);
  ElMessage.success('已删除');
  await load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="page">
    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-input v-model="filters.keyword" class="filter-keyword" placeholder="搜索客户名称 / 联系人" clearable @change="search" />
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
        <el-button v-permission="'demo:customer:create'" type="primary" @click="openCreate">新增客户档案</el-button>
      </div>
    </div>

    <div class="page-card">
      <el-alert v-if="loadError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>列表加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table :data="skeletonVisible ? [] : rows" row-key="id" style="width: 100%" @sort-change="table.onSortChange">
        <template #empty>
          <TableSkeleton v-if="skeletonVisible" :rows="6" />
          <div v-else-if="!loading" class="table-empty">
            <p class="empty-title">{{ hasFilter ? '没有匹配的记录' : '暂无数据' }}</p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
          </div>
        </template>
        <el-table-column
          v-if="table.isVisible('name')"
          prop="name"
          label="客户名称"
          show-overflow-tooltip
          :sortable="table.sortableOf('name')"
        />
        <el-table-column
          v-if="table.isVisible('level')"
          prop="level"
          label="客户等级"
          show-overflow-tooltip
          :sortable="table.sortableOf('level')"
        />
        <el-table-column
          v-if="table.isVisible('contact')"
          prop="contact"
          label="联系人"
          show-overflow-tooltip
        />
        <el-table-column
          v-if="table.isVisible('phone')"
          prop="phone"
          label="联系电话"
          show-overflow-tooltip
        />
        <el-table-column v-if="table.isVisible('isEnabled')" prop="isEnabled" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled ? 'success' : 'info'" disable-transitions>
              {{ row.isEnabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="table.isVisible('remark')"
          prop="remark"
          label="备注"
          show-overflow-tooltip
        />
        <el-table-column v-if="table.isVisible('createdAt')" prop="createdAt" label="创建时间" width="170" :sortable="table.sortableOf('createdAt')">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('ops')" prop="ops" label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-permission="'demo:customer:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-permission="'demo:customer:delete'" link type="danger" @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
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

    <el-drawer v-model="drawerVisible" :title="editingId === null ? '新增客户档案' : '编辑客户档案'" size="420px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <el-form-item label="客户名称" prop="name"
            :rules="[{ required: true, message: '请填写客户名称', trigger: 'blur' }]">
          <el-input v-model="form.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="客户等级" prop="level">
          <el-input v-model="form.level" maxlength="16" />
        </el-form-item>
        <el-form-item label="联系人" prop="contact">
          <el-input v-model="form.contact" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" maxlength="32" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.isEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>
