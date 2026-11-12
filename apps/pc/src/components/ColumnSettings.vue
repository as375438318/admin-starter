<script setup lang="ts">
import { computed } from 'vue';
import type { ColumnDefinition } from '@/composables/use-table-columns';

/**
 * 列设置面板：勾选控制显示哪些列。
 *
 * 只负责「展示与交互」，显隐状态由 `useTableColumns` 持有并持久化——面板本身无状态，
 * 便于在 16 个列表页复用。
 */
const props = defineProps<{
  columns: ColumnDefinition[];
  isVisible: (key: string) => boolean;
  hiddenCount: number;
}>();

const emit = defineEmits<{
  toggle: [key: string];
  showAll: [];
  invert: [];
  reset: [];
}>();

/** alwaysVisible 的列不允许取消勾选；无权限列不该出现在面板里，由页面过滤后传入 */
const items = computed(() => props.columns.filter((column) => column.permission === undefined || props.isVisible(column.key) || column.alwaysVisible !== true));
</script>

<template>
  <el-popover placement="bottom-end" :width="240" trigger="click">
    <template #reference>
      <el-button class="column-settings-trigger">
        列设置<span v-if="hiddenCount" class="column-settings-count">（隐藏 {{ hiddenCount }}）</span>
      </el-button>
    </template>

    <div class="column-settings">
      <div class="column-settings-actions">
        <el-button link size="small" @click="emit('showAll')">全选</el-button>
        <el-button link size="small" @click="emit('invert')">反选</el-button>
        <el-button link size="small" @click="emit('reset')">恢复默认</el-button>
      </div>
      <el-divider class="column-settings-divider" />
      <el-checkbox
        v-for="column in items"
        :key="column.key"
        :model-value="isVisible(column.key)"
        :disabled="column.alwaysVisible === true"
        class="column-settings-item"
        @change="emit('toggle', column.key)"
      >
        {{ column.label }}
        <span v-if="column.alwaysVisible" class="column-settings-lock">常显</span>
      </el-checkbox>
    </div>
  </el-popover>
</template>

<style scoped>
.column-settings-count {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.column-settings {
  display: flex;
  flex-direction: column;
}

.column-settings-actions {
  display: flex;
  gap: var(--space-2);
}

.column-settings-divider {
  margin: var(--space-2) 0;
}

.column-settings-item {
  margin-right: 0;
  height: 28px;
}

.column-settings-lock {
  margin-left: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}
</style>
