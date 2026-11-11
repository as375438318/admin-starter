<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { menuApi } from '@/api/menu';
import { ApiError } from '@/api/http';
import type { MenuItem } from '@/api/types';
import { formatDateTime } from '@/utils/datetime';
import { scrollToFirstError } from '@/utils/form';
import { useDelayedSkeleton } from '@/utils/skeleton';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();

const menuTypeLabels: Record<MenuItem['menuType'], string> = {
  M: '目录',
  C: '菜单',
  F: '按钮',
};

const rows = ref<MenuItem[]>([]);
const loading = ref(false);
const skeletonVisible = useDelayedSkeleton(loading);
const loadError = ref('');

const drawerVisible = ref(false);
const isEdit = ref(false);
const editingId = ref<number | null>(null);
const submitting = ref(false);
const formRef = ref<FormInstance>();
const fieldErrors = ref<Record<string, string>>({});
const form = reactive<{
  parentId: number;
  name: string;
  menuType: MenuItem['menuType'];
  path: string;
  component: string;
  permissionCode: string;
  icon: string;
  sort: number;
  isVisible: boolean;
  isEnabled: boolean;
}>({
  parentId: 0,
  name: '',
  menuType: 'C',
  path: '',
  component: '',
  permissionCode: '',
  icon: '',
  sort: 1,
  isVisible: true,
  isEnabled: true,
});

