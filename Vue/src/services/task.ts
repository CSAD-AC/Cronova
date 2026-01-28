import { get, post, put, del } from '@/utils/request'
import type { PageResponse, Task, TaskCreateDTO, TaskUpdateDTO, TaskVO, HealthCheckResult, CronDescriptionResult } from '@/types'

export interface TaskListParams {
  pageNum?: number
  pageSize?: number
  name?: string
  type?: string
  status?: string
  taskType?: string
}

export const taskApi = {
  list: (params: TaskListParams = {}) => {
    return get<PageResponse<TaskVO>>('/api/tasks', params)
  },
  
  getById: (id: string) => {
    return get<TaskVO>(`/api/tasks/${id}`)
  },
  
  create: (data: TaskCreateDTO) => {
    return post<string>('/api/tasks', data)
  },
  
  update: (data: TaskUpdateDTO) => {
    return put('/api/tasks', data)
  },
  
  delete: (id: string) => {
    return del(`/api/tasks/${id}`)
  },
  
  enable: (id: string) => {
    return put(`/api/tasks/${id}/enable`)
  },
  
  disable: (id: string) => {
    return put(`/api/tasks/${id}/disable`)
  },
  
  publish: (id: string) => {
    return post(`/api/tasks/${id}/publish`)
  },
  
  getRuns: (taskId: string, pageNum = 1, pageSize = 10) => {
    return get<PageResponse<any>>(`/api/tasks/${taskId}/runs`, { pageNum, pageSize })
  },

  // 健康检查 - 针对回调任务
  healthCheck: (configId: string) => {
    return post<HealthCheckResult>(`/api/callback-job-configs/${configId}/health-check`)
  },

  // 获取Cron表达式的中文描述
  getCronDescription: (cronExpr: string) => {
    return get<CronDescriptionResult>(`/api/cron/description?cronExpr=${encodeURIComponent(cronExpr)}`)
  },
}
