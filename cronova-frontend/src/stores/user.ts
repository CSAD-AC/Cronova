import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserVO } from '@/types'
import router from '@/router'

/**
 * 用户会话 store（纯状态管理，不调用 API）
 *
 * 设计说明：登录 API 调用由 Login.vue 编排（userApi.login → setSession），
 * 本 store 不再依赖 services 层——否则形成
 * stores/user → services → utils/request → stores/user 的模块循环依赖，
 * 导致 Rollup 分包出现循环 chunk 警告（执行顺序可能破坏）。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<UserVO | null>(null)
  const roles = ref<string[]>([])

  const isLoggedIn = computed(() => !!token.value)

  /**
   * 登录成功后写入会话（token + 用户信息）。
   *
   * @param tokenValue 登录令牌
   * @param userValue  用户信息（可能为 null）
   */
  function setSession(tokenValue: string, userValue: UserVO | null) {
    token.value = tokenValue
    user.value = userValue
    localStorage.setItem('token', String(tokenValue))
  }

  async function fetchUserInfo() {
    console.log('用户信息已从登录响应中获取')
  }

  function logout() {
    token.value = null
    user.value = null
    roles.value = []
    localStorage.removeItem('token')
    router.push('/login')
  }

  return {
    token,
    user,
    roles,
    isLoggedIn,
    setSession,
    fetchUserInfo,
    logout,
  }
})
