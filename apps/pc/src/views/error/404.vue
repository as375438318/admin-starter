<template>
  <div class="state-block">
    <el-icon :size="40" class="state-icon"><QuestionFilled /></el-icon>
    <p class="state-title">页面不存在</p>
    <p class="state-description">地址可能已变更，或你没有访问该页面的权限。</p>
    <el-button type="primary" @click="$router.replace('/dashboard')">返回工作台</el-button>
  </div>
</template>

<style scoped>
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: var(--space-2);
  text-align: center;
}

.state-icon {
  color: var(--color-text-tertiary);
}

.state-title {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-xl);
  font-weight: 600;
}

.state-description {
  margin: 0 0 var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
</style>
