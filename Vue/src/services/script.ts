import { get, post, del } from '@/utils/request'
import type { PageResponse, Script, ScriptVO } from '@/types'

export interface ScriptListParams {
  pageNum?: number
  pageSize?: number
  name?: string
}

export const scriptApi = {
  list: (params: ScriptListParams = {}) => {
    return get<ScriptVO[]>('/api/script/list', params)
  },
  
  getById: (id: string) => {
    return get<ScriptVO>(`/api/script/${id}`)
  },
  
  upload: (formData: FormData) => {
    return post<string>('/api/script/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  
  download: (id: string) => {
    // 后端暂无此接口，返回模拟数据
    return Promise.resolve(new Blob())
  },
  
  delete: (id: string) => {
    // 后端暂无此接口，返回成功
    return Promise.resolve()
  },
  
  addTags: (scriptId: string, tagIds: number[]) => {
    return post<number[]>(`/api/script/adds?scriptId=${scriptId}`, tagIds)
  },
  
  update: (id: string, content: string) => {
    // 模拟更新
    return Promise.resolve()
  },

  removeTag: (scriptId: string, tagId: string) => {
    return del(`/api/script/remove?scriptId=${scriptId}&tagId=${tagId}`)
  },
  
  getTags: (scriptId: string) => {
    // 后端暂无此接口，返回空数组
    return Promise.resolve([])
  },
}
