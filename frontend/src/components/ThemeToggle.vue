<template>
  <button
    class="theme-toggle"
    :title="isDark ? '切换到浅色模式' : '切换到深色模式'"
    @click="handleToggle"
  >
    <el-icon :size="16">
      <Sunny v-if="isDark" />
      <Moon v-else />
    </el-icon>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Sunny, Moon } from '@element-plus/icons-vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

/** 当前是否深色模式 */
const isDark = computed(() => themeStore.mode === 'dark')

/**
 * 切换深浅主题
 */
function handleToggle(): void {
  themeStore.toggleTheme()
}
</script>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  border: 1px solid var(--sjdt-border);
  background: var(--sjdt-surface);
  color: var(--sjdt-muted);
  cursor: pointer;
  transition: all .18s ease;
}

.theme-toggle:hover {
  color: var(--sjdt-primary);
  border-color: var(--sjdt-primary);
  background: var(--sjdt-primary-soft);
}
</style>
