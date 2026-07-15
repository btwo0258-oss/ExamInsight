import { getLearningPlans, saveLearningPlans } from '@/api/learning'
import { request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { createMockLearningDraft, createMockLearningPlan, evaluateMockExerciseAnswer } from '@/mock/generators/learning'
import { buildMockLearningConfirmation, createMockLearningProfileResult } from '@/mock/generators/learningProfile'
import { mockSession } from '@/mock/storage'
import type { Exercise, LearningPlan } from '@/mock/student'
import type { AsyncJob } from '@/types/contracts/common'
import type {
  CreateLearningDraftInput,
  LearningConfirmationRequest,
  CreateLearningPlanInput,
  LearningProfileRequest,
  LearningProfileResult,
  RecordLearningActivityRequest,
  SubmitAnswerRequest,
} from '@/types/contracts/learning'

export type AnswerResult = {
  correct: boolean
  score?: number
  feedback?: string
  explanation: string
  correctAnswer: string
}

export interface LearningRepository {
  initialPlans(): LearningPlan[]
  persistMockSnapshot(plans: LearningPlan[]): void
  listPlans(): Promise<LearningPlan[]>
  getPlan(id: number): Promise<LearningPlan>
  createDraft(input: CreateLearningDraftInput): Promise<LearningPlan>
  startProfileGeneration(input: LearningProfileRequest): Promise<AsyncJob<LearningProfileResult>>
  generateConfirmation(input: LearningConfirmationRequest): Promise<string>
  getGenerationJob<T>(jobId: string): Promise<AsyncJob<T>>
  startPlanGeneration(input: CreateLearningPlanInput): Promise<AsyncJob<{ projectId: number }>>
  recordActivity(input: RecordLearningActivityRequest): Promise<LearningPlan>
  submitAnswer(input: SubmitAnswerRequest): Promise<AnswerResult>
  startAdaptivePracticeGeneration(projectId: number, sourceTaskId: number, input: { mode: 'repeat' | 'reinforce'; count: number; difficultyMode: '保持难度' | '逐步提升' }): Promise<AsyncJob<{ projectId: number }>>
  startWrongReviewGeneration(projectId: number, wrongIds: number[], input: { count: number; difficultyMode: '保持难度' | '逐步提升' }): Promise<AsyncJob<{ projectId: number }>>
  startResourceGeneration(projectId: number, resourceId: number): Promise<AsyncJob<{ projectId: number }>>
  downloadResource(projectId: number, resourceId: number): Promise<Blob>
}

const JOB_DOMAIN = 'learning-jobs'

function readJobs() {
  return mockSession.get<Record<string, AsyncJob<unknown>>>(JOB_DOMAIN, {})
}

function saveJob<T>(job: AsyncJob<T>) {
  const jobs = readJobs()
  jobs[job.jobId] = job as AsyncJob<unknown>
  mockSession.set(JOB_DOMAIN, jobs)
  return job
}

const mockLearningRepository: LearningRepository = {
  initialPlans: getLearningPlans,
  persistMockSnapshot: saveLearningPlans,
  async listPlans() {
    return getLearningPlans()
  },
  async getPlan(id) {
    const plan = getLearningPlans().find((item) => item.id === id)
    if (!plan) throw new Error('Learning project not found')
    return plan
  },
  async createDraft(input) {
    const plans = getLearningPlans()
    const draft = createMockLearningDraft(input, plans)
    plans.unshift(draft)
    saveLearningPlans(plans)
    return draft
  },
  async startProfileGeneration(input) {
    const result = createMockLearningProfileResult(input)
    return saveJob({
      jobId: `mock-profile-${Date.now()}`,
      status: 'succeeded',
      progress: 100,
      result,
    })
  },
  async generateConfirmation(input) {
    return buildMockLearningConfirmation(input)
  },
  async getGenerationJob<T>(jobId: string) {
    const job = readJobs()[jobId]
    if (!job) throw new Error('Generation job not found')
    return job as AsyncJob<T>
  },
  async startPlanGeneration(input) {
    const plans = getLearningPlans()
    const plan = createMockLearningPlan(input, plans)
    const draftIndex = plans.findIndex((item) => item.id === input.draftPlanId)
    if (draftIndex >= 0) plans.splice(draftIndex, 1, plan)
    else plans.unshift(plan)
    saveLearningPlans(plans)
    return saveJob({
      jobId: `mock-plan-${Date.now()}`,
      status: 'succeeded',
      progress: 100,
      result: { projectId: plan.id },
    })
  },
  async recordActivity(input) {
    const plan = getLearningPlans().find((item) => item.id === input.projectId)
    if (!plan) throw new Error('Learning project not found')
    return plan
  },
  async submitAnswer(input) {
    const plan = getLearningPlans().find((item) => item.id === input.projectId)
    const exercise = plan?.exercises.find((item) => item.id === input.exerciseId)
    if (!exercise) throw new Error('Exercise not found')
    return evaluateMockExerciseAnswer(exercise, input.answer)
  },
  async startAdaptivePracticeGeneration(projectId) {
    const plan = getLearningPlans().find((item) => item.id === projectId)
    if (!plan) throw new Error('Learning project not found')
    return saveJob({
      jobId: `mock-adaptive-practice-${Date.now()}`,
      status: 'succeeded',
      progress: 100,
      result: { projectId },
    })
  },
  async startWrongReviewGeneration(projectId) {
    const plan = getLearningPlans().find((item) => item.id === projectId)
    if (!plan) throw new Error('Learning project not found')
    return saveJob({
      jobId: `mock-wrong-review-${Date.now()}`,
      status: 'succeeded',
      progress: 100,
      result: { projectId },
    })
  },
  async startResourceGeneration(projectId) {
    return saveJob({
      jobId: `mock-resource-${Date.now()}`,
      status: 'succeeded',
      progress: 100,
      result: { projectId },
    })
  },
  async downloadResource(projectId, resourceId) {
    const resource = getLearningPlans().find((item) => item.id === projectId)?.resources.find((item) => item.id === resourceId)
    if (!resource) throw new Error('学习资源不存在')
    return new Blob([resource.content || resource.desc], { type: 'text/plain;charset=utf-8' })
  },
}

function unwrap<T>(response: { data: unknown }): T {
  const payload = response.data as { data?: T }
  return (payload?.data ?? response.data) as T
}

function enumValue<T extends string>(value: unknown, values: Record<string, T>, fallback: T): T {
  return typeof value === 'string' ? values[value] ?? (value as T) : fallback
}

export function normalizeLearningPlan(raw: Record<string, any>): LearningPlan {
  const projectStatuses = {
    draft: '待开启',
    configuring: '待完善',
    ready: '已生成',
    in_progress: '进行中',
    completed: '已完成',
  } as const
  const taskStatuses = {
    not_started: '未开始',
    in_progress: '进行中',
    completed: '已完成',
  } as const
  const resourceStatuses = {
    not_selected: '未选择',
    generating: '生成中',
    ready: '已生成',
    failed: '生成失败',
  } as const
  const wrongStatuses = { needs_review: '需巩固', mastered: '已掌握' } as const
  const trainingStatuses = { pending: '待练习', answering: '答题中', submitted: '已交卷' } as const
  const reviewStatuses = { pending: '待作答', answering: '作答中', completed: '已完成' } as const

  return {
    ...raw,
    status: enumValue(raw.status, projectStatuses, '待开启'),
    stages: (raw.stages ?? []).map((stage: Record<string, any>) => ({
      ...stage,
      tasks: (stage.tasks ?? []).map((task: Record<string, any>) => ({
        ...task,
        status: enumValue(task.status, taskStatuses, '未开始'),
      })),
    })),
    resources: (raw.resources ?? []).map((resource: Record<string, any>) => ({
      ...resource,
      status: enumValue(resource.status, resourceStatuses, '未选择'),
    })),
    wrongQuestions: (raw.wrongQuestions ?? []).map((wrong: Record<string, any>) => ({
      ...wrong,
      status: enumValue(wrong.status, wrongStatuses, '需巩固'),
    })),
    trainingSets: (raw.trainingSets ?? []).map((set: Record<string, any>) => ({
      ...set,
      status: enumValue(set.status, trainingStatuses, '待练习'),
    })),
    wrongReviewSets: (raw.wrongReviewSets ?? []).map((set: Record<string, any>) => ({
      ...set,
      status: enumValue(set.status, reviewStatuses, '待作答'),
    })),
  } as LearningPlan
}

const apiLearningRepository: LearningRepository = {
  initialPlans() {
    return []
  },
  persistMockSnapshot() {},
  async listPlans() {
    return unwrap<Record<string, any>[]>(await request.get('/api/learning/projects')).map(normalizeLearningPlan)
  },
  async getPlan(id) {
    return normalizeLearningPlan(unwrap<Record<string, any>>(await request.get(`/api/learning/projects/${id}`)))
  },
  async createDraft(input) {
    return normalizeLearningPlan(unwrap<Record<string, any>>(await request.post('/api/learning/projects/drafts', input)))
  },
  async startProfileGeneration(input) {
    return unwrap<AsyncJob<LearningProfileResult>>(await request.post('/api/learning/profile-jobs', input))
  },
  async generateConfirmation(input) {
    const response = await request.post('/api/learning/profile-confirmations', input)
    return unwrap<{ content: string }>(response).content
  },
  async getGenerationJob<T>(jobId: string) {
    return unwrap<AsyncJob<T>>(await request.get(`/api/learning/generation-jobs/${jobId}`))
  },
  async startPlanGeneration(input) {
    return unwrap<AsyncJob<{ projectId: number }>>(await request.post('/api/learning/plan-jobs', input))
  },
  async recordActivity(input) {
    return normalizeLearningPlan(unwrap<Record<string, any>>(await request.post(`/api/learning/projects/${input.projectId}/activities`, input)))
  },
  async submitAnswer(input) {
    return unwrap<AnswerResult>(await request.post(`/api/learning/projects/${input.projectId}/answers`, input))
  },
  async startAdaptivePracticeGeneration(projectId, sourceTaskId, input) {
    return unwrap<AsyncJob<{ projectId: number }>>(await request.post(`/api/learning/projects/${projectId}/tasks/${sourceTaskId}/adaptive-practice-jobs`, input))
  },
  async startWrongReviewGeneration(projectId, wrongIds, input) {
    return unwrap<AsyncJob<{ projectId: number }>>(await request.post(`/api/learning/projects/${projectId}/mistake-review-jobs`, { wrongIds, ...input }))
  },
  async startResourceGeneration(projectId, resourceId) {
    return unwrap<AsyncJob<{ projectId: number }>>(await request.post(`/api/learning/projects/${projectId}/resource-jobs`, { resourceId }))
  },
  async downloadResource(projectId, resourceId) {
    const response = await request.get(`/api/learning/projects/${projectId}/resources/${resourceId}/download`, { responseType: 'blob' })
    return response.data as Blob
  },
}

export const learningRepository = isMockDataSource ? mockLearningRepository : apiLearningRepository

export function findExercise(plan: LearningPlan, exerciseId: number): Exercise | undefined {
  return plan.exercises.find((exercise) => exercise.id === exerciseId)
}
