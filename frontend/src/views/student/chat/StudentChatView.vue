<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppIcon from '@/components/common/AppIcon.vue'
import AppInput from '@/components/common/AppInput.vue'
import ChatSourceSelector from '@/components/chat/input/ChatSourceSelector.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import SegmentPanel from '@/components/chat/SegmentPanel.vue'
import MessageList from '@/components/chat/message/MessageList.vue'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore } from '@/stores/message'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useLearningStore } from '@/stores/learning'
import { useAuthStore } from '@/stores/auth'
import LibraryKnowledgeCreateModal from '@/components/library/LibraryKnowledgeCreateModal.vue'
import type { LearningProfileData, LearningSetupStateDto } from '@/types/contracts/learning'
import { isMockDataSource } from '@/config/dataSource'
import type { ChatClientAction } from '@/repositories/chat'
import { snapshotLearningProfile } from '@/utils/learningSetup'
import { createRandomId } from '@/utils/randomId'
import type { ConversationId } from '@/types/contracts/conversation'

const conversationStore = useConversationStore()
const messageStore = useMessageStore()
const libraryResourceStore = useLibraryResourceStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const learningStore = useLearningStore()
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const projectId = computed(() => {
  const value = Number(route.query.projectId)
  return Number.isFinite(value) && value > 0 ? value : null
})
const routeConversation = computed(() => {
  const id = typeof route.params.id === 'string' ? route.params.id : ''
  return id ? conversationStore.list.find((item) => String(item.id) === id) ?? null : null
})
const isLearningRoute = computed(() => route.name === 'learning-new' || route.name === 'learning-setup')
const isLearningChat = computed(() => isLearningRoute.value || route.query.learning === '1' || projectId.value !== null)
const isTutorChat = computed(() => projectId.value !== null && (
  route.query.tutor === '1'
  || routeConversation.value?.conversationType === 'learning-tutor'
  || routeConversation.value?.title?.includes('AI 助教')
))
const isLearningSetupChat = computed(() => isLearningChat.value && !isTutorChat.value)
const tutorQuestionFromRoute = computed(() => typeof route.query.tutorQuestion === 'string' ? route.query.tutorQuestion.trim() : '')
const learningProject = computed(() => projectId.value ? learningStore.getPlan(projectId.value) : null)
type LearningPhase = 'idle' | 'analyzing' | 'profile' | 'document' | 'generating'
const learningPhase = ref<LearningPhase>('idle')
const selectedKnowledgeBaseId = ref<number | null>(null)
const generalKnowledgeBaseId = ref<string | null>(null)
const generalSourceAssetIds = ref<string[]>([])
const knowledgeMenuOpen = ref(false)
const knowledgeMenuQuery = ref('')
const knowledgeCreateOpen = ref(false)
const currentProfileMessageId = ref('')
const currentDocumentMessageId = ref('')
const learningPrompt = ref('')
const confirmationDocument = ref('')
const decisionDismissed = ref(false)
const retryAction = ref<null | (() => void | Promise<void>)>(null)
const pendingClientAction = ref<ChatClientAction | undefined>()
let learningTimer: number | undefined
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
const learningMediaAssetIds = ref<string[]>([])
const learningSourceResourceIds = ref<string[]>([])
const learningUploadedFileNames = ref<string[]>([])
const learningConfirmationResourceId = ref<string | null>(null)
const learningSetupId = ref<string>(createRandomId('learning-setup'))
const completedLearningSetupProjectIds = new Set<number>()
let restoringLearningSetup = false

type LearningSetupSessionState = LearningSetupStateDto
let learningSetupPersistTimer: number | undefined
const pendingLearningSetups = new Map<number, LearningSetupSessionState>()
let learningSetupPersistence = Promise.resolve()

function isGeneratedLearningProject(activeProjectId = projectId.value) {
  if (!activeProjectId) return false
  const status = learningStore.getPlan(activeProjectId)?.status
  return status === '已生成' || status === '进行中' || status === '已完成'
}

function learningSetupStorageKey(activeProjectId = projectId.value) {
  return activeProjectId ? `examinsight.learning.chat-setup.v1.${activeProjectId}` : null
}

function persistLearningSetup(activeProjectId = projectId.value) {
  const key = learningSetupStorageKey(activeProjectId)
  if (!key || !activeProjectId || restoringLearningSetup || completedLearningSetupProjectIds.has(activeProjectId)) return
  const state: LearningSetupSessionState = {
    setupId: learningSetupId.value,
    knowledgeBaseId: selectedKnowledgeBaseId.value,
    prompt: learningPrompt.value,
    profile: snapshotLearningProfile(learningProfile.value),
    mediaAssetIds: [...learningMediaAssetIds.value],
    sourceResourceIds: [...learningSourceResourceIds.value],
    uploadedFileNames: [...learningUploadedFileNames.value],
    confirmationResourceId: learningConfirmationResourceId.value,
    confirmationDocument: confirmationDocument.value,
    phase: learningPhase.value,
    profileMessageId: currentProfileMessageId.value,
    documentMessageId: currentDocumentMessageId.value,
    updatedAt: new Date().toISOString(),
  }
  sessionStorage.setItem(key, JSON.stringify(state))
  pendingLearningSetups.set(activeProjectId, state)
  if (learningSetupPersistTimer) window.clearTimeout(learningSetupPersistTimer)
  learningSetupPersistTimer = window.setTimeout(() => {
    learningSetupPersistTimer = undefined
    void flushLearningSetupPersistence()
  }, 300)
}

