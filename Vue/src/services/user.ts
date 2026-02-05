import { get, post, del } from '@/utils/request'
import type { User, UserVO, LoginRequest, LoginResponse } from '@/types'

export const userApi = {
  login: (data: LoginRequest) => {
    return post<LoginResponse>('/api/auth/login', data)
  },
  
  register: (data: any) => {
    return post('/api/auth/register', data)
  },
  
  logout: () => {
    return post('/api/auth/logout')
  },
  
  getProfile: () => {
    // 后端没有profile接口，从token中获取或返回null
    return Promise.resolve(null)
  },
  
  getById: (id: string) => {
    return get<UserVO>(`/api/users/${id}`)
  },
  
  update: (data: any) => {
    return put('/api/users', data)
  },
  
  delete: (id: string) => {
    return del(`/api/users/${id}`)
  },
}

function put(url: string, data?: any): Promise<any> {
  return import('@/utils/request').then(module => module.default.put(url, data))
}
