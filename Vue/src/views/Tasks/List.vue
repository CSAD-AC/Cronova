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
        :data="paginatedTasks" 
        v-loading="taskStore.loading"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="name" label="任务名称" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/tasks/${row.id}`)">
              {{ row.name }}
            </el-link>
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
        <el-table-column prop="cronExpr" label="Cron表达式" width="200">
          <template #default="{ row }">
            <div v-if="row.cronExpr && row.taskType === 'cron'">
              <div class="cron-expr">{{ row.cronExpr }}</div>
              <div class="cron-desc" v-if="cronDescriptions.get(row.id)">
                {{ cronDescriptions.get(row.id) }}
              </div>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="150">
          <template #default="{ row }">
            <el-switch
              v-if="row.taskType === 'cron'"
              :model-value="row.status === 'active'"
              active-text="启用"
              inactive-text="禁用"
              @change="toggleStatus(row)"
            />
            <el-tag v-else type="info" size="small">非定时任务</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <div class="button-row">
                <el-button type="primary" size="default" @click="$router.push(`/tasks/${row.id}`)">
                  <el-icon class="mr-1"><View /></el-icon>详情
                </el-button>
                <el-button type="primary" size="default" @click="$router.push(`/tasks/${row.id}/edit`)">
                  <el-icon class="mr-1"><Edit /></el-icon>编辑
                </el-button>
              </div>
              <div class="button-row">
                <el-button type="success" size="default" @click="handlePublish(row)">
                  <el-icon class="mr-1"><VideoPlay /></el-icon>执行
                </el-button>
                <el-button type="danger" size="default" @click="handleDelete(row)">
                  <el-icon class="mr-1"><Delete /></el-icon>删除
                </el-button>
              </div>
            </div>
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

<style scoped>
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
</style>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Refresh, View, Edit, VideoPlay, Delete, Monitor
} from '@element-plus/icons-vue'
import { useTaskStore } from '@/stores/task'
import { formatDateTime } from '@/utils/date'
import { taskApi } from '@/services/task'

const router = useRouter()
const taskStore = useTaskStore()

const currentPage = ref(1)
const pageSize = ref(10)
const cronDescriptions = ref<Map<string, string>>(new Map()) // 存储任务ID到cron描述的映射

const filterForm = reactive({
  name: '',
  taskType: '',
  status: '',
})

const filteredTasks = computed(() => {
  let result = [...taskStore.tasks]
  
  // 任务名称筛选（模糊匹配）
  if (filterForm.name.trim()) {
    const searchName = filterForm.name.trim().toLowerCase()
    result = result.filter(task => 
      task.name.toLowerCase().includes(searchName) ||
      (task.description && task.description.toLowerCase().includes(searchName))
    )
  }
  
  // 任务类型筛选
  if (filterForm.taskType) {
    result = result.filter(task => task.taskType === filterForm.taskType)
  }
  
  // 状态筛选
  if (filterForm.status) {
    result = result.filter(task => task.status === filterForm.status)
  }
  
  return result
})

const paginatedTasks = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredTasks.value.slice(start, end)
})

function handleSearch() {
  currentPage.value = 1
  // 筛选在computed属性中自动进行，无需重新获取数据
}

function handleReset() {
  filterForm.name = ''
  filterForm.taskType = ''
  filterForm.status = ''
  currentPage.value = 1
}

function handleSizeChange() {
  currentPage.value = 1
  // 不需要重新获取数据，只需要重新分页
}

function handleCurrentChange() {
  // 不需要重新获取数据，只需要重新分页
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

async function fetchTasks() {
  await taskStore.fetchTasks({
    pageNum: 1,
    pageSize: 9999, // 获取所有数据进行本地筛选
  })
  // 获取所有cron任务的中文描述
  await fetchCronDescriptions()
}

/**
 * 获取Cron表达式的中文描述
 */
async function fetchCronDescriptions() {
  try {
    const tasks = taskStore.tasks
    const cronTasks = tasks.filter(task => 
      task.taskType === 'cron' && 
      task.cronExpr && 
      !cronDescriptions.value.has(task.id)
    )

    // 并发获取所有cron任务的描述
    const promises = cronTasks.map(async (task) => {
      try {
        const result = await taskApi.getCronDescription(task.cronExpr || '')
        if (result?.description) {
          cronDescriptions.value.set(task.id, result.description)
        }
      } catch (error) {
        console.warn(`获取任务 ${task.id} 的cron描述失败:`, error)
        // 不显示错误，只是静默失败
      }
    })

    await Promise.all(promises)
  } catch (error) {
    console.warn('批量获取cron描述失败:', error)
    // 不显示错误，只是静默失败
  }
}

async function toggleStatus(row: any) {
  try {
    if (row.status === 'active') {
      await taskStore.disableTask(row.id)
      ElMessage.success('任务已禁用')
    } else {
      await taskStore.enableTask(row.id)
      ElMessage.success('任务已启用')
    }
    fetchTasks()
  } catch (error) {
    ElMessage.error('操作失败')
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
    fetchTasks()
  } catch (error) {
    // 用户取消
  }
}

/**
 * 处理HTTP回调任务的健康检查
 * @param row 任务行数据
 */
async function handleHealthCheck(row: any) {
  try {
    if (!row.callbackConfig?.id) {
      ElMessage.warning('该任务没有回调配置，无法进行健康检查')
      return
    }

    const result = await taskApi.healthCheck(row.callbackConfig.id)

    if (result.urlResponseCode === 200 && result.urlHealthy) {
      // 健康检查通过，显示详细信息弹窗
      const detailMessage = [
                 `<span style="color: #67C23A; font-size: 16px;">✓ 回调任务健康检查通过</span>`,
                 '',
                 `<strong>检查时间：</strong>${result.checkTime}`,
                 `<strong>回调地址：</strong>${result.callbackUrl}`,
                 `<strong>响应状态：</strong>${result.urlResponseCode}`,
                 `<strong>响应时间：</strong>${result.urlResponseTimeMs}ms`
               ]
      
      if (result.endpoint) {
        detailMessage.push(`<strong>服务名称：</strong>${result.endpoint}`)
      }
      
      if (result.message) {
        detailMessage.push(`<strong>检查消息：</strong>${result.message}`)
      }
      
      if (result.cronExpr) {
        detailMessage.push(`<strong>Cron表达式：</strong>${result.cronExpr}`)
      }
      
      if (result.cronDescription) {
        detailMessage.push(`<strong>Cron描述：</strong>${result.cronDescription}`)
      }
      
      if (result.cronNextExecutionTime) {
        detailMessage.push(`<strong>下次执行：</strong>${result.cronNextExecutionTime}`)
      }
      
      ElMessageBox.alert(detailMessage.join('<br>'), '健康检查结果', {
                confirmButtonText: '确定',
                dangerouslyUseHTMLString: true,
              })
    } else {
         let errorMessage = ''
         
         if (result.urlResponseCode !== 200) {
           errorMessage = `URL服务不可用，请检查回调地址是否正确`
         } else if (!result.urlHealthy) {
           errorMessage = `秘钥校验失败: ${result.message || '目标回调地址[秘钥校验]未通过'}`
         } else {
           errorMessage = result.message || '健康检查未通过'
         }
         
         ElMessageBox.alert(
           errorMessage,
           '健康检查结果',
           {
             confirmButtonText: '确定',
             type: 'error',
           }
         )
       }
  } catch (error: any) {
    ElMessage.error(error?.message || '健康检查失败')
  }
}

onMounted(() => {
  fetchTasks()
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
  font-size: 20px;
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

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.button-row {
  display: flex;
  gap: 8px;
}

.button-row .el-button {
  flex: 1;
  font-weight: 500;
  min-width: 90px;
}

:deep(.el-table) {
  font-size: 14px;
}

:deep(.el-table .cell) {
  font-size: 14px;
}

:deep(.el-tag) {
  font-size: 13px;
}

:deep(.el-link) {
  font-size: 14px;
}

.button-row .el-button {
  font-size: 13px;
  padding: 6px 10px;
  height: 34px;
  line-height: 20px;
}

:deep(.el-button) {
  font-size: 13px;
  padding: 6px 10px;
  height: 34px;
}

.cron-desc {
  font-size: 13px;
}
</style>
