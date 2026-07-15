import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { generateMindMapFromAi } from '@/api/mindmap'
import type { CodeLanguageKey, Exercise, LearningPlan, LearningResource, TrainingSet, WrongQuestion } from '@/mock'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { learningRepository } from '@/repositories/learning'
import {
  createMockQuestionBatch,
  evaluateMockExerciseAnswer,
} from '@/mock/generators/learning'
import type {
  LearningConfirmationRequest,
  LearningProfileRequest,
  LearningProfileResult,
} from '@/types/contracts/learning'
import { isApiDataSource } from '@/config/dataSource'

export type CreateLearningPlanInput = {
  prompt: string
  libraryId: number
  projectId: number | null
  targetType: string
  preferences: string[]
  resourceGroups: LearningResource['group'][]
  period: string
  foundation: string
  weakPoints: string
  dailyTime: string
  studyDepth: string
  questionCount: number
  supplementalRequirement: string
  draftPlanId?: number | null
  libraryName?: string
}

export type CreateLearningDraftInput = {
  title: string
  libraryId: number | null
  libraryName?: string
  icon?: string
  iconColor?: string
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

function buildMindMapSourceContent(plan: LearningPlan, resource: LearningResource) {
  const profile = plan.profile.map((item) => `${item.label}: ${item.value}`).join('\n')
  const stages = plan.stages
    .map((stage) => {
      const tasks = stage.tasks.map((task) => `- ${task.type}: ${task.title}`).join('\n')
      return `## ${stage.title}\n${stage.desc}\n${tasks}`
    })
    .join('\n\n')
  const knowledge = plan.dashboard.map((item) => item.label).join('、')

  return [
    `学习项目: ${plan.title}`,
    `目标: ${plan.goal}`,
    `资源: ${resource.title}`,
    `重点知识: ${knowledge || '未设置'}`,
    '',
    '学习画像:',
    profile,
    '',
    '阶段安排:',
    stages,
  ].join('\n')
}

export const useLearningStore = defineStore('learning', () => {
  const plans = ref<LearningPlan[]>(learningRepository.initialPlans())
  const generatingResourceIds = ref<number[]>([])
  const isLoading = ref(false)
  const errorMessage = ref<string | null>(null)
  const libraryResourceStore = useLibraryResourceStore()

  const projectCount = computed(() => plans.value.length)

  function persist() {
    learningRepository.persistMockSnapshot(plans.value)
  }

  function getPlan(id: number) {
    return plans.value.find((plan) => plan.id === id)
  }

  function replacePlanFromServer(plan: LearningPlan) {
    const index = plans.value.findIndex((item) => item.id === plan.id)
    if (index >= 0) plans.value.splice(index, 1, plan)
    else plans.value.unshift(plan)
  }

  function syncLearningActivity(input: Omit<Parameters<typeof learningRepository.recordActivity>[0], 'clientRequestId'>) {
    if (!isApiDataSource) return
    void learningRepository.recordActivity({
      ...input,
      clientRequestId: crypto.randomUUID(),
    }).then(replacePlanFromServer)
  }

  async function fetchPlans() {
    if (isLoading.value) return plans.value
    isLoading.value = true
    errorMessage.value = null
    try {
      plans.value = await learningRepository.listPlans()
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
      const plan = await learningRepository.getPlan(id)
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

  async function generateLearningProfile(input: LearningProfileRequest): Promise<LearningProfileResult> {
    let job = await learningRepository.startProfileGeneration(input)
    for (let attempt = 0; ['pending', 'running'].includes(job.status) && attempt < 120; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000))
      job = await learningRepository.getGenerationJob<LearningProfileResult>(job.jobId)
    }
    if (job.status !== 'succeeded' || !job.result) {
      throw new Error(job.errorMessage || '学习画像生成失败')
    }
    return job.result
  }

  function generateLearningConfirmation(input: LearningConfirmationRequest) {
    return learningRepository.generateConfirmation(input)
  }

  function renamePlan(id: number, title: string) {
    const plan = getPlan(id)
    if (!plan || !title.trim()) return false
    plan.title = title.trim()
    plan.updatedAt = '刚刚'
    persist()
    return true
  }

  async function createPlan(input: CreateLearningPlanInput) {
    let job = await learningRepository.startPlanGeneration(input)
    for (let attempt = 0; ['pending', 'running'].includes(job.status) && attempt < 120; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000))
      job = await learningRepository.getGenerationJob<{ projectId: number }>(job.jobId)
    }
    if (job.status !== 'succeeded' || !job.result) {
      throw new Error(job.errorMessage || '学习方案生成失败')
    }
    const plan = await learningRepository.getPlan(job.result.projectId)
    const draftIndex = plans.value.findIndex((item) => item.id === input.draftPlanId)
    if (draftIndex >= 0) plans.value.splice(draftIndex, 1, plan)
    else plans.value.unshift(plan)
    if (!isApiDataSource) {
      plan.resources.forEach((resource) => {
        libraryResourceStore.addGeneratedResource(
          resource,
          '智能学习生成',
          plan.id,
          input.projectId,
          plan.libraryId,
        )
      })
    }
    persist()
    return plan
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

  function updatePlanConfig(planId: number, input: UpdateLearningPlanInput) {
    const plan = getPlan(planId)
    if (!plan) return false

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
    plan.status = plan.progress === 100 ? '已完成' : '进行中'
    plan.updatedAt = '刚刚'
  }

  function markTaskDone(planId: number, taskId: number, done: boolean) {
    const plan = getPlan(planId)
    const task = plan?.stages.flatMap((stage) => stage.tasks).find((item) => item.id === taskId)
    if (!plan || !task) return

    task.done = done
    task.status = done ? '已完成' : '进行中'
    task.completionSource = done ? '手动标记完成' : undefined
    updateProgress(plan)
    persist()
    syncLearningActivity({ projectId: planId, taskId, eventType: 'complete', action: done ? 'complete' : 'reopen' })
  }

  function getTask(plan: LearningPlan, taskId: number) {
    return plan.stages.flatMap((stage) => stage.tasks).find((item) => item.id === taskId)
  }

  function evaluateTaskCompletion(plan: LearningPlan, taskId: number) {
    const task = getTask(plan, taskId)
    if (!task || task.done) return Boolean(task?.done)

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
    if (!plan || !task || task.done || task.status === '进行中') return
    task.status = '进行中'
    plan.updatedAt = '刚刚'
    persist()
    syncLearningActivity({ projectId: planId, taskId, eventType: 'start' })
  }

  function recordTaskReading(planId: number, taskId: number, progress: number, secondsDelta = 0) {
    const plan = getPlan(planId)
    const task = plan && getTask(plan, taskId)
    if (!plan || !task || task.done) return false
    task.status = '进行中'
    task.readProgress = Math.max(task.readProgress ?? 0, Math.min(100, Math.round(progress)))
    task.validStudySeconds = (task.validStudySeconds ?? 0) + Math.max(0, secondsDelta)
    const completed = evaluateTaskCompletion(plan, taskId)
    persist()
    syncLearningActivity({ projectId: planId, taskId, eventType: 'reading', progress, secondsDelta })
    return completed
  }

  function completeTaskAction(planId: number, taskId: number, action: 'run-case') {
    const plan = getPlan(planId)
    const task = plan && getTask(plan, taskId)
    if (!plan || !task || task.done) return false
    task.status = '进行中'
    task.completedActions = Array.from(new Set([...(task.completedActions ?? []), action]))
    const completed = evaluateTaskCompletion(plan, taskId)
    persist()
    syncLearningActivity({ projectId: planId, taskId, eventType: 'action', action })
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
    return exercise.draftAnswer ?? ''
  }

  function createTrainingSet(
    planId: number,
    input: { knowledge: string; difficulty: string; questionType: string; count: number },
  ): TrainingSet | undefined {
    const plan = getPlan(planId)
    if (!plan) return
    const candidates = plan.exercises.filter((exercise) =>
      (!exercise.scene || exercise.scene === 'practice') &&
      (input.knowledge === '全部知识点' || exercise.knowledge === input.knowledge) &&
      (input.difficulty === '全部难度' || exercise.difficulty === input.difficulty) &&
      (input.questionType === '全部题型' || exercise.type === input.questionType),
    )
    const selected = candidates.slice(0, input.count)
    if (!selected.length) return
    selected.forEach((exercise) => {
      exercise.draftAnswer = undefined
      exercise.userAnswer = undefined
      exercise.submitted = false
    })
    const trainingSets = plan.trainingSets ??= []
    const set: TrainingSet = {
      id: Math.max(0, ...trainingSets.map((item) => item.id)) + 1,
      title: input.knowledge === '全部知识点' ? `${plan.title}专项训练` : `${input.knowledge}专项训练`,
      exerciseIds: selected.map((exercise) => exercise.id),
      status: '答题中',
      source: '专项训练',
      knowledge: input.knowledge,
      difficulty: input.difficulty,
      questionType: input.questionType,
      createdAt: '刚刚',
    }
    trainingSets.unshift(set)
    persist()
    return set
  }

  function startTrainingSet(planId: number, trainingSetId: number) {
    const plan = getPlan(planId)
    const set = plan?.trainingSets?.find((item) => item.id === trainingSetId)
    if (!plan || !set) return false
    set.status = '答题中'
    set.exerciseIds.forEach((id) => {
      const exercise = plan.exercises.find((item) => item.id === id)
      if (!exercise) return
      exercise.draftAnswer = undefined
      exercise.userAnswer = undefined
      exercise.submitted = false
    })
    persist()
    return true
  }

  async function submitExerciseGroup(planId: number, exerciseIds: number[], trainingSetId?: number): Promise<TrainingSetResult | undefined> {
    const plan = getPlan(planId)
    if (!plan) return
    const exercises = exerciseIds
      .map((id) => plan.exercises.find((exercise) => exercise.id === id))
      .filter((exercise): exercise is Exercise => Boolean(exercise))
    if (!exercises.length || exercises.some((exercise) => !exercise.draftAnswer)) return

    if (isApiDataSource) {
      const results = await Promise.all(exercises.map((exercise) => learningRepository.submitAnswer({
        projectId: planId,
        exerciseId: exercise.id,
        answer: exercise.draftAnswer!,
        language: exercise.selectedLanguage,
        clientRequestId: crypto.randomUUID(),
      })))
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

  function generateReinforcementSet(planId: number, sourceExerciseIds: number[]): TrainingSet | undefined {
    const plan = getPlan(planId)
    if (!plan || !sourceExerciseIds.length) return
    let nextExerciseId = Math.max(0, ...plan.exercises.map((item) => item.id)) + 1
    const generated = sourceExerciseIds.flatMap((id) => {
      const source = plan.exercises.find((exercise) => exercise.id === id)
      if (!source) return []
      return [1, 2].map((sequence) => ({
        ...source,
        options: [...source.options],
        id: nextExerciseId++,
        title: `巩固题 ${sequence}：${source.title}`,
        scene: 'practice' as const,
        sourceExerciseId: source.id,
        draftAnswer: undefined,
        userAnswer: undefined,
        submitted: false,
        gradingCorrect: undefined,
        gradingScore: undefined,
        gradingFeedback: undefined,
        codeDrafts: source.type === '代码题' ? {} : source.codeDrafts,
      }))
    })
    if (!generated.length) return
    plan.exercises.push(...generated)
    plan.totalExercises = plan.exercises.filter((exercise) => !exercise.scene || exercise.scene === 'practice').length
    const trainingSets = plan.trainingSets ??= []
    const set: TrainingSet = {
      id: Math.max(0, ...trainingSets.map((item) => item.id)) + 1,
      title: `错题巩固练习 · ${generated.length} 题`,
      exerciseIds: generated.map((exercise) => exercise.id),
      status: '待练习',
      source: '错题巩固',
      knowledge: '错题关联知识点',
      difficulty: '自适应',
      questionType: '混合题型',
      createdAt: '刚刚',
    }
    trainingSets.unshift(set)
    persist()
    return set
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
      let job = await learningRepository.startAdaptivePracticeGeneration(planId, sourceTaskId, input)
      for (let attempt = 0; ['pending', 'running'].includes(job.status) && attempt < 120; attempt += 1) {
        await new Promise((resolve) => window.setTimeout(resolve, 1000))
        job = await learningRepository.getGenerationJob<{ projectId: number }>(job.jobId)
      }
      if (job.status !== 'succeeded') throw new Error(job.errorMessage || '自适应练习生成失败')
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
      let job = await learningRepository.startWrongReviewGeneration(planId, wrongIds, input)
      for (let attempt = 0; ['pending', 'running'].includes(job.status) && attempt < 120; attempt += 1) {
        await new Promise((resolve) => window.setTimeout(resolve, 1000))
        job = await learningRepository.getGenerationJob<{ projectId: number }>(job.jobId)
      }
      if (job.status !== 'succeeded') throw new Error(job.errorMessage || '错题巩固生成失败')
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

  function startWrongReviewSet(planId: number, setId: number) {
    const plan = getPlan(planId)
    const set = plan?.wrongReviewSets?.find((item) => item.id === setId)
    if (!plan || !set) return false
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
    persist()
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
      const results = await Promise.all(exercises.map((item) => learningRepository.submitAnswer({
        projectId: planId,
        exerciseId: item.id,
        answer: item.draftAnswer!,
        language: item.selectedLanguage,
        clientRequestId: crypto.randomUUID(),
      })))
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

  function generateSimilarExercise(planId: number, exerciseId: number): Exercise | undefined {
    const plan = getPlan(planId)
    const source = plan?.exercises.find((item) => item.id === exerciseId)
    if (!plan || !source) return

    const exercise = structuredClone(source)
    exercise.id = Math.max(0, ...plan.exercises.map((item) => item.id)) + 1
    exercise.title = `同类题：${source.title}`
    exercise.userAnswer = undefined
    exercise.draftAnswer = undefined
    exercise.submitted = false
    exercise.gradingCorrect = undefined
    exercise.gradingScore = undefined
    exercise.gradingFeedback = undefined
    if (exercise.type === '代码题') exercise.codeDrafts = {}
    plan.exercises.push(exercise)
    plan.totalExercises = plan.exercises.length
    plan.updatedAt = '刚刚'
    persist()
    return exercise
  }

  async function generateResource(planId: number, resourceId: number) {
    const plan = getPlan(planId)
    const resource = plan?.resources.find((item) => item.id === resourceId)
    if (!plan || !resource || generatingResourceIds.value.includes(resourceId)) return

    if (isApiDataSource) {
      generatingResourceIds.value.push(resourceId)
      try {
        let job = await learningRepository.startResourceGeneration(planId, resourceId)
        for (let attempt = 0; ['pending', 'running'].includes(job.status) && attempt < 120; attempt += 1) {
          await new Promise((resolve) => window.setTimeout(resolve, 1000))
          job = await learningRepository.getGenerationJob<{ projectId: number }>(job.jobId)
        }
        if (job.status !== 'succeeded') throw new Error(job.errorMessage || '学习资源生成失败')
        replacePlanFromServer(await learningRepository.getPlan(planId))
      } finally {
        generatingResourceIds.value = generatingResourceIds.value.filter((id) => id !== resourceId)
      }
      return
    }

    generatingResourceIds.value.push(resourceId)
    resource.status = '生成中'
    libraryResourceStore.addGeneratedResource(
      resource,
      '智能学习生成',
      plan.id,
      plan.relatedProjectId ?? null,
      plan.libraryId,
    )
    persist()
    try {
      if (resource.group === '思维导图') {
        const result = await generateMindMapFromAi(buildMindMapSourceContent(plan, resource), resource.title)
        resource.mindMapId = result.id
        resource.mindMapTreeData = result.treeData
      } else {
        await new Promise((resolve) => window.setTimeout(resolve, 900))
      }
      resource.status = '已生成'
      resource.action = '查看'
      libraryResourceStore.addGeneratedResource(
        resource,
        '智能学习生成',
        plan.id,
        plan.relatedProjectId ?? null,
        plan.libraryId,
      )
      plan.updatedAt = '刚刚'
    } finally {
      generatingResourceIds.value = generatingResourceIds.value.filter((id) => id !== resourceId)
      persist()
    }
  }

  function downloadResource(planId: number, resourceId: number) {
    return learningRepository.downloadResource(planId, resourceId)
  }

  return {
    plans,
    isLoading,
    errorMessage,
    projectCount,
    fetchPlans,
    fetchPlan,
    clearError,
    generateLearningProfile,
    generateLearningConfirmation,
    getPlan,
    renamePlan,
    createPlan,
    createDraftPlan,
    updatePlanConfig,
    markTaskDone,
    startTask,
    recordTaskReading,
    completeTaskAction,
    saveExerciseDraft,
    selectExerciseLanguage,
    createTrainingSet,
    startTrainingSet,
    submitExerciseGroup,
    generateReinforcementSet,
    createAdaptivePracticeTask,
    submitExercise,
    reviewWrongQuestion,
    createWrongReviewSet,
    startWrongReviewSet,
    submitWrongReviewSet,
    generateSimilarExercise,
    generateResource,
    downloadResource,
  }
})
