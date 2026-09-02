import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Auth/Login.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('@/views/Layout/index.vue'),
      meta: { requiresAuth: true },
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/Dashboard/index.vue'),
          meta: { title: '仪表盘', icon: 'Odometer' },
        },
        {
          path: 'tasks',
          name: 'tasks',
          component: () => import('@/views/Tasks/List.vue'),
          meta: { title: '任务管理', icon: 'List' },
        },
        {
          path: 'tasks/create',
          name: 'task-create',
          component: () => import('@/views/Tasks/Create.vue'),
          meta: { title: '创建任务', icon: 'Plus' },
        },
        {
          path: 'tasks/:id',
          name: 'task-detail',
          component: () => import('@/views/Tasks/Detail.vue'),
          meta: { title: '任务详情', icon: 'Document' },
        },
        {
          path: 'tasks/:id/edit',
          name: 'task-edit',
          component: () => import('@/views/Tasks/Edit.vue'),
          meta: { title: '编辑任务', icon: 'Edit' },
        },
        {
          path: 'task-runs',
          name: 'task-runs',
          component: () => import('@/views/TaskRuns/index.vue'),
          meta: { title: '运行记录', icon: 'Clock' },
        },
        {
          path: 'workers',
          name: 'workers',
          component: () => import('@/views/Workers/List.vue'),
          meta: { title: '执行器管理', icon: 'Cpu' },
        },
        {
          path: 'workers/:id',
          name: 'worker-detail',
          component: () => import('@/views/Workers/Detail.vue'),
          meta: { title: '执行器详情', icon: 'Monitor' },
        },
        {
          path: 'scripts',
          name: 'scripts',
          component: () => import('@/views/Scripts/List.vue'),
          meta: { title: '脚本管理', icon: 'Document' },
        },
        {
          path: 'tags',
          name: 'tags',
          component: () => import('@/views/Tags/index.vue'),
          meta: { title: '标签管理', icon: 'PriceTag' },
        }
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFound.vue'),
    },
  ],
})

// 登录守卫：未登录访问受保护页跳登录页；已登录访问登录页则回仪表盘
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    next({ name: 'login', query: { redirect: to.fullPath } })
  } else if (to.name === 'login' && userStore.isLoggedIn) {
    next({ name: 'dashboard' })
  } else {
    next()
  }
})

export default router
