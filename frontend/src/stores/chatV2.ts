import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import * as api from '@/api/chatV2'
import { inferArtifactRequest } from '@/utils/artifactRequest'
import type {
  AiRun,
  ArtifactType,
  Artifact,
  ChatMessage,
  ChatStreamEvent,
  CreateConversationPayload,
  ConversationSummary,
  MessageAttachment,
  MessageSegment,
  MessageVersionGroup,
  SendMessageAccepted,
} from '@/types/contracts/chatV2'

type MessagesPage = Awaited<ReturnType<typeof api.getConversationMessages>>

/**
 * Keep old unit-test doubles and older deployments readable while the V2
 * endpoint rolls out. Production always uses the paged endpoint; the legacy
 * detail response is only a bounded-compatibility fallback when that method
 * is not present on the injected API module.
 */
async function fetchMessagesPage(
  conversationId: string,
  options: { cursor?: string | null; targetMessageId?: string | null; limit?: number } = {},
): Promise<MessagesPage> {
  // Vitest/older clients may expose a module namespace without this newer
  // export. Check own properties before reading it; accessing a missing
  // namespace export can itself throw in a proxy-based mock.
  const hasPagedEndpoint = Object.prototype.hasOwnProperty.call(api, 'getConversationMessages')
  if (hasPagedEndpoint) {
    const paged = (api as typeof api & {
      getConversationMessages?: typeof api.getConversationMessages
    }).getConversationMessages
    if (typeof paged === 'function') return paged(conversationId, options)
  }
  const detail = await api.getConversation(conversationId)
  return {
    ...detail,
    segments: [],
    nextCursor: null,
    hasMore: false,
  } as MessagesPage
}

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
  generationType: ArtifactType | null
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

