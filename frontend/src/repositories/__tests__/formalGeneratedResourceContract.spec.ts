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

  it('uses formal APIs for recoverable learning setup state', async () => {
    const state = {
      setupId: 'setup-1',
      knowledgeBaseId: null,
      prompt: '学习 Java',
      profile: { goal: '', subject: 'Java', foundation: '', weakPoints: [], period: '', dailyTime: '', preferences: [], source: '', extra: '' },
      mediaAssetIds: [],
      sourceResourceIds: [],
      uploadedFileNames: [],
      confirmationResourceId: null,
      confirmationDocument: '',
      phase: 'profile' as const,
      profileMessageId: 'profile-1',
      documentMessageId: '',
    }
    api.put.mockResolvedValueOnce({ data: { data: state } })
    await learningRepository.saveSetupState(7, state)
    expect(api.put).toHaveBeenCalledWith('/api/learning/projects/7/setup-state', state)

    api.get.mockResolvedValueOnce({ data: { data: state } })
    await learningRepository.getSetupState(7)
    expect(api.get).toHaveBeenCalledWith('/api/learning/projects/7/setup-state')
  })

  it('uses formal APIs for active generation, drafts, and wrong-review state', async () => {
    const active = { jobId: 'job-7', draftPlanId: 7, sourceResourceIds: [], knowledgeBaseId: null, startedAt: 1 }
    api.put.mockResolvedValueOnce({ data: { data: active } })
    await learningRepository.saveActivePlanGeneration(7, active)
    expect(api.put).toHaveBeenCalledWith('/api/learning/projects/7/active-plan-generation', active)

    const draft = { exerciseId: 3, answer: 'A' }
    api.put.mockResolvedValueOnce({ data: { data: draft } })
    await learningRepository.saveExerciseDraft(7, draft)
    expect(api.put).toHaveBeenCalledWith('/api/learning/projects/7/exercise-drafts/3', draft)

    api.put.mockResolvedValueOnce({ data: { data: { id: 7, resources: [], stages: [], exercises: [], wrongQuestions: [], dashboard: [], agents: [] } } })
    await learningRepository.startWrongReviewSet(7, 9, 'review-1')
    expect(api.put).toHaveBeenCalledWith('/api/learning/projects/7/wrong-review-sets/9/status', {
      status: 'answering',
      clientRequestId: 'review-1',
    })
  })
})
