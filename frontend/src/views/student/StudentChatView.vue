<script setup lang="ts">
// @ts-nocheck
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppIcon from '@/components/common/AppIcon.vue'
import AppInput from '@/components/common/AppInput.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import MindMapPanel from '@/components/main-area/mode3-chat/MindMapPanel.vue'
import SegmentPanel from '@/components/main-area/mode3-chat/SegmentPanel.vue'
import MessageList from '@/components/main-area/mode3-chat/message/MessageList.vue'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore } from '@/stores/message'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useLearningStore } from '@/stores/learning'
import LibraryKnowledgeCreateModal from '@/components/student/LibraryKnowledgeCreateModal.vue'
import type { LearningProfileData } from '@/components/student/LearningProfileCard.vue'
import { courseLibraries } from '@/mock'

const conversationStore = useConversationStore()
const messageStore = useMessageStore()
const libraryResourceStore = useLibraryResourceStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const learningStore = useLearningStore()
const route = useRoute()
const router = useRouter()

const learningProjectId = computed(() => {
  const value = Number(route.query.learningProjectId)
  return Number.isFinite(value) && value > 0 ? value : null
})
const routeConversation = computed(() => {
  const id = Number(route.params.id)
  return Number.isFinite(id) ? conversationStore.list.find((item) => item.id === id) : null
})
const isLearningChat = computed(() => route.query.learning === '1' || learningProjectId.value !== null)
const isTutorChat = computed(() => learningProjectId.value !== null && (
  route.query.tutor === '1'
  || routeConversation.value?.conversationType === 'learning-tutor'
  || routeConversation.value?.title?.includes('AI 助教')
))
const isLearningSetupChat = computed(() => isLearningChat.value && !isTutorChat.value)
const tutorQuestionFromRoute = computed(() => typeof route.query.tutorQuestion === 'string' ? route.query.tutorQuestion.trim() : '')
const learningProject = computed(() => learningProjectId.value ? learningStore.getPlan(learningProjectId.value) : null)
const learningPhase = ref<'idle' | 'analyzing' | 'profile' | 'document' | 'generating'>('idle')
const selectedKnowledgeBaseId = ref<number | null>(null)
const knowledgeMenuOpen = ref(false)
const knowledgeMenuQuery = ref('')
const knowledgeCreateOpen = ref(false)
const currentProfileMessageId = ref('')
const currentDocumentMessageId = ref('')
const learningPrompt = ref('')
const confirmationDocument = ref('')
const decisionDismissed = ref(false)
let learningTimer: ReturnType<typeof window.setTimeout> | undefined
let tutorRequestKey = ''

const emptyProfile = (): LearningProfileData => ({
  goal: '待识别',
  subject: '待识别',
  foundation: '基础一般',
  weakPoints: [],
  period: '待确认',
  dailyTime: '每天 60 分钟',
  preferences: [],
  source: '无',
  extra: '',
})
const learningProfile = ref<LearningProfileData>(emptyProfile())

const availableKnowledgeBases = computed(() => {
  return knowledgeBaseStore.list.map((item) => ({ id: item.id, name: item.name, icon: item.icon || 'folder' }))
})
const selectedKnowledgeBase = computed(() => availableKnowledgeBases.value.find((item) => item.id === selectedKnowledgeBaseId.value))
const filteredKnowledgeBases = computed(() => {
  const query = knowledgeMenuQuery.value.trim().toLocaleLowerCase()
  if (!query) return availableKnowledgeBases.value
  return availableKnowledgeBases.value.filter((item) => item.name.toLocaleLowerCase().includes(query))
})

const activeChatId = computed(() => conversationStore.currentId)

watch(
  activeChatId,
  (id) => {
    messageStore.clearError()
    if (!id) return
    messageStore.ensureLoaded(id).then(() => {
      const autoMsgKey = `chat_auto_msg_${id}`
      const autoMsgStr = sessionStorage.getItem(autoMsgKey)
      if (!autoMsgStr) return

      try {
        const autoMsg = JSON.parse(autoMsgStr)
        sessionStorage.removeItem(autoMsgKey)
        messageStore.sendMessage(id, autoMsg.message)
      } catch (err) {
        console.error('Failed to parse auto message:', err)
        sessionStorage.removeItem(autoMsgKey)
      }
    })
  },
  { immediate: true },
)

const currentConversation = computed(() => {
  if (!activeChatId.value) return null
  return conversationStore.list.find((item) => item.id === activeChatId.value) || null
})

const pageTitle = computed(() => currentConversation.value?.title || '新对话')

