<template>
  <div class="task-run-list-container">
    <PageHeader title="任务运行记录" subtitle="查看系统中所有任务的执行记录">
      <el-button type="primary" @click="fetchTaskRuns" :loading="loading">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </PageHeader>

    <!-- ============ 统计卡 ============ -->
    <div class="stat-grid">
      <StatCard title="运行中" :value="statusCount('running')" :icon="VideoPlay" tone="running" :pulse="statusCount('running') > 0" />
      <StatCard title="执行成功" :value="statusCount('success')" :icon="CircleCheck" tone="success" />
      <StatCard title="执行失败" :value="failCount" :icon="CircleClose" tone="danger" />
      <StatCard title="记录总数" :value="taskRuns.length" :icon="Document" tone="primary" />
    </div>

    <FilterCard>
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="任务">
          <el-select v-model="filterForm.taskId" placeholder="选择任务" clearable filterable style="width: 200px">
            <el-option
              v-for="[id, name] in taskMap"
              :key="id"
              :label="name"
              :value="id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="等待中" value="pending" />
            <el-option label="运行中" value="running" />
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
            <el-option label="超时" value="timeout" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="filterForm.sortOrder" style="width: 120px">
            <el-option label="最新在前" value="desc" />
            <el-option label="最早在前" value="asc" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </FilterCard>

    <div class="table-panel">
      <el-table
        :data="filteredTaskRuns"
        v-loading="loading"
        style="width: 100%"
        stripe
      >
        <el-table-column type="index" label="序号" width="70" :index="indexMethod" />
        <el-table-column prop="taskId" label="任务" min-width="170">
          <template #default="{ row }">
            <el-link type="primary" @click="viewTaskDetail(row.taskId)">
              {{ getTaskName(row.taskId) }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="workerId" label="执行器" width="170">
          <template #default="{ row }">
            <el-link v-if="row.workerId" type="primary" @click="viewWorkerDetail(row.workerId)">
              {{ getWorkerName(row.workerId) }}
            </el-link>
            <span v-else class="empty-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="exitCode" label="退出码" width="80">
          <template #default="{ row }">
            <span v-if="row.exitCode !== undefined && row.exitCode !== null" class="cronova-mono exit-code" :class="row.exitCode === 0 ? 'code-success' : 'code-fail'">
              {{ row.exitCode }}
            </span>
            <span v-else class="empty-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时" width="100">
          <template #default="{ row }">
            <span v-if="row.durationMs" class="cronova-mono time-text">
              {{ formatDuration(row.durationMs) }}
            </span>
            <span v-else class="empty-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170">
          <template #default="{ row }">
            <span class="cronova-mono time-text">{{ formatDateTime(row.startTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button link type="primary" @click="viewDetail(row)">
                <el-icon class="mr-1"><View /></el-icon>详情
              </el-button>
              <el-button v-if="isScriptTask(row.taskId)" link type="primary" @click="previewLog(row)">
                <el-icon class="mr-1"><Document /></el-icon>日志
              </el-button>
              <el-button v-if="canPause(row)" link type="warning" @click="handlePause(row)">
                <el-icon class="mr-1"><VideoPause /></el-icon>暂停
              </el-button>
              <el-button v-if="canResume(row)" link type="success" @click="handleResume(row)">
                <el-icon class="mr-1"><VideoPlay /></el-icon>恢复
              </el-button>
              <el-button link type="danger" @click="handleDelete(row)">
                <el-icon class="mr-1"><Delete /></el-icon>删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="taskRuns.length === 0 && !loading" class="empty-tip">
        <el-empty description="暂无运行记录" :image-size="100" />
      </div>
    </div>
    
    <el-dialog v-model="detailDialogVisible" title="运行详情" width="900px" height="600px">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border v-if="currentRun">
            <el-descriptions-item label="序号">{{ currentRunIndex }}</el-descriptions-item>
            <el-descriptions-item label="任务">
              <el-link type="primary" @click="viewTaskDetail(currentRun.taskId)">
                {{ getTaskName(currentRun.taskId) }}
              </el-link>
            </el-descriptions-item>
            <template v-if="isScriptTask(currentRun.taskId)">
              <el-descriptions-item label="执行器">
                <el-link type="info" @click="viewWorkerDetail(currentRun.workerId)">
                  {{ getWorkerName(currentRun.workerId) }}
                </el-link>
              </el-descriptions-item>
            </template>
            <el-descriptions-item label="状态">
              <StatusTag :status="currentRun.status" />
            </el-descriptions-item>
            <el-descriptions-item label="退出码">
              {{ currentRun.exitCode !== undefined && currentRun.exitCode !== null ? currentRun.exitCode : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="执行耗时">
              {{ currentRun.durationMs ? formatDuration(currentRun.durationMs) : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatDateTime(currentRun.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ formatDateTime(currentRun.endTime) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(currentRun.createTime) }}</el-descriptions-item>
            <template v-if="isCallbackTask(currentRun.taskId)">
              <el-descriptions-item label="请求URL">{{ currentRun.requestUrl || '-' }}</el-descriptions-item>
              <el-descriptions-item label="请求方法">{{ currentRun.requestMethod || '-' }}</el-descriptions-item>
              <el-descriptions-item label="响应状态码">{{ currentRun.responseStatusCode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="响应时间">{{ currentRun.responseTimeMs ? currentRun.responseTimeMs + 'ms' : '-' }}</el-descriptions-item>
              <el-descriptions-item label="重试次数">{{ currentRun.retryIndex || 0 }}</el-descriptions-item>
            </template>
            <el-descriptions-item label="错误信息" :span="2">
              <div v-if="currentRun.errorMessage" class="error-message">
                {{ currentRun.errorMessage }}
              </div>
              <span v-else>-</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        
        <!-- 脚本任务的执行日志 -->
        <el-tab-pane v-if="isScriptTask(currentRun?.taskId)" label="执行日志" name="logs">
          <div class="log-section">
            <div class="log-header">
              <span class="log-title">执行日志</span>
              <div class="log-toolbar">
                <el-button size="small" @click="toggleFullscreen">
                  <el-icon><FullScreen /></el-icon>
                  {{ isFullscreen ? '退出全屏' : '全屏查看' }}
                </el-button>
                <el-dropdown>
                  <el-button size="small">
                    <el-icon><Filter /></el-icon>
                    日志筛选
                    <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-checkbox-group v-model="selectedLogLevels" @change="filterLogs">
                    <div class="log-level-filter">
                      <el-checkbox value="DEBUG">调试信息</el-checkbox>
                      <el-checkbox value="INFO">一般信息</el-checkbox>
                      <el-checkbox value="WARNING">警告信息</el-checkbox>
                      <el-checkbox value="ERROR">错误信息</el-checkbox>
                      <el-checkbox value="CRITICAL">严重错误</el-checkbox>
                    </div>
                  </el-checkbox-group>
                      <el-dropdown-item>
                        <el-button size="small" type="primary" @click="selectAllLevels">全选</el-button>
                        <el-button size="small" @click="clearAllLevels">清空</el-button>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-divider direction="vertical" />
                <el-button size="small" type="primary" @click="downloadCurrentLog" :loading="downloadingLog">
                  <el-icon><Download /></el-icon>
                  下载日志
                </el-button>
              </div>
            </div>
            <div class="log-content-wrapper">
              <div v-if="loadingLog" class="log-loading">
                <el-icon class="is-loading"><Loading /></el-icon>
                加载日志中...
              </div>
              <div v-else ref="logContainer" class="log-container" :class="{ 'fullscreen': isFullscreen }">
                <pre class="log-text" v-html="displayLogContent || currentLogContentHtml || '暂无日志内容'"></pre>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 分片任务的分片日志聚合视图 -->
        <el-tab-pane v-if="isShardTask(currentRun)" label="分片日志" name="shardLogs">
          <div class="shard-log-layout">
            <!-- 左侧片列表 -->
            <div class="shard-log-sidebar">
              <div v-if="shardInfoLoading" class="log-loading">加载片信息...</div>
              <div v-else>
                <div class="shard-side-title">片列表（{{ shardShards.length }}）</div>
                <div v-for="s in shardShards" :key="s.index"
                     class="shard-side-item" :class="{ active: selectedShardIndex === s.index }"
                     @click="toggleShardFilter(s.index)">
                  <el-tag size="small" :type="stateTagType(s.state)">片{{ s.index }}</el-tag>
                  <span class="shard-side-state">{{ s.state }}</span>
                  <span class="shard-side-dur">{{ s.durationMs ? formatDuration(s.durationMs) : '-' }}</span>
                </div>
              </div>
            </div>
            <!-- 右侧聚合日志流 -->
            <div class="shard-log-main">
              <div class="log-header">
                <span class="log-title">分片日志（按时间排序）</span>
                <div class="log-toolbar">
                  <el-select v-model="shardLevelFilter" placeholder="全部级别" size="small" clearable style="width:120px">
                    <el-option v-for="l in allLogLevels" :key="l" :label="l" :value="l" />
                  </el-select>
                  <el-button size="small" @click="resetShardCursor">刷新</el-button>
                  <el-switch v-model="shardFollow" active-text="Follow" size="small" />
                </div>
              </div>
              <div class="shard-log-body">
                <div v-if="shardLogLoading" class="log-loading">加载日志中...</div>
                <div v-else-if="!shardLogEntries.length" class="log-loading">暂无分片日志</div>
                <div v-else class="shard-log-stream">
                  <div v-for="(e, i) in shardLogEntries" :key="i" class="shard-line">
                    <span class="shard-tag" :style="{ color: shardColor(e.shard) }">片{{ e.shard }}</span>
                    <span class="shard-level" :style="{ color: levelColor(e.level) }">{{ e.level }}</span>
                    <span class="shard-ts">{{ e.ts }}</span>
                    <span class="shard-msg">{{ e.lines.join('\n') }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
        
        <!-- 回调任务的请求记录 -->
        <el-tab-pane v-if="isCallbackTask(currentRun?.taskId)" label="请求记录" name="request">
          <div class="callback-section">
            <div class="callback-header">
              <span class="callback-title">HTTP请求详情</span>
              <el-button size="small" type="primary" @click="downloadRequestData" :loading="downloadingRequest">
                <el-icon><Download /></el-icon>
                下载请求数据
              </el-button>
            </div>
            <div class="callback-content">
              <div v-if="loadingRequest" class="callback-loading">
                <el-icon class="is-loading"><Loading /></el-icon>
                加载请求数据中...
              </div>
              <el-input
                v-else
                type="textarea"
                :model-value="currentRequestContent"
                readonly
                :rows="18"
                class="callback-content-text"
              />
            </div>
          </div>
        </el-tab-pane>
        
        <!-- 回调任务的响应记录 -->
        <el-tab-pane v-if="isCallbackTask(currentRun?.taskId)" label="响应记录" name="response">
          <div class="callback-section">
            <div class="callback-header">
              <span class="callback-title">HTTP响应详情</span>
              <el-button size="small" type="primary" @click="downloadResponseData" :loading="downloadingResponse">
                <el-icon><Download /></el-icon>
                下载响应数据
              </el-button>
            </div>
            <div class="callback-content">
              <div v-if="loadingResponse" class="callback-loading">
                <el-icon class="is-loading"><Loading /></el-icon>
                加载响应数据中...
              </div>
              <el-input
                v-else
                type="textarea"
                :model-value="currentResponseContent"
                readonly
                :rows="18"
                class="callback-content-text"
              />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
    
    <el-dialog v-model="controlDialogVisible" :title="getControlDialogTitle()" width="500px">
      <div class="control-warning">
        <el-icon :size="20" class="warning-icon"><Warning /></el-icon>
        <span>{{ getControlWarningMessage() }}</span>
      </div>
      
      <div v-if="showReasonInput" class="control-reason">
        <el-form-item label="操作原因">
          <el-input 
            v-model="controlReason" 
            type="textarea"
            :rows="3"
            placeholder="请输入操作原因（可选）"
          />
        </el-form-item>
      </div>
      
      <div class="control-task-info">
        <el-descriptions :column="1" size="small">
          <el-descriptions-item label="任务ID">{{ controlTaskRun?.taskId }}</el-descriptions-item>
          <el-descriptions-item label="任务名称">{{ getTaskName(controlTaskRun?.taskId) }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <StatusTag :status="controlTaskRun?.status || 'pending'" />
          </el-descriptions-item>
        </el-descriptions>
      </div>
      
      <template #footer>
        <el-button @click="controlDialogVisible = false" :loading="controlling">取消</el-button>
        <el-button 
          :type="getControlConfirmButtonType()" 
          @click="confirmControl" 
          :loading="controlling"
        >
          {{ getControlConfirmButtonText() }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search, View, Document, Download, Loading, Delete, VideoPause, VideoPlay, Warning, FullScreen, Filter, ArrowDown, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { taskRunApi, taskApi, workerApi, minioApi, callbackJobRunApi } from '@/services'
import { shardBatchApi, type ShardLogEntry, type ShardInfo } from '@/services/shardBatch'
import type { TaskRun, TaskVO, WorkerVO } from '@/types'
import { ElMessageBox } from 'element-plus'
import { formatDateTime, formatDuration } from '@/utils/date'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import FilterCard from '@/components/FilterCard.vue'

const router = useRouter()
const loading = ref(false)
const taskRuns = ref<TaskRun[]>([])

/**
 * 统计：指定状态的记录数
 * @param status 运行状态
 */
function statusCount(status: string): number {
  return taskRuns.value.filter(r => r.status === status).length
}

/** 统计：失败数（failed + timeout） */
const failCount = computed(() => taskRuns.value.filter(r => r.status === 'failed' || r.status === 'timeout').length)
const taskMap = ref<Map<string, string>>(new Map())
const workerMap = ref<Map<string, string>>(new Map())
const filterForm = ref({
  status: '',
  taskId: '',
  dateRange: [] as string[],
  sortOrder: 'desc'
})
const detailDialogVisible = ref(false)
const currentRun = ref<any>(null)
const currentRunIndex = ref(0)
const activeTab = ref('basic')
const loadingLog = ref(false)
const downloadingLog = ref(false)
const currentLogContent = ref('')
const currentLogContentHtml = ref('')  // 处理后的HTML内容
// 日志增量拉取状态（offset 协议）
let logPollTimer: number | null = null
let logNextOffset = 0
let logFinalized = false
let loadedLogRunId = ''

// 分片日志聚合状态
const shardInfoLoading = ref(false)
const shardLogLoading = ref(false)
const shardShards = ref<ShardInfo[]>([])
const shardLogEntries = ref<ShardLogEntry[]>([])
const shardLogCursor = ref('')
const shardFollow = ref(true)
const shardLevelFilter = ref('')
const selectedShardIndex = ref<number | null>(null)
const shardSettled = ref(false)
let shardPollTimer: number | null = null
const SHARD_PALETTE = ['#4FC3F7', '#66BB6A', '#FFB74D', '#EF5350', '#AB47BC', '#26C6DA', '#FFA726', '#7E57C2']
const taskTypeMap = ref<Map<string, string>>(new Map()) // 存储任务类型：'script' | 'callback'

// 全屏和筛选功能相关数据
const isFullscreen = ref(false)
const selectedLogLevels = ref<string[]>(['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'])
const allLogLevels = ['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL']
const filteredLogContent = ref('')
const displayLogContent = ref('') // 统一的显示内容
const loadingRequest = ref(false)
const loadingResponse = ref(false)
const downloadingRequest = ref(false)
const downloadingResponse = ref(false)
const currentRequestContent = ref('')
const currentResponseContent = ref('')
const controlDialogVisible = ref(false)
const controlType = ref<'pause' | 'resume'>('pause')
const controlTaskRun = ref<any>(null)
const controlReason = ref('')
const controlling = ref(false)
const showReasonInput = ref(false)

const filteredTaskRuns = computed(() => {
  let result = [...taskRuns.value]
  
  // 状态筛选
  if (filterForm.value.status) {
    result = result.filter(run => run.status === filterForm.value.status)
  }
  
  // 任务名称筛选
  if (filterForm.value.taskId) {
    result = result.filter(run => run.taskId === filterForm.value.taskId)
  }
  
  // 时间范围筛选
  if (filterForm.value.dateRange && filterForm.value.dateRange.length === 2) {
    const [startDate, endDate] = filterForm.value.dateRange
    const startTime = new Date(startDate || Date.now()).getTime()
    const endTime = new Date(endDate || Date.now()).getTime()
    
    result = result.filter(run => {
      const runTime = new Date(run.startTime || run.createTime || Date.now()).getTime()
      return runTime >= startTime && runTime <= endTime
    })
  }
  
  // 排序
  result.sort((a, b) => {
    const timeA = new Date(a.startTime || a.createTime || Date.now()).getTime()
    const timeB = new Date(b.startTime || b.createTime || Date.now()).getTime()
    return filterForm.value.sortOrder === 'asc' ? timeA - timeB : timeB - timeA
  })
  
  return result
})

async function fetchTaskRuns() {
  loading.value = true
  try {
    // 1. 先获取所有任务信息
    await fetchAllTasksInfo()

    // 2. 同时获取脚本任务运行记录和回调任务运行记录
    const [scriptRuns, callbackRuns] = await Promise.all([
      taskRunApi.list(),
      callbackJobRunApi.list({ pageSize: 1000 })
    ])
    
    let combinedRuns: any[] = []
    
    // 3. 只保留脚本任务记录：回调任务也会在 task_run 表产生 pending 记录，需排除
    if (Array.isArray(scriptRuns)) {
      const filteredScriptRuns = scriptRuns.filter(run => {
        const type = taskTypeMap.value.get(run.taskId)
        return type === 'script'
      })
      combinedRuns = [...filteredScriptRuns]
    }
    
    // 4. 添加回调任务记录
    if (Array.isArray(callbackRuns)) {
      combinedRuns = [...combinedRuns, ...callbackRuns]
    } else if (callbackRuns && (callbackRuns as any).records) {
      combinedRuns = [...combinedRuns, ...(callbackRuns as any).records]
    }
    
    // 5. 按开始时间倒序排列（默认排序，筛选后会重新排序）
    combinedRuns.sort((a, b) => {
      const timeA = new Date(a.startTime || a.createTime || Date.now()).getTime()
      const timeB = new Date(b.startTime || b.createTime || Date.now()).getTime()
      return timeB - timeA
    })
    
    taskRuns.value = combinedRuns
    
    // 6. 筛选出有运行记录的任务，只显示这些任务
    filterTaskOptions(combinedRuns)
  } catch (error) {
    console.error('获取运行记录失败:', error)
    ElMessage.error('获取运行记录失败')
    taskRuns.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 获取所有任务信息（用于获取任务类型等）
 */
async function fetchAllTasksInfo() {
  try {
    const response = await taskApi.list({ pageSize: 1000 })
    let tasks: TaskVO[] = []
    if (Array.isArray(response)) {
      tasks = response
    } else {
      tasks = response.records || []
    }
    taskMap.value.clear()
    taskTypeMap.value.clear()
    tasks.forEach(task => {
      taskMap.value.set(task.id, task.name)
      taskTypeMap.value.set(task.id, task.executeType || 'script')
    })
  } catch (error) {
    console.error('获取任务信息失败:', error)
  }
}

/**
 * 筛选出有运行记录的任务，用于下拉框显示
 * @param runs 任务运行记录列表
 */
function filterTaskOptions(runs: any[]) {
  // 提取所有有运行记录的任务ID
  const taskIdsWithRuns = new Set(runs.map(run => run.taskId))
  
  // 清空并重新填充 taskMap，只包含有运行记录的任务
  const filteredTaskMap = new Map<string, string>()
  const filteredTaskTypeMap = new Map<string, string>()
  
  taskIdsWithRuns.forEach(taskId => {
    const name = taskMap.value.get(taskId)
    const type = taskTypeMap.value.get(taskId)
    if (name) {
      filteredTaskMap.set(taskId, name)
    }
    if (type) {
      filteredTaskTypeMap.set(taskId, type)
    }
  })
  
  taskMap.value = filteredTaskMap
  taskTypeMap.value = filteredTaskTypeMap
}

async function fetchWorkerNames() {
  try {
    const response = await workerApi.list({ pageSize: 1000 })
    let workers: WorkerVO[] = []
    if (Array.isArray(response)) {
      workers = response
    } else {
      workers = response.records || []
    }
    workerMap.value.clear()
    workers.forEach(worker => {
      workerMap.value.set(worker.id, worker.host)
    })
  } catch (error) {
    console.error('获取执行器名称失败:', error)
  }
}

function indexMethod(index: number): number {
  return index + 1
}

function getTaskName(taskId: string): string {
  return taskMap.value.get(taskId) || taskId
}

function getWorkerName(workerId: string): string {
  if (!workerId) return '-'
  return workerMap.value.get(workerId) || workerId
}

function isScriptTask(taskId: string): boolean {
  return taskTypeMap.value.get(taskId) === 'script'
}

function isCallbackTask(taskId: string): boolean {
  return taskTypeMap.value.get(taskId) === 'callback'
}

function handleSearch() {
  // 筛选在computed属性中自动进行，无需重新获取数据
  ElMessage.info('正在筛选运行记录...')
}

function handleReset() {
  filterForm.value.status = ''
  filterForm.value.taskId = ''
  filterForm.value.dateRange = []
  filterForm.value.sortOrder = 'desc'
  ElMessage.info('已重置筛选条件')
}

async function handleDelete(row: TaskRun) {
  try {
    await ElMessageBox.confirm('确定要删除这条运行记录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    if (isCallbackTask(row.taskId)) {
      await callbackJobRunApi.delete(row.id)
    } else {
      await taskRunApi.delete(row.id)
    }
    
    ElMessage.success('删除成功')
    fetchTaskRuns()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

async function viewDetail(row: TaskRun) {
  currentRunIndex.value = taskRuns.value.findIndex(r => r.id === row.id) + 1
  
  try {
    let detail: any
    
    if (isCallbackTask(row.taskId)) {
      // 回调任务，调用CallbackJobRun接口
      const callbackDetail = await callbackJobRunApi.getById(row.id)
      detail = {
        ...callbackDetail,
        // 转换为统一的格式以便模板渲染
        taskName: callbackDetail.taskName,
        taskRunId: callbackDetail.id,
        requestUrl: callbackDetail.requestUrl,
        requestMethod: callbackDetail.requestMethod,
        responseStatusCode: callbackDetail.responseStatusCode,
        responseTimeMs: callbackDetail.responseTimeMs,
        retryIndex: callbackDetail.retryIndex,
        requestPath: callbackDetail.requestPath,
        responsePath: callbackDetail.responsePath,
      }
    } else {
      // 脚本任务，调用TaskRun接口
      const taskRunDetail = await taskRunApi.getById(row.id)
      detail = taskRunDetail
    }
    
    currentRun.value = detail
  } catch (error) {
    // 如果API调用失败，使用列表数据作为备选
    currentRun.value = row
    ElMessage.warning('获取详细信息失败，显示列表数据')
  }
  
  currentLogContent.value = ''
  currentLogContentHtml.value = ''
  detailDialogVisible.value = true
}

function handleTabChange(tabName: string) {
  if (tabName === 'logs' && currentRun.value && isScriptTask(currentRun.value.taskId)) {
    loadLogContent(currentRun.value.id)
  } else if (tabName === 'shardLogs' && currentRun.value && isShardTask(currentRun.value)) {
    loadShardLogs()
  } else if (tabName === 'request' && currentRun.value && isCallbackTask(currentRun.value.taskId)) {
    loadRequestContent(currentRun.value.id)
  } else if (tabName === 'response' && currentRun.value && isCallbackTask(currentRun.value.taskId)) {
    loadResponseContent(currentRun.value.id)
  }
}

/**
 * 处理日志内容，应用颜色高亮
 * @param content 原始日志内容
 * @returns 处理后的HTML内容
 */
function processLogContent(content: string): string {
  if (!content) return ''
  
  // 按行处理
  const lines = content.split('\n')
  const processedLines = lines.map(line => {
    // 匹配日志级别模式（基于我们统一日志格式）
    // 格式: 2026-02-04 14:25:27 | INFO     | module.py:function:123 - message
    
    // 1. 匹配整个日志行格式
    const logPattern = /^(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2})\s*\|\s*(\w+)\s*\|\s*(.*?)(?:\s*-\s*)(.*)$/
    const match = line.match(logPattern)
    
    if (match) {
      const [, timestamp, level, location, message] = match
      
      // 根据日志级别确定颜色（护眼配色）
      const levelColors: Record<string, string> = {
        'DEBUG': '#4FC3F7',     // 柔和青色
        'INFO': '#66BB6A',      // 柔和绿色
        'WARNING': '#FFB74D',   // 柔和琥珀色
        'ERROR': '#EF5350',     // 柔和红色
        'CRITICAL': '#AB47BC',  // 柔和紫色
        'CRIT': '#AB47BC'       // 兼容简写
      }
      
      const color = levelColors[level?.toUpperCase() || ''] || '#FFFFFF'
      
      // 所有字段先 HTML 转义再拼装，防止日志内容注入脚本（XSS）
      const ts = escapeHtml(timestamp || '')
      const lv = escapeHtml(level || '')
      const loc = escapeHtml(location || '')
      const msg = escapeHtml(message || '')
      
      // 构建带颜色的HTML
      return `<span style="color: ${color};">${ts}</span>` +
             ` | <span style="color: ${color}; font-weight: bold;">${lv}</span>` +
             ` | <span style="color: #B0BEC5;">${loc}</span>` +
             ` - <span style="color: ${color};">${msg}</span>`
    }
    
    // 2. 匹配包含异常信息的行
    if (line.includes('Traceback (most recent call last):') || 
        line.includes('File ') && line.includes('line ') ||
        line.match(/^\s*[A-Za-z_][A-Za-z0-9_]*Error:/) ||
        line.match(/^\s*at\s+/)) {
      return `<span style="color: #E57373;">${escapeHtml(line)}</span>`
    }
    
    // 3. 匹配包含关键字的行（护眼配色）
    const keywords = [
      { pattern: /\b(exception|Exception|ERROR|Error)\b/g, color: '#EF5350' },
      { pattern: /\b(warning|Warning|WARN|Warn)\b/g, color: '#FFB74D' },
      { pattern: /\b(info|Info|INFO)\b/g, color: '#66BB6A' },
      { pattern: /\b(debug|Debug|DEBUG)\b/g, color: '#4FC3F7' },
      { pattern: /\b(success|Success|SUCCESS)\b/g, color: '#66BB6A' },
      { pattern: /\b(failed|Failed|FAILED|FAIL|Fail)\b/g, color: '#EF5350' },
      { pattern: /\b(start|Start|START)\b/g, color: '#4FC3F7' },
      { pattern: /\b(end|End|END)\b/g, color: '#FFB74D' },
      { pattern: /\b(complete|Complete|COMPLETE|finish|Finish|FINISH)\b/g, color: '#66BB6A' }
    ]
    
    let processedLine = escapeHtml(line)
    keywords.forEach(({ pattern, color }) => {
      processedLine = processedLine.replace(pattern, 
        `<span style="color: ${color}; font-weight: bold;">$&</span>`)
    })
    
    return processedLine
  })
  
  return processedLines.join('\n')
}

/**
 * HTML转义函数
 * @param text 要转义的文本
 * @returns 转义后的文本
 */
function escapeHtml(text: string): string {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

/**
 * 切换全屏模式
 */
function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
  
  // 如果进入全屏模式，更新显示内容并添加ESC键监听
  if (isFullscreen.value) {
    filteredLogContent.value = filterLogs()
    // 延迟添加监听，确保DOM已经更新
    setTimeout(() => {
      addKeyboardListener()
    }, 100)
  } else {
    // 退出全屏时移除监听并清空内容
    removeKeyboardListener()
    filteredLogContent.value = ''
  }
}

/**
 * ESC键退出全屏
 */
function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && isFullscreen.value) {
    isFullscreen.value = false
    filteredLogContent.value = ''
  }
}

/**
 * 添加/移除键盘事件监听
 */
function addKeyboardListener() {
  document.addEventListener('keydown', handleKeydown)
}

function removeKeyboardListener() {
  document.removeEventListener('keydown', handleKeydown)
}

// 监听全屏状态变化，自动管理ESC键监听器
watch(isFullscreen, (newValue, oldValue) => {
  if (newValue && !oldValue) {
    addKeyboardListener()
  } else if (!newValue && oldValue) {
    removeKeyboardListener()
  }
})

// 监听筛选级别变化，自动更新显示内容
watch(selectedLogLevels, () => {
  updateDisplayContent()
}, { deep: true })

/**
 * 筛选日志内容
 */
function filterLogs(): string {
  const originalContent = currentLogContent.value || ''
  
  // 如果没有筛选条件或选择了全部级别，返回处理后的完整内容
  if (selectedLogLevels.value.length === 0 || selectedLogLevels.value.length === allLogLevels.length) {
    return currentLogContentHtml.value || '暂无日志内容'
  }
  
  // 按行分割原始内容（不包含HTML标签）
  const lines = originalContent.split('\n')
  const filteredLines: string[] = []
  
  lines.forEach(line => {
    // 检查是否包含选中的日志级别
    const hasSelectedLevel = selectedLogLevels.value.some(level => {
      // 更精确的匹配模式 - 匹配日志级别
      const pattern = new RegExp(`\\b${level}\\b`, 'i')
      return pattern.test(line)
    })
    
    if (hasSelectedLevel) {
      filteredLines.push(line)
    }
  })
  
  // 如果没有匹配的行，返回空内容
  if (filteredLines.length === 0) {
    return '暂无匹配的日志内容'
  }
  
  // 对筛选后的内容应用颜色高亮
  const filteredContent = filteredLines.join('\n')
  return processLogContent(filteredContent)
}

/**
 * 更新显示内容
 */
function updateDisplayContent() {
  // 获取筛选后的内容
  const filtered = filterLogs()
  
  // 如果在全屏模式，使用filteredLogContent
  if (isFullscreen.value) {
    filteredLogContent.value = filtered
  }
  
  // 更新统一显示内容
  displayLogContent.value = filtered
}

/**
 * 处理筛选变化
 */
function handleFilterChange() {
  // 更新显示内容
  updateDisplayContent()
}

/**
 * 全选日志级别
 */
function selectAllLevels() {
  selectedLogLevels.value = [...allLogLevels]
  filteredLogContent.value = ''
  filteredLogContent.value = filterLogs()
}

/**
 * 清空所有日志级别
 */
function clearAllLevels() {
  selectedLogLevels.value = []
  filteredLogContent.value = ''
}



/** 增量拉取一块日志并拼接到当前内容（truncatedHeadBytes>0 时从头重读） */
async function fetchLogChunk(taskRunId: string) {
  const data = await taskRunApi.getLogRange(taskRunId, logNextOffset, 65536)
  if (!data) return
  if (data.truncatedHeadBytes > 0) {
    // 偏移失效：对象被重建/截断（如分片合并、保留窗口清理），从头重读
    currentLogContent.value = data.content || ''
    currentLogContentHtml.value = processLogContent(currentLogContent.value || '暂无日志内容')
  } else if (data.content) {
    currentLogContent.value += data.content
    currentLogContentHtml.value = processLogContent(currentLogContent.value || '暂无日志内容')
  } else if (!currentLogContent.value) {
    currentLogContentHtml.value = processLogContent('暂无日志内容')
  }
  logNextOffset = data.nextOffset
  if (data.finalized) {
    logFinalized = true
    stopLogPolling()
  }
}

function startLogPolling(taskRunId: string) {
  stopLogPolling()
  logPollTimer = window.setInterval(async () => {
    // 弹窗已关闭则停止轮询
    if (!detailDialogVisible.value) {
      stopLogPolling()
      return
    }
    try {
      await fetchLogChunk(taskRunId)
    } catch (e) {
      console.error('轮询日志失败:', e)
    }
  }, 3000)
}

function stopLogPolling() {
  if (logPollTimer !== null) {
    clearInterval(logPollTimer)
    logPollTimer = null
  }
}

async function loadLogContent(taskRunId: string) {
  // 已完整加载过同一 run 的日志则直接复用，避免重复全量拉取
  if (loadedLogRunId === taskRunId && logFinalized && currentLogContent.value) {
    return
  }
  loadingLog.value = true
  try {
    currentLogContent.value = ''
    currentLogContentHtml.value = ''
    loadedLogRunId = taskRunId
    logFinalized = false
    logNextOffset = 0
    stopLogPolling()
    await fetchLogChunk(taskRunId)
    // 任务未终止则轮询增量
    if (!logFinalized) {
      startLogPolling(taskRunId)
    }
  } catch (error) {
    console.error('加载日志失败:', error)
    const errorText = '日志加载失败，请重试或手动下载'
    currentLogContent.value = errorText
    currentLogContentHtml.value = processLogContent(errorText)
    ElMessage.error('加载日志失败')
  } finally {
    loadingLog.value = false
  }
}

// ------------------------------------------------------------------ 分片日志

/** 是否为分片任务（shardTotal>1） */
function isShardTask(run: any): boolean {
  return !!run && (run.shardTotal || 0) > 1
}

/** 片色（稳定映射） */
function shardColor(shard: number): string {
  return SHARD_PALETTE[Math.abs(shard) % SHARD_PALETTE.length] || '#CBD5E1'
}

/** 级别色（护眼配色） */
function levelColor(level: string): string {
  const map: Record<string, string> = {
    'DEBUG': '#4FC3F7', 'INFO': '#66BB6A', 'WARN': '#FFB74D', 'WARNING': '#FFB74D',
    'ERROR': '#EF5350', 'CRITICAL': '#AB47BC'
  }
  return map[level?.toUpperCase() || ''] || '#CBD5E1'
}

/** 状态 → el-tag 类型 */
function stateTagType(state: string): 'success' | 'danger' | 'warning' | 'info' {
  if (state === 'success') return 'success'
  if (state === 'failed' || state === 'timeout') return 'danger'
  if (state === 'running' || state === 'pending') return 'warning'
  return 'info'
}

async function loadShardLogs() {
  const batchId = currentRun.value?.shardBatchId
  if (!batchId) return
  stopShardPolling()
  shardInfoLoading.value = true
  shardLogLoading.value = true
  shardLogEntries.value = []
  shardLogCursor.value = ''
  shardSettled.value = false
  try {
    const info = await shardBatchApi.getShardInfo(batchId)
    shardShards.value = info.shards || []
    shardSettled.value = !!info.settled
  } catch (e) {
    console.error('加载分片信息失败:', e)
    shardShards.value = []
  } finally {
    shardInfoLoading.value = false
  }
  await fetchShardChunk()
  if (shardFollow.value && !shardSettled.value) {
    startShardPolling()
  }
}

async function fetchShardChunk() {
  const batchId = currentRun.value?.shardBatchId
  if (!batchId) return
  try {
    const res = await shardBatchApi.getShardLogs(batchId, {
      cursor: shardLogCursor.value,
      level: shardLevelFilter.value || undefined,
      shard: selectedShardIndex.value ?? undefined
    })
    // 游标为空或设备分片时重置为本次返回，追加 entries
    shardLogEntries.value = res.entries || []
    shardLogCursor.value = res.cursor || ''
    shardSettled.value = !!res.settled
    if (res.settled) {
      stopShardPolling()
    }
  } catch (e) {
    console.error('获取分片日志失败:', e)
  } finally {
    shardLogLoading.value = false
  }
}

function startShardPolling() {
  stopShardPolling()
  shardPollTimer = window.setInterval(async () => {
    if (!detailDialogVisible.value) {
      stopShardPolling()
      return
    }
    try {
      await fetchShardChunk()
    } catch (e) {
      console.error('轮询分片日志失败:', e)
    }
  }, 3000)
}

function stopShardPolling() {
  if (shardPollTimer !== null) {
    clearInterval(shardPollTimer)
    shardPollTimer = null
  }
}

function resetShardCursor() {
  shardLogCursor.value = ''
  shardLogEntries.value = []
  shardLogLoading.value = true
  fetchShardChunk().then(() => {
    if (shardFollow.value && !shardSettled.value) {
      startShardPolling()
    }
  })
}

/** 点击片列表：切换单片过滤（再点取消） */
function toggleShardFilter(idx: number) {
  selectedShardIndex.value = selectedShardIndex.value === idx ? null : idx
  resetShardCursor()
}


async function loadRequestContent(taskRunId: string) {
  loadingRequest.value = true
  try {
    const requestData = await callbackJobRunApi.getRequestData(taskRunId)
    currentRequestContent.value = JSON.stringify(requestData, null, 2) || '暂无请求数据'
  } catch (error) {
    console.error('加载请求数据失败:', error)
    currentRequestContent.value = '请求数据加载失败，请重试'
    ElMessage.error('加载请求数据失败')
  } finally {
    loadingRequest.value = false
  }
}

async function loadResponseContent(taskRunId: string) {
  loadingResponse.value = true
  try {
    const responseData = await callbackJobRunApi.getResponseData(taskRunId)
    currentResponseContent.value = JSON.stringify(responseData, null, 2) || '暂无响应数据'
  } catch (error) {
    console.error('加载响应数据失败:', error)
    currentResponseContent.value = '响应数据加载失败，请重试'
    ElMessage.error('加载响应数据失败')
  } finally {
    loadingResponse.value = false
  }
}

function viewTaskDetail(taskId: string) {
  router.push(`/tasks/${taskId}`)
}

function viewWorkerDetail(workerId?: string) {
  if (workerId) {
    router.push(`/workers/${workerId}`)
  }
}

async function previewLog(row: TaskRun) {
  // 确保只对脚本任务执行日志预览
  if (!isScriptTask(row.taskId)) {
    ElMessage.warning('该任务类型不支持日志预览')
    return
  }
  
  currentRun.value = row
  activeTab.value = 'logs'
  detailDialogVisible.value = true
  
  await loadLogContent(row.id)
}

async function downloadLog(row: TaskRun) {
  try {
    downloadingLog.value = true
    
    // 首先获取 MinIO 文件信息
    const logFileInfo = await taskRunApi.getLogFileInfo(row.id)
    
    // 使用 MinIO API 下载文件
    const logBlob = await minioApi.downloadFile(logFileInfo)
    
    // 创建下载链接
    const url = URL.createObjectURL(logBlob)
    const a = document.createElement('a')
    a.href = url
    a.download = logFileInfo.fileName || `task-run-${row.id}.log`
    a.click()
    URL.revokeObjectURL(url)
    
    ElMessage.success('日志下载成功')
  } catch (error) {
    console.error('下载日志失败:', error)
    ElMessage.error('下载日志失败')
  } finally {
    downloadingLog.value = false
  }
}

async function downloadCurrentLog() {
  if (currentRun.value) {
    await downloadLog(currentRun.value)
  }
}

async function downloadRequestData() {
  if (currentRun.value) {
    try {
      downloadingRequest.value = true
      const blob = await callbackJobRunApi.downloadRequest(currentRun.value.id)
      
      // 创建下载链接
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `request-${currentRun.value.id}.json`
      a.click()
      URL.revokeObjectURL(url)
      
      ElMessage.success('请求数据下载成功')
    } catch (error) {
      console.error('下载请求数据失败:', error)
      ElMessage.error('下载请求数据失败')
    } finally {
      downloadingRequest.value = false
    }
  }
}

async function downloadResponseData() {
  if (currentRun.value) {
    try {
      downloadingResponse.value = true
      const blob = await callbackJobRunApi.downloadResponse(currentRun.value.id)
      
      // 创建下载链接
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `response-${currentRun.value.id}.json`
      a.click()
      URL.revokeObjectURL(url)
      
      ElMessage.success('响应数据下载成功')
    } catch (error) {
      console.error('下载响应数据失败:', error)
      ElMessage.error('下载响应数据失败')
    } finally {
      downloadingResponse.value = false
    }
  }
}

function canPause(row: TaskRun): boolean {
  return row.status === 'running' && isScriptTask(row.taskId)
}

function canResume(row: TaskRun): boolean {
  return row.status === 'paused' && isScriptTask(row.taskId)
}

function handlePause(row: TaskRun) {
  controlType.value = 'pause'
  controlTaskRun.value = row
  controlReason.value = ''
  showReasonInput.value = true
  controlDialogVisible.value = true
}

function handleResume(row: TaskRun) {
  controlType.value = 'resume'
  controlTaskRun.value = row
  controlReason.value = ''
  showReasonInput.value = false
  controlDialogVisible.value = true
}

async function confirmControl() {
  if (!controlTaskRun.value || !controlType.value) return
  
  controlling.value = true
  
  try {
    const taskRunId = controlTaskRun.value.id
    const reason = controlReason.value || undefined
    
    switch (controlType.value) {
      case 'pause':
        await taskRunApi.pause(taskRunId, reason)
        ElMessage.success('暂停指令已发送，任务即将暂停')
        break
      case 'resume':
        await taskRunApi.resume(taskRunId, reason)
        ElMessage.success('恢复指令已发送，任务即将继续执行')
        break
    }
    
    controlDialogVisible.value = false
    fetchTaskRuns()
  } catch (error) {
    console.error('控制操作失败:', error)
    ElMessage.error('控制操作失败，请重试')
  } finally {
    controlling.value = false
  }
}

function getControlDialogTitle(): string {
  const titles: Record<string, string> = {
    pause: '暂停任务',
    resume: '恢复任务'
  }
  return titles[controlType.value] || '操作确认'
}

function getControlConfirmButtonType(): string {
  const types: Record<string, string> = {
    pause: 'warning',
    resume: 'success'
  }
  return types[controlType.value] || 'primary'
}

function getControlConfirmButtonText(): string {
  const texts: Record<string, string> = {
    pause: '确认暂停',
    resume: '确认恢复'
  }
  return texts[controlType.value] || '确认'
}

function getControlWarningMessage(): string {
  const messages: Record<string, string> = {
    pause: '确定要暂停该任务吗？暂停后任务将进入暂停状态，可以手动恢复执行。',
    resume: '确定要恢复该任务吗？恢复后任务将继续执行。'
  }
  return messages[controlType.value] || '确定要执行此操作吗？'
}

function getStatusType(status: string): string {
  const map: Record<string, string> = {
    pending: 'info',
    running: 'primary',
    paused: 'warning',
    success: 'success',
    failed: 'danger',
    timeout: 'warning',
  }
  return map[status] || 'info'
}

function getStatusText(status: string): string {
  const map: Record<string, string> = {
    pending: '等待中',
    running: '运行中',
    paused: '已暂停',
    success: '成功',
    failed: '失败',
    timeout: '超时',
  }
  return map[status] || status
}

onMounted(() => {
  fetchTaskRuns()
  addKeyboardListener()
})

onUnmounted(() => {
  removeKeyboardListener()
  stopLogPolling()
  stopShardPolling()
})
</script>

<style scoped>
.task-run-list-container {
  max-width: 1440px;
  margin: 0 auto;
}

/* ---------- 统计卡 ---------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 1024px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* ---------- 表格面板 ---------- */
.table-panel {
  border-radius: 12px;
  border: 1px solid var(--cronova-border);
  background: var(--cronova-surface);
  box-shadow: var(--cronova-card-shadow);
  padding: 8px 16px 16px;
}

.empty-text {
  color: var(--cronova-muted);
}

.exit-code {
  font-weight: 600;
  font-size: 13px;
}

.code-success {
  color: var(--cronova-success);
}

.code-fail {
  color: var(--cronova-danger);
}

.time-text {
  font-size: 13px;
  color: var(--cronova-text);
}

.filter-form {
  display: flex;
  align-items: flex-end;
}

.empty-tip {
  padding: 40px 0;
}

.error-message {
  color: var(--cronova-danger);
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-section {
  margin-top: 16px;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.log-title {
  font-weight: 600;
  font-size: 16px;
  color: var(--cronova-text);
}

.log-content-wrapper {
  border: 1px solid #1F2E47;
  border-radius: 6px;
  background: #0B1220;
  box-shadow: inset 0 0 10px rgba(0, 0, 0, 0.5);
}

.log-container {
  flex: 1;
  overflow: auto;
  background-color: #0B1220;  /* 深海底色 */
  border-radius: 6px;
  padding: 16px;
  border: 1px solid #1F2E47;
  box-shadow: inset 0 0 10px rgba(0, 0, 0, 0.5);
  max-height: 500px;
}

.log-text {
  margin: 0;
  font-family: var(--cronova-mono);
  font-size: 12px;
  line-height: 1.4;
  color: #E2EAF4;
  white-space: pre-wrap;
  word-wrap: break-word;
  word-break: break-all;
  background: transparent;
}

/* ---------- 分片日志 ---------- */
.shard-log-layout {
  display: flex;
  gap: 12px;
  height: 500px;
}
.shard-log-sidebar {
  width: 220px;
  border: 1px solid #1F2E47;
  border-radius: 6px;
  background: #0B1220;
  padding: 8px;
  overflow: auto;
}
.shard-side-title {
  font-size: 12px;
  color: #94A3B8;
  margin-bottom: 8px;
}
.shard-side-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  margin-bottom: 4px;
}
.shard-side-item:hover {
  background: #16233A;
}
.shard-side-item.active {
  background: #1E3A5F;
}
.shard-side-state {
  font-size: 12px;
  color: #CBD5E1;
  flex: 1;
}
.shard-side-dur {
  font-size: 12px;
  color: #64748B;
}
.shard-log-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.shard-log-body {
  flex: 1;
  overflow: auto;
  border: 1px solid #1F2E47;
  border-radius: 6px;
  background: #0B1220;
  padding: 12px;
}
.shard-log-stream {
  font-family: var(--cronova-mono);
  font-size: 12px;
  line-height: 1.4;
}
.shard-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 2px 0;
  white-space: pre-wrap;
  word-break: break-all;
}
.shard-tag {
  flex-shrink: 0;
  font-weight: 600;
  font-size: 11px;
  min-width: 42px;
}
.shard-level {
  flex-shrink: 0;
  font-weight: 600;
  min-width: 62px;
}
.shard-ts {
  flex-shrink: 0;
  color: #64748B;
  font-size: 11px;
}
.shard-msg {
  color: #E2EAF4;
}

/* 滚动条样式 */
.log-container::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.log-container::-webkit-scrollbar-track {
  background: #111A2C;
  border-radius: 4px;
}

.log-container::-webkit-scrollbar-thumb {
  background: #2B3D5C;
  border-radius: 4px;
}

.log-container::-webkit-scrollbar-thumb:hover {
  background: #3B4D6C;
}

/* 日志工具栏样式 */
.log-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* 全屏模式样式 */
.log-container.fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  background-color: #0B1220 !important;
  border-radius: 0;
  border: none;
  box-shadow: none;
  padding: 20px;
  max-height: none !important;
}

/* 筛选下拉框样式 */
.log-level-filter {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  min-width: 200px;
}

.log-level-filter .el-checkbox {
  margin-right: 0;
}

.log-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--cronova-muted);
}

.log-loading .el-icon {
  margin-right: 8px;
}

.callback-section {
  margin-top: 16px;
}

.callback-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.callback-title {
  font-weight: 600;
  font-size: 16px;
  color: var(--cronova-text);
}

.callback-content {
  border: 1px solid var(--cronova-border);
  border-radius: 6px;
  background: var(--cronova-bg);
}

.callback-content-text {
  font-family: var(--cronova-mono);
  font-size: 12px;
  line-height: 1.5;
}

.callback-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--cronova-muted);
}

.callback-loading .el-icon {
  margin-right: 8px;
}

.control-warning {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: var(--cronova-danger-soft);
  border: 1px solid var(--cronova-danger);
  border-radius: 8px;
  margin-bottom: 20px;
  color: var(--cronova-danger);
  line-height: 1.5;
}

.warning-icon {
  color: var(--cronova-danger);
  flex-shrink: 0;
  margin-top: 2px;
}

.control-reason {
  margin-bottom: 20px;
}

.control-task-info {
  padding: 16px;
  background: var(--cronova-bg);
  border-radius: 8px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.action-buttons {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
</style>
