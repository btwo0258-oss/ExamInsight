import { request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { courseKnowledgeBases } from '@/mock'
import { mockSession } from '@/mock/storage'
import { detachMockResourcesFromKnowledgeBase } from '@/repositories/libraryResource'
import type { KnowledgeBaseDto } from '@/types/contracts/library'

export interface KnowledgeBaseRepository {
  list(): Promise<KnowledgeBaseDto[]>
  get(id: number): Promise<KnowledgeBaseDto>
  create(payload: Partial<KnowledgeBaseDto>): Promise<KnowledgeBaseDto>
  update(payload: KnowledgeBaseDto): Promise<KnowledgeBaseDto>
  remove(id: number): Promise<void>
  findByExamAnalysisId(examAnalysisId: number): Promise<KnowledgeBaseDto | null>
}

const DOMAIN = 'knowledge-bases'

function initialKnowledgeBases(): KnowledgeBaseDto[] {
  return courseKnowledgeBases.map((library) => ({
    id: library.id,
    name: library.name,
    description: library.description,
    icon: 'folder',
    color: '#71717a',
    documentCount: library.fileCount,
    mindMapCount: 0,
    availableForAi: true,
    createTime: new Date().toISOString(),
    updateTime: library.updatedAt,
  }))
}

function readMockList() {
  return mockSession.get<KnowledgeBaseDto[]>(DOMAIN, initialKnowledgeBases())
}

function normalizeKnowledgeBase(item: Record<string, unknown>): KnowledgeBaseDto {
  return {
    id: Number(item.id),
    name: String(item.name ?? ''),
    description: typeof item.description === 'string' ? item.description : undefined,
    icon: typeof item.icon === 'string' ? item.icon : typeof item.avatar === 'string' ? item.avatar : undefined,
    color: typeof item.color === 'string' ? item.color : undefined,
    documentCount: Number(item.docCount ?? item.documentCount ?? 0),
    mindMapCount: Number(item.mindMapCount ?? 0),
    examAnalysisId: item.examAnalysisId === undefined ? undefined : Number(item.examAnalysisId),
    availableForAi: Boolean(item.availableForAi ?? true),
    createTime: String(item.createTime ?? ''),
    updateTime: String(item.updateTime ?? ''),
  }
}

const mockKnowledgeBaseRepository: KnowledgeBaseRepository = {
  async list() {
    return readMockList()
  },

  async get(id) {
    const item = readMockList().find((knowledgeBase) => knowledgeBase.id === id)
    if (!item) throw new Error('Knowledge base not found')
    return item
  },

  async create(payload) {
    const list = readMockList()
    const now = new Date().toISOString()
    const item: KnowledgeBaseDto = {
      id: Date.now(),
      name: payload.name || '未命名资料库',
      description: payload.description,
      icon: payload.icon || 'folder',
      color: payload.color || '#71717a',
      documentCount: 0,
      mindMapCount: 0,
      availableForAi: true,
      createTime: now,
      updateTime: now,
    }
    list.unshift(item)
    mockSession.set(DOMAIN, list)
    return item
  },

  async update(payload) {
    const list = readMockList()
    const index = list.findIndex((item) => item.id === payload.id)
    if (index === -1) throw new Error('Knowledge base not found')
    const item = { ...payload, updateTime: new Date().toISOString() }
    list[index] = item
    mockSession.set(DOMAIN, list)
    return item
  },

  async remove(id) {
    mockSession.set(DOMAIN, readMockList().filter((item) => item.id !== id))
    detachMockResourcesFromKnowledgeBase(id)
  },

  async findByExamAnalysisId(examAnalysisId) {
    return readMockList().find((item) => item.examAnalysisId === examAnalysisId) ?? null
  },
}

const apiKnowledgeBaseRepository: KnowledgeBaseRepository = {
  async list() {
    const response = await request.get('/api/kb/list')
    const data = (response.data?.data ?? response.data) as Record<string, unknown>[]
    return data.map(normalizeKnowledgeBase)
  },

  async get(id) {
    const response = await request.get(`/api/kb/${id}`)
    return normalizeKnowledgeBase((response.data?.data ?? response.data) as Record<string, unknown>)
  },

  async create(payload) {
    const response = await request.post('/api/kb/create', {
      name: payload.name,
      description: payload.description,
      avatar: payload.icon,
      color: payload.color,
      examAnalysisId: payload.examAnalysisId,
    })
    return normalizeKnowledgeBase((response.data?.data ?? response.data) as Record<string, unknown>)
  },

  async update(payload) {
    const response = await request.put(`/api/kb/${payload.id}`, {
      name: payload.name,
      description: payload.description,
      avatar: payload.icon,
      color: payload.color,
    })
    return normalizeKnowledgeBase((response.data?.data ?? response.data) as Record<string, unknown>)
  },

  async remove(id) {
    await request.delete(`/api/kb/${id}`)
  },

  async findByExamAnalysisId(examAnalysisId) {
    const response = await request.get(`/api/kb/by-exam-analysis/${examAnalysisId}`)
    const data = response.data?.data ?? response.data
    return data ? normalizeKnowledgeBase(data as Record<string, unknown>) : null
  },
}

export const knowledgeBaseRepository = isMockDataSource
  ? mockKnowledgeBaseRepository
  : apiKnowledgeBaseRepository