const messages = computed(() => {
  if (!activeChatId.value) return []
  const key = String(activeChatId.value)
  const allMsgs = messageStore.byConversation[key] || []

  const filtered = allMsgs.filter((m) => {
    if (!m.turnId) return true

    const activeQ = messageStore.getActiveQVersion(activeChatId.value, m.turnId)
    const mQ = m.qVersion ?? 0
    const mA = m.aVersion ?? 0

    if (m.role === 'user') {
      return mQ === activeQ
    }
    if (m.role === 'assistant') {
      const activeA = messageStore.getActiveAVersion(activeChatId.value, m.turnId, activeQ)
      return mQ === activeQ && mA === activeA
    }
    return true
  })

  const turnMinTime: Record<string, number> = {}
  for (const m of allMsgs) {
    if (m.turnId && (!turnMinTime[m.turnId] || m.createTime < turnMinTime[m.turnId])) {
      turnMinTime[m.turnId] = m.createTime
    }
  }

  return filtered.sort((a, b) => {
    const timeA = a.turnId ? turnMinTime[a.turnId] : a.createTime
    const timeB = b.turnId ? turnMinTime[b.turnId] : b.createTime

    if (timeA !== timeB) return timeA - timeB
    if (a.role !== b.role) return a.role === 'user' ? -1 : 1
    return a.createTime - b.createTime
  })
})

const showWelcome = computed(() => !activeChatId.value || messages.value.length === 0)
const confirmationReady = computed(() => {
  if (learningPhase.value !== 'document' || !currentDocumentMessageId.value) return false
  const documentMessage = messages.value.find((message) => message.id === currentDocumentMessageId.value)
  return Boolean(documentMessage?.kind === 'learning-document' && !documentMessage.learningData?.loading && documentMessage.learningData?.content)
})

const showMindMapPanel = ref(false)
const mindMapContent = ref('')
const mindMapTitle = ref('')
const mindMapSidebarCollapsed = ref(false)
const messageListRef = ref<InstanceType<typeof MessageList> | null>(null)
const homeInputRef = ref<InstanceType<typeof AppInput> | null>(null)

const messageListContainer = computed(() => messageListRef.value?.scrollContainer ?? null)

const homePromptActions = [
  { icon: 'image', label: '生成图片', prompt: '帮我生成一张适合学习资料使用的图片，主题是：' },
  { icon: 'edit', label: '撰写或编辑', prompt: '帮我撰写或润色这段内容：' },
  { icon: 'search', label: '查找资料', prompt: '帮我查找并整理关于这个主题的资料：' },
  { icon: 'sparkle', label: '生成 PPT', prompt: '帮我生成一份 PPT 大纲，主题是：' },
  { icon: 'mindmap', label: '生成思维导图', prompt: '帮我生成一个思维导图，主题是：' },
]

function appendLearningMessage(conversationId: number, role: 'user' | 'assistant', content: string, extra: Record<string, unknown> = {}) {
  return messageStore.appendLocalMessage(conversationId, { role, content, ...extra })
}

function tutorConversationStorageKey(projectId: number) {
  return `examinsight.learning.tutor-conversation.${projectId}`
}

function buildTutorContext() {
  const project = learningProject.value
  if (!project) return '你是当前学习项目的 AI 助教。请围绕用户问题给出准确、清晰、可执行的学习指导。'
  const scheme = project.resources.find((resource) => resource.group === '学习方案')
  const stages = project.stages.map((stage) => {
    const tasks = stage.tasks.map((task) => `- ${task.title}（${task.status ?? (task.done ? '已完成' : '未开始')}）`).join('\n')
    return `### ${stage.title}\n${stage.desc}\n${tasks}`
  }).join('\n\n')
  const wrongPoints = [...new Set(project.wrongQuestions.flatMap((wrong) => wrong.knowledge))].join('、') || '暂无错题'

  return [
    '你是该学习项目专属的 AI 助教。回答应优先结合项目目标、学习画像、学习阶段、资源和错题情况；不要重新走学习方案确认流程。',
    `项目名称：${project.title}`,
    `目标类型：${project.targetType}`,
    `计划周期：${project.period}`,
    `项目进度：${project.progress}%`,
    '',
    '学习画像：',
    project.profile.map((item) => `- ${item.label}：${item.value}`).join('\n'),
    '',
    '学习路径：',
    stages,
    '',
    `当前错题知识点：${wrongPoints}`,
    `可用资源：${project.resources.map((resource) => resource.group).join('、') || '暂无'}`,
    '',
    '最终学习方案：',
    (scheme?.content || project.goal || '暂无').slice(0, 6000),
  ].join('\n')
}

async function ensureTutorConversation() {
  const project = learningProject.value
  if (!project) return null
  conversationStore.init()
  const storageKey = tutorConversationStorageKey(project.id)
  const storedId = Number(localStorage.getItem(storageKey) ?? sessionStorage.getItem(storageKey))
  const title = `${project.title} · AI 助教`
  const current = conversationStore.list.find((item) => item.id === activeChatId.value)
  let matched = current?.id === storedId || (current?.learningProjectId === project.id && current?.title === title)
    ? current
    : conversationStore.list.find((item) => item.id === storedId)
      ?? conversationStore.list.find((item) => item.learningProjectId === project.id && item.title === title)
  if (!matched && Number.isFinite(storedId) && storedId > 0) {
    conversationStore.restoreLearningConversation(storedId, project.id, project.title, project.libraryId || null, title)
    matched = conversationStore.list.find((item) => item.id === storedId)
  }

  const conversationId = matched?.id ?? await conversationStore.create({
    kbId: project.libraryId || null,
    title,
    navigate: false,
    learningProjectId: project.id,
    learningProjectName: project.title,
    conversationType: 'learning-tutor',
  })
  sessionStorage.setItem(storageKey, String(conversationId))
  localStorage.setItem(storageKey, String(conversationId))
  conversationStore.linkLearningProject(conversationId, project.id, project.title, 'learning-tutor')
  return conversationId
}

