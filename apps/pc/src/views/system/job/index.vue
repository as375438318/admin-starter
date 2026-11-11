<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormRules } from 'element-plus';
import { jobApi, jobLogApi, type JobPayload } from '@/api/job';
import { userApi } from '@/api/user';
import { ApiError } from '@/api/http';
import type {
  JobConcurrencyPolicy,
  JobItem,
  JobKeyOption,
  JobLogDetail,
  JobLogItem,
  JobNotifyPolicy,
  JobRunResult,
  JobStatus,
  JobTriggerType,
  UserOption,
} from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { formatDateTime } from '@/utils/datetime';
import { createOptionReporter } from '@/utils/option-load';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useCrudDrawer } from '@/composables/use-crud-drawer';

const STATUS_LABELS: Record<JobStatus, string> = { ENABLED: '启用', DISABLED: '停用' };
const STATUS_TAG: Record<JobStatus, 'success' | 'info'> = { ENABLED: 'success', DISABLED: 'info' };
const CONCURRENCY_LABELS: Record<JobConcurrencyPolicy, string> = {
  DISALLOW: '禁止并发',
  ALLOW: '允许并发',
};
const NOTIFY_LABELS: Record<JobNotifyPolicy, string> = {
  NEVER: '不通知',
  ON_FAILURE: '单次失败通知',
  ON_CONSECUTIVE: '连续失败通知',
};
const RESULT_LABELS: Record<JobRunResult, string> = {
  SUCCESS: '成功',
  FAIL: '失败',
  TIMEOUT: '超时',
  SKIPPED: '跳过',
};
const RESULT_TAG: Record<JobRunResult, 'success' | 'danger' | 'warning' | 'info'> = {
  SUCCESS: 'success',
  FAIL: 'danger',
  TIMEOUT: 'warning',
  SKIPPED: 'info',
};
const TRIGGER_LABELS: Record<JobTriggerType, string> = { AUTO: '自动', MANUAL: '手动' };

interface JobFilters {
  keyword: string;
  status?: JobStatus;
}

interface JobForm {
  name: string;
  groupName: string;
  jobKey: string;
  cronExpression: string;
  description: string;
  status: JobStatus;
  concurrencyPolicy: JobConcurrencyPolicy;
  timeoutSeconds: number;
  retryTimes: number;
  retryIntervalSeconds: number;
  notifyPolicy: JobNotifyPolicy;
  notifyThreshold: number;
  notifyTargets: number[];
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
} = usePagedList<JobItem, JobFilters>({
  initialFilters: { keyword: '', status: undefined },
  fetcher: (params) =>
    jobApi.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      status: params.status,
    }),
});

const jobKeys = ref<JobKeyOption[]>([]);
const userOptions = ref<UserOption[]>([]);
const reportOptionFailure = createOptionReporter();

const {
  visible: drawerVisible,
  editingId,
  submitting,
  formRef,
  form,
  openCreate,
  openEdit: openEditDrawer,
  submit: submitDrawer,
} = useCrudDrawer<JobForm>({
  emptyForm: () => ({
    name: '',
    groupName: 'DEFAULT',
    jobKey: jobKeys.value[0]?.key ?? '',
    cronExpression: '0 * * * * *',
    description: '',
    status: 'DISABLED',
    concurrencyPolicy: 'DISALLOW',
    timeoutSeconds: 60,
    retryTimes: 0,
    retryIntervalSeconds: 10,
    notifyPolicy: 'NEVER',
    notifyThreshold: 3,
    notifyTargets: [],
  }),
});

const rules: FormRules = {
  name: [
    { required: true, message: '任务名称不能为空', trigger: 'blur' },
    { max: 64, message: '任务名称不能超过 64 字', trigger: 'blur' },
  ],
  jobKey: [{ required: true, message: '请选择已注册的任务标识', trigger: 'change' }],
  cronExpression: [{ required: true, message: 'cron 表达式不能为空', trigger: 'blur' }],
};

const selectedKeyDescription = computed(
  () => jobKeys.value.find((item) => item.key === form.jobKey)?.description ?? '',
);

async function loadOptions(): Promise<void> {
  try {
    jobKeys.value = await jobApi.keys();
  } catch (error) {
    reportOptionFailure('任务 Key 选项', error);
  }
  try {
    userOptions.value = await userApi.options();
  } catch (error) {
    reportOptionFailure('用户选项', error);
  }
}

