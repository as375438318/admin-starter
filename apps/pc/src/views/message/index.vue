<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { inboxApi } from '@/api/notice';
import type { InboxNoticeDetail, InboxNoticeItem, NoticeLevel, NoticeType } from '@/api/types';
import { PAGE_SIZES } from '@/constants/pagination';
import { useNoticeStore } from '@/stores/notice';
import { formatDateTime } from '@/utils/datetime';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { usePagedList } from '@/composables/use-paged-list';
import { useTableColumns, type ColumnDefinition } from '@/composables/use-table-columns';
import ColumnSettings from '@/components/ColumnSettings.vue';

interface MessageFilters {
  readStatus: 'ALL' | 'UNREAD' | 'READ';
}

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

const noticeStore = useNoticeStore();
const {
  page,
  filters,
  rows,
  loading,
  loadError,
  skeletonVisible,
  load,
  search,
  onPageSizeChange,
} = usePagedList<InboxNoticeItem, MessageFilters>({
  initialFilters: { readStatus: 'ALL' },
  fetcher: (params) =>
    inboxApi.page({
      page: params.page,
      pageSize: params.pageSize,
      readStatus: params.readStatus,
    }),
  errorMessage: '消息加载失败',
});

/**
 * 列定义（唯一定义处）：驱动表格渲染与列勾选。
 * 收件箱不接排序（按发布时间倒序由后端固定），因此不写 `sortKey`、不传 `onSort`。
 */
const INBOX_COLUMNS: ColumnDefinition[] = [
  // 标题是唯一的详情入口，隐藏后整行不可点，故常显
  { key: 'title', label: '标题', alwaysVisible: true },
  { key: 'level', label: '级别' },
  { key: 'type', label: '类型' },
  { key: 'publisherName', label: '发布人' },
  { key: 'publishedAt', label: '发布时间' },
  { key: 'read', label: '状态' },
];

const inboxTable = useTableColumns({
  storageKey: 'message-inbox',
  columns: INBOX_COLUMNS,
});

const detailVisible = ref(false);
const detail = ref<InboxNoticeDetail | null>(null);
const detailLoading = ref(false);

const unreadText = computed(() => (noticeStore.unreadCount > 0 ? `未读 ${noticeStore.unreadCount}` : '没有未读消息'));

function changeStatus(status: 'ALL' | 'UNREAD' | 'READ'): void {
  filters.readStatus = status;
  search();
}

async function openDetail(row: InboxNoticeItem): Promise<void> {
  detailVisible.value = true;
  detail.value = null;
  detailLoading.value = true;
  try {
    detail.value = await inboxApi.detail(row.id);
    await noticeStore.refresh();
    await load();
  } catch {
    detailVisible.value = false;
  } finally {
    detailLoading.value = false;
  }
}

async function readAll(): Promise<void> {
  const data = await inboxApi.readAll();
  if (data.marked > 0) {
    ElMessage.success(`已将 ${data.marked} 条消息标记为已读`);
  } else {
    ElMessage.info('没有需要标记的未读消息');
  }
  await noticeStore.refresh();
  await load();
}

onMounted(() => {
  void noticeStore.refresh();
  void load();
});
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-radio-group
          :model-value="filters.readStatus"
          @change="(value: 'ALL' | 'UNREAD' | 'READ') => changeStatus(value)"
        >
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="UNREAD">未读</el-radio-button>
          <el-radio-button value="READ">已读</el-radio-button>
        </el-radio-group>
        <span class="unread-text">{{ unreadText }}</span>
      </div>
      <div class="toolbar-actions">
        <ColumnSettings
          :columns="INBOX_COLUMNS"
          :is-visible="inboxTable.isVisible"
          :hidden-count="inboxTable.hiddenCount.value"
          @toggle="inboxTable.toggleColumn"
          @show-all="inboxTable.showAllColumns"
          @invert="inboxTable.invertColumns"
          @reset="inboxTable.resetColumns"
        />
        <el-button :disabled="noticeStore.unreadCount === 0" @click="readAll">全部已读</el-button>
      </div>
    </div>

    <div class="page-card">
      <el-alert v-if="loadError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>消息加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table :data="skeletonVisible ? [] : rows" row-key="id" style="width: 100%">
        <template #empty>
          <TableSkeleton v-if="skeletonVisible" :rows="6" />
          <div v-else-if="!loading" class="table-empty">
            <p class="empty-title">没有消息</p>
            <p class="empty-desc">
              {{ filters.readStatus === 'UNREAD' ? '所有消息都已读完。' : '收到的通知会出现在这里。' }}
            </p>
          </div>
        </template>
        <el-table-column v-if="inboxTable.isVisible('title')" prop="title" label="标题" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="title-cell" :class="{ 'is-unread': !row.read }">
              <i v-if="!row.read" class="unread-dot" />
              <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.title }}</el-link>
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="inboxTable.isVisible('level')" prop="level" label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="LEVEL_TAG[row.level as NoticeLevel]" size="small">
              {{ LEVEL_LABELS[row.level as NoticeLevel] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="inboxTable.isVisible('type')" prop="type" label="类型" width="90">
          <template #default="{ row }">{{ TYPE_LABELS[row.type as NoticeType] }}</template>
        </el-table-column>
        <el-table-column
          v-if="inboxTable.isVisible('publisherName')"
          prop="publisherName"
          label="发布人"
          width="130"
          show-overflow-tooltip
        />
        <el-table-column v-if="inboxTable.isVisible('publishedAt')" prop="publishedAt" label="发布时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column v-if="inboxTable.isVisible('read')" prop="read" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.read ? 'info' : 'warning'" size="small">{{ row.read ? '已读' : '未读' }}</el-tag>
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

    <el-dialog v-model="detailVisible" title="消息详情" width="640px">
      <div v-if="detailLoading" class="detail-loading">加载中…</div>
      <template v-else-if="detail">
        <div class="detail-head">
          <h3 class="detail-title">{{ detail.title }}</h3>
          <div class="detail-tags">
            <el-tag :type="LEVEL_TAG[detail.level]" size="small">{{ LEVEL_LABELS[detail.level] }}</el-tag>
            <el-tag size="small" type="info">{{ TYPE_LABELS[detail.type] }}</el-tag>
          </div>
        </div>
        <div class="detail-meta">
          <span>发布人：{{ detail.publisherName }}</span>
          <span>发布时间：{{ formatDateTime(detail.publishedAt) }}</span>
          <span>阅读时间：{{ detail.readAt ? formatDateTime(detail.readAt) : '—' }}</span>
        </div>
        <pre class="detail-content">{{ detail.content }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar-filters {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.unread-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.toolbar-actions {
  display: flex;
  align-items: center;
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

.title-cell {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.title-cell.is-unread {
  font-weight: 600;
}

.unread-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: var(--color-danger, #f56c6c);
  border-radius: 50%;
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
</style>
