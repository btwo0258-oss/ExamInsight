import { request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'

export type MessageRole = 'user' | 'assistant' | 'system'

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
    try {
      const response = await request.get(`/api/conversation/${conversationId}/messages`)
      const data = (response.data?.data ?? response.data) as MessageDto[]
      if (!Array.isArray(data)) return []
      return data
    } catch (error) {
      console.error('Failed to load messages:', error)
      return []
    }
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
