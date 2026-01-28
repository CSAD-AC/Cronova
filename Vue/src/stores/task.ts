import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { TaskVO, TaskCreateDTO, TaskUpdateDTO } from '@/types'
import { taskApi } from '@/services'

export const useTaskStore = defineStore('task', () => {
  const tasks = ref<TaskVO[]>([])
  const currentTask = ref<TaskVO | null>(null)
  const loading = ref(false)
  const total = ref(0)

  async function fetchTasks(params: any = {}) {
    loading.value = true
    try {
      const response = await taskApi.list(params)
      // 处理API直接返回数组或分页格式的情况
      if (Array.isArray(response)) {
        tasks.value = response
        total.value = response.length
      } else {
        tasks.value = response.records || []
        total.value = response.total || 0
      }
    } finally {
      loading.value = false
    }
  }

  async function fetchTaskById(id: string) {
    loading.value = true
    try {
      currentTask.value = await taskApi.getById(id)
      return currentTask.value
    } finally {
      loading.value = false
    }
  }

  async function createTask(data: TaskCreateDTO) {
    return await taskApi.create(data)
  }

  async function updateTask(data: TaskUpdateDTO) {
    return await taskApi.update(data)
  }

  async function deleteTask(id: string) {
    return await taskApi.delete(id)
  }

  async function enableTask(id: string) {
    return await taskApi.enable(id)
  }

  async function disableTask(id: string) {
    return await taskApi.disable(id)
  }

  async function publishTask(id: string) {
    return await taskApi.publish(id)
  }

  function clearCurrentTask() {
    currentTask.value = null
  }

  return {
    tasks,
    currentTask,
    loading,
    total,
    fetchTasks,
    fetchTaskById,
    createTask,
    updateTask,
    deleteTask,
    enableTask,
    disableTask,
    publishTask,
    clearCurrentTask,
  }
})
