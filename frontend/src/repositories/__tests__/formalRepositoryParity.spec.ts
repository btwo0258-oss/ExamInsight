import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/config/dataSource', () => ({ isMockDataSource: false }))
vi.mock('@/api/request', () => ({ request: api }))

import { knowledgeBaseRepository } from '@/repositories/knowledgeBase'
import { learningRepository } from '@/repositories/learning'
import { libraryResourceRepository } from '@/repositories/libraryResource'

const apiProject = {
  id: 7,
  title: 'Java 学习项目',
  goal: '掌握 Java',
  updatedAt: '',
  knowledgeBaseId: null,
  status: 'draft',
  period: '3 天',
  targetType: '考试复习',
  progress: 0,
  taskDone: 0,
  totalTasks: 0,
  exerciseDone: 0,
  totalExercises: 0,
  correctRate: 0,
  weeklyHours: '0h',
  profile: [],
  stages: [],
  resources: [],
  exercises: [],
  wrongQuestions: [],
  dashboard: [],
  agents: [],
}

describe('formal repository main-chain parity', () => {
  beforeEach(() => vi.clearAllMocks())

  it('covers the complete library-resource HTTP chain', async () => {
    const resource = {
      resourceId: 'resource-1',
      name: 'notes.md',
      format: 'MD',
      fileType: 'document',
      sizeBytes: 5,
      status: 'waiting',
      updatedAt: '',
      sourceType: 'uploaded',
      origin: 'resource-library',
      projectId: null,
      knowledgeBaseId: null,
    }
    api.get.mockResolvedValueOnce({ data: { data: [resource] } })
    await libraryResourceRepository.list()
    expect(api.get).toHaveBeenCalledWith('/api/resources', { params: { knowledgeBaseId: undefined } })

    api.post.mockResolvedValueOnce({ data: { data: resource } })
    await libraryResourceRepository.upload(new File(['hello'], 'notes.md'), 'resource-library', {
      projectId: null,
      knowledgeBaseId: null,
    })
    expect(api.post).toHaveBeenCalledWith('/api/resources/upload', expect.any(FormData), {
      headers: { 'Content-Type': 'multipart/form-data' },
    })

    api.patch.mockResolvedValueOnce({ data: { data: { ...resource, name: 'renamed.md' } } })
    await libraryResourceRepository.rename('resource-1', 'renamed.md')
    expect(api.patch).toHaveBeenCalledWith('/api/resources/resource-1', { name: 'renamed.md' })

    api.put.mockResolvedValueOnce({ data: { data: { ...resource, knowledgeBaseId: 2 } } })
    await libraryResourceRepository.updateAssociations('resource-1', { projectId: null, knowledgeBaseId: 2 })
    expect(api.put).toHaveBeenCalledWith('/api/resources/resource-1/associations', { projectId: null, knowledgeBaseId: 2 })

    api.post.mockResolvedValueOnce({ data: { data: { ...resource, status: 'processing' } } })
    await libraryResourceRepository.retry('resource-1')
    expect(api.post).toHaveBeenCalledWith('/api/resources/resource-1/retry')

    api.get.mockResolvedValueOnce({ data: { data: { resource, status: 'ready', previewKind: 'text', textContent: 'hello' } } })
    await libraryResourceRepository.preview('resource-1')
    expect(api.get).toHaveBeenCalledWith('/api/resources/resource-1/preview')

    api.get.mockResolvedValueOnce({ data: new Blob(['hello']) })
    await libraryResourceRepository.download('resource-1')
    expect(api.get).toHaveBeenCalledWith('/api/resources/resource-1/download', { responseType: 'blob' })

    api.delete.mockResolvedValueOnce({ data: { data: null } })
    await libraryResourceRepository.remove('resource-1')
    expect(api.delete).toHaveBeenCalledWith('/api/resources/resource-1')
  })

  it('covers knowledge-base CRUD and deletion endpoints', async () => {
    const knowledgeBase = { id: 2, name: 'Java', docCount: 0, createTime: '', updateTime: '' }
    api.get.mockResolvedValueOnce({ data: { data: [knowledgeBase] } })
    expect(await knowledgeBaseRepository.list()).toHaveLength(1)

    api.post.mockResolvedValueOnce({ data: { data: knowledgeBase } })
    await knowledgeBaseRepository.create({ name: 'Java' })
    expect(api.post).toHaveBeenCalledWith('/api/kb/create', expect.objectContaining({ name: 'Java' }))

    api.put.mockResolvedValueOnce({ data: { data: knowledgeBase } })
    await knowledgeBaseRepository.update({
      id: 2,
      name: 'Java',
      documentCount: 0,
      createTime: '',
      updateTime: '',
    })
    expect(api.put).toHaveBeenCalledWith('/api/kb/2', expect.objectContaining({ name: 'Java' }))

    api.delete.mockResolvedValueOnce({ data: { data: null } })
    await knowledgeBaseRepository.remove(2)
    expect(api.delete).toHaveBeenCalledWith('/api/kb/2')
  })

  it('covers the intelligent-learning project, generation, activity, answer, and resource chain', async () => {
    api.get.mockResolvedValueOnce({ data: { data: [apiProject] } })
    await learningRepository.listPlans()
    expect(api.get).toHaveBeenCalledWith('/api/learning/projects')

    api.get.mockResolvedValueOnce({ data: { data: apiProject } })
    await learningRepository.getPlan(7)
    expect(api.get).toHaveBeenCalledWith('/api/learning/projects/7')

    api.post.mockResolvedValueOnce({ data: { data: apiProject } })
    await learningRepository.createDraft({ title: 'Java', knowledgeBaseId: null })
    expect(api.post).toHaveBeenCalledWith('/api/learning/projects/drafts', { title: 'Java', knowledgeBaseId: null })

    api.patch.mockResolvedValueOnce({ data: { data: apiProject } })
    await learningRepository.updatePlan(7, { title: 'Java 进阶' })
    expect(api.patch).toHaveBeenCalledWith('/api/learning/projects/7', { title: 'Java 进阶' })

    api.post.mockResolvedValueOnce({ data: { data: { jobId: 'profile-job', status: 'pending', progress: 0 } } })
    await learningRepository.startProfileGeneration({ knowledgeBaseId: null, text: '学习 Java' })
    expect(api.post).toHaveBeenCalledWith('/api/learning/profile-jobs', { knowledgeBaseId: null, text: '学习 Java' })

    const profile = { goal: '', subject: 'Java', foundation: '', weakPoints: [], period: '', dailyTime: '', preferences: [], source: '', extra: '' }
    api.post.mockResolvedValueOnce({ data: { data: { content: '# Java', resourceId: 'confirmation-1' } } })
    await learningRepository.generateConfirmation({
      setupId: 'setup-1', knowledgeBaseId: null, goal: '学习 Java', profile, clientRequestId: 'confirm-1',
    })
    expect(api.post).toHaveBeenCalledWith('/api/learning/profile-confirmations', expect.objectContaining({ setupId: 'setup-1' }))

    api.get.mockResolvedValueOnce({ data: { data: { jobId: 'profile-job', status: 'succeeded', result: { profile } } } })
    await learningRepository.getGenerationJob('profile-job')
    expect(api.get).toHaveBeenCalledWith('/api/learning/generation-jobs/profile-job')

    api.post.mockResolvedValueOnce({ data: { data: { jobId: 'plan-job', status: 'pending' } } })
    await learningRepository.startPlanGeneration({
      prompt: '学习 Java', knowledgeBaseId: null, targetType: '考试复习', preferences: [], resourceGroups: [], period: '3 天',
      foundation: '一般', weakPoints: '', dailyTime: '60 分钟', studyDepth: '系统学习', questionCount: 10, supplementalRequirement: '',
    })
    expect(api.post).toHaveBeenCalledWith('/api/learning/plan-jobs', expect.objectContaining({ prompt: '学习 Java' }))

    api.post.mockResolvedValueOnce({ data: { data: apiProject } })
    await learningRepository.recordActivity({ projectId: 7, taskId: 1, eventType: 'start', clientRequestId: 'activity-1' })
    expect(api.post).toHaveBeenCalledWith('/api/learning/projects/7/activities', expect.objectContaining({ taskId: 1 }))

    const answerResult = { correct: true, explanation: '', correctAnswer: 'A', taskProgress: 100, projectProgress: 10 }
    api.post.mockResolvedValueOnce({ data: { data: answerResult } })
    await learningRepository.submitAnswer({ projectId: 7, exerciseId: 2, answer: 'A', clientRequestId: 'answer-1' })
    expect(api.post).toHaveBeenCalledWith('/api/learning/projects/7/answers', expect.objectContaining({ exerciseId: 2 }))

    api.post.mockResolvedValueOnce({ data: { data: [answerResult] } })
    await learningRepository.submitAnswers({ projectId: 7, answers: [{ exerciseId: 2, answer: 'A' }], clientRequestId: 'answers-1' })
    expect(api.post).toHaveBeenCalledWith('/api/learning/projects/7/answers/batch', expect.objectContaining({ answers: expect.any(Array) }))

    api.post.mockResolvedValueOnce({ data: { data: { jobId: 'adaptive-job', status: 'pending' } } })
    await learningRepository.startAdaptivePracticeGeneration(7, 1, { mode: 'repeat', count: 5, difficultyMode: '保持难度' })
    expect(api.post).toHaveBeenCalledWith('/api/learning/projects/7/tasks/1/adaptive-practice-jobs', expect.objectContaining({ count: 5 }))

    api.post.mockResolvedValueOnce({ data: { data: { jobId: 'review-job', status: 'pending' } } })
    await learningRepository.startWrongReviewGeneration(7, [2], { count: 3, difficultyMode: '保持难度' })
    expect(api.post).toHaveBeenCalledWith('/api/learning/projects/7/mistake-review-jobs', expect.objectContaining({ wrongIds: [2] }))

    api.post.mockResolvedValueOnce({ data: { data: { jobId: 'resource-job', status: 'pending' } } })
    await learningRepository.startResourceGeneration(7, 4)
    expect(api.post).toHaveBeenCalledWith('/api/learning/projects/7/resource-jobs', { learningResourceId: 4 })

    api.get.mockResolvedValueOnce({ data: new Blob(['resource']) })
    await learningRepository.downloadResource(7, 4)
    expect(api.get).toHaveBeenCalledWith('/api/learning/projects/7/resources/4/download', { responseType: 'blob' })

    api.delete.mockResolvedValueOnce({ data: { data: null } })
    await learningRepository.removePlan(7)
    expect(api.delete).toHaveBeenCalledWith('/api/learning/projects/7')
  })
})
