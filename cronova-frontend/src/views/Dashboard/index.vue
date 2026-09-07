<template>
  <div class="dashboard-container">
    <PageHeader title="仪表盘" subtitle="实时监控任务调度系统的运行状态" />

    <!-- ============ 统计卡：运行中带脉搏角标（签名元素） ============ -->
    <div class="stat-grid">
      <StatCard title="运行中任务" :value="stats.runningTasks" :icon="List" tone="running" :pulse="stats.runningTasks > 0" />
      <StatCard title="今日执行次数" :value="stats.todayExecutions" :icon="CircleCheck" tone="success" />
      <StatCard title="在线执行器" :value="`${stats.onlineWorkers}/${stats.totalWorkers}`" :icon="Cpu" tone="accent" :pulse="stats.onlineWorkers > 0" />
      <StatCard title="执行成功率" :value="stats.successRate" unit="%" :icon="TrendCharts" tone="primary" />
      <StatCard title="触发延迟 P99" :value="formatLatency(metrics.latency.p99Ms)" :icon="Timer" tone="pending" />
      <StatCard title="调度实例在线" :value="metrics.schedulerOnline" :icon="Connection" tone="accent" :pulse="metrics.schedulerOnline > 0" />
    </div>

    <div class="panel-grid">
      <!-- ============ 最近任务执行 ============ -->
      <div class="panel recent-panel">
        <div class="panel-header">
          <span class="panel-title">最近任务执行</span>
          <el-button text type="primary" @click="$router.push('/task-runs')">
            查看更多
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <el-table :data="recentTasks" style="width: 100%" stripe>
          <el-table-column prop="name" label="任务名称" min-width="150">
            <template #default="{ row }">
              <el-link type="primary" @click="viewRunDetail(row)">
                {{ row.name }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="100">
            <template #default="{ row }">
              <span class="type-text">{{ row.type }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <StatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="startTime" label="执行时间" width="180">
            <template #default="{ row }">
              <span class="sjdt-mono time-text">{{ formatDateTime(row.startTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="耗时" width="100">
            <template #default="{ row }">
              <span class="sjdt-mono time-text">{{ formatDuration(row.duration) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- ============ 执行器状态 ============ -->
      <div class="panel worker-panel">
        <div class="panel-header">
          <span class="panel-title">执行器状态</span>
        </div>
        <div class="worker-stats">
          <div class="worker-stat-item">
            <div class="worker-stat-value st-online sjdt-mono">{{ workerStore.onlineCount }}</div>
            <div class="worker-stat-label">
              <span class="mini-dot online" />在线
            </div>
          </div>
          <div class="worker-stat-item">
            <div class="worker-stat-value st-offline sjdt-mono">{{ workerStore.offlineCount }}</div>
            <div class="worker-stat-label">
              <span class="mini-dot offline" />离线
            </div>
          </div>
        </div>
        <div class="divider" />
        <div class="worker-list">
          <div
            v-for="worker in (workerStore.workers || []).slice(0, 5)"
            :key="worker.id"
            class="worker-item"
            @click="goToWorkerDetail(worker.id)"
          >
            <div class="worker-info">
              <span class="status-dot" :class="worker.status === 'online' ? 'dot-online' : 'dot-offline'" />
              <span class="worker-name">{{ worker.host }}</span>
            </div>
            <div class="worker-resource sjdt-mono">
              <span>CPU {{ worker.cpuUsage?.toFixed(1) || '-' }}%</span>
              <span>MEM {{ worker.memUsage?.toFixed(1) || '-' }}%</span>
            </div>
          </div>
          <el-empty
            v-if="!(workerStore.workers || []).length"
            description="暂无执行器"
            :image-size="60"
          />
        </div>
      </div>
    </div>

    <div class="panel-grid bottom-grid">
      <!-- ============ 任务类型分布：环形图 ============ -->
      <div class="panel">
        <div class="panel-header">
          <span class="panel-title">任务类型分布</span>
        </div>
        <div class="type-chart">
          <div class="ring-wrap">
            <div class="ring" :style="ringStyle" />
            <div class="ring-center">
              <div class="ring-total sjdt-mono">{{ typeTotal }}</div>
              <div class="ring-total-label">总次数</div>
            </div>
          </div>
          <div class="legend">
            <div v-for="(item, index) in taskTypeDistribution" :key="item.type" class="legend-item">
              <span class="legend-dot" :style="{ background: ringColor(index) }" />
              <span class="legend-label">{{ item.label }}</span>
              <span class="legend-value sjdt-mono">{{ item.percentage.toFixed(1) }}% · {{ item.count }}</span>
            </div>
            <div v-if="!taskTypeDistribution.length" class="legend-empty">暂无数据</div>
          </div>
        </div>
      </div>

      <!-- ============ 调度链路指标：延迟/派发/路由分布/分片批次 ============ -->
      <div class="panel">
        <div class="panel-header">
          <span class="panel-title">调度链路指标</span>
          <el-select v-model="windowMinutes" size="small" style="width: 96px" @change="fetchMetrics">
            <el-option label="近15分钟" :value="15" />
            <el-option label="近1小时" :value="60" />
            <el-option label="近6小时" :value="360" />
            <el-option label="近24小时" :value="1440" />
          </el-select>
        </div>

        <!-- 触发延迟分位（负值=扫描窗口内提前派发） -->
        <div class="metric-row">
          <div class="metric-item">
            <div class="metric-value sjdt-mono">{{ formatLatency(metrics.latency.p50Ms) }}</div>
            <div class="metric-label">P50 延迟</div>
          </div>
          <div class="metric-item">
            <div class="metric-value sjdt-mono">{{ formatLatency(metrics.latency.p95Ms) }}</div>
            <div class="metric-label">P95 延迟</div>
          </div>
          <div class="metric-item">
            <div class="metric-value sjdt-mono">{{ formatLatency(metrics.latency.p99Ms) }}</div>
            <div class="metric-label">P99 延迟</div>
          </div>
          <div class="metric-item">
            <div class="metric-value sjdt-mono">{{ formatLatency(metrics.latency.avgMs) }}</div>
            <div class="metric-label">平均延迟</div>
          </div>
          <div class="metric-item">
            <div class="metric-value sjdt-mono">{{ metrics.latency.count }}</div>
            <div class="metric-label">样本数</div>
          </div>
        </div>

        <!-- 派发与状态计数 -->
        <div class="metric-row">
          <div class="metric-item">
            <div class="metric-value st-online sjdt-mono">{{ metrics.dispatch.dispatched }}</div>
            <div class="metric-label">已派发</div>
          </div>
          <div class="metric-item">
            <div class="metric-value sjdt-mono">{{ metrics.dispatch.pendingNotDispatched }}</div>
            <div class="metric-label">待派发</div>
          </div>
          <div class="metric-item">
            <div class="metric-value st-offline sjdt-mono">{{ metrics.dispatch.parkTimeout }}</div>
            <div class="metric-label">park 超时</div>
          </div>
          <div class="metric-item">
            <div class="metric-value sjdt-mono">{{ metrics.statusCounts.timeout || 0 }}</div>
            <div class="metric-label">执行超时</div>
          </div>
          <div class="metric-item">
            <div class="metric-value sjdt-mono">{{ (metrics.statusCounts.skipped || 0) + (metrics.statusCounts.queued || 0) }}</div>
            <div class="metric-label">跳过/排队</div>
          </div>
        </div>

        <div class="divider" />

        <!-- 路由策略命中分布 -->
        <div class="section-label">路由策略分布（首轮 {{ metrics.dispatch.totalRuns }} 次）</div>
        <div class="route-list">
          <div v-for="(count, strategy) in metrics.routeDistribution" :key="strategy" class="route-item">
            <div class="route-bar-wrap">
              <div class="route-bar" :style="{ width: routePercent(count) + '%' }" />
            </div>
            <span class="route-name">{{ strategy }}</span>
            <span class="route-value sjdt-mono">{{ count }}</span>
          </div>
          <div v-if="!Object.keys(metrics.routeDistribution).length" class="legend-empty">暂无数据</div>
        </div>

        <div class="divider" />

        <!-- 最近分片批次 -->
        <div class="section-label">最近分片批次</div>
        <div class="batch-list">
          <div v-for="batch in metrics.recentShardBatches" :key="batch.batchId" class="batch-item">
            <span class="batch-id sjdt-mono" :title="batch.batchId">{{ batch.batchId.slice(0, 8) }}</span>
            <span class="batch-shards sjdt-mono">{{ batch.shardTotal }} 片</span>
            <span class="batch-result sjdt-mono">
              <span class="st-online">{{ batch.successCount }}</span> / <span class="st-offline">{{ batch.failedCount }}</span>
            </span>
          </div>
          <div v-if="!metrics.recentShardBatches.length" class="legend-empty">暂无分片任务</div>
        </div>
      </div>

      <!-- ============ 快速操作 ============ -->
      <div class="panel">
        <div class="panel-header">
          <span class="panel-title">快速操作</span>
        </div>
        <div class="quick-actions">
          <div class="quick-action" @click="$router.push('/tasks/create')">
            <el-icon :size="22"><Plus /></el-icon>
            <span>创建任务</span>
          </div>
          <div class="quick-action" @click="$router.push('/workers')">
            <el-icon :size="22"><Cpu /></el-icon>
            <span>查看执行器</span>
          </div>
          <div class="quick-action" @click="$router.push('/scripts')">
            <el-icon :size="22"><Upload /></el-icon>
            <span>上传脚本</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 任务执行详情弹窗 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="运行详情"
      width="800px"
      destroy-on-close
    >
      <el-descriptions :column="2" border v-if="currentRun">
        <el-descriptions-item label="执行记录ID" :span="2">
          <span class="sjdt-mono">{{ currentRun.id }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="任务名称">
          {{ currentRun.taskName }}
        </el-descriptions-item>
        <el-descriptions-item label="任务类型">
          {{ currentRun.type || currentRun.scriptType || 'Callback' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag :status="currentRun.status" />
        </el-descriptions-item>
        <el-descriptions-item label="退出码">
          <span class="sjdt-mono">{{ currentRun.exitCode !== undefined && currentRun.exitCode !== null ? currentRun.exitCode : '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="执行耗时">
          <span class="sjdt-mono">{{ formatDuration(currentRun.duration) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">
          <span class="sjdt-mono">{{ formatDateTime(currentRun.startTime) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="结束时间">
          <span class="sjdt-mono">{{ formatDateTime(currentRun.endTime) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          <span class="sjdt-mono">{{ formatDateTime(currentRun.createTime) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2">
          <div v-if="currentRun.errorMessage" class="error-message">
            {{ currentRun.errorMessage }}
          </div>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  List, CircleCheck, Cpu, TrendCharts, ArrowRight, Plus, Upload, Timer, Connection
} from '@element-plus/icons-vue'
import { useWorkerStore } from '@/stores/worker'
import { dashboardApi } from '@/services'
import type { SchedulerMetrics } from '@/services/dashboard'
import { formatDateTime, formatDuration } from '@/utils/date'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'

const router = useRouter()
const workerStore = useWorkerStore()

/** 顶部统计指标（数据来自 dashboardApi.getStats） */
const stats = reactive({
  runningTasks: 0,
  todayExecutions: 0,
  totalWorkers: 0,
  onlineWorkers: 0,
  successRate: 0,
})

/** 最近任务执行列表 */
const recentTasks = ref<any[]>([])

/** 任务类型分布数据 */
const taskTypeDistribution = ref<{ type: string; label: string; count: number; percentage: number }[]>([])

/** 调度链路指标（数据来自 dashboardApi.getMetrics，30s 轮询刷新） */
const metrics = reactive<SchedulerMetrics>({
  windowMinutes: 60,
  latency: { count: 0, p50Ms: 0, p95Ms: 0, p99Ms: 0, avgMs: 0, maxMs: 0 },
  dispatch: { totalRuns: 0, dispatched: 0, pendingNotDispatched: 0, parkTimeout: 0 },
  routeDistribution: {},
  workerDistribution: {},
  statusCounts: {},
  schedulerOnline: 0,
  recentShardBatches: [],
})

/** 调度指标统计窗口（分钟） */
const windowMinutes = ref(60)

/** 指标轮询定时器句柄 */
let metricsTimer: ReturnType<typeof setInterval> | null = null

/** 运行详情弹窗可见性 */
const detailDialogVisible = ref(false)

/** 当前查看的运行详情 */
const currentRun = ref<any>(null)

/** 环形图色板（按序取色，循环兜底） */
const RING_COLORS = [
  'var(--sjdt-primary)',
  'var(--sjdt-success)',
  'var(--sjdt-running)',
  'var(--sjdt-accent)',
  'var(--sjdt-pending)',
]

/**
 * 取环形图第 index 段的颜色
 * @param index 分段下标
 */
function ringColor(index: number): string {
  return RING_COLORS[index % RING_COLORS.length] || 'var(--sjdt-primary)'
}

/** 任务类型分布总次数 */
const typeTotal = computed(() =>
  taskTypeDistribution.value.reduce((sum, item) => sum + item.count, 0),
)

/** 环形图的 conic-gradient 样式（由分布数据聚合计算） */
const ringStyle = computed(() => {
  const items = taskTypeDistribution.value
  const total = typeTotal.value
  if (!total) {
    return { background: 'var(--sjdt-row-stripe)' }
  }
  let acc = 0
  const stops = items.map((item, idx) => {
    const start = (acc / total) * 360
    acc += item.count
    const end = (acc / total) * 360
    return `${ringColor(idx)} ${start}deg ${end}deg`
  })
  return { background: `conic-gradient(${stops.join(', ')})` }
})

/**
 * 拉取调度链路指标：触发延迟分位、派发情况、路由分布、调度实例、分片批次
 * 失败仅打印日志（指标面板展示上一次快照，不阻塞主统计）
 */
async function fetchMetrics(): Promise<void> {
  try {
    const data = await dashboardApi.getMetrics(windowMinutes.value)
    Object.assign(metrics, data)
  } catch (error) {
    console.error('获取调度指标失败:', error)
  }
}

/**
 * 格式化触发延迟读数：绝对值 <1s 显示毫秒，否则显示秒（保留1位小数）。
 * 负值代表扫描窗口内提前派发（调度器特性），原样保留符号展示。
 * @param ms 延迟毫秒数
 */
function formatLatency(ms: number): string {
  const abs = Math.abs(ms)
  if (abs < 1000) {
    return `${Math.round(ms)}ms`
  }
  return `${(ms / 1000).toFixed(1)}s`
}

/**
 * 计算路由策略占比（用于分布条宽度）。
 * @param count 该策略命中的首轮 run 数
 */
function routePercent(count: number): number {
  const total = metrics.dispatch.totalRuns
  if (!total) {
    return 0
  }
  return Math.min(100, Math.round((count / total) * 100))
}

/**
 * 拉取仪表盘统计数据：统计卡、最近执行、类型分布
 */
async function fetchDashboardData(): Promise<void> {
  try {
    const data = await dashboardApi.getStats()
    stats.runningTasks = data.stats.runningTasks || 0
    stats.todayExecutions = data.stats.todayExecutions || 0
    stats.onlineWorkers = data.stats.activeWorkers || 0
    stats.totalWorkers = data.stats.totalWorkers || 0

    if (data.stats.totalRuns > 0) {
      stats.successRate = Number(((data.stats.successRuns / data.stats.totalRuns) * 100).toFixed(1))
    } else {
      stats.successRate = 0
    }

    recentTasks.value = data.recentTasks.map(t => {
      // 根据executeType正确显示任务类型
      let typeDisplay = 'Callback'
      if (t.executeType === 'script' && t.scriptType) {
        typeDisplay = t.scriptType
      }

      return {
        id: t.id,
        name: t.taskName,
        type: typeDisplay,
        status: t.status || 'pending',
        startTime: t.createTime,
        duration: t.duration || 0
      }
    })

    // 设置任务类型分布数据
    taskTypeDistribution.value = data.taskTypeDistribution || []
  } catch (error) {
    console.error('获取仪表盘数据失败:', error)
  }
}

/**
 * 查看运行详情（打开弹窗）
 * @param row 行数据
 */
function viewRunDetail(row: any): void {
  currentRun.value = row
  detailDialogVisible.value = true
}

/**
 * 跳转到执行器详情页
 * @param workerId 执行器ID
 */
function goToWorkerDetail(workerId: string): void {
  router.push(`/workers/${workerId}`)
}

onMounted(() => {
  workerStore.fetchWorkers({ pageSize: 100 }).catch(() => {})
  fetchDashboardData()
  // 调度链路指标 30s 轮询（压测/运维观察期间数字持续刷新）
  fetchMetrics()
  metricsTimer = setInterval(fetchMetrics, 30_000)
})

onUnmounted(() => {
  if (metricsTimer) {
    clearInterval(metricsTimer)
    metricsTimer = null
  }
})
</script>

<style scoped>
.dashboard-container {
  max-width: 1440px;
  margin: 0 auto;
}

/* ---------- 统计卡网格 ---------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 992px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 576px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
}

/* ---------- 面板网格 ---------- */
.panel-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}

.bottom-grid {
  grid-template-columns: 1fr 1.2fr 1fr;
  margin-top: 16px;
}

@media (max-width: 992px) {
  .panel-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
  }
}

.panel {
  border-radius: 12px;
  border: 1px solid var(--sjdt-border);
  background: var(--sjdt-surface);
  box-shadow: var(--sjdt-card-shadow);
  padding: 18px 20px;
  min-width: 0;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--sjdt-text);
}

.type-text {
  font-size: 13px;
  color: var(--sjdt-muted);
}

.time-text {
  font-size: 13px;
  color: var(--sjdt-text);
}

/* ---------- 执行器状态面板 ---------- */
.worker-stats {
  display: flex;
  gap: 40px;
  padding: 8px 0 4px;
}

.worker-stat-item {
  text-align: center;
}

.worker-stat-value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
}

.st-online { color: var(--sjdt-success); }
.st-offline { color: var(--sjdt-danger); }

.worker-stat-label {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  color: var(--sjdt-muted);
  margin-top: 4px;
}

.mini-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.mini-dot.online {
  background: var(--sjdt-success);
  animation: sjdt-pulse-success 2s infinite;
}

.mini-dot.offline {
  background: var(--sjdt-danger);
}

.divider {
  height: 1px;
  background: var(--sjdt-border);
  margin: 12px 0;
}

.worker-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 11px 4px;
  border-radius: 8px;
  cursor: pointer;
  transition: background .18s ease;
}

.worker-item:hover {
  background: var(--sjdt-primary-soft);
}

.worker-info {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.worker-name {
  font-size: 13px;
  color: var(--sjdt-text);
  font-family: var(--sjdt-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-online {
  background: var(--sjdt-success);
  animation: sjdt-pulse-success 2s infinite;
}

.dot-offline {
  background: var(--sjdt-danger);
}

.worker-resource {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--sjdt-muted);
  flex-shrink: 0;
}

/* ---------- 调度链路指标面板 ---------- */
.metric-row {
  display: flex;
  gap: 8px;
  padding: 6px 0;
}

.metric-item {
  flex: 1;
  text-align: center;
  min-width: 0;
}

.metric-value {
  font-size: 17px;
  font-weight: 700;
  color: var(--sjdt-text);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metric-label {
  font-size: 11px;
  color: var(--sjdt-muted);
  margin-top: 2px;
}

.section-label {
  font-size: 12px;
  color: var(--sjdt-muted);
  margin-bottom: 8px;
}

.route-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.route-item {
  display: grid;
  grid-template-columns: 1fr 110px 40px;
  gap: 10px;
  align-items: center;
}

.route-bar-wrap {
  height: 8px;
  border-radius: 4px;
  background: var(--sjdt-row-stripe);
  overflow: hidden;
}

.route-bar {
  height: 100%;
  border-radius: 4px;
  background: var(--sjdt-primary);
  min-width: 2px;
  transition: width .3s ease;
}

.route-name {
  font-size: 12px;
  color: var(--sjdt-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-value {
  font-size: 12px;
  color: var(--sjdt-muted);
  text-align: right;
}

.batch-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 130px;
  overflow-y: auto;
}

.batch-item {
  display: grid;
  grid-template-columns: 1fr 60px 70px;
  gap: 10px;
  align-items: center;
  font-size: 12px;
}

.batch-id {
  color: var(--sjdt-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-shards {
  color: var(--sjdt-muted);
}

.batch-result {
  color: var(--sjdt-muted);
  text-align: right;
}

/* ---------- 环形图 ---------- */
.type-chart {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 12px 4px;
}

@media (max-width: 768px) {
  .type-chart {
    flex-direction: column;
  }
}

.ring-wrap {
  position: relative;
  width: 180px;
  height: 180px;
  flex-shrink: 0;
}

.ring {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  position: relative;
}

/* 中心镂空（显示总数） */
.ring::after {
  content: '';
  position: absolute;
  inset: 26px;
  border-radius: 50%;
  background: var(--sjdt-surface);
}

.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.ring-total {
  font-size: 26px;
  font-weight: 700;
  color: var(--sjdt-text);
}

.ring-total-label {
  font-size: 11px;
  color: var(--sjdt-muted);
  margin-top: 2px;
}

.legend {
  flex: 1;
  min-width: 0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.legend-label {
  font-size: 13px;
  color: var(--sjdt-text);
  flex: 1;
}

.legend-value {
  font-size: 13px;
  color: var(--sjdt-muted);
}

.legend-empty {
  font-size: 13px;
  color: var(--sjdt-muted);
  text-align: center;
  padding: 20px 0;
}

/* ---------- 快速操作 ---------- */
.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 12px 0;
}

@media (max-width: 576px) {
  .quick-actions {
    grid-template-columns: 1fr;
  }
}

.quick-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 24px 12px;
  border-radius: 10px;
  border: 1px solid var(--sjdt-border);
  color: var(--sjdt-muted);
  cursor: pointer;
  transition: all .18s ease;
  font-size: 13px;
}

.quick-action:hover {
  color: var(--sjdt-primary);
  border-color: var(--sjdt-primary);
  background: var(--sjdt-primary-soft);
  transform: translateY(-2px);
}

/* ---------- 错误信息 ---------- */
.error-message {
  background-color: var(--sjdt-danger-soft);
  border: 1px solid var(--sjdt-danger);
  border-radius: 6px;
  padding: 8px 12px;
  color: var(--sjdt-danger);
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}
</style>
