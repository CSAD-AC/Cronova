<template>
  <div class="task-create-container">
    <PageHeader title="创建任务" subtitle="配置新的定时或一次性任务">
      <el-button @click="$router.back()">
        <el-icon class="mr-1"><ArrowLeft /></el-icon>
        返回
      </el-button>
    </PageHeader>

    <div class="panel form-panel">
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="120px"
        class="task-form"
      >
        <el-divider content-position="left">基本信息</el-divider>
        
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="任务名称" prop="name">
              <el-input v-model="formData.name" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行类型" prop="executeType">
              <el-radio-group v-model="formData.executeType">
                <el-radio value="script">脚本执行</el-radio>
                <el-radio value="callback">HTTP回调</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="任务描述" prop="description">
          <el-input 
            v-model="formData.description" 
            type="textarea" 
            :rows="3"
            placeholder="请输入任务描述" 
          />
        </el-form-item>
        
        <template v-if="formData.executeType === 'script'">
          <el-divider content-position="left">脚本配置</el-divider>
          
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="脚本类型" prop="scriptType">
                <el-select v-model="formData.scriptType" placeholder="选择脚本类型" style="width: 100%">
                  <el-option label="Python" value="python" />
                  <el-option label="Java" value="java" />
                  <el-option label="Go" value="go" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="目标执行器" prop="targetWorker">
                <el-select v-model="formData.targetWorker" placeholder="自动匹配" clearable style="width: 100%">
                  <el-option label="自动匹配" value="" />
                  <el-option 
                    v-for="worker in workers" 
                    :key="worker.id" 
                    :label="worker.host + ' (' + worker.ip + ')'" 
                    :value="worker.id" 
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-form-item label="脚本文件" prop="scriptId">
            <el-select v-model="formData.scriptId" placeholder="选择已有脚本" clearable filterable style="width: 100%; margin-bottom: 10px;">
              <el-option 
                v-for="script in executableScripts" 
                :key="script.id" 
                :label="getScriptLabel(script)" 
                :value="script.id" 
              >
                <div class="flex items-center justify-between">
                  <span>{{ getScriptLabel(script) }}</span>
                  <el-tag size="small" :type="getScriptTypeTag(script)">{{ getScriptType(script) }}</el-tag>
                </div>
              </el-option>
            </el-select>
            <el-upload
              action="#"
              :auto-upload="false"
              :on-change="handleScriptChange"
              accept=".py,.java,.jar,.class,.bin,.zip"
              :show-file-list="false"
            >
              <el-button type="primary" plain>
                <el-icon><Upload /></el-icon>
                上传新脚本
              </el-button>
            </el-upload>
          </el-form-item>
        </template>
        
        <template v-if="formData.executeType === 'callback'">
          <el-divider content-position="left">回调配置</el-divider>
          
          <el-form-item label="回调URL" prop="callbackUrl">
            <el-input v-model="formData.callbackUrl" placeholder="https://api.example.com/callback" />
          </el-form-item>
          
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="请求方法" prop="callbackMethod">
                <el-select v-model="formData.callbackMethod" style="width: 100%">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="密钥" prop="secretKey">
                <el-input v-model="formData.secretKey" placeholder="用于回调验证的密钥" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <el-divider content-position="left">执行控制</el-divider>
        <el-row :gutter="24">
            <el-col :span="8">
              <el-form-item label="超时时间" prop="timeoutSeconds">
                <el-input-number v-model="formData.timeoutSeconds" :min="0" :max="86400" />
                <span class="form-tip">秒 (0不限制)</span>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="重试次数" prop="retryCount">
                <el-input-number v-model="formData.retryCount" :min="0" :max="10" />
                <span class="form-tip">次</span>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="重试间隔" prop="retryIntervalSeconds">
                <el-input-number v-model="formData.retryIntervalSeconds" :min="1" :max="3600" />
                <span class="form-tip">秒</span>
              </el-form-item>
            </el-col>
        </el-row>
        
        <el-divider content-position="left">调度配置</el-divider>
        
        <el-form-item label="任务类型" prop="taskType">
          <el-radio-group v-model="formData.taskType">
            <el-radio value="cron">Cron定时任务</el-radio>
            <el-radio value="once">一次性执行</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item v-if="formData.taskType === 'cron'" label="Cron表达式" prop="cronExpr">
          <el-input v-model="formData.cronExpr" placeholder="0 0 0 * * ?">
            <template #append>
              <el-popover>
                <template #reference>
                  <el-button link>帮助</el-button>
                </template>
                <div class="cron-help">
                  <p><b>六字段格式：秒 分 时 日 月 星期</b></p>
                  <p>重要：日字段和星期字段不能同时为 "*"</p>
                  <p>0 0 0 * * ? - 每天凌晨0点执行（推荐）</p>
                  <p>0 0 0 ? * 1 - 每周一凌晨0点执行</p>
                  <p>0 0 0 1 * ? - 每月1号凌晨0点执行</p>
                  <p>0 */5 * * * ? - 每5分钟执行</p>
                </div>
              </el-popover>
            </template>
          </el-input>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">
            {{ loading ? '创建中...' : '创 建' }}
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Upload, ArrowLeft } from '@element-plus/icons-vue'
import { useTaskStore } from '@/stores/task'
import { workerApi, scriptApi } from '@/services'
import PageHeader from '@/components/PageHeader.vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { WorkerVO, ScriptVO } from '@/types'

