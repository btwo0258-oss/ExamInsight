import type { EntityId } from './common'
import type { ResourceFileType } from './library'

export type ChatArtifactStatus = 'queued' | 'generating' | 'ready' | 'failed' | 'cancelled'

export type MindMapTreeNode = {
  data: { text: string; [key: string]: unknown }
  children?: MindMapTreeNode[]
}

export type ArtifactTablePreview = {
  sheetName?: string
  columns: string[]
  rows: Array<Array<string | number | boolean | null>>
}

export type ArtifactSlidePreview = {
  title: string
  points?: string[]
}

/**
 * Lightweight content that can be rendered safely inside a chat message.
 * The original file remains the source of truth and is addressed by resourceId.
 */
export type ArtifactInlinePreview = {
  kind: 'image' | 'mindmap' | 'document' | 'spreadsheet' | 'presentation' | 'none'
  imageUrl?: string
  text?: string
  table?: ArtifactTablePreview
  mindMap?: MindMapTreeNode
  slides?: ArtifactSlidePreview[]
}

export type ChatArtifactDto = {
  artifactId: string
  resourceId?: string
  jobId?: string
  sourceMessageId?: EntityId | string | null
  conversationId?: EntityId | null
  projectId?: EntityId | null
  knowledgeBaseId?: EntityId | null
  title: string
  fileName: string
  fileType: ResourceFileType
  format: string
  mimeType?: string
  sizeBytes?: number
  status: ChatArtifactStatus
  progress?: number
  preview: ArtifactInlinePreview
  editable?: boolean
  editorRoute?: string
  errorCode?: string
  errorMessage?: string
}
