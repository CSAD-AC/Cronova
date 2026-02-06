<template>
  <div class="script-manage-container">
    <el-container class="h-full">
      <!-- 左侧文件夹树 -->
      <el-aside width="240px" class="script-sidebar">
        <div class="sidebar-header">
          <span class="sidebar-title">脚本文件夹</span>
        </div>
        <el-tree
          :data="folderTree"
          :props="defaultProps"
          default-expand-all
          highlight-current
          @node-click="handleNodeClick"
          class="folder-tree"
        >
          <template #default="{ node, data }">
            <span class="custom-tree-node">
              <el-icon class="mr-2">
                <component :is="data.icon || 'Folder'" />
              </el-icon>
              <span>{{ node.label }}</span>
              <span v-if="data.count" class="count-badge">{{ data.count }}</span>
            </span>
          </template>
        </el-tree>
      </el-aside>

      <!-- 右侧列表 -->
      <el-main class="script-main">
        <div class="page-header">
          <div class="header-left">
            <h1 class="page-title">{{ currentFolder }}</h1>
            <p class="page-subtitle">管理任务执行所需的脚本文件</p>
          </div>
          <div class="header-right">
            <el-button type="primary" @click="showUploadDialog">
              <el-icon class="mr-1"><Upload /></el-icon>
              上传脚本
            </el-button>
          </div>
        </div>

        <el-card class="table-card" shadow="never">
          <el-table 
            :data="filteredScripts" 
            v-loading="loading"
            style="width: 100%"
            stripe
          >
            <el-table-column prop="name" label="脚本名称" min-width="200">
              <template #default="{ row }">
                <div class="flex items-center">
                  <el-icon class="file-icon mr-2" :size="20">
                    <component :is="getFileIcon(row.name)" />
                  </el-icon>
                  <span>{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="标签" min-width="200">
              <template #default="{ row }">
                <div class="tags-cell">
                  <el-tag 
                    v-for="tag in row.tags" 
                    :key="tag.id"
                    size="small"
                    class="script-tag"
                    effect="plain"
                  >
                    {{ tag.name }}
                  </el-tag>
                  <span v-if="!row.tags?.length" class="no-tags">暂无标签</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="250" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleEdit(row)">
                  <el-icon class="mr-1"><Edit /></el-icon>编辑
                </el-button>
                <el-button link type="primary" @click="handleDownload(row)">
                  <el-icon class="mr-1"><Download /></el-icon>下载
                </el-button>
                <el-button link type="primary" @click="showTagDialog(row)">
                  <el-icon class="mr-1"><PriceTag /></el-icon>标签
                </el-button>
                <el-button link type="danger" @click="handleDelete(row)">
                  <el-icon class="mr-1"><Delete /></el-icon>删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-main>
    </el-container>

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadDialogVisible" title="上传脚本" width="500px">
      <el-upload
        ref="uploadRef"
        action="#"
        :auto-upload="false"
        :on-change="handleFileChange"
        :limit="1"
        accept=".py,.java,.go"
        drag
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip text-center">
            支持 Python(.py)、Java(.java)、Go(.go) 脚本文件
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">
          {{ uploading ? '上传中...' : '上传' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑脚本" width="800px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="脚本名称">
          <el-input v-model="editForm.name" disabled />
        </el-form-item>
        <el-form-item label="脚本内容">
          <el-input 
            v-model="editForm.content" 
            type="textarea" 
            :rows="20" 
            placeholder="请输入脚本内容..."
            class="font-mono"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveScript">
          保存修改
        </el-button>
      </template>
    </el-dialog>
    
    <!-- 标签弹窗 -->
    <el-dialog v-model="tagDialogVisible" title="编辑标签" width="500px">
      <div class="tag-dialog-content">
        <div class="script-info">
          <h4>{{ currentScript?.name }}</h4>
        </div>
        
        <div class="tag-selection">
          <h5 class="mb-2">选择标签</h5>
          <el-select
            v-model="selectedTags"
            multiple
            filterable
            placeholder="搜索并选择标签 (如 pandas)"
            style="width: 100%"
            tag-type="primary"
          >
            <el-option-group
              v-for="group in groupedAllTags"
              :key="group.name"
              :label="group.name"
            >
              <el-option
                v-for="tag in group.versions"
                :key="tag.id"
                :label="`${tag.name} ${tag.operator || ''} ${tag.version || ''}`"
                :value="tag.id"
              >
                <div class="flex justify-between items-center w-full">
                  <span>{{ tag.operator || '==' }} {{ tag.version || 'any' }}</span>
                  <el-tag size="small" type="info" effect="plain">ID: {{ String(tag.id).slice(0, 4) }}</el-tag>
                </div>
              </el-option>
            </el-option-group>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Download, PriceTag, Delete, Folder, Document, Edit, UploadFilled } from '@element-plus/icons-vue'
import { scriptApi, tagApi } from '@/services'
import type { ScriptVO, TagVO } from '@/types'
import { formatDateTime } from '@/utils/date'

const loading = ref(false)
const uploading = ref(false)
const saving = ref(false)
const uploadDialogVisible = ref(false)
const tagDialogVisible = ref(false)
const editDialogVisible = ref(false)

const scripts = ref<ScriptVO[]>([])
const allTags = ref<TagVO[]>([])
const currentScript = ref<ScriptVO | null>(null)
const selectedTags = ref<string[]>([])
const uploadFile = ref<any>(null)

const groupedAllTags = computed(() => {
  const groups: Record<string, { name: string; versions: TagVO[] }> = {}
  allTags.value.forEach(tag => {
    if (tag.name) {
      if (!groups[tag.name]) {
        groups[tag.name] = { name: tag.name, versions: [] }
      }
      groups[tag.name]!.versions.push(tag)
    }
  })
  return Object.values(groups).sort((a, b) => a.name.localeCompare(b.name))
})

const currentFolder = ref('全部脚本')
const currentFilter = ref('all')

const editForm = reactive({
  id: '',
  name: '',
  content: ''
})

const folderTree = computed(() => {
  const counts = {
    all: scripts.value.length,
    py: scripts.value.filter(s => s.name.endsWith('.py')).length,
    java: scripts.value.filter(s => s.name.endsWith('.java')).length,
    go: scripts.value.filter(s => s.name.endsWith('.go')).length,
    other: 0
  }
  counts.other = counts.all - (counts.py + counts.java + counts.go)

  return [
    { label: '全部脚本', id: 'all', icon: 'Folder', count: counts.all },
    { 
      label: '按类型分类', 
      id: 'types',
      icon: 'Folder', 
      children: [
        { label: 'Python', id: 'py', icon: 'Document', count: counts.py },
        { label: 'Java', id: 'java', icon: 'Document', count: counts.java },
        { label: 'Go', id: 'go', icon: 'Document', count: counts.go },
        { label: '其他', id: 'other', icon: 'Document', count: counts.other },
      ]
    }
  ]
})

const defaultProps = {
  children: 'children',
  label: 'label'
}

const filteredScripts = computed(() => {
  if (currentFilter.value === 'all' || currentFilter.value === 'types') {
    return scripts.value
  }
  
  return scripts.value.filter(s => {
    if (currentFilter.value === 'py') return s.name.endsWith('.py')
    if (currentFilter.value === 'java') return s.name.endsWith('.java')
    if (currentFilter.value === 'sh') return s.name.endsWith('.sh')
    if (currentFilter.value === 'go') return s.name.endsWith('.go')
    if (currentFilter.value === 'other') {
      return !s.name.endsWith('.py') && 
             !s.name.endsWith('.java') && 
             !s.name.endsWith('.sh') && 
             !s.name.endsWith('.go')
    }
    return true
  })
})

async function fetchScripts() {
  loading.value = true
  try {
    const response = await scriptApi.list({ pageSize: 1000 })
    if (Array.isArray(response)) {
      scripts.value = response
    } else {
      scripts.value = (response as any)?.records || []
    }
  } catch (error) {
    console.error('获取脚本列表失败:', error)
    scripts.value = []
  } finally {
    loading.value = false
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

function handleNodeClick(data: any) {
  if (data.children) return
  currentFolder.value = data.label
  currentFilter.value = data.id
}

function getFileIcon(filename: string) {
  if (filename.endsWith('.py')) return 'Document' // Ideally python icon
  if (filename.endsWith('.java')) return 'Document' // Ideally java icon
  return 'Document'
}

function showUploadDialog() {
  uploadDialogVisible.value = true
  uploadFile.value = null
}

function handleFileChange(file: any) {
  uploadFile.value = file
}

async function handleUpload() {
  if (!uploadFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.value.raw)
    await scriptApi.upload(formData)
    ElMessage.success('上传成功')
    uploadDialogVisible.value = false
    fetchScripts()
  } catch (error) {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleEdit(row: ScriptVO) {
  editForm.id = row.id
  editForm.name = row.name
  editForm.content = '正在加载内容...'
  editDialogVisible.value = true
  
  try {
    // 尝试下载内容
    const blob = await scriptApi.download(row.id)
    if (blob.size > 0) {
      editForm.content = await blob.text()
    } else {
      editForm.content = '# 暂无内容或无法读取'
    }
  } catch (error) {
    editForm.content = '# 读取失败'
  }
}

async function handleSaveScript() {
  saving.value = true
  try {
    await scriptApi.update(editForm.id, editForm.content)
    ElMessage.success('保存成功')
    editDialogVisible.value = false
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDownload(row: ScriptVO) {
  try {
    const blob = await scriptApi.download(row.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.name
    a.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error('下载失败')
  }
}

function showTagDialog(row: ScriptVO) {
  currentScript.value = row
  selectedTags.value = row.tags?.map(t => t.id) || []
  tagDialogVisible.value = true
}

async function saveTags() {
  if (!currentScript.value) return
  
  try {
    const oldTagIds = currentScript.value.tags?.map(t => t.id) || []
    const newSelectedTags = [...selectedTags.value]
    
    const addTagIds = newSelectedTags.filter(id => !oldTagIds.includes(id as string))
    const removeTagIds = oldTagIds.filter(id => !newSelectedTags.includes(id as string))
    
    for (const tagId of removeTagIds) {
      await scriptApi.removeTag(currentScript.value!.id, tagId)
    }
    
    if (addTagIds.length > 0) {
      await scriptApi.addTags(currentScript.value!.id, addTagIds as unknown as number[])
    }
    
    ElMessage.success('保存成功')
    tagDialogVisible.value = false
    fetchScripts()
  } catch (error) {
    console.error('保存标签失败:', error)
    ElMessage.error('保存失败')
  }
}

function getTagType(tagName: string): string {
  const types = ['primary', 'success', 'info', 'warning', 'danger']
  const hash = tagName.split('').reduce((a, b) => {
    a = ((a << 5) - a) + b.charCodeAt(0)
    return a & a
  }, 0)
  return types[Math.abs(hash) % types.length] || 'info'
}

async function handleDelete(row: ScriptVO) {
  try {
    await ElMessageBox.confirm(`确定要删除脚本"${row.name}"吗？`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await scriptApi.delete(row.id)
    ElMessage.success('删除成功')
    fetchScripts()
  } catch (error) {
    // 用户取消
  }
}

onMounted(() => {
  fetchScripts()
  fetchTags()
})
</script>

<style scoped>
.script-manage-container {
  height: calc(100vh - 84px); /* 减去头部高度 */
  margin: -24px; /* 抵消Layout的padding */
}

.script-sidebar {
  background: white;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.sidebar-title {
  font-weight: 600;
  color: #1e293b;
}

.folder-tree {
  padding: 12px;
  background: transparent;
}

.custom-tree-node {
  display: flex;
  align-items: center;
  width: 100%;
  font-size: 14px;
}

.count-badge {
  margin-left: auto;
  background: #f1f5f9;
  color: #64748b;
  padding: 2px 6px;
  border-radius: 99px;
  font-size: 12px;
}

.script-main {
  padding: 24px;
  background: #f8fafc;
  overflow-y: auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
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
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.file-icon {
  color: #64748b;
}

.tags-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.script-tag {
  margin: 0;
}

.no-tags {
  font-size: 12px;
  color: #94a3b8;
}

/* 编辑标签弹窗样式 */
.tag-dialog-content {
  padding: 16px 0;
}

.script-info {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.script-info h4 {
  margin: 0;
  color: #1e293b;
  font-size: 16px;
  font-weight: 600;
}

.tag-selection h5 {
  margin: 0 0 16px;
  color: #374151;
  font-size: 14px;
  font-weight: 500;
}

.tag-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  padding: 8px 0;
}

.tag-checkbox {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 6px;
  transition: background-color 0.2s;
}

.tag-checkbox:hover {
  background-color: #f9fafb;
}

.tag-checkbox .el-checkbox__input {
  margin-right: 8px;
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

.font-mono {
  font-family: 'Monaco', 'Consolas', monospace;
}
</style>
