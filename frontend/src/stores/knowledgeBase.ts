import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  getKnowledgeBase,
  getKnowledgeBases,
  updateKnowledgeBase,
} from '@/api/knowledgeBase'
import type { KnowledgeBase } from '@/api/knowledgeBase'

export const useKnowledgeBaseStore = defineStore('knowledgeBase', () => {
  const list = ref<KnowledgeBase[]>([])
  const current = ref<KnowledgeBase | null>(null)
  const editingKnowledgeBase = ref<KnowledgeBase | null>(null)
  const isInitialized = ref(false)
  const isLoading = ref(false)
  const errorMessage = ref<string | null>(null)

  async function fetchList() {
    if (isLoading.value) return
    isLoading.value = true
    errorMessage.value = null
    try {
      list.value = await getKnowledgeBases()
      isInitialized.value = true
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '获取资料库失败'
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function fetchAll() {
    await fetchList()
  }

  async function getDetail(id: number) {
    current.value = await getKnowledgeBase(id)
    const index = list.value.findIndex((item) => item.id === current.value?.id)
    if (index === -1) list.value.push(current.value)
    else list.value[index] = current.value
    return current.value
  }

  async function create(data: Partial<KnowledgeBase>) {
    const item = await createKnowledgeBase(data)
    list.value = list.value.filter((existing) => existing.id !== item.id)
    list.value.unshift(item)
    return item
  }

  async function update(data: KnowledgeBase) {
    const item = await updateKnowledgeBase(data)
    const index = list.value.findIndex((existing) => existing.id === item.id)
    if (index !== -1) list.value[index] = item
    if (current.value?.id === item.id) current.value = item
    return item
  }

  async function remove(id: number) {
    await deleteKnowledgeBase(id)
    list.value = list.value.filter((item) => item.id !== id)
    if (current.value?.id === id) current.value = null
  }

  function setEditingKnowledgeBase(knowledgeBase: KnowledgeBase | null) {
    editingKnowledgeBase.value = knowledgeBase
  }

  function clearAll() {
    list.value = []
    current.value = null
    editingKnowledgeBase.value = null
    isInitialized.value = false
    isLoading.value = false
    errorMessage.value = null
  }

  return {
    list,
    current,
    editingKnowledgeBase,
    isInitialized,
    isLoading,
    errorMessage,
    fetchAll,
    fetchList,
    getDetail,
    create,
    update,
    remove,
    setEditingKnowledgeBase,
    clearAll,
  }
})
