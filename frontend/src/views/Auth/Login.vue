<template>
  <div class="login-container">
    <!-- 背景光晕（深海观测氛围） -->
    <div class="glow glow-1" />
    <div class="glow glow-2" />

    <!-- 右上角主题切换 -->
    <div class="theme-corner">
      <ThemeToggle />
    </div>

    <div class="login-card">
      <div class="login-header">
        <div class="login-logo">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
            <path d="M3 12h4l2.5-6 4 12 2.5-6H21" />
          </svg>
        </div>
        <h1 class="login-title">SJDT 任务调度器</h1>
        <p class="login-subtitle">分布式任务调度管理系统</p>
      </div>

      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        class="login-form"
        size="large"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="login-button"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <span>还没有账号？</span>
        <el-link type="primary" :underline="false">立即注册</el-link>
      </div>
    </div>

    <div class="login-features">
      <div v-for="f in features" :key="f.label" class="feature-item">
        <el-icon :size="15">
          <component :is="f.icon" />
        </el-icon>
        <span>{{ f.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Clock, Cpu, Monitor, Connection } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import type { FormInstance, FormRules } from 'element-plus'
import ThemeToggle from '@/components/ThemeToggle.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 表单引用 */
const formRef = ref<FormInstance>()

/** 登录中加载状态 */
const loading = ref(false)

/** 登录表单数据 */
const loginForm = reactive({
  username: '',
  password: '',
})

/** 表单校验规则 */
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3-20个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6个字符', trigger: 'blur' },
  ],
}

/** 底部系统特性列表 */
const features = [
  { label: '分布式调度', icon: Clock },
  { label: '多执行器支持', icon: Cpu },
  { label: '实时监控', icon: Monitor },
  { label: '安全可靠', icon: Connection },
]

/**
 * 提交登录：校验通过后调用 userStore.login，
 * 成功后跳转 redirect 参数或仪表盘
 */
async function handleLogin(): Promise<void> {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const success = await userStore.login(loginForm)
      if (success) {
        ElMessage.success('登录成功')
        const redirect = route.query.redirect as string
        router.push(redirect || '/dashboard')
      } else {
        ElMessage.error('用户名或密码错误')
      }
    } catch (error) {
      ElMessage.error('登录失败，请检查网络连接')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--sjdt-bg);
  padding: 20px;
  overflow: hidden;
  transition: background-color .25s ease;
}

/* 深海光晕 */
.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: .45;
  pointer-events: none;
}

.glow-1 {
  width: 480px;
  height: 480px;
  top: -140px;
  right: -100px;
  background: radial-gradient(circle, var(--sjdt-primary) 0%, transparent 70%);
}

.glow-2 {
  width: 420px;
  height: 420px;
  bottom: -120px;
  left: -110px;
  background: radial-gradient(circle, var(--sjdt-accent) 0%, transparent 70%);
  opacity: .3;
}

.theme-corner {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 2;
}

.login-card {
  position: relative;
  z-index: 1;
  background: var(--sjdt-surface);
  border: 1px solid var(--sjdt-border);
  border-radius: 16px;
  padding: 44px 40px 36px;
  width: 100%;
  max-width: 420px;
  box-shadow: var(--sjdt-card-shadow);
  transition: background-color .25s ease, border-color .25s ease;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-logo {
  width: 60px;
  height: 60px;
  margin: 0 auto 16px;
  border-radius: 16px;
  background: var(--sjdt-logo-grad);
  color: #FFFFFF;
  display: grid;
  place-items: center;
  box-shadow: 0 10px 26px var(--sjdt-primary-glow);
}

.login-logo svg {
  width: 30px;
  height: 30px;
}

.login-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--sjdt-text);
  margin: 0 0 8px;
  letter-spacing: .5px;
}

.login-subtitle {
  font-size: 13px;
  color: var(--sjdt-muted);
  margin: 0;
}

.login-form {
  margin-bottom: 20px;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  margin-top: 4px;
}

.login-footer {
  text-align: center;
  font-size: 13px;
  color: var(--sjdt-muted);
}

/* 底部特性条 */
.login-features {
  position: absolute;
  bottom: 36px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 32px;
  z-index: 1;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--sjdt-muted);
}

.feature-item .el-icon {
  color: var(--sjdt-primary);
}

@media (max-width: 768px) {
  .login-features {
    display: none;
  }
}
</style>
