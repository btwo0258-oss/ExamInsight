import { request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { recentUploads } from '@/mock'
import { mockSession } from '@/mock/storage'
import { RESOURCE_PREVIEW_LIMITS } from '@/types/contracts/library'
import type {
  LibraryResourceDto,
  LibraryResourceProcessingStatus,
  ResourceAssociations,
  ResourceFileType,
  ResourceOrigin,
  ResourcePreviewDto,
  ResourcePreviewKind,
} from '@/types/contracts/library'

export interface LibraryResourceRepository {
  initial(): LibraryResourceDto[]
  list(knowledgeBaseId?: number): Promise<LibraryResourceDto[]>
  saveMock(resources: LibraryResourceDto[]): void
  upload(
    file: File,
    origin: Extract<ResourceOrigin, 'resource-library' | 'chat' | 'learning'>,
    associations: ResourceAssociations,
  ): Promise<LibraryResourceDto>
  remove(resourceId: string): Promise<void>
  retry(resourceId: string): Promise<LibraryResourceDto>
  rename(resourceId: string, name: string): Promise<LibraryResourceDto>
  updateAssociations(resourceId: string, associations: ResourceAssociations): Promise<LibraryResourceDto>
  preview(resourceId: string): Promise<ResourcePreviewDto>
  download(resourceId: string): Promise<Blob>
}

const DOMAIN = 'resources'
const mockPreviewFiles = new Map<string, File>()

function externalId(resource: LibraryResourceDto, prefix: string) {
  return resource.externalKey?.startsWith(prefix) ? resource.externalKey.slice(prefix.length) : undefined
}

function extensionOf(name: string) {
  return name.split('.').pop()?.toLowerCase() ?? ''
}

function previewKind(resource: LibraryResourceDto): ResourcePreviewKind {
  const extension = extensionOf(resource.name)
  if (resource.fileType === 'image') return 'image'
  if (resource.fileType === 'pdf') return 'pdf'
  if (resource.fileType === 'presentation') return 'presentation'
  if (resource.fileType === 'spreadsheet') return 'spreadsheet'
  if (resource.fileType === 'mindmap') return 'mindmap'
  if (resource.fileType === 'audio') return 'audio'
  if (resource.fileType === 'document' && ['doc', 'docx'].includes(extension)) return 'word'
  if (resource.fileType === 'document' || ['txt', 'md', 'json', 'js', 'ts', 'java', 'py', 'css', 'html'].includes(extension)) return 'text'
  return 'unsupported'
}

function previewLimit(resource: LibraryResourceDto, kind: ResourcePreviewKind) {
  if (kind === 'text') return RESOURCE_PREVIEW_LIMITS.text
  if (kind === 'mindmap') return RESOURCE_PREVIEW_LIMITS.mindmap
  if (kind === 'image') return RESOURCE_PREVIEW_LIMITS.image
  if (kind === 'pdf') return RESOURCE_PREVIEW_LIMITS.pdf
  if (kind === 'presentation') return RESOURCE_PREVIEW_LIMITS.presentation
  if (kind === 'spreadsheet') return RESOURCE_PREVIEW_LIMITS.spreadsheet
  if (kind === 'audio') return RESOURCE_PREVIEW_LIMITS.audio
  return RESOURCE_PREVIEW_LIMITS.document
}

export function rememberMockLibraryResourceFile(resourceId: string, file: File) {
  if (!isMockDataSource) return
  mockPreviewFiles.set(resourceId, file)
}

function normalizeStatus(status: unknown): LibraryResourceProcessingStatus {
  if (status === 'ready' || status === '解析完成') return 'ready'
  if (status === 'processing' || status === '向量化中') return 'processing'
  if (status === 'failed' || status === '解析失败') return 'failed'
  return 'waiting'
}

export function resourceFileType(name: string, mimeType = ''): ResourceFileType {
  const extension = name.split('.').pop()?.toLowerCase() ?? ''
  if (mimeType.startsWith('image/') || ['jpg', 'jpeg', 'png', 'webp', 'heic', 'heif'].includes(extension)) return 'image'
  if (extension === 'pdf' || mimeType === 'application/pdf') return 'pdf'
  if (['xls', 'xlsx', 'csv'].includes(extension)) return 'spreadsheet'
  if (['ppt', 'pptx'].includes(extension)) return 'presentation'
  if (['mp3', 'wav', 'm4a', 'aac', 'ogg', 'flac'].includes(extension) || mimeType.startsWith('audio/')) return 'audio'
  if (['zip', 'rar', '7z'].includes(extension)) return 'archive'
  if (extension === 'mindmap') return 'mindmap'
  if (['doc', 'docx', 'txt', 'md'].includes(extension)) return 'document'
  return 'other'
}

export function resourceFormat(name: string, fallback = 'FILE') {
  const extension = name.split('.').pop()?.toUpperCase()
  if (!extension || extension === name.toUpperCase()) return fallback
  if (extension === 'DOC' || extension === 'DOCX') return 'Word'
  if (extension === 'PPT' || extension === 'PPTX') return 'PPT'
  if (extension === 'XLS' || extension === 'XLSX') return 'Excel'
  if (extension === 'JPG' || extension === 'JPEG' || extension === 'PNG' || extension === 'WEBP') return '图片'
  return extension
}

function normalizeResource(item: LibraryResourceDto | Record<string, unknown>): LibraryResourceDto {
  return { ...item, status: normalizeStatus(item.status) } as LibraryResourceDto
}

function initialMockResources(): LibraryResourceDto[] {
  return recentUploads.map((file) => ({
    resourceId: `mock-${file.id}`,
    name: file.name,
    format: resourceFormat(file.name, file.type),
    fileType: resourceFileType(file.name),
    sizeBytes: 128 * 1024,
    status: normalizeStatus(file.status),
    updatedAt: file.updatedAt,
    sourceType: 'uploaded',
    origin: 'resource-library',
    projectId: null,
    knowledgeBaseId: null,
  }))
}

const mockRepository: LibraryResourceRepository = {
  initial() {
    return mockSession.get(DOMAIN, initialMockResources())
  },
  async list(knowledgeBaseId) {
    const resources = mockSession.get(DOMAIN, initialMockResources())
    return knowledgeBaseId === undefined
      ? resources
      : resources.filter((item) => item.knowledgeBaseId === knowledgeBaseId)
  },
  saveMock(resources) {
    mockSession.set(DOMAIN, resources)
  },
  async upload(file, origin, associations) {
    const resource: LibraryResourceDto = {
      resourceId: `upload-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      name: file.name,
      format: resourceFormat(file.name),
      fileType: resourceFileType(file.name, file.type),
      mimeType: file.type || undefined,
      sizeBytes: file.size,
      status: 'waiting',
      updatedAt: '刚刚',
      sourceType: 'uploaded',
      origin,
      ...associations,
    }
    const resources = mockSession.get(DOMAIN, initialMockResources())
    resources.unshift(resource)
    mockSession.set(DOMAIN, resources)
    mockPreviewFiles.set(resource.resourceId, file)
    return resource
  },
  async remove(resourceId) {
    mockSession.set(DOMAIN, mockSession.get(DOMAIN, initialMockResources()).filter((item) => item.resourceId !== resourceId))
  },
  async retry(resourceId) {
    const resources = mockSession.get(DOMAIN, initialMockResources())
    const resource = resources.find((item) => item.resourceId === resourceId)
    if (!resource) throw new Error('资料不存在')
    resource.status = 'waiting'
    resource.errorMessage = undefined
    resource.updatedAt = '刚刚'
    mockSession.set(DOMAIN, resources)
    return resource
  },
  async rename(resourceId, name) {
    const resources = mockSession.get(DOMAIN, initialMockResources())
    const resource = resources.find((item) => item.resourceId === resourceId)
    if (!resource) throw new Error('资料不存在')
    resource.name = name
    resource.format = resourceFormat(name, resource.format)
    resource.fileType = resourceFileType(name, resource.mimeType)
    resource.updatedAt = '刚刚'
    mockSession.set(DOMAIN, resources)
    return resource
  },
  async updateAssociations(resourceId, associations) {
    const resources = mockSession.get(DOMAIN, initialMockResources())
    const resource = resources.find((item) => item.resourceId === resourceId)
    if (!resource) throw new Error('资料不存在')
    resource.projectId = associations.projectId
    resource.knowledgeBaseId = associations.knowledgeBaseId
    resource.updatedAt = '刚刚'
    mockSession.set(DOMAIN, resources)
    return resource
  },
  async preview(resourceId) {
    const resource = mockSession.get(DOMAIN, initialMockResources()).find((item) => item.resourceId === resourceId)
    if (!resource) throw new Error('资料不存在或已被删除')

    const kind = previewKind(resource)
    const base = { resource: structuredClone(resource), previewKind: kind } as const
    if (resource.status === 'waiting' || resource.status === 'processing') {
      return { ...base, status: 'processing', errorMessage: '文件仍在解析，请稍后重试' }
    }
    if (resource.status === 'failed') {
      return { ...base, status: 'failed', errorMessage: resource.errorMessage || '文件解析失败' }
    }
    if (kind === 'unsupported') {
      return { ...base, status: 'unsupported', errorMessage: '当前格式不支持在线预览，可下载后查看' }
    }
    if (resource.sizeBytes > previewLimit(resource, kind)) {
      return { ...base, status: 'too_large', errorMessage: '文件超过该格式的在线预览大小限制' }
    }

    const presentationId = externalId(resource, 'presentation:')
    const spreadsheetId = externalId(resource, 'spreadsheet:')
    const mindMapId = Number(externalId(resource, 'mindmap:')) || undefined
    if (presentationId || spreadsheetId || mindMapId || resource.externalKey?.startsWith('learning:')) {
      return { ...base, status: 'ready', presentationId, spreadsheetId, mindMapId }
    }

    const file = mockPreviewFiles.get(resourceId)
    if (!file) {
      return {
        ...base,
        status: 'failed',
        errorMessage: 'Mock 环境只保留文件元数据，刷新后无法恢复原文件预览，可重新上传或下载查看',
      }
    }

    if (kind === 'text') {
      return { ...base, status: 'ready', textContent: await file.text() }
    }
    return { ...base, status: 'ready', previewUrl: URL.createObjectURL(file) }
  },
  async download(resourceId) {
    const resource = mockSession.get(DOMAIN, initialMockResources()).find((item) => item.resourceId === resourceId)
    if (!resource) throw new Error('资料不存在')
    return new Blob([`Mock 文件：${resource.name}\nMock 环境不保存真实上传文件内容。`], { type: 'text/plain;charset=utf-8' })
  },
}

function unwrap<T>(response: { data: unknown }): T {
  const payload = response.data as { data?: T }
  return (payload?.data ?? response.data) as T
}

const apiRepository: LibraryResourceRepository = {
  initial() {
    return []
  },
  async list(knowledgeBaseId) {
    const data = unwrap<Record<string, unknown>[]>(await request.get('/api/resources', { params: { knowledgeBaseId } }))
    return data.map(normalizeResource)
  },
  saveMock() {},
  async upload(file, origin, associations) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('origin', origin)
    if (associations.knowledgeBaseId !== null) formData.append('knowledgeBaseId', String(associations.knowledgeBaseId))
    if (associations.projectId !== null) formData.append('projectId', String(associations.projectId))
    return normalizeResource(unwrap<Record<string, unknown>>(await request.post('/api/resources/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })))
  },
  async remove(resourceId) {
    await request.delete(`/api/resources/${resourceId}`)
  },
  async retry(resourceId) {
    return normalizeResource(unwrap<Record<string, unknown>>(await request.post(`/api/resources/${resourceId}/retry`)))
  },
  async rename(resourceId, name) {
    return normalizeResource(unwrap<Record<string, unknown>>(await request.patch(`/api/resources/${resourceId}`, { name })))
  },
  async updateAssociations(resourceId, associations) {
    return normalizeResource(unwrap<Record<string, unknown>>(await request.put(`/api/resources/${resourceId}/associations`, associations)))
  },
  async preview(resourceId) {
    return unwrap<ResourcePreviewDto>(await request.get(`/api/resources/${resourceId}/preview`))
  },
  async download(resourceId) {
    const response = await request.get(`/api/resources/${resourceId}/download`, { responseType: 'blob' })
    return response.data as Blob
  },
}

export const libraryResourceRepository = isMockDataSource ? mockRepository : apiRepository
