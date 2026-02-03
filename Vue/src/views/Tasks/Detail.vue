<template>
  <div class="task-detail-container" v-loading="loading">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <span class="page-title">{{ task?.name }}</span>
        </template>
        <template #extra>
            <el-button-group>
              <el-button @click="$router.push(`/tasks/${taskId}/edit`)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button 
                type="info" 
                v-if="task?.executeType === 'callback'"
                @click="handleHealthCheck"
                :loading="healthCheckLoading"
              >
                <el-icon><Monitor /></el-icon>
                健康检查
              </el-button>
              <el-button type="primary" @click="handlePublish">
                <el-icon><VideoPlay /></el-icon>
                立即执行
              </el-button>
            </el-button-group>
          </template>
      </el-page-header>
    </div>
    
    <el-row :gutter="24">
      <el-col :span="16">
        <el-card class="info-card">
          <template #header>
            <div class="card-header">
              <span>任务信息</span>
              <el-tag :type="task?.status === 'active' ? 'success' : 'danger'">
                {{ task?.status === 'active' ? '活跃' : task?.status === 'paused' ? '暂停' : '已删除' }}
              </el-tag>
            </div>
          </template>
          
          <el-descriptions :column="2" border>
            <el-descriptions-item label="任务ID">{{ task?.id }}</el-descriptions-item>
            <el-descriptions-item label="任务名称">{{ task?.name }}</el-descriptions-item>
            <el-descriptions-item label="执行类型">
              <el-tag size="small">{{ task?.executeType === 'script' ? '脚本执行' : 'HTTP回调' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="任务类型">
              <el-tag size="small" :type="task?.taskType === 'cron' ? 'primary' : 'info'">
                {{ task?.taskType === 'cron' ? 'Cron定时任务' : '一次性任务' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="脚本类型">{{ task?.type || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Cron表达式">
              <div v-if="task?.cronExpr">
                <div class="cron-expr">{{ task.cronExpr }}</div>
                <div class="cron-desc" v-if="cronDescription">{{ cronDescription }}</div>
              </div>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="超时时间">{{ task?.timeoutSeconds }}秒</el-descriptions-item>
            <el-descriptions-item label="目标执行器">{{ task?.targetWorker || '自动匹配' }}</el-descriptions-item>
            
            <!-- 回调任务配置显示 -->
            <template v-if="task?.executeType === 'callback' && task?.callbackConfig">
              <el-descriptions-item label="回调URL">{{ task.callbackConfig.callbackUrl || '-' }}</el-descriptions-item>
              <el-descriptions-item label="请求方法">{{ task.callbackConfig.callbackMethod || '-' }}</el-descriptions-item>
              <el-descriptions-item label="密钥">{{ task.callbackConfig.secretKey || '-' }}</el-descriptions-item>
              <el-descriptions-item label="重试次数">{{ task.callbackConfig.retryCount || 0 }}次</el-descriptions-item>
              <el-descriptions-item label="重试间隔">{{ task.callbackConfig.retryIntervalSeconds || 60 }}秒</el-descriptions-item>
            </template>
            <el-descriptions-item label="创建人">{{ task?.createdBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(task?.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDateTime(task?.updateTime) }}</el-descriptions-item>
            <el-descriptions-item label="任务描述" :span="2">{{ task?.description || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
        
        <el-card class="runs-card">
          <template #header>
            <div class="card-header">
              <span>执行历史</span>
              <el-button-group>
                <el-button size="small" @click="fetchTaskRuns">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </el-button-group>
            </div>
          </template>
          
          <el-table 
            :data="taskRuns" 
            style="width: 100%"
            v-loading="runsLoading"
            stripe
          >
            <el-table-column prop="id" label="执行ID" width="180" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="workerId" label="执行器" width="120">
              <template #default="{ row }">
                <span v-if="row.workerId">{{ row.workerId }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.startTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="endTime" label="结束时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="durationMs" label="耗时" width="80">
              <template #default="{ row }">
                {{ row.durationMs ? formatDuration(row.durationMs) : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="exitCode" label="退出码" width="80" />
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button-group>
                  <el-tooltip content="查看日志">
                    <el-button size="small" @click="viewLogs(row)">
                      <el-icon><Document /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="下载日志" v-if="row.status === 'success' || row.status === 'failed'">
                    <el-button size="small" @click="downloadLog(row)">
                      <el-icon><Download /></el-icon>
                    </el-button>
                  </el-tooltip>
                </el-button-group>
              </template>
            </el-table-column>
          </el-table>
          
          <div class="pagination-container" v-if="runsTotal > 0">
            <el-pagination
              v-model:current-page="runsCurrentPage"
              v-model:page-size="runsPageSize"
              :page-sizes="[5, 10, 20, 50]"
              :total="runsTotal"
              layout="total, sizes, prev, pager, next"
              @size-change="handleRunsSizeChange"
              @current-change="handleRunsCurrentChange"
            />
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card class="action-card">
          <template #header>
            <div class="card-header">
              <span>操作</span>
            </div>
          </template>
          
          <div class="action-list">
            <el-button 
              :type="task?.status === 'active' ? 'warning' : 'success'" 
              @click="toggleEnabled"
            >
              <el-icon><Switch /></el-icon>
              {{ task?.status === 'active' ? '禁用任务' : '启用任务' }}
            </el-button>
            <el-button type="primary" @click="handlePublish">
              <el-icon><VideoPlay /></el-icon>
              立即执行
            </el-button>
            <el-button type="danger" @click="handleDelete">
              <el-icon><Delete /></el-icon>
              删除任务
            </el-button>
          </div>
        </el-card>
        
        <el-card class="status-card">
          <template #header>
            <div class="card-header">
              <span>执行统计</span>
            </div>
          </template>
          
          <div class="stats-list">
            <div class="stat-item">
              <span class="stat-label">今日执行</span>
              <span class="stat-value">12</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">成功率</span>
              <span class="stat-value">98.5%</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">平均耗时</span>
              <span class="stat-value">2.5s</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 日志查看对话框 -->
    <el-dialog 
      v-model="logDialogVisible" 
      :title="`任务日志 - ${currentLogTask?.id || ''}`" 
      width="80%"
      :close-on-click-modal="false"
    >
      <div v-if="logLoading" class="log-loading">
        <el-skeleton :rows="10" animated />
      </div>
      <div v-else class="log-content">
        <div class="log-toolbar">
          <el-button size="small" @click="refreshLogs">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button size="small" @click="clearLog">
            <el-icon><Delete /></el-icon>
            清空
          </el-button>
          <el-button size="small" @click="downloadCurrentLog">
            <el-icon><Download /></el-icon>
            下载
          </el-button>
        </div>
        <div ref="logContainer" class="log-container">
          <pre class="log-text">{{ logContent }}</pre>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="logDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Edit, VideoPlay, Switch, Delete, Refresh, Document, Download, Monitor
} from '@element-plus/icons-vue'
import { useTaskStore } from '@/stores/task'
import { taskRunApi } from '@/services'
import { taskApi } from '@/services/task'
import type { TaskRun } from '@/types'
import { formatDateTime, formatDuration } from '@/utils/date'

const route = useRoute()
const router = useRouter()
const taskStore = useTaskStore()

const taskId = route.params.id as string
const loading = ref(false)
const task = ref<any>(null)
const taskRuns = ref<TaskRun[]>([])
const healthCheckLoading = ref(false)
const cronDescription = ref('')

// 执行历史相关数据
const runsLoading = ref(false)
const runsCurrentPage = ref(1)
const runsPageSize = ref(10)
const runsTotal = ref(0)

// 日志相关数据
const logDialogVisible = ref(false)
const logLoading = ref(false)
const logContent = ref('')
const currentLogTask = ref<any>(null)
const logContainer = ref<HTMLDivElement>()

function getStatusType(status: string) {
  const map: Record<string, string> = {
    success: 'success',
    failed: 'danger',
    running: 'primary',
    pending: 'info',
    paused: 'warning',
    timeout: 'warning',
  }
  return map[status] || 'info'
}

function getStatusText(status: string) {
  const map: Record<string, string> = {
    success: '成功',
    failed: '失败',
    running: '运行中',
    pending: '等待',
    paused: '已暂停',
    timeout: '超时',
  }
  return map[status] || status
}

async function fetchTask() {
  loading.value = true
  try {
    task.value = await taskStore.fetchTaskById(taskId)
    
    // 如果有cron表达式，自动获取中文描述（适用于所有任务类型）
    if (task.value?.cronExpr && task.value?.taskType === 'cron') {
      cronDescription.value = '' // 先清空
      try {
        // 先尝试从回调配置获取（如果存在）
        if (task.value?.executeType === 'callback' && task.value?.callbackConfig?.id) {
          try {
            const healthResult = await taskApi.healthCheck(task.value.callbackConfig.id)
            if (healthResult?.cronDescription) {
              cronDescription.value = healthResult.cronDescription
              return // 成功获取描述，退出
            }
          } catch (error) {
            console.warn('通过健康检查获取Cron描述失败:', error)
          }
        }
        
        // 如果健康检查方式失败或不可用，使用专门的Cron描述接口
        const cronResult = await taskApi.getCronDescription(task.value.cronExpr)
        if (cronResult?.description) {
          cronDescription.value = cronResult.description
        }
      } catch (error) {
        console.warn('获取Cron描述失败:', error)
        // 不显示错误，只是静默失败
      }
    }
  } finally {
    loading.value = false
  }
}

/**
 * 获取任务执行历史记录
 * @param pageNum 页码
 * @param pageSize 每页数量
 */
async function fetchTaskRuns(pageNum = runsCurrentPage.value, pageSize = runsPageSize.value) {
  runsLoading.value = true
  try {
    // 尝试调用带分页的API
    const response = await taskApi.getRuns(taskId, pageNum, pageSize)
    if (response && response.records) {
      // 分页格式
      taskRuns.value = response.records
      runsTotal.value = response.total
    } else if (Array.isArray(response)) {
      // 直接数组格式
      taskRuns.value = response
      runsTotal.value = response.length
    } else {
      taskRuns.value = []
      runsTotal.value = 0
    }
  } catch (error) {
    console.error('获取运行记录失败:', error)
    taskRuns.value = []
    runsTotal.value = 0
  } finally {
    runsLoading.value = false
  }
}

/**
 * 处理分页大小变化
 */
function handleRunsSizeChange() {
  runsCurrentPage.value = 1
  fetchTaskRuns()
}

/**
 * 处理分页页码变化
 */
function handleRunsCurrentChange() {
  fetchTaskRuns()
}

async function toggleEnabled() {
  try {
    if (task.value.status === 'active') {
      await taskStore.disableTask(taskId)
      ElMessage.success('任务已禁用')
    } else {
      await taskStore.enableTask(taskId)
      ElMessage.success('任务已启用')
    }
    fetchTask()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 健康检查方法
async function handleHealthCheck() {
  try {
    healthCheckLoading.value = true
    
    if (!task.value?.callbackConfig?.id) {
      ElMessage.warning('该任务没有关联的回调配置')
      return
    }
    
    // 显示正在检查的消息，不使用弹窗
    ElMessage.info(`正在对"${task.value.name}"进行健康检查...`)
    
    const result = await taskApi.healthCheck(task.value.callbackConfig.id)
    
    // 请求拦截器已经处理了响应格式，直接使用返回的数据
    const healthData = result
    
    // 格式化结果消息
    const resultLines = [
      `URL状态: ${healthData.urlHealthy ? '健康' : '不健康'}`,
      `响应时间: ${healthData.urlResponseTimeMs || 'N/A'}ms`,
      `响应代码: ${healthData.urlResponseCode || 'N/A'}`,
    ]
    
    if (healthData.cronExpr) {
      resultLines.push(`Cron表达式: ${healthData.cronExpr}`)
    }
    
    if (healthData.cronDescription) {
      resultLines.push(`执行计划: ${healthData.cronDescription}`)
      cronDescription.value = healthData.cronDescription // 更新显示
    }
    
    if (healthData.cronNextExecutionTime) {
      resultLines.push(`下次执行: ${healthData.cronNextExecutionTime}`)
    }
    
    if (healthData.endpoint) {
      resultLines.push(`服务名称: ${healthData.endpoint}`)
    }
    
    if (healthData.message) {
      resultLines.push(`检查结果: ${healthData.message}`)
    }
    
    ElMessageBox.alert(
      `<div class="health-check-result">
        <div class="health-check-summary">
          ${healthData.urlHealthy ? '✅ 健康检查通过' : '❌ 健康检查失败'}
        </div>
        <div class="health-check-items">
          ${resultLines.map(line => {
            if (line === '') return '<div class="line-break"></div>'
            return `<div class="item">${line}</div>`
          }).join('')}
        </div>
      </div>`,
      '健康检查结果',
      {
        confirmButtonText: '确定',
        dangerouslyUseHTMLString: true,
        customClass: 'health-check-result-dialog'
      }
    )
  } catch (error) {
    console.error('健康检查失败:', error)
    ElMessage.error('健康检查失败')
  } finally {
    healthCheckLoading.value = false
  }
}

async function handlePublish() {
  try {
    await ElMessageBox.confirm(`确定要立即执行任务"${task.value.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
    })
    await taskStore.publishTask(taskId)
    ElMessage.success('任务已下发执行')
  } catch (error) {
    // 用户取消
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(`确定要删除任务"${task.value.name}"吗？此操作不可恢复。`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await taskStore.deleteTask(taskId)
    ElMessage.success('删除成功')
    router.push('/tasks')
  } catch (error) {
    // 用户取消
  }
}

/**
 * 查看任务执行日志
 * @param row 任务执行记录
 */
async function viewLogs(row: any) {
  currentLogTask.value = row
  logDialogVisible.value = true
  logContent.value = ''
  
  await loadLogs()
}

/**
 * 加载日志内容
 */
async function loadLogs() {
  if (!currentLogTask.value?.id) return
  
  logLoading.value = true
  try {
    // 获取日志文件信息
    const logInfo = await taskRunApi.getLogFileInfo(currentLogTask.value.id)
    
    if (logInfo.signedUrl) {
      // 如果有签名URL，直接获取日志内容
      const response = await fetch(logInfo.signedUrl)
      if (response.ok) {
        const text = await response.text()
        logContent.value = text || '暂无日志内容'
      } else {
        logContent.value = '无法获取日志内容'
      }
    } else {
      // 尝试直接获取日志内容
      try {
        const logText = await taskRunApi.getLogs(currentLogTask.value.id)
        logContent.value = logText || '暂无日志内容'
      } catch (error) {
        console.warn('无法获取日志内容:', error)
        logContent.value = '暂无日志内容或无法访问'
      }
    }
  } catch (error) {
    console.error('加载日志失败:', error)
    logContent.value = '加载日志失败'
  } finally {
    logLoading.value = false
    // 滚动到底部
    nextTick(() => {
      if (logContainer.value) {
        logContainer.value.scrollTop = logContainer.value.scrollHeight
      }
    })
  }
}

/**
 * 刷新日志
 */
async function refreshLogs() {
  await loadLogs()
}

/**
 * 清空日志显示
 */
function clearLog() {
  logContent.value = ''
}

/**
 * 下载当前任务日志
 * @param row 任务执行记录
 */
async function downloadLog(row: any) {
  try {
    const blob = await taskRunApi.downloadLog(row.id)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `task-${row.id}-${row.status}-${new Date().getTime()}.log`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('日志下载成功')
  } catch (error) {
    console.error('下载日志失败:', error)
    ElMessage.error('下载日志失败')
  }
}

/**
 * 下载当前对话框中的日志
 */
async function downloadCurrentLog() {
  if (!currentLogTask.value) return
  
  try {
    const blob = await taskRunApi.downloadLog(currentLogTask.value.id)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `task-${currentLogTask.value.id}-${currentLogTask.value.status}-${new Date().getTime()}.log`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('日志下载成功')
  } catch (error) {
    console.error('下载日志失败:', error)
    ElMessage.error('下载日志失败')
  }
}

onMounted(() => {
  fetchTask()
  fetchTaskRuns()
})
</script>

<style scoped>
.task-detail-container {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.info-card,
.runs-card,
.action-card,
.status-card {
  margin-bottom: 24px;
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-list .el-button {
  width: 100%;
}

.stats-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
}

.stat-item:last-child {
  border-bottom: none;
}

.stat-label {
  color: #64748b;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.log-loading {
  padding: 20px;
}

.log-content {
  height: 60vh;
  display: flex;
  flex-direction: column;
}

.log-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.log-container {
  flex: 1;
  overflow: auto;
  background-color: #1e1e1e;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #374151;
}

.log-text {
  margin: 0;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  color: #e5e7eb;
  white-space: pre-wrap;
  word-wrap: break-word;
  word-break: break-all;
}

.cron-expr {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #409EFF;
  margin-bottom: 4px;
}

.cron-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.3;
}

/* 健康检查结果弹窗样式 */
:deep(.health-check-result-dialog) {
  .el-dialog__body {
    padding: 20px 24px;
  }
  
  .el-message-box__content {
    white-space: pre-line;
    line-height: 1.6;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    font-size: 13px;
  }
}
</style>
