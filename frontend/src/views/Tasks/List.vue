<template>
  <div class="task-list-container">
    <PageHeader title="任务管理" subtitle="管理所有定时任务和一次性任务">
      <el-button type="primary" @click="$router.push('/tasks/create')">
        <el-icon><Plus /></el-icon>
        创建任务
      </el-button>
    </PageHeader>

    <!-- ============ 统计卡 ============ -->
    <div class="stat-grid">
      <StatCard title="任务总数" :value="taskStore.tasks.length" :icon="List" tone="primary" />
      <StatCard title="启用中" :value="activeCount" :icon="CircleCheck" tone="success" :pulse="activeCount > 0" />
      <StatCard title="定时任务" :value="cronCount" :icon="Timer" tone="accent" />
      <StatCard title="一次性任务" :value="onceCount" :icon="Flag" tone="pending" />
    </div>

    <FilterCard>
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
    </FilterCard>

    <div class="table-panel">
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
            <span class="type-chip" :class="row.executeType === 'script' ? 'chip-script' : 'chip-callback'">
              {{ row.executeType === 'script' ? '脚本' : '回调' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="scriptType" label="脚本类型" width="100">
          <template #default="{ row }">
            <template v-if="row.executeType === 'script'">
              <span class="type-chip chip-lang">{{ getScriptTypeText(row.scriptType) }}</span>
            </template>
            <span v-else class="empty-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskType" label="任务类型" width="120">
          <template #default="{ row }">
            <span class="type-chip" :class="row.taskType === 'cron' ? 'chip-cron' : 'chip-once'">
              {{ row.taskType === 'cron' ? '定时任务' : '一次性任务' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="cronExpr" label="Cron表达式" width="200">
          <template #default="{ row }">
            <div v-if="row.cronExpr && row.taskType === 'cron'" class="cron-cell">
              <div class="cron-expr sjdt-mono">{{ row.cronExpr }}</div>
              <div class="cron-desc" v-if="cronDescriptions.get(row.id)">
                {{ cronDescriptions.get(row.id) }}
              </div>
            </div>
            <span v-else class="empty-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="130">
          <template #default="{ row }">
            <el-switch
              v-if="row.taskType === 'cron'"
              :model-value="row.status === 'active'"
              @change="toggleStatus(row)"
            />
            <StatusTag v-else status="pending" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="sjdt-mono time-text">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button
                v-if="row.executeType === 'callback'"
                link
                type="primary"
                @click="handleHealthCheck(row)"
              >
                <el-icon class="mr-1"><Monitor /></el-icon>检查
              </el-button>
              <el-button v-else link type="primary" @click="$router.push(`/tasks/${row.id}`)">
                <el-icon class="mr-1"><View /></el-icon>详情
              </el-button>
              <el-button link type="primary" @click="$router.push(`/tasks/${row.id}/edit`)">
                <el-icon class="mr-1"><Edit /></el-icon>编辑
              </el-button>
              <el-button link type="success" @click="handlePublish(row)">
                <el-icon class="mr-1"><VideoPlay /></el-icon>执行
              </el-button>
              <el-button link type="danger" @click="handleDelete(row)">
                <el-icon class="mr-1"><Delete /></el-icon>删除
              </el-button>
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Refresh, View, Edit, VideoPlay, Delete, Monitor,
  List, CircleCheck, Timer, Flag
} from '@element-plus/icons-vue'
import { useTaskStore } from '@/stores/task'
import { formatDateTime } from '@/utils/date'
import { taskApi } from '@/services/task'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import FilterCard from '@/components/FilterCard.vue'

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

/** 统计：启用中的任务数 */
const activeCount = computed(() => taskStore.tasks.filter(t => t.status === 'active').length)

/** 统计：定时任务数 */
const cronCount = computed(() => taskStore.tasks.filter(t => t.taskType === 'cron').length)

/** 统计：一次性任务数 */
const onceCount = computed(() => taskStore.tasks.filter(t => t.taskType !== 'cron').length)

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
  
  if (filterForm.taskType) {
    result = result.filter(task => task.taskType === filterForm.taskType)
  }
  
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
  border: 1px solid var(--sjdt-border);
  background: var(--sjdt-surface);
  box-shadow: var(--sjdt-card-shadow);
  padding: 8px 16px 16px;
}

/* ---------- 类型徽章 ---------- */
.type-chip {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
  white-space: nowrap;
}

.chip-script {
  background: var(--sjdt-success-soft);
  color: var(--sjdt-success);
}

.chip-callback {
  background: var(--sjdt-accent-soft);
  color: var(--sjdt-accent);
}

.chip-cron {
  background: var(--sjdt-primary-soft);
  color: var(--sjdt-primary);
}

.chip-once {
  background: var(--sjdt-pending-soft);
  color: var(--sjdt-pending);
}

.chip-lang {
  background: var(--sjdt-running-soft);
  color: var(--sjdt-running);
}

.empty-text {
  color: var(--sjdt-muted);
}

/* ---------- Cron 单元格 ---------- */
.cron-cell {
  line-height: 1.4;
}

.cron-expr {
  font-size: 13px;
  color: var(--sjdt-primary);
  margin-bottom: 2px;
}

.cron-desc {
  font-size: 12px;
  color: var(--sjdt-muted);
  line-height: 1.3;
}

.time-text {
  font-size: 13px;
  color: var(--sjdt-text);
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
