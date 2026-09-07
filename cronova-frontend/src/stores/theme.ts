import { ref } from 'vue'
import { defineStore } from 'pinia'

/** 主题类型：浅色 / 深色 */
export type ThemeMode = 'light' | 'dark'

/** localStorage 持久化键名 */
const STORAGE_KEY = 'cronova-theme'

/**
 * 主题 Store（"调度脉搏"设计系统）
 * 职责：管理 light/dark 双主题切换，写入 <html data-theme="...">，
 * 并将用户偏好持久化到 localStorage。
 */
export const useThemeStore = defineStore('theme', () => {
  /** 当前主题模式 */
  const mode = ref<ThemeMode>('light')

  /**
   * 初始化主题：从 localStorage 读取偏好，无记录时跟随系统偏好。
   * 应在应用挂载前调用，避免主题闪烁（FOUC）。
   */
  function initTheme(): void {
    const saved = localStorage.getItem(STORAGE_KEY) as ThemeMode | null
    if (saved === 'light' || saved === 'dark') {
      mode.value = saved
    } else if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
      mode.value = 'dark'
    }
    applyMode()
  }

  /**
   * 切换主题：light <-> dark 双向切换，并持久化偏好。
   */
  function toggleTheme(): void {
    mode.value = mode.value === 'light' ? 'dark' : 'light'
    localStorage.setItem(STORAGE_KEY, mode.value)
    applyMode()
  }

  /**
   * 将当前模式写入 <html data-theme="...">，并同步 Element Plus
   * 所需的 dark 类，驱动两套 CSS 变量切换。
   */
  function applyMode(): void {
    document.documentElement.setAttribute('data-theme', mode.value)
    document.documentElement.classList.toggle('dark', mode.value === 'dark')
  }

  return { mode, initTheme, toggleTheme }
})