async function sendTutorQuestion(question: string, files?: File[]) {
  const nextQuestion = question.trim()
  if (!nextQuestion && !files?.length) return
  const conversationId = await ensureTutorConversation()
  if (!conversationId || !learningProject.value) return
  if (activeChatId.value !== conversationId) {
    await router.replace({
      path: `/chat/${conversationId}`,
      query: { learningProjectId: String(learningProject.value.id), tutor: '1' },
    })
  }
  await messageStore.sendMessage(
    conversationId,
    nextQuestion,
    undefined,
    undefined,
    undefined,
    files,
    false,
    {
      tutorContext: buildTutorContext(),
      tutorSource: {
        projectId: learningProject.value.id,
        projectTitle: learningProject.value.title,
        page: 'chat',
        label: '完整对话',
      },
    },
  )
  conversationStore.linkLearningProject(conversationId, learningProject.value.id, learningProject.value.title, 'learning-tutor')
}

function inferLearningProfile(text: string, current = emptyProfile()) {
  const next: LearningProfileData = {
    ...current,
    weakPoints: [...current.weakPoints],
    preferences: [...current.preferences],
  }
  const selectedCourse = courseLibraries.find((item) => item.id === selectedKnowledgeBaseId.value)
  next.source = selectedKnowledgeBase.value?.name || '无'

  if (/面试|秋招|春招|offer|职业/i.test(text)) next.goal = '职业技能'
  else if (/作业|实验|报告|论文|课程设计|科研/i.test(text)) next.goal = '作业 / 科研'
  else if (/项目|实战|开发|作品/i.test(text)) next.goal = '项目实践'
  else if (/考|复习|期末|期中|测验|四六级|cet/i.test(text)) next.goal = '考试备考'
  else if (next.goal === '待识别') next.goal = '系统学习'

  if (/零基础|从零|完全不会/i.test(text)) next.foundation = '尚未接触'
  else if (/基础差|不懂|不会|薄弱|混淆|分不清/i.test(text)) next.foundation = '基础薄弱'
  else if (/熟悉|掌握|有基础/i.test(text)) next.foundation = '有一定基础'

  const period = text.match(/(\d+)\s*(天|周|个月|月)/)
  if (period) next.period = `${period[1]} ${period[2]}`
  else if (/下周/i.test(text)) next.period = '1 周'
  else if (/明天/i.test(text)) next.period = '1 天'

  const daily = text.match(/每天.{0,8}?(\d+)\s*(分钟|小时)/)
  if (daily) next.dailyTime = `每天 ${daily[1]} ${daily[2]}`
  else if (/周末/i.test(text)) next.dailyTime = '仅周末'

  const preferences = new Set(next.preferences)
  if (/刷题|题海|错题|练习/i.test(text)) preferences.add('练习驱动')
  if (/项目|实战|开发/i.test(text)) preferences.add('项目实操')
  if (/先讲|讲解|概念|理论/i.test(text)) preferences.add('概念讲解')
  if (/案例|示例|例子/i.test(text)) preferences.add('案例演示')
  if (/图|导图|框架|结构/i.test(text)) preferences.add('图表梳理')
  if (/阅读|总结|笔记/i.test(text)) preferences.add('阅读总结')
  next.preferences = Array.from(preferences)

  const matchedTags = selectedCourse?.tags.filter((tag) => text.includes(tag)) || []
  if (matchedTags.length) next.weakPoints = Array.from(new Set([...next.weakPoints, ...matchedTags]))
  else {
    const weakMatch = text.match(/(?:薄弱|不会|不懂|分不清|复习)(?:的|是|一下)?[：:]?([^，。！？\n]{2,28})/)
    if (weakMatch?.[1]) next.weakPoints = Array.from(new Set([...next.weakPoints, ...weakMatch[1].split(/[、,，和与]/).map((item) => item.trim()).filter(Boolean)]))
  }

  if (selectedCourse) next.subject = selectedCourse.course
  else {
    const subjectMatch = text.match(/(?:学习|复习|准备|做一个)(?:一下)?\s*([^，。！？\n]{2,22})/)
    if (subjectMatch?.[1]) next.subject = subjectMatch[1].replace(/(?:的)?(?:计划|方案|考试)$/, '')
  }
  if (next.subject === '待识别' && next.weakPoints.length) next.subject = next.weakPoints.join('、')
  return next
}

