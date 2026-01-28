<template>
  <div class="tag-list-container">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">标签管理</h1>
        <p class="page-subtitle">管理脚本和执行器的依赖标签</p>
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
              <div class="stat-label">关联脚本总数</div>
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
              <div class="stat-label">关联执行器总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="table-card" shadow="never">
      <el-table 
        :data="tags" 
        v-loading="loading"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="name" label="标签名称" min-width="150" />
        <el-table-column label="版本约束" min-width="150">
          <template #default="{ row }">
            <span v-if="row.operator && row.version">
              {{ row.operator }} {{ row.version }}
            </span>
            <span v-else class="no-version">无约束</span>
          </template>
        </el-table-column>
        <el-table-column prop="scriptCount" label="关联脚本" width="100" align="center" />
        <el-table-column prop="workerCount" label="关联执行器" width="100" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showEditDialog(row)">
              <el-icon class="mr-1"><Edit /></el-icon>编辑
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">
              <el-icon class="mr-1"><Delete /></el-icon>删除
            </el-button>
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
          <el-input v-model="formData.name" placeholder="请输入标签名称" />
        </el-form-item>
        <el-form-item label="操作符">
          <el-select v-model="formData.operator" placeholder="选择操作符" style="width: 100%">
            <el-option label="无约束" value="" />
            <el-option label=">=" value=">=" />
            <el-option label=">" value=">" />
            <el-option label="==" value="==" />
            <el-option label="<=" value="<=" />
            <el-option label="<" value="<" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号" v-if="formData.operator" prop="version">
          <el-input v-model="formData.version" placeholder="例如: 2.28.0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ submitting ? '保存中...' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search, PriceTag, Document, Cpu } from '@element-plus/icons-vue'
import { tagApi } from '@/services'
import type { TagVO } from '@/types'
import type { FormInstance, FormRules } from 'element-plus'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)

const tags = ref<TagVO[]>([])
const currentTag = ref<TagVO | null>(null)

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchKeyword = ref('')

const totalScriptCount = computed(() => tags.value.reduce((sum, tag) => sum + (tag.scriptCount || 0), 0))
const totalWorkerCount = computed(() => tags.value.reduce((sum, tag) => sum + (tag.workerCount || 0), 0))

const formRef = ref<FormInstance>()
const formData = reactive({
  id: '',
  name: '',
  operator: '',
  version: '',
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { min: 1, max: 50, message: '标签名称长度在1-50个字符', trigger: 'blur' },
  ],
  version: [
    {
      validator: (_rule, value, callback) => {
        if (formData.operator && !value) {
          callback(new Error('选择操作符后必须填写版本号'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function fetchTags() {
  loading.value = true
  try {
    const response = await tagApi.list({ pageNum: currentPage.value, pageSize: pageSize.value })
    tags.value = response.records
    total.value = response.total
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  isEdit.value = false
  formData.id = ''
  formData.name = ''
  formData.operator = ''
  formData.version = ''
  dialogVisible.value = true
}

function showEditDialog(row: TagVO) {
  isEdit.value = true
  currentTag.value = row
  formData.id = row.id
  formData.name = row.name
  formData.operator = row.operator || ''
  formData.version = row.version || ''
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
          operator: formData.operator || undefined,
          version: formData.version || undefined,
        })
        ElMessage.success('更新成功')
      } else {
        await tagApi.create({
          name: formData.name,
          operator: formData.operator || undefined,
          version: formData.version || undefined,
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
    await ElMessageBox.confirm(`确定要删除标签"${row.name}"吗？`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await tagApi.delete(row.id)
    ElMessage.success('删除成功')
    // 如果当前页只有一条数据且不是第一页，则跳转到上一页
    if (tags.value.length === 1 && currentPage.value > 1) {
      currentPage.value--
    }
    await fetchTags()
  } catch (error) {
    // 用户取消或操作失败
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchTags()
})
</script>

<style scoped>
.tag-list-container {
  max-width: 1200px;
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

.no-version {
  color: #94a3b8;
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
</style>