const router = useRouter()
const taskStore = useTaskStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const workers = ref<WorkerVO[]>([])
const scripts = ref<ScriptVO[]>([])

const fetchWorkers = async () => {
  try {
    const response = await workerApi.list({ pageSize: 1000 })
    if (Array.isArray(response)) {
      workers.value = response
    } else {
      workers.value = response.records || []
    }
  } catch (error) {
    console.error('获取执行器列表失败:', error)
  }
}

const fetchScripts = async () => {
  try {
    const response = await scriptApi.list({ pageSize: 1000 })
    if (Array.isArray(response)) {
      scripts.value = response
    } else {
      scripts.value = (response as any)?.records || []
    }
  } catch (error) {
    console.error('获取脚本列表失败:', error)
  }
}

// 过滤出可执行的脚本（非目录类型）
const executableScripts = computed(() => {
  return scripts.value.filter(s => !s.isDirectory)
})

// 获取脚本显示名称（含层级路径）
const getScriptLabel = (script: ScriptVO) => {
  // 如果有父目录，显示完整路径
  if (script.parentId) {
    const parent = scripts.value.find(s => s.id === script.parentId)
    if (parent) {
      return `${parent.name}/${script.name}`
    }
  }
  return script.name
}

// 获取脚本类型
const getScriptType = (script: ScriptVO) => {
  const ext = script.name?.toLowerCase()
  if (ext?.endsWith('.py')) return 'Python'
  if (ext?.endsWith('.jar')) return 'Java'
  if (ext?.endsWith('.class')) return 'Java'
  if (ext?.endsWith('.bin')) return 'Go'
  return '未知'
}

// 获取脚本类型标签
const getScriptTypeTag = (script: ScriptVO) => {
  const ext = script.name?.toLowerCase()
  if (ext?.endsWith('.py')) return 'success'
  if (ext?.endsWith('.jar') || ext?.endsWith('.class')) return 'warning'
  if (ext?.endsWith('.bin')) return 'info'
  return 'info'
}

onMounted(() => {
  fetchWorkers()
  fetchScripts()
})

