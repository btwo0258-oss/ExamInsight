import { request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import type { PresentationChatCardDto } from '@/types/contracts/presentation'
import type { SpreadsheetChatCardDto } from '@/types/contracts/spreadsheet'
import type { LearningProfileData } from '@/types/contracts/learning'
import type { ChatArtifactDto } from '@/types/contracts/artifact'
import type { ConversationId } from '@/types/contracts/conversation'

export type MessageRole = 'user' | 'assistant' | 'system'

export type LearningMessageData = {
  loading?: boolean
  confirmed?: boolean
  profile?: LearningProfileData
  content?: string
  resourceId?: string | null
}

export type MessageDto = {
  id: ConversationId
  conversationId: ConversationId
  parentId?: ConversationId | null
  role: MessageRole
  content: string
  tokenCount?: number
  model?: string | null
  durationMs?: number | null
  sourceChunks?: unknown
  createTime?: string
  turnId?: string | null
  qVersion?: number | null
  aVersion?: number | null
  files?: string | null
  kind?: 'learning-profile' | 'learning-document' | 'presentation' | 'spreadsheet' | null
  learningData?: LearningMessageData | string | null
  presentationData?: PresentationChatCardDto | string | null
  spreadsheetData?: SpreadsheetChatCardDto | string | null
  artifacts?: ChatArtifactDto[] | string | null
  sequence?: number
  citations?: Array<{
    number: number
    assetId: string
    assetName: string
    assetVersionId: string
    chunkId: string
    quotedText: string
    locator?: string | null
    score?: number | null
  }>
}

export interface MessageRepository {
  list(conversationId: ConversationId): Promise<MessageDto[]>
  create(payload: { conversationId: ConversationId; role: MessageRole; content: string; model?: string }): Promise<MessageDto>
}

function isV2ConversationId(id: ConversationId) {
  return typeof id === 'string' && !/^\d+$/.test(id)
}

function normalizeV2Message(conversationId: ConversationId, item: Record<string, unknown>): MessageDto {
  const citations = Array.isArray(item.citations)
    ? item.citations as MessageDto['citations']
    : []
  return {
    id: String(item.id),
    conversationId,
    parentId: item.parentMessageId == null ? null : String(item.parentMessageId),
    role: String(item.role ?? 'assistant').toLowerCase() as MessageRole,
    content: String(item.content ?? ''),
    model: null,
    sourceChunks: citations?.map((citation) => ({
      docName: citation.assetName,
      chunkIndex: citation.number,
      content: citation.quotedText,
      _score: citation.score ?? undefined,
      assetId: citation.assetId,
      assetVersionId: citation.assetVersionId,
      chunkId: citation.chunkId,
      locator: citation.locator ?? undefined,
    })),
    citations,
    sequence: Number(item.sequence ?? 0),
    createTime: String(item.createdAt ?? ''),
  }
}

const mockMessageRepository: MessageRepository = {
  async list() {
    // Mock 消息由 message store 的统一 Mock Session 快照恢复。
    return []
  },
  async create(payload) {
    return { id: Date.now(), ...payload, createTime: new Date().toISOString() }
  },
}

const apiMessageRepository: MessageRepository = {
  async list(conversationId) {
    if (isV2ConversationId(conversationId)) {
      const response = await request.get(
        `/api/v2/conversations/${encodeURIComponent(String(conversationId))}`,
      )
      const detail = response.data as { messages?: Record<string, unknown>[] }
      return (detail.messages ?? [])
        .map((item) => normalizeV2Message(conversationId, item))
        .sort((left, right) => (left.sequence ?? 0) - (right.sequence ?? 0))
    }
    const response = await request.get(`/api/conversation/${conversationId}/messages`)
    return (response.data?.data ?? response.data) as MessageDto[]
  },
  async create(payload) {
    const response = await request.post('/api/chat/stream', {
      conversationId: payload.conversationId,
      message: payload.content,
      model: payload.model,
    })
    return (response.data?.data ?? response.data) as MessageDto
  },
}

export const messageRepository = isMockDataSource ? mockMessageRepository : apiMessageRepository
