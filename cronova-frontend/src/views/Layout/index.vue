<template>
  <div class="layout-container">
    <!-- ============ 侧边栏：随主题切换，深色模式三层明度阶梯的中层 ============ -->
    <aside class="sidebar" :class="{ 'is-collapsed': isCollapsed }">
      <div class="logo">
        <div class="logo-mark">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
            <path d="M3 12h4l2.5-6 4 12 2.5-6H21" />
          </svg>
        </div>
        <span v-if="!isCollapsed" class="logo-name">SJDT 调度器</span>
      </div>

      <nav class="menu-scroll">
        <template v-for="group in menuGroups" :key="group.label">
          <div v-if="!isCollapsed" class="menu-group-title">{{ group.label }}</div>
          <div v-else class="menu-group-divider" />
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="menu-item"
            :class="{ active: isActive(item.path) }"
            :title="isCollapsed ? item.title : undefined"
          >
            <el-icon :size="17">
              <component :is="item.icon" />
            </el-icon>
            <span v-if="!isCollapsed" class="menu-item-text">{{ item.title }}</span>
            <!-- 执行器在线数徽标（脉搏签名元素） -->
            <span
              v-if="!isCollapsed && item.path === '/workers' && onlineCount > 0"
              class="menu-badge"
            >
              <span class="badge-dot" />
              {{ onlineCount }}
            </span>
            <span
              v-else-if="isCollapsed && item.path === '/workers' && onlineCount > 0"
              class="menu-badge collapsed"
            >
              <span class="badge-dot" />
            </span>
          </router-link>
        </template>
      </nav>

      <div class="sidebar-foot">
        <button class="collapse-btn" :title="isCollapsed ? '展开菜单' : '收起菜单'" @click="toggleCollapse">
          <el-icon :size="15">
            <component :is="isCollapsed ? Expand : Fold" />
          </el-icon>
          <span v-if="!isCollapsed">收起菜单</span>
        </button>
      </div>
    </aside>

    <!-- ============ 右侧主体 ============ -->
    <div class="layout-main">
      <header class="layout-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentPageTitle">{{ currentPageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <ThemeToggle />
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-info">
              <div class="user-avatar" :style="{ background: 'var(--sjdt-avatar-grad)' }">
                {{ userStore.user?.nickname?.charAt(0) || 'U' }}
              </div>
              <span class="username">{{ userStore.user?.nickname || userStore.user?.username }}</span>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="layout-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useWorkerStore } from '@/stores/worker'
import { ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  SwitchButton,
  Odometer,
  List,
  Document,
  Cpu,
  PriceTag,
  Clock,
  Expand,
  Fold,
} from '@element-plus/icons-vue'
import ThemeToggle from '@/components/ThemeToggle.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const workerStore = useWorkerStore()

/** 侧边栏是否收起 */
const isCollapsed = ref(false)

/** 在线执行器数量（侧边栏徽标） */
const onlineCount = computed(() => workerStore.onlineCount)

/** 侧边栏菜单分组：监控 / 调度 / 资源 / 基础 */
const menuGroups = [
  {
    label: '监控',
    items: [{ path: '/dashboard', title: '仪表盘', icon: Odometer }],
  },
  {
    label: '调度',
    items: [
      { path: '/tasks', title: '任务管理', icon: List },
      { path: '/task-runs', title: '运行记录', icon: Clock },
    ],
  },
  {
    label: '资源',
    items: [
      { path: '/workers', title: '执行器', icon: Cpu },
      { path: '/scripts', title: '脚本管理', icon: Document },
    ],
  },
  {
    label: '基础',
    items: [{ path: '/tags', title: '标签管理', icon: PriceTag }],
  },
]

/**
 * 判断菜单项是否激活：路径相等或为该路径的子路由
 * @param path 菜单项路径
 */
function isActive(path: string): boolean {
  return route.path === path || route.path.startsWith(path + '/')
}

/** 当前页面标题（面包屑用） */
const currentPageTitle = computed(() => {
  const meta = route.meta as any
  return meta?.title || ''
})

/**
 * 切换侧边栏收起/展开
 */
function toggleCollapse(): void {
  isCollapsed.value = !isCollapsed.value
}

/**
 * 用户下拉菜单命令处理（退出登录）
 * @param command 命令标识
 */
function handleCommand(command: string): void {
  switch (command) {
    case 'logout':
      ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }).then(() => {
        userStore.logout()
        router.push('/login')
      })
      break
  }
}

