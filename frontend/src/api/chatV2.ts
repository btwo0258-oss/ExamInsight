import axios from 'axios'

import { request, sessionFetch } from '@/api/request'
import { prepareRecognitionAudio } from '@/repositories/media'
import type {
  AiRun,
  Artifact,
  ArtifactContent,
  ChatStreamEvent,
  CreateConversationPayload,
  ConversationDetail,
  ConversationPage,
  ConversationSummary,
  SendMessageAccepted,
} from '@/types/contracts/chatV2'

type ApiErrorBody = {
  error?: { code?: string; message?: string; requestId?: string }
  message?: string
}

export class ChatV2ApiError extends Error {
  constructor(
    message: string,
    readonly code = 'REQUEST_FAILED',
    readonly requestId: string | null = null,
  ) {
    super(message)
    this.name = 'ChatV2ApiError'
  }
}

export function chatError(error: unknown, fallback: string) {
  if (error instanceof ChatV2ApiError) return error
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    const body = error.response?.data
    return new ChatV2ApiError(
      body?.error?.message || body?.message || fallback,
      body?.error?.code,
      body?.error?.requestId ?? null,
    )
  }
  return new ChatV2ApiError(error instanceof Error ? error.message : fallback)
}

async function call<T>(operation: () => Promise<{ data: T }>, fallback: string) {
  try {
    return (await operation()).data
  } catch (error) {
    throw chatError(error, fallback)
  }
}

export function createConversation(payload: CreateConversationPayload) {
  return call(
    () => request.post<ConversationSummary>('/api/v2/conversations', payload),
    '创建对话失败。',
  )
}

export function listConversations(cursor?: string | null, limit = 30) {
  return call(
    () => request.get<ConversationPage>('/api/v2/conversations', {
      params: { cursor: cursor || undefined, limit },
    }),
    '加载对话列表失败。',
  )
}

export function getConversation(conversationId: string) {
  return call(
    () => request.get<ConversationDetail>(`/api/v2/conversations/${conversationId}`),
    '加载对话失败。',
  )
}

export function updateConversation(
  conversationId: string,
  payload: { title?: string; knowledgeBaseId?: string | null; clearKnowledgeBase?: boolean },
) {
  return call(
    () => request.patch<ConversationSummary>(`/api/v2/conversations/${conversationId}`, payload),
    '保存对话失败。',
  )
}

export async function trashConversation(conversationId: string) {
  try {
    await request.delete(`/api/v2/conversations/${conversationId}`)
  } catch (error) {
    throw chatError(error, '删除对话失败。')
  }
}

export function sendMessage(
  conversationId: string,
  payload: { content: string; sourceAssetIds: string[] },
  idempotencyKey: string,
) {
  return call(
    () => request.post<SendMessageAccepted>(
      `/api/v2/conversations/${conversationId}/messages`,
      payload,
      { headers: { 'Idempotency-Key': idempotencyKey }, timeout: 60_000 },
    ),
    '发送消息失败。',
  )
}

export function editMessage(
  conversationId: string,
  messageId: string,
  content: string,
  idempotencyKey: string,
) {
  return call(
    () => request.post<SendMessageAccepted>(
      `/api/v2/conversations/${conversationId}/messages/${messageId}/edit`,
      { content },
      { headers: { 'Idempotency-Key': idempotencyKey }, timeout: 60_000 },
    ),
    '编辑消息失败。',
  )
}

export function regenerateMessage(
  conversationId: string,
  messageId: string,
  idempotencyKey: string,
) {
  return call(
    () => request.post<SendMessageAccepted>(
      `/api/v2/conversations/${conversationId}/messages/${messageId}/regenerate`,
      undefined,
      { headers: { 'Idempotency-Key': idempotencyKey }, timeout: 60_000 },
    ),
    '重新生成失败。',
  )
}

export function activateBranch(conversationId: string, branchId: string) {
  return call(
    () => request.put<ConversationDetail>(
      `/api/v2/conversations/${conversationId}/branches/${branchId}/active`,
    ),
    '切换消息版本失败。',
  )
}

