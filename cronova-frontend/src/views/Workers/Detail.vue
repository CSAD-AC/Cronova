<template>
  <div class="worker-detail-container" v-loading="loading">
    <PageHeader :title="worker?.host || '执行器详情'" :subtitle="worker?.ip ? `${worker.ip} · 执行器节点` : '执行器节点详情'">
      <el-button @click="$router.back()">
        <el-icon class="mr-1"><ArrowLeft /></el-icon>
        返回
      </el-button>
    </PageHeader>

    <div class="detail-grid">
      <!-- 左列：基本信息 + 资源 -->
      <div class="detail-main">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">基本信息</span>
            <StatusTag :status="worker?.status || 'offline'" />
          </div>

          <div class="desc-grid">
            <div class="desc-item">
              <div class="desc-label">执行器ID</div>
              <div class="desc-value sjdt-mono">{{ worker?.id }}</div>
            </div>
            <div class="desc-item">
              <div class="desc-label">主机名</div>
              <div class="desc-value desc-with-action">
                <span>{{ worker?.host }}</span>
                <el-button link type="primary" size="small" @click="openEditHostDialog">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </div>
            </div>
            <div class="desc-item">
              <div class="desc-label">IP地址</div>
              <div class="desc-value sjdt-mono">{{ worker?.ip }}</div>
            </div>
            <div class="desc-item">
              <div class="desc-label">运行任务数</div>
              <div class="desc-value sjdt-mono">{{ worker?.runningTasks || 0 }}</div>
            </div>
            <div class="desc-item">
              <div class="desc-label">最后心跳</div>
              <div class="desc-value sjdt-mono">{{ formatDateTime(worker?.lastHeartbeat) }}</div>
            </div>
            <div class="desc-item">
              <div class="desc-label">创建时间</div>
              <div class="desc-value sjdt-mono">{{ formatDateTime(worker?.createTime) }}</div>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">资源使用情况</span>
          </div>

          <div class="resource-grid">
            <div class="resource-item">
              <div class="resource-head">
                <span class="resource-label">CPU使用率</span>
                <span class="resource-num sjdt-mono" :style="{ color: getCpuColor(worker?.cpuUsage || 0) }">
                  {{ worker?.cpuUsage?.toFixed(1) || '0.0' }}%
                </span>
              </div>
              <el-progress
                :percentage="worker?.cpuUsage || 0"
                :color="getCpuColor(worker?.cpuUsage)"
                :stroke-width="8"
                :show-text="false"
              />
            </div>
            <div class="resource-item">
              <div class="resource-head">
                <span class="resource-label">内存使用率</span>
                <span class="resource-num sjdt-mono" :style="{ color: getMemColor(worker?.memUsage || 0) }">
                  {{ worker?.memUsage?.toFixed(1) || '0.0' }}%
                </span>
              </div>
              <el-progress
                :percentage="worker?.memUsage || 0"
                :color="getMemColor(worker?.memUsage)"
                :stroke-width="8"
                :show-text="false"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- 右列：操作 + 标签 -->
      <div class="detail-side">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">操作</span>
          </div>

          <div class="action-list">
            <el-button type="primary" @click="refreshStatus">
              <el-icon><Refresh /></el-icon>
              刷新状态
            </el-button>
            <el-button @click="viewTasks">
              <el-icon><List /></el-icon>
              运行任务
            </el-button>
            <el-button type="danger" plain @click="handleDelete">
              <el-icon><Delete /></el-icon>
              删除执行器
            </el-button>
          </div>
        </div>

        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">标签</span>
          </div>

          <div class="tags-list" v-if="worker?.tags?.length">
            <span v-for="tag in worker.tags" :key="tag.id" class="tag-chip">
              {{ tag.name }}
            </span>
          </div>
          <el-empty v-else description="暂无标签" :image-size="60" />
        </div>
      </div>
    </div>

    <!-- 编辑主机名弹窗 -->
    <el-dialog v-model="editHostDialogVisible" title="编辑主机名" width="400px">
      <el-form ref="editHostFormRef" :model="editHostForm" :rules="hostRules" label-width="80px">
        <el-form-item label="主机名" prop="host">
          <el-input v-model="editHostForm.host" placeholder="请输入主机名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editHostDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editingHost" @click="handleUpdateHost">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, List, Delete, Edit, ArrowLeft } from '@element-plus/icons-vue'
