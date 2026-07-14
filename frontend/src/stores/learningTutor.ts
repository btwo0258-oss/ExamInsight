import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { Exercise, LearningPlan, LearningStage, LearningTask } from '@/mock'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore } from '@/stores/message'

type TutorContext = {
  stage?: LearningStage
  task?: LearningTask
  exercise?: Exercise
}

function buildSource(plan: LearningPlan, context: TutorContext = {}) {
  const parts = [context.stage?.title, context.task?.title, context.exercise?.title].filter(Boolean)
  return {
    projectId: plan.id,
    projectTitle: plan.title,
    page: context.task ? 'study' as const : 'detail' as const,
    stageId: context.stage?.id,
    stageTitle: context.stage?.title,
    taskId: context.task?.id,
    taskTitle: context.task?.title,
    taskType: context.task?.type,
    exerciseId: context.exercise?.id,
    exerciseTitle: context.exercise?.title,
    submitted: context.exercise?.submitted,
    label: parts.join(' · ') || '学习详情',
  }
}

function storageKey(planId: number) {
  return `examinsight.learning.tutor-conversation.${planId}`
}

function buildContext(plan: LearningPlan, context: TutorContext = {}) {
  const scheme = plan.resources.find((resource) => resource.group === '学习方案')
  const stages = plan.stages.map((stage) => {
    const tasks = stage.tasks.map((task) => `- ${task.title}（${task.status ?? (task.done ? '已完成' : '未开始')}）`).join('\n')
    return `### ${stage.title}\n${stage.desc}\n${tasks}`
  }).join('\n\n')
  const taskExercises = context.task?.exerciseIds?.map((id) => plan.exercises.find((item) => item.id === id)).filter(Boolean) ?? []
  const assessmentLocked = context.task?.type === '测验' && taskExercises.some((item) => !item?.submitted)
  const practiceLocked = context.task?.type === '练习' && taskExercises.some((item) => !item?.submitted)
  const guardrail = assessmentLocked
    ? '当前是未交卷测验：只能提供概念提示和解题方向，不得透露正确答案、标准代码或可直接提交的完整解法。'
    : practiceLocked
      ? '当前练习尚未全部提交：优先用提示、反问和分步引导帮助用户思考，不直接给出标准答案。'
      : '可以结合用户作答结果完整解释概念、错误原因和改进方式。'

  return [
    '你是该学习项目专属的 AI 助教。回答要简洁、准确，并优先结合当前页面内容。',
    guardrail,
    `项目：${plan.title}`,
    `目标：${plan.targetType}`,
    `周期：${plan.period}`,
    `进度：${plan.progress}%`,
    '',
    '学习画像：',
    plan.profile.map((item) => `- ${item.label}：${item.value}`).join('\n'),
    '',
    context.stage ? `当前阶段：${context.stage.title}｜${context.stage.desc}` : '',
    context.task ? `当前任务：${context.task.title}｜类型：${context.task.type}｜状态：${context.task.status ?? '未开始'}` : '',
    context.exercise ? `当前题目：${context.exercise.title}\n题型：${context.exercise.type}\n选项：${context.exercise.options.join('；') || '无'}\n用户草稿：${context.exercise.draftAnswer || context.exercise.userAnswer || '尚未作答'}` : '',
    '',
    '学习路径：',
    stages,
    '',
    `错题知识点：${[...new Set(plan.wrongQuestions.flatMap((wrong) => wrong.knowledge))].join('、') || '暂无'}`,
    `资源包：${plan.resources.map((resource) => resource.group).join('、') || '暂无'}`,
    '',
    '最终学习方案：',
    (scheme?.content || plan.goal || '暂无').slice(0, 6000),
  ].filter(Boolean).join('\n')
}

export const useLearningTutorStore = defineStore('learningTutor', () => {
  const conversationIds = ref<Record<number, number>>({})
  const conversationStore = useConversationStore()
  const messageStore = useMessageStore()

  async function ensureConversation(plan: LearningPlan) {
    conversationStore.init()
    const title = `${plan.title} · AI 助教`
    const storedId = conversationIds.value[plan.id] ?? Number(localStorage.getItem(storageKey(plan.id)) ?? sessionStorage.getItem(storageKey(plan.id)))
    let matched = conversationStore.list.find((item) => item.id === storedId)
      ?? conversationStore.list.find((item) => item.learningProjectId === plan.id && item.title === title)
    if (!matched && Number.isFinite(storedId) && storedId > 0) {
      conversationStore.restoreLearningConversation(storedId, plan.id, plan.title, plan.libraryId || null, title)
      matched = conversationStore.list.find((item) => item.id === storedId)
    }
    const conversationId = matched?.id ?? await conversationStore.create({
      kbId: plan.libraryId || null,
      title,
      navigate: false,
      learningProjectId: plan.id,
      learningProjectName: plan.title,
      conversationType: 'learning-tutor',
    })
    conversationIds.value[plan.id] = conversationId
    sessionStorage.setItem(storageKey(plan.id), String(conversationId))
    localStorage.setItem(storageKey(plan.id), String(conversationId))
    conversationStore.linkLearningProject(conversationId, plan.id, plan.title, 'learning-tutor')
    await messageStore.ensureLoaded(conversationId)
    return conversationId
  }

  async function send(plan: LearningPlan, question: string, context: TutorContext = {}) {
    const conversationId = await ensureConversation(plan)
    await messageStore.sendMessage(
      conversationId,
      question,
      undefined,
      undefined,
      undefined,
      undefined,
      false,
      { tutorContext: buildContext(plan, context), tutorSource: buildSource(plan, context) },
    )
    conversationStore.linkLearningProject(conversationId, plan.id, plan.title, 'learning-tutor')
    return conversationId
  }

  function getConversationId(planId: number) {
    return conversationIds.value[planId] ?? (Number(localStorage.getItem(storageKey(planId)) ?? sessionStorage.getItem(storageKey(planId))) || null)
  }

  return { conversationIds, ensureConversation, send, getConversationId }
})
