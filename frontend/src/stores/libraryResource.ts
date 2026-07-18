import { ref } from 'vue'
import { defineStore } from 'pinia'
import { mediaRepository } from '@/repositories/media'
import { rememberMockLibraryResourceFile, resourceFileType, resourceFormat } from '@/repositories/libraryResource'
import { getMediaSource, isAudioFile, isImageFile } from '@/utils/file'
import {
  deleteLibraryResource,
  downloadLibraryResource,
  listLibraryResources,
  renameLibraryResource,
  retryLibraryResource,
  saveLibraryResources,
  updateLibraryResourceAssociations,
  uploadLibraryResource,
} from '@/api/libraryResource'
import type { LearningResource } from '@/mock'
import type {
  LibraryResourceDto,
  ResourceAssociations,
  ResourceFileType,
  ResourceOrigin,
} from '@/types/contracts/library'

export type LibraryResource = LibraryResourceDto

function generatedFormat(group: LearningResource['group']) {
  if (group === '学习方案' || group === '个性化学习手册') return 'Markdown'
  if (group === 'PPT') return 'PPT'
  if (group === '思维导图') return '思维导图'
  if (group === '代码案例') return 'ZIP'
  if (group === '图片') return '图片'
  if (group === '文档') return '文档'
  if (group === '电子表格') return 'XLSX'
  if (group === '音频') return '音频'
  return 'PDF'
}

function generatedFileType(group: LearningResource['group']): ResourceFileType {
  if (group === 'PPT') return 'presentation'
  if (group === '思维导图') return 'mindmap'
  if (group === '代码案例') return 'archive'
  if (group === '图片') return 'image'
  if (group === '电子表格') return 'spreadsheet'
  if (group === '音频') return 'audio'
  if (group === '其他文件') return 'other'
  return 'document'
}