import { useWorkerStore } from '@/stores/worker'
import { workerApi } from '@/services'
import type { FormInstance } from 'element-plus'
import { formatDateTime } from '@/utils/date'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const workerStore = useWorkerStore()

/** 执行器ID（路由参数） */
const workerId = route.params.id as string

/** 页面加载状态 */
const loading = ref(false)

/** 执行器详情数据 */
const worker = ref<any>(null)

/** 编辑主机名弹窗可见性 */
const editHostDialogVisible = ref(false)

/** 编辑主机名表单引用 */
const editHostFormRef = ref<FormInstance>()

/** 主机名保存中 */
const editingHost = ref(false)

/** 编辑主机名表单数据 */
const editHostForm = reactive({
  host: ''
})

/** 主机名表单校验规则 */
const hostRules = {
  host: [{ required: true, message: '请输入主机名', trigger: 'blur' }]
}

/**
 * CPU 使用率颜色：<50% 绿，<80% 琥珀，其余红
 * @param usage CPU 使用率
 */
function getCpuColor(usage: number): string {
  if (usage < 50) return 'var(--sjdt-success)'
  if (usage < 80) return 'var(--sjdt-running)'
  return 'var(--sjdt-danger)'
}

/**
 * 内存使用率颜色：<60% 绿，<85% 琥珀，其余红
 * @param usage 内存使用率
 */
function getMemColor(usage: number): string {
  if (usage < 60) return 'var(--sjdt-success)'
  if (usage < 85) return 'var(--sjdt-running)'
  return 'var(--sjdt-danger)'
}

/**
 * 拉取执行器详情
 */
async function fetchWorker(): Promise<void> {
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

/**
 * 打开编辑主机名弹窗
 */
function openEditHostDialog(): void {
  editHostForm.host = worker.value?.host || ''
  editHostDialogVisible.value = true
}

/**
 * 保存主机名修改
 */
async function handleUpdateHost(): Promise<void> {
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

/**
 * 刷新执行器状态
 */
function refreshStatus(): void {
  fetchWorker()
  ElMessage.success('状态已刷新')
}

/**
 * 跳转到该执行器运行的任务列表
 */
function viewTasks(): void {
  router.push(`/tasks?workerId=${workerId}`)
}

/**
 * 删除执行器（带确认）
 */
async function handleDelete(): Promise<void> {
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
  max-width: 1440px;
  margin: 0 auto;
}

/* ---------- 布局网格 ---------- */
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}

@media (max-width: 1024px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

.detail-main,
.detail-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

/* ---------- 面板 ---------- */
.panel {
  border-radius: 12px;
  border: 1px solid var(--sjdt-border);
  background: var(--sjdt-surface);
  box-shadow: var(--sjdt-card-shadow);
  padding: 18px 20px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--sjdt-text);
}

/* ---------- 描述网格 ---------- */
.desc-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px 24px;
}

.desc-label {
  font-size: 12px;
  color: var(--sjdt-muted);
  margin-bottom: 4px;
}

.desc-value {
  font-size: 14px;
  color: var(--sjdt-text);
  word-break: break-all;
}

.desc-with-action {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ---------- 资源 ---------- */
.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

@media (max-width: 768px) {
  .resource-grid {
    grid-template-columns: 1fr;
  }
}

.resource-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
}

.resource-label {
  font-size: 13px;
  color: var(--sjdt-muted);
}

.resource-num {
  font-size: 20px;
  font-weight: 700;
}

/* ---------- 操作 ---------- */
.action-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-list .el-button {
  width: 100%;
}

/* ---------- 标签 ---------- */
.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-chip {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
  background: var(--sjdt-primary-soft);
  color: var(--sjdt-primary);
}
</style>
