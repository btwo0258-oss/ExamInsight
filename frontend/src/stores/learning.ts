import { computed, ref, toRaw } from 'vue'
import { defineStore } from 'pinia'
import type { CodeLanguageKey, Exercise, LearningPlan, LearningResource, WrongQuestion } from '@/mock'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { learningRepository } from '@/repositories/learning'
import {
  createMockQuestionBatch,
  createMockLearningMindMap,
  createMockLearningResourceContent,
  evaluateMockExerciseAnswer,
} from '@/mock/generators/learning'
import type {
  ActivePlanGenerationDto,
  CreateLearningDraftInput,
  CreateLearningPlanInput,
  ExerciseDraftDto,
  GeneratedProjectResourceRequest,
  LearningConfirmationRequest,
  LearningProfileRequest,
  LearningProfileResult,
  LearningSetupStateDto,
} from '@/types/contracts/learning'
import type { AsyncJob } from '@/types/contracts/common'
import type { ChatArtifactDto } from '@/types/contracts/artifact'
import type { ResourceFileType } from '@/types/contracts/library'
import { isApiDataSource } from '@/config/dataSource'

export type ProjectGeneratedResourceInput = Omit<GeneratedProjectResourceRequest, 'clientRequestId'> & {
  projectId: number
}

function generatedResourceRequestId(projectId: number, artifactId: string, resourceId: string) {
  return `project-resource:${projectId}:${artifactId}:${resourceId}`
}

function projectResourceGroup(fileType: ResourceFileType): LearningResource['group'] {
  if (fileType === 'presentation') return 'PPT'
  if (fileType === 'mindmap') return '思维导图'
  if (fileType === 'spreadsheet') return '电子表格'
  if (fileType === 'image') return '图片'
  if (fileType === 'archive') return '代码案例'
  if (fileType === 'audio') return '音频'
  if (fileType === 'document' || fileType === 'pdf') return '文档'
  return '其他文件'
}

function artifactExternalId(artifactId: string, prefix: string) {
  return artifactId.startsWith(prefix) ? artifactId.slice(prefix.length) : undefined
}

export type UpdateLearningPlanInput = {
  targetType: string
  period: string
  dailyTime: string
  weakPoints: string
  preferences: string[]
  keepExercises: boolean
  keepProgress: boolean
}

export type ExerciseResult = {
  correct: boolean
  explanation: string
  correctAnswer: string
  score?: number
  feedback?: string
}

export type TrainingSetResult = {
  total: number
  correctCount: number
  wrongCount: number
  correctRate: number
  wrongExerciseIds: number[]
}

type DifficultyStrategy = NonNullable<LearningPlan['questionBank']>['difficultyStrategy']

export const evaluateExerciseAnswer = evaluateMockExerciseAnswer

