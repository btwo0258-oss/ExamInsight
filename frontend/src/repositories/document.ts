import { request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { mockSession } from '@/mock/storage'
import type { DocumentProcessingStatus, LibraryDocumentDto } from '@/types/contracts/library'

export type DocumentStatus = DocumentProcessingStatus
export type DocumentDto = LibraryDocumentDto & { errorMsg?: string }

export interface DocumentRepository {
  list(kbId: number): Promise<DocumentDto[]>
  upload(kbId: number, file: File): Promise<DocumentDto>
  extract(file: File, signal?: AbortSignal): Promise<string>
  remove(id: number): Promise<void>
  status(id: number): Promise<{ status: DocumentStatus; errorMsg?: string; chunkCount?: number }>
}

const DOMAIN = 'documents'

function mockDocuments() {
  return mockSession.get<DocumentDto[]>(DOMAIN, [])
}

function normalizeDocument(item: Record<string, unknown>): DocumentDto {
  const rawStatus = item.status
  const knownStatuses: DocumentStatus[] = ['uploading', 'uploaded', 'parsing', 'ready', 'failed']
  const status: DocumentStatus = rawStatus === 1 || rawStatus === 'completed'
    ? 'ready'
    : rawStatus === 2
      ? 'failed'
      : knownStatuses.includes(rawStatus as DocumentStatus)
        ? rawStatus as DocumentStatus
        : 'uploading'
  const errorMessage = typeof item.errorMessage === 'string'
    ? item.errorMessage
    : typeof item.errorMsg === 'string' ? item.errorMsg : undefined
  return {
    id: Number(item.id),
    kbId: Number(item.kbId),
    fileName: String(item.fileName ?? ''),
    fileType: String(item.fileType ?? ''),
    fileSize: Number(item.fileSize ?? 0),
    chunkCount: Number(item.chunkCount ?? 0),
    status,
    errorCode: typeof item.errorCode === 'string' ? item.errorCode : undefined,
    errorMessage,
    errorMsg: errorMessage,
    createTime: String(item.createTime ?? ''),
  }
}

const mockDocumentRepository: DocumentRepository = {
  async list(kbId) {
    return mockDocuments().filter((item) => item.kbId === kbId)
  },
  async upload(kbId, file) {
    const document: DocumentDto = {
      id: Date.now(),
      kbId,
      fileName: file.name,
      fileType: file.name.split('.').pop() || 'unknown',
      fileSize: file.size,
      chunkCount: 0,
      status: 'uploading',
      createTime: new Date().toISOString(),
    }
    const documents = mockDocuments()
    documents.unshift(document)
    mockSession.set(DOMAIN, documents)
    return document
  },
  async extract(file) {
    return `[Mock 附件：${file.name}，${file.size} 字节]\nMock 环境仅模拟附件元数据，不读取或保存真实文件内容。`
  },
  async remove(id) {
    mockSession.set(DOMAIN, mockDocuments().filter((item) => item.id !== id))
  },
  async status(id) {
    const documents = mockDocuments()
    const document = documents.find((item) => item.id === id)
    if (!document) throw new Error('Document not found')
    if (document.status !== 'ready' && document.status !== 'failed') {
      document.status = 'ready'
      document.chunkCount = Math.max(1, document.chunkCount)
      mockSession.set(DOMAIN, documents)
    }
    return { status: document.status, errorMsg: document.errorMsg, chunkCount: document.chunkCount }
  },
}

const apiDocumentRepository: DocumentRepository = {
  async list(kbId) {
    const response = await request.get('/api/doc/list', { params: { kbId } })
    const data = (response.data?.data ?? response.data) as Record<string, unknown>[]
    return data.map(normalizeDocument)
  },
  async upload(kbId, file) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('kbId', String(kbId))
    const response = await request.post('/api/doc/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return normalizeDocument((response.data?.data ?? response.data) as Record<string, unknown>)
  },
  async extract(file, signal) {
    const formData = new FormData()
    formData.append('file', file)
    const response = await request.post('/api/doc/extract', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      signal,
    })
    const data = response.data?.data ?? response.data
    if (typeof data !== 'string') throw new Error('附件解析结果格式错误')
    return data
  },
  async remove(id) {
    await request.delete(`/api/doc/${id}`)
  },
  async status(id) {
    const response = await request.get(`/api/doc/status/${id}`)
    const data = (response.data?.data ?? response.data) as Record<string, unknown>
    const normalized = normalizeDocument({ id, kbId: 0, fileName: '', fileType: '', fileSize: 0, createTime: '', ...data })
    return { status: normalized.status, errorMsg: normalized.errorMsg, chunkCount: normalized.chunkCount }
  },
}

export const documentRepository = isMockDataSource ? mockDocumentRepository : apiDocumentRepository
