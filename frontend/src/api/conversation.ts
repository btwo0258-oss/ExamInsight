import { conversationRepository } from '@/repositories/conversation'
import type {
  ConversationDto,
  CreateConversationRequest,
  UpdateConversationRequest,
} from '@/types/contracts/conversation'

export type Conversation = ConversationDto

export function listConversations(): Promise<Conversation[]> {
  return conversationRepository.list()
}

export function createConversation(payload?: CreateConversationRequest): Promise<Conversation> {
  return conversationRepository.create(payload)
}

export function updateConversation(
  id: number,
  payload: UpdateConversationRequest,
): Promise<Partial<Conversation>> {
  return conversationRepository.update(id, payload)
}

export function deleteConversation(id: number): Promise<void> {
  return conversationRepository.remove(id)
}
