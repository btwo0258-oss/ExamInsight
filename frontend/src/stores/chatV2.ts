import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import * as api from '@/api/chatV2'
import type {
  AiRun,
  Artifact,
  ChatMessage,
  ChatStreamEvent,
  CreateConversationPayload,
  ConversationSummary,
  MessageAttachment,
  MessageVersionGroup,
  SendMessageAccepted,
} from '@/types/contracts/chatV2'

const TERMINAL_EVENTS = new Set(['run.completed', 'run.failed', 'run.cancelled'])
const TERMINAL_RUN_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED'])
const CROCKFORD = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'

type PendingTurn = {
  conversationId: string
  content: string
  sourceAssetIds: string[]
  idempotencyKey: string
  userMessageId: string
  assistantMessageId: string
}

function newExternalId() {
  const bytes = new Uint8Array(16)
  let timestamp = Date.now()
  for (let index = 5; index >= 0; index -= 1) {
    bytes[index] = timestamp & 0xff
    timestamp = Math.floor(timestamp / 256)
  }
  crypto.getRandomValues(bytes.subarray(6))
  let value = 0n
  for (const byte of bytes) value = (value << 8n) | BigInt(byte)
  let result = ''
  for (let index = 0; index < 26; index += 1) {
    result = CROCKFORD[Number(value & 31n)] + result
    value >>= 5n
  }
  return result
}

function sameSources(left: string[], right: string[]) {
  return left.length === right.length && left.every((value, index) => value === right[index])
}

function wait(ms: number, signal: AbortSignal) {
  return new Promise<void>((resolve) => {
    if (signal.aborted) return resolve()
    const timer = window.setTimeout(done, ms)
    signal.addEventListener('abort', done, { once: true })
    function done() {
      window.clearTimeout(timer)
      signal.removeEventListener('abort', done)
      resolve()
    }
  })
}

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
    reconnecting: '正在恢复实时连接',
    cancelling: '正在停止生成',
  }
  return labels[stage] ?? '正在处理'
}

