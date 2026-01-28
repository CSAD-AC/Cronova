import { get, post, del } from '@/utils/request'
import type { TaskRun } from '@/types'

export const taskRunApi = {
  list: () => {
    return get<TaskRun[]>('/api/task-run/list')
  },
  
  getById: (taskRunId: string) => {
    return get<TaskRun>(`/api/task-run/${taskRunId}`)
  },
  
  getByTaskId: (taskId: string) => {
    return get<TaskRun[]>(`/api/task-run/task/${taskId}`)
  },
  
  pause: (taskRunId: string, reason?: string) => {
    return post(`/api/task-run/${taskRunId}/pause`, { reason })
  },
  
  resume: (taskRunId: string, reason?: string) => {
    return post(`/api/task-run/${taskRunId}/resume`, { reason })
  },
  
  stop: (taskRunId: string, reason?: string) => {
    return post(`/api/task-run/${taskRunId}/stop`, { reason })
  },
  
  kill: (taskRunId: string, reason?: string) => {
    return post(`/api/task-run/${taskRunId}/kill`, { reason })
  },
  
  getLogFileInfo: (taskRunId: string) => {
    return get<LogFileInfo>(`/api/task-run/${taskRunId}/log/info`)
  },
  
  getLogs: (taskRunId: string) => {
    return get<string>(`/api/task-run/${taskRunId}/log`)
  },
  
  downloadLog: (taskRunId: string): Promise<Blob> => {
    return get<Blob>(`/api/task-run/${taskRunId}/log/download`, {}, {
      responseType: 'blob'
    })
  },
  
  delete: (taskRunId: string) => {
    return del(`/api/task-run/${taskRunId}`)
  },
}

export interface LogFileInfo {
  bucket: string
  objectName: string
  fileName: string
  signedUrl?: string
}
