import type { ChatArtifactDto } from '@/types/contracts/artifact'
import type { PresentationChatCardDto } from '@/types/contracts/presentation'
import type { SpreadsheetChatCardDto } from '@/types/contracts/spreadsheet'

export function upsertArtifact(items: ChatArtifactDto[], incoming: ChatArtifactDto) {
  const index = items.findIndex((item) => item.artifactId === incoming.artifactId)
  if (index === -1) return [...items, incoming]
  const next = [...items]
  const current = items[index]!
  next[index] = { ...current, ...incoming, preview: incoming.preview ?? current.preview }
  return next
}

export function spreadsheetCardToArtifact(card: SpreadsheetChatCardDto): ChatArtifactDto {
  const title = card.config.title || card.config.topic || '电子表格'
  return {
    artifactId: `spreadsheet:${card.spreadsheetId}`,
    resourceId: card.resourceId,
    sourceMessageId: card.sourceMessageId,
    conversationId: card.conversationId,
    projectId: card.projectId,
    knowledgeBaseId: card.knowledgeBaseId,
    title,
    fileName: card.fileName || `${title}.xlsx`,
    fileType: 'spreadsheet',
    format: 'XLSX',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    status: card.status,
    progress: card.status === 'generating' ? 48 : card.status === 'ready' ? 100 : undefined,
    preview: { kind: 'spreadsheet' },
    editable: false,
    errorMessage: card.errorMessage,
  }
}

export function presentationCardToArtifact(card: PresentationChatCardDto): ChatArtifactDto | null {
  if (card.view !== 'result' || !card.presentationId) return null
  const title = card.config.title || card.config.topic || '演示文稿'
  return {
    artifactId: `presentation:${card.presentationId}`,
    resourceId: card.resourceId,
    sourceMessageId: card.sourceMessageId,
    conversationId: card.conversationId,
    projectId: card.projectId,
    knowledgeBaseId: card.knowledgeBaseId,
    title,
    fileName: card.fileName || `${title}.pptx`,
    fileType: 'presentation',
    format: 'PPTX',
    mimeType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    status: card.status === 'outlining' || card.status === 'outline_ready' || card.status === 'draft'
      ? 'queued'
      : card.status,
    progress: card.status === 'generating' ? 62 : card.status === 'ready' ? 100 : undefined,
    preview: { kind: 'presentation' },
    editable: true,
    editorRoute: `/presentations/${card.presentationId}`,
    errorMessage: card.errorMessage,
  }
}
