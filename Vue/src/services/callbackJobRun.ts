import { get, del } from '@/utils/request'
import type { CallbackJobRun } from '@/types'

export interface CallbackJobRunDetail extends CallbackJobRun {
  requestId?: string
  requestPath?: string
  responsePath?: string
  responseTimeMs?: number
  retryIndex?: number
  errorMessage?: string
  taskName?: string
  configName?: string
}

export interface CallbackRequestData {
  headers: Record<string, string>
  body?: any
  method: string
  url: string
}

export interface CallbackResponseData {
  statusCode: number
  headers: Record<string, string>
  body?: any
  durationMs?: number
}

export const callbackJobRunApi = {
  list: (params?: any) => {
    return get<any>('/api/callback-job-runs', params)
  },

  getById: (id: string) => {
    return get<CallbackJobRunDetail>(`/api/callback-job-runs/${id}`)
  },

  getByTaskId: (taskId: string) => {
    return get<CallbackJobRunDetail[]>(`/api/callback-job-runs/task/${taskId}`)
  },

  getByConfigId: (configId: string) => {
    return get<CallbackJobRunDetail[]>(`/api/callback-job-runs/config/${configId}`)
  },

  getRequestData: (id: string) => {
    return get<CallbackRequestData>(`/api/callback-job-runs/${id}/request`)
  },

  getResponseData: (id: string) => {
    return get<CallbackResponseData>(`/api/callback-job-runs/${id}/response`)
  },

  downloadRequest: (id: string): Promise<Blob> => {
    return get<Blob>(`/api/callback-job-runs/${id}/download/request`, {}, {
      responseType: 'blob'
    })
  },

  downloadResponse: (id: string): Promise<Blob> => {
    return get<Blob>(`/api/callback-job-runs/${id}/download/response`, {}, {
      responseType: 'blob'
    })
  },

  retry: (id: string) => {
    return get(`/api/callback-job-runs/${id}/retry`)
  },

  delete: (id: string) => {
    return del(`/api/callback-job-runs/${id}`)
  }
}