function applyLearningSetupState(state: Partial<LearningSetupSessionState>) {
  restoringLearningSetup = true
  try {
    if (typeof state.setupId === 'string' && state.setupId) learningSetupId.value = state.setupId
    if (state.knowledgeBaseId === null || typeof state.knowledgeBaseId === 'number') selectedKnowledgeBaseId.value = state.knowledgeBaseId
    if (typeof state.prompt === 'string') learningPrompt.value = state.prompt
    if (state.profile) learningProfile.value = state.profile
    if (Array.isArray(state.mediaAssetIds)) learningMediaAssetIds.value = state.mediaAssetIds
    if (Array.isArray(state.sourceResourceIds)) learningSourceResourceIds.value = state.sourceResourceIds
    if (Array.isArray(state.uploadedFileNames)) learningUploadedFileNames.value = state.uploadedFileNames
    if (typeof state.confirmationResourceId === 'string' || state.confirmationResourceId === null) {
      learningConfirmationResourceId.value = state.confirmationResourceId ?? null
    }
    if (typeof state.confirmationDocument === 'string') confirmationDocument.value = state.confirmationDocument
    if (state.phase === 'generating') learningPhase.value = 'generating'
    else if (confirmationDocument.value) learningPhase.value = 'document'
    else if (state.phase === 'profile' || state.phase === 'analyzing' || state.phase === 'document') learningPhase.value = 'profile'
    else learningPhase.value = 'idle'
    if (typeof state.profileMessageId === 'string') currentProfileMessageId.value = state.profileMessageId
    if (typeof state.documentMessageId === 'string') currentDocumentMessageId.value = state.documentMessageId
  } finally {
    restoringLearningSetup = false
  }
}

function flushLearningSetupPersistence() {
  const pending = [...pendingLearningSetups.entries()]
  pendingLearningSetups.clear()
  if (!pending.length) return learningSetupPersistence
  learningSetupPersistence = learningSetupPersistence
    .catch(() => undefined)
    .then(async () => {
      for (const [projectId, state] of pending) {
        try {
          await learningStore.saveLearningSetupState(projectId, state)
        } catch (error) {
          learningStore.errorMessage = error instanceof Error ? `学习创建状态同步失败：${error.message}` : '学习创建状态同步失败'
        }
      }
    })
  return learningSetupPersistence
}

async function restoreLearningSetup(activeProjectId = projectId.value) {
  const key = learningSetupStorageKey(activeProjectId)
  if (!key || !activeProjectId) return false
  if (isGeneratedLearningProject(activeProjectId)) {
    sessionStorage.removeItem(key)
    pendingLearningSetups.delete(activeProjectId)
    completedLearningSetupProjectIds.add(activeProjectId)
    return false
  }
  const raw = sessionStorage.getItem(key)
  let restored = false
  let localState: Partial<LearningSetupSessionState> | null = null
  if (raw) {
    try {
      localState = JSON.parse(raw) as Partial<LearningSetupSessionState>
      applyLearningSetupState(localState)
      restored = true
    } catch {
      sessionStorage.removeItem(key)
    }
  }
  try {
    const remote = await learningStore.getLearningSetupState(activeProjectId)
    if (remote) {
      const remoteTime = Date.parse(remote.updatedAt || '') || 0
      const localTime = Date.parse(localState?.updatedAt || '') || 0
      if (!localState || remoteTime >= localTime) {
        applyLearningSetupState(remote)
        sessionStorage.setItem(key, JSON.stringify(remote))
      } else {
        pendingLearningSetups.set(activeProjectId, localState as LearningSetupSessionState)
        void flushLearningSetupPersistence()
      }
      restored = true
    }
  } catch (error) {
    if (!restored) throw error
  }
  return restored
}

async function clearLearningSetup(activeProjectId = projectId.value) {
  const key = learningSetupStorageKey(activeProjectId)
  if (learningSetupPersistTimer) window.clearTimeout(learningSetupPersistTimer)
  learningSetupPersistTimer = undefined
  if (activeProjectId) pendingLearningSetups.delete(activeProjectId)
  await learningSetupPersistence.catch(() => undefined)
  if (key) sessionStorage.removeItem(key)
  if (activeProjectId) {
    completedLearningSetupProjectIds.add(activeProjectId)
    try {
      await learningStore.removeLearningSetupState(activeProjectId)
    } catch (error) {
      learningStore.errorMessage = error instanceof Error ? `清理学习创建状态失败：${error.message}` : '清理学习创建状态失败'
    }
  }
}

async function requestLearningProfile(text: string) {
  const knowledgeBase = selectedKnowledgeBase.value
  return learningStore.generateLearningProfile({
    conversationId: activeLearningChatId.value,
    knowledgeBaseId: selectedKnowledgeBaseId.value,
    text,
    currentProfile: learningProfile.value,
    source: knowledgeBase?.name || '无',
    subject: knowledgeBase?.name,
    supplementalRequirement: learningProfile.value.extra,
    mediaAssetIds: learningMediaAssetIds.value,
  })
}

