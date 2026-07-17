import type { LocationQueryRaw } from 'vue-router'
import type { PresentationChatCardDto, PresentationDto } from '@/types/contracts/presentation'

export function toPresentationChatCard(presentation: PresentationDto): PresentationChatCardDto {
  return {
    cardType: 'presentation',
    view: presentation.status === 'ready' || presentation.status === 'failed' ? 'result' : 'proposal',
    status: presentation.status,
    presentationId: presentation.id,
    conversationId: presentation.conversationId ?? null,
    sourceMessageId: presentation.sourceMessageId ?? null,
    knowledgeBaseId: presentation.knowledgeBaseId ?? null,
    projectId: presentation.projectId ?? null,
    learningResourceId: presentation.learningResourceId ?? null,
    config: { ...presentation.config },
    fileName: presentation.fileName,
    previewPageCount: presentation.previewPages.length || presentation.outline.length,
    resourceId: presentation.resourceId,
    errorMessage: presentation.errorMessage,
  }
}

export function presentationRouteQuery(card: PresentationChatCardDto, returnTo: string): LocationQueryRaw {
  const query: LocationQueryRaw = {
    returnTo,
    topic: card.config.topic,
    title: card.config.title,
    pageCount: String(card.config.pageCount),
    templateId: card.config.templateId,
    aspectRatio: card.config.aspectRatio,
    style: card.config.style,
    audience: card.config.audience,
    language: card.config.language,
  }
  const ids = {
    conversationId: card.conversationId,
    sourceMessageId: card.sourceMessageId,
    knowledgeBaseId: card.knowledgeBaseId,
    projectId: card.projectId,
    learningResourceId: card.learningResourceId,
  }
  for (const [key, value] of Object.entries(ids)) {
    if (value !== undefined && value !== null) query[key] = String(value)
  }
  return query
}
