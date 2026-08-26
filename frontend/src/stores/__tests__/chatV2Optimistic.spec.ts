import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const api = vi.hoisted(() => ({
  createConversation: vi.fn(),
  listConversations: vi.fn(),
  getConversation: vi.fn(),
  updateConversation: vi.fn(),
  trashConversation: vi.fn(),
  sendMessage: vi.fn(),
  editMessage: vi.fn(),
  regenerateMessage: vi.fn(),
  activateBranch: vi.fn(),
  getRun: vi.fn(),
  cancelRun: vi.fn(),
  listArtifacts: vi.fn(),
  updateArtifact: vi.fn(),
  confirmArtifact: vi.fn(),
  streamRunEvents: vi.fn(),
  chatError: vi.fn((error: unknown, fallback: string) => (
    error instanceof Error ? error : new Error(fallback)
  )),
}))

vi.mock('@/api/chatV2', () => api)

import { useChatV2Store } from '@/stores/chatV2'

function conversation(id: string) {
  return {
    id,
    title: '新对话',
    type: 'GENERAL',
    status: 'ACTIVE',
    knowledgeBaseId: null,
    activeBranchId: '01BRANCH000000000000000000',
    messageCount: 0,
    version: 0,
    lastMessageAt: null,
    createdAt: '2026-08-26T00:00:00Z',
    updatedAt: '2026-08-26T00:00:00Z',
  }
}