const formData = reactive({
  name: '',
  description: '',
  executeType: 'script' as 'script' | 'callback',
  scriptType: '' as '' | 'python' | 'java' | 'go',
  scriptId: '',
  targetWorker: '',
  timeoutSeconds: 180,
  taskType: 'cron' as 'cron' | 'once',
  cronExpr: '',
  callbackUrl: '',
  callbackMethod: 'POST',
  secretKey: '',
  retryCount: 3,
  retryIntervalSeconds: 60,
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入任务名称', trigger: 'blur' },
    { min: 2, max: 50, message: '任务名称长度在2-50个字符', trigger: 'blur' },
  ],
  executeType: [
    { required: true, message: '请选择执行类型', trigger: 'change' },
  ],
  taskType: [
    { required: true, message: '请选择任务类型', trigger: 'change' },
  ],
  scriptType: [
    {
      validator: (rule: any, value: any, callback: any) => {
        if (formData.executeType === 'script' && !value) {
          callback(new Error('脚本类型为必填项'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  scriptId: [
    {
      validator: (rule: any, value: any, callback: any) => {
        if (formData.executeType === 'script' && !value) {
          callback(new Error('脚本文件为必填项'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  callbackUrl: [
    {
      validator: (rule: any, value: any, callback: any) => {
        if (formData.executeType === 'callback') {
          if (!value) {
            callback(new Error('回调URL为必填项'))
          } else if (!/^https?:\/\/.+/.test(value)) {
            callback(new Error('请输入有效的HTTP或HTTPS URL'))
          } else {
            callback()
          }
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  callbackMethod: [
    {
      validator: (rule: any, value: any, callback: any) => {
        if (formData.executeType === 'callback' && !value) {
          callback(new Error('请求方法为必填项'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  timeoutSeconds: [
    {
      validator: (rule: any, value: any, callback: any) => {
        if (value !== undefined && value < 0) {
          callback(new Error('超时时间不能为负数'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  retryCount: [
    {
      validator: (rule: any, value: any, callback: any) => {
        if (value !== undefined && value < 0) {
          callback(new Error('重试次数不能为负数'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  retryIntervalSeconds: [
    {
      validator: (rule: any, value: any, callback: any) => {
        if (value !== undefined && value <= 0) {
          callback(new Error('重试间隔必须大于0'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  cronExpr: [
    {
      validator: (rule: any, value: any, callback: any) => {
        if (formData.taskType === 'cron') {
          if (!value) {
            callback(new Error('Cron表达式为必填项'))
          } else {
            // 验证六字段cron表达式格式：秒 分 时 日 月 星期
            const cronPattern = /^([0-9]|[1-5][0-9]|\*|\*\/\d+)\s+([0-9]|[1-5][0-9]|\*|\*\/\d+)\s+([0-9]|[1-2][0-9]|\*|\*\/\d+)\s+([0-9]|[12][0-9]|[3][01]|\?|\*|\*\/\d+)\s+([1-9]|1[0-2]|\*|\*\/\d+)\s+([0-6]|\?|\*|\*\/\d+)$/
            if (!cronPattern.test(value)) {
              callback(new Error('请输入有效的六字段Cron表达式（秒 分 时 日 月 星期）'))
            } else {
              // 验证不能同时指定日字段和星期字段
              const parts = value.trim().split(/\s+/)
              if (parts.length === 6) {
                const dayField = parts[3] // 日字段
                const weekField = parts[5] // 星期字段
                
                // 如果日字段和星期字段都指定了非"?"值，则不合法
                const hasSpecificDay = dayField !== '?' && dayField !== '*'
                const hasSpecificWeek = weekField !== '?' && weekField !== '*'
                
                if (hasSpecificDay && hasSpecificWeek) {
                  callback(new Error('日字段和星期字段不能同时指定具体值'))
                } else if (dayField === '*' && weekField === '*') {
                  callback(new Error('日字段和星期字段不能同时为"*"'))
                } else {
                  callback()
                }
              } else {
                callback(new Error('Cron表达式格式错误'))
              }
            }
          }
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
}

async function handleScriptChange(file: any) {
  // 处理脚本文件选择
  const uploadedFile = file.raw || file
  if (uploadedFile) {
    try {
      const fileName = uploadedFile.name || ''
      const uploadFormData = new FormData()
      uploadFormData.append('file', uploadedFile)
      
      let response
      
      // 检测是否是 ZIP 文件夹上传
      if (fileName.toLowerCase().endsWith('.zip')) {
        response = await scriptApi.uploadDirectory(uploadFormData)
      } else {
        response = await scriptApi.upload(uploadFormData)
      }
      
      const scriptId = response.id
      
      // 上传成功后刷新脚本列表
      await fetchScripts()
      
      // 自动选取上传的脚本
      formData.scriptId = scriptId
      
      ElMessage.success('脚本上传成功')
    } catch (error) {
      console.error('脚本上传失败:', error)
      ElMessage.error('脚本上传失败')
    }
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    loading.value = true
    try {
      // 构建请求体结构，根据executeType动态添加字段，只包含非空值
      const requestBody: any = {
        name: formData.name,
        description: formData.description,
        executeType: formData.executeType,
        taskType: formData.taskType,
        timeoutSeconds: formData.timeoutSeconds,
        retryCount: formData.retryCount,
        retryIntervalSeconds: formData.retryIntervalSeconds,
      }

      // 根据执行类型添加特定字段
      if (formData.executeType === 'script') {
        if (formData.scriptType) requestBody.scriptType = formData.scriptType
        if (formData.scriptId) requestBody.scriptId = formData.scriptId
        if (formData.targetWorker) requestBody.targetWorker = formData.targetWorker
      } else if (formData.executeType === 'callback') {
        const callbackConfig: any = {}
        if (formData.callbackUrl) callbackConfig.callbackUrl = formData.callbackUrl
        if (formData.callbackMethod) callbackConfig.callbackMethod = formData.callbackMethod
        if (formData.secretKey) callbackConfig.secretKey = formData.secretKey
        
        // 只有当callbackConfig有值时才添加
        if (Object.keys(callbackConfig).length > 0) {
          requestBody.callbackConfig = callbackConfig
        }
      }

      // 添加调度配置
      if (formData.taskType === 'cron' && formData.cronExpr) {
        requestBody.cronExpr = formData.cronExpr
      }

      await taskStore.createTask(requestBody)
      ElMessage.success('任务创建成功')
      router.push('/tasks')
    } catch (error) {
      console.error('任务创建失败:', error)
      ElMessage.error('任务创建失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.task-create-container {
  max-width: 900px;
  margin: 0 auto;
}

/* ---------- 表单面板（主题 Token） ---------- */
.panel {
  border-radius: 12px;
  border: 1px solid var(--sjdt-border);
  background: var(--sjdt-surface);
  box-shadow: var(--sjdt-card-shadow);
}

.form-panel {
  padding: 24px 28px 12px;
}

.task-form {
  padding: 0;
}

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--sjdt-muted);
}

/* ---------- 分区标题 ---------- */
:deep(.el-divider__text) {
  font-size: 13px;
  font-weight: 600;
  color: var(--sjdt-text);
  background: var(--sjdt-surface);
}

.cron-help {
  font-size: 12px;
  line-height: 1.8;
  color: var(--sjdt-muted);
}

.cron-help p {
  margin: 2px 0;
}

.cron-help b {
  color: var(--sjdt-text);
}
</style>