async function requestLearningConfirmation() {
  const result = await learningStore.generateLearningConfirmation({
    setupId: learningSetupId.value,
    conversationId: activeLearningChatId.value,
    knowledgeBaseId: selectedKnowledgeBaseId.value,
    goal: learningPrompt.value || learningProfile.value.goal,
    profile: learningProfile.value,
    uploadedFileNames: learningUploadedFileNames.value,
    mediaAssetIds: learningMediaAssetIds.value,
    projectId: projectId.value,
    confirmationResourceId: learningConfirmationResourceId.value,
    clientRequestId: createRandomId('learning-confirmation'),
  })
  learningConfirmationResourceId.value = result.resourceId
  learningSourceResourceIds.value = [...new Set([...learningSourceResourceIds.value, result.resourceId])]
  persistLearningSetup()
  return result.content
}

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
const activeLearningChatId = computed<number | null>(() => {
  if (!isLearningChat.value) return null
  const numeric = Number(activeChatId.value)
  return Number.isFinite(numeric) && numeric > 0 ? numeric : null
})

async function loadConversationMessages(id: ConversationId) {
  const loaded = await messageStore.ensureLoaded(id)
  if (!loaded) {
    retryAction.value = () => loadConversationMessages(id)
    return
  }
  if (isLearningSetupChat.value) {
    await restoreLearningSetup(projectId.value)
    const learningConversationId = Number(id)
    if (Number.isFinite(learningConversationId) && learningConversationId > 0) {
      restoreLearningCards(learningConversationId)
    }
  }
  const autoMsgKey = `chat_auto_msg_${id}`
  const autoMsgStr = sessionStorage.getItem(autoMsgKey)
  if (!autoMsgStr) return

  try {
    const autoMsg = JSON.parse(autoMsgStr)
    sessionStorage.removeItem(autoMsgKey)
    await messageStore.sendMessage(id, autoMsg.message)
  } catch (err) {
    console.error('Failed to parse auto message:', err)
    sessionStorage.removeItem(autoMsgKey)
  }
}

watch(
  activeChatId,
  (id) => {
    messageStore.clearError()
    retryAction.value = null
    if (id) void loadConversationMessages(id)
  },
  { immediate: true },
)

const currentConversation = computed(() => {
  if (!activeChatId.value) return null
  return conversationStore.list.find(
    (item) => String(item.id) === String(activeChatId.value),
  ) || null
})

const pageTitle = computed(() => currentConversation.value?.title || '新对话')

const messages = computed(() => {
  const conversationId = activeChatId.value
  if (!conversationId) return []
  const key = String(conversationId)
  const allMsgs = messageStore.byConversation[key] || []

  const filtered = allMsgs.filter((m) => {
    if (!m.turnId) return true

    const activeQ = messageStore.getActiveQVersion(conversationId, m.turnId)
    const mQ = m.qVersion ?? 0
    const mA = m.aVersion ?? 0

    if (m.role === 'user') {
      return mQ === activeQ
    }
    if (m.role === 'assistant') {
      const activeA = messageStore.getActiveAVersion(conversationId, m.turnId, activeQ)
      return mQ === activeQ && mA === activeA
    }
    return true
  })

  const turnMinTime: Record<string, number> = {}
  for (const m of allMsgs) {
    const previousTime = m.turnId ? turnMinTime[m.turnId] : undefined
    if (m.turnId && (previousTime === undefined || m.createTime < previousTime)) {
      turnMinTime[m.turnId] = m.createTime
    }
  }

  return filtered.sort((a, b) => {
    const timeA = a.turnId ? (turnMinTime[a.turnId] ?? a.createTime) : a.createTime
    const timeB = b.turnId ? (turnMinTime[b.turnId] ?? b.createTime) : b.createTime

    if (timeA !== timeB) return timeA - timeB
    if (a.role !== b.role) return a.role === 'user' ? -1 : 1
    return a.createTime - b.createTime
  })
})

const failedTurnId = computed(() => {
  const failed = [...messages.value].reverse().find((message) => message.role === 'assistant' && message.errorMsg)
  return failed?.turnId || null
})
const canRetry = computed(() => Boolean(retryAction.value || (activeChatId.value && failedTurnId.value)))

function reportFlowError(error: unknown, fallback: string, retry?: () => void | Promise<void>) {
  retryAction.value = retry ?? null
  messageStore.reportError(error, fallback)
}

function retryFailedAction() {
  const action = retryAction.value
  retryAction.value = null
  messageStore.clearError()
  if (action) {
    void action()
    return
  }
  if (activeChatId.value && failedTurnId.value) {
    void messageStore.regenerate(activeChatId.value, failedTurnId.value)
  }
}

function dismissError() {
  retryAction.value = null
  messageStore.clearError()
}

const showWelcome = computed(() => !activeChatId.value || messages.value.length === 0)
const confirmationReady = computed(() => {
  if (learningPhase.value !== 'document' || !currentDocumentMessageId.value) return false
  const documentMessage = messages.value.find((message) => message.id === currentDocumentMessageId.value)
  return Boolean(documentMessage?.kind === 'learning-document' && !documentMessage.learningData?.loading && documentMessage.learningData?.content)
})

const messageListRef = ref<InstanceType<typeof MessageList> | null>(null)
const homeInputRef = ref<InstanceType<typeof AppInput> | null>(null)

const messageListContainer = computed(() => messageListRef.value?.scrollContainer ?? null)

type HomePromptAction = {
  icon: string
  label: string
  prompt?: string
  action?: 'presentation' | 'spreadsheet' | 'image' | 'mindmap'
}

