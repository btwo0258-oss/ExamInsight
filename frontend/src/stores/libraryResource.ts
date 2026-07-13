import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLibraryResources, saveLibraryResources } from '@/api/libraryResource'
import type { LearningResource } from '@/mock'

export type LibraryResourceSource = '资料库上传' | '智能学习上传' | '聊天上传' | '智能学习生成' | '聊天生成'
export type LibraryResourceCategory = 'file' | 'image' | 'mindmap'

export type LibraryResource = {
  id: string
  name: string
  type: string
  size: string
  status: '解析完成' | '向量化中' | '等待解析'
  updatedAt: string
  category: LibraryResourceCategory
  source: LibraryResourceSource
  projectId: number | null
  libraryId: number | null
  externalKey?: string
}

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
  if (group === 'PPT') return 'PPT'
  if (group === '思维导图') return '思维导图'
  if (group === '代码案例') return 'ZIP'
  if (group === '个性化学习手册') return 'Markdown'
  return 'PDF'
}

export const useLibraryResourceStore = defineStore('libraryResource', () => {
  const resources = ref<LibraryResource[]>(getLibraryResources())
  let sequence = 0

  function persist() {
    saveLibraryResources(resources.value)
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
      status: '解析完成',
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
      existing.status = resource.status === '生成中' ? '向量化中' : '解析完成'
      persist()
      return existing
    }

    const item: LibraryResource = {
      id: `generated-${planId}-${resource.id}`,
      name: resource.fileName ?? resource.title,
      type: generatedType(resource.group),
      size: 'AI 生成',
      status: resource.status === '生成中' ? '向量化中' : '解析完成',
      updatedAt: '刚刚',
      category: resource.group === '思维导图' ? 'mindmap' : 'file',
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
      status: '解析完成',
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
      existing.status = '解析完成'
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
      status: '解析完成',
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

  return { resources, addFile, addFiles, addGeneratedResource, addChatGenerated, addPlanExportMarkdown }
})
