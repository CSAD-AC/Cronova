<template>
  <div class="worker-detail-container" v-loading="loading">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <span class="page-title">{{ worker?.host }}</span>
        </template>
      </el-page-header>
    </div>
    
    <el-row :gutter="24">
      <el-col :span="16">
        <el-card class="info-card">
          <template #header>
            <div class="card-header">
              <span>基本信息</span>
              <el-tag :type="worker?.status === 'online' ? 'success' : 'danger'">
                {{ worker?.status === 'online' ? '在线' : '离线' }}
              </el-tag>
            </div>
          </template>
          
          <el-descriptions :column="2" border>
            <el-descriptions-item label="执行器ID">{{ worker?.id }}</el-descriptions-item>
            <el-descriptions-item label="主机名">
              <div class="flex items-center">
                <span>{{ worker?.host }}</span>
                <el-button link type="primary" @click="openEditHostDialog" class="ml-2">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="IP地址">{{ worker?.ip }}</el-descriptions-item>
            <el-descriptions-item label="最后心跳">{{ formatDateTime(worker?.lastHeartbeat) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(worker?.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="运行任务数">
              <el-tag size="small">{{ worker?.runningTasks || 0 }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
        
        <el-card class="resource-card">
          <template #header>
            <div class="card-header">
              <span>资源使用情况</span>
            </div>
          </template>
          
          <el-row :gutter="24">
            <el-col :span="12">
              <div class="resource-item">
                <div class="resource-label">CPU使用率</div>
                <el-progress 
                  :percentage="worker?.cpuUsage || 0" 
                  :color="getCpuColor(worker?.cpuUsage)"
                  :stroke-width="10"
                />
                <div class="resource-value">{{ worker?.cpuUsage?.toFixed(1) || '0' }}%</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="resource-item">
                <div class="resource-label">内存使用率</div>
                <el-progress 
                  :percentage="worker?.memUsage || 0" 
                  :color="getMemColor(worker?.memUsage)"
                  :stroke-width="10"
                />
                <div class="resource-value">{{ worker?.memUsage?.toFixed(1) || '0' }}%</div>
              </div>
            </el-col>
          </el-row>
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
            <el-button type="primary" @click="refreshStatus">
              <el-icon><Refresh /></el-icon>
              刷新状态
            </el-button>
            <el-button @click="viewTasks">
              <el-icon><List /></el-icon>
              运行任务
            </el-button>
            <el-button type="danger" @click="handleDelete">
              <el-icon><Delete /></el-icon>
              删除执行器
            </el-button>
          </div>
        </el-card>
        
        <el-card class="tags-card">
          <template #header>
            <div class="card-header">
              <span>标签</span>
            </div>
          </template>
          
          <div class="tags-list" v-if="worker?.tags?.length">
            <el-tag 
              v-for="tag in worker.tags" 
              :key="tag.id"
              class="worker-tag"
            >
              {{ tag.name }}
            </el-tag>
          </div>
          <el-empty v-else description="暂无标签" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, List, Delete, Edit } from '@element-plus/icons-vue'
import { useWorkerStore } from '@/stores/worker'
import { workerApi } from '@/services'
import type { FormInstance } from 'element-plus'
import { formatDateTime } from '@/utils/date'

const route = useRoute()
const router = useRouter()
const workerStore = useWorkerStore()

const workerId = route.params.id as string
const loading = ref(false)
const worker = ref<any>(null)
const editHostDialogVisible = ref(false)
const editHostFormRef = ref<FormInstance>()
const editingHost = ref(false)
const editHostForm = reactive({
  host: ''
})

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

async function fetchWorker() {
  loading.value = true
  try {
    const data = await workerApi.getById(workerId)
    worker.value = data
  } catch (error) {
    console.error('获取执行器详情失败:', error)
    ElMessage.error('获取执行器详情失败')
  } finally {
    loading.value = false
  }
}

function openEditHostDialog() {
  editHostForm.host = worker.value?.host || ''
  editHostDialogVisible.value = true
}

async function handleUpdateHost() {
  if (!editHostFormRef.value) return
  
  await editHostFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    editingHost.value = true
    try {
      await workerApi.updateHost(workerId, editHostForm.host)
      ElMessage.success('主机名修改成功')
      editHostDialogVisible.value = false
      fetchWorker()
    } catch (error) {
      console.error('修改主机名失败:', error)
      ElMessage.error('修改主机名失败')
    } finally {
      editingHost.value = false
    }
  })
}

function refreshStatus() {
  fetchWorker()
  ElMessage.success('状态已刷新')
}

function viewTasks() {
  router.push(`/tasks?workerId=${workerId}`)
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(`确定要删除执行器"${worker.value.host}"吗？`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await workerStore.deleteWorker(workerId)
    ElMessage.success('删除成功')
    router.push('/workers')
  } catch (error) {
    // 用户取消
  }
}

onMounted(() => {
  fetchWorker()
})
</script>

<style scoped>
.worker-detail-container {
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
.resource-card,
.action-card,
.tags-card {
  margin-bottom: 24px;
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.resource-item {
  padding: 16px 0;
}

.resource-label {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 12px;
}

.resource-value {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  margin-top: 8px;
  text-align: center;
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-list .el-button {
  width: 100%;
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.worker-tag {
  margin: 0;
}
</style>
