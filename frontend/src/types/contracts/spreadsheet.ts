import type { AsyncJob, EntityId } from './common'

export type SpreadsheetStatus =
  | 'generating'
  | 'ready'
  | 'failed'
  | 'cancelled'

export type SpreadsheetCellValue = string | number | boolean | null

export type SpreadsheetContext = {
  conversationId?: EntityId | null
  sourceMessageId?: EntityId | string | null
  knowledgeBaseId?: EntityId | null
  projectId?: EntityId | null
}

export type SpreadsheetConfig = {
  topic: string
  title: string
  sheetCount: number
  language: string
  requirements?: string
}

export type SpreadsheetSheetDraft = {
  sheetId: string
  name: string
  columns: string[]
  rows: SpreadsheetCellValue[][]
}

export type SpreadsheetWorkbookDraft = {
  sheets: SpreadsheetSheetDraft[]
}

export type SpreadsheetChatCardDto = SpreadsheetContext & {
  cardType: 'spreadsheet'
  status: SpreadsheetStatus
  spreadsheetId: string
  config: SpreadsheetConfig
  fileName?: string
  resourceId?: string
  errorMessage?: string
}

export type SpreadsheetDto = SpreadsheetContext & {
  id: string
  status: SpreadsheetStatus
  config: SpreadsheetConfig
  workbook: SpreadsheetWorkbookDraft
  activeJobId?: string
  fileName?: string
  fileSize?: number
  resourceId?: string
  errorCode?: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
}

export type CreateSpreadsheetRequest = SpreadsheetContext & {
  prompt: string
  resourceIds?: string[]
  mediaAssetIds?: string[]
  clientRequestId: string
}

export type SpreadsheetGenerationJob = AsyncJob<{
  spreadsheetId: string
}>
