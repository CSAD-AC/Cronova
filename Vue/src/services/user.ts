import { get, post } from '@/utils/request'
import type { UserVO } from '@/types'

export const userApi = {
  login: (data: any) => {
    return post('/api/auth/login', data)
  },
  
  register: (data: any) => {
    return post('/api/auth/register', data)
  },
  
  logout: () => {
    return post('/api/auth/logout')
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

function del(url: string): Promise<any> {
  return import('@/utils/request').then(module => module.default.delete(url))
}
