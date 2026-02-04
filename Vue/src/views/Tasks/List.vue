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
        <el-table-column prop="cronExpr" label="Cron表达式" width="140">
          <template #default="{ row }">
            {{ row.cronExpr || '-' }}
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Refresh, View, Edit, VideoPlay, Delete
} from '@element-plus/icons-vue'
import { useTaskStore } from '@/stores/task'
import { formatDateTime } from '@/utils/date'

const router = useRouter()
const taskStore = useTaskStore()

const currentPage = ref(1)
const pageSize = ref(10)

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
</style>
