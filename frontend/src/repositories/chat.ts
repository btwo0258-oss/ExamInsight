import { getStoredToken } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { parseSseTextStream } from '@/utils/stream'

export type ChatStreamPayload = {
  conversationId: number
  content: string
  model?: string
  kbId?: number | null
  fileContext?: string
  history?: { role: string; content: string }[]
  parentId?: number | null
  isRegenerate?: boolean
  editMsgId?: number | null
  turnId?: string
  qVersion?: number
  aVersion?: number
  files?: string
  mediaAssetIds?: string[]
}

export interface ChatRepository {
  stream(payload: ChatStreamPayload, options?: { signal?: AbortSignal }): Promise<AsyncGenerator<string>>
}

const mockChatRepository: ChatRepository = {
  async stream(payload, options) {
    return (async function* () {
      const mockText = `这是一个模拟回复：${payload.content}`
      for (const character of mockText) {
        if (options?.signal?.aborted) break
        yield character
        await new Promise((resolve) => window.setTimeout(resolve, 35))
      }
    })()
  },
}

const apiChatRepository: ChatRepository = {
  async stream(payload, options) {
    const token = getStoredToken()
    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL ?? ''}/api/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        conversationId: payload.conversationId,
        question: payload.content,
        model: payload.model,
        kbId: payload.kbId,
        fileContext: payload.fileContext,
        history: payload.history,
        parentId: payload.parentId,
        turnId: payload.turnId,
        qVersion: payload.qVersion,
        aVersion: payload.aVersion,
        isRegenerate: payload.isRegenerate,
        editMsgId: payload.editMsgId,
        files: payload.files,
        mediaAssetIds: payload.mediaAssetIds,
      }),
      signal: options?.signal,
    })
    if (!response.ok) {
      const text = await response.text().catch(() => '')
      throw new Error(text || `HTTP ${response.status}`)
    }
    return parseSseTextStream(response)
  },
}

export const chatRepository = isMockDataSource ? mockChatRepository : apiChatRepository