function openEdit(row: JobItem): void {
  openEditDrawer(row.id, {
    name: row.name,
    groupName: row.groupName,
    jobKey: row.jobKey,
    cronExpression: row.cronExpression,
    description: row.description ?? '',
    status: row.status,
    concurrencyPolicy: row.concurrencyPolicy,
    timeoutSeconds: row.timeoutSeconds,
    retryTimes: row.retryTimes,
    retryIntervalSeconds: row.retryIntervalSeconds,
    notifyPolicy: row.notifyPolicy,
    notifyThreshold: row.notifyThreshold,
    notifyTargets: parseTargets(row.notifyTargets),
  });
}

function parseTargets(raw: string | null): number[] {
  if (!raw) {
    return [];
  }
  return raw
    .split(',')
    .map((part) => Number(part.trim()))
    .filter((value) => Number.isFinite(value) && value > 0);
}

function buildPayload(): JobPayload {
  return {
    name: form.name.trim(),
    groupName: form.groupName.trim() || 'DEFAULT',
    jobKey: form.jobKey,
    cronExpression: form.cronExpression.trim(),
    description: form.description.trim() || null,
    status: form.status,
    concurrencyPolicy: form.concurrencyPolicy,
    timeoutSeconds: form.timeoutSeconds,
    retryTimes: form.retryTimes,
    retryIntervalSeconds: form.retryIntervalSeconds,
    notifyPolicy: form.notifyPolicy,
    notifyThreshold: form.notifyThreshold,
    notifyTargets: form.notifyTargets.length ? form.notifyTargets.join(',') : null,
  };
}

