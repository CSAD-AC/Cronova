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
            <el-descriptions-item v-if="task?.executeType === 'script'" label="脚本类型">
              {{ getScriptTypeText(task?.scriptType || task?.type) || '-' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="task?.executeType === 'script' && scriptName" label="脚本文件">
              {{ scriptName }}
            </el-descriptions-item>
            <el-descriptions-item label="Cron表达式">
              <div v-if="task?.cronExpr">
                <div class="cron-expr">{{ task.cronExpr }}</div>
                <div class="cron-desc" v-if="cronDescription">{{ cronDescription }}</div>
              </div>
              <span v-else>无</span>
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
            <el-divider direction="vertical" />
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
                  <el-checkbox-group v-model="selectedLogLevels" @change="handleFilterChange">
                    <div class="log-level-filter">
                      <el-checkbox value="DEBUG">调试信息</el-checkbox>
                      <el-checkbox value="INFO">一般信息</el-checkbox>
                      <el-checkbox value="WARNING">警告信息</el-checkbox>
                      <el-checkbox value="ERROR">错误信息</el-checkbox>
                      <el-checkbox value="CRITICAL">严重错误</el-checkbox>
                    </div>
                  </el-checkbox-group>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-divider direction="vertical" />
            <el-button size="small" @click="downloadCurrentLog">
              <el-icon><Download /></el-icon>
              下载
            </el-button>
          </div>
          <div ref="logContainer" class="log-container" :class="{ 'fullscreen': isFullscreen }">
            <pre class="log-text" v-html="displayLogContent || logContentHtml || '暂无日志内容'"></pre>
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
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Edit, VideoPlay, Switch, Delete, Refresh, Document, Download, Monitor, 
  FullScreen, Filter, ArrowDown
} from '@element-plus/icons-vue'
import { useTaskStore } from '@/stores/task'
import { taskRunApi } from '@/services'
import { taskApi } from '@/services/task'
import { scriptApi } from '@/services/script'
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
const scriptName = ref('')

// 执行历史相关数据
const runsLoading = ref(false)
const runsCurrentPage = ref(1)
const runsPageSize = ref(10)
const runsTotal = ref(0)

// 日志相关数据
const logDialogVisible = ref(false)
const logLoading = ref(false)
const logContent = ref('')
const logContentHtml = ref('')  // 处理后的HTML内容
const currentLogTask = ref<any>(null)
const logContainer = ref<HTMLDivElement>()

// 全屏和筛选功能相关数据
const isFullscreen = ref(false)
const selectedLogLevels = ref<string[]>(['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'])
const allLogLevels = ['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL']
const filteredLogContent = ref('')
const displayLogContent = ref('') // 统一的显示内容

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

function getScriptTypeText(type: string | undefined) {
  const map: Record<string, string> = {
    python: 'Python',
    java: 'Java', 
    go: 'Go'
  }
  return map[type || ''] || type || ''
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
      
      // 构建带颜色的HTML
      return `<span style="color: ${color};">${timestamp}</span>` +
             ` | <span style="color: ${color}; font-weight: bold;">${level}</span>` +
             ` | <span style="color: #B0BEC5;">${location}</span>` +
             ` - <span style="color: ${color};">${message}</span>`
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
    // 添加ESC键监听
    addKeyboardListener()
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
  // 只在全屏模式下响应ESC键
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
    // 进入全屏模式，添加ESC键监听
    addKeyboardListener()
  } else if (!newValue && oldValue) {
    // 退出全屏模式，移除ESC键监听
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
  const originalContent = logContent.value || ''
  
  // 如果没有筛选条件或选择了全部级别，返回处理后的完整内容
  if (selectedLogLevels.value.length === 0 || selectedLogLevels.value.length === allLogLevels.length) {
    return logContentHtml.value || '暂无日志内容'
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
 * 全选日志级别
 */
function selectAllLevels() {
  selectedLogLevels.value = [...allLogLevels]
  filteredLogContent.value = filterLogs()
}

/**
 * 清空所有日志级别
 */
function clearAllLevels() {
  selectedLogLevels.value = []
  filteredLogContent.value = ''
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



async function fetchTask() {
  loading.value = true
  try {
    task.value = await taskStore.fetchTaskById(taskId)
    
    // 如果是脚本任务且有scriptId，获取脚本名称
    if (task.value?.executeType === 'script' && task.value?.scriptId) {
      try {
        const scriptDetail = await scriptApi.getById(task.value.scriptId)
        if (scriptDetail?.name) {
          scriptName.value = scriptDetail.name
        }
      } catch (error) {
        console.warn('获取脚本详情失败:', error)
      }
    }
    
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
  logContentHtml.value = ''
  
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
        logContentHtml.value = processLogContent(text || '暂无日志内容')
      } else {
        const errorText = '无法获取日志内容'
        logContent.value = errorText
        logContentHtml.value = processLogContent(errorText)
      }
    } else {
      // 尝试直接获取日志内容
      try {
        const logText = await taskRunApi.getLogs(currentLogTask.value.id)
        logContent.value = logText || '暂无日志内容'
        logContentHtml.value = processLogContent(logText || '暂无日志内容')
      } catch (error) {
        console.warn('无法获取日志内容:', error)
        const warnText = '暂无日志内容或无法访问'
        logContent.value = warnText
        logContentHtml.value = processLogContent(warnText)
      }
    }
  } catch (error) {
    console.error('加载日志失败:', error)
    const failText = '加载日志失败'
    logContent.value = failText
    logContentHtml.value = processLogContent(failText)
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
  addKeyboardListener()
})

onUnmounted(() => {
  removeKeyboardListener()
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
  font-size: 20px;
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

/* 缩小侧栏操作按钮样式，保持与列表/表单一致 */
.action-list .el-button,
:deep(.task-detail-container) :deep(.el-button) {
  font-size: 13px;
  padding: 6px 10px;
  height: 34px;
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
  background-color: #000000 !important;
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

.log-level-filter .el-checkbox__label {
  color: #e5e7eb;
}

/* 下拉菜单样式优化 */
:deep(.el-dropdown-menu) {
  background-color: #1f2937;
  border: 1px solid #374151;
}

:deep(.el-dropdown-menu__item) {
  color: #e5e7eb;
  padding: 0;
}

:deep(.el-dropdown-menu__item:hover) {
  background-color: #374151;
}

:deep(.el-checkbox__inner) {
  background-color: #374151;
  border-color: #4b5563;
}

:deep(.el-checkbox__inner:hover) {
  border-color: #60a5fa;
}

:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: #3b82f6;
  border-color: #3b82f6;
}

:deep(.el-checkbox__input.is-checked .el-checkbox__inner::after) {
  border-color: #ffffff;
}

.log-container {
  flex: 1;
  overflow: auto;
  background-color: #000000;  /* 纯黑色背景 */
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #333333;
  box-shadow: inset 0 0 10px rgba(0, 0, 0, 0.5);  /* 内部阴影效果 */
}

.log-text {
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;  /* 经典终端字体 */
  font-size: 13px;
  line-height: 1.4;  /* 稍微紧凑的行距 */
  color: #FFFFFF;  /* 白色文字 */
  white-space: pre-wrap;
  word-wrap: break-word;
  word-break: break-all;
  background: transparent;
}

/* 终端风格增强 */
.log-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.02) 0%, transparent 70%);
  pointer-events: none;
  border-radius: 8px;
}

/* 滚动条样式 */
.log-container::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.log-container::-webkit-scrollbar-track {
  background: #1a1a1a;
  border-radius: 4px;
}

.log-container::-webkit-scrollbar-thumb {
  background: #444444;
  border-radius: 4px;
}

.log-container::-webkit-scrollbar-thumb:hover {
  background: #555555;
}

.cron-expr {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #409EFF;
  margin-bottom: 4px;
}

.cron-desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.4;
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
