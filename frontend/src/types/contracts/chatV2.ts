export type ConversationStatus = 'ACTIVE' | 'TRASHED'
export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL'
export type RunStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
export type ArtifactType = 'DOCUMENT' | 'MINDMAP' | 'PRESENTATION' | 'IMAGE'

export type ConversationSummary = {
  id: string
  title: string
  type: string
  status: ConversationStatus | string
  knowledgeBaseId: string | null
  activeBranchId: string
  messageCount: number
  version: number
  lastMessageAt: string | null
  createdAt: string
  updatedAt: string
}

export type Citation = {
  number: number
  assetId: string
  assetName: string
  assetVersionId: string
  chunkId: string
  quotedText: string
  locator: string | null
  score: number | null
}

export type MessageAttachment = {
  assetId: string
  assetVersionId: string
  name: string
  mimeType: string
  sizeBytes: number
  assetType: string
}

export type ChatMessage = {
  id: string
  branchId: string
  versionGroupId: string
  parentMessageId: string | null
  role: MessageRole | string
  status: string
  sequence: number
  content: string
  runId: string | null
  attachments: MessageAttachment[]
  citations: Citation[]
  createdAt: string
  finalizedAt: string | null
}

export type MessageVersion = {
  messageId: string
  branchId: string
  createdAt: string
}

export type MessageVersionGroup = {
  id: string
  role: string
  versions: MessageVersion[]
}

export type ConversationDetail = {
  conversation: ConversationSummary
  messages: ChatMessage[]
  versionGroups: MessageVersionGroup[]
}

export type ConversationPage = {
  items: ConversationSummary[]
  nextCursor: string | null
  hasMore: boolean
}

export type CreateConversationPayload = {
  conversationId?: string
  title?: string
  knowledgeBaseId?: string | null
}

export type SendMessageAccepted = {
  userMessageId: string
  assistantMessageId: string
  runId: string
  eventUrl: string
}

export type AiRun = {
  id: string
  conversationId: string
  branchId: string
  requestMessageId: string
  responseMessageId: string
  status: RunStatus | string
  stage: string
  cancellable: boolean
  errorCode: string | null
  safeErrorMessage: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
}

export type MindMapNode = { text: string; children: MindMapNode[] }
export type PresentationSlide = { title: string; bullets: string[]; speakerNotes?: string | null }

export type ArtifactContent = {
  markdown?: string
  root?: MindMapNode
  slides?: PresentationSlide[]
  prompt?: string
  width?: number
  height?: number
  [key: string]: unknown
}

export type Artifact = {
  id: string
  conversationId: string
  runId: string
  type: ArtifactType
  status: string
  title: string
  schemaVersion: number
  content: ArtifactContent
  revision: number
  version: number
  confirmedAssetId: string | null
  confirmedAssetVersionId: string | null
  errorCode: string | null
  createdAt: string
  updatedAt: string
  confirmedAt: string | null
}

export type StreamEventName =
  | 'run.accepted'
  | 'run.stage_changed'
  | 'message.delta'
  | 'artifact.created'
  | 'usage'
  | 'run.completed'
  | 'run.failed'
  | 'run.cancelled'

export type ChatStreamEvent = {
  id: string
  event: StreamEventName | string
  data: Record<string, unknown>
}
