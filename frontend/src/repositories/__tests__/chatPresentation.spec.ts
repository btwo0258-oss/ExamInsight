import { afterEach, describe, expect, it, vi } from 'vitest'
import { chatRepository } from '@/repositories/chat'

async function collect(content: string, clientAction?: 'presentation.create' | 'spreadsheet.create') {
  const stream = await chatRepository.stream({
    conversationId: 12,
    content,
    knowledgeBaseId: 3,
    projectId: 7,
    clientAction,
    history: [{ role: 'user', content: 'Java 多态课程笔记' }],
  })
  const events = []
  for await (const event of stream) events.push(event)
  return events
}

describe('Mock chat presentation intent', () => {
  afterEach(() => vi.restoreAllMocks())

  it('returns the shared proposal card for the explicit quick action', async () => {
    vi.spyOn(window, 'setTimeout').mockImplementation((handler: TimerHandler) => {
      if (typeof handler === 'function') handler()
      return 0
    })

    const events = await collect('生成 PPT', 'presentation.create')
    const cardEvent = events.find((event) => event.type === 'presentation-card')

    expect(cardEvent).toMatchObject({
      type: 'presentation-card',
      data: {
        cardType: 'presentation',
        view: 'proposal',
        status: 'draft',
        conversationId: 12,
        knowledgeBaseId: 3,
        projectId: 7,
        config: { pageCount: 8 },
      },
    })
  })

  it('keeps natural-language intent detection inside the Mock repository', async () => {
    vi.spyOn(window, 'setTimeout').mockImplementation((handler: TimerHandler) => {
      if (typeof handler === 'function') handler()
      return 0
    })

    const events = await collect('帮我生成一份关于 Java 多态的 PPT')
    const cardEvent = events.find((event) => event.type === 'presentation-card')

    expect(cardEvent?.type).toBe('presentation-card')
    if (cardEvent?.type === 'presentation-card') {
      expect(cardEvent.data.config.topic).toBe('Java 多态')
      expect(cardEvent.data.config.sourceText).toContain('Java 多态课程笔记')
    }
  })

  it('does not create a PPT card for an unrelated message', async () => {
    vi.spyOn(window, 'setTimeout').mockImplementation((handler: TimerHandler) => {
      if (typeof handler === 'function') handler()
      return 0
    })

    const events = await collect('解释一下 Java 多态')
    expect(events.some((event) => event.type === 'presentation-card')).toBe(false)
  })

  it('starts spreadsheet generation directly and returns the ready task card', async () => {
    vi.spyOn(window, 'setTimeout').mockImplementation((handler: TimerHandler) => {
      if (typeof handler === 'function') handler()
      return 0
    })

    const events = await collect('根据课程笔记生成电子表格，包括课程、负责人和状态', 'spreadsheet.create')
    const cardEvent = events.filter((event) => event.type === 'spreadsheet-card').at(-1)
    expect(cardEvent).toMatchObject({
      type: 'spreadsheet-card',
      data: {
        cardType: 'spreadsheet',
        status: 'ready',
        spreadsheetId: expect.any(String),
        knowledgeBaseId: 3,
        projectId: 7,
        config: { sheetCount: 1 },
      },
    })
  })
})
