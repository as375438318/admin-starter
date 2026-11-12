<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormRules } from 'element-plus';
import { deptApi } from '@/api/dept';
import { roleApi } from '@/api/role';
import { userApi } from '@/api/user';
import type { DeptOption, MaskStates, RoleItem, UserItem } from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import { loadStatusOptions, type StatusOption } from '@/utils/status-options';
import { createOptionReporter } from '@/utils/option-load';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { useAuthStore } from '@/stores/auth';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useCrudDrawer } from '@/composables/use-crud-drawer';
import { useTableColumns, type ColumnDefinition } from '@/composables/use-table-columns';
import ColumnSettings from '@/components/ColumnSettings.vue';

const auth = useAuthStore();

interface UserFilters {
  keyword: string;
  deptId?: number;
  isEnabled?: boolean;
}

interface UserForm {
  username: string;
  nickname: string;
  password: string;
  deptId?: number;
  email: string;
  phone: string;
  roleIds: number[];
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
} = usePagedList<UserItem, UserFilters>({
  initialFilters: { keyword: '', deptId: undefined, isEnabled: undefined },
  fetcher: (params) =>
    userApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      deptId: params.deptId,
      isEnabled: params.isEnabled,
      // 排序参数必须显式转发：fetcher 是逐字段映射的，漏一行就等于"点了表头没反应"
      sort: params.sort,
      order: params.order,
    }),
});

/**
 * 列定义（唯一定义处）：同时驱动表格渲染、列勾选、点表头排序、导出字段。
 * `sortKey` 必须是后端 `UserRepositoryImpl` 的 SORTS 白名单字段（见 constants/sort-whitelist.ts）。
 */
const USER_COLUMNS: ColumnDefinition[] = [
  { key: 'nickname', label: '姓名', sortKey: 'nickname', alwaysVisible: true },
  { key: 'username', label: '账号', sortKey: 'username' },
  { key: 'deptName', label: '部门' },
  { key: 'roleNames', label: '角色', exportable: false },
  { key: 'isEnabled', label: '状态' },
  { key: 'createdAt', label: '创建时间', sortKey: 'createdAt' },
  { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },
];

const table = useTableColumns({
  storageKey: 'system-user',
  columns: USER_COLUMNS,
  hasPermission: (code) => auth.hasPermission(code),
  onSort: (sort, order) => onSortChange(sort, order),
});

const deptOptions = ref<DeptOption[]>([]);
const roles = ref<RoleItem[]>([]);
const statusOptions = ref<StatusOption[]>([]);
const maskStates = ref<MaskStates>({});
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
} = useCrudDrawer<UserForm>({
  emptyForm: () => ({
    username: '',
    nickname: '',
    password: '',
    deptId: undefined,
    email: '',
    phone: '',
    roleIds: [],
  }),
  onOpen: () => {
    maskStates.value = {};
    void loadOptions();
  },
});

const createRules: FormRules = {
  username: [
    { required: true, message: '账号不能为空', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,32}$/, message: '账号需为 4-32 位字母、数字或下划线', trigger: 'blur' },
  ],
  nickname: [
    { required: true, message: '姓名不能为空', trigger: 'blur' },
    { max: 64, message: '姓名不能超过 64 字', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需为 8-64 位', trigger: 'blur' },
  ],
};

const editRules: FormRules = {
  nickname: createRules.nickname,
};

const emailPlaceholder = computed(() => {
  const state = maskStates.value.email;
  if (state === 'MASKED') {
    return '已脱敏，留空保持原值';
  }
  if (state === 'HIDDEN') {
    return '无权查看，留空保持原值';
  }
  return '请输入邮箱';
});

const phonePlaceholder = computed(() => {
  const state = maskStates.value.phone;
  if (state === 'MASKED') {
    return '已脱敏，留空保持原值';
  }
  if (state === 'HIDDEN') {
    return '无权查看，留空保持原值';
  }
  return '请输入手机号';
});

const pwdVisible = ref(false);
const pwdTarget = ref<UserItem | null>(null);
const pwdValue = ref('');
const pwdSubmitting = ref(false);

async function loadOptions(): Promise<void> {
  if (deptOptions.value.length === 0) {
    try {
      deptOptions.value = await deptApi.options();
    } catch (error) {
      reportOptionFailure('部门选项', error);
    }
  }
  if (roles.value.length === 0) {
    try {
      roles.value = await roleApi.all();
    } catch (error) {
      reportOptionFailure('角色选项', error);
    }
  }
}

const assignVisible = ref(false);
const assignTarget = ref<UserItem | null>(null);
const assignRoleIds = ref<number[]>([]);
const assignSubmitting = ref(false);

