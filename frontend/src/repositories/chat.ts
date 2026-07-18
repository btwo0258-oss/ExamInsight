import { getStoredToken, request } from '@/api/request'
import { generateMindMapFromAi } from '@/api/mindmap'
import { isMockDataSource } from '@/config/dataSource'
import { spreadsheetRepository } from '@/repositories/spreadsheet'
import type { ChatArtifactDto } from '@/types/contracts/artifact'
import type { PresentationChatCardDto } from '@/types/contracts/presentation'
import type { SpreadsheetChatCardDto } from '@/types/contracts/spreadsheet'
import { toSpreadsheetChatCard } from '@/utils/spreadsheet'
import { spreadsheetCardToArtifact } from '@/utils/artifact'
import { parseSseEventStream } from '@/utils/stream'

export type ChatClientAction = 'presentation.create' | 'spreadsheet.create'

export type ChatStreamEvent =
  | { type: 'text-delta'; delta: string }
  | { type: 'artifact'; data: ChatArtifactDto }
  | { type: 'presentation-card'; data: PresentationChatCardDto }
  | { type: 'spreadsheet-card'; data: SpreadsheetChatCardDto }

export type ChatStreamPayload = {
  conversationId: number
  content: string
  model?: string
  knowledgeBaseId?: number | null
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
  projectId?: number | null
  stageId?: number | string | null
  taskId?: number | string | null
  exerciseId?: number | string | null
  clientAction?: ChatClientAction
}

export type RetryChatArtifactRequest = {
  artifact: ChatArtifactDto
  conversationId: number
  sourceMessageId: string
  clientRequestId: string
}

export interface ChatRepository {
  stream(payload: ChatStreamPayload, options?: { signal?: AbortSignal }): Promise<AsyncGenerator<ChatStreamEvent>>
  retryArtifact(input: RetryChatArtifactRequest): Promise<ChatArtifactDto>
  getArtifact(artifactId: string): Promise<ChatArtifactDto>
}

function presentationIntent(content: string) {
  const hasTarget = /(?:ppt|幻灯片|演示文稿)/i.test(content)
  const hasAction = /(?:生成|制作|创建|整理|转换|转成|做一份|做个)/.test(content)
  return hasTarget && hasAction
}

function spreadsheetIntent(content: string) {
  const hasTarget = /(?:excel|xlsx|电子表格|数据表|表格)/i.test(content)
  const hasAction = /(?:生成|制作|创建|整理|转换|转成|做一份|做个)/.test(content)
  return hasTarget && hasAction
}

function imageIntent(content: string) {
  return /(?:生成|制作|创建|画|绘制).{0,16}(?:图片|插图|配图|海报)/i.test(content)
}

function mindMapIntent(content: string) {
  return /(?:生成|制作|创建|整理).{0,16}(?:思维导图|脑图|知识图谱)/i.test(content)
}

function documentFormats(content: string) {
  const hasAction = /(?:生成|制作|创建|导出|整理成|写一份|做一份)/.test(content)
  if (!hasAction) return [] as Array<'docx' | 'pdf'>
  const formats: Array<'docx' | 'pdf'> = []
  if (/(?:docx|word|文档)/i.test(content)) formats.push('docx')
  if (/(?:pdf)/i.test(content)) formats.push('pdf')
  return formats
}