function buildLearningDocument() {
  const profile = learningProfile.value
  const weakPoints = profile.weakPoints.length ? profile.weakPoints.map((item) => `- ${item}`).join('\n') : '- 在第一阶段进一步识别'
  const strategy = profile.foundation.includes('零基础') || profile.foundation.includes('薄弱') ? '基础理解为主，练习循序渐进' : '概念梳理与综合练习并行'
  return [
    '# 个性化学习方案确认稿', '',
    '## 1. 学习目标', learningPrompt.value || profile.goal, '',
    '## 2. 学习画像',
    `- 目标类型：${profile.goal}`,
    `- 学习内容：${profile.subject}`,
    `- 当前基础：${profile.foundation}`,
    `- 学习周期：${profile.period}`,
    `- 每日时间：${profile.dailyTime}`,
    `- 学习偏好：${profile.preferences.join(' · ') || '暂无特殊要求'}`,
    `- 资料来源：${profile.source}`, '',
    '## 3. 重点知识模块', weakPoints, '',
    '## 4. 学习路径',
    '### 阶段一：基础确认', '- 建立核心概念框架，通过小型理解检查定位薄弱环节。',
    '### 阶段二：专项强化', '- 围绕薄弱知识点完成例题拆解、专项练习和即时纠错。',
    '### 阶段三：综合复盘', '- 用综合任务检查迁移能力，归纳错题原因并完成巩固。', '',
    '## 5. 练习建议',
    `- 建议策略：${strategy}`,
    '- 具体题量和难度将根据阶段检查结果动态调整，不使用固定比例。', '',
    '## 6. 预计产出',
    '- 分阶段学习路径', '- 专项练习与错题巩固', '- 知识结构思维导图',
  ].join('\n')
}

async function ensureLearningConversation(text: string) {
  let projectId = learningProjectId.value
  if (!projectId) {
    const draft = learningStore.createDraftPlan({
      title: text.slice(0, 22) || '新学习项目',
      libraryId: selectedKnowledgeBaseId.value,
      libraryName: selectedKnowledgeBase.value?.name,
    })
    projectId = draft.id
  }
  let conversationId = activeChatId.value
  if (!conversationId) {
    conversationId = await conversationStore.create({
      kbId: selectedKnowledgeBaseId.value,
      title: `${learningProject.value?.title || text.slice(0, 22) || '新学习项目'} · 方案制定`,
      learningProjectId: projectId,
      learningProjectName: learningStore.getPlan(projectId)?.title || text.slice(0, 22),
      conversationType: 'learning-setup',
      localOnly: true,
      navigate: false,
    })
  }
  const projectName = learningStore.getPlan(projectId)?.title || text.slice(0, 22) || '新学习项目'
  conversationStore.linkLearningProject(conversationId, projectId, projectName, 'learning-setup')
  await router.replace({ path: `/chat/${conversationId}`, query: { learningProjectId: String(projectId) } })
  return conversationId
}

function showProfile(conversationId: number, text: string) {
  learningPhase.value = 'analyzing'
  const skeleton = appendLearningMessage(conversationId, 'assistant', '', {
    kind: 'learning-profile',
    learningData: { loading: true, confirmed: false, profile: learningProfile.value },
  })
  currentProfileMessageId.value = skeleton.id
  if (learningTimer) window.clearTimeout(learningTimer)
  learningTimer = window.setTimeout(() => {
    learningProfile.value = inferLearningProfile(text, learningProfile.value)
    messageStore.updateLocalMessage(conversationId, skeleton.id, {
      learningData: { loading: false, confirmed: false, profile: learningProfile.value },
    })
    learningPhase.value = 'profile'
  }, 850)
}

async function onSend(text: string, files?: File[]) {
  if (files?.length) {
    libraryResourceStore.addFiles(
      files,
      isLearningChat.value ? '智能学习上传' : '聊天上传',
      isLearningChat.value ? learningProjectId.value : null,
      isLearningChat.value ? selectedKnowledgeBaseId.value : currentConversation.value?.knowledgeBaseId ?? null,
    )
  }

  if (isTutorChat.value) {
    await sendTutorQuestion(text, files)
    return
  }

  if (isLearningSetupChat.value) {
    const conversationId = await ensureLearningConversation(text)
    appendLearningMessage(conversationId, 'user', text, {
      files: files?.map((file) => ({ name: file.name, type: file.type, size: file.size })),
    })
    learningPrompt.value ||= text
    learningProfile.value = inferLearningProfile(`${learningPrompt.value} ${text}`, learningProfile.value)
    if (learningPhase.value === 'document') {
      appendLearningMessage(conversationId, 'assistant', '好的，我已根据你的补充更新学习画像和方案确认稿。')
      confirmationDocument.value = buildLearningDocument()
      if (currentDocumentMessageId.value) {
        messageStore.updateLocalMessage(conversationId, currentDocumentMessageId.value, {
          learningData: { loading: false, content: confirmationDocument.value },
        })
      }
      decisionDismissed.value = false
      return
    }
    showProfile(conversationId, `${learningPrompt.value} ${text}`)
    return
  }

  if (!activeChatId.value) {
    const newChatId = await conversationStore.create()
    await messageStore.sendMessage(newChatId, text, undefined, undefined, undefined, files)
    return
  }

  await messageStore.sendMessage(activeChatId.value, text, undefined, undefined, undefined, files)
}

