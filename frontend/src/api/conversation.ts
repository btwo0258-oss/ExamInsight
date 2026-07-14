import { request, USER_KEY } from './request'
import { mockEnabled } from '@/mock'

export type Conversation = {
  id: number
  title: string | null
  knowledgeBaseId?: number | null
  isPinned?: boolean
  messageCount?: number
  totalTokens?: number
  updateTime?: string
  createTime?: string
  learningProjectId?: number | null
  learningProjectName?: string
  conversationType?: 'general' | 'learning-setup' | 'learning-tutor'
}

function getMockConvsKey(): string {
  const userStr = sessionStorage.getItem(USER_KEY) || localStorage.getItem(USER_KEY)
  let userPrefix = 'guest'
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      if (user && user.id) userPrefix = String(user.id)
    } catch {}
  }
  return `llm.mock.conversations.${userPrefix}`
}

function getMockConvs(): Conversation[] {
  const key = getMockConvsKey()
  const raw = sessionStorage.getItem(key)
  if (raw) return JSON.parse(raw) as Conversation[]
  return []
}

function saveMockConvs(list: Conversation[]) {
  const key = getMockConvsKey()
  sessionStorage.setItem(key, JSON.stringify(list))
}

export async function listConversations(): Promise<Conversation[]> {
  if (mockEnabled.value) return getMockConvs()
  try {
    const res = await request.get('/api/conversation/list')
    const list = (res.data?.data ?? res.data) as any[]
    return list.map(item => ({
      ...item,
      knowledgeBaseId: item.knowledgeBaseId ?? item.kbId
    })) as Conversation[]
  } catch (err: unknown) {
    if (typeof err === 'object' && err !== null && 'response' in err && (err as { response?: { status?: number } }).response?.status === 404) return getMockConvs()
    throw err
  }
}

export async function createConversation(payload?: { kbId?: number | null; knowledgeBaseId?: number | null; title?: string; learningProjectId?: number | null; learningProjectName?: string; conversationType?: Conversation['conversationType'] }): Promise<Conversation> {
  const actualKbId = payload?.kbId ?? payload?.knowledgeBaseId ?? null
  const reqPayload = { kbId: actualKbId, title: payload?.title }
  if (mockEnabled.value) {
    const list = getMockConvs()
    const next: Conversation = {
      id: Date.now(),
      title: payload?.title || '新对话',
      knowledgeBaseId: actualKbId,
      isPinned: false,
      messageCount: 0,
      updateTime: new Date().toISOString(),
      learningProjectId: payload?.learningProjectId ?? null,
      learningProjectName: payload?.learningProjectName,
      conversationType: payload?.conversationType ?? 'general',
    }
    list.unshift(next)
    saveMockConvs(list)
    return next
  }
  try {
    const res = await request.post('/api/conversation/create', reqPayload)
    const data = (res.data?.data ?? res.data) as any
    return { ...data, knowledgeBaseId: data.knowledgeBaseId ?? data.kbId } as Conversation
  } catch (err: unknown) {
    if (typeof err === 'object' && err !== null && 'response' in err && (err as { response?: { status?: number } }).response?.status === 404) {
      const list = getMockConvs()
      const next: Conversation = {
        id: Date.now(),
        title: payload?.title || '新对话',
        knowledgeBaseId: actualKbId,
        isPinned: false,
        messageCount: 0,
        updateTime: new Date().toISOString(),
        learningProjectId: payload?.learningProjectId ?? null,
        learningProjectName: payload?.learningProjectName,
        conversationType: payload?.conversationType ?? 'general',
      }
      list.unshift(next)
      saveMockConvs(list)
      return next
    }
    throw err
  }
}

export async function updateConversation(
  id: number,
  payload: { title?: string; status?: number; isPinned?: boolean; knowledgeBaseId?: number | null; learningProjectId?: number | null; learningProjectName?: string; conversationType?: Conversation['conversationType'] },
): Promise<Partial<Conversation>> {
  if (mockEnabled.value) {
    const list = getMockConvs()
    const item = list.find((x) => x.id === id)
    if (item) {
      Object.assign(item, payload)
      saveMockConvs(list)
      return payload
    }
    throw new Error('Not found')
  }
  try {
    const res = await request.put(`/api/conversation/${id}`, payload)
    return (res.data?.data ?? res.data) as Conversation
  } catch (err: unknown) {
    if (typeof err === 'object' && err !== null && 'response' in err && (err as { response?: { status?: number } }).response?.status === 404) {
      const list = getMockConvs()
      const item = list.find((x) => x.id === id)
      if (item) {
        Object.assign(item, payload)
        saveMockConvs(list)
        return payload
      }
      throw new Error('Not found')
    }
    throw err
  }
}

export async function deleteConversation(id: number): Promise<void> {
  if (mockEnabled.value) {
    const list = getMockConvs().filter((x) => x.id !== id)
    saveMockConvs(list)
    return
  }
  try {
    await request.delete(`/api/conversation/${id}`)
  } catch (err: unknown) {
    if (typeof err === 'object' && err !== null && 'response' in err && (err as { response?: { status?: number } }).response?.status === 404) {
      const list = getMockConvs().filter((x) => x.id !== id)
      saveMockConvs(list)
      return
    }
    throw err
  }
}
