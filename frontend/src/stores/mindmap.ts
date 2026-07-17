import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as mindmapApi from '@/api/mindmap'

export const useMindMapStore = defineStore('mindmap', () => {
  const mindMapList = ref<mindmapApi.MindMap[]>([])
  const currentMapId = ref<number | null>(null)
  const mapTitle = ref('未命名思维导图')

  const treeData = ref<any>({
    data: { text: '中心主题' },
    children: []
  })

  const pinnedIds = ref<number[]>(JSON.parse(localStorage.getItem('llm.mindmap.pinned') || '[]'))

  const togglePin = (id: number) => {
    const index = pinnedIds.value.indexOf(id)
    if (index === -1) {
      pinnedIds.value.push(id)
    } else {
      pinnedIds.value.splice(index, 1)
    }
    localStorage.setItem('llm.mindmap.pinned', JSON.stringify(pinnedIds.value))
  }

  const isPinned = (id: number) => pinnedIds.value.includes(id)

  const fetchList = async (knowledgeBaseId?: number | null) => {
    try {
      const list = await mindmapApi.getMindMapList(knowledgeBaseId)
      mindMapList.value = list
      return list
    } catch (error) {
      console.error('Failed to fetch mindmap list:', error)
      mindMapList.value = []
      return []
    }
  }

  const getMapById = async (id: number) => {
    try {
      const map = await mindmapApi.getMindMapDetail(id)
      currentMapId.value = map.id
      mapTitle.value = map.title
      if (map.content) {
        try {
          treeData.value = JSON.parse(map.content)
        } catch (e) {
          console.error('Failed to parse mindmap content', e)
        }
      } else {
        treeData.value = { data: { text: map.title }, children: [] }
      }
      return map
    } catch (error) {
      console.error('Failed to fetch mindmap detail:', error)
      return null
    }
  }

  const createMap = async (title: string, knowledgeBaseId: number | null = null) => {
    const initialData = { data: { text: title }, children: [] }
    const id = await mindmapApi.createMindMap({
      title,
      knowledgeBaseId,
      content: JSON.stringify(initialData)
    })
    await fetchList()
    return id
  }

  const updateMap = async (id: number, title?: string, content?: string, knowledgeBaseId?: number | null) => {
    const result = await mindmapApi.updateMindMap({ id, title, content, knowledgeBaseId })
    if (title && id === currentMapId.value) {
      mapTitle.value = title
    }
    await fetchList()
    return result
  }

  const deleteMap = async (id: number) => {
    await mindmapApi.deleteMindMap(id)
    mindMapList.value = mindMapList.value.filter(item => item.id !== id)
  }

  const renameMap = async (id: number, newTitle: string) => {
    await updateMap(id, newTitle)
  }

  const moveToKB = async (id: number, knowledgeBaseId: number | null) => {
    await updateMap(id, undefined, undefined, knowledgeBaseId)
  }

  const initEmptyMap = (title: string = '未命名思维导图') => {
    treeData.value = {
      data: { text: title },
      children: []
    }
    mapTitle.value = title
    currentMapId.value = null
  }

  function clearAll() {
    mindMapList.value = []
    currentMapId.value = null
    mapTitle.value = '未命名思维导图'
    treeData.value = {
      data: { text: '中心主题' },
      children: []
    }
    pinnedIds.value = []
    localStorage.removeItem('llm.mindmap.pinned')
  }

  return {
    mindMapList,
    currentMapId,
    mapTitle,
    treeData,
    pinnedIds,
    togglePin,
    isPinned,
    fetchList,
    getMapById,
    createMap,
    updateMap,
    deleteMap,
    renameMap,
    moveToKB,
    initEmptyMap,
    clearAll
  }
})