const homePromptActions: HomePromptAction[] = [
  { icon: 'edit', label: '撰写或编辑', prompt: '帮我撰写或润色这段内容：' },
  { icon: 'image', label: '生成图片', prompt: '帮我生成一张适合学习资料使用的图片，主题是：', action: 'image' },
  { icon: 'sparkle', label: '生成 PPT', action: 'presentation' },
  { icon: 'mindmap', label: '生成思维导图', prompt: '帮我生成一个思维导图，主题是：', action: 'mindmap' },
]

function appendLearningMessage(conversationId: number, role: 'user' | 'assistant', content: string, extra: Record<string, unknown> = {}) {
  return messageStore.appendLocalMessage(conversationId, { role, content, ...extra })
}

function restoreLearningCards(conversationId: number) {
  const items = messageStore.getMessages(conversationId)
  if (items.some((message) => message.kind === 'learning-profile' || message.kind === 'learning-document')) return
  if (!items.length && learningPrompt.value) appendLearningMessage(conversationId, 'user', learningPrompt.value)
  if (confirmationDocument.value) {
    const message = appendLearningMessage(conversationId, 'assistant', '', {
      kind: 'learning-document',
      learningData: { loading: false, content: confirmationDocument.value, resourceId: learningConfirmationResourceId.value },
    })
    currentDocumentMessageId.value = message.id
    learningPhase.value = 'document'
    return
  }
  if (learningPhase.value === 'profile') {
    const message = appendLearningMessage(conversationId, 'assistant', '', {
      kind: 'learning-profile',
      learningData: { loading: false, confirmed: false, profile: learningProfile.value },
    })
    currentProfileMessageId.value = message.id
  }
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
  const storedId = isMockDataSource ? Number(sessionStorage.getItem(storageKey)) : 0
  const title = `${project.title} · AI 助教`
  const current = conversationStore.list.find((item) => item.id === activeChatId.value)
  let matched = current?.id === storedId || (current?.projectId === project.id && current?.title === title)
    ? current
    : conversationStore.list.find((item) => item.id === storedId)
      ?? conversationStore.list.find((item) => item.projectId === project.id && item.title === title)
  if (isMockDataSource && !matched && Number.isFinite(storedId) && storedId > 0) {
    conversationStore.restoreLearningConversation(storedId, project.id, project.title, project.knowledgeBaseId ?? null, title)
    matched = conversationStore.list.find((item) => item.id === storedId)
  }

  const rawConversationId = matched?.id ?? await conversationStore.create({
    knowledgeBaseId: project.knowledgeBaseId ?? null,
    title,
    navigate: false,
    projectId: project.id,
    projectName: project.title,
    conversationType: 'learning-tutor',
    localOnly: true,
  })
  const conversationId = Number(rawConversationId)
  if (!Number.isFinite(conversationId) || conversationId <= 0) {
    throw new Error('学习助教会话初始化失败')
  }
  if (isMockDataSource) sessionStorage.setItem(storageKey, String(conversationId))
  conversationStore.linkLearningProject(conversationId, project.id, project.title, 'learning-tutor')
  return conversationId
}