async function confirmLearningProfile(messageId: string) {
  if (!activeChatId.value || learningPhase.value !== 'profile') return
  messageStore.updateLocalMessage(activeChatId.value, messageId, {
    learningData: { loading: false, confirmed: true, profile: learningProfile.value },
  })
  appendLearningMessage(activeChatId.value, 'user', '确认当前学习画像，请生成学习方案确认稿。')
  learningPhase.value = 'analyzing'
  const documentMessage = appendLearningMessage(activeChatId.value, 'assistant', '', {
    kind: 'learning-document',
    learningData: { loading: true, content: '' },
  })
  currentDocumentMessageId.value = documentMessage.id
  if (learningTimer) window.clearTimeout(learningTimer)
  learningTimer = window.setTimeout(() => {
    confirmationDocument.value = buildLearningDocument()
    if (!activeChatId.value) return
    messageStore.updateLocalMessage(activeChatId.value, documentMessage.id, {
      learningData: { loading: false, content: confirmationDocument.value },
    })
    decisionDismissed.value = false
    learningPhase.value = 'document'
  }, 1050)
}

function updateLearningProfile(messageId: string, profile: LearningProfileData) {
  if (!activeChatId.value || learningPhase.value !== 'profile') return
  learningProfile.value = profile
  messageStore.updateLocalMessage(activeChatId.value, messageId, {
    learningData: { loading: false, confirmed: false, profile },
  })
}

function updateLearningDocument(messageId: string, content: string) {
  if (!activeChatId.value) return
  confirmationDocument.value = content
  messageStore.updateLocalMessage(activeChatId.value, messageId, {
    learningData: { loading: false, content },
  })
}

function regenerateLearningDocument(messageId: string) {
  if (!activeChatId.value || messageId !== currentDocumentMessageId.value) return
  learningPhase.value = 'analyzing'
  decisionDismissed.value = false
  messageStore.updateLocalMessage(activeChatId.value, messageId, {
    learningData: { loading: true, content: '' },
  })
  if (learningTimer) window.clearTimeout(learningTimer)
  learningTimer = window.setTimeout(() => {
    if (!activeChatId.value) return
    confirmationDocument.value = buildLearningDocument()
    messageStore.updateLocalMessage(activeChatId.value, messageId, {
      learningData: { loading: false, content: confirmationDocument.value },
    })
    learningPhase.value = 'document'
  }, 900)
}

function continueEditing() {
  decisionDismissed.value = true
  homeInputRef.value?.setText('')
}

function selectKnowledgeBase(id: number | null) {
  selectedKnowledgeBaseId.value = id
  learningProfile.value.source = availableKnowledgeBases.value.find((item) => item.id === id)?.name || '无'
  knowledgeMenuOpen.value = false
  knowledgeMenuQuery.value = ''
  if (activeChatId.value) void conversationStore.moveToKnowledgeBase(activeChatId.value, id)
}

function handleKnowledgeCreated(id: number) {
  selectKnowledgeBase(id)
  knowledgeCreateOpen.value = false
}

async function confirmLearningPlan() {
  if (!activeChatId.value || learningPhase.value !== 'document') return
  learningPhase.value = 'generating'
  appendLearningMessage(activeChatId.value, 'user', '确认当前学习方案，开始生成学习路径和相关内容。')
  appendLearningMessage(activeChatId.value, 'assistant', '已确认。我正在生成学习路径、练习任务和思维导图…')
  const profile = learningProfile.value
  if (learningTimer) window.clearTimeout(learningTimer)
  learningTimer = window.setTimeout(() => {
    const generated = learningStore.createPlan({
      prompt: confirmationDocument.value || learningPrompt.value,
      libraryId: learningProject.value?.libraryId || selectedKnowledgeBaseId.value || 1,
      projectId: learningProjectId.value,
      targetType: profile.goal,
      preferences: profile.preferences,
      resourceGroups: ['思维导图'],
      period: profile.period,
      foundation: profile.foundation,
      weakPoints: profile.weakPoints.join('、'),
      dailyTime: profile.dailyTime,
      studyDepth: profile.preferences.includes('项目实操') ? '项目实操' : profile.preferences.includes('练习驱动') ? '刷题强化' : '系统学习',
      questionCount: 60,
      supplementalRequirement: profile.extra,
      draftPlanId: learningProjectId.value,
      libraryName: selectedKnowledgeBase.value?.name,
    })
    const mindMap = generated.resources.find((resource) => resource.group === '思维导图')
    if (mindMap) {
      void learningStore.generateResource(generated.id, mindMap.id).catch((error) => {
        console.error('Failed to generate mind map resource:', error)
      })
    }
    void router.push(`/learning/${generated.id}`)
  }, 900)
}

