import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getLearningPlans, saveLearningPlans } from '@/api/learning'
import { courseLibraries, learningPlans as mockPlans } from '@/mock'
import type { Exercise, LearningPlan, LearningResource, WrongQuestion } from '@/mock'
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
  supplementalRequirement: string
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
      { label: '学习偏好', value: input.preferences.join(' + ') || '图文讲解' },
      { label: '目标', value: input.targetType },
      { label: '节奏', value: input.dailyTime },
      { label: '输出深度', value: input.studyDepth },
    ]
    if (input.supplementalRequirement) {
      template.profile.push({ label: '补充要求', value: input.supplementalRequirement })
    }
    template.days.forEach((day, dayIndex) => {
      const topic = topics[dayIndex % topics.length]!
      day.title = `${topic}专项学习`
      day.desc = `围绕${topic}完成讲解、资料阅读、练习和复盘`
      day.tasks.forEach((task) => {
        task.done = false
        const actionMap = {
          讲解: `学习${topic}核心概念`,
          资料: `阅读${topic}个性化学习手册`,
          练习: `完成${topic}专项练习`,
          复盘: `复盘${topic}薄弱点`,
          测验: `完成${topic}综合测验`,
          案例: `分析${topic}典型案例`,
        }
        task.title = actionMap[task.type]
      })
    })
    template.totalTasks = template.days.reduce((total, day) => total + day.tasks.length, 0)
    template.exercises.forEach((exercise) => {
      exercise.userAnswer = undefined
      exercise.submitted = false
    })
    if (library.id !== 1) {
      template.exercises = topics.slice(0, 2).map((topic, index) => ({
        id: index + 1,
        title: `学习“${topic}”时，下面哪种做法最有助于真正掌握知识？`,
        knowledge: topic,
        difficulty: index === 0 ? '基础' : '中等',
        type: '单选题',
        options: ['A. 只记住结论', `B. 结合资料和练习理解${topic}`, 'C. 跳过例题', 'D. 只看答案'],
        answer: `B. 结合资料和练习理解${topic}`,
        explanation: `资料理解与练习反馈结合，能够帮助你形成对${topic}的稳定理解。`,
        submitted: false,
      }))
    }
    template.totalExercises = template.exercises.length
    template.wrongQuestions = []
    template.dashboard = library.tags.slice(0, 3).map((label) => ({ label, value: 0 }))
    template.resources = template.resources.filter((resource) => input.resourceGroups.includes(resource.group))
    template.resources.forEach((resource) => {
      resource.title = `${library.course}${resource.group}`
      resource.desc = `基于${library.name}生成的${resource.group}学习资源。`
      resource.fileName = `${library.course}-${resource.group}`
      resource.status = '已生成'
      resource.action = '查看'
    })

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

  function setProfileValue(plan: LearningPlan, label: string, value: string) {
    const item = plan.profile.find((profile) => profile.label === label)
    if (item) item.value = value
    else plan.profile.push({ label, value })
  }

  function periodDayCount(period: string) {
    const match = period.match(/(\d+)\s*天/)
    return match ? Number(match[1]) : 0
  }

  function adjustDayCount(plan: LearningPlan, nextCount: number) {
    if (!nextCount || nextCount === plan.days.length) return
    if (nextCount < plan.days.length) {
      plan.days = plan.days.slice(0, nextCount)
      updateProgress(plan)
      return
    }

    const topics = plan.dashboard.map((item) => item.label)
    const fallbackTopic = plan.profile.find((item) => item.label === '重点知识' || item.label === '薄弱点')?.value ?? plan.title
    while (plan.days.length < nextCount) {
      const nextDayId = Math.max(0, ...plan.days.map((day) => day.id)) + 1
      const nextTaskId = Math.max(0, ...plan.days.flatMap((day) => day.tasks.map((task) => task.id))) + 1
      const topic = topics[(nextDayId - 1) % Math.max(topics.length, 1)] ?? fallbackTopic
      plan.days.push({
        id: nextDayId,
        title: `${topic}巩固复习`,
        desc: `根据调整后的计划补充${topic}学习任务`,
        tasks: [
          { id: nextTaskId, title: `复习${topic}核心内容`, duration: '30 分钟', done: false, type: '讲解' },
          { id: nextTaskId + 1, title: `完成${topic}阶段练习`, duration: '30 分钟', done: false, type: '练习' },
          { id: nextTaskId + 2, title: `整理${topic}错题和笔记`, duration: '20 分钟', done: false, type: '复盘' },
        ],
      })
    }
    updateProgress(plan)
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
    setProfileValue(plan, '学习偏好', input.preferences.join(' + ') || '待确认')

    if (!input.keepProgress) {
      plan.days.forEach((day) => {
        day.tasks.forEach((task) => {
          task.done = false
        })
      })
    }
    if (!input.keepExercises) {
      plan.exercises.forEach((exercise) => {
        exercise.userAnswer = undefined
        exercise.submitted = false
      })
      plan.exerciseDone = 0
      plan.correctRate = 0
    }

    adjustDayCount(plan, periodDayCount(input.period))
    updateProgress(plan)
    persist()
    return true
  }

  function updateProgress(plan: LearningPlan) {
    plan.taskDone = plan.days.reduce(
      (total, day) => total + day.tasks.filter((task) => task.done).length,
      0,
    )
    plan.totalTasks = plan.days.reduce((total, day) => total + day.tasks.length, 0)
    plan.progress = plan.totalTasks ? Math.round((plan.taskDone / plan.totalTasks) * 100) : 0
    plan.status = plan.progress === 100 ? '已完成' : '进行中'
    plan.updatedAt = '刚刚'
  }

  function markTaskDone(planId: number, taskId: number, done: boolean) {
    const plan = getPlan(planId)
    const task = plan?.days.flatMap((day) => day.tasks).find((item) => item.id === taskId)
    if (!plan || !task) return

    task.done = done
    updateProgress(plan)
    persist()
  }

  function submitExercise(planId: number, exerciseId: number, userAnswer: string): ExerciseResult | undefined {
    const plan = getPlan(planId)
    const exercise = plan?.exercises.find((item) => item.id === exerciseId)
    if (!plan || !exercise || !userAnswer) return

    const wasSubmitted = Boolean(exercise.submitted)
    const correct = userAnswer === exercise.answer
    exercise.userAnswer = userAnswer
    exercise.submitted = true

    if (!wasSubmitted) {
      const previousCorrect = Math.round((plan.correctRate / 100) * plan.exerciseDone)
      plan.exerciseDone += 1
      plan.correctRate = Math.round(((previousCorrect + Number(correct)) / plan.exerciseDone) * 100)
    }

    if (!correct && !plan.wrongQuestions.some((wrong) => wrong.id === exercise.id)) {
      const wrong: WrongQuestion = {
        id: exercise.id,
        title: exercise.title,
        knowledge: [exercise.knowledge],
        userAnswer,
        correctAnswer: exercise.answer,
        reason: exercise.explanation,
        synced: false,
      }
      plan.wrongQuestions.unshift(wrong)
    }

    const mastery = plan.dashboard.find((item) => item.label === exercise.knowledge)
    if (mastery && !wasSubmitted) mastery.value = Math.min(100, Math.max(0, mastery.value + (correct ? 8 : -4)))
    plan.updatedAt = '刚刚'
    persist()

    return { correct, explanation: exercise.explanation, correctAnswer: exercise.answer }
  }

  function generateSimilarExercise(planId: number, exerciseId: number): Exercise | undefined {
    const plan = getPlan(planId)
    const source = plan?.exercises.find((item) => item.id === exerciseId)
    if (!plan || !source) return

    const exercise = structuredClone(source)
    exercise.id = Math.max(0, ...plan.exercises.map((item) => item.id)) + 1
    exercise.title = `同类题：${source.title}`
    exercise.userAnswer = undefined
    exercise.submitted = false
    plan.exercises.push(exercise)
    plan.totalExercises = plan.exercises.length
    plan.updatedAt = '刚刚'
    persist()
    return exercise
  }

  function addWrongToReview(planId: number, wrongId: number) {
    const plan = getPlan(planId)
    const wrong = plan?.wrongQuestions.find((item) => item.id === wrongId)
    if (!plan || !wrong || wrong.synced) return false

    let reviewDay = plan.days.at(-1)
    if (!reviewDay) {
      reviewDay = { id: 1, title: '错题复盘', desc: '复习练习中的薄弱知识点', tasks: [] }
      plan.days.push(reviewDay)
    }
    const nextTaskId = Math.max(0, ...plan.days.flatMap((day) => day.tasks.map((task) => task.id))) + 1
    reviewDay.tasks.push({
      id: nextTaskId,
      title: `复习错题：${wrong.knowledge[0] ?? wrong.title}`,
      duration: '20 分钟',
      done: false,
      type: '复盘',
    })
    wrong.synced = true
    updateProgress(plan)
    persist()
    return true
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
    await new Promise((resolve) => window.setTimeout(resolve, 900))
    resource.status = '已生成'
    resource.action = '查看'
    libraryResourceStore.addGeneratedResource(
      resource,
      '智能学习生成',
      plan.id,
      plan.relatedProjectId ?? null,
      plan.libraryId,
    )
    generatingResourceIds.value = generatingResourceIds.value.filter((id) => id !== resourceId)
    plan.updatedAt = '刚刚'
    persist()
  }

  return {
    plans,
    projectCount,
    getPlan,
    createPlan,
    updatePlanConfig,
    markTaskDone,
    submitExercise,
    generateSimilarExercise,
    addWrongToReview,
    generateResource,
  }
})