async function sendTutorQuestion(question: string, files?: File[], clientAction?: ChatClientAction) {
  const nextQuestion = question.trim()
  if (!nextQuestion && !files?.length) return
  const conversationId = await ensureTutorConversation()
  if (!conversationId || !learningProject.value) return
  if (activeLearningChatId.value !== conversationId) {
    await router.replace({
      path: `/chat/${conversationId}`,
      query: { projectId: String(learningProject.value.id), tutor: '1' },
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
      tutorContext: isMockDataSource ? buildTutorContext() : undefined,
      tutorSource: {
        projectId: learningProject.value.id,
        projectTitle: learningProject.value.title,
        page: 'chat',
        label: '完整对话',
      },
      clientAction,
      projectId: learningProject.value.id,
    },
  )
  conversationStore.linkLearningProject(conversationId, learningProject.value.id, learningProject.value.title, 'learning-tutor')
}

async function ensureLearningConversation(text: string) {
  let activeProjectId = projectId.value
  if (!activeProjectId) {
    const draft = await learningStore.createDraftPlan({
      title: text.slice(0, 22) || '新学习项目',
      knowledgeBaseId: selectedKnowledgeBaseId.value,
      knowledgeBaseName: selectedKnowledgeBase.value?.name,
    })
    activeProjectId = draft.id
  }
  let conversationId = activeLearningChatId.value
  if (!conversationId) {
    const rawConversationId = await conversationStore.create({
      knowledgeBaseId: selectedKnowledgeBaseId.value,
      title: `${learningProject.value?.title || text.slice(0, 22) || '新学习项目'} · 方案制定`,
      projectId: activeProjectId,
      projectName: learningStore.getPlan(activeProjectId)?.title || text.slice(0, 22),
      conversationType: 'learning-setup',
      localOnly: true,
      navigate: false,
    })
    const createdConversationId = Number(rawConversationId)
    if (!Number.isFinite(createdConversationId) || createdConversationId <= 0) {
      throw new Error('学习方案会话初始化失败')
    }
    conversationId = createdConversationId
  }
  const projectName = learningStore.getPlan(activeProjectId)?.title || text.slice(0, 22) || '新学习项目'
  conversationStore.linkLearningProject(conversationId, activeProjectId, projectName, 'learning-setup')
  persistLearningSetup(activeProjectId)
  await router.replace({ path: `/learning/setup/${conversationId}`, query: { projectId: String(activeProjectId) } })
  await restoreLearningSetup(activeProjectId)
  return conversationId
}

function showProfile(conversationId: number, text: string, existingMessageId?: string) {
  learningPhase.value = 'analyzing'
  const loadingPatch = {
    kind: 'learning-profile' as const,
    content: '',
    errorMsg: undefined,
    learningData: { loading: true, confirmed: false, profile: learningProfile.value },
  }
  const profileMessage = existingMessageId
    ? messageStore.getMessages(conversationId).find((message) => message.id === existingMessageId)
    : undefined
  const messageId = profileMessage?.id || appendLearningMessage(conversationId, 'assistant', '', loadingPatch).id
  if (profileMessage) messageStore.updateLocalMessage(conversationId, messageId, loadingPatch)
  currentProfileMessageId.value = messageId
  if (learningTimer) window.clearTimeout(learningTimer)
  learningTimer = window.setTimeout(async () => {
    try {
      const result = await requestLearningProfile(text)
      learningProfile.value = result.profile
      messageStore.updateLocalMessage(conversationId, messageId, {
        learningData: { loading: false, confirmed: false, profile: learningProfile.value },
      })
      if (activeLearningChatId.value === conversationId) {
        learningPhase.value = 'profile'
        persistLearningSetup()
      }
    } catch (error) {
      messageStore.updateLocalMessage(conversationId, messageId, {
        kind: undefined,
        content: '学习画像生成失败，请重试。',
        learningData: undefined,
        errorMsg: error instanceof Error ? error.message : '学习画像生成失败',
      })
      if (activeLearningChatId.value === conversationId) {
        learningPhase.value = 'idle'
        reportFlowError(error, '学习画像生成失败', () => showProfile(conversationId, text, messageId))
      }
    }
  }, 850)
}

async function onSend(text: string, files?: File[], complete?: (success?: boolean) => void) {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    complete?.(false)
    return
  }
  retryAction.value = null
  messageStore.clearError()
  let succeeded = false
  try {
  if (files?.length && isLearningSetupChat.value) {
    const uploaded = await libraryResourceStore.uploadFiles(
      files,
      'learning',
      projectId.value,
      selectedKnowledgeBaseId.value,
    )
    const nextMediaIds = uploaded.flatMap((item) => (
      item.resourceId.startsWith('media:') && typeof item.externalKey === 'string'
        ? [item.externalKey]
        : []
    ))
    learningMediaAssetIds.value = [...new Set([...learningMediaAssetIds.value, ...nextMediaIds])]
    learningSourceResourceIds.value = [...new Set([...learningSourceResourceIds.value, ...uploaded.map((item) => item.resourceId)])]
    learningUploadedFileNames.value = [...new Set([...learningUploadedFileNames.value, ...files.map((file) => file.name)])]
  } else if (files?.length && isMockDataSource) {
    libraryResourceStore.addFiles(
      files,
      'chat',
      null,
      typeof currentConversation.value?.knowledgeBaseId === 'number'
        ? currentConversation.value.knowledgeBaseId
        : null,
    )
  }

  if (isTutorChat.value) {
    await sendTutorQuestion(text, files, pendingClientAction.value)
    succeeded = !messageStore.errorMessage
    return
  }

  if (isLearningSetupChat.value) {
    const conversationId = await ensureLearningConversation(text)
    appendLearningMessage(conversationId, 'user', text, {
      files: files?.map((file) => ({ name: file.name, type: file.type, size: file.size })),
    })
    learningPrompt.value ||= text
    if (learningPhase.value === 'document') {
      learningProfile.value = (await requestLearningProfile(`${learningPrompt.value} ${text}`)).profile
      appendLearningMessage(conversationId, 'assistant', '好的，我已根据你的补充更新学习画像和方案确认稿。')
      confirmationDocument.value = await requestLearningConfirmation()
      if (currentDocumentMessageId.value) {
        messageStore.updateLocalMessage(conversationId, currentDocumentMessageId.value, {
          learningData: { loading: false, content: confirmationDocument.value, resourceId: learningConfirmationResourceId.value },
        })
      }
      decisionDismissed.value = false
      persistLearningSetup()
      succeeded = true
      return
    }
    persistLearningSetup()
    showProfile(conversationId, `${learningPrompt.value} ${text}`)
    succeeded = true
    return
  }

  if (!activeChatId.value) {
    const selectedSourceIds = [...generalSourceAssetIds.value]
    const selectedKnowledgeBase = generalKnowledgeBaseId.value
    const newChatId = await conversationStore.create({
      knowledgeBaseId: selectedKnowledgeBase,
    })
    await messageStore.sendMessage(newChatId, text, undefined, undefined, undefined, files, false, {
      clientAction: pendingClientAction.value,
      projectId: projectId.value,
      runtime: 'v2-general',
      sourceAssetExternalIds: selectedSourceIds,
    })
    succeeded = !messageStore.errorMessage
    return
  }

  if (currentConversation.value?.conversationType === 'general'
    && String(currentConversation.value.knowledgeBaseId ?? '') !== String(generalKnowledgeBaseId.value ?? '')) {
    await conversationStore.moveToKnowledgeBase(activeChatId.value, generalKnowledgeBaseId.value)
  }
  await messageStore.sendMessage(activeChatId.value, text, undefined, undefined, undefined, files, false, {
    clientAction: pendingClientAction.value,
    projectId: projectId.value,
    runtime: 'v2-general',
    sourceAssetExternalIds: generalSourceAssetIds.value,
  })
  succeeded = !messageStore.errorMessage
  } catch (error) {
    reportFlowError(error, '消息发送失败', () => onSend(text, files))
  } finally {
    if (succeeded) pendingClientAction.value = undefined
    complete?.(succeeded)
  }
}

