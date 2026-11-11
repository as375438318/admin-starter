<script setup lang="ts">
import type { MenuRoute } from '@/api/types';

const props = defineProps<{
  items: MenuRoute[];
}>();

function visibleChildren(item: MenuRoute): MenuRoute[] {
  const children = (item.children ?? []).filter((child) => !child.meta?.hidden && child.component !== 'Layout');
  return children;
}

function hasChildren(item: MenuRoute): boolean {
  return visibleChildren(item).length > 0;
}
</script>

<template>
  <template v-for="item in props.items" :key="item.id">
    <el-sub-menu v-if="hasChildren(item)" :index="item.path">
      <template #title>
        <el-icon v-if="item.meta?.icon"><component :is="item.meta.icon" /></el-icon>
        <span>{{ item.meta?.title ?? item.name }}</span>
      </template>
      <SideMenu :items="visibleChildren(item)" />
    </el-sub-menu>
    <el-menu-item v-else-if="!item.meta?.hidden" :index="item.path">
      <el-icon v-if="item.meta?.icon"><component :is="item.meta.icon" /></el-icon>
      <template #title>
        <span>{{ item.meta?.title ?? item.name }}</span>
      </template>
    </el-menu-item>
  </template>
</template>
