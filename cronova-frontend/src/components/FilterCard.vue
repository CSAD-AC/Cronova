<template>
  <div class="filter-card">
    <slot />
  </div>
</template>

<script setup lang="ts">
/**
 * 筛选卡片组件：统一列表页筛选表单的容器样式。
 * 内容通过默认插槽注入（通常为 el-form :inline）。
 */
</script>

<style scoped>
.filter-card {
  padding: 16px 20px;
  border-radius: 12px;
  border: 1px solid var(--cronova-border);
  background: var(--cronova-surface);
  box-shadow: var(--cronova-card-shadow);
  margin-bottom: 16px;
  transition: background-color .25s ease, border-color .25s ease;
}

.filter-card :deep(.el-form-item) {
  margin-bottom: 8px;
  margin-right: 16px;
}

.filter-card :deep(.el-form-item__label) {
  color: var(--cronova-muted);
  font-size: 13px;
}
</style>
