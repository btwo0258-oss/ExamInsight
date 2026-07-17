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
  projectId?: EntityId | null
  projectName?: string
  conversationType?: ConversationType
}

export type CreateConversationRequest = {
  knowledgeBaseId?: EntityId | null
  title?: string
  projectId?: EntityId | null
  projectName?: string
  conversationType?: ConversationType
}

export type UpdateConversationRequest = {
  title?: string
  status?: number
  isPinned?: boolean
  knowledgeBaseId?: EntityId | null
  projectId?: EntityId | null
  projectName?: string
  conversationType?: ConversationType
}
