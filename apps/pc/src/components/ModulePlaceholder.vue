<script setup lang="ts">
import { toRefs } from 'vue';
import { useRouter } from 'vue-router';

const props = defineProps<{
  title: string;
  description?: string;
}>();

const { title, description } = toRefs(props);
const router = useRouter();
</script>

<template>
  <div class="page-container">
    <div class="page-card module-card">
      <div class="page-toolbar">
        <h1 class="page-title" :title="title">{{ title }}</h1>
      </div>
      <div class="module-body">
        <el-icon :size="44" class="module-icon"><Grid /></el-icon>
        <p class="module-title">模块骨架已就绪</p>
        <p class="module-description">{{ description || '功能将在后续阶段接入，当前页面用于验证路由与布局。' }}</p>
        <el-button @click="router.replace('/dashboard')">返回工作台</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.module-card {
  overflow: hidden;
}

.module-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-12) var(--space-6);
  text-align: center;
}

.module-icon {
  color: var(--color-text-tertiary);
}

.module-title {
  margin: var(--space-2) 0 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.module-description {
  margin: 0 0 var(--space-4);
  max-width: 480px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
</style>
