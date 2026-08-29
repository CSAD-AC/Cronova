import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { WorkerVO } from '@/types'
import { workerApi } from '@/services'

export const useWorkerStore = defineStore('worker', () => {
  const workers = ref<WorkerVO[]>([])
  const currentWorker = ref<WorkerVO | null>(null)
  const loading = ref(false)
  const total = ref(0)

  const onlineCount = computed(() => (workers.value || []).filter(w => w.status === 'online').length)
  const offlineCount = computed(() => (workers.value || []).filter(w => w.status === 'offline').length)

  async function fetchWorkers(params: any = {}) {
    loading.value = true
    try {
      const response = await workerApi.list(params)
      // 处理API直接返回数组的情况
      if (Array.isArray(response)) {
        workers.value = response
        total.value = response.length
      } else {
        // 如果是标准分页格式
        workers.value = response.records || []
        total.value = response.total || 0
      }
    } finally {
      loading.value = false
    }
  }

  async function fetchWorkerById(id: string) {
    loading.value = true
    try {
      currentWorker.value = await workerApi.getById(id)
      return currentWorker.value
    } finally {
      loading.value = false
    }
  }

  async function deleteWorker(id: string) {
    return await workerApi.delete(id)
  }

  function clearCurrentWorker() {
    currentWorker.value = null
  }

  return {
    workers,
    currentWorker,
    loading,
    total,
    onlineCount,
    offlineCount,
    fetchWorkers,
    fetchWorkerById,
    deleteWorker,
    clearCurrentWorker,
  }
})
