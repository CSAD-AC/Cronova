import { get, post, del, put } from '@/utils/request'
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
    return post<ScriptVO>('/api/script/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  uploadDirectory: (formData: FormData) => {
    return post<ScriptVO>('/api/script/upload-directory', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  
  download: (id: string) => {
    return get<Blob>(`/api/script/download/${id}`, {}, { responseType: 'blob' })
  },

  downloadDirectory: (id: string) => {
    return get<Blob>(`/api/script/download-directory/${id}`, {}, { responseType: 'blob' })
  },
  
  delete: (id: string) => {
    return del(`/api/script/${id}`)
  },
  
  addTags: (scriptId: string, tagIds: number[]) => {
    return post<number[]>(`/api/script/adds?scriptId=${scriptId}`, tagIds)
  },
  
  update: (id: string, content: string) => {
    return put(`/api/script/${id}`, { content }, {
      headers: { 'Content-Type': 'text/plain' }
    })
  },

  removeTag: (scriptId: string, tagId: string | number) => {
    return del(`/api/script/remove?scriptId=${scriptId}&tagId=${tagId}`)
  },
  
  getTags: (scriptId: string) => {
    return get<number[]>(`/api/script/tags?scriptId=${scriptId}`)
  },
}