async function openAssignRoles(row: UserItem): Promise<void> {
  assignTarget.value = row;
  assignRoleIds.value = [];
  await loadOptions();
  try {
    const detail = await userApi.get(row.id);
    assignRoleIds.value = detail.roleIds ?? [];
    assignVisible.value = true;
  } catch {
    ElMessage.error('用户详情加载失败，无法分配角色');
  }
}

async function submitAssignRoles(): Promise<void> {
  if (!assignTarget.value) {
    return;
  }
  assignSubmitting.value = true;
  try {
    await userApi.assignRoles(assignTarget.value.id, assignRoleIds.value);
    ElMessage.success('角色已更新');
    assignVisible.value = false;
    await load();
  } finally {
    assignSubmitting.value = false;
  }
}

async function openEdit(row: UserItem): Promise<void> {
  openEditDrawer(row.id, {});
  try {
    const detail = await userApi.get(row.id);
    maskStates.value = detail.maskStates ?? {};
    Object.assign(form, {
      username: detail.username,
      nickname: detail.nickname,
      password: '',
      deptId: detail.deptId ?? undefined,
      email: maskStates.value.email ? '' : (detail.email ?? ''),
      phone: maskStates.value.phone ? '' : (detail.phone ?? ''),
      roleIds: detail.roleIds ?? [],
    });
  } catch {
    drawerVisible.value = false;
    ElMessage.error('用户详情加载失败，请重试');
  }
}

async function submit(): Promise<void> {
  const saved = await submitDrawer(async ({ isEdit: editing, editingId: id, form: values }) => {
    if (editing && id !== null) {
      await userApi.update(id, {
        nickname: values.nickname,
        deptId: values.deptId ?? null,
        email: maskStates.value.email ? (values.email || undefined) : (values.email || null),
        phone: maskStates.value.phone ? (values.phone || undefined) : (values.phone || null),
        roleIds: values.roleIds,
      });
      ElMessage.success('用户已更新');
    } else {
      await userApi.create({
        username: values.username,
        nickname: values.nickname,
        password: values.password,
        deptId: values.deptId ?? null,
        email: values.email || undefined,
        phone: values.phone || undefined,
        roleIds: values.roleIds,
      });
      ElMessage.success('用户已创建');
    }
  });
  if (saved) {
    await load();
  }
}

function openResetPassword(row: UserItem): void {
  pwdTarget.value = row;
  pwdValue.value = '';
  pwdVisible.value = true;
}

async function submitResetPassword(): Promise<void> {
  if (pwdValue.value.length < 8 || pwdValue.value.length > 64) {
    ElMessage.error('密码长度需为 8-64 位');
    return;
  }
  if (!pwdTarget.value) {
    return;
  }
  pwdSubmitting.value = true;
  try {
    await userApi.resetPassword(pwdTarget.value.id, pwdValue.value);
    ElMessage.success('密码已重置');
    pwdVisible.value = false;
  } finally {
    pwdSubmitting.value = false;
  }
}

async function toggleStatus(row: UserItem): Promise<void> {
  const next = !row.isEnabled;
  const action = next ? '启用' : '停用';
  try {
    await ElMessageBox.confirm(`确定${action}用户「${row.nickname}」吗？`, `${action}用户`, {
      type: 'warning',
      confirmButtonText: action,
      cancelButtonText: '取消',
      closeOnClickModal: false,
      customClass: 'confirm-dialog-440',
    });
  } catch {
    return;
  }
  await userApi.updateStatus(row.id, next);
  ElMessage.success(`已${action}`);
  await load();
}

