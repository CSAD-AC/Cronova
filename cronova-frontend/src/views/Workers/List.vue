<template>
  <div class="worker-list-container">
    <PageHeader title="执行器管理" subtitle="管理系统中的任务执行器节点" />

    <!-- ============ 统计卡 ============ -->
    <div class="stat-grid">
      <StatCard title="在线执行器" :value="workerStore.onlineCount" :icon="CircleCheck" tone="success" :pulse="workerStore.onlineCount > 0" />
      <StatCard title="离线执行器" :value="workerStore.offlineCount" :icon="CircleClose" tone="danger" />
      <StatCard title="执行器总数" :value="workerStore.total" :icon="Cpu" tone="primary" />
    </div>

    <FilterCard>
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
    </FilterCard>

    <div class="table-panel">
      <el-table
        :data="workerStore.workers"
        v-loading="workerStore.loading"
        style="width: 100%"
        stripe
      >
        <el-table-column label="执行器" min-width="200">
          <template #default="{ row }">
            <div class="worker-info">
              <div class="worker-avatar" :class="row.status === 'online' ? 'avatar-online' : 'avatar-offline'">
                <el-icon :size="19">
                  <Cpu />
                </el-icon>
              </div>
              <div class="worker-details">
                <el-link type="primary" class="cronova-mono" @click="$router.push(`/workers/${row.id}`)">
                  {{ row.host }}
                </el-link>
                <div class="worker-ip cronova-mono">{{ row.ip }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="CPU使用率" width="130">
          <template #default="{ row }">
            <div class="usage-cell">
              <el-progress
                :percentage="row.cpuUsage || 0"
                :color="getCpuColor(row.cpuUsage)"
                :stroke-width="8"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="内存使用率" width="130">
          <template #default="{ row }">
            <div class="usage-cell">
              <el-progress
                :percentage="row.memUsage || 0"
                :color="getMemColor(row.memUsage)"
                :stroke-width="8"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="runningTasks" label="运行任务" width="100" align="center">
          <template #default="{ row }">
            <span class="running-count cronova-mono">{{ row.runningTasks || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastHeartbeat" label="最后心跳" width="180">
          <template #default="{ row }">
            <span class="cronova-mono time-text">{{ formatDateTime(row.lastHeartbeat) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button link type="primary" @click="$router.push(`/workers/${row.id}`)">
                <el-icon class="mr-1"><View /></el-icon>详情
              </el-button>
              <el-button link type="primary" @click="handleEdit(row)">
                <el-icon class="mr-1"><Edit /></el-icon>编辑
              </el-button>
              <el-button link type="primary" @click="showTagDialog(row)">
                <el-icon class="mr-1"><PriceTag /></el-icon>标签
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
          :page-sizes="[10, 20, 50]"
          :total="workerStore.total"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 标签管理弹窗 -->
    <el-dialog v-model="tagDialogVisible" title="编辑标签" width="500px">
      <div class="tag-dialog-content">
        <div class="worker-info-header">
          <h4 class="cronova-mono">{{ currentWorker?.host }}</h4>
          <span class="worker-ip cronova-mono">{{ currentWorker?.ip }}</span>
        </div>

        <div class="tag-selection">
          <h5>选择标签</h5>
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
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import FilterCard from '@/components/FilterCard.vue'

const workerStore = useWorkerStore()

/** 标签弹窗可见性 */
const tagDialogVisible = ref(false)

/** 编辑主机名弹窗可见性 */
const editDialogVisible = ref(false)

/** 全部标签列表 */
const allTags = ref<TagVO[]>([])

/** 当前操作的执行器 */
const currentWorker = ref<WorkerVO | null>(null)

/** 已选标签 */
const selectedTags = ref<(string | number)[]>([])

/** 编辑中的主机名 */
const editingHost = ref('')

/** 分页：当前页 */
const currentPage = ref(1)

/** 分页：每页条数 */
const pageSize = ref(10)

/** 筛选表单 */
const filterForm = reactive({
  name: '',
  status: '',
})

/**
 * 触发搜索（回到第一页并拉取数据）
 */
function handleSearch(): void {
  currentPage.value = 1
  fetchWorkers()
}

/**
 * 重置筛选条件并重新搜索
 */
function handleReset(): void {
  filterForm.name = ''
  filterForm.status = ''
  handleSearch()
}

/**
 * 每页条数变化：回到第一页并重新拉取
 */
function handleSizeChange(): void {
  currentPage.value = 1
  fetchWorkers()
}

/**
 * 页码变化：重新拉取当前页数据
 */
function handleCurrentChange(): void {
  fetchWorkers()
}

/**
 * 拉取执行器列表（带分页与筛选参数）
 */
async function fetchWorkers(): Promise<void> {
  await workerStore.fetchWorkers({
    pageNum: currentPage.value,
    pageSize: pageSize.value,
    ...filterForm,
  })
}

/**
 * CPU 使用率进度条颜色：<50% 绿，<80% 琥珀，其余红
 * @param usage CPU 使用率
 */
function getCpuColor(usage: number): string {
  if (usage < 50) return 'var(--cronova-success)'
  if (usage < 80) return 'var(--cronova-running)'
  return 'var(--cronova-danger)'
}

/**
 * 内存使用率进度条颜色：<60% 绿，<85% 琥珀，其余红
 * @param usage 内存使用率
 */
function getMemColor(usage: number): string {
  if (usage < 60) return 'var(--cronova-success)'
  if (usage < 85) return 'var(--cronova-running)'
  return 'var(--cronova-danger)'
}

/**
 * 删除执行器（带确认）
 * @param row 行数据
 */
async function handleDelete(row: any): Promise<void> {
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

/**
 * 拉取全部标签列表（弹窗下拉用）
 */
async function fetchTags(): Promise<void> {
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

/**
 * 打开标签编辑弹窗
 * @param row 执行器行数据
 */
function showTagDialog(row: WorkerVO): void {
  currentWorker.value = row
  selectedTags.value = row.tags?.map(t => t.id) || []
  tagDialogVisible.value = true
}

/**
 * 保存执行器标签绑定关系（增量绑定/解绑）
 */
async function saveTags(): Promise<void> {
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

/**
 * 打开编辑主机名弹窗
 * @param row 执行器行数据
 */
function handleEdit(row: WorkerVO): void {
  currentWorker.value = row
  editingHost.value = row.host
  editDialogVisible.value = true
}

/**
 * 保存主机名修改
 */
async function saveHost(): Promise<void> {
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
  max-width: 1440px;
  margin: 0 auto;
}

/* ---------- 统计卡 ---------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 768px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
}

/* ---------- 表格面板 ---------- */
.table-panel {
  border-radius: 12px;
  border: 1px solid var(--cronova-border);
  background: var(--cronova-surface);
  box-shadow: var(--cronova-card-shadow);
  padding: 8px 16px 16px;
}

.worker-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.worker-avatar {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.avatar-online {
  background: var(--cronova-success-soft);
  color: var(--cronova-success);
}

.avatar-offline {
  background: var(--cronova-danger-soft);
  color: var(--cronova-danger);
}

.worker-details {
  line-height: 1.4;
  min-width: 0;
}

.worker-ip {
  font-size: 12px;
  color: var(--cronova-muted);
}

.usage-cell {
  padding-right: 8px;
}

.running-count {
  font-weight: 600;
  color: var(--cronova-text);
}

.time-text {
  font-size: 13px;
  color: var(--cronova-text);
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ---------- 标签弹窗 ---------- */
.tag-dialog-content {
  padding: 16px 0;
}

.worker-info-header {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--cronova-border);
}

.worker-info-header h4 {
  margin: 0 0 4px;
  color: var(--cronova-text);
  font-size: 15px;
  font-weight: 600;
}

.worker-info-header .worker-ip {
  font-size: 12px;
  color: var(--cronova-muted);
}

.tag-selection h5 {
  margin: 0 0 16px;
  color: var(--cronova-text);
  font-size: 14px;
  font-weight: 500;
}

.no-tags-hint {
  text-align: center;
  padding: 40px 0;
  color: var(--cronova-muted);
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.selected-count {
  color: var(--cronova-muted);
  font-size: 13px;
}

.action-buttons {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
</style>
