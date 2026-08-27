import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { Artifact, ChatStreamEvent } from '@/types/contracts/chatV2'

const api = vi.hoisted(() => ({
  sendMessage: vi.fn(), streamRunEvents: vi.fn(), getRun: vi.fn(),
  getConversationMessages: vi.fn(), listArtifacts: vi.fn(), getArtifact: vi.fn(),
  chatError: (error: unknown) => error,
}))
vi.mock('@/api/chatV2', () => api)
import { useChatV2Store } from '@/stores/chatV2'

describe('generation placeholders', () => {
  let receive: (event: ChatStreamEvent) => void
  let release: () => void
  let store: ReturnType<typeof useChatV2Store>

  beforeEach(() => {
    vi.resetAllMocks()
    setActivePinia(createPinia())
    store = useChatV2Store()
    const conversation = store.beginConversation()
    api.sendMessage.mockResolvedValue({ userMessageId: 'USER', assistantMessageId: 'ANSWER', runId: 'RUN', eventUrl: '/events' })
    api.streamRunEvents.mockImplementation((_id, options) => new Promise<void>(resolve => {
      receive = options.onEvent
      release = resolve
    }))
    api.getRun.mockResolvedValue({ id: 'RUN', status: 'SUCCEEDED' })
    api.getConversationMessages.mockImplementation(async () => ({ conversation,
      messages: store.messages.map(message => ({ ...message, status: 'FINALIZED' })),
      versionGroups: [], segments: [], hasMore: false, nextCursor: null,
    }))
    api.listArtifacts.mockResolvedValue([])
  })

  const start = (generationId: string, type = 'DOCUMENT') => receive({ id: generationId, event: 'artifact.started',
    data: { runId: 'RUN', generationId, type, title: '生成测试' } })
  const created = (generationId: string, artifactId: string) => receive({ id: artifactId, event: 'artifact.created',
    data: { runId: 'RUN', generationId, artifactId } })
  const artifact = (id: string): Artifact => ({ id, conversationId: store.activeConversation!.id, runId: 'RUN',
    type: 'DOCUMENT', status: 'DRAFT', title: '文档', content: { markdown: '# 文档' }, schemaVersion: 1,
    revision: 1, version: 1, confirmedAssetId: null, confirmedAssetVersionId: null, errorCode: null,
    createdAt: '2026-08-28T00:00:00Z', updatedAt: '2026-08-28T00:00:00Z', confirmedAt: null,
  })
  async function accepted() { await vi.waitFor(() => expect(api.streamRunEvents).toHaveBeenCalledOnce()) }
  function end(event = 'run.completed') {
    receive({ id: 'END', event, data: { runId: 'RUN' } })
    release()
  }

  it('reserves both the answer and card before acceptance, preserving the card on run-id handoff', async () => {
    store.beginOptimisticTurn('请生成一份文档', [])
    expect(store.messages[1]).toMatchObject({ content: '', status: 'STREAMING' })
    expect(store.artifacts).toHaveLength(1)
    expect(store.artifacts[0]!.runId).toBe(store.messages[1]!.runId)
    const pending = store.send('请生成一份文档', [])
    await accepted()
    expect(store.artifacts[0]!.runId).toBe('RUN')
    start('FIRST')
    start('FIRST') // replay must not duplicate a card
    expect(store.artifacts).toHaveLength(1)
    const ready = artifact('DOC1')
    api.getArtifact.mockResolvedValue(ready)
    created('FIRST', 'DOC1')
    await vi.waitFor(() => expect(store.artifacts[0]!.id).toBe('DOC1'))
    expect(store.artifacts).toHaveLength(1)
    api.listArtifacts.mockResolvedValue([ready])
    end()
    await pending
    expect(store.artifacts[0]!.status).toBe('DRAFT')
  })

  it('adds a card when the agent starts a tool, without requiring a quick card', async () => {
    const pending = store.send('请帮我处理这个主题', [])
    await accepted()
    expect(store.artifacts).toHaveLength(0)
    start('FIRST', 'PRESENTATION')
    expect(store.artifacts[0]).toMatchObject({ type: 'PRESENTATION', status: 'GENERATING' })
    expect(store.stageText).toBe('正在生成内容')
    end()
    await pending
    expect(store.artifacts).toHaveLength(0)
  })

  it('does not erase another pending card when one artifact finishes', async () => {
    const pending = store.send('帮我处理资料', [])
    await accepted()
    start('FIRST')
    start('SECOND')
    expect(store.artifacts).toHaveLength(2)
    const ready = artifact('DOC1')
    api.getArtifact.mockResolvedValue(ready)
    created('FIRST', 'DOC1')
    await vi.waitFor(() => expect(store.artifacts.some(item => item.id === 'DOC1')).toBe(true))
    expect(store.artifacts.filter(item => item.status === 'GENERATING')).toHaveLength(1)
    start('FIRST')
    expect(store.artifacts).toHaveLength(2)
    api.listArtifacts.mockResolvedValue([ready])
    end()
    await pending
    expect(store.artifacts).toHaveLength(1)
  })

  it.each(['FAILED', 'CANCELLED'])('removes waiting UI on %s even if final refresh fails', async status => {
    const pending = store.send('生成一个PPT', [])
    await accepted()
    api.getRun.mockResolvedValue({ id: 'RUN', status })
    api.getConversationMessages.mockRejectedValue(new Error('offline'))
    end(status === 'FAILED' ? 'run.failed' : 'run.cancelled')
    expect(store.artifacts).toHaveLength(0)
    expect(store.messages[1]!.status).toBe(status)
    await pending
    expect(store.sending).toBe(false)
    expect(store.messages[1]!.status).toBe(status)
  })
})
