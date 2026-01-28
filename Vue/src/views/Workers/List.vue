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
        <el-table-column prop="lastHeartbeat" label="最后心跳" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button-group>
              <el-tooltip content="查看详情">
                <el-button size="small" @click="$router.push(`/workers/${row.id}`)">
                  <el-icon><View /></el-icon>
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
          :page-sizes="[10, 20, 50]"
          :total="workerStore.total"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheck, CircleClose, Cpu, Search, Refresh, View, Delete
} from '@element-plus/icons-vue'
import { useWorkerStore } from '@/stores/worker'

const workerStore = useWorkerStore()

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

onMounted(() => {
  fetchWorkers()
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
</style>
