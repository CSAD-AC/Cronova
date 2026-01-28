<template>
  <div class="task-list-container">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">任务管理</h1>
        <p class="page-subtitle">管理所有定时任务和一次性任务</p>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="$router.push('/tasks/create')">
          <el-icon><Plus /></el-icon>
          创建任务
        </el-button>
      </div>
    </div>
    
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="任务名称">
          <el-input 
            v-model="filterForm.name" 
            placeholder="搜索任务名称" 
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="filterForm.taskType" placeholder="全部" clearable style="width: 140px">
            <el-option label="定时任务" value="cron" />
            <el-option label="一次性任务" value="once" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="paused" />
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
        :data="getPaginatedData()" 
        v-loading="taskStore.loading"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="name" label="任务名称" min-width="120">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/tasks/${row.id}`)">
              {{ row.name }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="任务描述" min-width="200">
          <template #default="{ row }">
            <span class="description-text">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="executeType" label="执行类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.executeType === 'script' ? 'success' : 'warning'" size="small">
              {{ row.executeType === 'script' ? '脚本' : '回调' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scriptType" label="脚本类型" width="100">
          <template #default="{ row }">
            <template v-if="row.executeType === 'script'">
              <el-tag size="small" :type="getScriptTypeTag(row.scriptType)">
                {{ getScriptTypeText(row.scriptType) }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskType" label="任务类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.taskType === 'cron' ? 'primary' : 'info'" size="small">
              {{ row.taskType === 'cron' ? '定时任务' : '一次性任务' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cronExpr" label="Cron表达式" width="180">
          <template #default="{ row }">
            <div v-if="row.cronExpr">
              <div class="cron-expr">{{ row.cronExpr }}</div>
              <div class="cron-desc" v-if="row.cronDescription">{{ row.cronDescription }}</div>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="150">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'active'"
              active-text="活跃"
              inactive-text="暂停"
              @change="toggleEnabled(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button-group>
              <el-tooltip content="查看详情">
                <el-button size="small" @click="$router.push(`/tasks/${row.id}`)">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="编辑">
                <el-button size="small" @click="$router.push(`/tasks/${row.id}/edit`)">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="健康检查" v-if="row.executeType === 'callback'">
                <el-button 
                  size="small" 
                  type="info" 
                  :loading="healthCheckLoading[row.id]"
                  @click="handleHealthCheck(row)"
                >
                  <el-icon><Monitor /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="立即执行">
                <el-button size="small" type="success" @click="handlePublish(row)">
                  <el-icon><VideoPlay /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button size="small" type="danger" @click="handleDelete(row)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredTasks.length"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Refresh, View, Edit, VideoPlay, Delete, Monitor
} from '@element-plus/icons-vue'
import { useTaskStore } from '@/stores/task'
import { taskApi } from '@/services/task'

const router = useRouter()
const taskStore = useTaskStore()

const currentPage = ref(1)
const pageSize = ref(10)

// 存储原始任务数据
const allTasks = ref<any[]>([])
// 筛选后的任务数据
const filteredTasks = ref<any[]>([])
// 健康检查加载状态
const healthCheckLoading = ref<Record<string, boolean>>({})

const filterForm = reactive({
  name: '',
  taskType: '',
  status: '',
})

/**
 * 获取所有任务数据（不分页）
 */
async function fetchAllTasks() {
  try {
    // 获取所有任务数据（设置较大的pageSize）
    const response = await taskStore.fetchTasks({
      pageNum: 1,
      pageSize: 9999, // 获取尽可能多的数据
    })
    
    allTasks.value = [...taskStore.tasks]
    
    // 为所有有Cron表达式的任务自动获取中文描述
    await fetchCronDescriptionsForTasks()
    
    applyFilters()
  } catch (error) {
    console.error('获取任务数据失败:', error)
    ElMessage.error('获取任务数据失败')
  }
}

/**
 * 为任务列表中的所有Cron任务获取中文描述
 */
async function fetchCronDescriptionsForTasks() {
  const tasksWithCron = allTasks.value.filter(task => 
    task.cronExpr && task.taskType === 'cron' && !task.cronDescription
  )
  
  // 批量获取，避免过多并发请求
  const batchSize = 5
  for (let i = 0; i < tasksWithCron.length; i += batchSize) {
    const batch = tasksWithCron.slice(i, i + batchSize)
    await Promise.allSettled(
      batch.map(async (task) => {
        try {
          const result = await taskApi.getCronDescription(task.cronExpr)
          if (result && result.description) {
            task.cronDescription = result.description
          }
        } catch (error) {
          console.warn(`获取任务"${task.name}"的Cron描述失败:`, error)
        }
      })
    )
    
    // 添加小延迟避免请求过快
    if (i + batchSize < tasksWithCron.length) {
      await new Promise(resolve => setTimeout(resolve, 100))
    }
  }
}

/**
 * 应用筛选条件
 */
function applyFilters() {
  let filtered = [...allTasks.value]
  
  // 任务名称筛选（模糊匹配）
  if (filterForm.name.trim()) {
    const searchName = filterForm.name.trim().toLowerCase()
    filtered = filtered.filter(task => 
      task.name.toLowerCase().includes(searchName) ||
      (task.description && task.description.toLowerCase().includes(searchName))
    )
  }
  
  // 任务类型筛选
  if (filterForm.taskType) {
    filtered = filtered.filter(task => task.taskType === filterForm.taskType)
  }
  
  // 状态筛选
  if (filterForm.status) {
    if (filterForm.status === 'active') {
      filtered = filtered.filter(task => task.status === 'active')
    } else if (filterForm.status === 'paused') {
      filtered = filtered.filter(task => task.status === 'paused')
    }
  }
  
  filteredTasks.value = filtered
}

/**
 * 搜索处理（前端筛选）
 */
function handleSearch() {
  currentPage.value = 1
  applyFilters()
}

/**
 * 重置筛选条件
 */
function handleReset() {
  filterForm.name = ''
  filterForm.taskType = ''
  filterForm.status = ''
  handleSearch()
}

function handleSizeChange() {
  currentPage.value = 1
  // 不需要重新获取数据，只需要重新分页
}

function handleCurrentChange() {
  // 不需要重新获取数据，只需要重新分页
}

/**
 * 获取分页数据
 */
function getPaginatedData() {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredTasks.value.slice(start, end)
}

function getScriptTypeTag(type: string): string {
  const map: Record<string, string> = {
    python: 'success',
    java: 'danger',
    shell: 'info',
    go: 'warning',
  }
  return map[type?.toLowerCase()] || 'info'
}

function getScriptTypeText(type: string): string {
  const map: Record<string, string> = {
    python: 'Python',
    java: 'Java',
    go: 'Go',
  }
  return map[type?.toLowerCase()] || type || '-'
}

// 原来的fetchTasks函数已不再使用，改为fetchAllTasks

async function toggleEnabled(row: any) {
  try {
    if (row.status === 'active') {
      await taskStore.disableTask(row.id)
      ElMessage.success('任务已禁用')
    } else {
      await taskStore.enableTask(row.id)
      ElMessage.success('任务已启用')
    }
    fetchAllTasks()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 健康检查方法
async function handleHealthCheck(row: any) {
  try {
    healthCheckLoading.value[row.id] = true
    
    // 由于健康检查API需要configId，而任务列表中没有这个信息，
    // 我们需要先获取任务详情来获取configId
    const taskDetail = await taskStore.fetchTaskById(row.id)
    
    if (!taskDetail.callbackConfig || !taskDetail.callbackConfig.id) {
      ElMessage.warning('该任务没有关联的回调配置')
      return
    }
    
    // 显示正在检查的消息，不使用弹窗
    ElMessage.info(`正在对"${row.name}"进行健康检查...`)
    
    const result = await taskApi.healthCheck(taskDetail.callbackConfig.id)
    
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
    
    const message = resultLines.join('\n')
    
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
    
    // 如果健康检查成功，更新任务的Cron描述显示
    if (healthData.cronDescription) {
      // 更新当前任务列表中的cron描述
      const taskIndex = allTasks.value.findIndex(task => task.id === row.id)
      if (taskIndex !== -1) {
        allTasks.value[taskIndex].cronDescription = healthData.cronDescription
        applyFilters() // 重新应用筛选条件以更新显示
      }
    }
  } catch (error) {
    console.error('健康检查失败:', error)
    ElMessage.error('健康检查失败')
  } finally {
    healthCheckLoading.value[row.id] = false
  }
}

async function handlePublish(row: any) {
  try {
    await ElMessageBox.confirm(`确定要立即执行任务"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
    })
    await taskStore.publishTask(row.id)
    ElMessage.success('任务已下发执行')
    // 立即执行不需要刷新列表
  } catch (error) {
    // 用户取消
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除任务"${row.name}"吗？此操作不可恢复。`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await taskStore.deleteTask(row.id)
    ElMessage.success('删除成功')
    fetchAllTasks()
  } catch (error) {
    // 用户取消
  }
}

onMounted(() => {
  fetchAllTasks()
})
</script>

<style scoped>
.task-list-container {
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

.filter-card {
  margin-bottom: 24px;
  border-radius: 12px;
}

.table-card {
  border-radius: 12px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.description-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
  max-height: 2.6em;
}

.cron-expr {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #409EFF;
  margin-bottom: 2px;
}

.cron-desc {
  font-size: 11px;
  color: #909399;
  line-height: 1.2;
}

/* 健康检查结果弹窗样式 */
:deep(.health-check-result-dialog) {
  .el-dialog__body {
    padding: 20px 24px;
    min-height: 300px;
    display: flex;
    flex-direction: column;
  }
  
  .el-message-box__content {
    flex: 1;
    overflow-y: auto;
  }
  
  .health-check-result {
    .health-check-summary {
      font-size: 16px;
      font-weight: 600;
      margin-bottom: 16px;
      text-align: center;
      padding: 12px;
      border-radius: 8px;
      background-color: #f8f9fa;
    }
    
    .health-check-items {
      .item {
        padding: 6px 0;
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
        font-size: 13px;
        line-height: 1.4;
        border-bottom: 1px solid #f0f0f0;
        
        &:last-child {
          border-bottom: none;
        }
      }
      
      .line-break {
        height: 8px;
      }
    }
  }
}
</style>
