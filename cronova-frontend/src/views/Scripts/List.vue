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
            <span
              class="custom-tree-node"
              :class="{ 'is-directory': data.isDirectory, 'is-file': !data.isDirectory }"
            >
              <el-icon class="mr-2">
                <component :is="data.isDirectory ? 'Folder' : 'Document'" />
              </el-icon>
              <span>{{ node.label }}</span>
            </span>
          </template>
        </el-tree>
      </el-aside>

      <!-- 右侧列表 -->
      <el-main class="script-main">
        <div class="page-header">
          <div class="header-left">
            <h1 class="page-title">{{ currentFolder }}</h1>
            <el-breadcrumb v-if="breadcrumb.length > 1" separator="/" class="mt-2">
              <el-breadcrumb-item v-for="(item, index) in breadcrumb" :key="item.id">
                <span
                  :class="{ 'breadcrumb-clickable': index < breadcrumb.length - 1 }"
                  @click="index < breadcrumb.length - 1 ? navigateToBreadcrumb(item) : undefined"
                >
                  {{ item.name }}
                </span>
              </el-breadcrumb-item>
            </el-breadcrumb>
            <p class="page-subtitle">管理任务执行所需的脚本文件</p>
          </div>
          <div class="header-right">
            <el-button type="primary" @click="showUploadDialog">
              <el-icon class="mr-1"><Upload /></el-icon>
              上传脚本
            </el-button>
          </div>
        </div>

        <div class="table-panel">
          <el-table
            :data="filteredScripts"
            v-loading="loading"
            style="width: 100%"
            stripe
            row-class-name="table-row"
            @row-dblclick="handleRowDblClick"
          >
            <el-table-column prop="name" label="脚本名称" min-width="220">
              <template #default="{ row }">
                <div class="file-cell">
                  <div class="file-avatar" :class="row.isDirectory ? 'avatar-dir' : 'avatar-file'">
                    <el-icon :size="17">
                      <Folder v-if="row.isDirectory" />
                      <Document v-else />
                    </el-icon>
                  </div>
                  <span class="file-name" :class="{ 'is-dir': row.isDirectory }">{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="标签" min-width="200">
              <template #default="{ row }">
                <div class="tags-cell">
                  <span
                    v-for="tag in row.tags"
                    :key="tag.id"
                    class="tag-chip"
                  >
                    {{ tag.name }}
                  </span>
                  <span v-if="!row.tags?.length" class="no-tags">暂无标签</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="170">
              <template #default="{ row }">
                <span class="cronova-mono time-text">{{ formatDateTime(row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <div class="action-buttons">
                  <el-button
                    v-if="row.isDirectory"
                    link
                    type="primary"
                    @click="enterDirectory(row.id)"
                  >
                    <el-icon class="mr-1"><FolderOpened /></el-icon>进入
                  </el-button>
                  <el-button v-else link type="primary" @click="handlePreview(row)">
                    <el-icon class="mr-1"><View /></el-icon>预览
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
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-main>
    </el-container>

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadDialogVisible" title="上传脚本" width="600px">
      <el-tabs v-model="uploadTab">
        <el-tab-pane label="单文件上传" name="file">
          <el-upload
            ref="uploadRef"
            action="#"
            :auto-upload="false"
            :on-change="handleFileChange"
            :limit="1"
            accept=".py,.java,.jar,.bin"
            drag
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处或 <em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip text-center">
                支持 Python(.py)、Java(.jar/.class)、Go(.bin) 二进制文件
              </div>
            </template>
          </el-upload>
        </el-tab-pane>

        <el-tab-pane label="文件夹上传" name="directory">
          <el-upload
            ref="uploadDirRef"
            action="#"
            :auto-upload="false"
            :on-change="handleDirectoryChange"
            :limit="1"
            accept=".zip"
            drag
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">拖拽ZIP压缩包到此处或 <em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip text-center">上传ZIP压缩包，将自动解压保持目录结构</div>
            </template>
          </el-upload>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">
          {{ uploading ? '上传中...' : '上传' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="editDialogVisible" title="预览脚本" width="800px">
      <el-form label-width="80px">
        <el-form-item label="脚本名称">
          <el-input :model-value="editForm.name" disabled />
        </el-form-item>
        <el-form-item label="脚本内容">
          <el-input
            :model-value="editForm.content"
            type="textarea"
            :rows="20"
            readonly
            class="font-mono bg-gray-50"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">关闭</el-button>
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
            placeholder="搜索并选择标签"
            style="width: 100%"
          >
            <el-option v-for="tag in allTags" :key="tag.id" :label="tag.name" :value="tag.id">
              <div class="flex justify-between items-center w-full">
                <span>{{ tag.name }}</span>
                <el-tag v-if="tag.description" size="small" type="info" effect="plain">{{
                  tag.description
                }}</el-tag>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Upload,
  Download,
  PriceTag,
  Delete,
  Folder,
  Document,
  View,
  UploadFilled,
  FolderOpened,
} from '@element-plus/icons-vue'
import { scriptApi, tagApi } from '@/services'
import type { ScriptVO, TagVO } from '@/types'
import { formatDateTime } from '@/utils/date'

const loading = ref(false)
const uploading = ref(false)
const uploadDialogVisible = ref(false)
const tagDialogVisible = ref(false)
const editDialogVisible = ref(false)

const scripts = ref<ScriptVO[]>([])
const allTags = ref<TagVO[]>([])
const currentScript = ref<ScriptVO | null>(null)
const selectedTags = ref<(string | number)[]>([])
const uploadFile = ref<any>(null)
const uploadDirFile = ref<any>(null)
const uploadTab = ref('file')

const currentFolder = ref('根目录')

// 文件管理器模式：当前所在目录ID（null表示根目录）
const currentDirectoryId = ref<string | null>(null)

// 面包屑路径
const breadcrumb = computed(() => {
  if (!currentDirectoryId.value) {
    return [{ id: null, name: '根目录' }]
  }

  const path: { id: string | null; name: string }[] = [{ id: null, name: '根目录' }]
  let currentId: string | null = currentDirectoryId.value

  while (currentId) {
    const folder = scripts.value.find((s) => s.id === currentId && s.isDirectory)
    if (folder) {
      path.push({ id: folder.id, name: folder.name })
      currentId = folder.parentId ?? null
    } else {
      break
    }
  }

  return path
})

// 进入目录
function enterDirectory(folderId: string | null) {
  currentDirectoryId.value = folderId
  if (folderId === null) {
    currentFolder.value = '根目录'
  } else {
    const folder = scripts.value.find((s) => s.id === folderId)
    currentFolder.value = folder?.name || '目录'
  }
}

// 点击面包屑导航
function navigateToBreadcrumb(item: { id: string | null; name: string }) {
  enterDirectory(item.id)
}

const editForm = reactive({
  id: '',
  name: '',
  content: '',
})

const folderTree = computed(() => {
  // 基于 parent_id 构建真实的层级树形结构
  const allScripts = scripts.value
  const rootNodes: any[] = []
  const nodeMap = new Map<string, any>()

  // 第一遍：创建所有节点
  allScripts.forEach((script) => {
    nodeMap.set(script.id, {
      id: script.id,
      label: script.name,
      isDirectory: script.isDirectory,
      parentId: script.parentId,
      children: [],
    })
  })

  // 第二遍：构建父子关系
  allScripts.forEach((script) => {
    const node = nodeMap.get(script.id)
    // 为层级节点生成特殊ID
    if (script.isDirectory) {
      node.id = 'dir_' + script.id
    }
    if (script.parentId && nodeMap.has(script.parentId)) {
      // 有父节点，添加到父节点的 children 中
      const parent = nodeMap.get(script.parentId)
      parent.children.push(node)
    } else {
      // 没有父节点，是根节点
      rootNodes.push(node)
    }
  })

  // 排序：目录在前，文件在后
  const sortNodes = (nodes: any[]) => {
    nodes.sort((a, b) => {
      if (a.isDirectory !== b.isDirectory) {
        return a.isDirectory ? -1 : 1
      }
      return a.label.localeCompare(b.label)
    })
    nodes.forEach((node) => {
      if (node.children.length > 0) {
        sortNodes(node.children)
      }
    })
  }
  sortNodes(rootNodes)

  // 直接返回根目录的树形结构
  return rootNodes
})

const defaultProps = {
  children: 'children',
  label: 'label',
}

const filteredScripts = computed(() => {
  // 文件管理器模式：根据当前目录ID过滤
  let result = scripts.value

  if (currentDirectoryId.value !== null) {
    // 在目录中：只显示当前目录的直接子文件和子目录
    result = result.filter((s) => s.parentId === currentDirectoryId.value)
  } else {
    // 根目录视图：显示所有根目录下的直接子文件和子目录
    result = result.filter((s) => !s.parentId)
  }

  // 目录在前，文件在后排序
  return result.sort((a, b) => {
    if (a.isDirectory !== b.isDirectory) {
      return a.isDirectory ? -1 : 1
    }
    return a.name.localeCompare(b.name)
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
  // 处理层级目录中的真实目录节点
  if (data.isDirectory) {
    // 提取真实目录ID（去除 dir_ 前缀）
    const folderId = data.id.toString().startsWith('dir_')
      ? data.id.toString().replace('dir_', '')
      : data.id
    enterDirectory(folderId)
    return
  }

  // 处理层级目录中的文件节点 - 跳转到文件所在目录
  const allScripts = scripts.value
  const fileNode = allScripts.find(
    (s) => s.id === data.id || s.id === data.id.toString().replace('dir_', ''),
  )
  if (fileNode && !fileNode.isDirectory) {
    // 跳转到文件的父目录
    enterDirectory(fileNode.parentId ?? null)
    return
  }
}

function handleRowDblClick(row: ScriptVO) {
  if (row.isDirectory) {
    enterDirectory(row.id)
  } else {
    handlePreview(row)
  }
}

function showUploadDialog() {
  uploadDialogVisible.value = true
  uploadFile.value = null
  uploadDirFile.value = null
  uploadTab.value = 'file'
}

function handleFileChange(file: any) {
  uploadFile.value = file
}

function handleDirectoryChange(file: any) {
  uploadDirFile.value = file
}

async function handleUpload() {
  if (uploadTab.value === 'file') {
    if (!uploadFile.value) {
      ElMessage.warning('请选择文件')
      return
    }

    // 检测是否是 zip 文件，如果是则自动使用文件夹上传逻辑，静默处理
    const fileName = uploadFile.value?.name || ''
    if (fileName.toLowerCase().endsWith('.zip')) {
      uploadDirFile.value = uploadFile.value
      uploadDialogVisible.value = false
      await uploadDirectory()
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
  } else {
    // 文件夹上传
    if (!uploadDirFile.value) {
      ElMessage.warning('请选择ZIP文件')
      return
    }

    await uploadDirectory()
  }
}

async function uploadDirectory() {
  if (!uploadDirFile.value) {
    ElMessage.warning('请选择ZIP文件')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadDirFile.value.raw)
    await scriptApi.uploadDirectory(formData)
    ElMessage.success('上传成功')
    uploadDialogVisible.value = false
    fetchScripts()
  } catch (error) {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

async function handlePreview(row: ScriptVO) {
  editForm.id = row.id
  editForm.name = row.name
  editForm.content = '正在加载内容...'
  editDialogVisible.value = true

  try {
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

async function handleDownload(row: ScriptVO) {
  try {
    let blob: Blob
    let fileName: string
    
    if (row.isDirectory) {
      blob = await scriptApi.downloadDirectory(row.id)
      fileName = row.name + '.zip'
    } else {
      blob = await scriptApi.download(row.id)
      fileName = row.name
    }
    
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    a.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error('下载失败')
  }
}

function showTagDialog(row: ScriptVO) {
  currentScript.value = row
  selectedTags.value = row.tags?.map((t) => t.id) || []
  tagDialogVisible.value = true
}

async function saveTags() {
  if (!currentScript.value) return

  try {
    const oldTagIds = currentScript.value.tags?.map((t) => t.id) || []
    const newSelectedTags = [...selectedTags.value]

    const addTagIds = newSelectedTags.filter((id) => !oldTagIds.includes(id as string))
    const removeTagIds = oldTagIds.filter((id) => !newSelectedTags.includes(id as string))

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
    a = (a << 5) - a + b.charCodeAt(0)
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

/* ---------- 左侧文件夹树 ---------- */
.script-sidebar {
  background: var(--cronova-surface);
  border-right: 1px solid var(--cronova-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--cronova-border);
}

.sidebar-title {
  font-weight: 600;
  color: var(--cronova-text);
  font-size: 14px;
}

.folder-tree {
  padding: 12px;
  background: transparent;
  overflow-y: auto;
  flex: 1;
  --el-tree-node-hover-bg-color: var(--cronova-row-hover);
}

.custom-tree-node {
  display: flex;
  align-items: center;
  width: 100%;
  font-size: 13px;
}

.custom-tree-node.is-directory {
  color: var(--cronova-running);
  font-weight: 500;
}

.custom-tree-node.is-file {
  color: var(--cronova-muted);
}

.script-main {
  padding: 24px;
  background: var(--cronova-bg);
  overflow-y: auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--cronova-text);
  margin: 0 0 4px;
}

.page-subtitle {
  font-size: 13px;
  color: var(--cronova-muted);
  margin: 0;
}

.breadcrumb-clickable {
  cursor: pointer;
  color: var(--cronova-primary);
}

.breadcrumb-clickable:hover {
  text-decoration: underline;
}

/* ---------- 表格面板 ---------- */
.table-panel {
  border-radius: 12px;
  border: 1px solid var(--cronova-border);
  background: var(--cronova-surface);
  box-shadow: var(--cronova-card-shadow);
  padding: 8px 16px 16px;
}

.table-row {
  cursor: pointer;
}

.file-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-avatar {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.avatar-dir {
  background: var(--cronova-running-soft);
  color: var(--cronova-running);
}

.avatar-file {
  background: var(--cronova-primary-soft);
  color: var(--cronova-primary);
}

.file-name {
  font-size: 13px;
  color: var(--cronova-text);
}

.file-name.is-dir {
  font-weight: 600;
}

.time-text {
  font-size: 13px;
  color: var(--cronova-text);
}

.tags-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-chip {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
  background: var(--cronova-pending-soft);
  color: var(--cronova-pending);
  white-space: nowrap;
}

.no-tags {
  font-size: 12px;
  color: var(--cronova-muted);
}

.action-buttons {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

/* ---------- 弹窗样式 ---------- */
.tag-dialog-content {
  padding: 16px 0;
}

.script-info {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--cronova-border);
}

.script-info h4 {
  margin: 0;
  color: var(--cronova-text);
  font-size: 16px;
  font-weight: 600;
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

.font-mono {
  font-family: var(--cronova-mono);
}
</style>
