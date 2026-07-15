import type { EntityId } from './common'

export type DocumentProcessingStatus = 'uploading' | 'uploaded' | 'parsing' | 'ready' | 'failed'

export type KnowledgeBaseDto = {
  id: EntityId
  name: string
  description?: string
  icon?: string
  color?: string
  documentCount?: number
  mindMapCount?: number
  examAnalysisId?: EntityId
  availableForAi?: boolean
  createTime: string
  updateTime: string
}

export type LibraryDocumentDto = {
  id: EntityId
  kbId: EntityId
  fileName: string
  fileType: string
  fileSize: number
  chunkCount: number
  status: DocumentProcessingStatus
  errorCode?: string
  errorMessage?: string
  createTime: string
}

export type LibraryResourceSource = '资料库上传' | '智能学习上传' | '聊天上传' | '智能学习生成' | '聊天生成'
export type LibraryResourceCategory = 'file' | 'image' | 'mindmap'
export type LibraryResourceProcessingStatus = 'waiting' | 'processing' | 'ready' | 'failed'

export type LibraryResourceDto = {
  id: string
  name: string
  type: string
  size: string
  status: LibraryResourceProcessingStatus
  errorMessage?: string
  updatedAt: string
  category: LibraryResourceCategory
  source: LibraryResourceSource
  projectId: number | null
  libraryId: number | null
  externalKey?: string
}
