import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { USER_KEY } from '@/api/request'
import { learningRepository } from '@/repositories/learning'
import type { LearningSetupStateDto } from '@/types/contracts/learning'

const setupState: LearningSetupStateDto = {
  setupId: 'setup-1',
  knowledgeBaseId: 2,
  prompt: '三天复习 Java',
  profile: {
    goal: '考试复习',
    subject: 'Java',
    foundation: '一般',
    weakPoints: ['多态'],
    period: '3 天',
    dailyTime: '60 分钟',
    preferences: ['练习驱动'],
    source: 'Java 知识库',
    extra: '',
  },
  mediaAssetIds: [],
  sourceResourceIds: ['resource-1'],
  uploadedFileNames: ['notes.md'],
  confirmationResourceId: null,
  confirmationDocument: '',
  phase: 'profile',
  profileMessageId: 'profile-message-1',
  documentMessageId: '',
  updatedAt: '2026-07-18T08:00:00.000Z',
}

describe('Mock intelligent-learning repository parity', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: 202 }))
  })

  afterEach(() => vi.useRealTimers())

  it('persists setup state, active jobs, and exercise drafts through repository contracts', async () => {
    const project = (await learningRepository.listPlans())[0]!
    await learningRepository.saveSetupState(project.id, setupState)
    expect(await learningRepository.getSetupState(project.id)).toMatchObject({ setupId: 'setup-1', prompt: '三天复习 Java' })

    const active = {
      jobId: 'job-1',
      draftPlanId: project.id,
      sourceResourceIds: ['resource-1'],
      knowledgeBaseId: 2,
      startedAt: Date.now(),
    }
    await learningRepository.saveActivePlanGeneration(project.id, active)
    expect(await learningRepository.getActivePlanGeneration(project.id)).toEqual(active)

    const exercise = project.exercises[0]!
    await learningRepository.saveExerciseDraft(project.id, { exerciseId: exercise.id, answer: 'A' })
    expect(await learningRepository.listExerciseDrafts(project.id)).toEqual([
      expect.objectContaining({ exerciseId: exercise.id, answer: 'A' }),
    ])

    await learningRepository.removeExerciseDrafts(project.id, [exercise.id])
    await learningRepository.removeActivePlanGeneration(project.id)
    await learningRepository.removeSetupState(project.id)
    expect(await learningRepository.listExerciseDrafts(project.id)).toEqual([])
    expect(await learningRepository.getActivePlanGeneration(project.id)).toBeNull()
    expect(await learningRepository.getSetupState(project.id)).toBeNull()
  })

  it('exposes pending, running, and succeeded generation states', async () => {
    const now = new Date('2026-07-18T08:00:00.000Z')
    vi.useFakeTimers()
    vi.setSystemTime(now)
    const job = await learningRepository.startProfileGeneration({
      knowledgeBaseId: null,
      text: '帮我制定 Java 学习方案',
    })
    expect(job.status).toBe('pending')

    vi.setSystemTime(now.getTime() + 400)
    expect((await learningRepository.getGenerationJob(job.jobId)).status).toBe('running')

    vi.setSystemTime(now.getTime() + 1_000)
    const completed = await learningRepository.getGenerationJob(job.jobId)
    expect(completed.status).toBe('succeeded')
    expect(completed.result).toBeTruthy()
  })

  it('keeps a failed generation job retryable instead of returning mock success', async () => {
    const now = new Date('2026-07-18T08:00:00.000Z')
    vi.useFakeTimers()
    vi.setSystemTime(now)
    const job = await learningRepository.startProfileGeneration({
      knowledgeBaseId: null,
      text: '[mock-fail] 测试画像失败',
    })
    vi.setSystemTime(now.getTime() + 1_000)
    const failed = await learningRepository.getGenerationJob(job.jobId)
    expect(failed).toMatchObject({
      status: 'failed',
      errorCode: 'MOCK_GENERATION_FAILED',
      errorMessage: 'Mock 学习画像生成失败',
    })
    expect(failed.result).toBeUndefined()
  })

  it('persists entering a wrong-review set instead of changing UI state only', async () => {
    const plans = await learningRepository.listPlans()
    const project = plans.find((item) => item.exercises.length)!
    const exercise = project.exercises[0]!
    project.wrongReviewSets = [{
      id: 991,
      title: '多态巩固',
      exerciseIds: [exercise.id],
      sourceWrongIds: [],
      status: '待作答',
      createdAt: '刚刚',
      difficultyMode: '保持难度',
    }]
    learningRepository.persistMockSnapshot(plans)

    const updated = await learningRepository.startWrongReviewSet(project.id, 991, 'start-review-1')
    expect(updated.wrongReviewSets?.[0]?.status).toBe('作答中')
    expect((await learningRepository.getPlan(project.id)).wrongReviewSets?.[0]?.status).toBe('作答中')
  })
})
