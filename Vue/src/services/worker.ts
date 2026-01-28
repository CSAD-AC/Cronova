import { get, post, del, put } from '@/utils/request'
import type { PageResponse, Worker, WorkerVO } from '@/types'

export interface WorkerListParams {
  pageNum?: number
  pageSize?: number
  name?: string
  status?: string
  tagId?: string
}

export const workerApi = {
  list: (params: WorkerListParams = {}) => {
    return get<PageResponse<WorkerVO>>('/api/frontend/worker/list', params)
  },
  
  getById: (id: string) => {
    return get<WorkerVO>(`/api/frontend/worker/${id}`)
  },
  
  updateHost: (id: string, host: string) => {
    return put(`/api/frontend/worker/${id}/host`, { host })
  },
  
  delete: (id: string) => {
    return del(`/api/frontend/worker/${id}`)
  },
  
  register: (data: { host: string; ip: string; tags?: any[] }) => {
    return post<{ workerId: string; accessKey: string; secretKey: string }>('/api/worker/register', data)
  },
  
  heartbeat: (workerId: string) => {
    return post(`/api/worker/heartbeat/${workerId}`)
  },
  
  status: (workerId: string, cpuUsage: number, memUsage: number, runningTasks: number) => {
    return post(`/api/worker/status/${workerId}`, {}, { params: { cpuUsage, memUsage, runningTasks } })
  },
}