function clientRequestId() {
  return globalThis.crypto?.randomUUID?.() ?? `media-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export const useLibraryResourceStore = defineStore('libraryResource', () => {
  // Do not expose mock seed data before a user has authenticated.
  const resources = ref<LibraryResource[]>([])
  const isLoading = ref(false)
  const isMutating = ref(false)
  const errorMessage = ref<string | null>(null)
  let sequence = 0

  function persist() {
    saveLibraryResources(resources.value)
  }

  function upsert(item: LibraryResource) {
    const index = resources.value.findIndex((resource) => resource.resourceId === item.resourceId)
    if (index === -1) resources.value.unshift(item)
    else resources.value[index] = item
  }

  async function fetchList(knowledgeBaseId?: number) {
    if (isLoading.value) return
    isLoading.value = true
    errorMessage.value = null
    try {
      const items = await listLibraryResources(knowledgeBaseId)
      if (knowledgeBaseId === undefined) resources.value = items
      else {
        resources.value = [
          ...resources.value.filter((item) => item.knowledgeBaseId !== knowledgeBaseId),
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
    origin: Extract<ResourceOrigin, 'resource-library' | 'chat' | 'learning'>,
    projectId: number | null = null,
    knowledgeBaseId: number | null = null,
  ) {
    if (isMutating.value) return []
    isMutating.value = true
    errorMessage.value = null
    const uploaded: LibraryResource[] = []
    const associations: ResourceAssociations = { projectId, knowledgeBaseId }
    try {
      for (const file of files) {
        let item: LibraryResource
        if (isImageFile(file)) {
          const asset = await mediaRepository.uploadImage(file, {
            source: getMediaSource(file),
            purpose: origin === 'learning' ? 'learning-input' : 'library-resource',
            knowledgeBaseId,
            projectId,
            clientRequestId: clientRequestId(),
          })
          item = {
            resourceId: `media:${asset.id}`,
            name: asset.fileName,
            format: resourceFormat(asset.fileName, '图片'),
            fileType: 'image',
            mimeType: asset.mimeType,
            sizeBytes: asset.size,
            status: asset.status === 'failed' ? 'failed' : asset.status === 'ready' ? 'ready' : 'processing',
            errorMessage: asset.errorMessage,
            updatedAt: '刚刚',
            sourceType: 'uploaded',
            origin,
            ...associations,
            externalKey: asset.id,
          }
        } else if (isAudioFile(file)) {
          const transcription = await mediaRepository.transcribeAudio(file, {
            source: 'upload',
            purpose: origin === 'learning' ? 'learning-input' : 'library-resource',
            knowledgeBaseId,
            projectId,
            clientRequestId: clientRequestId(),
            language: 'zh-CN',
          })
          const asset = transcription.asset
          item = {
            resourceId: `media:${asset.id}`,
            name: asset.fileName,
            format: resourceFormat(asset.fileName, '音频'),
            fileType: 'audio',
            mimeType: asset.mimeType,
            sizeBytes: asset.size,
            status: asset.status === 'failed' ? 'failed' : asset.status === 'ready' ? 'ready' : 'processing',
            errorMessage: asset.errorMessage,
            updatedAt: '刚刚',
            sourceType: 'uploaded',
            origin,
            ...associations,
            externalKey: asset.id,
          }
        } else {
          item = await uploadLibraryResource(file, origin, associations)
        }
        rememberMockLibraryResourceFile(item.resourceId, file)
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

  async function remove(resourceId: string) {
    isMutating.value = true
    errorMessage.value = null
    try {
      await deleteLibraryResource(resourceId)
      resources.value = resources.value.filter((item) => item.resourceId !== resourceId)
      persist()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '删除资料失败'
      throw error
    } finally {
      isMutating.value = false
    }
  }

  async function retry(resourceId: string) {
    isMutating.value = true
    errorMessage.value = null
    try {
      upsert(await retryLibraryResource(resourceId))
      persist()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '重试解析失败'
      throw error
    } finally {
      isMutating.value = false
    }
  }

  async function rename(resourceId: string, name: string) {
    if (!name.trim()) return
    isMutating.value = true
    errorMessage.value = null
    try {
      upsert(await renameLibraryResource(resourceId, name.trim()))
      persist()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '重命名失败'
      throw error
    } finally {
      isMutating.value = false
    }
  }

  async function updateAssociations(resourceId: string, associations: ResourceAssociations) {
    isMutating.value = true
    errorMessage.value = null
    try {
      upsert(await updateLibraryResourceAssociations(resourceId, associations))
      persist()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '更新资料关联失败'
      throw error
    } finally {
      isMutating.value = false
    }
  }

  async function download(resourceId: string, name: string) {
    isMutating.value = true
    errorMessage.value = null
    try {
      const blob = await downloadLibraryResource(resourceId)
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
    origin: Extract<ResourceOrigin, 'resource-library' | 'chat' | 'learning'>,
    projectId: number | null = null,
    knowledgeBaseId: number | null = null,
  ) {
    const signature = `${origin}:${file.name}:${file.size}:${file.lastModified}`
    const existing = resources.value.find((item) => item.externalKey === signature)
    if (existing) {
      existing.projectId = projectId
      existing.knowledgeBaseId = knowledgeBaseId
      existing.updatedAt = '刚刚'
      persist()
      return existing
    }

    const item: LibraryResource = {
      resourceId: `upload-${Date.now()}-${sequence++}`,
      name: file.name,
      format: resourceFormat(file.name),
      fileType: resourceFileType(file.name, file.type),
      mimeType: file.type || undefined,
      sizeBytes: file.size,
      status: 'ready',
      updatedAt: '刚刚',
      sourceType: 'uploaded',
      origin,
      projectId,
      knowledgeBaseId,
      externalKey: signature,
    }
    resources.value.unshift(item)
    rememberMockLibraryResourceFile(item.resourceId, file)
    persist()
    return item
  }

  function addFiles(
    files: File[],
    origin: Extract<ResourceOrigin, 'resource-library' | 'chat' | 'learning'>,
    projectId: number | null = null,
    knowledgeBaseId: number | null = null,
  ) {
    return files.map((file) => addFile(file, origin, projectId, knowledgeBaseId))
  }

  function addGeneratedFile(input: {
    resourceId?: string
    externalKey: string
    name: string
    format: string
    fileType: ResourceFileType
    mimeType?: string
    origin: Extract<ResourceOrigin, 'chat' | 'learning' | 'presentation' | 'spreadsheet' | 'mindmap'>
    projectId?: number | null
    knowledgeBaseId?: number | null
    status?: LibraryResource['status']
    sizeBytes?: number
  }) {
    const existing = resources.value.find((item) => item.externalKey === input.externalKey)
    if (existing) {
      Object.assign(existing, {
        name: input.name,
        format: input.format,
        fileType: input.fileType,
        mimeType: input.mimeType ?? existing.mimeType,
        origin: input.origin,
        projectId: input.projectId ?? null,
        knowledgeBaseId: input.knowledgeBaseId ?? null,
        status: input.status ?? 'ready',
        sizeBytes: input.sizeBytes ?? existing.sizeBytes,
        updatedAt: '刚刚',
      })
      persist()
      return existing
    }

    const item: LibraryResource = {
      resourceId: input.resourceId ?? `generated-${Date.now()}-${sequence++}`,
      name: input.name,
      format: input.format,
      fileType: input.fileType,
      mimeType: input.mimeType,
      sizeBytes: input.sizeBytes ?? 0,
      status: input.status ?? 'ready',
      updatedAt: '刚刚',
      sourceType: 'generated',
      origin: input.origin,
      projectId: input.projectId ?? null,
      knowledgeBaseId: input.knowledgeBaseId ?? null,
      externalKey: input.externalKey,
    }
    resources.value.unshift(item)
    persist()
    return item
  }

  function addGeneratedResource(resource: LearningResource, planId: number, projectId: number | null, knowledgeBaseId: number | null) {
    const archived = addGeneratedFile({
      externalKey: `learning:${planId}:${resource.id}`,
      name: resource.fileName ?? resource.title,
      format: generatedFormat(resource.group),
      fileType: generatedFileType(resource.group),
      origin: 'learning',
      projectId,
      knowledgeBaseId,
      status: resource.status === '生成中'
        ? 'processing'
        : resource.status === '生成失败'
          ? 'failed'
          : resource.status === '未选择'
            ? 'waiting'
            : 'ready',
    })
    resource.resourceId = archived.resourceId
    return archived
  }

  function addChatGenerated(name: string, knowledgeBaseId: number | null = null) {
    return addGeneratedFile({
      externalKey: `mindmap:${Date.now()}-${sequence++}`,
      name,
      format: '思维导图',
      fileType: 'mindmap',
      origin: 'mindmap',
      knowledgeBaseId,
    })
  }

  function addPresentation(
    presentationId: string,
    name: string,
    projectId: number | null = null,
    knowledgeBaseId: number | null = null,
    sizeBytes = 0,
  ) {
    return addGeneratedFile({
      resourceId: `presentation:${presentationId}`,
      externalKey: `presentation:${presentationId}`,
      name,
      format: 'PPT',
      fileType: 'presentation',
      origin: 'presentation',
      projectId,
      knowledgeBaseId,
      sizeBytes,
    })
  }

  function addPlanExportMarkdown(name: string, planId: number, projectId: number | null, knowledgeBaseId: number | null) {
    return addGeneratedFile({
      externalKey: `learning:${planId}:export-markdown`,
      name,
      format: 'Markdown',
      fileType: 'document',
      origin: 'learning',
      projectId,
      knowledgeBaseId,
    })
  }

  function detachProject(projectId: number) {
    let changed = false
    resources.value.forEach((resource) => {
      if (resource.projectId !== projectId) return
      resource.projectId = null
      resource.updatedAt = '刚刚'
      changed = true
    })
    if (changed) persist()
  }

  function clearError() {
    errorMessage.value = null
  }

  function clearAll() {
    resources.value = []
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
    updateAssociations,
    download,
    clearError,
    clearAll,
    addFile,
    addFiles,
    addGeneratedFile,
    addGeneratedResource,
    addChatGenerated,
    addPresentation,
    addPlanExportMarkdown,
    detachProject,
  }
})