export const useLearningStore = defineStore('learning', () => {
  // Business data is loaded only after authentication. In particular, mock mode
  // must not hydrate a guest session with demo projects.
  const plans = ref<LearningPlan[]>([])
  const generatingResourceIds = ref<number[]>([])
  const isLoading = ref(false)
  const errorMessage = ref<string | null>(null)
  const libraryResourceStore = useLibraryResourceStore()
  const pendingReadingActivities = new Map<string, Omit<Parameters<typeof learningRepository.recordActivity>[0], 'clientRequestId'>>()
  const readingFlushTimers = new Map<string, number>()
  const READING_FLUSH_INTERVAL_MS = 10_000
  const ACTIVE_PLAN_JOB_STORAGE_KEY = 'examinsight.learning.active-plan-job.v1'

  type ActivePlanGeneration = ActivePlanGenerationDto

  function readActivePlanGeneration(): ActivePlanGeneration | null {
    try {
      const raw = sessionStorage.getItem(ACTIVE_PLAN_JOB_STORAGE_KEY)
      return raw ? JSON.parse(raw) as ActivePlanGeneration : null
    } catch {
      sessionStorage.removeItem(ACTIVE_PLAN_JOB_STORAGE_KEY)
      return null
    }
  }

  const activePlanGeneration = ref<ActivePlanGeneration | null>(readActivePlanGeneration())

  function cacheActivePlanGeneration(value: ActivePlanGeneration | null) {
    activePlanGeneration.value = value
    if (value) sessionStorage.setItem(ACTIVE_PLAN_JOB_STORAGE_KEY, JSON.stringify(value))
    else sessionStorage.removeItem(ACTIVE_PLAN_JOB_STORAGE_KEY)
  }

  async function persistActivePlanGeneration(value: ActivePlanGeneration | null) {
    const previous = activePlanGeneration.value
    cacheActivePlanGeneration(value)
    try {
      if (value?.draftPlanId) {
        await learningRepository.saveActivePlanGeneration(Number(value.draftPlanId), value)
      } else if (!value && previous?.draftPlanId) {
        await learningRepository.removeActivePlanGeneration(Number(previous.draftPlanId))
      }
    } catch (error) {
      errorMessage.value = error instanceof Error ? `生成任务恢复状态同步失败：${error.message}` : '生成任务恢复状态同步失败'
    }
  }

  async function waitForGenerationJob<T>(
    initialJob: AsyncJob<T>,
    fallbackMessage: string,
  ): Promise<AsyncJob<T> & { result: T }> {
    let job = initialJob
    for (let attempt = 0; ['pending', 'running'].includes(job.status) && attempt < 120; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000))
      job = await learningRepository.getGenerationJob<T>(job.jobId)
    }
    if (job.status !== 'succeeded' || !job.result) {
      throw new Error(job.errorMessage || (['pending', 'running'].includes(job.status) ? '生成任务仍在处理中，请稍后重试' : fallbackMessage))
    }
    return job as AsyncJob<T> & { result: T }
  }

  type ApiExerciseDraft = { answer: string; language?: CodeLanguageKey }
  const exerciseDraftTimers = new Map<string, number>()

  function exerciseDraftStorageKey(projectId: number) {
    return `examinsight.learning.answer-drafts.v1.${projectId}`
  }

  function readExerciseDrafts(projectId: number) {
    try {
      const raw = sessionStorage.getItem(exerciseDraftStorageKey(projectId))
      return raw ? JSON.parse(raw) as Record<string, ApiExerciseDraft> : {}
    } catch {
      sessionStorage.removeItem(exerciseDraftStorageKey(projectId))
      return {} as Record<string, ApiExerciseDraft>
    }
  }

  function writeExerciseDraft(planId: number, exercise: Exercise) {
    const drafts = readExerciseDrafts(planId)
    drafts[String(exercise.id)] = {
      answer: exercise.draftAnswer ?? '',
      language: exercise.selectedLanguage,
    }
    sessionStorage.setItem(exerciseDraftStorageKey(planId), JSON.stringify(drafts))
    const key = `${planId}:${exercise.id}`
    const previousTimer = exerciseDraftTimers.get(key)
    if (previousTimer !== undefined) window.clearTimeout(previousTimer)
    exerciseDraftTimers.set(key, window.setTimeout(async () => {
      exerciseDraftTimers.delete(key)
      try {
        await learningRepository.saveExerciseDraft(planId, {
          exerciseId: exercise.id,
          answer: exercise.draftAnswer ?? '',
          language: exercise.selectedLanguage,
        })
      } catch (error) {
        errorMessage.value = error instanceof Error ? `练习草稿同步失败：${error.message}` : '练习草稿同步失败'
      }
    }, 350))
  }

  function clearExerciseDrafts(planId: number, exerciseIds: number[]) {
    const drafts = readExerciseDrafts(planId)
    exerciseIds.forEach((id) => {
      delete drafts[String(id)]
      const key = `${planId}:${id}`
      const timer = exerciseDraftTimers.get(key)
      if (timer !== undefined) window.clearTimeout(timer)
      exerciseDraftTimers.delete(key)
    })
    if (Object.keys(drafts).length) sessionStorage.setItem(exerciseDraftStorageKey(planId), JSON.stringify(drafts))
    else sessionStorage.removeItem(exerciseDraftStorageKey(planId))
    void learningRepository.removeExerciseDrafts(planId, exerciseIds).catch((error) => {
      errorMessage.value = error instanceof Error ? `清理练习草稿失败：${error.message}` : '清理练习草稿失败'
    })
  }

  async function hydrateExerciseDrafts(plan: LearningPlan) {
    const persisted = await learningRepository.listExerciseDrafts(plan.id)
    if (persisted.length) {
      const cache = persisted.reduce<Record<string, ApiExerciseDraft>>((result, draft: ExerciseDraftDto) => {
        result[String(draft.exerciseId)] = {
          answer: draft.answer,
          language: draft.language as CodeLanguageKey | undefined,
        }
        return result
      }, {})
      sessionStorage.setItem(exerciseDraftStorageKey(plan.id), JSON.stringify(cache))
    } else if (![...exerciseDraftTimers.keys()].some((key) => key.startsWith(`${plan.id}:`))) {
      sessionStorage.removeItem(exerciseDraftStorageKey(plan.id))
    }
    return restoreExerciseDrafts(plan)
  }

  function discardExerciseDraftCache(planId: number) {
    const keys = [...exerciseDraftTimers.keys()].filter((key) => key.startsWith(`${planId}:`))
    keys.forEach((key) => {
      const timer = exerciseDraftTimers.get(key)
      if (timer !== undefined) window.clearTimeout(timer)
      exerciseDraftTimers.delete(key)
    })
    sessionStorage.removeItem(exerciseDraftStorageKey(planId))
  }

  function restoreExerciseDrafts(plan: LearningPlan) {
    const drafts = readExerciseDrafts(plan.id)
    plan.exercises.forEach((exercise) => {
      if (exercise.submitted) return
      const draft = drafts[String(exercise.id)]
      if (!draft) return
      exercise.draftAnswer = draft.answer
      if (exercise.type === '代码题' && draft.language) {
        exercise.selectedLanguage = draft.language
        exercise.codeDrafts ??= {}
        exercise.codeDrafts[draft.language] = draft.answer
      }
    })
    return plan
  }

  const projectCount = computed(() => plans.value.length)

  function persist() {
    learningRepository.persistMockSnapshot(plans.value)
  }

  function getPlan(id: number) {
    return plans.value.find((plan) => plan.id === id)
  }

  function replacePlanFromServer(plan: LearningPlan) {
    restoreExerciseDrafts(plan)
    const index = plans.value.findIndex((item) => item.id === plan.id)
    if (index >= 0) plans.value.splice(index, 1, plan)
    else plans.value.unshift(plan)
  }

  function activityKey(projectId: number, taskId: number) {
    return `${projectId}:${taskId}`
  }

  async function sendLearningActivity(
    input: Omit<Parameters<typeof learningRepository.recordActivity>[0], 'clientRequestId'>,
    applyServerPlan = true,
    rollbackPlan?: LearningPlan,
  ) {
    if (!isApiDataSource) return
    try {
      const updated = await learningRepository.recordActivity({
        ...input,
        clientRequestId: crypto.randomUUID(),
      })
      if (applyServerPlan) replacePlanFromServer(updated)
    } catch (error) {
      if (rollbackPlan) replacePlanFromServer(rollbackPlan)
      errorMessage.value = error instanceof Error ? `学习进度同步失败：${error.message}` : '学习进度同步失败'
    }
  }

  function syncLearningActivity(
    input: Omit<Parameters<typeof learningRepository.recordActivity>[0], 'clientRequestId'>,
    rollbackPlan?: LearningPlan,
  ) {
    void sendLearningActivity(input, true, rollbackPlan)
  }

  async function flushLearningActivities(projectId?: number, taskId?: number) {
    const keys = [...pendingReadingActivities.keys()].filter((key) => {
      if (projectId === undefined) return true
      if (!key.startsWith(`${projectId}:`)) return false
      return taskId === undefined || key === activityKey(projectId, taskId)
    })
    await Promise.all(keys.map(async (key) => {
      const pending = pendingReadingActivities.get(key)
      pendingReadingActivities.delete(key)
      const timer = readingFlushTimers.get(key)
      if (timer !== undefined) window.clearTimeout(timer)
      readingFlushTimers.delete(key)
      if (pending) await sendLearningActivity(pending, false)
    }))
  }

  function queueReadingActivity(projectId: number, taskId: number, progress: number, secondsDelta: number) {
    if (!isApiDataSource) return
    const key = activityKey(projectId, taskId)
    const existing = pendingReadingActivities.get(key)
    pendingReadingActivities.set(key, {
      projectId,
      taskId,
      eventType: 'reading',
      progress: Math.max(existing?.progress ?? 0, progress),
      secondsDelta: (existing?.secondsDelta ?? 0) + secondsDelta,
    })
    if (readingFlushTimers.has(key)) return
    readingFlushTimers.set(key, window.setTimeout(() => {
      void flushLearningActivities(projectId, taskId)
    }, READING_FLUSH_INTERVAL_MS))
  }

  async function fetchPlans() {
    if (isLoading.value) return plans.value
    isLoading.value = true
    errorMessage.value = null
    try {
      plans.value = (await learningRepository.listPlans()).map(restoreExerciseDrafts)
      return plans.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '获取学习项目失败'
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function fetchPlan(id: number) {
    isLoading.value = true
    errorMessage.value = null
    try {
      const plan = await hydrateExerciseDrafts(await learningRepository.getPlan(id))
      replacePlanFromServer(plan)
      return plan
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '获取学习项目失败'
      throw error
    } finally {
      isLoading.value = false
    }
  }

  function clearError() {
    errorMessage.value = null
  }

  function clearAll() {
    plans.value = []
    generatingResourceIds.value = []
    errorMessage.value = null
    isLoading.value = false
    pendingReadingActivities.clear()
    readingFlushTimers.forEach((timer) => window.clearTimeout(timer))
    readingFlushTimers.clear()
    cacheActivePlanGeneration(null)
    exerciseDraftTimers.forEach((timer) => window.clearTimeout(timer))
    exerciseDraftTimers.clear()
  }

  function getLearningSetupState(projectId: number) {
    return learningRepository.getSetupState(projectId)
  }

  function saveLearningSetupState(projectId: number, state: LearningSetupStateDto) {
    return learningRepository.saveSetupState(projectId, state)
  }

  function removeLearningSetupState(projectId: number) {
    return learningRepository.removeSetupState(projectId)
  }

  async function generateLearningProfile(input: LearningProfileRequest): Promise<LearningProfileResult> {
    const job = await waitForGenerationJob(await learningRepository.startProfileGeneration(input), '学习画像生成失败')
    return job.result
  }

  async function generateLearningConfirmation(input: LearningConfirmationRequest) {
    const result = await learningRepository.generateConfirmation(input)
    const artifactId = `learning-confirmation:${input.projectId ?? input.setupId}`
    const fileName = `${input.profile.subject || '智能学习'}-方案确认稿.md`
    if (isApiDataSource) {
      if (input.projectId && result.resourceId) {
        await attachGeneratedResourceToProject({
          projectId: Number(input.projectId),
          resourceId: result.resourceId,
          artifactId,
          title: `${input.profile.subject || '智能学习'}方案确认稿`,
          fileName,
          fileType: 'document',
          content: result.content,
          preview: { kind: 'document', text: result.content },
          source: 'learning-profile',
        })
      } else if (input.projectId) await fetchPlan(Number(input.projectId))
      await libraryResourceStore.fetchList()
      return result
    }
    const archived = libraryResourceStore.addGeneratedFile({
      resourceId: result.resourceId || input.confirmationResourceId || undefined,
      externalKey: artifactId,
      name: fileName,
      format: 'Markdown',
      fileType: 'document',
      origin: 'learning',
      projectId: input.projectId ?? null,
      knowledgeBaseId: input.knowledgeBaseId,
    })
    if (input.projectId) {
      await attachGeneratedResourceToProject({
        projectId: Number(input.projectId),
        resourceId: archived.resourceId,
        artifactId,
        title: `${input.profile.subject || '智能学习'}方案确认稿`,
        fileName,
        fileType: 'document',
        content: result.content,
        preview: { kind: 'document', text: result.content },
        source: 'learning-profile',
      })
    }
    return { content: result.content, resourceId: archived.resourceId }
  }

  async function renamePlan(id: number, title: string) {
    const plan = getPlan(id)
    if (!plan || !title.trim()) return false
    if (isApiDataSource) {
      replacePlanFromServer(await learningRepository.updatePlan(id, { title: title.trim() }))
      return true
    }
    plan.title = title.trim()
    plan.updatedAt = '刚刚'
    persist()
    return true
  }

  async function removePlan(id: number) {
    discardExerciseDraftCache(id)
    await learningRepository.removePlan(id)
    plans.value = plans.value.filter((plan) => plan.id !== id)
    libraryResourceStore.detachProject(id)
    persist()
  }

  function archiveMockPlanResources(plan: LearningPlan) {
    if (isApiDataSource) return
    plan.resources.filter((resource) => resource.status !== '未选择').forEach((resource) => {
      libraryResourceStore.addGeneratedResource(resource, plan.id, plan.id, plan.knowledgeBaseId)
    })
  }

  async function finishPlanGeneration(
    job: AsyncJob<{ projectId: number }>,
    draftPlanId: number | null,
    sourceResourceIds: string[] = [],
    knowledgeBaseId: number | null = null,
  ) {
    let completed: AsyncJob<{ projectId: number }> & { result: { projectId: number } }
    try {
      completed = await waitForGenerationJob(job, '学习方案生成失败')
    } catch (error) {
      try {
        const latest = await learningRepository.getGenerationJob<{ projectId: number }>(job.jobId)
        if (['failed', 'cancelled'].includes(latest.status)) await persistActivePlanGeneration(null)
      } catch {
        // Keep the job id for a later resume when the status endpoint is temporarily unavailable.
      }
      throw error
    }
    const plan = await learningRepository.getPlan(completed.result.projectId)
    const draftIndex = plans.value.findIndex((item) => item.id === draftPlanId)
    if (draftIndex >= 0) plans.value.splice(draftIndex, 1, plan)
    else replacePlanFromServer(plan)
    archiveMockPlanResources(plan)
    if (sourceResourceIds.length) {
      await Promise.all(sourceResourceIds.map((resourceId) =>
        libraryResourceStore.updateAssociations(resourceId, { projectId: plan.id, knowledgeBaseId }),
      ))
      await Promise.all(sourceResourceIds.map(async (resourceId) => {
        const source = libraryResourceStore.resources.find((resource) => resource.resourceId === resourceId)
        if (!source) return
        await attachGeneratedResourceToProject({
          projectId: plan.id,
          resourceId,
          artifactId: source.externalKey || `learning-source:${resourceId}`,
          title: source.name.replace(/\.[^.]+$/, ''),
          fileName: source.name,
          fileType: source.fileType,
          source: 'learning-profile',
        })
      }))
    }
    await persistActivePlanGeneration(null)
    persist()
    return plan
  }

  async function createPlan(input: CreateLearningPlanInput) {
    const job = await learningRepository.startPlanGeneration(input)
    await persistActivePlanGeneration({
      jobId: job.jobId,
      draftPlanId: input.draftPlanId ?? null,
      sourceResourceIds: input.sourceResourceIds ?? [],
      knowledgeBaseId: input.knowledgeBaseId,
      startedAt: Date.now(),
    })
    return finishPlanGeneration(
      job,
      input.draftPlanId ?? null,
      input.sourceResourceIds ?? [],
      input.knowledgeBaseId,
    )
  }

  async function resumePlanGeneration(draftPlanId?: number | null) {
    let active = activePlanGeneration.value
    if ((!active || (draftPlanId !== undefined && active.draftPlanId !== draftPlanId)) && draftPlanId) {
      active = await learningRepository.getActivePlanGeneration(Number(draftPlanId))
      if (active) cacheActivePlanGeneration(active)
    }
    if (!active || (draftPlanId !== undefined && active.draftPlanId !== draftPlanId)) return null
    const job = await learningRepository.getGenerationJob<{ projectId: number }>(active.jobId)
    return finishPlanGeneration(
      job,
      active.draftPlanId,
      active.sourceResourceIds ?? [],
      active.knowledgeBaseId ?? null,
    )
  }

  async function createDraftPlan(input: CreateLearningDraftInput) {
    const plan = await learningRepository.createDraft(input)
    const existingIndex = plans.value.findIndex((item) => item.id === plan.id)
    if (existingIndex >= 0) plans.value.splice(existingIndex, 1, plan)
    else plans.value.unshift(plan)
    persist()
    return plan
  }

  function setProfileValue(plan: LearningPlan, label: string, value: string) {
    const item = plan.profile.find((profile) => profile.label === label)
    if (item) item.value = value
    else plan.profile.push({ label, value })
  }

  function periodDayCount(period: string) {
    const match = period.match(/(\d+)\s*天/)
    return match ? Number(match[1]) : 0
  }

  function updateStageSchedule(plan: LearningPlan, period: string) {
    const totalDays = periodDayCount(period)
    plan.stages.forEach((stage, index) => {
      if (!totalDays) {
        stage.scheduleLabel = `建议第 ${index + 1} 个学习时段完成`
        return
      }
      const start = Math.max(1, Math.floor((index * totalDays) / plan.stages.length) + 1)
      const end = Math.max(start, Math.floor(((index + 1) * totalDays) / plan.stages.length))
      stage.scheduleLabel = start === end ? `建议第 ${start} 天完成` : `建议第 ${start}～${end} 天完成`
    })
  }

  async function updatePlanConfig(planId: number, input: UpdateLearningPlanInput) {
    const plan = getPlan(planId)
    if (!plan) return false

    if (isApiDataSource) {
      replacePlanFromServer(await learningRepository.updatePlan(planId, input))
      return true
    }

    plan.targetType = input.targetType
    plan.period = input.period
    setProfileValue(plan, '学习目标', input.targetType)
    setProfileValue(plan, '时间安排', `${input.period}，${input.dailyTime}`)
    setProfileValue(plan, '重点知识', input.weakPoints)
    setProfileValue(plan, '学习方式', input.preferences.join(' + ') || '待确认')

    if (!input.keepProgress) {
      plan.stages.forEach((stage) => {
        stage.tasks.forEach((task) => {
          task.done = false
          task.status = '未开始'
          task.completionSource = undefined
        })
      })
    }
    if (!input.keepExercises) {
        plan.exercises.forEach((exercise) => {
          exercise.userAnswer = undefined
          exercise.draftAnswer = undefined
          exercise.submitted = false
      })
      plan.exerciseDone = 0
      plan.correctRate = 0
    }

    updateStageSchedule(plan, input.period)
    updateProgress(plan)
    persist()
    return true
  }

  function updateProgress(plan: LearningPlan) {
    plan.taskDone = plan.stages.reduce(
      (total, stage) => total + stage.tasks.filter((task) => task.done).length,
      0,
    )
    plan.totalTasks = plan.stages.reduce((total, stage) => total + stage.tasks.length, 0)
    plan.progress = plan.totalTasks ? Math.round((plan.taskDone / plan.totalTasks) * 100) : 0
    const hasStarted = plan.stages.some((stage) => stage.tasks.some((task) =>
      task.done
      || task.status === '进行中'
      || task.status === '需复习'
      || (task.readProgress ?? 0) > 0
      || (task.completedActions?.length ?? 0) > 0,
    )) || plan.exercises.some((exercise) => exercise.submitted)
    if (!plan.totalTasks && ['待开启', '待完善'].includes(plan.status)) {
      // Setup drafts do not become active projects until generation succeeds.
    } else if (plan.progress === 100 && plan.totalTasks > 0) {
      plan.status = '已完成'
    } else {
      plan.status = hasStarted ? '进行中' : '已生成'
    }
    plan.updatedAt = '刚刚'
  }

  function getTask(plan: LearningPlan, taskId: number) {
    return plan.stages.flatMap((stage) => stage.tasks).find((item) => item.id === taskId)
  }

  function evaluateTaskCompletion(plan: LearningPlan, taskId: number) {
    const task = getTask(plan, taskId)
    if (!task || task.status === '已锁定' || task.done) return Boolean(task?.done)

    const exercises = (task.exerciseIds ?? [])
      .map((id) => plan.exercises.find((exercise) => exercise.id === id))
      .filter((exercise): exercise is Exercise => Boolean(exercise))
    const completed = task.completionMode === 'content' || task.completionMode === 'resource'
      ? (task.readProgress ?? 0) >= 80 && (task.validStudySeconds ?? 0) >= 5
      : task.completionMode === 'exercise' || task.completionMode === 'assessment'
        ? exercises.length > 0 && exercises.every((exercise) => exercise.submitted)
        : task.completionMode === 'case'
          ? task.completedActions?.includes('run-case')
          : false

    if (!completed) return false
    task.done = true
    task.status = '已完成'
    task.completionSource = task.completionMode === 'content' ? '已完成概念阅读'
      : task.completionMode === 'resource' ? '已完成关联资料阅读'
        : task.completionMode === 'exercise' ? '已提交任务要求的全部练习'
          : task.completionMode === 'assessment' ? '已提交阶段测验'
            : task.completionMode === 'case' ? '已运行并查看案例'
              : '已完成复盘确认'
    updateProgress(plan)
    return true
  }

  function startTask(planId: number, taskId: number) {
    const plan = getPlan(planId)
    const task = plan && getTask(plan, taskId)
    if (!plan || !task || task.done || task.status === '进行中' || task.status === '已锁定') return
    const rollbackPlan = isApiDataSource ? structuredClone(toRaw(plan)) : undefined
    task.status = '进行中'
    plan.status = '进行中'
    plan.updatedAt = '刚刚'
    persist()
    syncLearningActivity({ projectId: planId, taskId, eventType: 'start' }, rollbackPlan)
  }

  function recordTaskReading(planId: number, taskId: number, progress: number, secondsDelta = 0) {
    const plan = getPlan(planId)
    const task = plan && getTask(plan, taskId)
    if (!plan || !task || task.done || task.status === '已锁定') return false
    task.status = '进行中'
    plan.status = '进行中'
    task.readProgress = Math.max(task.readProgress ?? 0, Math.min(100, Math.round(progress)))
    task.validStudySeconds = (task.validStudySeconds ?? 0) + Math.max(0, secondsDelta)
    const completed = evaluateTaskCompletion(plan, taskId)
    persist()
    queueReadingActivity(planId, taskId, progress, secondsDelta)
    if (completed) void flushLearningActivities(planId, taskId)
    return completed
  }

  function completeTaskAction(planId: number, taskId: number, action: 'run-case') {
    const plan = getPlan(planId)
    const task = plan && getTask(plan, taskId)
    if (!plan || !task || task.done || task.status === '已锁定') return false
    const rollbackPlan = isApiDataSource ? structuredClone(toRaw(plan)) : undefined
    task.status = '进行中'
    plan.status = '进行中'
    task.completedActions = Array.from(new Set([...(task.completedActions ?? []), action]))
    const completed = evaluateTaskCompletion(plan, taskId)
    persist()
    syncLearningActivity({ projectId: planId, taskId, eventType: 'action', action }, rollbackPlan)
    return completed
  }

  function saveExerciseDraft(planId: number, exerciseId: number, answer: string, allowSubmitted = false) {
    const plan = getPlan(planId)
    const exercise = plan?.exercises.find((item) => item.id === exerciseId)
    if (!plan || !exercise || (exercise.submitted && !allowSubmitted)) return
    exercise.draftAnswer = answer
    if (exercise.type === '代码题' && exercise.selectedLanguage) {
      exercise.codeDrafts ??= {}
      exercise.codeDrafts[exercise.selectedLanguage] = answer
    }
    plan.updatedAt = '刚刚'
    persist()
    writeExerciseDraft(planId, exercise)
  }

  function selectExerciseLanguage(planId: number, exerciseId: number, language: CodeLanguageKey, allowSubmitted = false) {
    const plan = getPlan(planId)
    const exercise = plan?.exercises.find((item) => item.id === exerciseId)
    if (!plan || !exercise || exercise.type !== '代码题' || (exercise.submitted && !allowSubmitted)) return
    exercise.codeDrafts ??= {}
    if (exercise.selectedLanguage && exercise.draftAnswer) {
      exercise.codeDrafts[exercise.selectedLanguage] = exercise.draftAnswer
    }
    exercise.selectedLanguage = language
    exercise.draftAnswer = exercise.codeDrafts[language]
    plan.updatedAt = '刚刚'
    persist()
    writeExerciseDraft(planId, exercise)
    return exercise.draftAnswer ?? ''
  }

  async function submitExerciseGroup(planId: number, exerciseIds: number[], trainingSetId?: number): Promise<TrainingSetResult | undefined> {
    const plan = getPlan(planId)
    if (!plan) return
    const exercises = exerciseIds
      .map((id) => plan.exercises.find((exercise) => exercise.id === id))
      .filter((exercise): exercise is Exercise => Boolean(exercise))
    if (!exercises.length || exercises.some((exercise) => !exercise.draftAnswer)) return

    if (isApiDataSource) {
      const results = await learningRepository.submitAnswers({
        projectId: planId,
        answers: exercises.map((exercise) => ({
          exerciseId: exercise.id,
          answer: exercise.draftAnswer!,
          language: exercise.selectedLanguage,
        })),
        clientRequestId: crypto.randomUUID(),
      })
      clearExerciseDrafts(planId, exercises.map((exercise) => exercise.id))
      replacePlanFromServer(await learningRepository.getPlan(planId))
      const correctCount = results.filter((result) => result.correct).length
      return {
        total: results.length,
        correctCount,
        wrongCount: results.length - correctCount,
        correctRate: Math.round((correctCount / results.length) * 100),
        wrongExerciseIds: exercises.filter((_, index) => !results[index]?.correct).map((exercise) => exercise.id),
      }
    }

    for (const exercise of exercises) await submitExercise(planId, exercise.id, exercise.draftAnswer!)
    const correctCount = exercises.filter((exercise) => exercise.gradingCorrect).length
    const wrongExerciseIds = exercises.filter((exercise) => !exercise.gradingCorrect).map((exercise) => exercise.id)
    const set = plan.trainingSets?.find((item) => item.id === trainingSetId)
    if (set) set.status = '已交卷'
    persist()
    return {
      total: exercises.length,
      correctCount,
      wrongCount: exercises.length - correctCount,
      correctRate: Math.round((correctCount / exercises.length) * 100),
      wrongExerciseIds,
    }
  }

  async function createAdaptivePracticeTask(
    planId: number,
    sourceTaskId: number,
    input: { mode: 'repeat' | 'reinforce'; count: number; difficultyMode: '保持难度' | '逐步提升' },
  ) {
    const plan = getPlan(planId)
    const stage = plan?.stages.find((item) => item.tasks.some((task) => task.id === sourceTaskId))
    const sourceTask = stage?.tasks.find((task) => task.id === sourceTaskId)
    if (!plan || !stage || !sourceTask) return
    if (isApiDataSource) {
      await waitForGenerationJob(
        await learningRepository.startAdaptivePracticeGeneration(planId, sourceTaskId, input),
        '自适应练习生成失败',
      )
      const updated = await learningRepository.getPlan(planId)
      replacePlanFromServer(updated)
      const updatedStage = updated.stages.find((item) => item.tasks.some((task) => task.id === sourceTaskId))
      const sourceIndex = updatedStage?.tasks.findIndex((task) => task.id === sourceTaskId) ?? -1
      const task = sourceIndex >= 0 ? updatedStage?.tasks[sourceIndex + 1] : undefined
      if (!updatedStage || !task) return
      return { stage: updatedStage, task, generatedCount: task.exerciseIds?.length ?? 0 }
    }
    const sourceExercises = (sourceTask.exerciseIds ?? [])
      .map((id) => plan.exercises.find((item) => item.id === id))
      .filter((item): item is Exercise => Boolean(item))
    const wrongExercises = sourceExercises.filter((item) => item.submitted && !item.gradingCorrect)
    const count = Math.max(3, Math.min(input.mode === 'reinforce' ? 15 : 40, Math.round(input.count)))
    const nextExerciseId = Math.max(0, ...plan.exercises.map((item) => item.id)) + 1
    let taskExercises: Exercise[] = []
    let generated: Exercise[] = []

    if (input.mode === 'repeat') {
      const sourceKnowledge = new Set(sourceExercises.map((item) => item.knowledge))
      const reserve = plan.exercises.filter((item) => item.purpose === '备用题' && !item.submitted && (!sourceKnowledge.size || sourceKnowledge.has(item.knowledge)))
      taskExercises = reserve.slice(0, count)
      taskExercises.forEach((item) => {
        item.purpose = '追加练习'
        item.sourceTaskId = sourceTaskId
      })
      const deficit = count - taskExercises.length
      if (deficit > 0) {
        const topics = [...sourceKnowledge]
        const strategy: DifficultyStrategy = input.difficultyMode === '逐步提升' ? '强化提高' : plan.questionBank?.difficultyStrategy ?? '均衡'
        generated = createMockQuestionBatch(deficit, topics, strategy, nextExerciseId, `additional-${Date.now()}`, '追加练习')
        generated.forEach((item) => { item.sourceTaskId = sourceTaskId })
        taskExercises.push(...generated)
      }
    } else {
      if (!wrongExercises.length) return
      const upgrade = (difficulty: Exercise['difficulty']): Exercise['difficulty'] => {
        if (input.difficultyMode === '保持难度') return difficulty === '中等' ? '进阶' : difficulty === '提高' ? '挑战' : difficulty
        if (difficulty === '基础') return '进阶'
        return '挑战'
      }
      generated = Array.from({ length: count }, (_, index) => {
        const source = wrongExercises[index % wrongExercises.length]!
        const difficulty = upgrade(source.difficulty)
        return {
          ...source,
          options: [...source.options],
          id: nextExerciseId + index,
          title: `${source.knowledge}巩固变式 ${index + 1}：${source.title.replace(/^[^：]+[:：]/, '')}`,
          difficulty,
          cognitiveLevel: difficulty === '基础' ? '概念理解' : difficulty === '进阶' ? '直接应用' : '综合迁移',
          purpose: '错题巩固',
          generationBatch: `reinforcement-${Date.now()}`,
          sourceExerciseId: source.id,
          sourceTaskId,
          draftAnswer: undefined,
          userAnswer: undefined,
          submitted: false,
        }
      })
      taskExercises = generated
    }

    if (generated.length) plan.exercises.push(...generated)
    const nextTaskId = Math.max(0, ...plan.stages.flatMap((item) => item.tasks.map((task) => task.id))) + 1
    const task = {
      id: nextTaskId,
      title: input.mode === 'reinforce' ? `错题巩固 · ${taskExercises.length} 题` : `追加练习 · ${taskExercises.length} 题`,
      duration: `${Math.max(10, Math.ceil(taskExercises.length * 2.5))} 分钟`,
      done: false,
      type: '练习' as const,
      exerciseIds: taskExercises.map((item) => item.id),
      status: '未开始' as const,
      completionMode: 'exercise' as const,
    }
    const sourceIndex = stage.tasks.findIndex((item) => item.id === sourceTaskId)
    stage.tasks.splice(sourceIndex + 1, 0, task)
    plan.totalTasks = plan.stages.reduce((total, item) => total + item.tasks.length, 0)
    plan.totalExercises = plan.exercises.length
    if (plan.questionBank) {
      plan.questionBank.generatedCount = plan.exercises.length
      const counts = {
        basic: plan.exercises.filter((item) => item.difficulty === '基础').length,
        advanced: plan.exercises.filter((item) => ['中等', '进阶'].includes(item.difficulty)).length,
        challenge: plan.exercises.filter((item) => ['提高', '挑战'].includes(item.difficulty)).length,
      }
      plan.questionBank.difficultyCounts = counts
    }
    plan.updatedAt = '刚刚'
    persist()
    return { stage, task, generatedCount: generated.length }
  }

  async function submitExercise(planId: number, exerciseId: number, userAnswer: string): Promise<ExerciseResult | undefined> {
    const plan = getPlan(planId)
    const exercise = plan?.exercises.find((item) => item.id === exerciseId)
    if (!plan || !exercise || !userAnswer) return

    if (isApiDataSource) {
      const result = await learningRepository.submitAnswer({
        projectId: planId,
        exerciseId,
        answer: userAnswer,
        language: exercise.selectedLanguage,
        clientRequestId: crypto.randomUUID(),
      })
      clearExerciseDrafts(planId, [exerciseId])
      replacePlanFromServer(await learningRepository.getPlan(planId))
      return result
    }

    const wasSubmitted = Boolean(exercise.submitted)
    const result = evaluateExerciseAnswer(exercise, userAnswer)
    const correct = result.correct
    exercise.userAnswer = userAnswer
    exercise.submitted = true
    exercise.gradingCorrect = correct
    exercise.gradingScore = result.score
    exercise.gradingFeedback = result.feedback

    if (!wasSubmitted) {
      const previousCorrect = Math.round((plan.correctRate / 100) * plan.exerciseDone)
      plan.exerciseDone += 1
      plan.correctRate = Math.round(((previousCorrect + Number(correct)) / plan.exerciseDone) * 100)
    }

    if (!correct && exercise.scene !== 'checkpoint') {
      const existingWrong = plan.wrongQuestions.find((wrong) => wrong.id === exercise.id)
      if (existingWrong) {
        existingWrong.errorCount = (existingWrong.errorCount ?? 1) + 1
        existingWrong.lastWrongAt = '刚刚'
        existingWrong.correctStreak = 0
        existingWrong.status = '需巩固'
        existingWrong.answerLanguage = exercise.selectedLanguage
      } else {
        const referenceAnswer = exercise.type === '代码题'
          ? exercise.codeLanguages?.find((item) => item.key === exercise.selectedLanguage)?.referenceAnswer ?? exercise.answer
          : exercise.answer
        const wrong: WrongQuestion = {
          id: exercise.id,
          title: exercise.title,
          knowledge: [exercise.knowledge],
          userAnswer,
          correctAnswer: referenceAnswer,
          answerLanguage: exercise.selectedLanguage,
          reason: exercise.explanation,
          synced: false,
          status: '需巩固',
          errorCount: 1,
          reviewCount: 0,
          correctStreak: 0,
          lastWrongAt: '刚刚',
          reviewHistory: [],
        }
        plan.wrongQuestions.unshift(wrong)
      }
    }

    const mastery = plan.dashboard.find((item) => item.label === exercise.knowledge)
    if (mastery && !wasSubmitted) {
      const delta = exercise.scene === 'checkpoint' ? (correct ? 2 : -1) : (correct ? 8 : -4)
      mastery.value = Math.min(100, Math.max(0, mastery.value + delta))
    }
    plan.stages.flatMap((stage) => stage.tasks)
      .filter((task) => task.exerciseIds?.includes(exercise.id))
      .forEach((task) => evaluateTaskCompletion(plan, task.id))
    plan.updatedAt = '刚刚'
    persist()

    return result
  }

  async function reviewWrongQuestion(planId: number, wrongId: number, answer: string): Promise<ExerciseResult | undefined> {
    const plan = getPlan(planId)
    const wrong = plan?.wrongQuestions.find((item) => item.id === wrongId)
    const exercise = plan?.exercises.find((item) => item.id === wrongId)
    if (!plan || !wrong || !exercise || !answer) return
    if (isApiDataSource) {
      const result = await learningRepository.submitAnswer({
        projectId: planId,
        exerciseId: exercise.id,
        answer,
        language: exercise.selectedLanguage,
        clientRequestId: crypto.randomUUID(),
      })
      clearExerciseDrafts(planId, [exercise.id])
      replacePlanFromServer(await learningRepository.getPlan(planId))
      return result
    }
    const result = evaluateExerciseAnswer(exercise, answer)
    const correct = result.correct
    wrong.reviewCount = (wrong.reviewCount ?? 0) + 1
    wrong.correctStreak = correct ? (wrong.correctStreak ?? 0) + 1 : 0
    wrong.reviewHistory ??= []
    wrong.reviewHistory.unshift({ date: '刚刚', correct, answer })
    if (!correct) {
      wrong.errorCount = (wrong.errorCount ?? 1) + 1
      wrong.lastWrongAt = '刚刚'
      wrong.status = '需巩固'
    } else {
      wrong.status = (wrong.correctStreak ?? 0) >= 2 ? '已掌握' : '需巩固'
    }
    plan.updatedAt = '刚刚'
    persist()
    return result
  }

  async function createWrongReviewSet(
    planId: number,
    wrongIds: number[],
    input: { count: number; difficultyMode: '保持难度' | '逐步提升' },
  ) {
    const plan = getPlan(planId)
    if (isApiDataSource) {
      await waitForGenerationJob(
        await learningRepository.startWrongReviewGeneration(planId, wrongIds, input),
        '错题巩固生成失败',
      )
      const updated = await learningRepository.getPlan(planId)
      replacePlanFromServer(updated)
      return updated.wrongReviewSets?.[0]
    }
    const sources = wrongIds
      .map((id) => plan?.exercises.find((item) => item.id === id))
      .filter((item): item is Exercise => Boolean(item))
    if (!plan || !sources.length) return
    const count = Math.max(2, Math.min(15, Math.round(input.count)))
    const startId = Math.max(0, ...plan.exercises.map((item) => item.id)) + 1
    const generated = Array.from({ length: count }, (_, index): Exercise => {
      const source = sources[index % sources.length]!
      const difficulty: Exercise['difficulty'] = input.difficultyMode === '保持难度'
        ? source.difficulty
        : source.difficulty === '基础' ? '进阶' : '挑战'
      return {
        ...source,
        options: [...source.options],
        id: startId + index,
        title: `${source.knowledge}巩固变式 ${index + 1}：${source.title.replace(/^[^：]+[:：]/, '')}`,
        difficulty,
        cognitiveLevel: difficulty === '基础' ? '概念理解' : difficulty === '进阶' ? '直接应用' : '综合迁移',
        purpose: '错题巩固',
        generationBatch: `wrongbook-${Date.now()}`,
        sourceExerciseId: source.id,
        draftAnswer: undefined,
        userAnswer: undefined,
        submitted: false,
      }
    })
    plan.exercises.push(...generated)
    const sets = plan.wrongReviewSets ??= []
    const knowledge = [...new Set(sources.map((item) => item.knowledge))].join('、')
    const set = {
      id: Math.max(0, ...sets.map((item) => item.id)) + 1,
      title: `${knowledge}巩固 · ${generated.length} 题`,
      exerciseIds: generated.map((item) => item.id),
      sourceWrongIds: wrongIds,
      status: '待作答' as const,
      createdAt: '刚刚',
      difficultyMode: input.difficultyMode,
    }
    sets.unshift(set)
    plan.totalExercises = plan.exercises.length
    if (plan.questionBank) plan.questionBank.generatedCount = plan.exercises.length
    persist()
    return set
  }

  async function startWrongReviewSet(planId: number, setId: number) {
    const plan = getPlan(planId)
    const set = plan?.wrongReviewSets?.find((item) => item.id === setId)
    if (!plan || !set) return false
    const updated = await learningRepository.startWrongReviewSet(planId, setId, crypto.randomUUID())
    replacePlanFromServer(updated)
    return true
  }

  async function submitWrongReviewSet(planId: number, setId: number): Promise<TrainingSetResult | undefined> {
    const plan = getPlan(planId)
    const set = plan?.wrongReviewSets?.find((item) => item.id === setId)
    const exercises = set?.exerciseIds
      .map((id) => plan?.exercises.find((item) => item.id === id))
      .filter((item): item is Exercise => Boolean(item)) ?? []
    if (!plan || !set || !exercises.length || exercises.some((item) => !item.draftAnswer)) return
    if (isApiDataSource) {
      const results = await learningRepository.submitAnswers({
        projectId: planId,
        answers: exercises.map((item) => ({
          exerciseId: item.id,
          answer: item.draftAnswer!,
          language: item.selectedLanguage,
        })),
        clientRequestId: crypto.randomUUID(),
      })
      clearExerciseDrafts(planId, exercises.map((exercise) => exercise.id))
      replacePlanFromServer(await learningRepository.getPlan(planId))
      const correctCount = results.filter((item) => item.correct).length
      return {
        total: results.length,
        correctCount,
        wrongCount: results.length - correctCount,
        correctRate: Math.round((correctCount / results.length) * 100),
        wrongExerciseIds: exercises.filter((_, index) => !results[index]?.correct).map((item) => item.id),
      }
    }
    exercises.forEach((item) => {
      item.userAnswer = item.draftAnswer
      item.submitted = true
    })
    exercises.forEach((item) => {
      const result = evaluateExerciseAnswer(item, item.userAnswer ?? '')
      item.gradingCorrect = result.correct
      item.gradingScore = result.score
      item.gradingFeedback = result.feedback
    })
    const correctCount = exercises.filter((item) => item.gradingCorrect).length
    const wrongExerciseIds = exercises.filter((item) => !item.gradingCorrect).map((item) => item.id)
    const correctRate = Math.round((correctCount / exercises.length) * 100)
    set.status = '已完成'
    set.correctRate = correctRate
    set.sourceWrongIds.forEach((wrongId) => {
      const wrong = plan.wrongQuestions.find((item) => item.id === wrongId)
      const related = exercises.filter((item) => item.sourceExerciseId === wrongId)
      if (!wrong || !related.length) return
      const passed = related.every((item) => item.gradingCorrect)
      wrong.reviewCount = (wrong.reviewCount ?? 0) + 1
      wrong.correctStreak = passed ? (wrong.correctStreak ?? 0) + 1 : 0
      if (!passed) {
        wrong.errorCount = (wrong.errorCount ?? 1) + 1
        wrong.lastWrongAt = '刚刚'
      }
      wrong.status = passed && (wrong.correctStreak ?? 0) >= 2 ? '已掌握' : '需巩固'
      wrong.reviewHistory ??= []
      wrong.reviewHistory.unshift({ date: '刚刚', correct: passed, answer: `巩固题组 ${related.filter((item) => item.gradingCorrect).length}/${related.length}` })
    })
    persist()
    return { total: exercises.length, correctCount, wrongCount: exercises.length - correctCount, correctRate, wrongExerciseIds }
  }

  async function generateResource(planId: number, learningResourceId: number) {
    const plan = getPlan(planId)
    const resource = plan?.resources.find((item) => item.id === learningResourceId)
    if (!plan || !resource || generatingResourceIds.value.includes(learningResourceId)) return

    if (isApiDataSource) {
      generatingResourceIds.value.push(learningResourceId)
      resource.status = '生成中'
      resource.errorMessage = undefined
      try {
        await waitForGenerationJob(
          await learningRepository.startResourceGeneration(planId, learningResourceId),
          '学习资源生成失败',
        )
        replacePlanFromServer(await learningRepository.getPlan(planId))
      } catch (error) {
        resource.status = '生成失败'
        resource.errorMessage = error instanceof Error ? error.message : '学习资源生成失败'
        throw error
      } finally {
        generatingResourceIds.value = generatingResourceIds.value.filter((id) => id !== learningResourceId)
      }
      return
    }

    generatingResourceIds.value.push(learningResourceId)
    resource.status = '生成中'
    libraryResourceStore.addGeneratedResource(
      resource,
      plan.id,
      plan.id,
      plan.knowledgeBaseId,
    )
    persist()
    try {
      if (resource.group === '思维导图') {
        await new Promise((resolve) => window.setTimeout(resolve, 650))
        const result = createMockLearningMindMap(plan, resource)
        resource.mindMapId = result.id
        resource.mindMapTreeData = result.treeData
      } else {
        await new Promise((resolve) => window.setTimeout(resolve, 900))
        resource.content = createMockLearningResourceContent(plan, resource)
      }
      resource.status = '已生成'
      resource.action = '查看'
      libraryResourceStore.addGeneratedResource(
        resource,
        plan.id,
        plan.id,
        plan.knowledgeBaseId,
      )
      plan.updatedAt = '刚刚'
    } catch (error) {
      resource.status = '生成失败'
      resource.errorMessage = error instanceof Error ? error.message : '学习资源生成失败'
      libraryResourceStore.addGeneratedResource(resource, plan.id, plan.id, plan.knowledgeBaseId)
      throw error
    } finally {
      generatingResourceIds.value = generatingResourceIds.value.filter((id) => id !== learningResourceId)
      persist()
    }
  }

  function downloadResource(planId: number, learningResourceId: number) {
    return learningRepository.downloadResource(planId, learningResourceId)
  }

  async function attachPresentationResult(planId: number, learningResourceId: number, presentationId: string, fileName: string) {
    if (isApiDataSource) {
      return fetchPlan(planId)
    }
    const plan = getPlan(planId)
    const resource = plan?.resources.find((item) => item.id === learningResourceId)
    if (!plan || !resource || resource.group !== 'PPT') return
    resource.presentationId = presentationId
    resource.fileName = fileName
    resource.status = '已生成'
    resource.action = '查看'
    resource.errorMessage = undefined
    plan.updatedAt = '刚刚'
    const archived = libraryResourceStore.addPresentation(
      presentationId,
      fileName,
      plan.id,
      plan.knowledgeBaseId,
    )
    resource.resourceId = archived.resourceId
    persist()
    return plan
  }

  async function attachGeneratedResourceToProject(input: ProjectGeneratedResourceInput) {
    if (isApiDataSource) {
      const plan = await learningRepository.attachGeneratedResource(input.projectId, {
        learningResourceId: input.learningResourceId,
        resourceId: input.resourceId,
        artifactId: input.artifactId,
        title: input.title,
        fileName: input.fileName,
        fileType: input.fileType,
        preview: input.preview,
        content: input.content,
        source: input.source,
        clientRequestId: generatedResourceRequestId(input.projectId, input.artifactId, input.resourceId),
      })
      replacePlanFromServer(plan)
      return plan
    }
    let plan = getPlan(input.projectId)
    if (!plan) {
      try {
        plan = await fetchPlan(input.projectId)
      } catch {
        return undefined
      }
    }
    const explicitId = Number(input.learningResourceId)
    let resource = Number.isFinite(explicitId) && explicitId > 0
      ? plan.resources.find((item) => item.id === explicitId)
      : undefined
    resource ??= plan.resources.find((item) => (
      item.resourceId === input.resourceId
      || item.artifactId === input.artifactId
    ))
    if (!resource) {
      resource = {
        id: Math.max(0, ...plan.resources.map((item) => item.id)) + 1,
        group: projectResourceGroup(input.fileType),
        title: input.title,
        desc: input.source === 'learning-profile' ? '通过学习画像流程生成的项目资源。' : '由当前项目 AI 对话生成的资源。',
        status: '已生成',
        action: '查看',
      }
      plan.resources.push(resource)
    }
    resource.group = projectResourceGroup(input.fileType)
    resource.title = input.title
    resource.fileName = input.fileName
    resource.resourceId = input.resourceId
    resource.artifactId = input.artifactId
    resource.source = input.source
    resource.updatedAt = '刚刚'
    resource.status = '已生成'
    resource.action = '查看'
    resource.errorMessage = undefined
    if (input.content !== undefined) resource.content = input.content
    if (input.preview?.text !== undefined) resource.content = input.preview.text
    if (input.preview?.imageUrl) resource.previewUrl = input.preview.imageUrl
    if (input.preview?.mindMap) {
      resource.mindMapTreeData = input.preview.mindMap
      resource.mindMapRenderConfig = input.preview.mindMapConfig
      const id = Number(artifactExternalId(input.artifactId, 'mindmap:'))
      if (Number.isFinite(id) && id > 0) resource.mindMapId = id
    }
    const presentationId = artifactExternalId(input.artifactId, 'presentation:')
    if (presentationId) resource.presentationId = presentationId
    plan.updatedAt = '刚刚'
    persist()
    return plan
  }

  async function syncGeneratedArtifact(artifact: ChatArtifactDto) {
    const projectId = Number(artifact.projectId)
    if (!Number.isFinite(projectId) || projectId <= 0 || artifact.status !== 'ready' || !artifact.resourceId) return
    return attachGeneratedResourceToProject({
      projectId,
      learningResourceId: artifact.learningResourceId == null ? null : Number(artifact.learningResourceId),
      resourceId: artifact.resourceId,
      artifactId: artifact.artifactId,
      title: artifact.title,
      fileName: artifact.fileName,
      fileType: artifact.fileType,
      preview: artifact.preview,
      source: 'ai-conversation',
    })
  }

  return {
    plans,
    activePlanGeneration,
    isLoading,
    errorMessage,
    projectCount,
    fetchPlans,
    fetchPlan,
    clearError,
    clearAll,
    generateLearningProfile,
    generateLearningConfirmation,
    getLearningSetupState,
    saveLearningSetupState,
    removeLearningSetupState,
    getPlan,
    renamePlan,
    removePlan,
    createPlan,
    resumePlanGeneration,
    createDraftPlan,
    updatePlanConfig,
    startTask,
    recordTaskReading,
    completeTaskAction,
    saveExerciseDraft,
    selectExerciseLanguage,
    submitExerciseGroup,
    createAdaptivePracticeTask,
    submitExercise,
    reviewWrongQuestion,
    createWrongReviewSet,
    startWrongReviewSet,
    submitWrongReviewSet,
    generateResource,
    downloadResource,
    attachPresentationResult,
    attachGeneratedResourceToProject,
    syncGeneratedArtifact,
    flushLearningActivities,
  }
})
