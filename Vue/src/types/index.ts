export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token?: string
  user?: UserVO
  data?: {
    token?: string
    user?: UserVO
  }
}

export interface User {
  id: string
  username: string
  password?: string
  nickname: string
  email?: string
  phone?: string
  avatar?: string
  status: number
  createTime?: string
  updateTime?: string
}

export interface UserVO {
  id: string
  username: string
  nickname: string
  email?: string
  phone?: string
  avatar?: string
  status: number
  createTime?: string
}

export interface Role {
  id: string
  name: string
  code: string
  description?: string
  createTime?: string
  updateTime?: string
}

export interface Tag {
  id: number | string
  name: string
  description?: string
  createTime?: string
  updateTime?: string
}

export interface TagVO {
  id: number | string
  name: string
  description?: string
  createTime?: string
  updateTime?: string
  scriptCount?: number
  workerCount?: number
}

export interface Script {
  id: string
  name: string
  path: string
  createTime?: string
  updateTime?: string
}

export interface ScriptVO {
  id: string
  name: string
  path: string
  isDirectory?: boolean
  mainFile?: string
  tags?: Tag[]
  createTime?: string
  updateTime?: string
}

export type ScriptType = 'python' | 'java' | 'shell' | 'go'

export type TaskType = 'once' | 'cron'

export type ExecuteType = 'script' | 'callback'

export type TaskStatus = 'active' | 'paused' | 'deleted'

export interface Task {
  id?: string
  name: string
  description?: string
  type?: ScriptType
  scriptId?: string
  taskType: TaskType
  cronExpr?: string
  enabled?: boolean
  timeoutSeconds?: number
  targetWorker?: string
  status?: TaskStatus
  executeType?: ExecuteType
  createdBy?: string
  createTime?: string
  updateTime?: string
}

export interface TaskVO {
  id: string
  name: string
  description?: string
  type?: ScriptType
  scriptId?: string
  scriptName?: string
  taskType: TaskType
  cronExpr?: string
  cronDescription?: string  // Cron表达式的中文含义
  timeoutSeconds?: number
  retryCount?: number
  retryIntervalSeconds?: number
  targetWorker?: string
  status: TaskStatus
  executeType?: ExecuteType
  duration?: number  // 执行耗时（毫秒）
  createdBy?: string
  createTime?: string
  updateTime?: string
  callbackConfig?: any  // 回调配置信息
}

export interface TaskCreateDTO {
  name: string
  description?: string
  executeType: ExecuteType
  scriptType?: ScriptType
  scriptId?: string
  taskType: TaskType
  cronExpr?: string
  status?: TaskStatus
  timeoutSeconds?: number
  targetWorker?: string
}

export interface TaskUpdateDTO {
  id: string
  name: string
  description?: string
  executeType: ExecuteType
  scriptType?: ScriptType
  scriptId?: string
  taskType: TaskType
  cronExpr?: string
  status?: TaskStatus
  timeoutSeconds?: number
  targetWorker?: string
}

export interface TaskRun {
  id: string
  taskId: string
  workerId?: string
  status: TaskRunStatus
  exitCode?: number
  startTime?: string
  endTime?: string
  durationMs?: number
  errorMessage?: string
  createTime?: string
}

export type TaskRunStatus = 'pending' | 'running' | 'paused' | 'success' | 'failed' | 'timeout'

export interface Worker {
  id: string
  host: string
  ip: string
  status: WorkerStatus
  cpuUsage?: number
  memUsage?: number
  runningTasks?: number
  lastHeartbeat?: string
  accessKey?: string
  secretKey?: string
  tags?: Tag[]
  createTime?: string
  updateTime?: string
}

export type WorkerStatus = 'online' | 'offline'

export interface WorkerVO {
  id: string
  host: string
  ip: string
  status: WorkerStatus
  cpuUsage?: number
  memUsage?: number
  runningTasks?: number
  lastHeartbeat?: string
  tags?: Tag[]
  createTime?: string
}

export interface CallbackJobConfig {
  id: string
  taskId: string
  callbackUrl: string
  callbackMethod?: string
  secretKey?: string
  timeoutSeconds?: number
  retryCount?: number
  retryIntervalSeconds?: number
  createTime?: string
  updateTime?: string
}

export interface CallbackJobRun {
  id: string
  taskId: string
  configId: string
  status: string
  requestUrl?: string
  requestMethod?: string
  responseStatusCode?: number
  durationMs?: number
  startTime?: string
  endTime?: string
  createTime?: string
}

export interface HealthCheckResult {
  configId: string
  callbackUrl: string
  checkTime: string
  urlHealthy: boolean
  urlResponseCode?: number
  urlResponseTimeMs?: number
  message?: string
  endpoint?: string
  cronExpr?: string
  cronDescription?: string
  cronNextExecutionTime?: string
}

export interface CronDescriptionResult {
  cronExpr: string
  description: string
  nextExecutionTime?: string
}
