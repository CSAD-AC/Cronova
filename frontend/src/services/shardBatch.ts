import { get } from '@/utils/request'

/** 分片批次日志聚合查看接口 */

export const shardBatchApi = {
  /** 按批次聚合查看日志（无状态游标协议，按时间稳定归并） */
  getShardLogs: (batchId: string, opts: { cursor?: string; limit?: number; level?: string; shard?: number } = {}) => {
    const params: Record<string, unknown> = {}
    if (opts.cursor) params.cursor = opts.cursor
    if (opts.limit) params.limit = opts.limit
    if (opts.level) params.level = opts.level
    if (opts.shard !== undefined) params.shard = opts.shard
    return get<ShardLogAggregateResult>(`/api/shard-batches/${batchId}/logs`, params)
  },

  /** 分片批次信息（片列表 + 状态） */
  getShardInfo: (batchId: string) => {
    return get<ShardBatchInfo>(`/api/shard-batches/${batchId}/logs/info`)
  },
}

/** 聚合结果（一条有序条目，可含多行续行） */
export interface ShardLogEntry {
  ts: string
  shard: number
  level: string
  lines: string[]
}

/** 单片读取进度 */
export interface ShardStatus {
  index: number
  state: string
  offset: number
  size: number
}

export interface ShardLogAggregateResult {
  cursor: string
  entries: ShardLogEntry[]
  shardStatus: ShardStatus[]
  settled: boolean
}

export interface ShardInfo {
  index: number
  runId: string
  state: string
  durationMs: number
  logPath: string
}

export interface ShardBatchInfo {
  shards: ShardInfo[]
  shardTotal: number
  taskId: string
  settled: boolean
}
