<template>
  <div class="state-block">
    <el-icon :size="40" class="state-icon"><Lock /></el-icon>
    <p class="state-title">无权访问</p>
    <p class="state-description">当前账号缺少该页面的功能权限，请联系管理员分配。</p>
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
  color: var(--color-warning);
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
