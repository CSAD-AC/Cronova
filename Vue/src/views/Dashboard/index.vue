<template>
  <div class="dashboard-container">
    <div class="page-header">
      <h1 class="page-title">系统概览</h1>
      <p class="page-subtitle">实时监控任务调度系统的运行状态</p>
    </div>
    
    <el-row :gutter="24" class="stat-cards">
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card stat-primary">
          <div class="stat-icon">
            <el-icon><List /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.runningTasks }}</div>
            <div class="stat-label">运行中任务</div>
          </div>
        </div>
      </el-col>
      
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card stat-success">
          <div class="stat-icon">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.todayExecutions }}</div>
            <div class="stat-label">今日执行次数</div>
          </div>
        </div>
      </el-col>
      
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card stat-warning">
          <div class="stat-icon">
            <el-icon><Cpu /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.onlineWorkers }}/{{ stats.totalWorkers }}</div>
            <div class="stat-label">在线执行器</div>
          </div>
        </div>
      </el-col>
      
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card stat-info">
          <div class="stat-icon">
            <el-icon><TrendCharts /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.successRate }}%</div>
            <div class="stat-label">执行成功率</div>
          </div>
        </div>
      </el-col>
    </el-row>
    
    <el-row :gutter="24">
      <el-col :xs="24" :lg="16">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>最近任务执行</span>
              <el-button text type="primary" @click="$router.push('/task-runs')">
                查看更多
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>
          <el-table :data="recentTasks" style="width: 100%">
            <el-table-column prop="name" label="任务名称" min-width="150">
              <template #default="{ row }">
                <el-link type="primary" @click="viewRunDetail(row)">
                  {{ row.name }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" label="执行时间" width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.startTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="duration" label="耗时" width="100">
              <template #default="{ row }">
                {{ formatDuration(row.duration) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      
      <el-col :xs="24" :lg="8">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>Worker状态</span>
            </div>
          </template>
          <div class="worker-stats">
            <div class="worker-stat-item">
              <div class="worker-stat-label">在线</div>
              <div class="worker-stat-value text-success">
                {{ workerStore.onlineCount }}
              </div>
            </div>
            <div class="worker-stat-item">
              <div class="worker-stat-label">离线</div>
              <div class="worker-stat-value text-danger">
                {{ workerStore.offlineCount }}
              </div>
            </div>
          </div>
          <el-divider />
          <div class="worker-list">
            <div 
              v-for="worker in (workerStore.workers || []).slice(0, 5)" 
              :key="worker.id"
              class="worker-item"
              @click="goToWorkerDetail(worker.id)"
              style="cursor: pointer;"
            >
              <div class="worker-info">
                <el-icon :class="worker.status === 'online' ? 'text-success' : 'text-danger'">
                  <Check />
                </el-icon>
                <span class="worker-name">{{ worker.host }}</span>
              </div>
              <div class="worker-resource">
                <span>CPU: {{ worker.cpuUsage?.toFixed(1) || '-' }}%</span>
                <span>MEM: {{ worker.memUsage?.toFixed(1) || '-' }}%</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="24" style="margin-top: 24px;">
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>任务类型分布</span>
            </div>
          </template>
          <div class="task-type-chart">
            <div 
              v-for="(item, index) in taskTypeDistribution" 
              :key="item.type"
              class="chart-item"
            >
              <div class="chart-row">
                <span class="chart-label">{{ item.label }}</span>
                <div class="chart-bar-wrapper">
                  <div 
                    class="chart-bar" 
                    :class="index === 0 ? '' : 'chart-bar-secondary'"
                    :style="{ width: Math.max(item.percentage, 5) + '%' }"
                  ></div>
                </div>
                <span class="chart-value">{{ item.percentage.toFixed(2) }}% ({{ item.count }})</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>快速操作</span>
            </div>
          </template>
          <div class="quick-actions">
            <el-button type="primary" @click="$router.push('/tasks/create')">
              <el-icon><Plus /></el-icon>
              创建任务
            </el-button>
            <el-button @click="$router.push('/workers')">
              <el-icon><Cpu /></el-icon>
              查看执行器
            </el-button>
            <el-button @click="$router.push('/scripts')">
              <el-icon><Upload /></el-icon>
              上传脚本
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 任务执行详情弹窗 -->
    <el-dialog 
      v-model="detailDialogVisible" 
      title="运行详情" 
      width="800px"
      destroy-on-close
    >
      <el-descriptions :column="2" border v-if="currentRun">
        <el-descriptions-item label="执行记录ID" :span="2">
          {{ currentRun.id }}
        </el-descriptions-item>
        <el-descriptions-item label="任务名称">
          {{ currentRun.taskName }}
        </el-descriptions-item>
        <el-descriptions-item label="任务类型">
          <el-tag size="small">{{ currentRun.type || currentRun.scriptType || 'Callback' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentRun.status)" size="small">
            {{ getStatusText(currentRun.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="退出码">
          {{ currentRun.exitCode !== undefined && currentRun.exitCode !== null ? currentRun.exitCode : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="执行耗时">
          {{ formatDuration(currentRun.duration) }}
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ formatDateTime(currentRun.startTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="结束时间">
          {{ formatDateTime(currentRun.endTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatDateTime(currentRun.createTime) }}
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  List, CircleCheck, Cpu, TrendCharts, ArrowRight, Plus, Upload, Check
} from '@element-plus/icons-vue'
import { useWorkerStore } from '@/stores/worker'
import { dashboardApi } from '@/services'
import { formatDateTime, formatDuration } from '@/utils/date'

const router = useRouter()
const workerStore = useWorkerStore()

const stats = reactive({
  runningTasks: 0,
  todayExecutions: 0,
  totalWorkers: 0,
  onlineWorkers: 0,
  successRate: 0,
})

const recentTasks = ref<any[]>([])
const taskTypeDistribution = ref<{ type: string; label: string; count: number; percentage: number }[]>([])
const detailDialogVisible = ref(false)
const currentRun = ref<any>(null)

const fetchDashboardData = async () => {
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

onMounted(async () => {
  await workerStore.fetchWorkers({ pageSize: 100 })
  fetchDashboardData()
})

function getStatusType(status: string) {
  const map: Record<string, string> = {
    success: 'success',
    failed: 'danger',
    running: 'primary',
    pending: 'info',
  }
  return map[status] || 'info'
}

function getStatusText(status: string) {
  const map: Record<string, string> = {
    success: '成功',
    failed: '失败',
    running: '运行中',
    pending: '等待',
    timeout: '超时',
  }
  return map[status] || status
}

function viewRunDetail(row: any) {
  currentRun.value = row
  detailDialogVisible.value = true
}

function goToWorkerDetail(workerId: string) {
  router.push(`/workers/${workerId}`)
}

onMounted(() => {
  workerStore.fetchWorkers({ pageSize: 100 })
})
</script>

<style scoped>
.dashboard-container {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 8px;
}

.page-subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.stat-cards {
  margin-bottom: 24px;
}

.stat-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-primary .stat-icon {
  background: #dbeafe;
  color: #3b82f6;
}

.stat-success .stat-icon {
  background: #dcfce7;
  color: #10b981;
}

.stat-warning .stat-icon {
  background: #fef3c7;
  color: #f59e0b;
}

.stat-info .stat-icon {
  background: #f1f5f9;
  color: #64748b;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #64748b;
  margin-top: 4px;
}

.chart-card {
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.worker-stats {
  display: flex;
  gap: 32px;
  padding: 16px 0;
}

.worker-stat-item {
  text-align: center;
}

.worker-stat-label {
  font-size: 14px;
  color: #64748b;
}

.worker-stat-value {
  font-size: 36px;
  font-weight: 700;
}

.text-success {
  color: #10b981;
}

.text-danger {
  color: #ef4444;
}

.worker-list {
  margin-top: 16px;
}

.worker-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
}

.worker-item:last-child {
  border-bottom: none;
}

.worker-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.worker-name {
  font-size: 14px;
  color: #1e293b;
}

.worker-resource {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #64748b;
}

.task-type-chart {
  padding: 16px 0;
}

.chart-item {
  margin-bottom: 20px;
}

.chart-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chart-bar-wrapper {
  flex: 1;
  height: 40px;
  background: #f1f5f9;
  border-radius: 8px;
  overflow: hidden;
}

.chart-bar {
  height: 100%;
  min-width: 40px;
  background: linear-gradient(90deg, #3b82f6, #60a5fa);
  border-radius: 8px;
  transition: width 0.3s ease;
}

.chart-bar-secondary {
  background: linear-gradient(90deg, #10b981, #34d399);
}

.chart-label {
  font-size: 14px;
  color: #1e293b;
  min-width: 100px;
  flex-shrink: 0;
}

.chart-value {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  min-width: 100px;
  text-align: right;
  flex-shrink: 0;
}

.quick-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.quick-actions .el-button {
  flex: 1;
  min-width: 120px;
}

.error-message {
  background-color: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 4px;
  padding: 8px 12px;
  color: #dc2626;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}
</style>
