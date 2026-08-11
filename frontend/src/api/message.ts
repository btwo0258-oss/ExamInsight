import { messageRepository } from '@/repositories/message'
import type { MessageDto, MessageRole } from '@/repositories/message'
import type { ConversationId } from '@/types/contracts/conversation'

export type Message = MessageDto
export type { MessageRole }

export function listMessages(conversationId: ConversationId): Promise<Message[]> {
  return messageRepository.list(conversationId)
}

export function createMessage(payload: {
  conversationId: ConversationId
  role: MessageRole
  content: string
  model?: string
}): Promise<Message> {
  return messageRepository.create(payload)
}