export const useChatV2Store = defineStore('chatV2', () => {
  const conversations = ref<ConversationSummary[]>([])
  const nextCursor = ref<string | null>(null)
  const activeConversation = ref<ConversationSummary | null>(null)
  const messages = ref<ChatMessage[]>([])
  const versionGroups = ref<MessageVersionGroup[]>([])
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
  let pendingTurn: PendingTurn | null = null
  let cancelRequested = false

  async function loadList(append = false) {
    const page = await api.listConversations(append ? nextCursor.value : null)
    conversations.value = append ? [...conversations.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor
    return page
  }

  function beginConversation(payload: { title?: string; knowledgeBaseId?: string | null } = {}) {
    const now = new Date().toISOString()
    const conversation: ConversationSummary = {
      id: newExternalId(),
      title: payload.title?.trim() || '新对话',
      type: 'GENERAL',
      status: 'ACTIVE',
      knowledgeBaseId: payload.knowledgeBaseId ?? null,
      activeBranchId: '',
      messageCount: 0,
      version: 0,
      lastMessageAt: null,
      createdAt: now,
      updatedAt: now,
    }
    activeConversation.value = conversation
    messages.value = []
    versionGroups.value = []
    artifacts.value = []
    upsert(conversations.value, conversation, value => value.id)
    return conversation
  }

  async function create(payload: CreateConversationPayload = {}) {
    const conversation = await api.createConversation(payload)
    const preserveOptimisticMessages = activeConversation.value?.id === conversation.id
    upsert(conversations.value, conversation, value => value.id)
    activeConversation.value = conversation
    if (preserveOptimisticMessages) {
      messages.value.forEach((message) => {
        if (!message.branchId) message.branchId = conversation.activeBranchId
      })
    } else {
      messages.value = []
      versionGroups.value = []
      artifacts.value = []
    }
    return conversation
  }

  function beginOptimisticTurn(
    content: string,
    sourceAssetIds: string[],
    attachments: MessageAttachment[] = [],
  ) {
    const conversation = activeConversation.value
    if (!conversation) throw new Error('请先创建对话。')
    const normalizedContent = content.trim()
    if (pendingTurn
      && pendingTurn.conversationId === conversation.id
      && pendingTurn.content === normalizedContent
      && sameSources(pendingTurn.sourceAssetIds, sourceAssetIds)) {
      const assistant = messages.value.find(message => message.id === pendingTurn?.assistantMessageId)
      if (assistant) {
        assistant.content = ''
        assistant.status = 'STREAMING'
        assistant.finalizedAt = null
      }
      error.value = ''
      stage.value = cancelRequested ? 'cancelling' : 'queued'
      sending.value = true
      return pendingTurn
    }

    const now = new Date().toISOString()
    const lastMessage = messages.value.at(-1)
    const userMessageId = `optimistic-user-${crypto.randomUUID()}`
    const assistantMessageId = `optimistic-assistant-${crypto.randomUUID()}`
    const sequence = (lastMessage?.sequence ?? 0) + 1
    messages.value.push({
      id: userMessageId,
      branchId: conversation.activeBranchId,
      versionGroupId: userMessageId,
      parentMessageId: lastMessage?.id ?? null,
      role: 'USER',
      status: 'FINALIZED',
      sequence,
      content: normalizedContent,
      runId: null,
      attachments,
      citations: [],
      createdAt: now,
      finalizedAt: now,
    }, {
      id: assistantMessageId,
      branchId: conversation.activeBranchId,
      versionGroupId: assistantMessageId,
      parentMessageId: userMessageId,
      role: 'ASSISTANT',
      status: 'STREAMING',
      sequence: sequence + 1,
      content: '',
      runId: null,
      attachments: [],
      citations: [],
      createdAt: now,
      finalizedAt: null,
    })
    pendingTurn = {
      conversationId: conversation.id,
      content: normalizedContent,
      sourceAssetIds: [...sourceAssetIds],
      idempotencyKey: crypto.randomUUID(),
      userMessageId,
      assistantMessageId,
    }
    error.value = ''
    stage.value = 'queued'
    sending.value = true
    cancelRequested = false
    return pendingTurn
  }

  function discardConversation(conversationId: string) {
    conversations.value = conversations.value.filter(item => item.id !== conversationId)
    if (activeConversation.value?.id === conversationId) clearActive()
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
      versionGroups.value = detail.versionGroups ?? []
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
        versionGroupId: messageId,
        parentMessageId: null,
        role: 'ASSISTANT',
        status: 'STREAMING',
        sequence: messages.value.length + 1,
        content: '',
        runId,
        attachments: [],
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
    const run = await api.getRun(runId).catch(() => null)
    if (run) activeRun.value = run
    try {
      if (activeConversation.value) {
        const [detail, artifactItems] = await Promise.all([
          api.getConversation(activeConversation.value.id),
          api.listArtifacts(activeConversation.value.id),
        ])
        activeConversation.value = detail.conversation
        messages.value = detail.messages
        versionGroups.value = detail.versionGroups ?? []
        artifacts.value = artifactItems
        upsert(conversations.value, detail.conversation, value => value.id)
      }
    } catch (cause) {
      error.value = api.chatError(cause, '回答已经结束，但最新消息同步失败，请重新进入对话。').message
    }
    if (run?.status === 'FAILED') error.value = run.safeErrorMessage || '生成失败，请重试。'
    else if (run?.status === 'CANCELLED') error.value = ''
    pendingTurn = null
    activeRunId.value = null
    assistantMessageId.value = null
    stage.value = ''
    sending.value = false
    cancelRequested = false
  }

  async function pollUntilTerminal(runId: string, signal: AbortSignal) {
    let requestFailures = 0
    while (!signal.aborted) {
      const run = await api.getRun(runId).catch(() => null)
      if (run) {
        activeRun.value = run
        requestFailures = 0
        if (TERMINAL_RUN_STATUSES.has(run.status)) return true
      } else if (++requestFailures >= 6) {
        return false
      }
      await wait(2_000, signal)
    }
    return false
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
    while (!terminal && !streamController.signal.aborted) {
      try {
        let receivedEvent = false
        await api.streamRunEvents(runId, {
          signal: streamController.signal,
          lastEventId: lastEventId.value,
          onEvent(event) {
            receivedEvent = true
            handleEvent(event)
            if (TERMINAL_EVENTS.has(event.event)) terminal = true
          },
        })
        const status = await api.getRun(runId)
        activeRun.value = status
        terminal = TERMINAL_RUN_STATUSES.has(status.status)
        reconnects = receivedEvent ? 0 : reconnects + 1
      } catch (cause) {
        if (streamController.signal.aborted) return
        const status = await api.getRun(runId).catch(() => null)
        if (status && TERMINAL_RUN_STATUSES.has(status.status)) {
          terminal = true
        } else {
          reconnects += 1
        }
      }
      if (!terminal && reconnects >= 4) {
        stage.value = 'reconnecting'
        terminal = await pollUntilTerminal(runId, streamController.signal)
        if (!terminal && !streamController.signal.aborted) {
          error.value = '生成仍在后台继续，但实时连接已中断。重新进入该对话可恢复进度。'
          sending.value = true
          return
        }
      } else if (!terminal) {
        await wait(Math.min(4_000, 500 * (2 ** reconnects)), streamController.signal)
      }
    }
    if (!streamController.signal.aborted) await finishStream(runId)
  }

  async function send(
    content: string,
    sourceAssetIds: string[],
    attachments: MessageAttachment[] = [],
  ) {
    const conversation = activeConversation.value
    if (!conversation) throw new Error('请先创建对话。')
    const optimistic = beginOptimisticTurn(content, sourceAssetIds, attachments)
    lastEventId.value = null
    try {
      const accepted = await api.sendMessage(
        conversation.id,
        { content: content.trim(), sourceAssetIds },
        optimistic.idempotencyKey,
      )
      const userMessage = messages.value.find(message => message.id === optimistic.userMessageId)
      const assistantMessage = messages.value.find(message => message.id === optimistic.assistantMessageId)
      if (userMessage) userMessage.id = accepted.userMessageId
      if (assistantMessage) {
        assistantMessage.id = accepted.assistantMessageId
        assistantMessage.parentMessageId = accepted.userMessageId
        assistantMessage.runId = accepted.runId
      }
      optimistic.userMessageId = accepted.userMessageId
      optimistic.assistantMessageId = accepted.assistantMessageId
      if (cancelRequested) await api.cancelRun(accepted.runId)
      await connectStream(accepted.runId, accepted.assistantMessageId)
      return accepted
    } catch (cause) {
      const assistantMessage = messages.value.find(message => message.id === optimistic.assistantMessageId)
      if (assistantMessage && !assistantMessage.runId) {
        assistantMessage.status = 'FAILED'
        assistantMessage.finalizedAt = new Date().toISOString()
        cancelRequested = false
      }
      error.value = api.chatError(cause, '发送消息失败。').message
      stage.value = ''
      sending.value = false
      throw cause
    }
  }

  async function resume(runId: string, responseMessageId: string) {
    const run = await api.getRun(runId)
    activeRun.value = run
    if (!TERMINAL_RUN_STATUSES.has(run.status)) {
      lastEventId.value = null
      void connectStream(runId, responseMessageId)
    }
  }

  async function cancel() {
    cancelRequested = true
    stage.value = 'cancelling'
    if (!activeRunId.value) return
    await api.cancelRun(activeRunId.value)
  }

  async function beginBranchRun(
    operation: () => Promise<SendMessageAccepted>,
  ) {
    const conversation = activeConversation.value
    if (!conversation) throw new Error('请先创建对话。')
    streamController?.abort()
    lastEventId.value = null
    sending.value = true
    stage.value = 'queued'
    error.value = ''
    try {
      const accepted = await operation()
      const detail = await api.getConversation(conversation.id)
      activeConversation.value = detail.conversation
      messages.value = detail.messages
      versionGroups.value = detail.versionGroups ?? []
      upsert(conversations.value, detail.conversation, value => value.id)
      await connectStream(accepted.runId, accepted.assistantMessageId)
      return accepted
    } catch (cause) {
      error.value = api.chatError(cause, '操作失败，请重试。').message
      sending.value = false
      stage.value = ''
      throw cause
    }
  }

  async function editMessage(messageId: string, content: string) {
    const conversationId = activeConversation.value?.id
    if (!conversationId) throw new Error('请先创建对话。')
    const idempotencyKey = crypto.randomUUID()
    return beginBranchRun(() => api.editMessage(conversationId, messageId, content.trim(), idempotencyKey))
  }

  async function regenerateMessage(messageId: string) {
    const conversationId = activeConversation.value?.id
    if (!conversationId) throw new Error('请先创建对话。')
    const idempotencyKey = crypto.randomUUID()
    return beginBranchRun(() => api.regenerateMessage(conversationId, messageId, idempotencyKey))
  }

  async function activateMessageBranch(branchId: string) {
    const conversation = activeConversation.value
    if (!conversation || conversation.activeBranchId === branchId) return
    if (sending.value) throw new Error('回答生成完成或停止后才能切换版本。')
    loading.value = true
    error.value = ''
    try {
      const detail = await api.activateBranch(conversation.id, branchId)
      activeConversation.value = detail.conversation
      messages.value = detail.messages
      versionGroups.value = detail.versionGroups ?? []
      upsert(conversations.value, detail.conversation, value => value.id)
    } catch (cause) {
      error.value = api.chatError(cause, '切换消息版本失败。').message
      throw cause
    } finally {
      loading.value = false
    }
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
    versionGroups.value = []
    artifacts.value = []
    activeRun.value = null
    activeRunId.value = null
    assistantMessageId.value = null
    lastEventId.value = null
    pendingTurn = null
    cancelRequested = false
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
    versionGroups,
    artifacts,
    activeRun,
    activeRunId,
    stage,
    stageText,
    loading,
    sending,
    error,
    loadList,
    beginConversation,
    create,
    beginOptimisticTurn,
    discardConversation,
    load,
    send,
    cancel,
    editMessage,
    regenerateMessage,
    activateMessageBranch,
    remove,
    rename,
    setKnowledgeBase,
    saveArtifact,
    confirmArtifact,
    clearActive,
    clear,
  }
})
