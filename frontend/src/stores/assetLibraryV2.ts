import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as api from '@/api/assetLibraryV2'
import type {
  KnowledgeBase,
  LibraryAsset,
  LibraryView,
} from '@/types/contracts/assetLibraryV2'

function replaceById<T>(items: T[], item: T, id: (value: T) => string) {
  const index = items.findIndex((existing) => id(existing) === id(item))
  if (index === -1) items.unshift(item)
  else items[index] = item
}

export const useAssetLibraryV2Store = defineStore('assetLibraryV2', () => {
  const assets = ref<LibraryAsset[]>([])
  const trashedAssets = ref<LibraryAsset[]>([])
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const trashedKnowledgeBases = ref<KnowledgeBase[]>([])
  const assetCursor = ref<string | null>(null)
  const trashAssetCursor = ref<string | null>(null)
  const knowledgeBaseCursor = ref<string | null>(null)
  const trashKnowledgeBaseCursor = ref<string | null>(null)
  const loading = ref(false)
  const mutating = ref(false)
  const error = ref('')

  async function loadAssets(view: LibraryView, append = false) {
    const target = view === 'library' ? assets : trashedAssets
    const cursor = view === 'library' ? assetCursor : trashAssetCursor
    const page = await api.listAssets(view, append ? cursor.value : null)
    target.value = append ? [...target.value, ...page.items] : page.items
    cursor.value = page.nextCursor
  }

  async function loadKnowledgeBases(view: LibraryView, append = false) {
    const target = view === 'library' ? knowledgeBases : trashedKnowledgeBases
    const cursor = view === 'library' ? knowledgeBaseCursor : trashKnowledgeBaseCursor
    const page = await api.listKnowledgeBases(view, append ? cursor.value : null)
    target.value = append ? [...target.value, ...page.items] : page.items
    cursor.value = page.nextCursor
  }

  async function refresh(view: LibraryView = 'library') {
    loading.value = true
    error.value = ''
    try {
      await Promise.all([loadAssets(view), loadKnowledgeBases(view)])
    } catch (cause) {
      error.value = api.libraryError(cause, '资料库加载失败。').message
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function mutate<T>(operation: () => Promise<T>) {
    mutating.value = true
    error.value = ''
    try {
      return await operation()
    } catch (cause) {
      error.value = api.libraryError(cause, '操作失败。').message
      throw cause
    } finally {
      mutating.value = false
    }
  }

  async function createKnowledgeBase(name: string, description: string) {
    const detail = await mutate(() => api.createKnowledgeBase(name, description))
    replaceById(knowledgeBases.value, detail.knowledgeBase, (item) => item.knowledgeBaseId)
    return detail.knowledgeBase
  }

  async function updateKnowledgeBase(
    knowledgeBaseId: string,
    payload: { name?: string; description?: string },
  ) {
    const detail = await mutate(() => api.updateKnowledgeBase(knowledgeBaseId, payload))
    replaceById(knowledgeBases.value, detail.knowledgeBase, (item) => item.knowledgeBaseId)
    return detail.knowledgeBase
  }

  async function moveKnowledgeBaseToTrash(knowledgeBaseId: string) {
    const detail = await mutate(() => api.trashKnowledgeBase(knowledgeBaseId))
    knowledgeBases.value = knowledgeBases.value.filter((item) => item.knowledgeBaseId !== knowledgeBaseId)
    replaceById(trashedKnowledgeBases.value, detail.knowledgeBase, (item) => item.knowledgeBaseId)
  }

  async function restoreKnowledgeBase(knowledgeBaseId: string) {
    const detail = await mutate(() => api.restoreKnowledgeBase(knowledgeBaseId))
    trashedKnowledgeBases.value = trashedKnowledgeBases.value.filter(
      (item) => item.knowledgeBaseId !== knowledgeBaseId,
    )
    replaceById(knowledgeBases.value, detail.knowledgeBase, (item) => item.knowledgeBaseId)
  }

  async function permanentlyDeleteKnowledgeBase(knowledgeBaseId: string) {
    await mutate(() => api.purgeKnowledgeBase(knowledgeBaseId))
    trashedKnowledgeBases.value = trashedKnowledgeBases.value.filter(
      (item) => item.knowledgeBaseId !== knowledgeBaseId,
    )
  }

  async function renameAsset(assetId: string, name: string) {
    const item = await mutate(() => api.renameAsset(assetId, name))
    replaceById(assets.value, item, (asset) => asset.assetId)
    return item
  }

  async function moveAssetToTrash(assetId: string) {
    const item = await mutate(() => api.trashAsset(assetId))
    assets.value = assets.value.filter((asset) => asset.assetId !== assetId)
    replaceById(trashedAssets.value, item, (asset) => asset.assetId)
  }

  async function restoreAsset(assetId: string) {
    const item = await mutate(() => api.restoreAsset(assetId))
    trashedAssets.value = trashedAssets.value.filter((asset) => asset.assetId !== assetId)
    replaceById(assets.value, item, (asset) => asset.assetId)
  }

  async function requestAssetPurge(assetId: string) {
    return mutate(() => api.purgeAsset(assetId))
  }

  function forgetPurgedAsset(assetId: string) {
    trashedAssets.value = trashedAssets.value.filter((asset) => asset.assetId !== assetId)
  }

  function upsertUploadedAsset(item: LibraryAsset) {
    replaceById(assets.value, item, (asset) => asset.assetId)
  }

  function clear() {
    assets.value = []
    trashedAssets.value = []
    knowledgeBases.value = []
    trashedKnowledgeBases.value = []
    assetCursor.value = null
    trashAssetCursor.value = null
    knowledgeBaseCursor.value = null
    trashKnowledgeBaseCursor.value = null
    loading.value = false
    mutating.value = false
    error.value = ''
  }

  return {
    assets,
    trashedAssets,
    knowledgeBases,
    trashedKnowledgeBases,
    assetCursor,
    trashAssetCursor,
    knowledgeBaseCursor,
    trashKnowledgeBaseCursor,
    loading,
    mutating,
    error,
    refresh,
    loadAssets,
    loadKnowledgeBases,
    createKnowledgeBase,
    updateKnowledgeBase,
    moveKnowledgeBaseToTrash,
    restoreKnowledgeBase,
    permanentlyDeleteKnowledgeBase,
    renameAsset,
    moveAssetToTrash,
    restoreAsset,
    requestAssetPurge,
    forgetPurgedAsset,
    upsertUploadedAsset,
    clear,
  }
})
