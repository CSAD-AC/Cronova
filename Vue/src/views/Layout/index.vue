<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="layout-aside" :class="{ 'is-collapsed': isCollapsed }">
      <div class="logo-container">
        <img src="@/assets/logo.svg" alt="Logo" class="logo" />
        <span v-if="!isCollapsed" class="logo-text">SJDT调度器</span>
      </div>
      
      <el-scrollbar class="sidebar-scrollbar">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapsed"
          :collapse-transition="false"
          router
          class="sidebar-menu"
        >
          <template v-for="item in menuItems" :key="item.path">
            <el-menu-item :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.title }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
      
      <div class="sidebar-footer">
        <el-button 
          :icon="isCollapsed ? 'Expand' : 'Fold'" 
          text
          @click="toggleCollapse"
        >
          <el-icon><component :is="isCollapsed ? 'Expand' : 'Fold'" /></el-icon>
        </el-button>
      </div>
    </el-aside>
    
    <el-container class="layout-main">
      <el-header class="layout-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentPageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" :src="userStore.user?.avatar">
                {{ userStore.user?.nickname?.charAt(0) || 'U' }}
              </el-avatar>
              <span class="username">{{ userStore.user?.nickname || userStore.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
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
      </el-header>
      
      <el-main class="layout-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  User,
  SwitchButton,
  Odometer,
  List,
  Document,
  Cpu,
  PriceTag,
  Setting,
  Clock
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)

const menuItems = [
  { path: '/dashboard', title: '仪表盘', icon: Odometer },
  { path: '/tasks', title: '任务管理', icon: List },
  { path: '/task-runs', title: '运行记录', icon: Clock },
  { path: '/workers', title: '执行器', icon: Cpu },
  { path: '/scripts', title: '脚本管理', icon: Document },
  { path: '/tags', title: '标签管理', icon: PriceTag },
]

const activeMenu = computed(() => route.path)

const currentPageTitle = computed(() => {
  const meta = route.meta as any
  return meta?.title || ''
})

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}

function handleCommand(command: string) {
  switch (command) {
    case 'logout':
      ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }).then(() => {
        userStore.logout()
      })
      break
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background: linear-gradient(180deg, #1e3a8a 0%, #1e40af 100%);
  color: white;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  overflow: hidden;
}

.layout-aside.is-collapsed {
  width: 64px !important;
}

.logo-container {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo {
  width: 32px;
  height: 32px;
}

.logo-text {
  margin-left: 12px;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}

.sidebar-scrollbar {
  flex: 1;
  overflow-x: hidden;
}

.sidebar-menu {
  border-right: none;
  background: transparent;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 220px;
}

:deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.7);
}

:deep(.el-menu-item:hover),
:deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  justify-content: center;
}

.sidebar-footer .el-button {
  color: rgba(255, 255, 255, 0.7);
}

.sidebar-footer .el-button:hover {
  color: white;
  background: rgba(255, 255, 255, 0.1);
}

.layout-main {
  flex-direction: column;
  background: #f8fafc;
}

.layout-header {
  height: 64px;
  background: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-info:hover {
  background: #f1f5f9;
}

.username {
  margin: 0 8px;
  font-size: 14px;
  color: #1e293b;
}

.layout-content {
  padding: 24px;
  overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
