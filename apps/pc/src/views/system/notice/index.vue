<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, type FormRules } from 'element-plus';
import { deptApi } from '@/api/dept';
import { noticeApi, type NoticePublishPayload, type NoticeScopePayload } from '@/api/notice';
import { roleApi } from '@/api/role';
import { userApi } from '@/api/user';
import { ApiError } from '@/api/http';
import type {
  DeptOption,
  NoticeItem,
  NoticeLevel,
  NoticeReaderItem,
  NoticeScopeType,
  NoticeType,
  RoleItem,
  UserOption,
} from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import { createOptionReporter } from '@/utils/option-load';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useCrudDrawer } from '@/composables/use-crud-drawer';

const LEVEL_LABELS: Record<NoticeLevel, string> = {
  NORMAL: '普通',
  IMPORTANT: '重要',
  URGENT: '紧急',
};
const LEVEL_TAG: Record<NoticeLevel, 'info' | 'warning' | 'danger'> = {
  NORMAL: 'info',
  IMPORTANT: 'warning',
  URGENT: 'danger',
};
const TYPE_LABELS: Record<NoticeType, string> = {
  SYSTEM: '系统',
  BUSINESS: '业务',
  SECURITY: '安全',
  TASK: '任务',
};
const SCOPE_LABELS: Record<NoticeScopeType, string> = {
  ALL: '全员',
  ROLE: '指定角色',
  DEPT: '指定部门（含下级）',
  USER: '指定用户',
};

interface NoticeFilters {
  keyword: string;
  level?: NoticeLevel;
  type?: NoticeType;
  status?: string;
}

interface NoticeForm {
  title: string;
  content: string;
  level: NoticeLevel;
  type: NoticeType;
  revocable: boolean;
  scopes: NoticeScopePayload[];
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
  onPageChange,
  onPageSizeChange,
} = usePagedList<NoticeItem, NoticeFilters>({
  initialFilters: { keyword: '', level: undefined, type: undefined, status: undefined },
  // 显式指定排序：后端默认按 SortSupport 的语义是**升序**（未传 order 时 isAsc 返回 true），
  // 对"通知公告"来说体验是反的 —— 管理员要看刚发的，而不是翻到最后一页。
  // 这里显式传 createdAt + desc，不动全局默认（改全局默认会把十几个列表的顺序一起翻过来）。
  fetcher: (params) =>
    noticeApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      level: params.level,
      type: params.type,
      status: params.status,
      sort: 'createdAt',
      order: 'desc',
    }),
});

const detailVisible = ref(false);
const detail = ref<NoticeItem | null>(null);

const rules: FormRules = {
  title: [
    { required: true, message: '标题不能为空', trigger: 'blur' },
    { max: 200, message: '标题不能超过 200 字', trigger: 'blur' },
  ],
  content: [{ required: true, message: '内容不能为空', trigger: 'blur' }],
};

const roleOptions = ref<RoleItem[]>([]);
const deptOptions = ref<DeptOption[]>([]);
const userOptions = ref<UserOption[]>([]);
const reportOptionFailure = createOptionReporter();

const {
  visible: publishVisible,
  submitting,
  formRef,
  form,
  openCreate: openPublish,
  submit: submitPublish,
} = useCrudDrawer<NoticeForm>({
  emptyForm: () => ({
    title: '',
    content: '',
    level: 'NORMAL',
    type: 'SYSTEM',
    revocable: true,
    scopes: [{ scopeType: 'ALL', targetId: null }],
  }),
  onOpen: () => {
    void loadOptions();
  },
});

async function loadOptions(): Promise<void> {
  try {
    roleOptions.value = await roleApi.all();
  } catch (error) {
    reportOptionFailure('角色选项', error);
  }
  try {
    deptOptions.value = await deptApi.options();
  } catch (error) {
    reportOptionFailure('部门选项', error);
  }
  try {
    userOptions.value = await userApi.options();
  } catch (error) {
    reportOptionFailure('用户选项', error);
  }
}

const readersVisible = ref(false);
const readersNoticeId = ref<number | null>(null);
const readersTitle = ref('');