async function confirmLearningProfile(messageId: string) {
  if (!activeLearningChatId.value || learningPhase.value !== 'profile') return
  const conversationId = activeLearningChatId.value
  messageStore.updateLocalMessage(conversationId, messageId, {
    learningData: { loading: false, confirmed: true, profile: learningProfile.value },
  })
  appendLearningMessage(conversationId, 'user', '确认当前学习画像，请生成学习方案确认稿。')
  learningPhase.value = 'analyzing'
  const documentMessage = appendLearningMessage(conversationId, 'assistant', '', {
    kind: 'learning-document',
    learningData: { loading: true, content: '' },
  })
  currentDocumentMessageId.value = documentMessage.id
  persistLearningSetup()
  if (learningTimer) window.clearTimeout(learningTimer)
  learningTimer = window.setTimeout(async () => {
    try {
      confirmationDocument.value = await requestLearningConfirmation()
      messageStore.updateLocalMessage(conversationId, documentMessage.id, {
        learningData: { loading: false, content: confirmationDocument.value, resourceId: learningConfirmationResourceId.value },
      })
      if (activeLearningChatId.value !== conversationId) return
      decisionDismissed.value = false
      learningPhase.value = 'document'
      persistLearningSetup()
    } catch (error) {
      messageStore.updateLocalMessage(conversationId, documentMessage.id, {
        learningData: { loading: false, content: '' },
      })
      if (activeLearningChatId.value !== conversationId) return
      learningPhase.value = 'profile'
      reportFlowError(error, '学习方案确认稿生成失败', () => regenerateLearningDocument(documentMessage.id))
    }
  }, 1050)
}

function updateLearningProfile(messageId: string, profile: LearningProfileData) {
  if (!activeLearningChatId.value || learningPhase.value !== 'profile') return
  learningProfile.value = profile
  messageStore.updateLocalMessage(activeLearningChatId.value, messageId, {
    learningData: { loading: false, confirmed: false, profile },
  })
  persistLearningSetup()
}

function updateLearningDocument(messageId: string, content: string) {
  if (!activeLearningChatId.value) return
  confirmationDocument.value = content
  messageStore.updateLocalMessage(activeLearningChatId.value, messageId, {
    learningData: { loading: false, content, resourceId: learningConfirmationResourceId.value },
  })
  persistLearningSetup()
}

function regenerateLearningDocument(messageId: string) {
  if (!activeLearningChatId.value || messageId !== currentDocumentMessageId.value) return
  const conversationId = activeLearningChatId.value
  learningPhase.value = 'analyzing'
  decisionDismissed.value = false
  messageStore.updateLocalMessage(conversationId, messageId, {
    learningData: { loading: true, content: '' },
  })
  if (learningTimer) window.clearTimeout(learningTimer)
  learningTimer = window.setTimeout(async () => {
    try {
      confirmationDocument.value = await requestLearningConfirmation()
      messageStore.updateLocalMessage(conversationId, messageId, {
        learningData: { loading: false, content: confirmationDocument.value, resourceId: learningConfirmationResourceId.value },
      })
      if (activeLearningChatId.value !== conversationId) return
      learningPhase.value = 'document'
      persistLearningSetup()
    } catch (error) {
      messageStore.updateLocalMessage(conversationId, messageId, {
        learningData: { loading: false, content: '' },
      })
      if (activeLearningChatId.value !== conversationId) return
      learningPhase.value = 'profile'
      reportFlowError(error, '学习方案确认稿生成失败', () => regenerateLearningDocument(messageId))
    }
  }, 900)
}

function continueEditing() {
  decisionDismissed.value = true
  homeInputRef.value?.setText('')
  persistLearningSetup()
}

function selectKnowledgeBase(id: number | null) {
  selectedKnowledgeBaseId.value = id
  learningProfile.value.source = availableKnowledgeBases.value.find((item) => item.id === id)?.name || '无'
  knowledgeMenuOpen.value = false
  knowledgeMenuQuery.value = ''
  if (activeChatId.value) void conversationStore.moveToKnowledgeBase(activeChatId.value, id)
  persistLearningSetup()
}

function handleKnowledgeCreated(id: number) {
  selectKnowledgeBase(id)
  knowledgeCreateOpen.value = false
}

function scheduleLearningPlanGeneration(conversationId: number) {
  learningPhase.value = 'generating'
  persistLearningSetup()
  if (learningTimer) window.clearTimeout(learningTimer)
  learningTimer = window.setTimeout(async () => {
    try {
      await flushLearningSetupPersistence()
      const profile = learningProfile.value
      const draftProjectId = projectId.value
      const generated = await learningStore.createPlan({
        prompt: confirmationDocument.value || learningPrompt.value,
        knowledgeBaseId: selectedKnowledgeBaseId.value,
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
        sourceResourceIds: learningSourceResourceIds.value,
        mediaAssetIds: learningMediaAssetIds.value,
        confirmationResourceId: learningConfirmationResourceId.value,
        draftPlanId: draftProjectId,
        knowledgeBaseName: selectedKnowledgeBase.value?.name,
      })
      await clearLearningSetup(draftProjectId)
      if (activeLearningChatId.value === conversationId) await router.push(`/learning/${generated.id}`)
    } catch (error) {
      if (activeLearningChatId.value !== conversationId) return
      learningPhase.value = 'document'
      reportFlowError(error, '学习方案生成失败', () => scheduleLearningPlanGeneration(conversationId))
    }
  }, 900)
}