export function getRun(runId: string) {
  return call(() => request.get<AiRun>(`/api/v2/ai-runs/${runId}`), '获取生成状态失败。')
}

export function cancelRun(runId: string) {
  return call(() => request.post<AiRun>(`/api/v2/ai-runs/${runId}/cancel`), '停止生成失败。')
}

export function listArtifacts(conversationId: string) {
  return call(
    () => request.get<Artifact[]>('/api/v2/artifacts', { params: { conversationId } }),
    '加载生成内容失败。',
  )
}

export function getArtifact(artifactId: string) {
  return call(
    () => request.get<Artifact>(`/api/v2/artifacts/${artifactId}`),
    '加载生成内容失败。',
  )
}

export function updateArtifact(
  artifactId: string,
  payload: { title: string; content: ArtifactContent; version: number },
) {
  return call(
    () => request.patch<Artifact>(`/api/v2/artifacts/${artifactId}`, payload),
    '保存草稿失败。',
  )
}

export function confirmArtifact(artifactId: string) {
  return call(
    () => request.post<Artifact>(`/api/v2/artifacts/${artifactId}/confirm`),
    '确认生成内容失败。',
  )
}

export type ChatTranscription = {
  text: string
  language: string
}

export async function transcribeChatAudio(file: File, signal?: AbortSignal) {
  try {
    const recognitionFile = await prepareRecognitionAudio(file)
    const formData = new FormData()
    formData.append('file', recognitionFile)
    const response = await request.post<ChatTranscription>('/api/v2/media/transcriptions', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      signal,
      timeout: 120_000,
    })
    return response.data
  } catch (error) {
    throw chatError(error, '语音识别失败，请稍后重试。')
  }
}

export async function synthesizeSpeech(text: string, signal?: AbortSignal) {
  try {
    const response = await request.post<Blob>('/api/v2/media/speech', { text }, {
      responseType: 'blob',
      signal,
      timeout: 120_000,
    })
    return response.data
  } catch (error) {
    throw chatError(error, '朗读服务暂时不可用，请稍后重试。')
  }
}

function parseEventBlock(block: string): ChatStreamEvent | null {
  let id = ''
  let event = 'message'
  const data: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue
    const separator = line.indexOf(':')
    const field = separator === -1 ? line : line.slice(0, separator)
    const value = separator === -1 ? '' : line.slice(separator + 1).replace(/^ /, '')
    if (field === 'id') id = value
    else if (field === 'event') event = value
    else if (field === 'data') data.push(value)
  }
  if (!data.length) return null
  try {
    const parsed = JSON.parse(data.join('\n'))
    return {
      id,
      event,
      data: parsed && typeof parsed === 'object' ? parsed as Record<string, unknown> : { value: parsed },
    }
  } catch {
    return { id, event, data: { value: data.join('\n') } }
  }
}

export async function streamRunEvents(
  runId: string,
  options: {
    signal: AbortSignal
    lastEventId?: string | null
    onEvent: (event: ChatStreamEvent) => void
  },
) {
  const headers: Record<string, string> = { Accept: 'text/event-stream' }
  if (options.lastEventId) headers['Last-Event-ID'] = options.lastEventId
  const response = await sessionFetch(`/api/v2/ai-runs/${runId}/events`, {
    headers,
    signal: options.signal,
  })
  if (!response.ok || !response.body) {
    throw new ChatV2ApiError(`连接生成流失败（${response.status}）。`, 'STREAM_CONNECT_FAILED')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
    let boundary = buffer.indexOf('\n\n')
    while (boundary !== -1) {
      const event = parseEventBlock(buffer.slice(0, boundary))
      buffer = buffer.slice(boundary + 2)
      if (event) options.onEvent(event)
      boundary = buffer.indexOf('\n\n')
    }
    if (done) break
  }
  const tail = parseEventBlock(buffer.trim())
  if (tail) options.onEvent(tail)
}