function fillHomePrompt(prompt: string) {
  homeInputRef.value?.setText(prompt)
}

function handleKeyDown(e: KeyboardEvent) {
  if (!e.ctrlKey || !e.shiftKey) return

  if (e.key.toLowerCase() === 'c') {
    e.preventDefault()
    const lastAiMsg = messages.value.filter((m) => m.role === 'assistant').pop()
    if (lastAiMsg) {
      import('@/utils/clipboard').then(({ copyText }) => copyText(lastAiMsg.content))
    }
    return
  }

  if (e.key.toLowerCase() === 'r') {
    e.preventDefault()
    const lastAiMsg = messages.value.filter((m) => m.role === 'assistant').pop()
    if (lastAiMsg && activeChatId.value && !messageStore.isStreaming) {
      const turnId = lastAiMsg.turnId || lastAiMsg.id
      messageStore.regenerate(activeChatId.value, turnId)
    }
  }
}

function onGenerateMindmap(messageId: string, content: string) {
  mindMapContent.value = content
  mindMapTitle.value = ''
  showMindMapPanel.value = true
  mindMapSidebarCollapsed.value = false
}

function onMindMapSaved() {
  libraryResourceStore.addChatGenerated(
    `${mindMapTitle.value.trim() || pageTitle.value}思维导图`,
    currentConversation.value?.knowledgeBaseId ?? null,
  )
  showMindMapPanel.value = false
}

function handleToggleMindMapSidebar() {
  mindMapSidebarCollapsed.value = !mindMapSidebarCollapsed.value
}

onMounted(() => {
  conversationStore.init()
  knowledgeBaseStore.fetchList()
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  if (learningTimer) window.clearTimeout(learningTimer)
  window.removeEventListener('keydown', handleKeyDown)
})

watch(
  [isLearningChat, learningProjectId],
  ([learning, projectId]) => {
    if (!learning) return
    const project = projectId ? learningStore.getPlan(projectId) : null
    if (project?.libraryId) selectedKnowledgeBaseId.value = project.libraryId
  },
  { immediate: true },
)

watch(
  [activeChatId, learningProjectId],
  ([chatId, projectId], previous) => {
    if (previous && chatId === previous[0] && projectId === previous[1]) return
    if (learningTimer) window.clearTimeout(learningTimer)
    learningPhase.value = 'idle'
    currentProfileMessageId.value = ''
    currentDocumentMessageId.value = ''
    learningPrompt.value = ''
    confirmationDocument.value = ''
    decisionDismissed.value = false
    learningProfile.value = emptyProfile()
  },
  { flush: 'sync' },
)

watch(
  messages,
  (items) => {
    if (!isLearningSetupChat.value || learningPhase.value !== 'idle') return
    const lastDocument = [...items].reverse().find((message) => message.kind === 'learning-document')
    if (lastDocument?.learningData) {
      currentDocumentMessageId.value = lastDocument.id
      confirmationDocument.value = lastDocument.learningData.content || ''
      learningPhase.value = lastDocument.learningData.loading ? 'analyzing' : 'document'
      return
    }
    const lastProfile = [...items].reverse().find((message) => message.kind === 'learning-profile')
    if (lastProfile?.learningData) {
      currentProfileMessageId.value = lastProfile.id
      learningProfile.value = lastProfile.learningData.profile || emptyProfile()
      learningPhase.value = lastProfile.learningData.loading ? 'analyzing' : 'profile'
    }
  },
  { immediate: true },
)

watch(
  [isTutorChat, tutorQuestionFromRoute, learningProjectId],
  async ([tutor, question, projectId]) => {
    if (!tutor || !question || !projectId) return
    const requestKey = `${projectId}:${question}`
    if (tutorRequestKey === requestKey) return
    tutorRequestKey = requestKey
    const conversationId = await ensureTutorConversation()
    if (!conversationId) {
      tutorRequestKey = ''
      return
    }
    await router.replace({
      path: `/chat/${conversationId}`,
      query: { learningProjectId: String(projectId), tutor: '1' },
    })
    await sendTutorQuestion(question)
    tutorRequestKey = ''
  },
  { immediate: true },
)
</script>