onMounted(() => {
  // 拉取执行器列表以显示在线数徽标（失败静默，不阻塞布局）
  workerStore.fetchWorkers({ pageNum: 1, pageSize: 1000 }).catch(() => {})
})
</script>

<style scoped>
.layout-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ---------- 侧边栏（随主题切换，与内容区分层） ---------- */
.sidebar {
  width: 224px;
  background: var(--sjdt-sidebar-bg);
  border-right: 1px solid var(--sjdt-sidebar-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width .25s ease, background-color .25s ease, border-color .25s ease;
  overflow: hidden;
}

.sidebar.is-collapsed {
  width: 64px;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  border-bottom: 1px solid var(--sjdt-sidebar-divider);
  flex-shrink: 0;
}

.sidebar.is-collapsed .logo {
  justify-content: center;
  padding: 0;
}

.logo-mark {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--sjdt-logo-grad);
  color: #FFFFFF;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.logo-mark svg {
  width: 19px;
  height: 19px;
}

.logo-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--sjdt-sidebar-heading);
  letter-spacing: .5px;
  white-space: nowrap;
}

/* ---------- 菜单 ---------- */
.menu-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px 10px;
}

.menu-scroll::-webkit-scrollbar {
  width: 4px;
}

.menu-scroll::-webkit-scrollbar-thumb {
  background: var(--sjdt-sidebar-border);
  border-radius: 4px;
}

.menu-group-title {
  font-size: 11px;
  letter-spacing: 2px;
  padding: 14px 10px 6px;
  color: var(--sjdt-sidebar-group);
  white-space: nowrap;
}

.menu-group-divider {
  height: 1px;
  background: var(--sjdt-sidebar-divider);
  margin: 10px 8px;
}

.menu-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 8px;
  color: var(--sjdt-sidebar-text);
  font-size: 14px;
  text-decoration: none;
  margin-bottom: 2px;
  transition: background .18s ease, color .18s ease;
}

.sidebar.is-collapsed .menu-item {
  justify-content: center;
  padding: 10px 0;
}

.menu-item:hover {
  background: var(--sjdt-sidebar-hover);
  color: var(--sjdt-sidebar-hover-text);
}

.menu-item.active {
  background: var(--sjdt-sidebar-active-bg);
  color: var(--sjdt-sidebar-active-text);
  font-weight: 600;
}

/* 激活项左侧指示条 */
.menu-item.active::before {
  content: '';
  position: absolute;
  left: -10px;
  top: 20%;
  height: 60%;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--sjdt-primary);
}

.menu-item-text {
  flex: 1;
  white-space: nowrap;
}

/* 在线数徽标 */
.menu-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--sjdt-success-soft);
  color: var(--sjdt-success);
}

.menu-badge.collapsed {
  padding: 2px;
  position: absolute;
  top: 4px;
  right: 4px;
}

.badge-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--sjdt-success);
  animation: sjdt-pulse-success 2s infinite;
}

/* ---------- 侧边栏底部 ---------- */
.sidebar-foot {
  padding: 12px;
  border-top: 1px solid var(--sjdt-sidebar-divider);
  display: flex;
  justify-content: center;
  flex-shrink: 0;
}

.collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 7px;
  border-radius: 8px;
  border: 1px solid var(--sjdt-border);
  background: transparent;
  color: var(--sjdt-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all .18s ease;
  white-space: nowrap;
}

.collapse-btn:hover {
  background: var(--sjdt-sidebar-hover);
  color: var(--sjdt-sidebar-hover-text);
  border-color: var(--sjdt-primary);
}

/* ---------- 右侧主体（内容区底色为三层阶梯中最暗层） ---------- */
.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--sjdt-bg);
}

.layout-header {
  height: 60px;
  flex-shrink: 0;
  background: var(--sjdt-surface);
  border-bottom: 1px solid var(--sjdt-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  z-index: 10;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 8px;
  transition: background .18s ease;
}

.user-info:hover {
  background: var(--sjdt-primary-soft);
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: #FFFFFF;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.username {
  font-size: 13px;
  color: var(--sjdt-text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-info .el-icon {
  color: var(--sjdt-muted);
}

/* ---------- 内容区 ---------- */
.layout-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity .2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
