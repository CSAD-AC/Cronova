import { get } from '@/utils/request'

export interface TaskRunVO {
  id: string
  taskName: string
  executeType?: string
  scriptType?: string
  status: string | null
  exitCode?: number
  duration?: number
  startTime?: string
  endTime?: string
  createTime?: string
  errorMessage?: string
}

export interface DashboardStats {
  stats: {
    totalTasks: number          // 总任务数
    runningTasks: number       // 运行中任务数
    todayExecutions: number    // 今日执行次数
    totalRuns: number          // 总执行次数
    successRuns: number        // 成功执行次数
    failureRuns: number        // 失败执行次数
    activeWorkers: number      // 在线执行器数
    totalWorkers: number       // 总执行器数
    successRate: number       // 执行成功率
  }
  recentTasks: TaskRunVO[]
  chartData: {
    date: string
    success: number
    failure: number
  }[]
  taskTypeDistribution: {
    type: string
    label: string
    count: number
    percentage: number
  }[]
}

/** 调度可观测指标：触发延迟分布（窗口内首轮触发的 run） */
export interface MetricsLatency {
  count: number
  p50Ms: number
  p95Ms: number
  p99Ms: number
  avgMs: number
  maxMs: number
}

/** 调度可观测指标：派发情况汇总 */
export interface MetricsDispatch {
  totalRuns: number
  dispatched: number
  pendingNotDispatched: number
  parkTimeout: number
}

/** 调度可观测指标：最近分片批次汇总 */
export interface ShardBatchSummary {
  batchId: string
  taskId: string
  shardTotal: number
  finished: number
  successCount: number
  failedCount: number
  lastActiveTime?: string
}

/** 调度可观测指标（GET /api/dashboard/metrics 返回结构） */
export interface SchedulerMetrics {
  windowMinutes: number
  latency: MetricsLatency
  dispatch: MetricsDispatch
  routeDistribution: Record<string, number>
  workerDistribution: Record<string, number>
  statusCounts: Record<string, number>
  schedulerOnline: number
  recentShardBatches: ShardBatchSummary[]
}

export const dashboardApi = {
  /** 获取系统概览统计（卡片/最近执行/类型分布） */
  getStats: () => {
    return get<DashboardStats>('/api/dashboard/stats')
  },
  /** 获取调度可观测指标（触发延迟/派发/路由分布/调度实例/分片批次） */
  getMetrics: (minutes = 60) => {
    return get<SchedulerMetrics>('/api/dashboard/metrics', { minutes })
  }
}