async function resumeLearningPlanGeneration() {
  const draftProjectId = projectId.value
  if (!isLearningSetupChat.value || !draftProjectId) return
  learningPhase.value = 'generating'
  try {
    const generated = await learningStore.resumePlanGeneration(draftProjectId)
    if (!generated) {
      learningPhase.value = confirmationDocument.value ? 'document' : learningPrompt.value ? 'profile' : 'idle'
      return
    }
    await clearLearningSetup(draftProjectId)
    await router.replace(`/learning/${generated.id}`)
  } catch (error) {
    learningPhase.value = confirmationDocument.value ? 'document' : 'profile'
    persistLearningSetup(draftProjectId)
    reportFlowError(error, '学习方案生成恢复失败', resumeLearningPlanGeneration)
  }
}

function confirmLearningPlan() {
  if (!activeLearningChatId.value || learningPhase.value !== 'document') return
  if (isGeneratedLearningProject()) {
    void clearLearningSetup().then(() => router.replace(`/learning/${projectId.value}`))
    return
  }
  const conversationId = activeLearningChatId.value
  decisionDismissed.value = true
  appendLearningMessage(conversationId, 'user', '确认当前学习方案，开始生成学习路径和相关内容。')
  appendLearningMessage(conversationId, 'assistant', '已确认。我正在生成学习路径、练习任务和思维导图…')
  scheduleLearningPlanGeneration(conversationId)
}

function fillHomePrompt(prompt: string, clientAction?: ChatClientAction) {
  pendingClientAction.value = clientAction
  homeInputRef.value?.setText(prompt)
}