describe('V2 optimistic chat flow', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    Object.values(api).forEach(mock => mock.mockReset())
    api.chatError.mockImplementation((error: unknown, fallback: string) => (
      error instanceof Error ? error : new Error(fallback)
    ))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('creates the final conversation id locally and displays the turn immediately', () => {
    const store = useChatV2Store()
    const created = store.beginConversation()

    store.beginOptimisticTurn('立即显示的问题', [])

    expect(created.id).toMatch(/^[0-7][0-9A-HJKMNP-TV-Z]{25}$/)
    expect(store.messages.map(message => [message.role, message.content])).toEqual([
      ['USER', '立即显示的问题'],
      ['ASSISTANT', ''],
    ])
    expect(store.sending).toBe(true)
  })

  it('connects the SSE stream immediately after message acceptance without refetching first', async () => {
    const store = useChatV2Store()
    const created = store.beginConversation()
    const persisted = conversation(created.id)
    api.createConversation.mockResolvedValue(persisted)
    api.sendMessage.mockResolvedValue({
      userMessageId: '01USER0000000000000000000',
      assistantMessageId: '01ASSISTANT00000000000000',
      runId: '01RUN00000000000000000000',
      eventUrl: '/events',
    })
    api.streamRunEvents.mockImplementation(async (_runId, options) => {
      expect(api.getConversation).not.toHaveBeenCalled()
      options.onEvent({ id: '1', event: 'message.delta', data: {
        messageId: '01ASSISTANT00000000000000', delta: '流式回答',
      } })
      options.onEvent({ id: '2', event: 'run.completed', data: {} })
    })
    api.getRun.mockResolvedValue({
      id: '01RUN00000000000000000000',
      conversationId: created.id,
      branchId: persisted.activeBranchId,
      requestMessageId: '01USER0000000000000000000',
      responseMessageId: '01ASSISTANT00000000000000',
      status: 'SUCCEEDED',
      stage: 'completed',
      cancellable: false,
      errorCode: null,
      safeErrorMessage: null,
      createdAt: '2026-08-26T00:00:00Z',
      startedAt: '2026-08-26T00:00:00Z',
      completedAt: '2026-08-26T00:00:01Z',
    })
    api.getConversation.mockResolvedValue({ conversation: persisted, messages: [] })
    api.listArtifacts.mockResolvedValue([])

    store.beginOptimisticTurn('测试问题', [])
    await store.create({ conversationId: created.id, title: '新对话' })
    await store.send('测试问题', [])

    expect(api.streamRunEvents).toHaveBeenCalledTimes(1)
    expect(api.getConversation).toHaveBeenCalledTimes(1)
    expect(api.getConversation.mock.invocationCallOrder[0]).toBeGreaterThan(
      api.streamRunEvents.mock.invocationCallOrder[0],
    )
    expect(store.sending).toBe(false)
  })

  it('reuses the idempotency key when the acceptance request must be retried', async () => {
    const store = useChatV2Store()
    const created = store.beginConversation()
    const persisted = conversation(created.id)
    api.createConversation.mockResolvedValue(persisted)
    await store.create({ conversationId: created.id })
    api.sendMessage.mockRejectedValueOnce(new Error('temporary network failure'))

    await expect(store.send('可重试问题', [])).rejects.toThrow('temporary network failure')
    const firstKey = api.sendMessage.mock.calls[0]?.[2]

    api.sendMessage.mockRejectedValueOnce(new Error('still offline'))
    await expect(store.send('可重试问题', [])).rejects.toThrow('still offline')

    expect(api.sendMessage.mock.calls[1]?.[2]).toBe(firstKey)
    expect(store.messages).toHaveLength(2)
  })

  it('honors stop requests made before the backend has accepted the run', async () => {
    const store = useChatV2Store()
    const created = store.beginConversation()
    const persisted = conversation(created.id)
    api.createConversation.mockResolvedValue(persisted)
    await store.create({ conversationId: created.id })

    let acceptMessage!: (value: Record<string, string>) => void
    api.sendMessage.mockReturnValue(new Promise(resolve => { acceptMessage = resolve }))
    const cancelledRun = {
      id: '01RUN00000000000000000000', conversationId: created.id,
      branchId: persisted.activeBranchId, requestMessageId: '01USER0000000000000000000',
      responseMessageId: '01ASSISTANT00000000000000', status: 'CANCELLED', stage: 'cancelled',
      cancellable: false, errorCode: null, safeErrorMessage: null,
      createdAt: '2026-08-26T00:00:00Z', startedAt: null, completedAt: '2026-08-26T00:00:01Z',
    }
    api.cancelRun.mockResolvedValue(cancelledRun)
    api.streamRunEvents.mockImplementation(async (_runId, options) => {
      options.onEvent({ id: '1', event: 'run.cancelled', data: {} })
    })
    api.getRun.mockResolvedValue(cancelledRun)
    api.getConversation.mockResolvedValue({ conversation: persisted, messages: [] })
    api.listArtifacts.mockResolvedValue([])

    const sending = store.send('需要停止的问题', [])
    await Promise.resolve()
    await store.cancel()
    acceptMessage({
      userMessageId: cancelledRun.requestMessageId,
      assistantMessageId: cancelledRun.responseMessageId,
      runId: cancelledRun.id,
      eventUrl: '/events',
    })
    await sending

    expect(api.cancelRun).toHaveBeenCalledWith(cancelledRun.id)
    expect(api.cancelRun.mock.invocationCallOrder[0]).toBeLessThan(
      api.streamRunEvents.mock.invocationCallOrder[0],
    )
    expect(store.sending).toBe(false)
  })

  it('reconnects with the last event cursor after a temporary stream failure', async () => {
    vi.useFakeTimers()
    const store = useChatV2Store()
    const created = store.beginConversation()
    const persisted = conversation(created.id)
    api.createConversation.mockResolvedValue(persisted)
    await store.create({ conversationId: created.id })
    api.sendMessage.mockResolvedValue({
      userMessageId: '01USER0000000000000000000', assistantMessageId: '01ASSISTANT00000000000000',
      runId: '01RUN00000000000000000000', eventUrl: '/events',
    })
    api.streamRunEvents
      .mockImplementationOnce(async (_runId, options) => {
        options.onEvent({ id: '3', event: 'run.stage_changed', data: { stage: 'generating' } })
        throw new Error('stream disconnected')
      })
      .mockImplementationOnce(async (_runId, options) => {
        options.onEvent({ id: '4', event: 'message.delta', data: {
          messageId: '01ASSISTANT00000000000000', delta: '恢复后的回答',
        } })
        options.onEvent({ id: '5', event: 'run.completed', data: {} })
      })
    const runningRun = {
      id: '01RUN00000000000000000000', conversationId: created.id,
      branchId: persisted.activeBranchId, requestMessageId: '01USER0000000000000000000',
      responseMessageId: '01ASSISTANT00000000000000', status: 'RUNNING', stage: 'generating',
      cancellable: true, errorCode: null, safeErrorMessage: null,
      createdAt: '2026-08-26T00:00:00Z', startedAt: '2026-08-26T00:00:00Z', completedAt: null,
    }
    const completedRun = { ...runningRun, status: 'SUCCEEDED', cancellable: false, completedAt: '2026-08-26T00:00:01Z' }
    api.getRun.mockResolvedValueOnce(runningRun).mockResolvedValue(completedRun)
    api.getConversation.mockResolvedValue({ conversation: persisted, messages: [] })
    api.listArtifacts.mockResolvedValue([])

    const sending = store.send('断线恢复问题', [])
    await vi.advanceTimersByTimeAsync(1_100)
    await sending

    expect(api.streamRunEvents).toHaveBeenCalledTimes(2)
    expect(api.streamRunEvents.mock.calls[1]?.[1].lastEventId).toBe('3')
    expect(store.sending).toBe(false)
  })

  it('activates a message version branch and replaces the visible timeline', async () => {
    const store = useChatV2Store()
    const created = store.beginConversation()
    const original = conversation(created.id)
    api.createConversation.mockResolvedValue(original)
    await store.create({ conversationId: created.id })

    const editedBranchId = '01BRANCHEDITED000000000000'
    const editedConversation = {
      ...original,
      activeBranchId: editedBranchId,
      messageCount: 2,
      version: 1,
    }
    const editedMessages = [
      {
        id: '01USEREDITED00000000000000',
        branchId: editedBranchId,
        versionGroupId: '01USERORIGINAL000000000000',
        parentMessageId: null,
        role: 'USER',
        status: 'FINAL',
        sequence: 1,
        content: '编辑后的问题',
        runId: null,
        attachments: [],
        citations: [],
        createdAt: '2026-08-26T00:01:00Z',
        finalizedAt: '2026-08-26T00:01:00Z',
      },
      {
        id: '01ASSISTANTEDITED000000000',
        branchId: editedBranchId,
        versionGroupId: '01ASSISTANTEDITED000000000',
        parentMessageId: '01USEREDITED00000000000000',
        role: 'ASSISTANT',
        status: 'FINAL',
        sequence: 2,
        content: '编辑分支的回答',
        runId: '01RUNEDITED000000000000000',
        attachments: [],
        citations: [],
        createdAt: '2026-08-26T00:01:01Z',
        finalizedAt: '2026-08-26T00:01:02Z',
      },
    ]
    const versionGroups = [{
      id: '01USERORIGINAL000000000000',
      role: 'USER',
      versions: [
        {
          messageId: '01USERORIGINAL000000000000',
          branchId: original.activeBranchId,
          createdAt: '2026-08-26T00:00:00Z',
        },
        {
          messageId: '01USEREDITED00000000000000',
          branchId: editedBranchId,
          createdAt: '2026-08-26T00:01:00Z',
        },
      ],
    }]
    api.activateBranch.mockResolvedValue({
      conversation: editedConversation,
      messages: editedMessages,
      versionGroups,
    })

    await store.activateMessageBranch(editedBranchId)

    expect(api.activateBranch).toHaveBeenCalledWith(created.id, editedBranchId)
    expect(store.activeConversation?.activeBranchId).toBe(editedBranchId)
    expect(store.messages.map(message => message.content)).toEqual([
      '编辑后的问题',
      '编辑分支的回答',
    ])
    expect(store.versionGroups).toEqual(versionGroups)
  })
})
