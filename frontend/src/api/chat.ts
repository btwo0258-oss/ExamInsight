import { chatRepository } from '@/repositories/chat'
import type { ChatStreamPayload } from '@/repositories/chat'

export type { ChatStreamPayload }

export function streamChat(
  payload: ChatStreamPayload,
  options?: { signal?: AbortSignal },
): Promise<AsyncGenerator<string>> {
  return chatRepository.stream(payload, options)
}
