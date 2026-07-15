import { messageRepository } from '@/repositories/message'
import type { MessageDto, MessageRole } from '@/repositories/message'

export type Message = MessageDto
export type { MessageRole }

export function listMessages(conversationId: number): Promise<Message[]> {
  return messageRepository.list(conversationId)
}

export function createMessage(payload: {
  conversationId: number
  role: MessageRole
  content: string
  model?: string
}): Promise<Message> {
  return messageRepository.create(payload)
}
