import { createCodeLanguageOptions, learningPlans } from '@/mock'
import type { LearningPlan } from '@/mock'

const STORAGE_KEY = 'examinsight.learning.plans'

function clonePlans(plans: LearningPlan[]) {
  return normalizePlans(structuredClone(plans))
}

type LegacyLearningPlan = Omit<LearningPlan, 'stages'> & {
  stages?: LearningPlan['stages']
  days?: LearningPlan['stages']
}

function normalizePlans(input: LegacyLearningPlan[]): LearningPlan[] {
  return input.map((legacyPlan) => {
    const stages = legacyPlan.stages ?? legacyPlan.days ?? []
    stages.forEach((stage) => {
      stage.tasks = stage.tasks.filter((task) => String(task.type) !== '复盘')
      if (stage.title === '综合测验与错题复盘') stage.title = '综合测验'
      if (stage.desc.includes('错题整理')) stage.desc = '通过综合训练检验核心知识掌握情况'
    })
    const plan = { ...legacyPlan, stages } as LearningPlan
    delete (plan as LearningPlan & { days?: LearningPlan['stages'] }).days
    if (!plan.resources.some((resource) => resource.group === '学习方案')) {
      plan.resources.unshift({
        id: Math.max(0, ...plan.resources.map((resource) => resource.id)) + 1,
        group: '学习方案',
        title: `${plan.title}学习方案`,
        desc: '最终确认的学习目标、学习画像与阶段安排。',
        status: '已生成',
        action: '查看',
        fileName: `${plan.title}-学习方案.md`,
      })
    }
    plan.resources = plan.resources.filter((resource) => !['练习题', '推荐阅读'].includes(String(resource.group)))
    plan.trainingSets ??= []
    plan.wrongReviewSets ??= []
    plan.wrongQuestions.forEach((wrong) => {
      wrong.errorCount ??= 1
      wrong.reviewCount ??= 0
      wrong.correctStreak ??= 0
      wrong.status = wrong.status === '已掌握' && wrong.correctStreak >= 2 ? '已掌握' : '需巩固'
      wrong.lastWrongAt ??= plan.updatedAt
      wrong.reviewHistory ??= []
    })

    const checkpointIds = plan.exercises.filter((item) => item.scene === 'checkpoint').map((item) => item.id)
    const practiceIds = plan.exercises.filter((item) => !item.scene || item.scene === 'practice').map((item) => item.id)
    const assessmentIds = plan.exercises.filter((item) => item.scene === 'assessment').map((item) => item.id)
    plan.exercises.forEach((item) => {
      item.options ??= []
      if (item.difficulty === '中等') item.difficulty = '进阶'
      if (item.difficulty === '提高') item.difficulty = '挑战'
      item.cognitiveLevel ??= item.difficulty === '基础' ? '概念理解' : item.difficulty === '进阶' ? '直接应用' : '综合迁移'
      item.purpose ??= item.scene === 'checkpoint' ? '随堂检查' : item.scene === 'assessment' ? '阶段测验' : '阶段练习'
      item.generationBatch ??= 'legacy'
      if (item.type === '代码题') {
        item.codeLanguages ??= createCodeLanguageOptions(item.title.includes('Dog') ? 'countDogs' : 'verify', item.id)
        item.selectedLanguage ??= 'java'
        item.codeDrafts ??= {}
        if (item.draftAnswer) item.codeDrafts[item.selectedLanguage] ??= item.draftAnswer
      }
      if (item.submitted && item.gradingCorrect === undefined) {
        item.gradingCorrect = item.userAnswer === item.answer
        item.gradingScore = item.gradingCorrect ? 100 : 0
      }
    })
    plan.questionBank ??= {
      targetCount: plan.exercises.length,
      initialCount: plan.exercises.length,
      generatedCount: plan.exercises.length,
      difficultyStrategy: '均衡',
      difficultyCounts: {
        basic: plan.exercises.filter((item) => item.difficulty === '基础').length,
        advanced: plan.exercises.filter((item) => item.difficulty === '进阶').length,
        challenge: plan.exercises.filter((item) => item.difficulty === '挑战').length,
      },
      typeCounts: plan.exercises.reduce<Partial<Record<(typeof plan.exercises)[number]['type'], number>>>((counts, item) => {
        counts[item.type] = (counts[item.type] ?? 0) + 1
        return counts
      }, {}),
      generatedAt: plan.updatedAt,
    }
    plan.questionBank.typeCounts ??= plan.exercises.reduce<Partial<Record<(typeof plan.exercises)[number]['type'], number>>>((counts, item) => {
      counts[item.type] = (counts[item.type] ?? 0) + 1
      return counts
    }, {})
    const submittedExercises = plan.exercises.filter((item) => item.submitted)
    plan.totalExercises = plan.exercises.length
    plan.exerciseDone = submittedExercises.length
    plan.correctRate = submittedExercises.length
      ? Math.round((submittedExercises.filter((item) => item.gradingCorrect).length / submittedExercises.length) * 100)
      : 0
    const practiceTasks = stages.flatMap((stage) => stage.tasks).filter((task) => task.type === '练习')
    const conceptTasks = stages.flatMap((stage) => stage.tasks).filter((task) => task.type === '讲解')

    stages.forEach((stage, stageIndex) => {
      stage.scheduleLabel ??= `建议安排在计划第 ${stageIndex + 1} 个时间段`
      stage.tasks.forEach((task) => {
        task.status ??= task.done ? '已完成' : '未开始'
        task.completionSource ??= task.done ? '历史学习进度' : undefined
        task.readProgress ??= task.done && (task.type === '讲解' || task.type === '资料') ? 100 : 0
        task.validStudySeconds ??= 0
        task.completedActions ??= []
        if (!task.completionMode) {
          const modeMap = {
            讲解: 'content',
            资料: 'resource',
            练习: 'exercise',
            测验: 'assessment',
            案例: 'case',
          } as const
          task.completionMode = modeMap[task.type]
        }
        if (!task.exerciseIds?.length) {
          if (task.type === '讲解') {
            const taskIndex = conceptTasks.findIndex((item) => item.id === task.id)
            task.exerciseIds = checkpointIds.filter((_, index) => index % Math.max(conceptTasks.length, 1) === taskIndex)
          }
          if (task.type === '测验') task.exerciseIds = assessmentIds
          if (task.type === '练习') {
            const taskIndex = practiceTasks.findIndex((item) => item.id === task.id)
            task.exerciseIds = practiceIds.filter((_, index) => index % Math.max(practiceTasks.length, 1) === taskIndex)
          }
        }
      })
    })
    plan.totalTasks = stages.reduce((total, stage) => total + stage.tasks.length, 0)
    plan.taskDone = stages.reduce((total, stage) => total + stage.tasks.filter((task) => task.done).length, 0)
    plan.progress = plan.totalTasks ? Math.round((plan.taskDone / plan.totalTasks) * 100) : 0
    plan.status = plan.progress === 100 ? '已完成' : '进行中'
    return plan
  })
}

export function getLearningPlans(): LearningPlan[] {
  const stored = sessionStorage.getItem(STORAGE_KEY)
  if (!stored) return clonePlans(learningPlans)

  try {
    return normalizePlans(JSON.parse(stored) as LegacyLearningPlan[])
  } catch {
    return clonePlans(learningPlans)
  }
}

export function saveLearningPlans(plans: LearningPlan[]) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(plans))
}
