import type { LocationQueryRaw } from 'vue-router'
import type { SpreadsheetChatCardDto, SpreadsheetDto } from '@/types/contracts/spreadsheet'

export function toSpreadsheetChatCard(spreadsheet: SpreadsheetDto): SpreadsheetChatCardDto {
  return {
    cardType: 'spreadsheet',
    status: spreadsheet.status,
    spreadsheetId: spreadsheet.id,
    conversationId: spreadsheet.conversationId ?? null,
    sourceMessageId: spreadsheet.sourceMessageId ?? null,
    knowledgeBaseId: spreadsheet.knowledgeBaseId ?? null,
    projectId: spreadsheet.projectId ?? null,
    config: { ...spreadsheet.config },
    fileName: spreadsheet.fileName,
    resourceId: spreadsheet.resourceId,
    errorMessage: spreadsheet.errorMessage,
  }
}

export function spreadsheetRouteQuery(card: SpreadsheetChatCardDto, returnTo: string): LocationQueryRaw {
  const query: LocationQueryRaw = {
    returnTo,
  }
  const context = {
    conversationId: card.conversationId,
    sourceMessageId: card.sourceMessageId,
    knowledgeBaseId: card.knowledgeBaseId,
    projectId: card.projectId,
  }
  for (const [key, value] of Object.entries(context)) {
    if (value !== undefined && value !== null) query[key] = String(value)
  }
  return query
}
