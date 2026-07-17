import { getStoredToken } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { spreadsheetRepository } from '@/repositories/spreadsheet'
import type { PresentationChatCardDto } from '@/types/contracts/presentation'
import type { SpreadsheetChatCardDto } from '@/types/contracts/spreadsheet'
import { toSpreadsheetChatCard } from '@/utils/spreadsheet'
import { parseSseEventStream } from '@/utils/stream'

export type ChatClientAction = 'presentation.create' | 'spreadsheet.create'

export type ChatStreamEvent =
  | { type: 'text-delta'; delta: string }
  | { type: 'presentation-card'; data: PresentationChatCardDto }
  | { type: 'spreadsheet-card'; data: SpreadsheetChatCardDto }

export type ChatStreamPayload = {
  conversationId: number
  content: string
  model?: string
  knowledgeBaseId?: number | null
  fileContext?: string
  history?: { role: string; content: string }[]
  parentId?: number | null
  isRegenerate?: boolean
  editMsgId?: number | null
  turnId?: string
  qVersion?: number
  aVersion?: number
  files?: string
  mediaAssetIds?: string[]
  projectId?: number | null
  stageId?: number | string | null
  taskId?: number | string | null
  exerciseId?: number | string | null
  clientAction?: ChatClientAction
}

export interface ChatRepository {
  stream(payload: ChatStreamPayload, options?: { signal?: AbortSignal }): Promise<AsyncGenerator<ChatStreamEvent>>
}

function presentationIntent(content: string) {
  const hasTarget = /(?:ppt|幻灯片|演示文稿)/i.test(content)
  const hasAction = /(?:生成|制作|创建|整理|转换|转成|做一份|做个)/.test(content)
  return hasTarget && hasAction
}

function spreadsheetIntent(content: string) {
  const hasTarget = /(?:excel|xlsx|电子表格|数据表|表格)/i.test(content)
  const hasAction = /(?:生成|制作|创建|整理|转换|转成|做一份|做个)/.test(content)
  return hasTarget && hasAction
}

function inferPresentationTopic(content: string) {
  const about = content.match(/(?:关于|主题(?:是|为)?)[：:\s]*([^，。！？]{2,80})/i)?.[1]
  if (about) return about.replace(/的?\s*(?:ppt|幻灯片|演示文稿).*$/i, '').trim()

  const simplified = content
    .replace(/(?:请|帮我|麻烦|把|将|这些|当前|内容|整理成|转换成|转成|生成|制作|创建|做一份|做个|一份|一个)/g, ' ')
    .replace(/(?:ppt|幻灯片|演示文稿)/gi, ' ')
    .replace(/[：:，。！？]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return simplified.length >= 2 ? simplified.slice(0, 120) : ''
}

function createMockPresentationCard(payload: ChatStreamPayload): PresentationChatCardDto {
  const sourceText = (payload.history ?? [])
    .filter((item) => item.content.trim())
    .slice(-8)
    .map((item) => `${item.role === 'assistant' ? 'AI' : '用户'}：${item.content}`)
    .join('\n')
    .slice(0, 6000)
  const topic = inferPresentationTopic(payload.content)

  return {
    cardType: 'presentation',
    view: 'proposal',
    status: 'draft',
    conversationId: payload.conversationId,
    knowledgeBaseId: payload.knowledgeBaseId ?? null,
    projectId: payload.projectId ?? null,
    config: {
      topic,
      title: topic,
      pageCount: 8,
      templateId: 'ink-focus',
      aspectRatio: '16:9',
      style: 'academic',
      audience: 'student',
      language: 'zh-CN',
      sourceText: sourceText || undefined,
    },
  }
}

function textDelta(data: string) {
  const trimmed = data.trim()
  if (!trimmed) return ''
  if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
    try {
      const json = JSON.parse(trimmed) as {
        delta?: unknown
        content?: unknown
        text?: unknown
        choices?: Array<{ delta?: { content?: string } }>
      }
      const direct = json.delta ?? json.content ?? json.text
      if (typeof direct === 'string') return direct
      const choice = json.choices?.[0]?.delta?.content
      if (typeof choice === 'string') return choice
    } catch {
      return data
    }
  }
  return data
}

function parsePresentationCard(data: string): PresentationChatCardDto | null {
  try {
    const parsed = JSON.parse(data) as PresentationChatCardDto | { data?: PresentationChatCardDto }
    const wrapped = parsed as { data?: PresentationChatCardDto }
    const card = wrapped.data ?? parsed as PresentationChatCardDto
    return card.cardType === 'presentation' ? card : null
  } catch {
    return null
  }
}

function parseSpreadsheetCard(data: string): SpreadsheetChatCardDto | null {
  try {
    const parsed = JSON.parse(data) as SpreadsheetChatCardDto | { data?: SpreadsheetChatCardDto }
    const wrapped = parsed as { data?: SpreadsheetChatCardDto }
    const card = wrapped.data ?? parsed as SpreadsheetChatCardDto
    return card.cardType === 'spreadsheet' ? card : null
  } catch {
    return null
  }
}