<template>
  <StudentShell>
    <section class="student-chat">
      <header v-if="!showWelcome" class="student-chat__header">
        <button
          v-if="isTutorChat && learningProject"
          class="tutor-back-button"
          type="button"
          @click="router.push(`/learning/${learningProject.id}`)"
        >
          <AppIcon name="chevron-left" :size="17" />
          返回学习详情
        </button>
        <h1>{{ pageTitle }}</h1>
      </header>

      <div class="student-chat__body">
        <div v-if="showWelcome" class="chat-home">
          <div class="chat-home__main">
            <h1>{{ isTutorChat ? '围绕当前学习项目提问' : isLearningChat ? '想制定怎样的学习计划？' : '我们先从哪里开始呢？' }}</h1>

            <div class="home-action-chips">
              <button
                v-for="action in homePromptActions"
                :key="action.label"
                class="home-action-chip ui-hover-row"
                type="button"
                @click="fillHomePrompt(action.prompt)"
              >
                <AppIcon :name="action.icon" :size="18" />
                <span>{{ action.label }}</span>
              </button>
            </div>
          </div>
        </div>

        <div v-else class="message-container">
          <MessageList
            ref="messageListRef"
            :conversation-id="activeChatId"
            :messages="messages"
            @generate-mindmap="onGenerateMindmap"
            @confirm-learning-profile="confirmLearningProfile"
            @update-learning-profile="updateLearningProfile"
            @update-learning-document="updateLearningDocument"
            @regenerate-learning-document="regenerateLearningDocument"
          />
        </div>

        <div v-if="isLearningSetupChat && confirmationReady && !decisionDismissed" class="learning-decision-wrap">
          <section class="learning-decision">
            <div class="learning-decision__icon"><AppIcon name="sparkle" :size="18" /></div>
            <div>
              <strong>学习方案确认稿已就绪</strong>
              <span>可继续对话或直接编辑确认稿，确认后将生成学习路径与练习。</span>
            </div>
            <div class="learning-decision__actions">
              <button type="button" @click="continueEditing">继续修改</button>
              <button class="primary" type="button" @click="confirmLearningPlan">确认并生成</button>
            </div>
          </section>
        </div>

        <AppInput
          ref="homeInputRef"
          :is-streaming="messageStore.isStreaming"
          placeholder="输入消息，Enter 发送，Shift+Enter 换行"
          @send="onSend"
        >
          <template v-if="isLearningSetupChat && showWelcome" #context>
            <div class="learning-context">
              <button class="learning-context__trigger ui-hover-row" type="button" @click="knowledgeMenuOpen = !knowledgeMenuOpen">
                <AppIcon name="folder" :size="15" />
                <span>{{ selectedKnowledgeBase?.name || '不关联知识库' }}</span>
                <AppIcon name="chevron-down" :size="13" />
              </button>
              <div v-if="knowledgeMenuOpen" class="knowledge-menu ui-menu-panel">
                <label><AppIcon name="search" :size="15" /><input v-model="knowledgeMenuQuery" placeholder="搜索知识库" /></label>
                <button class="ui-menu-item" type="button" :aria-selected="selectedKnowledgeBaseId === null" @click="selectKnowledgeBase(null)">
                  <span class="ui-menu-icon"><AppIcon name="close" :size="16" /></span><span>无</span><AppIcon v-if="selectedKnowledgeBaseId === null" name="check" :size="15" />
                </button>
                <button v-for="item in filteredKnowledgeBases" :key="item.id" class="ui-menu-item" type="button" :aria-selected="selectedKnowledgeBaseId === item.id" @click="selectKnowledgeBase(item.id)">
                  <span class="ui-menu-icon"><AppIcon :name="item.icon" :size="16" /></span><span>{{ item.name }}</span><AppIcon v-if="selectedKnowledgeBaseId === item.id" name="check" :size="15" />
                </button>
                <div class="ui-menu-divider" />
                <button class="knowledge-menu__create ui-menu-item" type="button" @click="knowledgeMenuOpen = false; knowledgeMenuQuery = ''; knowledgeCreateOpen = true">
                  <span class="ui-menu-icon"><AppIcon name="plus" :size="16" /></span><span>新建空白知识库</span>
                </button>
              </div>
            </div>
          </template>
        </AppInput>

        <p v-if="messageStore.errorMessage" class="chat-error">
          {{ messageStore.errorMessage }}
        </p>

        <SegmentPanel
          v-if="!showWelcome"
          :conversation-id="activeChatId"
          :container-ref="messageListContainer"
        />

        <MindMapPanel
          :visible="showMindMapPanel"
          :ai-content="mindMapContent"
          :ai-title="mindMapTitle"
          @close="showMindMapPanel = false"
          @saved="onMindMapSaved"
          @toggle-sidebar="handleToggleMindMapSidebar"
        />

        <button
          v-if="showMindMapPanel && mindMapSidebarCollapsed"
          class="mindmap-fab"
          type="button"
          @click="handleToggleMindMapSidebar"
        >
          <AppIcon name="panel-left-open" :size="20" />
        </button>
      </div>
    </section>

    <LibraryKnowledgeCreateModal
      :open="knowledgeCreateOpen"
      @close="knowledgeCreateOpen = false"
      @created="handleKnowledgeCreated"
    />
  </StudentShell>
</template>

<style scoped>
.student-chat {
  height: 100%;
  min-width: 0;
  background: var(--color-bg);
  color: var(--color-text);
  display: flex;
  flex-direction: column;
  position: relative;
}

