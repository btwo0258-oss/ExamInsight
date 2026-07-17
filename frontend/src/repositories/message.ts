import { request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import type { PresentationChatCardDto } from '@/types/contracts/presentation'
import type { SpreadsheetChatCardDto } from '@/types/contracts/spreadsheet'
import type { LearningProfileData } from '@/types/contracts/learning'

export type MessageRole = 'user' | 'assistant' | 'system'

export type LearningMessageData = {
  loading?: boolean
  confirmed?: boolean
  profile?: LearningProfileData
  content?: string
  resourceId?: string | null
}

export type MessageDto = {
  id: number
  conversationId: number
  parentId?: number | null
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
}

export interface MessageRepository {
  list(conversationId: number): Promise<MessageDto[]>
  create(payload: { conversationId: number; role: MessageRole; content: string; model?: string }): Promise<MessageDto>
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
