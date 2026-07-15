import { courseLibraries, createCodeLanguageOptions, learningPlans as mockPlans } from '@/mock/student'
import type { CodeLanguageKey, Exercise, LearningPlan, LearningResource } from '@/mock/student'
import type { CreateLearningDraftInput, CreateLearningPlanInput } from '@/types/contracts/learning'

export type DifficultyStrategy = NonNullable<LearningPlan['questionBank']>['difficultyStrategy']

export type MockExerciseResult = {
  correct: boolean
  explanation: string
  correctAnswer: string
  score?: number
  feedback?: string
}

function normalizeAnswer(value: string) {
  return value.trim().toLowerCase().replace(/[\s，。；、,.;]+/g, '')
}

export function evaluateMockExerciseAnswer(exercise: Exercise, userAnswer: string): MockExerciseResult {
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
      ? `${language?.runtime ?? exercise.runtime ?? '当前语言'}原型规则检查通过。正式环境必须由后端安全判题服务执行公开和隐藏用例。`
      : `实现尚未通过 Mock 原型规则检查，缺少关键逻辑：${patterns.filter((item) => !matched.includes(item)).join('、') || '待后端判题服务确认'}。`
  } else {
    correct = userAnswer === exercise.answer
    score = correct ? 100 : 0
  }

  const correctAnswer = exercise.type === '代码题'
    ? exercise.codeLanguages?.find((item) => item.key === exercise.selectedLanguage)?.referenceAnswer ?? exercise.answer
    : exercise.answer
  return { correct, score, feedback, explanation: exercise.explanation, correctAnswer }
}

export function getMockDifficultyCounts(count: number, strategy: DifficultyStrategy) {
  const ratios = strategy === '基础为主' ? [0.5, 0.4] : strategy === '强化提高' ? [0.2, 0.5] : [0.3, 0.5]
  const basic = Math.round(count * ratios[0]!)
  const advanced = Math.round(count * ratios[1]!)
  return { basic, advanced, challenge: Math.max(0, count - basic - advanced) }
}

function inferMockDifficultyStrategy(foundation: string, studyDepth: string): DifficultyStrategy {
  if (foundation.includes('零基础') || foundation.includes('薄弱')) return '基础为主'
  if (foundation.includes('有一定') || studyDepth.includes('刷题') || studyDepth.includes('实操')) return '强化提高'
  return '均衡'
}

