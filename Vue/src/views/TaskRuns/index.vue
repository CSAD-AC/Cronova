<template>
  <div class="task-run-list-container">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">任务运行记录</h1>
        <p class="page-subtitle">查看系统中所有任务的执行记录</p>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="fetchTaskRuns" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>
    
    <el-card class="filter-card">
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
    </el-card>
    
    <el-card class="table-card">
      <el-table 
        :data="filteredTaskRuns" 
        v-loading="loading"
        style="width: 100%"
        stripe
      >
        <el-table-column type="index" label="序号" width="80" :index="indexMethod" />
        <el-table-column prop="taskId" label="任务" min-width="170">
          <template #default="{ row }">
            <el-link type="primary" @click="viewTaskDetail(row.taskId)">
              {{ getTaskName(row.taskId) }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="workerId" label="执行器" width="180">
          <template #default="{ row }">
            <el-link type="info" @click="viewWorkerDetail(row.workerId)">
              {{ getWorkerName(row.workerId) }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="exitCode" label="退出码" width="70">
          <template #default="{ row }">
            <span v-if="row.exitCode !== undefined && row.exitCode !== null">
              {{ row.exitCode }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时" width="100">
          <template #default="{ row }">
            <span v-if="row.durationMs">
              {{ formatDuration(row.durationMs) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">
              <el-icon class="mr-1"><View /></el-icon>详情
            </el-button>
            <el-button 
              v-if="isScriptTask(row.taskId)" 
              link 
              type="primary" 
              @click="previewLog(row)"
            >
              <el-icon class="mr-1"><Document /></el-icon>日志
            </el-button>
            <el-button 
              v-if="canPause(row)" 
              link 
              type="warning" 
              @click="handlePause(row)"
            >
              <el-icon class="mr-1"><VideoPause /></el-icon>暂停
            </el-button>
            <el-button 
              v-if="canResume(row)" 
              link 
              type="success" 
              @click="handleResume(row)"
            >
              <el-icon class="mr-1"><VideoPlay /></el-icon>恢复
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">
              <el-icon class="mr-1"><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div v-if="taskRuns.length === 0 && !loading" class="empty-tip">
        <el-empty description="暂无运行记录" :image-size="100" />
      </div>
    </el-card>
    
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
              <el-tag :type="getStatusType(currentRun.status)" size="small">
                {{ getStatusText(currentRun.status) }}
              </el-tag>
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
              <el-button size="small" type="primary" @click="downloadCurrentLog" :loading="downloadingLog">
                <el-icon><Download /></el-icon>
                下载日志
              </el-button>
            </div>
            <div class="log-content-wrapper">
              <div v-if="loadingLog" class="log-loading">
                <el-icon class="is-loading"><Loading /></el-icon>
                加载日志中...
              </div>
              <el-input
                v-else
                type="textarea"
                :model-value="currentLogContent"
                readonly
                :rows="20"
                class="log-content"
              />
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
            <el-tag :type="getStatusType(controlTaskRun?.status)" size="small">
              {{ getStatusText(controlTaskRun?.status) }}
            </el-tag>
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
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search, View, Document, Download, Loading, Delete, VideoPause, VideoPlay, Warning } from '@element-plus/icons-vue'
import { taskRunApi, taskApi, workerApi, minioApi, callbackJobRunApi } from '@/services'
import type { TaskRun, TaskVO, WorkerVO } from '@/types'
import { ElMessageBox } from 'element-plus'
import { formatDateTime, formatDuration } from '@/utils/date'

const router = useRouter()
const loading = ref(false)
const taskRuns = ref<TaskRun[]>([])
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
const taskTypeMap = ref<Map<string, string>>(new Map()) // 存储任务类型：'script' | 'callback'
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
    
    // 3. 过滤脚本任务记录：只保留 executeType 为 'script' 的任务记录
    // 因为 callback 类型的任务也会在 task_run 表产生 pending 记录，需要排除掉，只看 callback_job_runs 表的记录
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
  
  detailDialogVisible.value = true
}

function handleTabChange(tabName: string) {
  if (tabName === 'logs' && currentRun.value && isScriptTask(currentRun.value.taskId)) {
    loadLogContent(currentRun.value.id)
  } else if (tabName === 'request' && currentRun.value && isCallbackTask(currentRun.value.taskId)) {
    loadRequestContent(currentRun.value.id)
  } else if (tabName === 'response' && currentRun.value && isCallbackTask(currentRun.value.taskId)) {
    loadResponseContent(currentRun.value.id)
  }
}

async function loadLogContent(taskRunId: string) {
  loadingLog.value = true
  try {
    // 首先获取 MinIO 文件信息
    const logFileInfo = await taskRunApi.getLogFileInfo(taskRunId)
    
    // 使用 MinIO API 下载文件
    const logText = await minioApi.downloadTextFile(logFileInfo)
    currentLogContent.value = logText || '暂无日志信息'
  } catch (error) {
    console.error('加载日志失败:', error)
    currentLogContent.value = '日志加载失败，请重试或手动下载'
    ElMessage.error('加载日志失败')
  } finally {
    loadingLog.value = false
  }
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
  
  // 立即开始加载日志内容
  loadingLog.value = true
  try {
    // 首先获取 MinIO 文件信息
    const logFileInfo = await taskRunApi.getLogFileInfo(row.id)
    
    // 使用 MinIO API 下载文件
    const logText = await minioApi.downloadTextFile(logFileInfo)
    currentLogContent.value = logText || '暂无日志信息'
  } catch (error) {
    console.error('预览日志失败:', error)
    currentLogContent.value = '日志加载失败，请重试或手动下载'
    ElMessage.error('加载日志失败')
  } finally {
    loadingLog.value = false
  }
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
})
</script>

<style scoped>
.task-run-list-container {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
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

.filter-card,
.table-card {
  margin-bottom: 24px;
  border-radius: 12px;
}

.filter-form {
  display: flex;
  align-items: flex-end;
}

.empty-tip {
  padding: 40px 0;
}

.error-message {
  color: #ef4444;
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
}

.log-content-wrapper {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
}

.log-content {
  font-family: 'Monaco', 'Consolas', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
}

.log-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #6b7280;
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
}

.callback-content {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
}

.callback-content-text {
  font-family: 'Monaco', 'Consolas', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
}

.callback-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #6b7280;
}

.callback-loading .el-icon {
  margin-right: 8px;
}

.control-warning {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  margin-bottom: 20px;
  color: #991b1b;
  line-height: 1.5;
}

.warning-icon {
  color: #ef4444;
  flex-shrink: 0;
  margin-top: 2px;
}

.control-reason {
  margin-bottom: 20px;
}

.control-task-info {
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
}
</style>
