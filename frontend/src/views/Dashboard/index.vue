<template>
  <div class="dashboard-container">
    <PageHeader title="仪表盘" subtitle="实时监控任务调度系统的运行状态" />

    <!-- ============ 统计卡：运行中带脉搏角标（签名元素） ============ -->
    <div class="stat-grid">
      <StatCard title="运行中任务" :value="stats.runningTasks" :icon="List" tone="running" :pulse="stats.runningTasks > 0" />
      <StatCard title="今日执行次数" :value="stats.todayExecutions" :icon="CircleCheck" tone="success" />
      <StatCard title="在线执行器" :value="`${stats.onlineWorkers}/${stats.totalWorkers}`" :icon="Cpu" tone="accent" :pulse="stats.onlineWorkers > 0" />
      <StatCard title="执行成功率" :value="stats.successRate" unit="%" :icon="TrendCharts" tone="primary" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  List, CircleCheck, Cpu, TrendCharts, ArrowRight, Plus, Upload
} from '@element-plus/icons-vue'
import { useWorkerStore } from '@/stores/worker'
import { dashboardApi } from '@/services'
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
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
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
  grid-template-columns: 1fr 1fr;
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
