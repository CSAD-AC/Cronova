import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserVO, LoginRequest } from '@/types'
import { userApi } from '@/services'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<UserVO | null>(null)
  const roles = ref<string[]>([])

  const isLoggedIn = computed(() => !!token.value)

  async function login(data: LoginRequest) {
    try {
      const response = await userApi.login(data)
      console.log('登录响应:', response)
      if (response) {
        // 兼容后端返回格式：可能是 {token, user} 或直接在response中
        const tokenValue = response.token || response.data?.token || (typeof response === 'string' ? response : '')
        const userValue = response.user || response.data?.user || null
        
        token.value = tokenValue
        user.value = userValue
        localStorage.setItem('token', String(tokenValue))
        console.log('登录成功，token:', tokenValue)
        return true
      }
      return false
    } catch (error: any) {
      console.error('登录失败:', error)
      return false
    }
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
    login,
    fetchUserInfo,
    logout,
  }
})
