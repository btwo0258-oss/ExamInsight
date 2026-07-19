import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import * as modelApi from '@/api/model'

const STORAGE_KEY = 'llm.default_model'

export const useModelStore = defineStore('model', () => {
  const list = ref<modelApi.ModelInfo[]>([])
  const current = ref<string>(localStorage.getItem(STORAGE_KEY) || 'qwen-plus-2025-07-28')
  const isLoading = ref(false)

  const currentModel = computed(() => current.value)

  async function fetchList() {
    isLoading.value = true
    try {
      list.value = await modelApi.listModels()
      if (list.value.length && !list.value.some((model) => model.name === current.value)) {
        setCurrent(list.value[0]!.name)
      }
    } finally {
      isLoading.value = false
    }
  }

  function setCurrent(name: string) {
    current.value = name
    localStorage.setItem(STORAGE_KEY, name)
  }

  return { list, currentModel, isLoading, fetchList, setCurrent }
})
