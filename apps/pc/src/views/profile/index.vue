<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { profileApi } from '@/api/log';
import { mySessionApi } from '@/api/session';
import { ApiError } from '@/api/http';
import type { LoginLogItem, ProfileInfo, SessionDeviceType, SessionItem } from '@/api/types';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import { scrollToFirstError } from '@/utils/form';
import { useDelayedSkeleton } from '@/utils/skeleton';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { useAuthStore } from '@/stores/auth';
import { useTableColumns, type ColumnDefinition } from '@/composables/use-table-columns';
import ColumnSettings from '@/components/ColumnSettings.vue';

const auth = useAuthStore();
const mustChangePassword = computed(() => auth.mustChangePassword);
const passwordExpired = computed(() => auth.passwordExpired);

const profile = ref<ProfileInfo | null>(null);
const loading = ref(false);
const loadError = ref('');

const logPage = reactive({ page: 1, pageSize: DEFAULT_PAGE_SIZE, total: 0 });
const logs = ref<LoginLogItem[]>([]);
const logsLoading = ref(false);
const logsSkeletonVisible = useDelayedSkeleton(logsLoading);
const logsError = ref('');

const DEVICE_LABELS: Record<SessionDeviceType, string> = {
  PC: '电脑',
  MOBILE: '手机',
  TABLET: '平板',
  UNKNOWN: '未知设备',
};

const sessions = ref<SessionItem[]>([]);
const sessionsLoading = ref(false);
const sessionsSkeletonVisible = useDelayedSkeleton(sessionsLoading);
const sessionsError = ref('');

/**
 * 两张表各自一份列定义与实例（storageKey 不同）：本页不接排序，
 * 因此不写 `sortKey`、不传 `onSort`。
 */
const SESSION_COLUMNS: ColumnDefinition[] = [
  // 设备 / 浏览器是 deviceType + browser + os 的拼接文案，不参与导出
  { key: 'device', label: '设备 / 浏览器', exportable: false },
  { key: 'ip', label: 'IP' },
  { key: 'loginAt', label: '登录时间' },
  { key: 'lastActiveAt', label: '最近活跃' },
  { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },
];

const sessionTable = useTableColumns({
  storageKey: 'profile-session',
  columns: SESSION_COLUMNS,
});

const LOGIN_LOG_COLUMNS: ColumnDefinition[] = [
  { key: 'createdAt', label: '时间' },
  { key: 'ip', label: 'IP' },
  { key: 'userAgent', label: '设备' },
  { key: 'isSuccess', label: '结果' },
  { key: 'message', label: '说明' },
];

const loginLogTable = useTableColumns({
  storageKey: 'profile-login-log',
  columns: LOGIN_LOG_COLUMNS,
});

const editVisible = ref(false);
const editSubmitting = ref(false);
const editFormRef = ref<FormInstance>();
const editErrors = ref<Record<string, string>>({});
const editForm = reactive({ nickname: '', email: '', phone: '' });

const editRules: FormRules = {
  nickname: [
    { required: true, message: '姓名不能为空', trigger: 'blur' },
    { max: 64, message: '姓名不能超过 64 字', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ max: 32, message: '手机号不能超过 32 位', trigger: 'blur' }],
};

const pwdVisible = ref(false);
const pwdSubmitting = ref(false);
const pwdFormRef = ref<FormInstance>();
const pwdErrors = ref<Record<string, string>>({});
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '原密码不能为空', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '新密码不能为空', trigger: 'blur' },
    { min: 8, max: 64, message: '新密码长度需为 8-64 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
};

const avatarText = computed(() => {
  const name = profile.value?.nickname || profile.value?.username || '';
  return name.slice(0, 1).toUpperCase();
});