function inferArtifactTopic(content: string, fallback: string) {
  const topic = content.match(/(?:关于|主题(?:是|为)?)[：:\s]*([^，。！？/]{2,48})/i)?.[1]
  if (topic) return topic.trim()
  const simplified = content
    .replace(/(?:请|帮我|麻烦|生成|制作|创建|导出|整理成|写一份|做一份|一个|一份)/g, ' ')
    .replace(/(?:docx|word|pdf|xlsx|excel|电子表格|数据表|表格|图片|插图|配图|海报|思维导图|脑图|知识图谱)/gi, ' ')
    .replace(/[：:，。！？/]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return simplified.slice(0, 36) || fallback
}

function mockImageUrl(title: string) {
  const safeTitle = title.replace(/[<>&'\"]/g, '').slice(0, 32)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="720" viewBox="0 0 1200 720"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#111827"/><stop offset="1" stop-color="#4f46e5"/></linearGradient></defs><rect width="1200" height="720" fill="url(#g)"/><circle cx="980" cy="120" r="210" fill="#8b5cf6" opacity=".28"/><circle cx="180" cy="650" r="280" fill="#06b6d4" opacity=".2"/><text x="80" y="330" fill="white" font-family="Arial,sans-serif" font-size="62" font-weight="700">${safeTitle}</text><text x="84" y="390" fill="#c7d2fe" font-family="Arial,sans-serif" font-size="26">AI 生成图片 · Mock 预览</text></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

function baseMockArtifact(payload: ChatStreamPayload, input: Pick<ChatArtifactDto, 'artifactId' | 'title' | 'fileName' | 'fileType' | 'format' | 'mimeType' | 'preview'>): ChatArtifactDto {
  return {
    ...input,
    jobId: `job:${input.artifactId}`,
    conversationId: payload.conversationId,
    projectId: payload.projectId ?? null,
    knowledgeBaseId: payload.knowledgeBaseId ?? null,
    status: 'queued',
    progress: 0,
  }
}

async function createMockArtifacts(payload: ChatStreamPayload): Promise<ChatArtifactDto[]> {
  const stamp = `${payload.conversationId}-${payload.turnId || Date.now()}`
  if (spreadsheetIntent(payload.content)) {
    const spreadsheet = await spreadsheetRepository.create({
      prompt: payload.content,
      conversationId: payload.conversationId,
      knowledgeBaseId: payload.knowledgeBaseId ?? null,
      projectId: payload.projectId ?? null,
      mediaAssetIds: payload.mediaAssetIds,
      clientRequestId: `chat-spreadsheet:${stamp}`,
    })
    const artifact = spreadsheetCardToArtifact(toSpreadsheetChatCard(spreadsheet))
    const firstSheet = spreadsheet.workbook.sheets[0]
    artifact.preview = firstSheet ? {
      kind: 'spreadsheet',
      table: { sheetName: firstSheet.name, columns: firstSheet.columns.slice(0, 6), rows: firstSheet.rows.slice(0, 5).map((row) => row.slice(0, 6)) },
    } : { kind: 'spreadsheet' }
    artifact.jobId = spreadsheet.activeJobId
    return [artifact]
  }

  if (mindMapIntent(payload.content)) {
    const title = inferArtifactTopic(payload.content, '学习主题')
    const generated = await generateMindMapFromAi(payload.content, title)
    return [baseMockArtifact(payload, {
      artifactId: `mindmap:${generated.id}`,
      title,
      fileName: `${title}.mindmap`,
      fileType: 'mindmap',
      format: '思维导图',
      mimeType: 'application/vnd.examinsight.mindmap+json',
      preview: { kind: 'mindmap', mindMap: generated.treeData, mindMapConfig: generated.renderConfig },
    })]
  }

  if (imageIntent(payload.content)) {
    const title = inferArtifactTopic(payload.content, '学习主题插图')
    return [baseMockArtifact(payload, {
      artifactId: `image:${stamp}`,
      title,
      fileName: `${title}.svg`,
      fileType: 'image',
      format: 'SVG',
      mimeType: 'image/svg+xml',
      preview: { kind: 'image', imageUrl: mockImageUrl(title) },
    })]
  }

  const formats = documentFormats(payload.content)
  const title = inferArtifactTopic(payload.content, 'AI 生成文档')
  return formats.map((format) => baseMockArtifact(payload, {
    artifactId: `${format}:${stamp}`,
    title,
    fileName: `${title}.${format}`,
    fileType: format === 'pdf' ? 'pdf' : 'document',
    format: format.toUpperCase(),
    mimeType: format === 'pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    preview: {
      kind: 'document',
      text: `${title}\n\n这是一份由当前对话生成的 Mock 文档预览。正式环境中，文件内容由后端生成服务写入对象存储，并以同一个 resourceId 出现在对话和资料库中。`,
    },
  }))
}

function inferPresentationTopic(content: string) {
  const about = content.match(/(?:关于|主题(?:是|为)?)[：:\s]*([^，。！？]{2,80})/i)?.[1]
  if (about) return about.replace(/的?\s*(?:ppt|幻灯片|演示文稿).*$/i, '').trim()

  const simplified = content
    .replace(/(?:请|帮我|麻烦|把|将|这些|当前|内容|整理成|转换成|转成|生成|制作|创建|做一份|做个|一份|一个)/g, ' ')
    .replace(/(?:ppt|幻灯片|演示文稿)/gi, ' ')
    .replace(/[：:，。！？]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return simplified.length >= 2 ? simplified.slice(0, 120) : ''
}

function createMockPresentationCard(payload: ChatStreamPayload): PresentationChatCardDto {
  const sourceText = (payload.history ?? [])
    .filter((item) => item.content.trim())
    .slice(-8)
    .map((item) => `${item.role === 'assistant' ? 'AI' : '用户'}：${item.content}`)
    .join('\n')
    .slice(0, 6000)
  const topic = inferPresentationTopic(payload.content)

  return {
    cardType: 'presentation',
    view: 'proposal',
    status: 'draft',
    conversationId: payload.conversationId,
    knowledgeBaseId: payload.knowledgeBaseId ?? null,
    projectId: payload.projectId ?? null,
    config: {
      topic,
      title: topic,
      pageCount: 8,
      templateId: 'ink-focus',
      aspectRatio: '16:9',
      style: 'academic',
      audience: 'student',
      language: 'zh-CN',
      sourceText: sourceText || undefined,
    },
  }
}

function textDelta(data: string) {
  const trimmed = data.trim()
  if (!trimmed) return ''
  if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
    try {
      const json = JSON.parse(trimmed) as {
        delta?: unknown
        content?: unknown
        text?: unknown
        choices?: Array<{ delta?: { content?: string } }>
      }
      const direct = json.delta ?? json.content ?? json.text
      if (typeof direct === 'string') return direct
      const choice = json.choices?.[0]?.delta?.content
      if (typeof choice === 'string') return choice
    } catch {
      return data
    }
  }
  return data
}

function parsePresentationCard(data: string): PresentationChatCardDto | null {
  try {
    const parsed = JSON.parse(data) as PresentationChatCardDto | { data?: PresentationChatCardDto }
    const wrapped = parsed as { data?: PresentationChatCardDto }
    const card = wrapped.data ?? parsed as PresentationChatCardDto
    return card.cardType === 'presentation' ? card : null
  } catch {
    return null
  }
}

function parseSpreadsheetCard(data: string): SpreadsheetChatCardDto | null {
  try {
    const parsed = JSON.parse(data) as SpreadsheetChatCardDto | { data?: SpreadsheetChatCardDto }
    const wrapped = parsed as { data?: SpreadsheetChatCardDto }
    const card = wrapped.data ?? parsed as SpreadsheetChatCardDto
    return card.cardType === 'spreadsheet' ? card : null
  } catch {
    return null
  }
}

function parseArtifact(data: string): ChatArtifactDto | null {
  try {
    const parsed = JSON.parse(data) as ChatArtifactDto | { data?: ChatArtifactDto; artifact?: ChatArtifactDto }
    const artifact = 'artifact' in parsed && parsed.artifact
      ? parsed.artifact
      : 'data' in parsed && parsed.data
        ? parsed.data
        : parsed as ChatArtifactDto
    return artifact?.artifactId && artifact?.preview ? artifact : null
  } catch {
    return null
  }
}

const mockChatRepository: ChatRepository = {
  async stream(payload, options) {
    return (async function* () {
      if (payload.clientAction === 'presentation.create' || presentationIntent(payload.content)) {
        const mockText = '我先确认演示主题。你可以直接生成大纲，也可以进入完整配置。'
        for (const character of mockText) {
          if (options?.signal?.aborted) return
          yield { type: 'text-delta', delta: character } as const
          await new Promise((resolve) => window.setTimeout(resolve, 12))
        }
        if (!options?.signal?.aborted) {
          yield { type: 'presentation-card', data: createMockPresentationCard(payload) } as const
        }
        return
      }

      if (payload.clientAction === 'spreadsheet.create' || spreadsheetIntent(payload.content)) {
        // Spreadsheet now follows the same artifact stream as every other file.
      }

      const artifacts = await createMockArtifacts(payload)
      if (artifacts.length) {
        const mockText = `我会根据当前对话生成 ${artifacts.map((item) => item.format).join(' 和 ')} 文件。生成完成后会自动归档到资料库。`
        for (const character of mockText) {
          if (options?.signal?.aborted) return
          yield { type: 'text-delta', delta: character } as const
          await new Promise((resolve) => window.setTimeout(resolve, 10))
        }
        for (const artifact of artifacts) {
          yield { type: 'artifact', data: artifact } as const
          for (const progress of [28, 62, 88]) {
            await new Promise((resolve) => window.setTimeout(resolve, 260))
            if (options?.signal?.aborted) return
            yield { type: 'artifact', data: { ...artifact, status: 'generating', progress } } as const
          }
          let ready = { ...artifact, resourceId: `chat:${artifact.artifactId}`, status: 'ready' as const, progress: 100, sizeBytes: artifact.fileType === 'image' ? 384_000 : 128_000 }
          if (artifact.fileType === 'spreadsheet') {
            const spreadsheetId = artifact.artifactId.replace(/^spreadsheet:/, '')
            let spreadsheet = await spreadsheetRepository.get(spreadsheetId)
            while (spreadsheet.status === 'generating' && spreadsheet.activeJobId) {
              await new Promise((resolve) => window.setTimeout(resolve, 220))
              if (options?.signal?.aborted) return
              await spreadsheetRepository.getJob(spreadsheet.activeJobId)
              spreadsheet = await spreadsheetRepository.get(spreadsheetId)
            }
            ready = { ...ready, resourceId: spreadsheet.resourceId || ready.resourceId, fileName: spreadsheet.fileName || ready.fileName, sizeBytes: spreadsheet.fileSize || ready.sizeBytes, editable: false, editorRoute: undefined }
          } else if (artifact.fileType === 'mindmap') {
            ready = { ...ready, editorRoute: `/mindmap/${artifact.artifactId.replace(/^mindmap:/, '')}`, editable: true }
          }
          yield { type: 'artifact', data: ready } as const
        }
        return
      }

      const wantsCode = /(?:代码|code|typescript|javascript|python|java)/i.test(payload.content)
      const mockText = wantsCode
        ? `下面通过 **SSE** 流式返回 Markdown，并渲染代码块：\n\n\`\`\`ts\nfunction summarize(topic: string) {\n  return \`正在整理：\${topic}\`\n}\n\`\`\`\n\n你可以继续要求我把结果生成成文件。`
        : `### Mock 流式回答\n\n我已经收到你的问题：**${payload.content}**\n\n- 文本使用 SSE 增量输出\n- Markdown 会实时渲染\n- 文件生成会显示为统一附件卡片`
      for (const character of mockText) {
        if (options?.signal?.aborted) break
        yield { type: 'text-delta', delta: character } as const
        await new Promise((resolve) => window.setTimeout(resolve, 35))
      }
    })()
  },
  async retryArtifact(input) {
    await new Promise((resolve) => window.setTimeout(resolve, 180))
    return {
      ...input.artifact,
      artifactId: input.artifact.artifactId,
      resourceId: input.artifact.resourceId || `chat:${input.artifact.artifactId}`,
      status: 'ready',
      progress: 100,
      errorCode: undefined,
      errorMessage: undefined,
    }
  },
  async getArtifact() {
    throw new Error('Mock 重试会直接返回最终状态')
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
        kbId: payload.knowledgeBaseId,
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
        projectId: payload.projectId,
        stageId: payload.stageId,
        taskId: payload.taskId,
        exerciseId: payload.exerciseId,
        clientAction: payload.clientAction,
      }),
      signal: options?.signal,
    })
    if (!response.ok) {
      const errorBody = await response.text().catch(() => '')
      throw new Error(errorBody || `HTTP ${response.status}`)
    }

    return (async function* () {
      for await (const event of parseSseEventStream(response)) {
        if (event.data === '[DONE]' || event.event === 'done') return
        if (event.event === 'error') throw new Error(event.data)

        if (event.event === 'presentation-card' || event.event === 'presentation_card') {
          const card = parsePresentationCard(event.data)
          if (card) yield { type: 'presentation-card', data: card } as const
          continue
        }

        if (event.event === 'spreadsheet-card' || event.event === 'spreadsheet_card') {
          const card = parseSpreadsheetCard(event.data)
          if (card) yield { type: 'spreadsheet-card', data: card } as const
          continue
        }

        if (event.event === 'artifact' || event.event === 'artifact-upsert' || event.event === 'artifact_upsert') {
          const artifact = parseArtifact(event.data)
          if (artifact) yield { type: 'artifact', data: artifact } as const
          continue
        }

        if (event.event && event.event !== 'message' && event.event !== 'delta') continue
        const card = parsePresentationCard(event.data)
        if (card) {
          yield { type: 'presentation-card', data: card } as const
          continue
        }
        const spreadsheetCard = parseSpreadsheetCard(event.data)
        if (spreadsheetCard) {
          yield { type: 'spreadsheet-card', data: spreadsheetCard } as const
          continue
        }
        const artifact = parseArtifact(event.data)
        if (artifact) {
          yield { type: 'artifact', data: artifact } as const
          continue
        }
        const delta = textDelta(event.data)
        if (delta) yield { type: 'text-delta', delta } as const
      }
    })()
  },
  async retryArtifact(input) {
    const { artifact, ...context } = input
    const response = await request.post(`/api/chat/artifacts/${encodeURIComponent(artifact.artifactId)}/retry`, {
      ...context,
      projectId: artifact.projectId ?? null,
      knowledgeBaseId: artifact.knowledgeBaseId ?? null,
      learningResourceId: artifact.learningResourceId ?? null,
      resourceId: artifact.resourceId,
    })
    return (response.data?.data ?? response.data) as ChatArtifactDto
  },
  async getArtifact(artifactId) {
    const response = await request.get(`/api/chat/artifacts/${encodeURIComponent(artifactId)}`)
    return (response.data?.data ?? response.data) as ChatArtifactDto
  },
}

export const chatRepository = isMockDataSource ? mockChatRepository : apiChatRepository
