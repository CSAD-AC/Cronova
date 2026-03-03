<template>
  <div class="worker-list-container">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">执行器管理</h1>
        <p class="page-subtitle">管理系统中的任务执行器节点</p>
      </div>
    </div>
    
    <el-row :gutter="24" class="stat-row">
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-icon online">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ workerStore.onlineCount }}</div>
            <div class="stat-label">在线执行器</div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-icon offline">
            <el-icon><CircleClose /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ workerStore.offlineCount }}</div>
            <div class="stat-label">离线执行器</div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-icon total">
            <el-icon><Cpu /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ workerStore.total }}</div>
            <div class="stat-label">执行器总数</div>
          </div>
        </div>
      </el-col>
    </el-row>
    
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="执行器名称">
          <el-input 
            v-model="filterForm.name" 
            placeholder="搜索执行器" 
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="在线" value="online" />
            <el-option label="离线" value="offline" />
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
        :data="workerStore.workers" 
        v-loading="workerStore.loading"
        style="width: 100%"
        stripe
      >
        <el-table-column label="执行器" min-width="200">
          <template #default="{ row }">
            <div class="worker-info">
              <el-avatar :size="40" :style="{ background: row.status === 'online' ? '#dcfce7' : '#fef2f2' }">
                <el-icon :style="{ color: row.status === 'online' ? '#10b981' : '#ef4444' }">
                  <Cpu />
                </el-icon>
              </el-avatar>
              <div class="worker-details">
                <el-link type="primary" @click="$router.push(`/workers/${row.id}`)">
                  {{ row.host }}
                </el-link>
                <div class="worker-ip">{{ row.ip }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'online' ? 'success' : 'danger'" size="small">
              {{ row.status === 'online' ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="CPU使用率" width="120">
          <template #default="{ row }">
            <el-progress 
              :percentage="row.cpuUsage || 0" 
              :color="getCpuColor(row.cpuUsage)"
              :stroke-width="8"
            />
          </template>
        </el-table-column>
        <el-table-column label="内存使用率" width="120">
          <template #default="{ row }">
            <el-progress 
              :percentage="row.memUsage || 0" 
              :color="getMemColor(row.memUsage)"
              :stroke-width="8"
            />
          </template>
        </el-table-column>
        <el-table-column prop="runningTasks" label="运行任务" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.runningTasks || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastHeartbeat" label="最后心跳" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.lastHeartbeat) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="default" @click="$router.push(`/workers/${row.id}`)">
                <el-icon class="mr-1"><View /></el-icon>详情
              </el-button>
              <el-button type="primary" size="default" @click="handleEdit(row)">
                <el-icon class="mr-1"><Edit /></el-icon>编辑
              </el-button>
              <el-button type="primary" size="default" @click="showTagDialog(row)">
                <el-icon class="mr-1"><PriceTag /></el-icon>标签
              </el-button>
              <el-button type="danger" size="default" @click="handleDelete(row)">
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
          :page-sizes="[10, 20, 50]"
          :total="workerStore.total"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 标签管理弹窗 -->
    <el-dialog v-model="tagDialogVisible" title="编辑标签" width="500px">
      <div class="tag-dialog-content">
        <div class="worker-info-header">
          <h4>{{ currentWorker?.host }}</h4>
          <span class="worker-ip">{{ currentWorker?.ip }}</span>
        </div>
        
        <div class="tag-selection">
          <h5 class="mb-2">选择标签</h5>
          <el-select
            v-model="selectedTags"
            multiple
            filterable
            placeholder="搜索并选择标签"
            style="width: 100%"
          >
            <el-option
              v-for="tag in allTags"
              :key="tag.id"
              :label="tag.name"
              :value="tag.id"
            >
              <div class="flex justify-between items-center w-full">
                <span>{{ tag.name }}</span>
                <el-tag v-if="tag.description" size="small" type="info" effect="plain">{{ tag.description }}</el-tag>
              </div>
            </el-option>
          </el-select>
          
          <div v-if="!allTags.length" class="no-tags-hint">
            <el-empty description="暂无标签" :image-size="60" />
          </div>
        </div>
      </div>
      
      <template #footer>
        <div class="dialog-footer">
          <span class="selected-count">已选择 {{ selectedTags.length }} 个标签</span>
          <div>
            <el-button @click="tagDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveTags">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 编辑主机名弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑执行器" width="400px">
      <el-form label-width="80px">
        <el-form-item label="主机名">
          <el-input v-model="editingHost" placeholder="请输入主机名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveHost">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheck, CircleClose, Cpu, Search, Refresh, View, Edit, Delete, PriceTag
} from '@element-plus/icons-vue'
import { useWorkerStore } from '@/stores/worker'
import { tagApi } from '@/services/tag'
import { workerApi } from '@/services/worker'
import { formatDateTime } from '@/utils/date'
import type { WorkerVO, TagVO } from '@/types'

const workerStore = useWorkerStore()

const tagDialogVisible = ref(false)
const editDialogVisible = ref(false)
const allTags = ref<TagVO[]>([])
const currentWorker = ref<WorkerVO | null>(null)
const selectedTags = ref<(string | number)[]>([])
const editingHost = ref('')

const currentPage = ref(1)
const pageSize = ref(10)

const filterForm = reactive({
  name: '',
  status: '',
})

function handleSearch() {
  currentPage.value = 1
  fetchWorkers()
}

function handleReset() {
  filterForm.name = ''
  filterForm.status = ''
  handleSearch()
}

function handleSizeChange() {
  currentPage.value = 1
  fetchWorkers()
}

function handleCurrentChange() {
  fetchWorkers()
}

async function fetchWorkers() {
  await workerStore.fetchWorkers({
    pageNum: currentPage.value,
    pageSize: pageSize.value,
    ...filterForm,
  })
}

function getCpuColor(usage: number) {
  if (usage < 50) return '#10b981'
  if (usage < 80) return '#f59e0b'
  return '#ef4444'
}

function getMemColor(usage: number) {
  if (usage < 60) return '#10b981'
  if (usage < 85) return '#f59e0b'
  return '#ef4444'
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除执行器"${row.host}"吗？`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await workerStore.deleteWorker(row.id)
    ElMessage.success('删除成功')
    fetchWorkers()
  } catch (error) {
    // 用户取消
  }
}

async function fetchTags() {
  try {
    const response = await tagApi.list({ size: 100 })
    if (Array.isArray(response)) {
      allTags.value = response
    } else {
      allTags.value = response.records || []
    }
  } catch (error) {
    console.error('获取标签失败:', error)
    allTags.value = []
  }
}

function showTagDialog(row: WorkerVO) {
  currentWorker.value = row
  selectedTags.value = row.tags?.map(t => t.id) || []
  tagDialogVisible.value = true
}

async function saveTags() {
  if (!currentWorker.value) return
  
  try {
    const oldTagIds = currentWorker.value.tags?.map(t => t.id) || []
    const newSelectedTags = [...selectedTags.value]
    
    const addTagIds = newSelectedTags.filter(id => !oldTagIds.includes(id as string))
    const removeTagIds = oldTagIds.filter(id => !newSelectedTags.includes(id as string))
    
    for (const tagId of removeTagIds) {
      await tagApi.unbindWorker(tagId as number, currentWorker.value!.id)
    }
    
    if (addTagIds.length > 0) {
      for (const tagId of addTagIds) {
        await tagApi.bindWorker(tagId as number, currentWorker.value!.id)
      }
    }
    
    ElMessage.success('保存成功')
    tagDialogVisible.value = false
    fetchWorkers()
  } catch (error) {
    console.error('保存标签失败:', error)
    ElMessage.error('保存失败')
  }
}

function handleEdit(row: WorkerVO) {
  currentWorker.value = row
  editingHost.value = row.host
  editDialogVisible.value = true
}

async function saveHost() {
  if (!currentWorker.value || !editingHost.value.trim()) {
    ElMessage.warning('请输入主机名')
    return
  }
  
  try {
    await workerApi.updateHost(currentWorker.value.id, editingHost.value.trim())
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    fetchWorkers()
  } catch (error) {
    console.error('保存主机名失败:', error)
    ElMessage.error('保存失败')
  }
}

onMounted(() => {
  fetchWorkers()
  fetchTags()
})
</script>

<style scoped>
.worker-list-container {
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

.stat-row {
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

.stat-icon.online {
  background: #dcfce7;
  color: #10b981;
}

.stat-icon.offline {
  background: #fef2f2;
  color: #ef4444;
}

.stat-icon.total {
  background: #dbeafe;
  color: #3b82f6;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #1e293b;
}

.stat-label {
  font-size: 14px;
  color: #64748b;
}

.filter-card,
.table-card {
  border-radius: 12px;
  margin-bottom: 24px;
}

.worker-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.worker-details {
  line-height: 1.4;
}

.worker-ip {
  font-size: 12px;
  color: #64748b;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

/* 标签弹窗样式 */
.tag-dialog-content {
  padding: 16px 0;
}

.worker-info-header {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.worker-info-header h4 {
  margin: 0 0 4px;
  color: #1e293b;
  font-size: 16px;
  font-weight: 600;
}

.worker-info-header .worker-ip {
  font-size: 12px;
  color: #64748b;
}

.tag-selection h5 {
  margin: 0 0 16px;
  color: #374151;
  font-size: 14px;
  font-weight: 500;
}

.no-tags-hint {
  text-align: center;
  padding: 40px 0;
  color: #9ca3af;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.selected-count {
  color: #6b7280;
  font-size: 14px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.action-buttons .el-button {
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
</style>
