import { get, post, put, del } from '@/utils/request'
import type { PageResponse, Tag, TagVO } from '@/types'

export interface TagListParams {
  pageNum?: number
  pageSize?: number
  name?: string
  operator?: string
  version?: string
}

export const tagApi = {
  list: (params: TagListParams = {}) => {
    return get<PageResponse<TagVO>>('/api/tag/list', params)
  },
  
  getById: (id: string) => {
    return get<TagVO>(`/api/tag/${id}`)
  },
  
  create: (data: { name: string; operator?: string; version?: string }) => {
    return post<string>('/api/tag', data)
  },
  
  update: (data: Tag) => {
    return put('/api/tag', data)
  },
  
  delete: (id: string) => {
    return del(`/api/tag/${id}`)
  },
}