interface ReadersFilters {
  readStatus: string;
  keyword: string;
}

const {
  page: readersPage,
  filters: readersFilters,
  rows: readers,
  loading: readersLoading,
  load: loadReaders,
  search: searchReaders,
  onPageChange: onReadersPageChange,
  onPageSizeChange: onReadersPageSizeChange,
} = usePagedList<NoticeReaderItem, ReadersFilters>({
  initialFilters: { readStatus: 'UNREAD', keyword: '' },
  // 已读统计加载失败保持静默，与改造前一致
  onError: () => undefined,
  fetcher: (params) =>
    readersNoticeId.value === null
      ? Promise.resolve({ page: params.page, pageSize: params.pageSize, list: [], total: 0 })
      : noticeApi.readers(readersNoticeId.value, {
          page: params.page,
          pageSize: params.pageSize,
          readStatus: params.readStatus,
          keyword: params.keyword || undefined,
        }),
});

function addScope(): void {
  form.scopes.push({ scopeType: 'ROLE', targetId: null });
}

function removeScope(index: number): void {
  if (form.scopes.length <= 1) {
    return;
  }
  form.scopes.splice(index, 1);
}

function onScopeTypeChange(scope: NoticeScopePayload): void {
  scope.targetId = null;
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (form.scopes.length === 0) {
    ElMessage.warning('至少选择一个接收范围');
    return;
  }
  const missing = form.scopes.find((scope) => scope.scopeType !== 'ALL' && scope.targetId == null);
  if (missing) {
    ElMessage.warning(`请为「${SCOPE_LABELS[missing.scopeType]}」选择目标`);
    return;
  }
  const saved = await submitPublish(async () => {
    const payload: NoticePublishPayload = {
      title: form.title,
      content: form.content,
      level: form.level,
      type: form.type,
      revocable: form.revocable,
      scopes: form.scopes.map((scope) => ({
        scopeType: scope.scopeType,
        targetId: scope.scopeType === 'ALL' ? null : scope.targetId,
      })),
    };
    try {
      await noticeApi.publish(payload);
      ElMessage.success('通知已发布');
    } catch (error) {
      if (error instanceof ApiError && error.status === 422) {
        ElMessage.error(error.errors[0]?.message ?? error.message);
      }
      throw error;
    }
  });
  if (saved) {
    await load();
  }
}

async function openDetail(row: NoticeItem): Promise<void> {
  detailVisible.value = true;
  detail.value = null;
  try {
    detail.value = await noticeApi.detail(row.id);
  } catch {
    detailVisible.value = false;
  }
}

async function revoke(row: NoticeItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确定撤回通知「${row.title}」吗？撤回后用户端不再展示。`,
    title: '撤回通知',
    confirmText: '撤回',
  });
  if (!confirmed) {
    return;
  }
  await noticeApi.revoke(row.id);
  ElMessage.success('通知已撤回');
  await load();
}

async function remove(row: NoticeItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确定删除通知「${row.title}」吗？删除后管理端与用户端均不可见，已读记录一并删除。`,
    title: '删除通知',
  });
  if (!confirmed) {
    return;
  }
  await noticeApi.remove(row.id);
  ElMessage.success('通知已删除');
  if (rows.value.length === 1 && page.page > 1) {
    page.page -= 1;
  }
  await load();
}