const rules: FormRules = {
  name: [
    { required: true, message: '名称不能为空', trigger: 'blur' },
    { max: 64, message: '名称不能超过 64 字', trigger: 'blur' },
  ],
  path: [
    {
      validator: (_rule, value: string, callback) => {
        if (form.menuType !== 'F' && !value) {
          callback(new Error('路由地址不能为空'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
  component: [
    {
      validator: (_rule, value: string, callback) => {
        if (form.menuType === 'C' && !value) {
          callback(new Error('组件路径不能为空'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
  permissionCode: [
    {
      validator: (_rule, value: string, callback) => {
        if (form.menuType === 'F' && !value) {
          callback(new Error('权限码不能为空'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
};

const parentTree = computed(() => {
  function filterTree(items: MenuItem[]): MenuItem[] {
    return items
      .filter((item) => item.id !== editingId.value && item.menuType !== 'F')
      .map((item) => ({ ...item, children: item.children ? filterTree(item.children) : undefined }));
  }
  return [{ id: 0, name: '根目录', children: filterTree(rows.value) } as unknown as MenuItem];
});

function fieldError(field: string): string {
  return fieldErrors.value[field] ?? '';
}

function collectErrors(error: unknown): void {
  fieldErrors.value = {};
  if (error instanceof ApiError && error.status === 422) {
    if (error.errors.length === 0) {
      ElMessage.error(error.message);
      return;
    }
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
    rows.value = await menuApi.tree();
  } catch (error) {
    rows.value = [];
    loadError.value = error instanceof ApiError ? error.message : '菜单加载失败';
  } finally {
    loading.value = false;
  }
}

function openCreate(parent?: MenuItem): void {
  isEdit.value = false;
  editingId.value = null;
  Object.assign(form, {
    parentId: parent?.id ?? 0,
    name: '',
    menuType: parent ? 'C' : 'M',
    path: '',
    component: parent ? '' : 'Layout',
    permissionCode: '',
    icon: '',
    sort: 1,
    isVisible: true,
    isEnabled: true,
  });
  fieldErrors.value = {};
  drawerVisible.value = true;
}

function openEdit(row: MenuItem): void {
  isEdit.value = true;
  editingId.value = row.id;
  fieldErrors.value = {};
  Object.assign(form, {
    parentId: row.parentId,
    name: row.name,
    menuType: row.menuType,
    path: row.path ?? '',
    component: row.component ?? '',
    permissionCode: row.permissionCode ?? '',
    icon: row.icon ?? '',
    sort: row.sort,
    isVisible: row.isVisible,
    isEnabled: row.isEnabled,
  });
  drawerVisible.value = true;
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    await scrollToFirstError(formRef.value);
    return;
  }
  submitting.value = true;
  fieldErrors.value = {};
  const payload = {
    parentId: form.parentId,
    name: form.name,
    menuType: form.menuType,
    path: form.menuType === 'F' ? undefined : form.path,
    component: form.menuType === 'C' ? form.component : (form.menuType === 'M' ? form.component || 'Layout' : undefined),
    permissionCode: form.menuType === 'M' ? undefined : form.permissionCode,
    icon: form.menuType === 'F' ? undefined : form.icon,
    sort: form.sort,
    isVisible: form.isVisible,
    isEnabled: form.isEnabled,
  };
  try {
    if (isEdit.value && editingId.value !== null) {
      await menuApi.update(editingId.value, payload);
      ElMessage.success('已保存，侧栏将在刷新页面后更新');
    } else {
      await menuApi.create(payload);
      ElMessage.success('已创建，侧栏将在刷新页面后更新');
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

async function remove(row: MenuItem): Promise<void> {
  const hasChildren = (row.children?.length ?? 0) > 0;
  if (hasChildren) {
    ElMessage.warning('该菜单存在下级，请先删除下级菜单');
    return;
  }
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」吗？删除后不可恢复。`, '删除菜单', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      closeOnClickModal: false,
      customClass: 'confirm-dialog-440',
    });
  } catch {
    return;
  }
  await menuApi.remove(row.id);
  ElMessage.success('菜单已删除，侧栏将在刷新页面后更新');
  await load();
}

function handleCommand(command: string, row: MenuItem): void {
  if (command === 'edit') {
    openEdit(row);
  } else if (command === 'delete') {
    void remove(row);
  } else if (command === 'child') {
    openCreate(row);
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div class="toolbar-filters">
        <span class="page-hint">目录 / 菜单 / 按钮三级结构，按钮节点只承载权限码。</span>
      </div>
      <div class="toolbar-actions">
        <el-button @click="load">刷新</el-button>
        <el-button v-permission="'system:menu:create'" type="primary" @click="openCreate()">新增目录</el-button>
      </div>
    </div>

    <div class="page-card">
      <el-alert v-if="loadError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>菜单加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table
        :data="skeletonVisible ? [] : rows"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        style="width: 100%"
      >
        <el-table-column prop="name" label="菜单名称" width="230" fixed="left" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.menuType === 'M' ? 'info' : row.menuType === 'C' ? 'primary' : 'warning'" disable-transitions>
              {{ menuTypeLabels[row.menuType as MenuItem['menuType']] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由地址" width="170" show-overflow-tooltip />
        <el-table-column prop="component" label="组件路径" width="200" show-overflow-tooltip />
        <el-table-column prop="permissionCode" label="权限码" width="190" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="70" align="right" class-name="tabular-nums" />
        <el-table-column label="显示" width="80">
          <template #default="{ row }">{{ row.isVisible ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
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
              <el-button
                v-if="row.menuType !== 'F'"
                v-permission="'system:menu:create'"
                link
                type="primary"
                @click="openCreate(row)"
              >
                新增下级
              </el-button>
              <el-button v-permission="'system:menu:update'" link type="primary" @click="openEdit(row)">
                编辑
              </el-button>
              <el-dropdown
                v-if="auth.hasPermission('system:menu:delete')"
                trigger="click"
                @command="(command: string) => handleCommand(command, row)"
              >
                <el-button link type="primary">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="delete">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <TableSkeleton v-if="skeletonVisible" :rows="6" />
          <div v-else-if="!loading" class="table-empty">
            <el-empty description="暂无数据" :image-size="80">
              <el-button v-permission="'system:menu:create'" type="primary" @click="openCreate()">新增目录</el-button>
            </el-empty>
          </div>
        </template>
      </el-table>
    </div>

    <el-drawer
      v-model="drawerVisible"
      :title="isEdit ? '编辑菜单' : '新增菜单'"
      size="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="true"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            check-strictly
            default-expand-all
            placeholder="根目录"
          />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.menuType">
            <el-radio-button value="M">目录</el-radio-button>
            <el-radio-button value="C">菜单</el-radio-button>
            <el-radio-button value="F">按钮</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" prop="name" :error="fieldError('name')">
          <el-input v-model="form.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 'F'" label="路由地址" prop="path" :error="fieldError('path')">
          <el-input v-model="form.path" placeholder="如 /system/users" />
        </el-form-item>
        <el-form-item v-if="form.menuType === 'C'" label="组件路径" prop="component" :error="fieldError('component')">
          <el-input v-model="form.component" placeholder="如 system/user/index" />
        </el-form-item>
        <el-form-item v-if="form.menuType === 'M'" label="组件路径">
          <el-input v-model="form.component" placeholder="目录默认 Layout" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 'M'" label="权限码" prop="permissionCode" :error="fieldError('permissionCode')">
          <el-input v-model="form.permissionCode" placeholder="如 system:user:create" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 'F'" label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 User" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="1" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 'F'" label="是否显示">
          <el-switch v-model="form.isVisible" active-text="显示" inactive-text="隐藏" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.isEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">保存</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.toolbar-filters,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-hint {
  color: var(--color-text-secondary);
  font-size: 13px;
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

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
