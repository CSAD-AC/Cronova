<template>
  <div class="tag-list-container">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">标签管理</h1>
        <p class="page-subtitle">管理执行器镜像标签，用于任务匹配</p>
      </div>
      <div class="header-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索标签..."
          class="mr-4"
          style="width: 200px"
          clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="showCreateDialog">
          <el-icon class="mr-1"><Plus /></el-icon>
          创建标签
        </el-button>
      </div>
    </div>
    
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="mb-6">
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon bg-blue-100 text-blue-600">
              <el-icon><PriceTag /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ total }}</div>
              <div class="stat-label">总标签数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon bg-green-100 text-green-600">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ totalScriptCount }}</div>
              <div class="stat-label">关联脚本数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon bg-purple-100 text-purple-600">
              <el-icon><Cpu /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ totalWorkerCount }}</div>
              <div class="stat-label">关联执行器数</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="table-card" shadow="never">
      <el-table 
        :data="filteredTags" 
        v-loading="loading"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="name" label="标签名称" min-width="150">
          <template #default="{ row }">
            <el-tag type="primary" effect="plain">{{ row.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200">
          <template #default="{ row }">
            <span v-if="row.description" class="description-text">{{ row.description }}</span>
            <span v-else class="no-description">暂无描述</span>
          </template>
        </el-table-column>
        <el-table-column label="关联脚本" width="120" align="center">
          <template #default="{ row }">
            <el-button type="success" size="small" @click="showDetailDialog(row, 'script')">
              {{ row.scriptCount || 0 }} 个
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="关联执行器" width="120" align="center">
          <template #default="{ row }">
            <el-button type="warning" size="small" @click="showDetailDialog(row, 'worker')">
              {{ row.workerCount || 0 }} 个
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="default" @click="showEditDialog(row)">
                <el-icon class="mr-1"><Edit /></el-icon>编辑
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
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchTags"
          @current-change="fetchTags"
        />
      </div>
    </el-card>
    
    <!-- 创建/编辑标签弹窗 -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="isEdit ? '编辑标签' : '创建标签'"
      width="450px"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="formData.name" placeholder="如: V1, FastAPI, Python3.11" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="标签描述，用于说明标签用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ submitting ? '保存中...' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
    
    <!-- 详情弹窗 -->
    <el-dialog 
      v-model="detailDialogVisible" 
      :title="detailType === 'script' ? `标签「${currentTag?.name}」关联的脚本` : `标签「${currentTag?.name}」关联的执行器`"
      width="600px"
    >
      <div class="detail-content">
        <div class="detail-header">
          <el-button type="primary" @click="showBindDialog">
            <el-icon class="mr-1"><Plus /></el-icon>
            {{ detailType === 'script' ? '关联脚本' : '关联执行器' }}
          </el-button>
        </div>
        
        <el-table :data="detailList" v-loading="detailLoading" style="width: 100%; margin-top: 16px;">
          <el-table-column v-if="detailType === 'script'" prop="name" label="脚本名称" min-width="150" />
          <el-table-column v-if="detailType === 'script'" prop="path" label="脚本路径" min-width="200" show-overflow-tooltip />
          <el-table-column v-if="detailType === 'worker'" prop="host" label="主机名" min-width="120" />
          <el-table-column v-if="detailType === 'worker'" prop="ip" label="IP地址" min-width="140" />
          <el-table-column v-if="detailType === 'worker'" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'online' ? 'success' : 'danger'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" plain size="small" @click="handleUnbind(row)">
                取消关联
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <el-empty v-if="!detailLoading && detailList.length === 0" description="暂无关联" />
      </div>
    </el-dialog>
    
    <!-- 关联弹窗 -->
    <el-dialog 
      v-model="bindDialogVisible" 
      :title="detailType === 'script' ? '关联脚本' : '关联执行器'"
      width="600px"
    >
      <el-table :data="availableList" v-loading="bindLoading" style="width: 100%">
        <el-table-column v-if="detailType === 'script'" prop="name" label="脚本名称" min-width="150" />
        <el-table-column v-if="detailType === 'script'" prop="path" label="脚本路径" min-width="200" show-overflow-tooltip />
        <el-table-column v-if="detailType === 'worker'" prop="host" label="主机名" min-width="120" />
        <el-table-column v-if="detailType === 'worker'" prop="ip" label="IP地址" min-width="140" />
        <el-table-column v-if="detailType === 'worker'" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'online' ? 'success' : 'danger'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" plain size="small" @click="handleBind(row)">
              关联
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!bindLoading && availableList.length === 0" description="没有可关联的资源" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search, PriceTag, Document, Cpu } from '@element-plus/icons-vue'
import { tagApi } from '@/services'
import { scriptApi } from '@/services'
import { workerApi } from '@/services'
import type { TagVO, ScriptVO, WorkerVO } from '@/types'
import type { FormInstance, FormRules } from 'element-plus'
import { formatDateTime } from '@/utils/date'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const detailDialogVisible = ref(false)
const bindDialogVisible = ref(false)
const detailType = ref<'script' | 'worker'>('script')
const detailLoading = ref(false)
const bindLoading = ref(false)

const tags = ref<TagVO[]>([])
const currentTag = ref<TagVO | null>(null)
const detailList = ref<(ScriptVO | WorkerVO)[]>([])
const availableList = ref<(ScriptVO | WorkerVO)[]>([])

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchKeyword = ref('')

const totalScriptCount = computed(() => tags.value.reduce((sum, tag) => sum + (tag.scriptCount || 0), 0))
const totalWorkerCount = computed(() => tags.value.reduce((sum, tag) => sum + (tag.workerCount || 0), 0))

const filteredTags = computed(() => {
  if (!searchKeyword.value) return tags.value
  return tags.value.filter(tag => 
    tag.name.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
    (tag.description && tag.description.toLowerCase().includes(searchKeyword.value.toLowerCase()))
  )
})

const formRef = ref<FormInstance>()
const formData = reactive({
  id: 0,
  name: '',
  description: '',
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { min: 1, max: 50, message: '标签名称长度在1-50个字符', trigger: 'blur' },
  ],
}

async function fetchTags() {
  loading.value = true
  try {
    const response = await tagApi.list({ page: currentPage.value, size: pageSize.value })
    tags.value = response.records
    total.value = response.total
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  isEdit.value = false
  formData.id = 0
  formData.name = ''
  formData.description = ''
  dialogVisible.value = true
}

function showEditDialog(row: TagVO) {
  isEdit.value = true
  currentTag.value = row
  formData.id = row.id as number
  formData.name = row.name
  formData.description = row.description || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    submitting.value = true
    try {
      if (isEdit.value) {
        await tagApi.update({
          id: formData.id,
          name: formData.name,
          description: formData.description || undefined,
        })
        ElMessage.success('更新成功')
      } else {
        await tagApi.create({
          name: formData.name,
          description: formData.description || undefined,
        })
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchTags()
    } catch (error) {
      ElMessage.error('操作失败')
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(row: TagVO) {
  try {
    await ElMessageBox.confirm(`确定要删除标签「${row.name}」吗？`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await tagApi.delete(row.id)
    ElMessage.success('删除成功')
    if (tags.value.length === 1 && currentPage.value > 1) {
      currentPage.value--
    }
    await fetchTags()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

async function showDetailDialog(row: TagVO, type: 'script' | 'worker') {
  currentTag.value = row
  detailType.value = type
  detailDialogVisible.value = true
  detailLoading.value = true
  detailList.value = []
  
  try {
    if (type === 'script') {
      const res = await tagApi.getScripts(row.id as number)
      detailList.value = Array.isArray(res) ? res : []
    } else {
      const res = await tagApi.getWorkers(row.id as number)
      detailList.value = Array.isArray(res) ? res : []
    }
  } finally {
    detailLoading.value = false
  }
}

async function showBindDialog() {
  if (!currentTag.value) return
  bindDialogVisible.value = true
  bindLoading.value = true
  availableList.value = []
  
  try {
    if (detailType.value === 'script') {
      const [allScriptsRes, boundScriptsRes] = await Promise.all([
        scriptApi.list({ pageNum: 1, pageSize: 1000 }),
        tagApi.getScripts(currentTag.value.id as number)
      ])
      // scriptApi.list 返回的是数组
      const allScripts = Array.isArray(allScriptsRes) ? allScriptsRes : []
      // 过滤掉文件夹，只保留文件
      const fileScripts = allScripts.filter((s: ScriptVO) => !s.isDirectory)
      // getScripts 返回数组
      const boundScripts = boundScriptsRes || []
      const boundIds = new Set(boundScripts.map((s: ScriptVO) => s.id))
      availableList.value = fileScripts.filter((s: ScriptVO) => !boundIds.has(s.id))
    } else {
      const [allWorkersRes, boundWorkersRes] = await Promise.all([
        workerApi.list({ pageNum: 1, pageSize: 1000 }),
        tagApi.getWorkers(currentTag.value.id as number)
      ])
      // workerApi.list 返回的是数组（不是分页对象）
      const allWorkers = Array.isArray(allWorkersRes) ? allWorkersRes : (allWorkersRes?.records || [])
      // getWorkers 返回数组
      const boundWorkers = boundWorkersRes || []
      const boundIds = new Set(boundWorkers.map((w: WorkerVO) => w.id))
      availableList.value = allWorkers.filter((w: WorkerVO) => !boundIds.has(w.id))
    }
  } finally {
    bindLoading.value = false
  }
}

async function handleBind(row: ScriptVO | WorkerVO) {
  if (!currentTag.value) return
  
  try {
    if (detailType.value === 'script') {
      await tagApi.bindScript(currentTag.value.id as number, row.id)
      ElMessage.success('关联成功')
    } else {
      await tagApi.bindWorker(currentTag.value.id as number, row.id)
      ElMessage.success('关联成功')
    }
    bindDialogVisible.value = false
    await showDetailDialog(currentTag.value, detailType.value)
    fetchTags()
  } catch (error) {
    ElMessage.error('关联失败')
  }
}

async function handleUnbind(row: ScriptVO | WorkerVO) {
  if (!currentTag.value) return
  
  try {
    await ElMessageBox.confirm('确定要取消关联吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    
    if (detailType.value === 'script') {
      await tagApi.unbindScript(currentTag.value.id as number, row.id)
      ElMessage.success('取消关联成功')
    } else {
      await tagApi.unbindWorker(currentTag.value.id as number, row.id)
      ElMessage.success('取消关联成功')
    }
    
    await showDetailDialog(currentTag.value, detailType.value)
    fetchTags()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消关联失败')
    }
  }
}

onMounted(() => {
  fetchTags()
})
</script>

<style scoped>
.tag-list-container {
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

.table-card {
  border-radius: 12px;
}

.no-description {
  color: #94a3b8;
  font-size: 13px;
}

.description-text {
  color: #475569;
  font-size: 13px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.stat-card {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.stat-content {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-right: 16px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
}

.bg-blue-100 { background-color: #dbeafe; }
.text-blue-600 { color: #2563eb; }
.bg-green-100 { background-color: #dcfce7; }
.text-green-600 { color: #16a34a; }
.bg-purple-100 { background-color: #f3e8ff; }
.text-purple-600 { color: #9333ea; }

.detail-header {
  margin-bottom: 16px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.action-buttons .el-button {
  font-weight: 500;
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

:deep(.el-button link) {
  font-size: 14px;
}
</style>