async function openReaders(row: NoticeItem): Promise<void> {
  readersVisible.value = true;
  readersNoticeId.value = row.id;
  readersTitle.value = row.title;
  Object.assign(readersFilters, { readStatus: 'UNREAD', keyword: '' });
  readersPage.page = 1;
  await loadReaders();
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
          placeholder="搜索通知标题"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="filters.level" class="filter-select" placeholder="级别" clearable @change="search">
          <el-option v-for="(label, value) in LEVEL_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="filters.type" class="filter-select" placeholder="类型" clearable @change="search">
          <el-option v-for="(label, value) in TYPE_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="filters.status" class="filter-select" placeholder="状态" clearable @change="search">
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已撤回" value="REVOKED" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <el-button v-permission="'system:notice:create'" type="primary" @click="openPublish">发布通知</el-button>
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
            <p class="empty-title">{{ hasFilter ? '没有匹配的通知' : '暂无通知' }}</p>
            <p class="empty-desc">{{ hasFilter ? '调整筛选条件后再试。' : '发布第一条通知后即可在这里管理。' }}</p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
          </div>
        </template>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="LEVEL_TAG[row.level as NoticeLevel]" size="small">
              {{ LEVEL_LABELS[row.level as NoticeLevel] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ TYPE_LABELS[row.type as NoticeType] }}</template>
        </el-table-column>
        <el-table-column label="接收范围" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-for="(scope, index) in row.scopes" :key="index">
              {{ scope.scopeType === 'ALL' ? '全员' : scope.targetName }}
              <template v-if="index < row.scopes.length - 1">、</template>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="已读 / 应读" width="120" align="right" class-name="tabular-nums">
          <template #default="{ row }">{{ row.stats.readCount }} / {{ row.stats.totalRecipients }}</template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布人" width="120" show-overflow-tooltip />
        <el-table-column label="发布时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.revoked ? 'info' : 'success'" size="small">
              {{ row.revoked ? '已撤回' : '已发布' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-permission="'system:notice:read-stats'" link type="primary" @click="openReaders(row)">
                已读统计
              </el-button>
              <el-button
                v-if="!row.revoked"
                v-permission="'system:notice:revoke'"
                link
                type="primary"
                @click="revoke(row)"
              >
                撤回
              </el-button>
              <el-button v-permission="'system:notice:delete'" link type="danger" @click="remove(row)">
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
          @current-change="onPageChange"
          @size-change="onPageSizeChange"
        />
      </div>
    </div>

    <el-drawer v-model="publishVisible" title="发布通知" size="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px" label-position="left">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="请输入通知标题" />
        </el-form-item>
        <el-form-item label="级别" prop="level">
          <el-radio-group v-model="form.level">
            <el-radio-button v-for="(label, value) in LEVEL_LABELS" :key="value" :value="value">
              {{ label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="(label, value) in TYPE_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="5000" show-word-limit
                    placeholder="请输入通知内容（纯文本）" />
        </el-form-item>
        <el-form-item label="可撤回">
          <el-switch v-model="form.revocable" active-text="允许撤回" inactive-text="不可撤回" />
        </el-form-item>
        <el-form-item label="接收范围">
          <div class="scope-list">
            <div v-for="(scope, index) in form.scopes" :key="index" class="scope-row">
              <el-select v-model="scope.scopeType" class="scope-type" @change="onScopeTypeChange(scope)">
                <el-option v-for="(label, value) in SCOPE_LABELS" :key="value" :label="label" :value="value" />
              </el-select>
              <el-select v-if="scope.scopeType === 'ROLE'" v-model="scope.targetId" class="scope-target"
                         placeholder="选择角色" filterable>
                <el-option v-for="role in roleOptions" :key="role.id" :label="role.name" :value="role.id" />
              </el-select>
              <el-tree-select v-else-if="scope.scopeType === 'DEPT'" v-model="scope.targetId" class="scope-target"
                              :data="deptOptions" :props="{ label: 'name', value: 'id', children: 'children' }"
                              placeholder="选择部门" check-strictly filterable />
              <el-select v-else-if="scope.scopeType === 'USER'" v-model="scope.targetId" class="scope-target"
                         placeholder="选择用户" filterable>
                <el-option v-for="user in userOptions" :key="user.id"
                           :label="user.nickname ? `${user.nickname}（${user.username}）` : user.username"
                           :value="user.id" />
              </el-select>
              <el-button link type="danger" :disabled="form.scopes.length <= 1" @click="removeScope(index)">
                移除
              </el-button>
            </div>
            <el-button size="small" @click="addScope">添加范围</el-button>
            <p class="scope-hint">
              多个范围取并集；「指定部门」包含该部门及其全部下级部门；发布时生成应读人快照，之后组织调整不补发。
            </p>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">发布</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="detailVisible" title="通知详情" size="560px">
      <template v-if="detail">
        <div class="detail-head">
          <h3 class="detail-title">{{ detail.title }}</h3>
          <div class="detail-tags">
            <el-tag :type="LEVEL_TAG[detail.level]" size="small">{{ LEVEL_LABELS[detail.level] }}</el-tag>
            <el-tag size="small" type="info">{{ TYPE_LABELS[detail.type] }}</el-tag>
            <el-tag v-if="detail.revoked" size="small" type="info">已撤回</el-tag>
          </div>
        </div>
        <div class="detail-meta">
          <span>发布人：{{ detail.publisherName }}</span>
          <span>发布时间：{{ formatDateTime(detail.publishedAt) }}</span>
          <span v-if="detail.deptName">发布部门：{{ detail.deptName }}</span>
          <span>已读 {{ detail.stats.readCount }} / 应读 {{ detail.stats.totalRecipients }}</span>
        </div>
        <div class="detail-section">
          <h4>接收范围</h4>
          <div class="scope-tags">
            <el-tag v-for="(scope, index) in detail.scopes" :key="index" size="small" type="info">
              {{ scope.scopeType === 'ALL' ? '全员' : `${SCOPE_LABELS[scope.scopeType]}：${scope.targetName}` }}
            </el-tag>
          </div>
        </div>
        <div class="detail-section">
          <h4>内容</h4>
          <pre class="detail-content">{{ detail.content }}</pre>
        </div>
        <div v-if="detail.revoked" class="detail-section">
          <h4>撤回信息</h4>
          <p class="detail-meta">撤回人：{{ detail.revokedByName || '—' }}，撤回时间：
            {{ formatDateTime(detail.revokedAt) }}</p>
        </div>
      </template>
      <div v-else class="detail-loading">加载中…</div>
    </el-drawer>

    <el-dialog v-model="readersVisible" :title="`已读统计 · ${readersTitle}`" width="720px">
      <div class="readers-toolbar">
        <el-radio-group v-model="readersFilters.readStatus" @change="searchReaders">
          <el-radio-button value="UNREAD">未读</el-radio-button>
          <el-radio-button value="READ">已读</el-radio-button>
          <el-radio-button value="ALL">全部</el-radio-button>
        </el-radio-group>
        <el-input v-model="readersFilters.keyword" class="readers-keyword" placeholder="搜索用户名或昵称" clearable
                  @keyup.enter="searchReaders" @clear="searchReaders" />
      </div>
      <el-table v-loading="readersLoading" :data="readers" row-key="userId" height="360px">
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="deptName" label="部门" min-width="160" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.read ? 'success' : 'warning'" size="small">{{ row.read ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="阅读时间" width="160">
          <template #default="{ row }">{{ row.readAt ? formatDateTime(row.readAt) : '—' }}</template>
        </el-table-column>
      </el-table>
      <div class="table-pagination">
        <el-pagination
          v-model:current-page="readersPage.page"
          :page-size="readersPage.pageSize"
          :total="readersPage.total"
          :page-sizes="PAGE_SIZES"
          layout="total, prev, pager, next, sizes"
          background
          @current-change="onReadersPageChange"
          @size-change="onReadersPageSizeChange"
        />
      </div>
    </el-dialog>
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
  width: 220px;
}

.filter-select {
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
  gap: var(--space-2);
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

.scope-list {
  width: 100%;
}

.scope-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.scope-type {
  width: 170px;
}

.scope-target {
  flex: 1;
  min-width: 0;
}

.scope-hint {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.detail-title {
  margin: 0;
  font-size: var(--font-size-lg);
}

.detail-tags {
  display: flex;
  gap: var(--space-2);
  flex: 0 0 auto;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin: var(--space-3) 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.detail-section {
  margin-top: var(--space-5);
}

.detail-section h4 {
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.scope-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.detail-content {
  margin: 0;
  padding: var(--space-4);
  font-family: inherit;
  font-size: var(--font-size-sm);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--color-surface-subtle);
  border-radius: var(--radius-md);
}

.detail-loading {
  color: var(--color-text-tertiary);
}

.readers-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.readers-keyword {
  width: 220px;
}
</style>
