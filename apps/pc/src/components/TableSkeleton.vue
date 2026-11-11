<script setup lang="ts">
withDefaults(defineProps<{ rows?: number }>(), { rows: 6 });

const widths = ['24%', '14%', '18%', '12%', '16%'];
</script>

<template>
  <div class="table-skeleton" aria-hidden="true">
    <div v-for="index in rows" :key="index" class="skeleton-row">
      <span v-for="(width, i) in widths" :key="i" class="skeleton-bar" :style="{ width }" />
    </div>
  </div>
</template>

<style scoped>
.table-skeleton {
  width: 100%;
  padding: 0 var(--space-3);
}

.skeleton-row {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  height: var(--table-row-height);
  border-bottom: 1px solid var(--color-divider);
}

.skeleton-bar {
  flex: none;
  height: 16px;
  border-radius: var(--radius-sm);
  background: linear-gradient(
    90deg,
    var(--color-surface-muted) 25%,
    var(--color-surface-subtle) 37%,
    var(--color-surface-muted) 63%
  );
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0 50%;
  }
}
</style>