async function submit(): Promise<void> {
  const saved = await submitDrawer(async ({ editingId: id }) => {
    try {
      if (id === null) {
        await jobApi.create(buildPayload());
        ElMessage.success('任务已创建');
      } else {
        await jobApi.update(id, buildPayload());
        ElMessage.success('任务已保存，cron 立即生效');
      }
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

async function toggleStatus(row: JobItem): Promise<void> {
  const target: JobStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
  if (target === 'DISABLED') {
    try {
      await ElMessageBox.confirm(`确定停用任务「${row.name}」吗？停用后不再自动执行。`, '停用任务', {
        type: 'warning',
        confirmButtonText: '停用',
        cancelButtonText: '取消',
        closeOnClickModal: false,
      });
    } catch {
      return;
    }
  }
  await jobApi.changeStatus(row.id, target);
  ElMessage.success(target === 'ENABLED' ? '任务已启用' : '任务已停用');
  await load();
}

async function runOnce(row: JobItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定立即执行任务「${row.name}」吗？本次执行会单独记录为“手动”触发。`,
      '手动执行',
      {
        type: 'info',
        confirmButtonText: '执行',
        cancelButtonText: '取消',
        closeOnClickModal: false,
      },
    );
  } catch {
    return;
  }
  try {
    const result = await jobApi.run(row.id);
    if (result.result === 'SUCCESS') {
      ElMessage.success(`执行成功，耗时 ${result.elapsedMs}ms，影响 ${result.affectedRows ?? 0} 条`);
    } else {
      ElMessage.warning(`执行${RESULT_LABELS[result.result]}：${result.errorSummary ?? '无异常摘要'}`);
    }
  } finally {
    await load();
  }
}

async function remove(row: JobItem): Promise<void> {
  const confirmed = await confirmDelete({
    message: `确定删除任务「${row.name}」吗？删除会取消调度，执行日志保留。`,
    title: '删除任务',
  });
  if (!confirmed) {
    return;
  }
  await jobApi.remove(row.id);
  ElMessage.success('任务已删除');
  if (rows.value.length === 1 && page.page > 1) {
    page.page -= 1;
  }
  await load();
}

// ---------------------------------------------------------------- 执行日志

const logsVisible = ref(false);
const logsJob = ref<JobItem | null>(null);
const logDetailVisible = ref(false);
const logDetail = ref<JobLogDetail | null>(null);

interface LogFilters {
  result?: JobRunResult;
  triggerType?: JobTriggerType;
}

const {
  page: logsPage,
  filters: logsFilters,
  rows: logs,
  loading: logsLoading,
  load: loadLogs,
  search: searchLogs,
  onPageSizeChange: onLogsPageSizeChange,
  onPageChange: onLogsPageChange,
} = usePagedList<JobLogItem, LogFilters>({
  initialFilters: { result: undefined, triggerType: undefined },
  // 日志抽屉内的加载失败不覆盖整页，保持与改造前一致的静默处理
  onError: () => undefined,
  fetcher: (params) =>
    logsJob.value
      ? jobLogApi.page({
          page: params.page,
          pageSize: params.pageSize,
          jobId: logsJob.value.id,
          result: params.result,
          triggerType: params.triggerType,
        })
      : Promise.resolve({ page: params.page, pageSize: params.pageSize, list: [], total: 0 }),
});

async function openLogs(row: JobItem): Promise<void> {
  logsJob.value = row;
  logsVisible.value = true;
  Object.assign(logsFilters, { result: undefined, triggerType: undefined });
  logsPage.page = 1;
  await loadLogs();
}

async function openLogDetail(row: JobLogItem): Promise<void> {
  logDetailVisible.value = true;
  logDetail.value = null;
  try {
    logDetail.value = await jobLogApi.detail(row.id);
  } catch {
    logDetailVisible.value = false;
  }
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
          placeholder="搜索任务名称或标识"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="filters.status" class="filter-select" placeholder="状态" clearable @change="search">
          <el-option v-for="(label, value) in STATUS_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <el-button v-permission="'system:job:create'" type="primary" @click="openCreate">新增任务</el-button>
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
            <p class="empty-title">{{ hasFilter ? '没有匹配的任务' : '暂无定时任务' }}</p>
            <p class="empty-desc">{{ hasFilter ? '调整筛选条件后再试。' : '新增任务并启用后即可按 cron 自动执行。' }}</p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
          </div>
        </template>
        <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openEdit(row)">{{ row.name }}</el-link>
            <el-tag v-if="row.running" size="small" type="warning" class="running-tag">执行中</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="groupName" label="分组" width="110" show-overflow-tooltip />
        <el-table-column prop="jobKey" label="任务标识" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.jobKey }}</span>
            <el-tag v-if="!row.registered" size="small" type="danger">未注册</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cronExpression" label="cron" min-width="130" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.cronExpression }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG[row.status as JobStatus]" size="small">
              {{ STATUS_LABELS[row.status as JobStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下次执行" width="160">
          <template #default="{ row }">
            {{ row.nextRunAt ? formatDateTime(row.nextRunAt) : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="最近结果" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.lastRunResult" :type="RESULT_TAG[row.lastRunResult as JobRunResult]" size="small">
              {{ RESULT_LABELS[row.lastRunResult as JobRunResult] }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-permission="'system:job:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button
                v-permission="'system:job:run'"
                link
                type="primary"
                :disabled="row.running"
                @click="runOnce(row)"
              >
                执行
              </el-button>
              <el-button v-permission="'system:job:status'" link type="primary" @click="toggleStatus(row)">
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
              <el-button v-permission="'system:job:log'" link type="primary" @click="openLogs(row)">日志</el-button>
              <el-button v-permission="'system:job:delete'" link type="danger" @click="remove(row)">删除</el-button>
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

    <el-drawer
      v-model="drawerVisible"
      :title="editingId === null ? '新增定时任务' : '编辑定时任务'"
      size="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" label-position="left">
        <el-form-item label="任务名称" prop="name">
          <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="分组">
          <el-input v-model="form.groupName" maxlength="64" placeholder="默认 DEFAULT" />
        </el-form-item>
        <el-form-item label="任务标识" prop="jobKey">
          <el-select v-model="form.jobKey" filterable style="width: 100%" placeholder="选择已注册的任务标识">
            <el-option v-for="item in jobKeys" :key="item.key" :label="item.key" :value="item.key" />
          </el-select>
          <p class="form-hint">{{ selectedKeyDescription || '只允许调度代码中已注册的任务 Bean，不支持脚本/命令/SQL。' }}</p>
        </el-form-item>
        <el-form-item label="cron 表达式" prop="cronExpression">
          <el-input v-model="form.cronExpression" class="mono-input" maxlength="64"
                    placeholder="秒 分 时 日 月 周，例如 0 * * * * *" />
          <p class="form-hint">Spring 6 段格式，按服务器时区解析；修改保存后立即生效，无需重启。</p>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="255" show-word-limit
                    placeholder="任务用途说明（可选）" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button value="ENABLED">启用</el-radio-button>
            <el-radio-button value="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="并发策略">
          <el-radio-group v-model="form.concurrencyPolicy">
            <el-radio-button v-for="(label, value) in CONCURRENCY_LABELS" :key="value" :value="value">
              {{ label }}
            </el-radio-button>
          </el-radio-group>
          <p class="form-hint">单机部署：并发控制只在当前进程内生效，多实例会重复执行（见设计文档 §8）。</p>
        </el-form-item>
        <el-form-item label="超时时间（秒）">
          <el-input-number v-model="form.timeoutSeconds" :min="1" :max="3600" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="失败重试次数">
          <el-input-number v-model="form.retryTimes" :min="0" :max="10" :step="1" controls-position="right" />
          <span class="form-inline-hint">不含首次执行</span>
        </el-form-item>
        <el-form-item label="重试间隔（秒）">
          <el-input-number v-model="form.retryIntervalSeconds" :min="0" :max="3600" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="失败通知">
          <el-select v-model="form.notifyPolicy" style="width: 100%">
            <el-option v-for="(label, value) in NOTIFY_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.notifyPolicy === 'ON_CONSECUTIVE'" label="连续失败阈值">
          <el-input-number v-model="form.notifyThreshold" :min="1" :max="10" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item v-if="form.notifyPolicy !== 'NEVER'" label="通知接收人">
          <el-select v-model="form.notifyTargets" multiple filterable style="width: 100%"
                     placeholder="留空则通知任务创建人">
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="user.nickname ? `${user.nickname}（${user.username}）` : user.username"
              :value="user.id"
            />
          </el-select>
          <p class="form-hint">失败通知通过站内信发送，使用独立事务，任务失败不会影响通知落库。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">保存</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="logsVisible" :title="`执行日志 · ${logsJob?.name ?? ''}`" size="860px">
      <div class="logs-toolbar">
        <el-select v-model="logsFilters.result" class="filter-select" placeholder="结果" clearable @change="searchLogs">
          <el-option v-for="(label, value) in RESULT_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="logsFilters.triggerType" class="filter-select" placeholder="触发方式" clearable @change="searchLogs">
          <el-option v-for="(label, value) in TRIGGER_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
      </div>
      <el-table v-loading="logsLoading" :data="logs" row-key="id" height="480px">
        <el-table-column label="触发" width="70">
          <template #default="{ row }">{{ TRIGGER_LABELS[row.triggerType as JobTriggerType] }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="RESULT_TAG[row.result as JobRunResult]" size="small">
              {{ RESULT_LABELS[row.result as JobRunResult] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="right">
          <template #default="{ row }">{{ row.elapsedMs }} ms</template>
        </el-table-column>
        <el-table-column prop="attemptCount" label="执行次数" width="90" align="right" />
        <el-table-column label="影响条数" width="90" align="right">
          <template #default="{ row }">{{ row.affectedRows ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="errorSummary" label="异常摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openLogDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-pagination">
        <el-pagination
          v-model:current-page="logsPage.page"
          :page-size="logsPage.pageSize"
          :total="logsPage.total"
          :page-sizes="PAGE_SIZES"
          layout="total, prev, pager, next, sizes"
          background
          @current-change="onLogsPageChange"
          @size-change="onLogsPageSizeChange"
        />
      </div>
    </el-drawer>

    <el-dialog v-model="logDetailVisible" title="执行日志详情" width="760px">
      <template v-if="logDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="任务">{{ logDetail.jobName }}（{{ logDetail.jobKey }}）</el-descriptions-item>
          <el-descriptions-item label="触发方式">{{ TRIGGER_LABELS[logDetail.triggerType] }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ RESULT_LABELS[logDetail.result] }}</el-descriptions-item>
          <el-descriptions-item label="执行次数">{{ logDetail.attemptCount }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatDateTime(logDetail.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatDateTime(logDetail.finishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ logDetail.elapsedMs }} ms</el-descriptions-item>
          <el-descriptions-item label="影响条数">{{ logDetail.affectedRows ?? '—' }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-section">
          <h4>异常摘要</h4>
          <p>{{ logDetail.errorSummary || '无' }}</p>
        </div>
        <div class="detail-section">
          <h4>异常堆栈</h4>
          <pre class="stack-pre">{{ logDetail.errorStack || '无' }}</pre>
        </div>
      </template>
      <div v-else class="detail-loading">加载中…</div>
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

.running-tag {
  margin-left: var(--space-2);
}

.mono,
.mono-input :deep(.el-input__inner) {
  font-family: var(--font-family-mono, ui-monospace, 'SFMono-Regular', Consolas, monospace);
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

.form-hint {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  line-height: 1.5;
}

.form-inline-hint {
  margin-left: var(--space-3);
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.logs-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.detail-section {
  margin-top: var(--space-5);
}

.detail-section h4 {
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-md);
}

.stack-pre {
  max-height: 260px;
  overflow: auto;
  padding: var(--space-3);
  border-radius: var(--radius-md, 6px);
  background: var(--color-bg-secondary, #f5f7fa);
  font-family: var(--font-family-mono, ui-monospace, 'SFMono-Regular', Consolas, monospace);
  font-size: var(--font-size-sm);
  white-space: pre-wrap;
  word-break: break-all;
}

.detail-loading {
  padding: var(--space-6) 0;
  text-align: center;
  color: var(--color-text-tertiary);
}
</style>
