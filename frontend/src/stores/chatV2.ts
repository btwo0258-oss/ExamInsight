import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import * as api from '@/api/chatV2'
import type {
  AiRun,
  Artifact,
  ChatMessage,
  ChatStreamEvent,
  ConversationSummary,
} from '@/types/contracts/chatV2'

const TERMINAL_EVENTS = new Set(['run.completed', 'run.failed', 'run.cancelled'])

function upsert<T>(items: T[], item: T, id: (value: T) => string) {
  const index = items.findIndex(existing => id(existing) === id(item))
  if (index === -1) items.unshift(item)
  else items[index] = item
}

function stageLabel(stage: string) {
  const labels: Record<string, string> = {
    queued: '等待开始',
    'agent-planning': '正在理解你的要求',
    retrieving: '正在检索已关联资料',
    'retrieval-completed': '资料检索完成',
    generating: '正在生成回答',
    persisting: '正在保存结果',
  }
  return labels[stage] ?? '正在处理'
}

export const useChatV2Store = defineStore('chatV2', () => {
  const conversations = ref<ConversationSummary[]>([])
  const nextCursor = ref<string | null>(null)
  const activeConversation = ref<ConversationSummary | null>(null)
  const messages = ref<ChatMessage[]>([])
  const artifacts = ref<Artifact[]>([])
  const activeRun = ref<AiRun | null>(null)
  const activeRunId = ref<string | null>(null)
  const assistantMessageId = ref<string | null>(null)
  const stage = ref('')
  const stageText = computed(() => stage.value ? stageLabel(stage.value) : '')
  const loading = ref(false)
  const sending = ref(false)
  const error = ref('')
  const lastEventId = ref<string | null>(null)
  let streamController: AbortController | null = null

  async function loadList(append = false) {
    const page = await api.listConversations(append ? nextCursor.value : null)
    conversations.value = append ? [...conversations.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor
    return page
  }

  async function create(payload: { title?: string; knowledgeBaseId?: string | null } = {}) {
    const conversation = await api.createConversation(payload)
    upsert(conversations.value, conversation, value => value.id)
    activeConversation.value = conversation
    messages.value = []
    artifacts.value = []
    return conversation
  }

  async function load(conversationId: string) {
    loading.value = true
    error.value = ''
    try {
      const [detail, artifactItems] = await Promise.all([
        api.getConversation(conversationId),
        api.listArtifacts(conversationId),
      ])
      activeConversation.value = detail.conversation
      messages.value = detail.messages
      artifacts.value = artifactItems
      upsert(conversations.value, detail.conversation, value => value.id)
      const running = [...detail.messages].reverse().find(message =>
        message.role === 'ASSISTANT' && message.runId && !message.finalizedAt,
      )
      if (running?.runId) await resume(running.runId, running.id)
      return detail
    } catch (cause) {
      error.value = api.chatError(cause, '加载对话失败。').message
      throw cause
    } finally {
      loading.value = false
    }
  }

  function ensureAssistantMessage(messageId: string, runId: string) {
    let message = messages.value.find(item => item.id === messageId)
    if (!message) {
      message = {
        id: messageId,
        branchId: activeConversation.value?.activeBranchId ?? '',
        parentMessageId: null,
        role: 'ASSISTANT',
        status: 'STREAMING',
        sequence: messages.value.length + 1,
        content: '',
        runId,
        citations: [],
        createdAt: new Date().toISOString(),
        finalizedAt: null,
      }
      messages.value.push(message)
    }
    return message
  }

  function handleEvent(event: ChatStreamEvent) {
    if (event.id) lastEventId.value = event.id
    const payload = event.data
    if (event.event === 'run.stage_changed') {
      stage.value = String(payload.stage ?? '')
    } else if (event.event === 'message.delta') {
      const messageId = String(payload.messageId ?? assistantMessageId.value ?? '')
      if (!messageId || !activeRunId.value) return
      ensureAssistantMessage(messageId, activeRunId.value).content += String(payload.delta ?? '')
    }
  }

  async function finishStream(runId: string) {
    const run = await api.getRun(runId)
    activeRun.value = run
    if (activeConversation.value) {
      const [detail, artifactItems] = await Promise.all([
        api.getConversation(activeConversation.value.id),
        api.listArtifacts(activeConversation.value.id),
      ])
      activeConversation.value = detail.conversation
      messages.value = detail.messages
      artifacts.value = artifactItems
      upsert(conversations.value, detail.conversation, value => value.id)
    }
    if (run.status === 'FAILED') error.value = run.safeErrorMessage || '生成失败，请重试。'
    activeRunId.value = null
    assistantMessageId.value = null
    stage.value = ''
    sending.value = false
  }

  async function connectStream(runId: string, responseMessageId: string) {
    streamController?.abort()
    streamController = new AbortController()
    activeRunId.value = runId
    assistantMessageId.value = responseMessageId
    sending.value = true
    ensureAssistantMessage(responseMessageId, runId)
    let reconnects = 0
    let terminal = false
    while (!terminal && reconnects <= 3 && !streamController.signal.aborted) {
      try {
        await api.streamRunEvents(runId, {
          signal: streamController.signal,
          lastEventId: lastEventId.value,
          onEvent(event) {
            handleEvent(event)
            if (TERMINAL_EVENTS.has(event.event)) terminal = true
          },
        })
        const status = await api.getRun(runId)
        activeRun.value = status
        terminal = ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status.status)
        if (!terminal) reconnects += 1
      } catch (cause) {
        if (streamController.signal.aborted) return
        const status = await api.getRun(runId).catch(() => null)
        if (status && ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status.status)) {
          terminal = true
        } else {
          reconnects += 1
          await new Promise(resolve => window.setTimeout(resolve, 500 * (2 ** reconnects)))
        }
      }
    }
    if (!streamController.signal.aborted) await finishStream(runId)
  }

  async function send(content: string, sourceAssetIds: string[]) {
    const conversation = activeConversation.value
    if (!conversation) throw new Error('请先创建对话。')
    error.value = ''
    sending.value = true
    lastEventId.value = null
    const accepted = await api.sendMessage(
      conversation.id,
      { content: content.trim(), sourceAssetIds },
      crypto.randomUUID(),
    )
    const detail = await api.getConversation(conversation.id)
    activeConversation.value = detail.conversation
    messages.value = detail.messages
    upsert(conversations.value, detail.conversation, value => value.id)
    await connectStream(accepted.runId, accepted.assistantMessageId)
    return accepted
  }

  async function resume(runId: string, responseMessageId: string) {
    const run = await api.getRun(runId)
    activeRun.value = run
    if (!['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(run.status)) {
      lastEventId.value = null
      void connectStream(runId, responseMessageId)
    }
  }

  async function cancel() {
    if (!activeRunId.value) return
    await api.cancelRun(activeRunId.value)
  }

  async function remove(conversationId: string) {
    await api.trashConversation(conversationId)
    conversations.value = conversations.value.filter(item => item.id !== conversationId)
    if (activeConversation.value?.id === conversationId) clearActive()
  }

  async function rename(conversationId: string, title: string) {
    const conversation = await api.updateConversation(conversationId, { title })
    upsert(conversations.value, conversation, value => value.id)
    if (activeConversation.value?.id === conversationId) activeConversation.value = conversation
    return conversation
  }

  async function setKnowledgeBase(knowledgeBaseId: string | null) {
    const conversation = activeConversation.value
    if (!conversation || conversation.knowledgeBaseId === knowledgeBaseId) return conversation
    const updated = await api.updateConversation(conversation.id, knowledgeBaseId
      ? { knowledgeBaseId }
      : { clearKnowledgeBase: true })
    activeConversation.value = updated
    upsert(conversations.value, updated, value => value.id)
    return updated
  }

  async function saveArtifact(item: Artifact) {
    const saved = await api.updateArtifact(item.id, {
      title: item.title,
      content: item.content,
      version: item.version,
    })
    upsert(artifacts.value, saved, value => value.id)
    return saved
  }

  async function confirmArtifact(item: Artifact) {
    const confirmed = await api.confirmArtifact(item.id)
    upsert(artifacts.value, confirmed, value => value.id)
    return confirmed
  }

  function clearActive() {
    streamController?.abort()
    streamController = null
    activeConversation.value = null
    messages.value = []
    artifacts.value = []
    activeRun.value = null
    activeRunId.value = null
    assistantMessageId.value = null
    lastEventId.value = null
    stage.value = ''
    sending.value = false
    error.value = ''
  }

  function clear() {
    clearActive()
    conversations.value = []
    nextCursor.value = null
  }

  return {
    conversations,
    nextCursor,
    activeConversation,
    messages,
    artifacts,
    activeRun,
    activeRunId,
    stage,
    stageText,
    loading,
    sending,
    error,
    loadList,
    create,
    load,
    send,
    cancel,
    remove,
    rename,
    setKnowledgeBase,
    saveArtifact,
    confirmArtifact,
    clearActive,
    clear,
  }
})