const mockChatRepository: ChatRepository = {
  async stream(payload, options) {
    return (async function* () {
      if (payload.clientAction === 'presentation.create' || presentationIntent(payload.content)) {
        const mockText = '我先确认演示主题。你可以直接生成大纲，也可以进入完整配置。'
        for (const character of mockText) {
          if (options?.signal?.aborted) return
          yield { type: 'text-delta', delta: character } as const
          await new Promise((resolve) => window.setTimeout(resolve, 12))
        }
        if (!options?.signal?.aborted) {
          yield { type: 'presentation-card', data: createMockPresentationCard(payload) } as const
        }
        return
      }

      if (payload.clientAction === 'spreadsheet.create' || spreadsheetIntent(payload.content)) {
        const mockText = '我会根据当前对话、附件和已关联资料直接生成电子表格。'
        for (const character of mockText) {
          if (options?.signal?.aborted) return
          yield { type: 'text-delta', delta: character } as const
          await new Promise((resolve) => window.setTimeout(resolve, 12))
        }
        if (options?.signal?.aborted) return
        let spreadsheet = await spreadsheetRepository.create({
          prompt: payload.content,
          conversationId: payload.conversationId,
          knowledgeBaseId: payload.knowledgeBaseId ?? null,
          projectId: payload.projectId ?? null,
          mediaAssetIds: payload.mediaAssetIds,
          clientRequestId: `chat-spreadsheet:${payload.conversationId}:${payload.turnId || Date.now()}`,
        })
        yield { type: 'spreadsheet-card', data: toSpreadsheetChatCard(spreadsheet) } as const
        while (spreadsheet.status === 'generating' && spreadsheet.activeJobId) {
          await new Promise((resolve) => window.setTimeout(resolve, 380))
          if (options?.signal?.aborted) return
          const job = await spreadsheetRepository.getJob<{ spreadsheetId: string }>(spreadsheet.activeJobId)
          if (job.status === 'failed' || job.status === 'cancelled') break
          spreadsheet = await spreadsheetRepository.get(spreadsheet.id)
        }
        yield { type: 'spreadsheet-card', data: toSpreadsheetChatCard(spreadsheet) } as const
        return
      }

      const mockText = `这是一个模拟回答：${payload.content}`
      for (const character of mockText) {
        if (options?.signal?.aborted) break
        yield { type: 'text-delta', delta: character } as const
        await new Promise((resolve) => window.setTimeout(resolve, 35))
      }
    })()
  },
}

const apiChatRepository: ChatRepository = {
  async stream(payload, options) {
    const token = getStoredToken()
    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL ?? ''}/api/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        conversationId: payload.conversationId,
        question: payload.content,
        model: payload.model,
        kbId: payload.knowledgeBaseId,
        fileContext: payload.fileContext,
        history: payload.history,
        parentId: payload.parentId,
        turnId: payload.turnId,
        qVersion: payload.qVersion,
        aVersion: payload.aVersion,
        isRegenerate: payload.isRegenerate,
        editMsgId: payload.editMsgId,
        files: payload.files,
        mediaAssetIds: payload.mediaAssetIds,
        projectId: payload.projectId,
        stageId: payload.stageId,
        taskId: payload.taskId,
        exerciseId: payload.exerciseId,
        clientAction: payload.clientAction,
      }),
      signal: options?.signal,
    })
    if (!response.ok) {
      const errorBody = await response.text().catch(() => '')
      throw new Error(errorBody || `HTTP ${response.status}`)
    }

    return (async function* () {
      for await (const event of parseSseEventStream(response)) {
        if (event.data === '[DONE]' || event.event === 'done') return
        if (event.event === 'error') throw new Error(event.data)

        if (event.event === 'presentation-card' || event.event === 'presentation_card') {
          const card = parsePresentationCard(event.data)
          if (card) yield { type: 'presentation-card', data: card } as const
          continue
        }

        if (event.event === 'spreadsheet-card' || event.event === 'spreadsheet_card') {
          const card = parseSpreadsheetCard(event.data)
          if (card) yield { type: 'spreadsheet-card', data: card } as const
          continue
        }

        if (event.event && event.event !== 'message' && event.event !== 'delta') continue
        const card = parsePresentationCard(event.data)
        if (card) {
          yield { type: 'presentation-card', data: card } as const
          continue
        }
        const spreadsheetCard = parseSpreadsheetCard(event.data)
        if (spreadsheetCard) {
          yield { type: 'spreadsheet-card', data: spreadsheetCard } as const
          continue
        }
        const delta = textDelta(event.data)
        if (delta) yield { type: 'text-delta', delta } as const
      }
    })()
  },
}

export const chatRepository = isMockDataSource ? mockChatRepository : apiChatRepository
