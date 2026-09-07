<template>
  <div class="page-header">
    <div class="header-left">
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
    </div>
    <div class="header-right">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 页面标题组件：统一各页面的标题/副标题/操作区布局。
 * 右侧操作区通过默认插槽注入（如"创建任务"按钮）。
 */
defineProps<{
  /** 页面标题 */
  title: string
  /** 副标题说明（可选） */
  subtitle?: string
}>()
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--sjdt-text);
  margin: 0 0 6px;
  letter-spacing: .3px;
}

.page-subtitle {
  font-size: 13px;
  color: var(--sjdt-muted);
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
