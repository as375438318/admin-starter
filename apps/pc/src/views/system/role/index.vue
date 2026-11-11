<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, type FormRules } from 'element-plus';
import { deptApi } from '@/api/dept';
import { menuApi } from '@/api/menu';
import { roleApi } from '@/api/role';
import { userApi } from '@/api/user';
import type { DataScope, DeptOption, MenuItem, OwnFilter, RoleItem, UserOption } from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import { loadStatusOptions, type StatusOption } from '@/utils/status-options';
import { createOptionReporter } from '@/utils/option-load';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { useAuthStore } from '@/stores/auth';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useCrudDrawer } from '@/composables/use-crud-drawer';

const auth = useAuthStore();

const scopeLabels: Record<DataScope, string> = {
  ALL: '全部',
  DEPT: '本部门',
  DEPT_AND_CHILD: '本部门及下级',
  SELF: '仅本人',
  CUSTOM_DEPT: '指定部门',
  CUSTOM_USER: '指定人员',
};

const ownLabels: Record<OwnFilter, string> = {
  NONE: '不过滤',
  CREATOR: '仅本人创建',
  OWNER: '仅本人负责',
};

interface RoleFilters {
  keyword: string;
  isEnabled?: boolean;
}

interface RoleForm {
  name: string;
  code: string;
  sort: number;
  remark: string;
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
} = usePagedList<RoleItem, RoleFilters>({
  initialFilters: { keyword: '', isEnabled: undefined },
  fetcher: (params) =>
    roleApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      isEnabled: params.isEnabled,
    }),
});

const statusOptions = ref<StatusOption[]>([]);

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
} = useCrudDrawer<RoleForm>({
  emptyForm: () => ({ name: '', code: '', sort: 1, remark: '', isEnabled: true }),
});

const rules: FormRules = {
  name: [
    { required: true, message: '角色名称不能为空', trigger: 'blur' },
    { max: 64, message: '角色名称不能超过 64 字', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '角色编码不能为空', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{2,64}$/, message: '角色编码需为 2-64 位字母、数字或下划线', trigger: 'blur' },
  ],
};

const menuDrawerVisible = ref(false);
const menuTarget = ref<RoleItem | null>(null);
const menuTree = ref<MenuItem[]>([]);
const menuTreeRef = ref();
const menuSubmitting = ref(false);
const menuIds = ref<number[]>([]);

const scopeDrawerVisible = ref(false);
const scopeTarget = ref<RoleItem | null>(null);
const scopeSubmitting = ref(false);
const scopeForm = reactive<{ dataScope: DataScope; ownFilter: OwnFilter }>({
  dataScope: 'ALL',
  ownFilter: 'NONE',
});
const deptOptions = ref<DeptOption[]>([]);
const deptTreeRef = ref();
const deptFilter = ref('');
const deptIds = ref<number[]>([]);
const userOptions = ref<UserOption[]>([]);
const selectedUsers = ref<UserOption[]>([]);
const userLoading = ref(false);
const userIds = ref<number[]>([]);
const reportOptionFailure = createOptionReporter();

function openEdit(row: RoleItem): void {
  openEditDrawer(row.id, {
    name: row.name,
    code: row.code,
    sort: row.sort,
    remark: row.remark ?? '',
    isEnabled: row.isEnabled,
  });
}

async function submit(): Promise<void> {
  const saved = await submitDrawer(async ({ isEdit: editing, editingId: id, form: values }) => {
    if (editing && id !== null) {
      await roleApi.update(id, { ...values });
      ElMessage.success('角色已更新');
    } else {
      await roleApi.create({ ...values });
      ElMessage.success('角色已创建');
    }
  });
  if (saved) {
    await load();
  }
}