async function runHomePromptAction(action: HomePromptAction) {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  if (!action.action) {
    if (action.prompt) fillHomePrompt(action.prompt)
    return
  }

  if (action.action === 'spreadsheet') {
    fillHomePrompt('请根据以下要求、当前对话和我上传的文件直接生成电子表格：', 'spreadsheet.create')
    return
  }

  if (action.action === 'image') {
    fillHomePrompt(action.prompt || '帮我生成一张图片，主题是：', 'image.create')
    return
  }

  if (action.action === 'mindmap') {
    fillHomePrompt(action.prompt || '帮我生成一个思维导图，主题是：', 'mindmap.create')
    return
  }

  if (messageStore.isStreaming) return
  try {
    const conversationId = activeChatId.value ?? await conversationStore.create({
      knowledgeBaseId: selectedKnowledgeBaseId.value,
      title: 'PPT 创作',
      navigate: false,
      projectId: projectId.value,
      projectName: learningProject.value?.title,
      conversationType: isTutorChat.value ? 'learning-tutor' : 'general',
    })
    if (String(activeChatId.value ?? '') !== String(conversationId)) {
      const query = projectId.value ? { projectId: String(projectId.value) } : undefined
      await router.replace({ path: `/chat/${conversationId}`, query })
    }
    await messageStore.sendMessage(
      conversationId,
      '生成 PPT',
      undefined,
      undefined,
      undefined,
      undefined,
      false,
      {
        clientAction: 'presentation.create',
        projectId: projectId.value,
      },
    )
  } catch (error) {
    reportFlowError(error, 'PPT 创建入口加载失败', () => runHomePromptAction(action))
  }
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

async function initializeLearningChat() {
  if (!authStore.isAuthed) return
  try {
    await Promise.all([knowledgeBaseStore.fetchList(), learningStore.fetchPlans()])
    if (!isLearningSetupChat.value) return
    const project = projectId.value ? learningStore.getPlan(projectId.value) : null
    if (project?.knowledgeBaseId !== null && project?.knowledgeBaseId !== undefined) {
      selectedKnowledgeBaseId.value = project.knowledgeBaseId
    }
    if (projectId.value && isGeneratedLearningProject(projectId.value)) {
      await clearLearningSetup(projectId.value)
      await router.replace(`/learning/${projectId.value}`)
      return
    }
    await restoreLearningSetup(projectId.value)
    if (activeLearningChatId.value) restoreLearningCards(activeLearningChatId.value)
    await resumeLearningPlanGeneration()
  } catch (error) {
    reportFlowError(error, '初始化学习方案对话失败', initializeLearningChat)
  }
}

onMounted(() => {
  authStore.init()
  conversationStore.init()
  void initializeLearningChat()
  window.addEventListener('keydown', handleKeyDown)
})

watch(
  [currentConversation, () => route.query.knowledgeBaseId, () => route.query.sourceAssetIds, isLearningChat],
  ([conversation, requestedKnowledgeBaseId, requestedAssetIds, learning]) => {
    if (learning) return
    generalKnowledgeBaseId.value = typeof requestedKnowledgeBaseId === 'string'
      ? requestedKnowledgeBaseId
      : typeof conversation?.knowledgeBaseId === 'string'
        ? conversation.knowledgeBaseId
        : null
    const rawAssetIds = Array.isArray(requestedAssetIds)
      ? requestedAssetIds.join(',')
      : typeof requestedAssetIds === 'string'
        ? requestedAssetIds
        : ''
    generalSourceAssetIds.value = [...new Set(
      rawAssetIds.split(',').map((value) => value.trim()).filter(Boolean),
    )].slice(0, 20)
  },
  { immediate: true },
)

let routeIntentHandled = false
watch(
  [() => route.name === 'spreadsheet-new' ? 'spreadsheet' : route.query.intent, homeInputRef],
  ([intent, input]) => {
    if (routeIntentHandled || intent !== 'spreadsheet' || !input) return
    routeIntentHandled = true
    fillHomePrompt('请根据以下要求、当前对话和我上传的文件直接生成电子表格：', 'spreadsheet.create')
  },
  { immediate: true, flush: 'post' },
)

onUnmounted(() => {
  if (learningTimer) window.clearTimeout(learningTimer)
  persistLearningSetup()
  void flushLearningSetupPersistence()
  window.removeEventListener('keydown', handleKeyDown)
})

watch(
  [isLearningChat, projectId, () => route.query.knowledgeBaseId, availableKnowledgeBases],
  ([learning, activeProjectId, requestedKnowledgeBaseId]) => {
    if (!learning) return
    const project = activeProjectId ? learningStore.getPlan(activeProjectId) : null
    if (project?.knowledgeBaseId !== null && project?.knowledgeBaseId !== undefined) {
      selectedKnowledgeBaseId.value = project.knowledgeBaseId
      return
    }
    const requestedId = Number(requestedKnowledgeBaseId)
    if (!activeProjectId && availableKnowledgeBases.value.some((item) => item.id === requestedId)) {
      selectedKnowledgeBaseId.value = requestedId
    }
  },
  { immediate: true },
)

watch(
  [activeChatId, projectId],
  ([chatId, activeProjectId], previous) => {
    if (previous && chatId === previous[0] && activeProjectId === previous[1]) return
    const previousProjectId = previous?.[1]
    if (previousProjectId) persistLearningSetup(previousProjectId)
    if (learningTimer) window.clearTimeout(learningTimer)
    learningPhase.value = 'idle'
    currentProfileMessageId.value = ''
    currentDocumentMessageId.value = ''
    learningPrompt.value = ''
    confirmationDocument.value = ''
    decisionDismissed.value = false
    learningProfile.value = emptyProfile()
    learningMediaAssetIds.value = []
    learningSourceResourceIds.value = []
    learningUploadedFileNames.value = []
    learningConfirmationResourceId.value = null
    learningSetupId.value = createRandomId('learning-setup')
    if (activeProjectId) void restoreLearningSetup(activeProjectId)
  },
  { flush: 'sync' },
)

watch(
  [
    selectedKnowledgeBaseId,
    learningPrompt,
    learningProfile,
    learningMediaAssetIds,
    learningSourceResourceIds,
    learningUploadedFileNames,
    learningConfirmationResourceId,
    confirmationDocument,
    learningPhase,
    currentProfileMessageId,
    currentDocumentMessageId,
  ],
  () => persistLearningSetup(),
  { deep: true },
)

watch(
  messages,
  (items) => {
    if (!isLearningSetupChat.value || learningPhase.value !== 'idle') return
    const lastDocument = [...items].reverse().find((message) => message.kind === 'learning-document')
    if (lastDocument?.learningData) {
      currentDocumentMessageId.value = lastDocument.id
      confirmationDocument.value = lastDocument.learningData.content || ''
      if (lastDocument.learningData.resourceId) {
        learningConfirmationResourceId.value = lastDocument.learningData.resourceId
        learningSourceResourceIds.value = [...new Set([...learningSourceResourceIds.value, lastDocument.learningData.resourceId])]
      }
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
  [isTutorChat, tutorQuestionFromRoute, projectId],
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
      query: { projectId: String(projectId), tutor: '1' },
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

            <div v-if="!isLearningSetupChat" class="home-action-chips">
              <button
                v-for="action in homePromptActions"
                :key="action.label"
                class="home-action-chip ui-hover-row"
                type="button"
                :disabled="messageStore.isStreaming"
                @click="runHomePromptAction(action)"
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
          :media-enabled="true"
          :media-purpose="isLearningChat ? 'learning-input' : 'chat-attachment'"
          :media-context="{
            conversationId: activeLearningChatId,
            knowledgeBaseId: selectedKnowledgeBaseId,
            projectId,
          }"
          placeholder="输入消息，Enter 发送，Shift+Enter 换行"
          @send="onSend"
          @stop="messageStore.stopStreaming"
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
          <template v-else-if="!isLearningChat" #context>
            <ChatSourceSelector
              v-model:knowledge-base-id="generalKnowledgeBaseId"
              v-model:asset-ids="generalSourceAssetIds"
              :disabled="messageStore.isStreaming"
            />
          </template>
        </AppInput>

        <div v-if="messageStore.errorMessage" class="chat-error" role="alert">
          <span>{{ messageStore.errorMessage }}</span>
          <div class="chat-error__actions">
            <button v-if="canRetry" type="button" @click="retryFailedAction">重试</button>
            <button type="button" title="关闭错误提示" aria-label="关闭错误提示" @click="dismissError">
              <AppIcon name="close" :size="14" />
            </button>
          </div>
        </div>

        <SegmentPanel
          v-if="!showWelcome"
          :conversation-id="activeChatId"
          :container-ref="messageListContainer"
        />

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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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
  min-height: 34px;
  padding: 8px 8px 8px 11px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--color-border));
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

.chat-error > span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.chat-error__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.chat-error__actions button {
  min-width: 30px;
  height: 26px;
  padding: 0 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  white-space: nowrap;
}

.chat-error__actions button:hover {
  background: color-mix(in srgb, var(--color-danger) 10%, transparent);
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

</style>