async function loadProfile(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  try {
    profile.value = await profileApi.get();
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '个人信息加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadLogs(): Promise<void> {
  logsLoading.value = true;
  logsError.value = '';
  try {
    const data = await profileApi.loginLogs({ page: logPage.page, pageSize: logPage.pageSize });
    logs.value = data.list;
    logPage.total = data.total;
  } catch (error) {
    logs.value = [];
    logPage.total = 0;
    logsError.value = error instanceof ApiError ? error.message : '登录日志加载失败';
  } finally {
    logsLoading.value = false;
  }
}

async function loadSessions(): Promise<void> {
  sessionsLoading.value = true;
  sessionsError.value = '';
  try {
    sessions.value = await mySessionApi.list();
  } catch (error) {
    sessions.value = [];
    sessionsError.value = error instanceof ApiError ? error.message : '登录设备加载失败';
  } finally {
    sessionsLoading.value = false;
  }
}

async function kickSession(row: SessionItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认踢下线「${deviceText(row)}」？该设备会立即退出登录，当前设备不受影响。`,
      '踢下线',
      { type: 'warning', confirmButtonText: '踢下线', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await mySessionApi.kick(row.id);
  ElMessage.success('该设备已下线');
  await loadSessions();
}

function deviceText(row: SessionItem): string {
  const parts = [DEVICE_LABELS[row.deviceType], row.browser, row.os].filter(Boolean);
  return parts.join(' · ') || '未知设备';
}

function openEdit(): void {
  if (!profile.value) {
    return;
  }
  Object.assign(editForm, {
    nickname: profile.value.nickname,
    email: profile.value.email ?? '',
    phone: profile.value.phone ?? '',
  });
  editErrors.value = {};
  editVisible.value = true;
}

function collectEditErrors(error: unknown): void {
  editErrors.value = {};
  if (error instanceof ApiError && error.status === 422) {
    for (const item of error.errors) {
      editErrors.value[item.field] = item.message;
    }
    return;
  }
  if (error instanceof Error && !(error instanceof ApiError)) {
    ElMessage.error(error.message);
  }
}

async function submitEdit(): Promise<void> {
  const valid = await editFormRef.value?.validate().catch(() => false);
  if (!valid) {
    await scrollToFirstError(editFormRef.value);
    return;
  }
  editSubmitting.value = true;
  editErrors.value = {};
  try {
    profile.value = await profileApi.update({
      nickname: editForm.nickname,
      email: editForm.email || undefined,
      phone: editForm.phone || undefined,
    });
    ElMessage.success('资料已更新');
    editVisible.value = false;
  } catch (error) {
    collectEditErrors(error);
    void scrollToFirstError(editFormRef.value);
  } finally {
    editSubmitting.value = false;
  }
}

function openPassword(): void {
  Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirmPassword: '' });
  pwdErrors.value = {};
  pwdVisible.value = true;
}

function collectPwdErrors(error: unknown): void {
  pwdErrors.value = {};
  if (error instanceof ApiError && error.status === 422) {
    for (const item of error.errors) {
      pwdErrors.value[item.field] = item.message;
    }
    return;
  }
  if (error instanceof Error && !(error instanceof ApiError)) {
    ElMessage.error(error.message);
  }
}

async function submitPassword(): Promise<void> {
  const valid = await pwdFormRef.value?.validate().catch(() => false);
  if (!valid) {
    await scrollToFirstError(pwdFormRef.value);
    return;
  }
  pwdSubmitting.value = true;
  pwdErrors.value = {};
  try {
    await profileApi.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    });
    ElMessage.success('密码已修改，其他设备需重新登录');
    pwdVisible.value = false;
  } catch (error) {
    collectPwdErrors(error);
    void scrollToFirstError(pwdFormRef.value);
  } finally {
    pwdSubmitting.value = false;
  }
}

function onLogPageSizeChange(size: number): void {
  logPage.pageSize = size;
  logPage.page = 1;
  void loadLogs();
}

onMounted(() => {
  void loadProfile();
  void loadLogs();
  void loadSessions();
});
</script>

<template>
  <div class="page-container profile-page">
    <div class="page-card profile-card">
      <!-- 等保 P0-1：必须改密时给出阻断式提示（服务端已限制其他接口，这里说明"为什么点哪都不行"） -->
      <el-alert
        v-if="mustChangePassword"
        class="table-error"
        type="warning"
        :closable="false"
        show-icon
        title="必须先修改登录密码"
      >
        <template #default>
          <span v-if="passwordExpired">当前密码已超过有效期。</span>
          <span v-else>账号处于「下次登录必须改密」状态（管理员重置过密码或首次登录）。</span>
          在下方「修改密码」提交成功后即可正常使用其他功能。
        </template>
      </el-alert>

      <el-alert v-if="loadError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>个人信息加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="loadProfile">重试</el-button>
      </el-alert>

      <el-skeleton v-if="loading" class="table-skeleton" :rows="4" animated />

      <template v-else-if="profile">
        <div class="profile-head">
          <div class="avatar">{{ avatarText }}</div>
          <div class="profile-head-main">
            <h2 class="profile-name">{{ profile.nickname }}</h2>
            <p class="profile-sub">{{ profile.username }} · {{ profile.deptName || '未分配部门' }}</p>
            <div class="role-tags">
              <el-tag v-for="role in profile.roles" :key="role" size="small" type="info">{{ role }}</el-tag>
            </div>
          </div>
          <div class="profile-head-actions">
            <el-button @click="openPassword">修改密码</el-button>
            <el-button type="primary" @click="openEdit">编辑资料</el-button>
          </div>
        </div>

        <div class="profile-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="账号">{{ profile.username }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ profile.nickname }}</el-descriptions-item>
            <el-descriptions-item label="所属部门">{{ profile.deptName || '未分配部门' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ profile.email || '—' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ profile.phone || '—' }}</el-descriptions-item>
            <el-descriptions-item label="权限数量">{{ profile.permissions.length }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </template>
    </div>

    <div class="page-card sessions-card">
      <div class="card-header">
        <div>
          <h3 class="card-title">登录设备</h3>
          <p class="card-subtitle">当前账号的活跃会话；最近 5 分钟内有请求才算在线</p>
        </div>
        <div class="card-header-actions">
          <ColumnSettings
            :columns="SESSION_COLUMNS"
            :is-visible="sessionTable.isVisible"
            :hidden-count="sessionTable.hiddenCount.value"
            @toggle="sessionTable.toggleColumn"
            @show-all="sessionTable.showAllColumns"
            @invert="sessionTable.invertColumns"
            @reset="sessionTable.resetColumns"
          />
          <el-button @click="loadSessions">刷新</el-button>
        </div>
      </div>

      <el-alert v-if="sessionsError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>登录设备加载失败：{{ sessionsError }}</template>
        <el-button class="error-retry" size="small" @click="loadSessions">重试</el-button>
      </el-alert>

      <el-table :data="sessionsSkeletonVisible ? [] : sessions" row-key="id" style="width: 100%">
        <template #empty>
          <TableSkeleton v-if="sessionsSkeletonVisible" :rows="3" />
          <div v-else-if="!sessionsLoading" class="table-empty">
            <p class="empty-title">暂无活跃设备</p>
            <p class="empty-desc">重新登录后，设备会出现在这里。</p>
          </div>
        </template>
        <el-table-column
          v-if="sessionTable.isVisible('device')"
          prop="device"
          label="设备 / 浏览器"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ deviceText(row) }}
            <el-tag v-if="row.current" class="current-tag" size="small" type="success">当前设备</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="sessionTable.isVisible('ip')" prop="ip" label="IP" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ip || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="sessionTable.isVisible('loginAt')" prop="loginAt" label="登录时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.loginAt) }}</template>
        </el-table-column>
        <el-table-column v-if="sessionTable.isVisible('lastActiveAt')" prop="lastActiveAt" label="最近活跃" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastActiveAt) }}</template>
        </el-table-column>
        <el-table-column v-if="sessionTable.isVisible('ops')" prop="ops" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="!row.current"
              link
              type="danger"
              @click="kickSession(row)"
            >
              踢下线
            </el-button>
            <span v-else class="table-hint">当前会话</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="page-card logs-card">
      <div class="card-header">
        <div>
          <h3 class="card-title">登录日志</h3>
          <p class="card-subtitle">最近 20 条登录记录，用于核对异常登录</p>
        </div>
        <div class="card-header-actions">
          <ColumnSettings
            :columns="LOGIN_LOG_COLUMNS"
            :is-visible="loginLogTable.isVisible"
            :hidden-count="loginLogTable.hiddenCount.value"
            @toggle="loginLogTable.toggleColumn"
            @show-all="loginLogTable.showAllColumns"
            @invert="loginLogTable.invertColumns"
            @reset="loginLogTable.resetColumns"
          />
          <el-button @click="loadLogs">刷新</el-button>
        </div>
      </div>

      <el-alert v-if="logsError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>登录日志加载失败：{{ logsError }}</template>
        <el-button class="error-retry" size="small" @click="loadLogs">重试</el-button>
      </el-alert>

      <el-table :data="logsSkeletonVisible ? [] : logs" row-key="id" style="width: 100%">
        <template #empty>
          <TableSkeleton v-if="logsSkeletonVisible" :rows="5" />
          <div v-else-if="!logsLoading" class="table-empty">
            <p class="empty-title">暂无登录记录</p>
            <p class="empty-desc">下次登录成功后，这里会显示设备、IP 与结果。</p>
          </div>
        </template>
        <el-table-column v-if="loginLogTable.isVisible('createdAt')" prop="createdAt" label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="loginLogTable.isVisible('ip')" prop="ip" label="IP" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ip || '—' }}</template>
        </el-table-column>
        <el-table-column
          v-if="loginLogTable.isVisible('userAgent')"
          prop="userAgent"
          label="设备"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row.userAgent || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="loginLogTable.isVisible('isSuccess')" prop="isSuccess" label="结果" width="110">
          <template #default="{ row }">
            <el-tag :type="row.isSuccess ? 'success' : 'danger'" size="small">
              {{ row.isSuccess ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="loginLogTable.isVisible('message')" prop="message" label="说明" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.message || '—' }}</template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          v-model:current-page="logPage.page"
          :page-size="logPage.pageSize"
          :total="logPage.total"
          :page-sizes="PAGE_SIZES"
          layout="total, prev, pager, next, sizes"
          background
          @current-change="loadLogs"
          @size-change="onLogPageSizeChange"
        />
      </div>
    </div>

    <el-drawer v-model="editVisible" title="编辑资料" size="480px" :close-on-click-modal="false">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="88px" label-position="left">
        <el-form-item label="姓名" prop="nickname" :error="editErrors.nickname">
          <el-input v-model="editForm.nickname" maxlength="64" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email" :error="editErrors.email">
          <el-input v-model="editForm.email" maxlength="128" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone" :error="editErrors.phone">
          <el-input v-model="editForm.phone" maxlength="32" placeholder="请输入手机号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" :disabled="editSubmitting" @click="submitEdit">
          保存
        </el-button>
      </template>
    </el-drawer>

    <el-dialog
      v-model="pwdVisible"
      title="修改密码"
      width="440px"
      :close-on-click-modal="false"
      custom-class="confirm-dialog-440"
    >
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="88px" label-position="left">
        <el-form-item label="原密码" prop="oldPassword" :error="pwdErrors.oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password maxlength="64" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword" :error="pwdErrors.newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password maxlength="64" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password maxlength="64" />
        </el-form-item>
        <p class="profile-pwd-hint">修改后当前设备保持登录，其他设备上的登录会立即失效。</p>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" :disabled="pwdSubmitting" @click="submitPassword">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.profile-head {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-6);
}

.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary-light-8);
  color: var(--color-primary);
  font-size: 24px;
  font-weight: 600;
  flex-shrink: 0;
}

.profile-head-main {
  flex: 1;
  min-width: 0;
}

.profile-name {
  margin: 0;
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: var(--color-text-primary);
}

.profile-sub {
  margin: var(--space-1) 0 var(--space-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.role-tags {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.profile-head-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.profile-info {
  padding: 0 var(--space-6) var(--space-6);
}

.profile-pwd-hint {
  margin: 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.current-tag {
  margin-left: var(--space-2);
}

.table-hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-divider);
}

.card-title {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.card-subtitle {
  margin: var(--space-1) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.card-header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex: 0 0 auto;
}

.table-error {
  margin: var(--space-4);
}

.error-retry {
  margin-top: var(--space-2);
}

.table-skeleton {
  padding: var(--space-5);
}

.table-pagination {
  padding: var(--space-4) var(--space-5);
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
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}
</style>