async function remove(row: RoleItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确定删除角色「${row.name}」吗？删除后不可恢复。`,
    title: '删除角色',
    confirmText: '删除',
    customClass: 'confirm-dialog-440',
  });
  if (!confirmed) {
    return;
  }
  await roleApi.remove(row.id);
  ElMessage.success('角色已删除');
  if (rows.value.length === 1 && page.page > 1) {
    page.page -= 1;
  }
  await load();
}

async function openMenus(row: RoleItem): Promise<void> {
  menuTarget.value = row;
  menuTree.value = [];
  menuIds.value = [];
  menuDrawerVisible.value = true;
  try {
    const [tree, detail] = await Promise.all([menuApi.tree(), roleApi.get(row.id)]);
    menuTree.value = tree;
    menuIds.value = detail.menuIds ?? [];
    await nextTick();
    menuTreeRef.value?.setCheckedKeys(menuIds.value);
  } catch (error) {
    menuDrawerVisible.value = false;
    reportOptionFailure('菜单权限树', error);
  }
}

async function submitMenus(): Promise<void> {
  if (!menuTarget.value) {
    return;
  }
  menuSubmitting.value = true;
  try {
    const checked = (menuTreeRef.value?.getCheckedKeys() ?? []) as number[];
    await roleApi.assignMenus(menuTarget.value.id, checked);
    ElMessage.success('菜单权限已保存');
    menuDrawerVisible.value = false;
  } finally {
    menuSubmitting.value = false;
  }
}

async function openScope(row: RoleItem): Promise<void> {
  scopeTarget.value = row;
  deptIds.value = [];
  userIds.value = [];
  selectedUsers.value = [];
  scopeDrawerVisible.value = true;
  try {
    const [detail, depts] = await Promise.all([roleApi.get(row.id), deptApi.options()]);
    deptOptions.value = depts;
    scopeForm.dataScope = detail.dataScope;
    scopeForm.ownFilter = detail.ownFilter;
    deptIds.value = detail.deptIds ?? [];
    userIds.value = detail.userIds ?? [];
    await nextTick();
    deptTreeRef.value?.setCheckedKeys(deptIds.value);
    userOptions.value = await userApi.options();
    selectedUsers.value = userOptions.value.filter((item) => userIds.value.includes(item.id));
  } catch (error) {
    scopeDrawerVisible.value = false;
    reportOptionFailure('数据范围选项', error);
  }
}

function filterDeptNode(value: string, data: DeptOption): boolean {
  if (!value) {
    return true;
  }
  return data.name.includes(value);
}

watch(deptFilter, (value) => {
  deptTreeRef.value?.filter(value);
});

async function searchUsers(keyword: string): Promise<void> {
  userLoading.value = true;
  try {
    const data = await userApi.options(keyword || undefined);
    const selected = userOptions.value.filter((item) => userIds.value.includes(item.id));
    const merged = new Map<number, UserOption>();
    for (const item of [...selected, ...data]) {
      merged.set(item.id, item);
    }
    userOptions.value = Array.from(merged.values());
  } finally {
    userLoading.value = false;
  }
}

function onDeptCheck(): void {
  deptIds.value = (deptTreeRef.value?.getCheckedKeys() ?? []) as number[];
}

function onUserChange(ids: number[]): void {
  userIds.value = ids;
  selectedUsers.value = userOptions.value.filter((item) => ids.includes(item.id));
}

function onScopeChange(): void {
  // 切换组织范围时保留已选目标，保存时按当前范围决定提交字段
  if (scopeForm.dataScope === 'CUSTOM_DEPT') {
    void nextTick().then(() => deptTreeRef.value?.setCheckedKeys(deptIds.value));
  }
}

async function submitScope(): Promise<void> {
  if (!scopeTarget.value) {
    return;
  }
  scopeSubmitting.value = true;
  try {
    const payload: { dataScope: DataScope; ownFilter: OwnFilter; deptIds?: number[]; userIds?: number[] } = {
      dataScope: scopeForm.dataScope,
      ownFilter: scopeForm.ownFilter,
    };
    if (scopeForm.dataScope === 'CUSTOM_DEPT') {
      payload.deptIds = (deptTreeRef.value?.getCheckedKeys() ?? []) as number[];
    }
    if (scopeForm.dataScope === 'CUSTOM_USER') {
      payload.userIds = userIds.value;
    }
    await roleApi.assignDataScope(scopeTarget.value.id, payload);
    ElMessage.success('数据范围已保存');
    scopeDrawerVisible.value = false;
    await load();
  } finally {
    scopeSubmitting.value = false;
  }
}

onMounted(() => {
  void load();
  void loadStatusOptions().then((o) => {
    statusOptions.value = o;
  });
});
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-input
          v-model="filters.keyword"
          class="filter-keyword"
          placeholder="搜索角色名称或编码"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="filters.isEnabled" class="filter-status" placeholder="状态" clearable @change="search">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <el-button v-permission="'system:role:create'" type="primary" @click="openCreate">新增角色</el-button>
      </div>
    </div>

    <div class="page-card">
      <el-alert v-if="loadError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>列表加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table :data="skeletonVisible ? [] : rows" row-key="id" style="width: 100%">
        <el-table-column prop="name" label="角色名称" width="190" fixed="left" show-overflow-tooltip />
        <el-table-column prop="code" label="角色编码" width="160" show-overflow-tooltip />
        <el-table-column label="组织范围" width="140">
          <template #default="{ row }">
            <el-tag disable-transitions>{{ scopeLabels[row.dataScope as DataScope] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归属过滤" width="130">
          <template #default="{ row }">{{ ownLabels[row.ownFilter as OwnFilter] }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled ? 'success' : 'info'" disable-transitions>
              {{ row.isEnabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-permission="'system:role:menu'" link type="primary" @click="openMenus(row)">
                菜单权限
              </el-button>
              <el-button v-permission="'system:role:scope'" link type="primary" @click="openScope(row)">
                数据范围
              </el-button>
              <el-dropdown
                v-if="auth.hasPermission('system:role:update') || auth.hasPermission('system:role:delete')"
                trigger="click"
                @command="(command: string) => command === 'edit' ? openEdit(row) : remove(row)"
              >
                <el-button link type="primary">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit" v-permission="'system:role:update'">编辑</el-dropdown-item>
                    <el-dropdown-item command="delete" v-permission="'system:role:delete'" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <TableSkeleton v-if="skeletonVisible" :rows="6" />
          <div v-else-if="!loading" class="table-empty">
            <el-empty :description="hasFilter ? '无匹配结果' : '暂无数据'" :image-size="80">
              <el-button v-if="hasFilter" @click="resetFilters">清除筛选</el-button>
              <el-button v-else v-permission="'system:role:create'" type="primary" @click="openCreate">
                新增角色
              </el-button>
            </el-empty>
          </div>
        </template>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="page.page"
          v-model:page-size="page.pageSize"
          :page-sizes="PAGE_SIZES"
          :total="page.total"
          layout="total, prev, pager, next, sizes"
          background
          @current-change="load"
          @size-change="onPageSizeChange"
        />
      </div>
    </div>

    <el-drawer
      v-model="drawerVisible"
      :title="isEdit ? '编辑角色' : '新增角色'"
      size="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="true"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="角色名称" prop="name" :error="fieldError('name')">
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code" :error="fieldError('code')">
          <el-input v-model="form.code" :disabled="isEdit" placeholder="2-64 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="1" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.isEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注" :error="fieldError('remark')">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">保存</el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="menuDrawerVisible"
      title="分配菜单权限"
      size="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="true"
    >
      <p class="drawer-tip">角色：{{ menuTarget?.name }}。勾选后该角色可见的菜单与按钮。</p>
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        show-checkbox
        check-strictly
        default-expand-all
        class="perm-tree"
      />
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="menuDrawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="menuSubmitting" :disabled="menuSubmitting" @click="submitMenus">
            保存
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="scopeDrawerVisible"
      title="数据范围"
      size="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="true"
    >
      <p class="drawer-tip">角色：{{ scopeTarget?.name }}。功能权限与数据权限相互独立。</p>
      <el-form label-width="96px">
        <el-form-item label="组织范围">
          <el-select v-model="scopeForm.dataScope" class="full-width" @change="onScopeChange">
            <el-option v-for="(label, value) in scopeLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属过滤">
          <el-select v-model="scopeForm.ownFilter" class="full-width">
            <el-option v-for="(label, value) in ownLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <div class="form-hint">与上方组织范围叠加生效</div>
        </el-form-item>

        <template v-if="scopeForm.dataScope === 'CUSTOM_DEPT'">
          <el-form-item label="指定部门">
            <el-input v-model="deptFilter" placeholder="搜索部门名称" clearable />
            <div class="scope-tree">
              <el-tree
                ref="deptTreeRef"
                :data="deptOptions"
                :props="{ label: 'name', children: 'children' }"
                node-key="id"
                show-checkbox
                check-strictly
                default-expand-all
                :filter-node-method="filterDeptNode"
                @check="onDeptCheck"
              />
            </div>
            <div class="form-hint">支持跨部门勾选，选中后仅这些部门的数据可见</div>
          </el-form-item>
        </template>

        <template v-if="scopeForm.dataScope === 'CUSTOM_USER'">
          <el-form-item label="指定人员">
            <el-select
              v-model="userIds"
              multiple
              filterable
              remote
              reserve-keyword
              class="full-width"
              :remote-method="searchUsers"
              :loading="userLoading"
              placeholder="按姓名或账号搜索"
              @change="onUserChange"
            >
              <el-option
                v-for="item in userOptions"
                :key="item.id"
                :label="`${item.nickname}（${item.username}）${item.deptName ? ' · ' + item.deptName : ''}`"
                :value="item.id"
              />
            </el-select>
            <div class="form-hint">已选 {{ selectedUsers.length }} 人，支持跨部门选择</div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="scopeDrawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="scopeSubmitting" :disabled="scopeSubmitting" @click="submitScope">
            保存
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.filter-keyword {
  width: 240px;
}

.filter-status {
  width: 120px;
}

.toolbar-filters,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.table-error {
  margin: 16px 20px 0;
}

.error-retry {
  margin-top: 8px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.row-actions :deep(.el-button) {
  margin: 0;
}

.table-empty {
  padding: 16px 0;
}

.page-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 16px 20px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.drawer-tip {
  margin: 0 0 16px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.drawer-tip,
.form-hint {
  font-size: 13px;
}

.form-hint {
  margin-top: 6px;
  color: var(--color-text-tertiary);
  line-height: 1.5;
}

.full-width {
  width: 100%;
}

.perm-tree {
  max-height: calc(100vh - 220px);
  overflow: auto;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.scope-tree {
  width: 100%;
  max-height: 300px;
  margin-top: 8px;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 4px;
}
</style>
