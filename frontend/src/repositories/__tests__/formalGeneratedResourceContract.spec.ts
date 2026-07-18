import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/config/dataSource', () => ({ isMockDataSource: false }))
vi.mock('@/api/request', () => ({
  getStoredToken: vi.fn(() => 'token'),
  request: api,
}))

import { chatRepository } from '@/repositories/chat'
import { learningRepository } from '@/repositories/learning'
import { presentationRepository } from '@/repositories/presentation'

describe('formal generated-resource API contract', () => {
  beforeEach(() => vi.clearAllMocks())

  it('upserts a generated file into the project resource package', async () => {
    api.put.mockResolvedValueOnce({ data: { data: { id: 7, resources: [], stages: [], exercises: [], wrongQuestions: [], dashboard: [], agents: [] } } })
    await learningRepository.attachGeneratedResource(7, {
      resourceId: 'resource-1',
      artifactId: 'document:1',
      title: '复习文档',
      fileName: '复习文档.docx',
      fileType: 'document',
      source: 'ai-conversation',
      clientRequestId: 'resource-request-1',
    })
    expect(api.put).toHaveBeenCalledWith('/api/learning/projects/7/resources/generated', expect.objectContaining({
      resourceId: 'resource-1',
      artifactId: 'document:1',
    }))
  })

  it('persists PPT associations through the presentation aggregate', async () => {
    api.put.mockResolvedValueOnce({ data: { data: { id: 'ppt-1' } } })
    await presentationRepository.updateAssociations('ppt-1', {
      projectId: 7,
      knowledgeBaseId: 12,
      learningResourceId: null,
      clientRequestId: 'ppt-association-1',
    })
    expect(api.put).toHaveBeenCalledWith('/api/presentations/ppt-1/associations', expect.objectContaining({ knowledgeBaseId: 12 }))
  })

  it('retries the same chat artifact and carries its existing resource identity', async () => {
    api.post.mockResolvedValueOnce({ data: { data: { artifactId: 'document:1', status: 'ready', preview: { kind: 'document' } } } })
    await chatRepository.retryArtifact({
      artifact: {
        artifactId: 'document:1',
        resourceId: 'resource-1',
        title: '复习文档',
        fileName: '复习文档.docx',
        fileType: 'document',
        format: 'DOCX',
        status: 'failed',
        preview: { kind: 'document' },
      },
      conversationId: 9,
      sourceMessageId: 'message-1',
      clientRequestId: 'retry-1',
    })
    expect(api.post).toHaveBeenCalledWith('/api/chat/artifacts/document%3A1/retry', expect.objectContaining({
      resourceId: 'resource-1',
      conversationId: 9,
    }))
  })
})
