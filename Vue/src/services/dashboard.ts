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

export const dashboardApi = {
  getStats: () => {
    return get<DashboardStats>('/api/dashboard/stats')
  }
}
