import { get, post, put, del } from '@/utils/request'
import type { PageResponse, Tag, TagVO, ScriptVO, WorkerVO } from '@/types'

export interface TagListParams {
  page?: number
  size?: number
  name?: string
}

export interface TagCreateDTO {
  name: string
  description?: string
}

export interface TagUpdateDTO {
  id: number
  name: string
  description?: string
}

export const tagApi = {
  list: (params: TagListParams = {}) => {
    return get<PageResponse<TagVO>>('/api/tag/list', params)
  },
  
  getById: (id: string | number) => {
    return get<TagVO>(`/api/tag/${id}`)
  },
  
  create: (data: TagCreateDTO) => {
    return post<Tag>('/api/tag', data)
  },
  
  update: (data: TagUpdateDTO) => {
    return put(`/api/tag/${data.id}`, data)
  },
  
  delete: (id: string | number) => {
    return del(`/api/tag/${id}`)
  },

  getScripts: (tagId: number) => {
    return get<ScriptVO[]>(`/api/tag/${tagId}/scripts`)
  },

  getWorkers: (tagId: number) => {
    return get<WorkerVO[]>(`/api/tag/${tagId}/workers`)
  },

  bindScript: (tagId: number, scriptId: string) => {
    return post(`/api/tag/${tagId}/scripts/${scriptId}`)
  },

  unbindScript: (tagId: number, scriptId: string) => {
    return del(`/api/tag/${tagId}/scripts/${scriptId}`)
  },

  bindWorker: (tagId: number, workerId: string) => {
    return post(`/api/tag/${tagId}/workers/${workerId}`)
  },

  unbindWorker: (tagId: number, workerId: string) => {
    return del(`/api/tag/${tagId}/workers/${workerId}`)
  },
}