async function remove(row: UserItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确定删除用户「${row.nickname}」吗？删除后不可恢复。`,
    title: '删除用户',
    confirmText: '删除',
    customClass: 'confirm-dialog-440',
  });
  if (!confirmed) {
    return;
  }
  await userApi.remove(row.id);
  ElMessage.success('用户已删除');
  if (rows.value.length === 1 && page.page > 1) {
    page.page -= 1;
  }
  await load();
}

function handleCommand(command: string, row: UserItem): void {
  if (command === 'reset') {
    openResetPassword(row);
  } else if (command === 'status') {
    void toggleStatus(row);
  } else if (command === 'delete') {
    void remove(row);
  }
}

onMounted(() => {
  void load();
  void loadOptions();
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
          placeholder="搜索账号或姓名"
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
        <el-select
          v-model="filters.isEnabled"
          class="filter-status"
          placeholder="状态"
          clearable
          @change="search"
        >
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <ColumnSettings
          :columns="USER_COLUMNS"
          :is-visible="table.isVisible"
          :hidden-count="table.hiddenCount.value"
          @toggle="table.toggleColumn"
          @show-all="table.showAllColumns"
          @invert="table.invertColumns"
          @reset="table.resetColumns"
        />
        <el-button v-permission="'system:user:create'" type="primary" @click="openCreate">
          新增用户
        </el-button>
      </div>
    </div>

    <div class="page-card">
      <el-alert
        v-if="loadError"
        class="table-error"
        type="error"
        :closable="false"
        show-icon
      >
        <template #title>列表加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table
        :data="skeletonVisible ? [] : rows"
        row-key="id"
        style="width: 100%"
        @sort-change="table.onSortChange"
      >
        <el-table-column
          v-if="table.isVisible('nickname')"
          prop="nickname"
          label="姓名"
          width="170"
          fixed="left"
          show-overflow-tooltip
          :sortable="table.sortableOf('nickname')"
        />
        <el-table-column
          v-if="table.isVisible('username')"
          prop="username"
          label="账号"
          width="130"
          show-overflow-tooltip
          :sortable="table.sortableOf('username')"
        />
        <el-table-column
          v-if="table.isVisible('deptName')"
          prop="deptName"
          label="部门"
          width="150"
          show-overflow-tooltip
        />
        <el-table-column v-if="table.isVisible('roleNames')" prop="roleNames" label="角色" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span :title="row.roleNames.join('、')">{{ row.roleNames.join('、') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('isEnabled')" prop="isEnabled" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled ? 'success' : 'info'" disable-transitions>
              {{ row.isEnabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="table.isVisible('createdAt')"
          prop="createdAt"
          label="创建时间"
          width="170"
          :sortable="table.sortableOf('createdAt')"
        >
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('ops')" prop="ops" label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-permission="'system:user:update'" link type="primary" @click="openEdit(row)">
                编辑
              </el-button>
              <el-button v-permission="'system:user:assign'" link type="primary" @click="openAssignRoles(row)">
                分配角色
              </el-button>
              <el-dropdown
                v-if="auth.hasPermission('system:user:reset') || auth.hasPermission('system:user:status') || auth.hasPermission('system:user:delete')"
                trigger="click"
                @command="(command: string) => handleCommand(command, row)"
              >
                <el-button link type="primary">
                  更多
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="reset" v-permission="'system:user:reset'">
                      重置密码
                    </el-dropdown-item>
                    <el-dropdown-item command="status" v-permission="'system:user:status'">
                      {{ row.isEnabled ? '停用账号' : '启用账号' }}
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" v-permission="'system:user:delete'" divided>
                      删除用户
                    </el-dropdown-item>
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
              <el-button v-else v-permission="'system:user:create'" type="primary" @click="openCreate">
                新增用户
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
      :title="isEdit ? '编辑用户' : '新增用户'"
      size="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="true"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="isEdit ? editRules : createRules" label-width="88px">
        <el-form-item label="账号" prop="username" :error="fieldError('username')">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="4-32 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="姓名" prop="nickname" :error="fieldError('nickname')">
          <el-input v-model="form.nickname" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="初始密码" prop="password" :error="fieldError('password')">
          <el-input v-model="form.password" type="password" show-password placeholder="8-64 位" />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-tree-select
            v-model="form.deptId"
            :data="deptOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="请选择部门"
            clearable
            check-strictly
          />
        </el-form-item>
        <el-form-item label="邮箱" :error="fieldError('email')">
          <el-input v-model="form.email" :placeholder="emailPlaceholder" />
        </el-form-item>
        <el-form-item label="手机号" :error="fieldError('phone')">
          <el-input v-model="form.phone" :placeholder="phonePlaceholder" />
        </el-form-item>
        <el-form-item v-permission="'system:user:assign'" label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色" class="full-width">
            <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">
            保存
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="pwdVisible"
      title="重置密码"
      width="440px"
      :close-on-click-modal="false"
      :close-on-press-escape="true"
    >
      <p class="dialog-tip">为用户「{{ pwdTarget?.nickname }}」设置新密码，长度 8-64 位。</p>
      <el-input v-model="pwdValue" type="password" show-password placeholder="请输入新密码" />
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" :disabled="pwdSubmitting" @click="submitResetPassword">
          确定
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="assignVisible"
      title="分配角色"
      width="440px"
      :close-on-click-modal="false"
      :close-on-press-escape="true"
    >
      <p class="dialog-tip">为用户「{{ assignTarget?.nickname }}」选择角色，保存后立即生效。</p>
      <el-select v-model="assignRoleIds" multiple placeholder="请选择角色" style="width: 100%">
        <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignSubmitting" :disabled="assignSubmitting" @click="submitAssignRoles">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-keyword {
  width: 220px;
}

.filter-dept {
  width: 180px;
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

.full-width {
  width: 100%;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.dialog-tip {
  margin: 0 0 12px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
</style>