export function createMockQuestionBatch(
  count: number,
  topics: string[],
  strategy: DifficultyStrategy,
  startId: number,
  batch: string,
  purposeOverride?: Exercise['purpose'],
): Exercise[] {
  const distribution = getMockDifficultyCounts(count, strategy)
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
      exercise.selectedLanguage = 'java' as CodeLanguageKey
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

export function createMockLearningPlan(input: CreateLearningPlanInput, plans: LearningPlan[]): LearningPlan {
  const template = structuredClone(mockPlans[0]!)
  const library = courseLibraries.find((item) => item.id === input.libraryId)
  const draftPlan = input.draftPlanId ? plans.find((plan) => plan.id === input.draftPlanId) : null
  const id = draftPlan?.id ?? Math.max(0, ...plans.map((plan) => plan.id)) + 1
  const libraryName = input.libraryName || library?.name || '无'
  const subjectName = library?.course || input.libraryName?.replace(/知识库|资料库/g, '').trim() || '个性化学习'
  const inferredTopics = input.weakPoints.split(/[、,，/]+/).map((item) => item.trim()).filter(Boolean)
  const topics = library?.tags.length ? library.tags : inferredTopics.length ? inferredTopics : ['核心知识']
  const focus = topics.slice(0, 3).join('、') || subjectName

  Object.assign(template, {
    id,
    relatedProjectId: input.projectId,
    title: draftPlan?.title || `${subjectName}${input.targetType}计划`,
    goal: input.supplementalRequirement ? `${input.prompt}（补充要求：${input.supplementalRequirement}）` : input.prompt,
    updatedAt: '刚刚',
    libraryId: input.libraryId || library?.id || 0,
    status: '进行中',
    period: input.period,
    targetType: input.targetType,
    progress: 0,
    taskDone: 0,
    exerciseDone: 0,
    correctRate: 0,
    weeklyHours: '0h',
  })
  template.profile = [
    { label: '学习目标', value: input.targetType },
    { label: '当前基础', value: input.foundation },
    { label: '重点知识', value: input.weakPoints || focus },
    { label: '时间安排', value: `${input.period}，${input.dailyTime}` },
    { label: '学习方式', value: input.preferences.join(' + ') || input.studyDepth },
    { label: '资料来源', value: libraryName },
    { label: '输出深度', value: input.studyDepth },
  ]
  if (input.supplementalRequirement) template.profile.push({ label: '补充要求', value: input.supplementalRequirement })

  const stageTitles = ['基础认知', '核心强化', '综合测验']
  template.stages.forEach((stage, stageIndex) => {
    const topic = topics[stageIndex % topics.length]!
    stage.title = `${topic}${stageTitles[stageIndex] ?? '巩固提升'}`
    stage.desc = stageIndex === 0 ? `建立${topic}知识框架并完成基础理解检查`
      : stageIndex === 1 ? `通过案例和专项训练强化${topic}` : `通过综合测验检测${topic}掌握情况`
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
  const difficultyStrategy = inferMockDifficultyStrategy(input.foundation, input.studyDepth)
  const questionCount = Math.max(10, Math.min(200, Math.round(input.questionCount || 60)))
  template.exercises = createMockQuestionBatch(questionCount, topics, difficultyStrategy, 1, 'initial')
  template.questionBank = {
    targetCount: questionCount,
    initialCount: questionCount,
    generatedCount: questionCount,
    difficultyStrategy,
    difficultyCounts: getMockDifficultyCounts(questionCount, difficultyStrategy),
    typeCounts: template.exercises.reduce<Partial<Record<Exercise['type'], number>>>((counts, exercise) => {
      counts[exercise.type] = (counts[exercise.type] ?? 0) + 1
      return counts
    }, {}),
    generatedAt: '刚刚',
  }
  template.totalExercises = questionCount
  template.wrongQuestions = []
  template.trainingSets = []
  template.dashboard = topics.slice(0, 3).map((label) => ({ label, value: 0 }))
  template.resources = template.resources.filter((resource) => input.resourceGroups.includes(resource.group))
  template.resources.forEach((resource) => {
    resource.title = `${subjectName}${resource.group}`
    resource.desc = `基于${libraryName}生成的${resource.group}学习资源。`
    resource.fileName = `${subjectName}-${resource.group}`
    resource.status = '已生成'
    resource.action = '查看'
  })
  template.resources.unshift({
    id: Math.max(0, ...template.resources.map((resource) => resource.id)) + 1,
    group: '学习方案',
    title: `${template.title}学习方案`,
    desc: '最终确认的学习目标、学习画像与阶段安排。',
    status: '已生成',
    action: '查看',
    fileName: `${template.title}-学习方案.md`,
    content: input.prompt,
  })
  const resourcePreferences: Partial<Record<LearningPlan['stages'][number]['tasks'][number]['type'], LearningResource['group'][]>> = {
    讲解: ['个性化学习手册', '思维导图', 'PPT'],
    资料: ['个性化学习手册'],
    案例: ['代码案例'],
  }
  template.stages.forEach((stage) => stage.tasks.forEach((task) => {
    const groups = resourcePreferences[task.type] ?? []
    task.resourceId = template.resources.find((resource) => groups.includes(resource.group))?.id
    task.completionMode = task.type === '讲解' ? 'content' : task.type === '资料' ? 'resource'
      : task.type === '练习' ? 'exercise' : task.type === '测验' ? 'assessment' : task.type === '案例' ? 'case' : 'manual'
  }))
  assignQuestionBankToTasks(template)
  return template
}

export function createMockLearningDraft(input: CreateLearningDraftInput, plans: LearningPlan[]): LearningPlan {
  const template = structuredClone(mockPlans[0]!)
  const library = courseLibraries.find((item) => item.id === input.libraryId)
  template.id = Math.max(0, ...plans.map((plan) => plan.id)) + 1
  template.relatedProjectId = null
  template.title = input.title.trim() || '未命名智能学习'
  template.icon = input.icon || 'folder'
  template.iconColor = input.iconColor || '#000'
  template.goal = '待通过对话确认学习目标、学习约束和学习路径。'
  template.updatedAt = '刚刚'
  template.libraryId = input.libraryId ?? 0
  template.status = '待开启'
  template.period = '待确认'
  template.targetType = '待确认'
  template.progress = 0
  template.taskDone = 0
  template.exerciseDone = 0
  template.correctRate = 0
  template.weeklyHours = '0h'
  template.profile = [
    { label: '资料来源', value: input.libraryName || library?.name || '无' },
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
  return template
}
