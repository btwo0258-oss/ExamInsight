import { chatRepository } from '@/repositories/chat'
import type { ChatStreamEvent, ChatStreamPayload } from '@/repositories/chat'

export type { ChatStreamEvent, ChatStreamPayload }

export function streamChat(
  payload: ChatStreamPayload,
  options?: { signal?: AbortSignal },
): Promise<AsyncGenerator<ChatStreamEvent>> {
  return chatRepository.stream(payload, options)
}
