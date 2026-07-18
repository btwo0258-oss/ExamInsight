import { getLearningPlans, saveLearningPlans } from '@/api/learning'
import { request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { createMockLearningDraft, createMockLearningPlan, evaluateMockExerciseAnswer } from '@/mock/generators/learning'
import { buildMockLearningConfirmation, createMockLearningProfileResult } from '@/mock/generators/learningProfile'
import { mockSession } from '@/mock/storage'
import type { Exercise, LearningPlan } from '@/mock/student'
import type { AsyncJob } from '@/types/contracts/common'
import type {
  AnswerResult,
  ActivePlanGenerationDto,
  CreateLearningDraftInput,
  CreateLearningPlanInput,
  GeneratedProjectResourceRequest,
  ExerciseDraftDto,
  LearningConfirmationRequest,
  LearningConfirmationResult,
  LearningProjectDto,
  LearningProfileRequest,
  LearningProfileResult,
  LearningSetupStateDto,
  RecordLearningActivityRequest,
  SubmitAnswerRequest,
  SubmitAnswerBatchRequest,
  UpdateLearningProjectRequest,
} from '@/types/contracts/learning'

export interface LearningRepository {
  initialPlans(): LearningPlan[]
  persistMockSnapshot(plans: LearningPlan[]): void
  listPlans(): Promise<LearningPlan[]>
  getPlan(id: number): Promise<LearningPlan>
  createDraft(input: CreateLearningDraftInput): Promise<LearningPlan>
  updatePlan(id: number, input: UpdateLearningProjectRequest): Promise<LearningPlan>
  removePlan(id: number): Promise<void>
  getSetupState(projectId: number): Promise<LearningSetupStateDto | null>
  saveSetupState(projectId: number, state: LearningSetupStateDto): Promise<LearningSetupStateDto>
  removeSetupState(projectId: number): Promise<void>
  getActivePlanGeneration(projectId: number): Promise<ActivePlanGenerationDto | null>
  saveActivePlanGeneration(projectId: number, state: ActivePlanGenerationDto): Promise<ActivePlanGenerationDto>
  removeActivePlanGeneration(projectId: number): Promise<void>
  listExerciseDrafts(projectId: number): Promise<ExerciseDraftDto[]>
  saveExerciseDraft(projectId: number, draft: ExerciseDraftDto): Promise<ExerciseDraftDto>
  removeExerciseDrafts(projectId: number, exerciseIds: number[]): Promise<void>
  startProfileGeneration(input: LearningProfileRequest): Promise<AsyncJob<LearningProfileResult>>
  generateConfirmation(input: LearningConfirmationRequest): Promise<LearningConfirmationResult>
  getGenerationJob<T>(jobId: string): Promise<AsyncJob<T>>
  startPlanGeneration(input: CreateLearningPlanInput): Promise<AsyncJob<{ projectId: number }>>
  recordActivity(input: RecordLearningActivityRequest): Promise<LearningPlan>
  submitAnswer(input: SubmitAnswerRequest): Promise<AnswerResult>
  submitAnswers(input: SubmitAnswerBatchRequest): Promise<AnswerResult[]>
  startAdaptivePracticeGeneration(projectId: number, sourceTaskId: number, input: { mode: 'repeat' | 'reinforce'; count: number; difficultyMode: '保持难度' | '逐步提升' }): Promise<AsyncJob<{ projectId: number }>>
  startWrongReviewGeneration(projectId: number, wrongIds: number[], input: { count: number; difficultyMode: '保持难度' | '逐步提升' }): Promise<AsyncJob<{ projectId: number }>>
  startWrongReviewSet(projectId: number, setId: number, clientRequestId: string): Promise<LearningPlan>
  startResourceGeneration(projectId: number, learningResourceId: number): Promise<AsyncJob<{ projectId: number }>>
  attachGeneratedResource(projectId: number, input: GeneratedProjectResourceRequest): Promise<LearningPlan>
  downloadResource(projectId: number, learningResourceId: number): Promise<Blob>
}

const JOB_DOMAIN = 'learning-jobs'
const SETUP_DOMAIN = 'learning-setup-states'
const ACTIVE_PLAN_DOMAIN = 'learning-active-plan-generations'
const EXERCISE_DRAFT_DOMAIN = 'learning-exercise-drafts'

type StoredMockJob<T> = AsyncJob<T> & {
  mockStartedAt?: number
  mockDurationMs?: number
  mockResult?: T
  mockFailureMessage?: string
  mockPendingPlan?: { plan: LearningPlan; draftPlanId: number | null }
}

function readJobs() {
  return mockSession.get<Record<string, AsyncJob<unknown>>>(JOB_DOMAIN, {})
}

function saveJob<T>(job: AsyncJob<T>) {
  const jobs = readJobs()
  jobs[job.jobId] = job as AsyncJob<unknown>
  mockSession.set(JOB_DOMAIN, jobs)
  return job
}

function createPendingJob<T>(
  prefix: string,
  result: T,
  durationMs = 750,
  failureMessage?: string,
  pendingPlan?: StoredMockJob<T>['mockPendingPlan'],
) {
  return saveJob<T>({
    jobId: `mock-${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    status: 'pending',
    progress: 0,
    mockStartedAt: Date.now(),
    mockDurationMs: durationMs,
    mockResult: result,
    mockFailureMessage: failureMessage,
    mockPendingPlan: pendingPlan,
  } as StoredMockJob<T>)
}

function readRecord<T>(domain: string) {
  return mockSession.get<Record<string, T>>(domain, {})
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
  async updatePlan(id, input) {
    const plans = getLearningPlans()
    const plan = plans.find((item) => item.id === id)
    if (!plan) throw new Error('Learning project not found')
    if (input.title?.trim()) plan.title = input.title.trim()
    if (input.targetType) plan.targetType = input.targetType
    if (input.period) plan.period = input.period
    plan.updatedAt = '刚刚'
    saveLearningPlans(plans)
    return plan
  },
  async removePlan(id) {
    saveLearningPlans(getLearningPlans().filter((item) => item.id !== id))
  },
  async getSetupState(projectId) {
    return readRecord<LearningSetupStateDto>(SETUP_DOMAIN)[String(projectId)] ?? null
  },
  async saveSetupState(projectId, state) {
    const states = readRecord<LearningSetupStateDto>(SETUP_DOMAIN)
    const saved = { ...state, updatedAt: new Date().toISOString() }
    states[String(projectId)] = saved
    mockSession.set(SETUP_DOMAIN, states)
    return saved
  },
  async removeSetupState(projectId) {
    const states = readRecord<LearningSetupStateDto>(SETUP_DOMAIN)
    delete states[String(projectId)]
    mockSession.set(SETUP_DOMAIN, states)
  },
  async getActivePlanGeneration(projectId) {
    return readRecord<ActivePlanGenerationDto>(ACTIVE_PLAN_DOMAIN)[String(projectId)] ?? null
  },
  async saveActivePlanGeneration(projectId, state) {
    const states = readRecord<ActivePlanGenerationDto>(ACTIVE_PLAN_DOMAIN)
    states[String(projectId)] = state
    mockSession.set(ACTIVE_PLAN_DOMAIN, states)
    return state
  },
  async removeActivePlanGeneration(projectId) {
    const states = readRecord<ActivePlanGenerationDto>(ACTIVE_PLAN_DOMAIN)
    delete states[String(projectId)]
    mockSession.set(ACTIVE_PLAN_DOMAIN, states)
  },
  async listExerciseDrafts(projectId) {
    return readRecord<ExerciseDraftDto[]>(EXERCISE_DRAFT_DOMAIN)[String(projectId)] ?? []
  },
  async saveExerciseDraft(projectId, draft) {
    const records = readRecord<ExerciseDraftDto[]>(EXERCISE_DRAFT_DOMAIN)
    const drafts = records[String(projectId)] ?? []
    const saved = { ...draft, updatedAt: new Date().toISOString() }
    const index = drafts.findIndex((item) => item.exerciseId === draft.exerciseId)
    if (index === -1) drafts.push(saved)
    else drafts[index] = saved
    records[String(projectId)] = drafts
    mockSession.set(EXERCISE_DRAFT_DOMAIN, records)
    return saved
  },
  async removeExerciseDrafts(projectId, exerciseIds) {
    const records = readRecord<ExerciseDraftDto[]>(EXERCISE_DRAFT_DOMAIN)
    const removed = new Set(exerciseIds)
    records[String(projectId)] = (records[String(projectId)] ?? []).filter((item) => !removed.has(Number(item.exerciseId)))
    mockSession.set(EXERCISE_DRAFT_DOMAIN, records)
  },
  async startProfileGeneration(input) {
    const result = createMockLearningProfileResult(input)
    return createPendingJob(
      'profile',
      result,
      750,
      /\[mock-fail\]/i.test(input.text) ? 'Mock 学习画像生成失败' : undefined,
    )
  },
  async generateConfirmation(input) {
    return { content: buildMockLearningConfirmation(input), resourceId: '' }
  },
  async getGenerationJob<T>(jobId: string) {
    const job = readJobs()[jobId] as StoredMockJob<T> | undefined
    if (!job) throw new Error('Generation job not found')
    if (!job.mockStartedAt || !job.mockDurationMs || !job.mockResult) return job
    const elapsed = Date.now() - job.mockStartedAt
    if (elapsed < 200) {
      job.status = 'pending'
      job.progress = 0
    } else if (elapsed < job.mockDurationMs) {
      job.status = 'running'
      job.progress = Math.max(1, Math.min(99, Math.round((elapsed / job.mockDurationMs) * 100)))
    } else if (job.mockFailureMessage) {
      job.status = 'failed'
      job.progress = 100
      job.errorCode = 'MOCK_GENERATION_FAILED'
      job.errorMessage = job.mockFailureMessage
      delete job.result
      delete job.mockFailureMessage
      delete job.mockStartedAt
      delete job.mockDurationMs
      delete job.mockResult
      delete job.mockPendingPlan
    } else {
      job.status = 'succeeded'
      job.progress = 100
      job.result = job.mockResult
      if (job.mockPendingPlan) {
        const plans = getLearningPlans()
        const { plan, draftPlanId } = job.mockPendingPlan
        const draftIndex = plans.findIndex((item) => item.id === draftPlanId)
        if (draftIndex >= 0) plans.splice(draftIndex, 1, plan)
        else if (!plans.some((item) => item.id === plan.id)) plans.unshift(plan)
        saveLearningPlans(plans)
      }
      delete job.mockStartedAt
      delete job.mockDurationMs
      delete job.mockResult
      delete job.mockPendingPlan
    }
    saveJob(job)
    return job
  },
  async startPlanGeneration(input) {
    const plans = getLearningPlans()
    const plan = createMockLearningPlan(input, plans)
    return createPendingJob(
      'plan',
      { projectId: plan.id },
      900,
      /\[mock-fail\]/i.test(input.prompt) ? 'Mock 学习方案生成失败' : undefined,
      { plan, draftPlanId: input.draftPlanId == null ? null : Number(input.draftPlanId) },
    )
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
    return {
      ...evaluateMockExerciseAnswer(exercise, input.answer),
      taskProgress: plan?.stages.flatMap((stage) => stage.tasks).find((task) => task.exerciseIds?.includes(exercise.id))?.done ? 100 : 0,
      projectProgress: plan?.progress ?? 0,
    }
  },
  async submitAnswers(input) {
    const plan = getLearningPlans().find((item) => item.id === input.projectId)
    if (!plan) throw new Error('Learning project not found')
    return input.answers.map((answer) => {
      const exercise = plan.exercises.find((item) => item.id === answer.exerciseId)
      if (!exercise) throw new Error('Exercise not found')
      if (answer.language && exercise.type === '代码题') exercise.selectedLanguage = answer.language as Exercise['selectedLanguage']
      return {
        ...evaluateMockExerciseAnswer(exercise, answer.answer),
        taskProgress: plan.stages.flatMap((stage) => stage.tasks).find((task) => task.exerciseIds?.includes(exercise.id))?.done ? 100 : 0,
        projectProgress: plan.progress,
      }
    })
  },
  async startAdaptivePracticeGeneration(projectId) {
    const plan = getLearningPlans().find((item) => item.id === projectId)
    if (!plan) throw new Error('Learning project not found')
    return createPendingJob('adaptive-practice', { projectId })
  },
  async startWrongReviewGeneration(projectId) {
    const plan = getLearningPlans().find((item) => item.id === projectId)
    if (!plan) throw new Error('Learning project not found')
    return createPendingJob('wrong-review', { projectId })
  },
  async startWrongReviewSet(projectId, setId) {
    const plans = getLearningPlans()
    const plan = plans.find((item) => item.id === projectId)
    const set = plan?.wrongReviewSets?.find((item) => item.id === setId)
    if (!plan || !set) throw new Error('Wrong review set not found')
    set.status = '作答中'
    set.exerciseIds.forEach((id) => {
      const exercise = plan.exercises.find((item) => item.id === id)
      if (!exercise) return
      exercise.draftAnswer = undefined
      exercise.userAnswer = undefined
      exercise.submitted = false
      exercise.gradingCorrect = undefined
      exercise.gradingScore = undefined
      exercise.gradingFeedback = undefined
      if (exercise.type === '代码题') exercise.codeDrafts = {}
    })
    saveLearningPlans(plans)
    return plan
  },
  async startResourceGeneration(projectId) {
    return createPendingJob('resource', { projectId })
  },
  async attachGeneratedResource(projectId) {
    const plan = getLearningPlans().find((item) => item.id === projectId)
    if (!plan) throw new Error('Learning project not found')
    return plan
  },
  async downloadResource(projectId, learningResourceId) {
    const resource = getLearningPlans().find((item) => item.id === projectId)?.resources.find((item) => item.id === learningResourceId)
    if (!resource) throw new Error('学习资源不存在')
    const content = resource.group === '思维导图' && resource.mindMapTreeData
      ? JSON.stringify(resource.mindMapTreeData, null, 2)
      : resource.content || resource.desc
    const type = resource.group === '思维导图' ? 'application/json;charset=utf-8' : 'text/markdown;charset=utf-8'
    return new Blob([content], { type })
  },
}

function unwrap<T>(response: { data: unknown }): T {
  const payload = response.data as { data?: T }
  return (payload?.data ?? response.data) as T
}

function enumValue<T extends string>(value: unknown, values: Record<string, T>, fallback: T): T {
  return typeof value === 'string' ? values[value] ?? fallback : fallback
}

export function normalizeLearningPlan(raw: LearningProjectDto): LearningPlan {
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
    needs_review: '需复习',
    locked: '已锁定',
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
    knowledgeBaseId: raw.knowledgeBaseId ?? null,
    status: enumValue(raw.status, projectStatuses, '待开启'),
    stages: (raw.stages ?? []).map((stage) => ({
      ...stage,
      tasks: (stage.tasks ?? []).map((task) => ({
        ...task,
        status: enumValue(task.status, taskStatuses, '未开始'),
      })),
    })),
    resources: (raw.resources ?? []).map((resource) => ({
      ...resource,
      status: enumValue(resource.status, resourceStatuses, '未选择'),
    })),
    wrongQuestions: (raw.wrongQuestions ?? []).map((wrong) => ({
      ...wrong,
      status: enumValue(wrong.status, wrongStatuses, '需巩固'),
    })),
    trainingSets: (raw.trainingSets ?? []).map((set) => ({
      ...set,
      status: enumValue(set.status, trainingStatuses, '待练习'),
    })),
    wrongReviewSets: (raw.wrongReviewSets ?? []).map((set) => ({
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
    return unwrap<LearningProjectDto[]>(await request.get('/api/learning/projects')).map(normalizeLearningPlan)
  },
  async getPlan(id) {
    return normalizeLearningPlan(unwrap<LearningProjectDto>(await request.get(`/api/learning/projects/${id}`)))
  },
  async createDraft(input) {
    return normalizeLearningPlan(unwrap<LearningProjectDto>(await request.post('/api/learning/projects/drafts', input)))
  },
  async updatePlan(id, input) {
    return normalizeLearningPlan(unwrap<LearningProjectDto>(await request.patch(`/api/learning/projects/${id}`, input)))
  },
  async removePlan(id) {
    await request.delete(`/api/learning/projects/${id}`)
  },
  async getSetupState(projectId) {
    return unwrap<LearningSetupStateDto | null>(await request.get(`/api/learning/projects/${projectId}/setup-state`))
  },
  async saveSetupState(projectId, state) {
    return unwrap<LearningSetupStateDto>(await request.put(`/api/learning/projects/${projectId}/setup-state`, state))
  },
  async removeSetupState(projectId) {
    await request.delete(`/api/learning/projects/${projectId}/setup-state`)
  },
  async getActivePlanGeneration(projectId) {
    return unwrap<ActivePlanGenerationDto | null>(await request.get(`/api/learning/projects/${projectId}/active-plan-generation`))
  },
  async saveActivePlanGeneration(projectId, state) {
    return unwrap<ActivePlanGenerationDto>(await request.put(`/api/learning/projects/${projectId}/active-plan-generation`, state))
  },
  async removeActivePlanGeneration(projectId) {
    await request.delete(`/api/learning/projects/${projectId}/active-plan-generation`)
  },
  async listExerciseDrafts(projectId) {
    return unwrap<ExerciseDraftDto[]>(await request.get(`/api/learning/projects/${projectId}/exercise-drafts`))
  },
  async saveExerciseDraft(projectId, draft) {
    return unwrap<ExerciseDraftDto>(await request.put(`/api/learning/projects/${projectId}/exercise-drafts/${draft.exerciseId}`, draft))
  },
  async removeExerciseDrafts(projectId, exerciseIds) {
    await request.delete(`/api/learning/projects/${projectId}/exercise-drafts`, { data: { exerciseIds } })
  },
  async startProfileGeneration(input) {
    return unwrap<AsyncJob<LearningProfileResult>>(await request.post('/api/learning/profile-jobs', input))
  },
  async generateConfirmation(input) {
    return unwrap<LearningConfirmationResult>(await request.post('/api/learning/profile-confirmations', input))
  },
  async getGenerationJob<T>(jobId: string) {
    return unwrap<AsyncJob<T>>(await request.get(`/api/learning/generation-jobs/${jobId}`))
  },
  async startPlanGeneration(input) {
    return unwrap<AsyncJob<{ projectId: number }>>(await request.post('/api/learning/plan-jobs', input))
  },
  async recordActivity(input) {
    return normalizeLearningPlan(unwrap<LearningProjectDto>(await request.post(`/api/learning/projects/${input.projectId}/activities`, input)))
  },
  async submitAnswer(input) {
    return unwrap<AnswerResult>(await request.post(`/api/learning/projects/${input.projectId}/answers`, input))
  },
  async submitAnswers(input) {
    return unwrap<AnswerResult[]>(await request.post(`/api/learning/projects/${input.projectId}/answers/batch`, input))
  },
  async startAdaptivePracticeGeneration(projectId, sourceTaskId, input) {
    return unwrap<AsyncJob<{ projectId: number }>>(await request.post(`/api/learning/projects/${projectId}/tasks/${sourceTaskId}/adaptive-practice-jobs`, input))
  },
  async startWrongReviewGeneration(projectId, wrongIds, input) {
    return unwrap<AsyncJob<{ projectId: number }>>(await request.post(`/api/learning/projects/${projectId}/mistake-review-jobs`, { wrongIds, ...input }))
  },
  async startWrongReviewSet(projectId, setId, clientRequestId) {
    return normalizeLearningPlan(unwrap<LearningProjectDto>(await request.put(
      `/api/learning/projects/${projectId}/wrong-review-sets/${setId}/status`,
      { status: 'answering', clientRequestId },
    )))
  },
  async startResourceGeneration(projectId, learningResourceId) {
    return unwrap<AsyncJob<{ projectId: number }>>(await request.post(`/api/learning/projects/${projectId}/resource-jobs`, { learningResourceId }))
  },
  async attachGeneratedResource(projectId, input) {
    return normalizeLearningPlan(unwrap<LearningProjectDto>(
      await request.put(`/api/learning/projects/${projectId}/resources/generated`, input),
    ))
  },
  async downloadResource(projectId, learningResourceId) {
    const response = await request.get(`/api/learning/projects/${projectId}/resources/${learningResourceId}/download`, { responseType: 'blob' })
    return response.data as Blob
  },
}

export const learningRepository = isMockDataSource ? mockLearningRepository : apiLearningRepository

export function findExercise(plan: LearningPlan, exerciseId: number): Exercise | undefined {
  return plan.exercises.find((exercise) => exercise.id === exerciseId)
}