.student-chat__header {
  position: relative;
  height: 58px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tutor-back-button {
  position: absolute;
  left: 20px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 0 11px;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
}

.tutor-back-button:hover {
  background: var(--ui-hover-bg);
}

.student-chat__header h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0;
}

.student-chat__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}

.chat-home {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.chat-home__main {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  padding: 8vh 24px 4vh;
}

.chat-home__main h1 {
  margin: 0 0 28px;
  color: var(--color-text);
  font-size: 26px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 0;
  text-align: center;
}

.home-action-chips {
  width: min(760px, 100%);
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.home-action-chip {
  min-height: 64px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  text-align: left;
  font: inherit;
  font-size: 13px;
  line-height: 18px;
  box-shadow: var(--shadow-sm);
  transition: background 0.16s ease, color 0.16s ease, border-color 0.16s ease;
}

.home-action-chip:hover {
  background: var(--color-hover);
  border-color: var(--color-hover-strong);
  color: var(--color-text);
}

.home-action-chip span {
  color: inherit;
  font-weight: 600;
}

.message-container {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  position: relative;
}

.learning-context {
  position: relative;
  min-height: 38px;
  padding: 5px 8px;
  display: flex;
  align-items: center;
  border: 1px solid var(--color-border);
  border-bottom: 0;
  border-radius: 14px 14px 0 0;
  background: var(--color-surface-subtle);
}

.learning-context__trigger {
  max-width: 100%;
  height: 28px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  gap: 7px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}

.learning-context__trigger:hover { background: var(--color-hover); color: var(--color-text); }
.learning-context__trigger span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }

.knowledge-menu {
  position: absolute;
  left: 0;
  bottom: calc(100% + 7px);
  z-index: 30;
  width: min(330px, calc(100vw - 48px));
  max-height: 330px;
  overflow: auto;
}

.knowledge-menu label {
  height: 34px;
  margin-bottom: 6px;
  padding: 0 9px;
  display: flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: var(--color-text-muted);
}

.knowledge-menu label input { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; color: var(--color-text); }
.knowledge-menu > button { height: var(--ui-menu-item-height); }
.knowledge-menu > button span { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.knowledge-menu > button .ui-menu-icon { flex: 0 0 var(--ui-menu-icon-size); }
.knowledge-menu > .knowledge-menu__create { color: var(--color-primary); }

.learning-decision-wrap { width: min(800px, calc(100% - 32px)); margin: 0 auto 10px; }
.learning-decision { padding: 13px 14px; display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 11px; border: 1px solid var(--color-border); border-radius: 13px; background: var(--color-surface); box-shadow: var(--shadow-md); }
.learning-decision__icon { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 9px; background: color-mix(in srgb, var(--color-primary) 12%, transparent); color: var(--color-primary); }
.learning-decision strong, .learning-decision span { display: block; }
.learning-decision strong { margin-bottom: 2px; font-size: 13px; }
.learning-decision span { color: var(--color-text-muted); font-size: 11px; line-height: 17px; }
.learning-decision__actions { display: flex; gap: 7px; }
.learning-decision__actions button { height: 34px; padding: 0 12px; border: 1px solid var(--color-border); border-radius: 8px; background: transparent; color: var(--color-text); cursor: pointer; white-space: nowrap; }
.learning-decision__actions button.primary { border-color: var(--color-primary); background: var(--color-primary); color: var(--color-on-primary); font-weight: 700; }

.chat-error {
  width: min(800px, calc(100% - 32px));
  margin: 0;
  position: absolute;
  left: 50%;
  bottom: 8px;
  z-index: 5;
  transform: translateX(-50%);
  color: var(--color-danger);
  font-size: 12px;
  line-height: 16px;
  text-align: left;
}

.chat-home .home-action-chip:nth-child(1) {
  color: #8b5cf6;
}

.chat-home .home-action-chip:nth-child(2) {
  color: var(--color-text-muted);
}

.chat-home .home-action-chip:nth-child(3) {
  color: var(--color-success);
}

.chat-home .home-action-chip:nth-child(4) {
  color: var(--color-warning);
}

.chat-home .home-action-chip:nth-child(5) {
  color: #7c3aed;
}

@media (max-width: 760px) {
  .chat-home__main {
    padding: 7vh 16px 3vh;
  }

  .chat-home__main h1 {
    font-size: 22px;
    margin-bottom: 22px;
  }

  .home-action-chips {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .learning-decision { grid-template-columns: auto minmax(0, 1fr); }
  .learning-decision__actions { grid-column: 1 / -1; justify-content: flex-end; }
}

.mindmap-fab {
  position: fixed;
  top: 12px;
  right: 12px;
  z-index: 9998;
  width: 44px;
  height: 44px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  box-shadow: var(--shadow-md);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: background 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
}

.mindmap-fab:hover {
  background: var(--color-hover);
  color: var(--color-text);
  box-shadow: var(--shadow-lg);
}
</style>