function retainCancelledContent(snapshot: ChatMessage[], visible: ChatMessage[]): ChatMessage[] {
  const previousById = new Map(visible.map(message => [message.id, message]))
  return snapshot.map(message => {
    if (message.role !== 'ASSISTANT' || !message.runId) return message
    const previous = previousById.get(message.id)
    if (!previous || previous.runId !== message.runId || previous.branchId !== message.branchId) return message
    // An earlier history request may finish after the cancellation event.
    if (previous.status === 'CANCELLED' && message.status === 'STREAMING') return previous
    if (message.status !== 'CANCELLED') return message
    const content = message.content ?? ''
    // An empty or lagging snapshot cannot erase a prefix already shown for this
    // cancelled answer. Never borrow text from another message, run or branch.
    if (previous.content && previous.content.length > content.length && previous.content.startsWith(content)) {
      return { ...message, content: previous.content }
    }
    return message
  })
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

function shouldReplaceArtifact(previous: Artifact, next: Artifact) {
  // Confirmation is monotonic from the chat UI's point of view. A list request
  // that started before confirmation must not put a stale draft back on screen.
  if (previous.confirmedAssetId && !next.confirmedAssetId) return false
  if (next.confirmedAssetId && !previous.confirmedAssetId) return true
  if (next.revision !== previous.revision) return next.revision > previous.revision
  return next.updatedAt >= previous.updatedAt
}

function conversationOrderAt(item: ConversationSummary) {
  return item.pinnedAt || item.lastMessageAt || item.updatedAt || item.createdAt
}

function sortConversations(items: ConversationSummary[]) {
  return [...items].sort((left, right) => {
    const leftPinned = left.pinnedAt ? 1 : 0
    const rightPinned = right.pinnedAt ? 1 : 0
    if (leftPinned !== rightPinned) return rightPinned - leftPinned
    return conversationOrderAt(right).localeCompare(conversationOrderAt(left))
      || right.id.localeCompare(left.id)
  })
}

function stageLabel(stage: string) {
  const labels: Record<string, string> = {
    queued: '等待开始',
    'agent-planning': '正在理解你的要求',
    retrieving: '正在检索已关联资料',
    'retrieval-completed': '资料检索完成',
    generating: '正在生成回答',
    'artifact-started': '正在生成内容',
    persisting: '正在保存结果',
    reconnecting: '正在恢复实时连接',
    cancelling: '正在停止生成',
  }
  return labels[stage] ?? '正在处理'
}

export const useChatV2Store = defineStore('chatV2', () => {
  const conversations = ref<ConversationSummary[]>([])
  const nextCursor = ref<string | null>(null)
  const listLoading = ref(false)
  const hasMoreConversations = computed(() => Boolean(nextCursor.value))
  const activeConversation = ref<ConversationSummary | null>(null)
  const messages = ref<ChatMessage[]>([])
  const messageCursor = ref<string | null>(null)
  const hasMoreMessages = ref(false)
  const loadingEarlierMessages = ref(false)
  const segments = ref<MessageSegment[]>([])
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
  let listRequest: Promise<Awaited<ReturnType<typeof api.listConversations>>> | null = null
  const titleRefreshTimers = new Map<string, number>()
  const artifactGenerations = new Map<string, string>()
  const artifactReplacements = new Map<string, string>()

  function optimisticArtifact(
    conversationId: string,
    runId: string,
    type: ArtifactType,
    createdAt: string,
  ): Artifact {
    const labels: Record<ArtifactType, string> = {
      DOCUMENT: '文档草稿',
      MINDMAP: '思维导图草稿',
      PRESENTATION: '演示文稿草稿',
      IMAGE: '生成图片',
    }
    return {
      id: `optimistic-artifact-${crypto.randomUUID()}`,
      conversationId,
      runId,
      type,
      status: 'GENERATING',
      title: labels[type],
      schemaVersion: 1,
      content: {},
      revision: 0,
      version: 0,
      confirmedAssetId: null,
      confirmedAssetVersionId: null,
      errorCode: null,
      createdAt,
      updatedAt: createdAt,
      confirmedAt: null,
    }
  }

  function scheduleAutoTitleRefresh(conversationId: string, attempts = 5) {
    const previous = titleRefreshTimers.get(conversationId)
    if (previous) window.clearTimeout(previous)
    const timer = window.setTimeout(async () => {
      titleRefreshTimers.delete(conversationId)
      // Keep the delayed title refresh tolerant of partial API mocks and
      // temporarily unavailable summary endpoints.  A missing summary must
      // never surface as an unhandled timer rejection.
      let getSummary: typeof api.getConversationSummary | undefined
      try { getSummary = api.getConversationSummary } catch { /* partial test/mock module */ }
      const summary = await Promise.resolve(getSummary ? getSummary(conversationId) : null).catch(() => null)
      if (!summary) return
      upsertConversation(summary)
      if (activeConversation.value?.id === conversationId) activeConversation.value = summary
      if (summary.titleSource === 'AUTO' && attempts > 0) {
        scheduleAutoTitleRefresh(conversationId, attempts - 1)
      }
    }, 700)
    titleRefreshTimers.set(conversationId, timer)
  }

  async function loadList(append = false) {
    if (append && !nextCursor.value) {
      return { items: conversations.value, nextCursor: null, hasMore: false }
    }
    if (listRequest) return listRequest
    listLoading.value = true
    const request = api.listConversations(append ? nextCursor.value : null)
    listRequest = request
    try {
      const page = await request
      const merged = append ? [...conversations.value, ...page.items] : page.items
      const unique = merged.filter((item, index, all) => (
        all.findIndex(candidate => candidate.id === item.id) === index
      ))
      conversations.value = sortConversations(unique)
      nextCursor.value = page.nextCursor
      return page
    } finally {
      if (listRequest === request) listRequest = null
      listLoading.value = false
    }
  }

  function upsertConversation(item: ConversationSummary) {
    const index = conversations.value.findIndex(candidate => candidate.id === item.id)
    const merged = [...conversations.value]
    if (index === -1) merged.push(item)
    else merged[index] = item
    conversations.value = sortConversations(merged)
  }

  function beginConversation(payload: { title?: string; knowledgeBaseId?: string | null } = {}) {
    const now = new Date().toISOString()
    const conversation: ConversationSummary = {
      id: newExternalId(),
      title: payload.title?.trim() || '新对话',
      titleSource: payload.title?.trim() && payload.title.trim() !== '新对话' ? 'MANUAL' : 'AUTO',
      type: 'GENERAL',
      status: 'ACTIVE',
      knowledgeBaseId: payload.knowledgeBaseId ?? null,
      activeBranchId: '',
      messageCount: 0,
      version: 0,
      pinnedAt: null,
      lastMessageAt: null,
      createdAt: now,
      updatedAt: now,
    }
    activeConversation.value = conversation
    messages.value = []
    messageCursor.value = null
    hasMoreMessages.value = false
    loadingEarlierMessages.value = false
    segments.value = []
    versionGroups.value = []
    artifacts.value = []
    upsertConversation(conversation)
    return conversation
  }

  async function create(payload: CreateConversationPayload = {}) {
    const conversation = await api.createConversation(payload)
    const preserveOptimisticMessages = activeConversation.value?.id === conversation.id
    upsertConversation(conversation)
    activeConversation.value = conversation
    if (preserveOptimisticMessages) {
      messages.value.forEach((message) => {
        if (!message.branchId) message.branchId = conversation.activeBranchId
      })
    } else {
      messages.value = []
      messageCursor.value = null
      hasMoreMessages.value = false
      segments.value = []
      versionGroups.value = []
      artifacts.value = []
    }
    return conversation
  }

  function beginOptimisticTurn(
    content: string,
    sourceAssetIds: string[],
    attachments: MessageAttachment[] = [],
    generationType: ArtifactType | null = null,
  ) {
    const conversation = activeConversation.value
    if (!conversation) throw new Error('请先创建对话。')
    const normalizedContent = content.trim()
    if (!generationType) {
      const previousMessage = [...messages.value].reverse().find(message => message.role === 'ASSISTANT'
        && artifacts.value.some(artifact => artifact.runId === message.runId))
      const previousType = artifacts.value.find(artifact => artifact.runId === previousMessage?.runId)?.type
      generationType = inferArtifactRequest(normalizedContent, previousType)
    }
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
      if (pendingTurn.generationType && !artifacts.value.some(artifact => artifact.runId === pendingTurn!.idempotencyKey)) {
        artifacts.value.unshift(optimisticArtifact(conversation.id, pendingTurn.idempotencyKey,
          pendingTurn.generationType, new Date().toISOString()))
      }
      stage.value = cancelRequested ? 'cancelling' : 'queued'
      sending.value = true
      return pendingTurn
    }

    const now = new Date().toISOString()
    const lastMessage = messages.value.at(-1)
    const userMessageId = `optimistic-user-${crypto.randomUUID()}`
    const assistantMessageId = `optimistic-assistant-${crypto.randomUUID()}`
    const idempotencyKey = crypto.randomUUID()
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
      runId: idempotencyKey,
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
      // Use the idempotency key from the moment the optimistic turn is
      // created. This lets the matching artifact skeleton render immediately,
      // before the server has accepted the run and returned its real runId.
      runId: idempotencyKey,
      attachments: [],
      citations: [],
      createdAt: now,
      finalizedAt: null,
    })
    pendingTurn = {
      conversationId: conversation.id,
      content: normalizedContent,
      sourceAssetIds: [...sourceAssetIds],
      idempotencyKey,
      userMessageId,
      assistantMessageId,
      generationType,
    }
    if (generationType) {
      artifacts.value.unshift(optimisticArtifact(conversation.id, idempotencyKey, generationType, now))
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
      const [page, artifactItems] = await Promise.all([
        fetchMessagesPage(conversationId),
        api.listArtifacts(conversationId),
      ])
      const visibleMessages = activeConversation.value?.id === conversationId ? messages.value : []
      activeConversation.value = page.conversation
      messages.value = retainCancelledContent(page.messages, visibleMessages)
      versionGroups.value = page.versionGroups ?? []
      segments.value = page.segments ?? []
      messageCursor.value = page.nextCursor
      hasMoreMessages.value = page.hasMore
      replaceArtifacts(artifactItems)
      upsertConversation(page.conversation)
      if (page.conversation.titleSource === 'AUTO') {
        scheduleAutoTitleRefresh(page.conversation.id)
      }
      const running = [...page.messages].reverse().find(message =>
        message.role === 'ASSISTANT' && message.runId && !message.finalizedAt,
      )
      if (running?.runId) await resume(running.runId, running.id)
      return page
    } catch (cause) {
      error.value = api.chatError(cause, '加载对话失败。').message
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function loadEarlierMessages() {
    const conversation = activeConversation.value
    if (!conversation || !messageCursor.value || loadingEarlierMessages.value) return false
    loadingEarlierMessages.value = true
    try {
      const page = await fetchMessagesPage(conversation.id, {
        cursor: messageCursor.value,
      })
      const existing = new Set(messages.value.map(message => message.id))
      const older = page.messages.filter(message => !existing.has(message.id))
      messages.value = [...older, ...messages.value].sort((left, right) => left.sequence - right.sequence)
      versionGroups.value = page.versionGroups ?? versionGroups.value
      segments.value = page.segments ?? segments.value
      messageCursor.value = page.nextCursor
      hasMoreMessages.value = page.hasMore
      return older.length > 0
    } catch (cause) {
      error.value = api.chatError(cause, '加载更早消息失败。').message
      return false
    } finally {
      loadingEarlierMessages.value = false
    }
  }

  async function loadMessagesAround(messageId: string) {
    const conversation = activeConversation.value
    if (!conversation) return false
    const page = await fetchMessagesPage(conversation.id, { targetMessageId: messageId })
    const existing = new Map(messages.value.map(message => [message.id, message]))
    page.messages.forEach(message => existing.set(message.id, message))
    messages.value = [...existing.values()].sort((left, right) => left.sequence - right.sequence)
    versionGroups.value = page.versionGroups ?? versionGroups.value
    segments.value = page.segments ?? segments.value
    messageCursor.value = page.nextCursor
    hasMoreMessages.value = page.hasMore
    return messages.value.some(message => message.id === messageId)
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

  async function loadArtifactEventually(artifactId: string) {
    // The artifact.created event can arrive just before the transaction is
    // visible to the detail endpoint. Retry a few short times so the chat card
    // appears immediately instead of waiting for a later full conversation reload.
    for (let attempt = 0; attempt < 6; attempt += 1) {
      try {
        const item = await api.getArtifact(artifactId)
        if (item.conversationId === activeConversation.value?.id) upsertArtifact(item)
        return item
      } catch {
        if (attempt < 5) await new Promise(resolve => window.setTimeout(resolve, 350))
      }
    }
    return null
  }

  function handleEvent(event: ChatStreamEvent) {
    if (event.id) lastEventId.value = event.id
    const payload = event.data
    if (event.event === 'run.stage_changed') {
      stage.value = String(payload.stage ?? '')
    } else if (event.event === 'artifact.started') {
      const type = payload.type as ArtifactType
      const runId = String(payload.runId ?? activeRunId.value ?? '')
      if (!activeConversation.value || !runId || runId !== activeRunId.value
        || !['DOCUMENT', 'MINDMAP', 'PRESENTATION', 'IMAGE'].includes(type)) return
      const generationId = String(payload.generationId ?? `${runId}:${type}`)
      if (artifactGenerations.has(generationId)) return
      const claimed = new Set(artifactGenerations.values())
      let placeholder = artifacts.value.find(item => item.id.startsWith('optimistic-artifact-')
        && item.runId === runId && item.type === type && !claimed.has(item.id))
      if (!placeholder) {
        placeholder = optimisticArtifact(activeConversation.value.id, runId, type, new Date().toISOString())
        artifacts.value.push(placeholder)
      }
      if (typeof payload.title === 'string' && payload.title.trim()) placeholder.title = payload.title
      artifactGenerations.set(generationId, placeholder.id)
      stage.value = 'artifact-started'
    } else if (event.event === 'message.delta') {
      const messageId = String(payload.messageId ?? assistantMessageId.value ?? '')
      if (!messageId || !activeRunId.value) return
      ensureAssistantMessage(messageId, activeRunId.value).content += String(payload.delta ?? '')
    } else if (event.event === 'artifact.created') {
      const artifactId = String(payload.artifactId ?? '')
      if (!artifactId) return
      const generationId = String(payload.generationId ?? '')
      const placeholderId = artifactGenerations.get(generationId)
      if (placeholderId?.startsWith('optimistic-artifact-')) artifactReplacements.set(artifactId, placeholderId)
      if (generationId) artifactGenerations.set(generationId, artifactId)
      void loadArtifactEventually(artifactId)
    } else if (TERMINAL_EVENTS.has(event.event)) {
      const runId = String(payload.runId ?? activeRunId.value ?? '')
      if (runId !== activeRunId.value) return
      const status = event.event === 'run.failed' ? 'FAILED' : event.event === 'run.cancelled' ? 'CANCELLED' : 'FINALIZED'
      messages.value.filter(message => message.runId === runId && message.status === 'STREAMING')
        .forEach(message => { message.status = status })
      if (event.event !== 'run.completed') {
        artifacts.value = artifacts.value.filter(item => !(item.id.startsWith('optimistic-artifact-') && item.runId === runId))
      } else stage.value = 'persisting'
    }
  }

  async function finishStream(runId: string) {
    const conversationId = activeConversation.value?.id
    const isCurrentRun = () => activeRunId.value === runId && activeConversation.value?.id === conversationId
    if (!conversationId || !isCurrentRun()) return
    const run = await api.getRun(runId).catch(() => null)
    if (!isCurrentRun()) return
    if (run) activeRun.value = run
    try {
      if (activeConversation.value) {
        const [page, artifactItems] = await Promise.all([
          fetchMessagesPage(conversationId),
          api.listArtifacts(conversationId),
        ])
        if (!isCurrentRun()) return
        activeConversation.value = page.conversation
        messages.value = retainCancelledContent(page.messages, messages.value)
        versionGroups.value = page.versionGroups ?? []
        segments.value = page.segments ?? []
        messageCursor.value = page.nextCursor
        hasMoreMessages.value = page.hasMore
        replaceArtifacts(artifactItems)
        upsertConversation(page.conversation)
        if (page.conversation.titleSource === 'AUTO') {
          scheduleAutoTitleRefresh(page.conversation.id)
        }
      }
    } catch (cause) {
      if (!isCurrentRun()) return
      error.value = api.chatError(cause, '回答已经结束，但最新消息同步失败，请重新进入对话。').message
    }
    if (run?.status === 'FAILED') error.value = run.safeErrorMessage || '生成失败，请重试。'
    else if (run?.status === 'CANCELLED') error.value = ''
    artifacts.value = artifacts.value.filter(item => !(item.id.startsWith('optimistic-artifact-') && item.runId === runId))
    messages.value.filter(message => message.runId === runId && message.status === 'STREAMING')
      .forEach(message => { message.status = run?.status === 'FAILED' ? 'FAILED' : run?.status === 'CANCELLED' ? 'CANCELLED' : 'FINALIZED' })
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
      artifacts.value.forEach((artifact) => {
        if (artifact.id.startsWith('optimistic-artifact-') && artifact.runId === optimistic.idempotencyKey) {
          artifact.runId = accepted.runId
        }
      })
      optimistic.userMessageId = accepted.userMessageId
      optimistic.assistantMessageId = accepted.assistantMessageId
      if (cancelRequested) await api.cancelRun(accepted.runId)
      await connectStream(accepted.runId, accepted.assistantMessageId)
      return accepted
    } catch (cause) {
      const assistantMessage = messages.value.find(message => message.id === optimistic.assistantMessageId)
      if (assistantMessage && assistantMessage.runId === optimistic.idempotencyKey) {
        assistantMessage.status = 'FAILED'
        assistantMessage.finalizedAt = new Date().toISOString()
        cancelRequested = false
      }
      artifacts.value = artifacts.value.filter(artifact => artifact.runId !== optimistic.idempotencyKey)
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
    targetMessageId: string,
    idempotencyKey: string,
    generationType: ArtifactType | null,
    editedContent?: string,
  ) {
    const conversation = activeConversation.value
    if (!conversation) throw new Error('请先创建对话。')
    if (sending.value) throw new Error('请先停止当前回答，再重新生成。')
    const originalMessages = messages.value
    const targetIndex = originalMessages.findIndex(message => message.id === targetMessageId)
    if (targetIndex < 0) throw new Error('未找到当前消息，请重新进入对话。')
    const target = originalMessages[targetIndex]!
    const now = new Date().toISOString()
    const temporaryAssistantId = `optimistic-assistant-${crypto.randomUUID()}`
    const question = editedContent !== undefined ? {
      ...target,
      id: `optimistic-user-${crypto.randomUUID()}`,
      content: editedContent,
      createdAt: now,
    } : originalMessages.find(message => message.id === target.parentMessageId)
    const placeholder: ChatMessage = {
      id: temporaryAssistantId,
      branchId: conversation.activeBranchId,
      versionGroupId: temporaryAssistantId,
      parentMessageId: question?.id ?? target.parentMessageId,
      role: 'ASSISTANT',
      status: 'STREAMING',
      sequence: editedContent !== undefined ? target.sequence + 1 : target.sequence,
      content: '',
      runId: idempotencyKey,
      attachments: [],
      citations: [],
      createdAt: now,
      finalizedAt: null,
    }
    streamController?.abort()
    activeRunId.value = null
    cancelRequested = false
    lastEventId.value = null
    sending.value = true
    stage.value = 'queued'
    error.value = ''
    // Reserve the new branch locally before waiting for the mutation or history fetch.
    // The original branch remains available for rollback if the request is rejected.
    messages.value = [
      ...originalMessages.slice(0, targetIndex),
      ...(editedContent !== undefined && question ? [question] : []),
      placeholder,
    ]
    if (generationType) artifacts.value.unshift(optimisticArtifact(conversation.id, idempotencyKey, generationType, now))
    let accepted: SendMessageAccepted | null = null
    try {
      accepted = await operation()
      if (activeConversation.value?.id !== conversation.id) return accepted
      activeRunId.value = accepted.runId
      const assistant = messages.value.find(message => message.id === temporaryAssistantId)
      if (assistant) {
        assistant.id = accepted.assistantMessageId
        assistant.parentMessageId = accepted.userMessageId
        assistant.runId = accepted.runId
      }
      if (editedContent !== undefined) {
        const optimisticQuestion = messages.value.find(message => message.id === question?.id)
        if (optimisticQuestion) optimisticQuestion.id = accepted.userMessageId
      }
      artifacts.value.forEach(artifact => {
        if (artifact.id.startsWith('optimistic-artifact-') && artifact.runId === idempotencyKey) artifact.runId = accepted!.runId
      })
      if (cancelRequested) await api.cancelRun(accepted.runId)
      // A failed history refresh must not discard an accepted run or submit it again.
      const page = await fetchMessagesPage(conversation.id).catch(() => null)
      if (activeConversation.value?.id !== conversation.id) return accepted
      if (page) {
        activeConversation.value = page.conversation
        messages.value = page.messages
        versionGroups.value = page.versionGroups ?? []
        segments.value = page.segments ?? []
        messageCursor.value = page.nextCursor
        hasMoreMessages.value = page.hasMore
        upsertConversation(page.conversation)
      }
      const response = ensureAssistantMessage(accepted.assistantMessageId, accepted.runId)
      // Events replay from the beginning here, so don't append them to a partial snapshot.
      response.content = ''
      response.status = 'STREAMING'
      response.finalizedAt = null
      await connectStream(accepted.runId, accepted.assistantMessageId)
      return accepted
    } catch (cause) {
      if (activeConversation.value?.id !== conversation.id) throw cause
      if (!accepted) {
        messages.value = originalMessages
        artifacts.value = artifacts.value.filter(artifact => artifact.runId !== idempotencyKey)
        cancelRequested = false
      }
      error.value = api.chatError(cause, '操作失败，请重试。').message
      sending.value = Boolean(accepted)
      stage.value = accepted ? 'reconnecting' : ''
      throw cause
    }
  }

  async function editMessage(messageId: string, content: string) {
    const conversationId = activeConversation.value?.id
    if (!conversationId) throw new Error('请先创建对话。')
    const idempotencyKey = crypto.randomUUID()
    const originalReply = messages.value.find(message => message.parentMessageId === messageId && message.role === 'ASSISTANT')
    const previousType = artifacts.value.find(artifact => artifact.runId === originalReply?.runId)?.type
    return beginBranchRun(() => api.editMessage(conversationId, messageId, content.trim(), idempotencyKey),
      messageId, idempotencyKey, inferArtifactRequest(content, previousType), content.trim())
  }

  async function regenerateMessage(messageId: string) {
    const conversationId = activeConversation.value?.id
    if (!conversationId) throw new Error('请先创建对话。')
    const idempotencyKey = crypto.randomUUID()
    const originalReply = messages.value.find(message => message.id === messageId)
    const originalType = artifacts.value.find(artifact => artifact.runId === originalReply?.runId)?.type
    const question = messages.value.find(message => message.id === originalReply?.parentMessageId)
    return beginBranchRun(() => api.regenerateMessage(conversationId, messageId, idempotencyKey),
      messageId, idempotencyKey, originalType ?? inferArtifactRequest(question?.content ?? ''))
  }

  async function activateMessageBranch(branchId: string) {
    const conversation = activeConversation.value
    if (!conversation || conversation.activeBranchId === branchId) return
    if (sending.value) throw new Error('回答生成完成或停止后才能切换版本。')
    loading.value = true
    error.value = ''
    try {
      const activated = await api.activateBranch(conversation.id, branchId)
      const hasPagedEndpoint = Object.prototype.hasOwnProperty.call(api, 'getConversationMessages')
      const page = hasPagedEndpoint
        ? await fetchMessagesPage(conversation.id)
        : {
            ...activated,
            segments: [],
            nextCursor: null,
            hasMore: false,
          } as MessagesPage
      activeConversation.value = page.conversation
      messages.value = page.messages
      versionGroups.value = page.versionGroups ?? []
      segments.value = page.segments ?? []
      messageCursor.value = page.nextCursor
      hasMoreMessages.value = page.hasMore
      upsertConversation(page.conversation)
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
    upsertConversation(conversation)
    if (activeConversation.value?.id === conversationId) activeConversation.value = conversation
    return conversation
  }

  async function setPinned(conversationId: string, pinned: boolean) {
    const conversation = await api.updateConversation(conversationId, { pinned })
    upsertConversation(conversation)
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
    upsertConversation(updated)
    return updated
  }

  async function saveArtifact(item: Artifact) {
    const saved = await api.updateArtifact(item.id, {
      title: item.title,
      content: item.content,
      version: item.version,
    })
    upsertArtifact(saved)
    return saved
  }

  async function confirmArtifact(item: Artifact) {
    const confirmed = await api.confirmArtifact(item.id)
    upsertArtifact(confirmed)
    return confirmed
  }

  function upsertArtifact(item: Artifact) {
    // The optimistic skeleton uses the idempotency key as its run id. Once the
    // server emits the real artifact, replace that placeholder instead of
    // leaving two cards in the conversation.
    const previous = artifacts.value.find(value => value.id === item.id)
    const replacement = artifactReplacements.get(item.id) ?? (!previous ? artifacts.value.find(existing =>
      existing.id.startsWith('optimistic-artifact-') && existing.runId === item.runId && existing.type === item.type)?.id : undefined)
    if (replacement) artifacts.value = artifacts.value.filter(existing => existing.id !== replacement)
    artifactReplacements.delete(item.id)
    if (!previous || shouldReplaceArtifact(previous, item)) {
      upsert(artifacts.value, item, value => value.id)
    }
  }

  function replaceArtifacts(items: Artifact[]) {
    const merged = new Map(artifacts.value.map(item => [item.id, item]))
    items.forEach((item) => {
      for (const [id, existing] of merged) {
        if (id.startsWith('optimistic-artifact-') && existing.runId === item.runId && existing.type === item.type) merged.delete(id)
      }
      const previous = merged.get(item.id)
      if (!previous || shouldReplaceArtifact(previous, item)) merged.set(item.id, item)
    })
    artifacts.value = [...merged.values()].sort((left, right) => (
      right.createdAt.localeCompare(left.createdAt) || right.id.localeCompare(left.id)
    ))
  }

  async function refreshArtifacts(conversationId = activeConversation.value?.id) {
    if (!conversationId) return artifacts.value
    const latest = await api.listArtifacts(conversationId)
    replaceArtifacts(latest)
    return artifacts.value
  }

  function clearActive() {
    streamController?.abort()
    streamController = null
    activeConversation.value = null
    messages.value = []
    messageCursor.value = null
    hasMoreMessages.value = false
    loadingEarlierMessages.value = false
    segments.value = []
    versionGroups.value = []
    artifacts.value = []
    activeRun.value = null
    artifactGenerations.clear()
    artifactReplacements.clear()
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
    titleRefreshTimers.forEach(timer => window.clearTimeout(timer))
    titleRefreshTimers.clear()
    clearActive()
    conversations.value = []
    nextCursor.value = null
  }

  return {
    conversations,
    nextCursor,
    activeConversation,
    messages,
    messageCursor,
    hasMoreMessages,
    loadingEarlierMessages,
    segments,
    versionGroups,
    artifacts,
    activeRun,
    activeRunId,
    stage,
    stageText,
    loading,
    listLoading,
    hasMoreConversations,
    sending,
    error,
    loadList,
    beginConversation,
    create,
    beginOptimisticTurn,
    discardConversation,
    load,
    loadEarlierMessages,
    loadMessagesAround,
    send,
    cancel,
    editMessage,
    regenerateMessage,
    activateMessageBranch,
    remove,
    rename,
    setPinned,
    setKnowledgeBase,
    saveArtifact,
    confirmArtifact,
    upsertArtifact,
    refreshArtifacts,
    clearActive,
    clear,
  }
})
