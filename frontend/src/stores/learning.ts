import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getLearningPlans, saveLearningPlans } from '@/api/learning'
import { generateMindMapFromAi } from '@/api/mindmap'
import { courseLibraries, createCodeLanguageOptions, learningPlans as mockPlans } from '@/mock'
import type { CodeLanguageKey, Exercise, LearningPlan, LearningResource, TrainingSet, WrongQuestion } from '@/mock'
import { useLibraryResourceStore } from '@/stores/libraryResource'

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
}

export type CreateLearningDraftInput = {
  title: string
  libraryId: number | null
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

function normalizeAnswer(value: string) {
  return value.trim().toLowerCase().replace(/[\s，。；、,.;]+/g, '')
}

export function evaluateExerciseAnswer(exercise: Exercise, userAnswer: string): ExerciseResult {
  let correct = false
  let score = 0
  let feedback = exercise.explanation

  if (exercise.type === '填空题') {
    const accepted = exercise.acceptedAnswers?.length ? exercise.acceptedAnswers : [exercise.answer]
    correct = accepted.some((answer) => normalizeAnswer(answer) === normalizeAnswer(userAnswer))
    score = correct ? 100 : 0
  } else if (exercise.type === '简答题') {
    const keywords = exercise.gradingKeywords ?? []
    const matched = keywords.filter((keyword) => normalizeAnswer(userAnswer).includes(normalizeAnswer(keyword)))
    score = keywords.length ? Math.round((matched.length / keywords.length) * 100) : 0
    correct = score >= (exercise.passingScore ?? 80)
    feedback = correct
      ? `已覆盖 ${matched.length}/${keywords.length} 个核心评分点。${exercise.explanation}`
      : `已覆盖 ${matched.length}/${keywords.length} 个核心评分点；建议补充：${keywords.filter((item) => !matched.includes(item)).join('、') || '题目要求中的关键论证'}。`
  } else if (exercise.type === '代码题') {
    const language = exercise.codeLanguages?.find((item) => item.key === exercise.selectedLanguage) ?? exercise.codeLanguages?.[0]
    const patterns = language?.requiredCodePatterns ?? exercise.requiredCodePatterns ?? []
    const matched = patterns.filter((pattern) => userAnswer.includes(pattern))
    score = patterns.length ? Math.round((matched.length / patterns.length) * 100) : 0
    correct = patterns.length > 0 && matched.length === patterns.length
    feedback = correct
      ? `${language?.runtime ?? exercise.runtime ?? '当前语言'}原型规则检查通过。正式环境还需由安全判题服务执行 ${exercise.sampleTests?.length ?? 0} 组公开用例和隐藏用例。`
      : `实现尚未通过原型规则检查，缺少关键逻辑：${patterns.filter((item) => !matched.includes(item)).join('、') || '待判题服务确认'}。`
  } else {
    correct = userAnswer === exercise.answer
    score = correct ? 100 : 0
  }

  const correctAnswer = exercise.type === '代码题'
    ? exercise.codeLanguages?.find((item) => item.key === exercise.selectedLanguage)?.referenceAnswer ?? exercise.answer
    : exercise.answer
  return { correct, score, feedback, explanation: exercise.explanation, correctAnswer }
}

function getDifficultyCounts(count: number, strategy: DifficultyStrategy) {
  const ratios = strategy === '基础为主' ? [0.5, 0.4] : strategy === '强化提高' ? [0.2, 0.5] : [0.3, 0.5]
  const basic = Math.round(count * ratios[0]!)
  const advanced = Math.round(count * ratios[1]!)
  return { basic, advanced, challenge: Math.max(0, count - basic - advanced) }
}

function inferDifficultyStrategy(foundation: string, studyDepth: string): DifficultyStrategy {
  if (foundation.includes('零基础') || foundation.includes('薄弱')) return '基础为主'
  if (foundation.includes('有一定') || studyDepth.includes('刷题') || studyDepth.includes('实操')) return '强化提高'
  return '均衡'
}

function createQuestionBatch(
  count: number,
  topics: string[],
  strategy: DifficultyStrategy,
  startId: number,
  batch: string,
  purposeOverride?: Exercise['purpose'],
): Exercise[] {
  const distribution = getDifficultyCounts(count, strategy)
  const checkpointEnd = Math.round(count * 0.2)
  const practiceEnd = checkpointEnd + Math.round(count * 0.5)
  const assessmentEnd = practiceEnd + Math.round(count * 0.2)
  return Array.from({ length: count }, (_, index) => {
    const topic = topics[index % Math.max(1, topics.length)] ?? '核心知识'
    const difficulty: Exercise['difficulty'] = index < distribution.basic ? '基础' : index < distribution.basic + distribution.advanced ? '进阶' : '挑战'
    const purpose = purposeOverride ?? (index < checkpointEnd ? '随堂检查' : index < practiceEnd ? '阶段练习' : index < assessmentEnd ? '阶段测验' : '备用题')
    const scene: Exercise['scene'] = purpose === '随堂检查' ? 'checkpoint' : purpose === '阶段测验' ? 'assessment' : 'practice'
    const cognitiveLevel: Exercise['cognitiveLevel'] = difficulty === '基础' ? '概念理解' : difficulty === '进阶' ? '直接应用' : '综合迁移'
    const variant = index + 1
    const objectiveTypes: Exercise['type'][] = ['单选题', '判断题', '填空题']
    const fullTypes: Exercise['type'][] = ['单选题', '多选题', '判断题', '填空题', '简答题', '代码题']
    const type = scene === 'checkpoint' ? objectiveTypes[index % objectiveTypes.length]! : fullTypes[index % fullTypes.length]!
    const options = type === '判断题'
      ? ['正确', '错误']
      : type === '单选题' || type === '多选题'
        ? ['A. 只记住题干结论', `B. 结合概念与场景分析${topic}`, `C. 核对${topic}的适用条件`, 'D. 忽略条件直接判断']
        : []
    const answer = type === '判断题' ? '正确'
      : type === '多选题' ? `B. 结合概念与场景分析${topic}||C. 核对${topic}的适用条件`
        : type === '填空题' ? topic
          : type === '简答题' ? `需要说明${topic}的核心概念、适用条件以及在具体场景中的推理过程。`
            : type === '代码题' ? `class Solution { boolean verify${variant}(Object value) { return value != null; } }`
              : `B. 结合概念与场景分析${topic}`
    const exercise: Exercise = {
      id: startId + index,
      title: type === '填空题' ? `${topic}·${cognitiveLevel}题 ${variant}：请填写本题对应的核心知识点。`
        : type === '简答题' ? `${topic}·${cognitiveLevel}题 ${variant}：请结合具体场景说明概念、条件和推理过程。`
          : type === '代码题' ? `${topic}·${cognitiveLevel}题 ${variant}：补全方法，使其能够验证输入并返回正确结果。`
            : `${topic}·${cognitiveLevel}题 ${variant}：以下哪项最符合当前学习目标？`,
      knowledge: topic,
      difficulty,
      type,
      options,
      answer,
      explanation: `本题考查${topic}的${cognitiveLevel}，需要同时核对概念、适用条件和实际场景。`,
      scene,
      cognitiveLevel,
      purpose,
      generationBatch: batch,
      submitted: false,
    }
    if (type === '填空题') exercise.acceptedAnswers = [topic, topic.replace(/\s+/g, '')]
    if (type === '简答题') {
      exercise.gradingKeywords = [topic, '适用条件', '场景']
      exercise.gradingRubric = [`说明${topic}核心概念`, '指出适用条件', '结合场景完成推理']
      exercise.passingScore = 80
    }
    if (type === '代码题') {
      exercise.codeLanguages = createCodeLanguageOptions('verify', variant)
      exercise.selectedLanguage = 'java'
      exercise.codeDrafts = {}
      exercise.sampleTests = [{ input: 'new Object()', expected: 'true' }, { input: 'null', expected: 'false' }]
    }
    return exercise
  })
}

function assignQuestionBankToTasks(plan: LearningPlan) {
  const groups = {
    checkpoint: plan.exercises.filter((item) => item.purpose === '随堂检查'),
    practice: plan.exercises.filter((item) => item.purpose === '阶段练习'),
    assessment: plan.exercises.filter((item) => item.purpose === '阶段测验'),
  }
  const tasks = plan.stages.flatMap((stage) => stage.tasks)
  const assign = (targets: typeof tasks, exercises: Exercise[]) => {
    targets.forEach((task, taskIndex) => {
      task.exerciseIds = exercises.filter((_, index) => index % Math.max(targets.length, 1) === taskIndex).map((item) => item.id)
      if (task.exerciseIds.length && (task.type === '练习' || task.type === '测验')) {
        task.title = task.title.match(/\d+\s*题/) ? task.title.replace(/\d+\s*题/, `${task.exerciseIds.length} 题`) : `${task.title} · ${task.exerciseIds.length} 题`
      }
    })
  }
  assign(tasks.filter((task) => task.type === '讲解'), groups.checkpoint)
  assign(tasks.filter((task) => task.type === '练习'), groups.practice)
  assign(tasks.filter((task) => task.type === '测验'), groups.assessment)
}

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
  const plans = ref<LearningPlan[]>(getLearningPlans())
  const generatingResourceIds = ref<number[]>([])
  const libraryResourceStore = useLibraryResourceStore()

  const projectCount = computed(() => plans.value.length)

  function persist() {
    saveLearningPlans(plans.value)
  }

  function getPlan(id: number) {
    return plans.value.find((plan) => plan.id === id)
  }

  function createPlan(input: CreateLearningPlanInput) {
    const template = structuredClone(mockPlans[0]!)
    const library = courseLibraries.find((item) => item.id === input.libraryId) ?? courseLibraries[0]!
    const id = Math.max(0, ...plans.value.map((plan) => plan.id)) + 1
    const focus = library.tags.slice(0, 3).join('、') || library.course
    const topics = library.tags.length ? library.tags : [library.course]

    template.id = id
    template.relatedProjectId = input.projectId
    template.title = `${library.course}${input.targetType}计划`
    template.goal = input.supplementalRequirement
      ? `${input.prompt}（补充要求：${input.supplementalRequirement}）`
      : input.prompt
    template.updatedAt = '刚刚'
    template.libraryId = library.id
    template.status = '进行中'
    template.period = input.period
    template.targetType = input.targetType
    template.progress = 0
    template.taskDone = 0
    template.exerciseDone = 0
    template.correctRate = 0
    template.weeklyHours = '0h'
    template.profile = [
      { label: '资料来源', value: library.name },
      { label: '当前基础', value: input.foundation },
      { label: '重点知识', value: input.weakPoints || focus },
      { label: '学习约束', value: input.preferences.join(' + ') || '考试复习' },
      { label: '目标', value: input.targetType },
      { label: '节奏', value: input.dailyTime },
      { label: '输出深度', value: input.studyDepth },
    ]
    if (input.supplementalRequirement) {
      template.profile.push({ label: '补充要求', value: input.supplementalRequirement })
    }
    const stageTitles = ['基础认知', '核心强化', '综合测验']
    template.stages.forEach((stage, stageIndex) => {
      const topic = topics[stageIndex % topics.length]!
      stage.title = `${topic}${stageTitles[stageIndex] ?? '巩固提升'}`
      stage.desc = stageIndex === 0
        ? `建立${topic}知识框架并完成基础理解检查`
        : stageIndex === 1
          ? `通过案例和专项训练强化${topic}`
          : `通过综合测验检测${topic}掌握情况`
      stage.scheduleLabel = `建议第 ${stageIndex + 1} 个学习时段完成`
      stage.tasks.forEach((task) => {
        task.done = false
        task.status = '未开始'
        task.completionSource = undefined
        task.readProgress = 0
        task.validStudySeconds = 0
        task.completedActions = []
        const actionMap = {
          讲解: `学习${topic}核心概念`,
          资料: `阅读${topic}个性化学习手册`,
          练习: `完成${topic}专项练习`,
          测验: `完成${topic}综合测验`,
          案例: `分析${topic}典型案例`,
        }
        task.title = actionMap[task.type]
      })
    })
    template.totalTasks = template.stages.reduce((total, stage) => total + stage.tasks.length, 0)
    const difficultyStrategy = inferDifficultyStrategy(input.foundation, input.studyDepth)
    const questionCount = Math.max(10, Math.min(200, Math.round(input.questionCount || 60)))
    const difficultyCounts = getDifficultyCounts(questionCount, difficultyStrategy)
    template.exercises = createQuestionBatch(questionCount, topics, difficultyStrategy, 1, 'initial')
    const typeCounts = template.exercises.reduce<Partial<Record<Exercise['type'], number>>>((counts, exercise) => {
      counts[exercise.type] = (counts[exercise.type] ?? 0) + 1
      return counts
    }, {})
    template.questionBank = {
      targetCount: questionCount,
      initialCount: questionCount,
      generatedCount: questionCount,
      difficultyStrategy,
      difficultyCounts,
      typeCounts,
      generatedAt: '刚刚',
    }
    template.totalExercises = questionCount
    template.wrongQuestions = []
    template.trainingSets = []
    template.dashboard = library.tags.slice(0, 3).map((label) => ({ label, value: 0 }))
    template.resources = template.resources.filter((resource) => input.resourceGroups.includes(resource.group))
    template.resources.forEach((resource) => {
      resource.title = `${library.course}${resource.group}`
      resource.desc = `基于${library.name}生成的${resource.group}学习资源。`
      resource.fileName = `${library.course}-${resource.group}`
      resource.status = '已生成'
      resource.action = '查看'
    })
    const resourcePreferences: Partial<Record<LearningPlan['stages'][number]['tasks'][number]['type'], LearningResource['group'][]>> = {
      讲解: ['个性化学习手册', '思维导图', 'PPT'],
      资料: ['个性化学习手册'],
      案例: ['代码案例'],
    }
    template.stages.forEach((stage) => {
      stage.tasks.forEach((task) => {
        const groups = resourcePreferences[task.type] ?? []
        task.resourceId = template.resources.find((resource) => groups.includes(resource.group))?.id
        task.completionMode = task.type === '讲解' ? 'content'
          : task.type === '资料' ? 'resource'
            : task.type === '练习' ? 'exercise'
              : task.type === '测验' ? 'assessment'
                : task.type === '案例' ? 'case' : 'manual'
      })
    })
    assignQuestionBankToTasks(template)

    plans.value.unshift(template)
    template.resources.forEach((resource) => {
      libraryResourceStore.addGeneratedResource(
        resource,
        '智能学习生成',
        template.id,
        input.projectId,
        template.libraryId,
      )
    })
    persist()
    return template
  }

  function createDraftPlan(input: CreateLearningDraftInput) {
    const template = structuredClone(mockPlans[0]!)
    const id = Math.max(0, ...plans.value.map((plan) => plan.id)) + 1
    const library = courseLibraries.find((item) => item.id === input.libraryId)
    const title = input.title.trim() || '未命名智能学习'

    template.id = id
    template.relatedProjectId = null
    template.title = title
    template.goal = '待通过对话确认学习目标、学习约束和学习路径。'
    template.updatedAt = '刚刚'
    template.libraryId = library?.id ?? 0
    template.status = '待开启'
    template.period = '待确认'
    template.targetType = '待确认'
    template.progress = 0
    template.taskDone = 0
    template.exerciseDone = 0
    template.correctRate = 0
    template.weeklyHours = '0h'
    template.profile = [
      { label: '资料来源', value: library?.name ?? '无' },
      { label: '学习约束', value: '待确认' },
      { label: '重点知识', value: '待确认' },
      { label: '节奏', value: '待确认' },
    ]
    template.stages = []
    template.resources = []
    template.exercises = []
    template.questionBank = undefined
    template.trainingSets = []
    template.wrongQuestions = []
    template.wrongReviewSets = []
    template.dashboard = []
    template.totalTasks = 0
    template.totalExercises = 0
    template.agents = template.agents.map((agent) => ({ ...agent, status: 'pending' }))

    plans.value.unshift(template)
    persist()
    return template
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
    setProfileValue(plan, '目标', input.targetType)
    setProfileValue(plan, '节奏', input.dailyTime)
    setProfileValue(plan, '重点知识', input.weakPoints)
    setProfileValue(plan, '薄弱点', input.weakPoints)
    setProfileValue(plan, '学习约束', input.preferences.join(' + ') || '待确认')

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

  function submitExerciseGroup(planId: number, exerciseIds: number[], trainingSetId?: number): TrainingSetResult | undefined {
    const plan = getPlan(planId)
    if (!plan) return
    const exercises = exerciseIds
      .map((id) => plan.exercises.find((exercise) => exercise.id === id))
      .filter((exercise): exercise is Exercise => Boolean(exercise))
    if (!exercises.length || exercises.some((exercise) => !exercise.draftAnswer)) return

    exercises.forEach((exercise) => {
      submitExercise(planId, exercise.id, exercise.draftAnswer!)
    })
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

  function createAdaptivePracticeTask(
    planId: number,
    sourceTaskId: number,
    input: { mode: 'repeat' | 'reinforce'; count: number; difficultyMode: '保持难度' | '逐步提升' },
  ) {
    const plan = getPlan(planId)
    const stage = plan?.stages.find((item) => item.tasks.some((task) => task.id === sourceTaskId))
    const sourceTask = stage?.tasks.find((task) => task.id === sourceTaskId)
    if (!plan || !stage || !sourceTask) return
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
        generated = createQuestionBatch(deficit, topics, strategy, nextExerciseId, `additional-${Date.now()}`, '追加练习')
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

  function submitExercise(planId: number, exerciseId: number, userAnswer: string): ExerciseResult | undefined {
    const plan = getPlan(planId)
    const exercise = plan?.exercises.find((item) => item.id === exerciseId)
    if (!plan || !exercise || !userAnswer) return

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

  function reviewWrongQuestion(planId: number, wrongId: number, answer: string): ExerciseResult | undefined {
    const plan = getPlan(planId)
    const wrong = plan?.wrongQuestions.find((item) => item.id === wrongId)
    const exercise = plan?.exercises.find((item) => item.id === wrongId)
    if (!plan || !wrong || !exercise || !answer) return
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

  function createWrongReviewSet(
    planId: number,
    wrongIds: number[],
    input: { count: number; difficultyMode: '保持难度' | '逐步提升' },
  ) {
    const plan = getPlan(planId)
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

  function submitWrongReviewSet(planId: number, setId: number): TrainingSetResult | undefined {
    const plan = getPlan(planId)
    const set = plan?.wrongReviewSets?.find((item) => item.id === setId)
    const exercises = set?.exerciseIds
      .map((id) => plan?.exercises.find((item) => item.id === id))
      .filter((item): item is Exercise => Boolean(item)) ?? []
    if (!plan || !set || !exercises.length || exercises.some((item) => !item.draftAnswer)) return
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

  return {
    plans,
    projectCount,
    getPlan,
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
  }
})
