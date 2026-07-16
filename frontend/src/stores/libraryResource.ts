import { ref } from 'vue'
import { defineStore } from 'pinia'
import { mediaRepository } from '@/repositories/media'
import { getMediaSource, isImageFile } from '@/utils/mediaFile'
import {
  deleteLibraryResource,
  downloadLibraryResource,
  getLibraryResources,
  listLibraryResources,
  moveLibraryResource,
  renameLibraryResource,
  retryLibraryResource,
  saveLibraryResources,
  uploadLibraryResource,
} from '@/api/libraryResource'
import type { LearningResource } from '@/mock'
import type { LibraryResourceDto, LibraryResourceSource, LibraryResourceCategory } from '@/types/contracts/library'

export type { LibraryResourceSource, LibraryResourceCategory }
export type LibraryResource = LibraryResourceDto

function fileType(file: File) {
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (extension === 'pdf') return 'PDF'
  if (extension === 'doc' || extension === 'docx') return 'Word'
  if (extension === 'md') return 'Markdown'
  if (extension === 'txt') return 'TXT'
  if (file.type.startsWith('image/')) return '图片'
  return extension?.toUpperCase() || '文件'
}

function fileSize(size: number) {
  if (size < 1024 * 1024) return `${Math.max(1, Math.round(size / 1024))} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function generatedType(group: LearningResource['group']) {
  if (group === '学习方案') return 'Markdown'
  if (group === 'PPT') return 'PPT'
  if (group === '思维导图') return '思维导图'
  if (group === '代码案例') return 'ZIP'
  if (group === '图片') return '图片'
  if (group === '个性化学习手册') return 'Markdown'
  return 'PDF'
}

function clientRequestId() {
  return globalThis.crypto?.randomUUID?.() ?? `media-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export const useLibraryResourceStore = defineStore('libraryResource', () => {
  const resources = ref<LibraryResource[]>(getLibraryResources())
  const isLoading = ref(false)
  const isMutating = ref(false)
  const errorMessage = ref<string | null>(null)
  let sequence = 0

  function persist() {
    saveLibraryResources(resources.value)
  }

  function upsert(item: LibraryResource) {
    const index = resources.value.findIndex((resource) => resource.id === item.id)
    if (index === -1) resources.value.unshift(item)
    else resources.value[index] = item
  }

  async function fetchList(libraryId?: number) {
    if (isLoading.value) return
    isLoading.value = true
    errorMessage.value = null
    try {
      const items = await listLibraryResources(libraryId)
      if (libraryId === undefined) resources.value = items
      else {
        resources.value = [
          ...resources.value.filter((item) => item.libraryId !== libraryId),
          ...items,
        ]
      }
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '获取资料失败'
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function uploadFiles(
    files: File[],
    source: LibraryResourceSource,
    projectId: number | null = null,
    libraryId: number | null = null,
  ) {
    if (isMutating.value) return []
    isMutating.value = true
    errorMessage.value = null
    const uploaded: LibraryResource[] = []
    try {
      for (const file of files) {
        let item: LibraryResource
        if (isImageFile(file)) {
          const asset = await mediaRepository.uploadImage(file, {
            source: getMediaSource(file),
            purpose: source === '智能学习上传' ? 'learning-input' : 'library-resource',
            libraryId,
            learningProjectId: projectId,
            clientRequestId: clientRequestId(),
          })
          item = {
            id: `media:${asset.id}`,
            name: asset.fileName,
            type: '图片',
            size: fileSize(asset.size),
            status: asset.status === 'failed' ? 'failed' : asset.status === 'ready' ? 'ready' : 'processing',
            errorMessage: asset.errorMessage,
            updatedAt: '刚刚',
            category: 'image',
            source,
            projectId,
            libraryId,
            externalKey: asset.id,
          }
        } else {
          item = await uploadLibraryResource(file, libraryId, projectId)
          item.source = source
        }
        upsert(item)
        uploaded.push(item)
      }
      persist()
      return uploaded
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '上传资料失败'
      throw error
    } finally {
      isMutating.value = false
    }
  }

  async function remove(id: string) {
    isMutating.value = true
    errorMessage.value = null
    try {
      await deleteLibraryResource(id)
      resources.value = resources.value.filter((item) => item.id !== id)
      persist()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '删除资料失败'
      throw error
    } finally {
      isMutating.value = false
    }
  }

  async function retry(id: string) {
    isMutating.value = true
    errorMessage.value = null
    try {
      upsert(await retryLibraryResource(id))
      persist()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '重试解析失败'
      throw error
    } finally {
      isMutating.value = false
    }
  }

  async function rename(id: string, name: string) {
    if (!name.trim()) return
    isMutating.value = true
    errorMessage.value = null
    try {
      upsert(await renameLibraryResource(id, name.trim()))
      persist()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '重命名失败'
      throw error
    } finally {
      isMutating.value = false
    }
  }

  async function move(id: string, libraryId: number | null) {
    isMutating.value = true
    errorMessage.value = null
    try {
      upsert(await moveLibraryResource(id, libraryId))
      persist()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '移动资料失败'
      throw error
    } finally {
      isMutating.value = false
    }
  }

  async function download(id: string, name: string) {
    isMutating.value = true
    errorMessage.value = null
    try {
      const blob = await downloadLibraryResource(id)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = name
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '下载资料失败'
      throw error
    } finally {
      isMutating.value = false
    }
  }

  function addFile(
    file: File,
    source: LibraryResourceSource,
    projectId: number | null = null,
    libraryId: number | null = null,
  ) {
    const signature = `${source}:${file.name}:${file.size}:${file.lastModified}`
    const existing = resources.value.find((item) => item.externalKey === signature)
    if (existing) {
      existing.projectId = projectId
      existing.libraryId = libraryId
      existing.updatedAt = '刚刚'
      persist()
      return existing
    }

    const item: LibraryResource = {
      id: `upload-${Date.now()}-${sequence++}`,
      name: file.name,
      type: fileType(file),
      size: fileSize(file.size),
      status: 'ready',
      updatedAt: '刚刚',
      category: file.type.startsWith('image/') ? 'image' : 'file',
      source,
      projectId,
      libraryId,
      externalKey: signature,
    }
    resources.value.unshift(item)
    persist()
    return item
  }

  function addFiles(
    files: File[],
    source: LibraryResourceSource,
    projectId: number | null = null,
    libraryId: number | null = null,
  ) {
    return files.map((file) => addFile(file, source, projectId, libraryId))
  }

  function addGeneratedResource(
    resource: LearningResource,
    source: Extract<LibraryResourceSource, '智能学习生成'>,
    planId: number,
    projectId: number | null,
    libraryId: number,
  ) {
    const externalKey = `learning:${planId}:${resource.id}`
    const existing = resources.value.find((item) => item.externalKey === externalKey)
    if (existing) {
      existing.name = resource.fileName ?? resource.title
      existing.updatedAt = '刚刚'
      existing.status = resource.status === '生成中' ? 'processing' : 'ready'
      persist()
      return existing
    }

    const item: LibraryResource = {
      id: `generated-${planId}-${resource.id}`,
      name: resource.fileName ?? resource.title,
      type: generatedType(resource.group),
      size: 'AI 生成',
      status: resource.status === '生成中' ? 'processing' : 'ready',
      updatedAt: '刚刚',
      category: resource.group === '思维导图' ? 'mindmap' : resource.group === '图片' ? 'image' : 'file',
      source,
      projectId,
      libraryId,
      externalKey,
    }
    resources.value.unshift(item)
    persist()
    return item
  }

  function addChatGenerated(name: string, libraryId: number | null = null) {
    const item: LibraryResource = {
      id: `chat-generated-${Date.now()}-${sequence++}`,
      name,
      type: '思维导图',
      size: 'AI 生成',
      status: 'ready',
      updatedAt: '刚刚',
      category: 'mindmap',
      source: '聊天生成',
      projectId: null,
      libraryId,
    }
    resources.value.unshift(item)
    persist()
    return item
  }

  function addPlanExportMarkdown(
    name: string,
    planId: number,
    projectId: number | null,
    libraryId: number,
  ) {
    const externalKey = `learning:${planId}:export-markdown`
    const existing = resources.value.find((item) => item.externalKey === externalKey)
    if (existing) {
      existing.name = name
      existing.updatedAt = '刚刚'
      existing.status = 'ready'
      existing.projectId = projectId
      existing.libraryId = libraryId
      persist()
      return existing
    }

    const item: LibraryResource = {
      id: `generated-${planId}-export-markdown`,
      name,
      type: 'Markdown',
      size: 'AI 生成',
      status: 'ready',
      updatedAt: '刚刚',
      category: 'file',
      source: '智能学习生成',
      projectId,
      libraryId,
      externalKey,
    }
    resources.value.unshift(item)
    persist()
    return item
  }

  function clearError() {
    errorMessage.value = null
  }

  function clearAll() {
    resources.value = getLibraryResources()
    isLoading.value = false
    isMutating.value = false
    errorMessage.value = null
  }

  return {
    resources,
    isLoading,
    isMutating,
    errorMessage,
    fetchList,
    uploadFiles,
    remove,
    retry,
    rename,
    move,
    download,
    clearError,
    clearAll,
    addFile,
    addFiles,
    addGeneratedResource,
    addChatGenerated,
    addPlanExportMarkdown,
  }
})
