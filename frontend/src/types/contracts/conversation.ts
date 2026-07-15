import type { EntityId } from './common'

export type ConversationType = 'general' | 'learning-setup' | 'learning-tutor'

export type ConversationDto = {
  id: EntityId
  title: string | null
  knowledgeBaseId?: EntityId | null
  isPinned?: boolean
  messageCount?: number
  totalTokens?: number
  updateTime?: string
  createTime?: string
  learningProjectId?: EntityId | null
  learningProjectName?: string
  conversationType?: ConversationType
}

export type CreateConversationRequest = {
  kbId?: EntityId | null
  title?: string
  learningProjectId?: EntityId | null
  learningProjectName?: string
  conversationType?: ConversationType
}

export type UpdateConversationRequest = {
  title?: string
  status?: number
  isPinned?: boolean
  knowledgeBaseId?: EntityId | null
  learningProjectId?: EntityId | null
  learningProjectName?: string
  conversationType?: ConversationType
}
