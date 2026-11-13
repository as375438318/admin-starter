<script setup lang="ts">
import { computed, ref } from 'vue';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';

/**
 * Element Plus 图标选择器（菜单管理的「图标」字段用）。
 *
 * <p>为什么不再用文本输入：图标名是 280+ 个 PascalCase 单词（`Setting` / `Operation` / `Avatar`…），
 * 手输必拼错，而拼错的后果是**侧边栏静默不显示图标**（`<component :is="未注册的名字">` 不报错、只渲染空）。
 * 这里把同一个图标集合可视化，选出来的名字一定注册过（`main.ts` 全局注册的就是这个包）。</p>
 *
 * <p>历史值兼容：早期手输的值可能不在当前图标集合里，此时仍按原文显示并给出提示，不擅自清空。</p>
 */
const props = withDefaults(
  defineProps<{
    modelValue?: string;
    placeholder?: string;
  }>(),
  { modelValue: '', placeholder: '选择图标' },
);

const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const keyword = ref('');
const visible = ref(false);

/** 图标名集合与 `main.ts` 里的全局注册同源，保证选出来的名字一定能渲染 */
const ICON_NAMES: string[] = Object.keys(ElementPlusIconsVue).sort((a, b) => a.localeCompare(b));

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return kw ? ICON_NAMES.filter((name) => name.toLowerCase().includes(kw)) : ICON_NAMES;
});

const current = computed(() => props.modelValue ?? '');
const isKnown = computed(() => current.value === '' || ICON_NAMES.includes(current.value));

function select(name: string): void {
  emit('update:modelValue', name);
  visible.value = false;
  keyword.value = '';
}

function clear(): void {
  emit('update:modelValue', '');
  visible.value = false;
  keyword.value = '';
}

function onVisibleChange(value: boolean): void {
  visible.value = value;
  if (!value) {
    keyword.value = '';
  }
}
</script>

<template>
  <el-popover v-model:visible="visible" placement="bottom-start" :width="468" trigger="click" @show="onVisibleChange(true)" @hide="onVisibleChange(false)">
    <template #reference>
      <el-input :model-value="current" readonly :placeholder="placeholder" class="icon-picker-trigger">
        <template #prefix>
          <el-icon v-if="current && isKnown"><component :is="current" /></el-icon>
        </template>
        <template #suffix>
          <el-icon v-if="current" class="icon-picker-clear" title="清除" @click.stop="clear"><Close /></el-icon>
        </template>
      </el-input>
    </template>

    <div class="icon-picker">
      <el-input v-model="keyword" placeholder="搜索图标名，如 setting" clearable size="small" class="icon-picker-search" />
      <p v-if="current && !isKnown" class="icon-picker-warning">
        当前值「{{ current }}」不在内置图标集合里，侧边栏不会显示图标；选中任意图标即可修正。
      </p>
      <div v-if="filtered.length" class="icon-grid">
        <button
          v-for="name in filtered"
          :key="name"
          type="button"
          class="icon-grid-item"
          :class="{ 'icon-grid-item--active': name === current }"
          :title="name"
          @click="select(name)"
        >
          <el-icon :size="18"><component :is="name" /></el-icon>
        </button>
      </div>
      <p v-else class="icon-picker-empty">没有匹配的图标</p>
    </div>
  </el-popover>
</template>

<style scoped>
.icon-picker-trigger {
  width: 100%;
}

.icon-picker-clear {
  cursor: pointer;
  color: var(--color-text-tertiary);
}

.icon-picker-clear:hover {
  color: var(--color-text-secondary);
}

.icon-picker-search {
  margin-bottom: var(--space-2);
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(9, 1fr);
  gap: var(--space-1);
  max-height: 260px;
  overflow-y: auto;
}

.icon-grid-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.icon-grid-item:hover {
  background: var(--color-fill-light);
  color: var(--color-primary);
}

.icon-grid-item--active {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.icon-picker-warning,
.icon-picker-empty {
  margin: 0 0 var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}
</style>
