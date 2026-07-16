import type { AsyncJob, EntityId } from './common'

export type PresentationStatus =
  | 'draft'
  | 'outlining'
  | 'outline_ready'
  | 'generating'
  | 'ready'
  | 'failed'
  | 'cancelled'

export type PresentationAspectRatio = '16:9' | '4:3'
export type PresentationOutlineMode = 'confirm' | 'auto'
export type PresentationStyle = 'academic' | 'minimal' | 'vibrant' | 'professional'
export type PresentationAudience = 'student' | 'teacher' | 'general' | 'business'
export type PresentationSlideLayout = 'cover' | 'section' | 'content' | 'comparison' | 'summary'

export type PresentationContext = {
  conversationId?: EntityId | null
  libraryId?: EntityId | null
  learningProjectId?: EntityId | null
  learningResourceId?: EntityId | null
}

export type PresentationTemplateDto = {
  id: string
  name: string
  description: string
  style: PresentationStyle
  backgroundColor: string
  surfaceColor: string
  textColor: string
  accentColor: string
}

export type PresentationSlideOutline = {
  id: string
  order: number
  title: string
  points: string[]
  speakerNotes?: string
  layout: PresentationSlideLayout
}

export type PresentationPreviewPage = PresentationSlideOutline & {
  backgroundColor: string
  surfaceColor: string
  textColor: string
  accentColor: string
  previewImageUrl?: string
}

export type PresentationConfig = {
  topic: string
  title: string
  pageCount: number
  outlineMode: PresentationOutlineMode
  templateId: string
  aspectRatio: PresentationAspectRatio
  style: PresentationStyle
  audience: PresentationAudience
  language: string
  sourceText?: string
  sourceFileNames?: string[]
  mediaAssetIds?: string[]
}

export type PresentationDto = PresentationContext & {
  id: string
  status: PresentationStatus
  config: PresentationConfig
  outline: PresentationSlideOutline[]
  previewPages: PresentationPreviewPage[]
  activeJobId?: string
  fileName?: string
  fileSize?: number
  libraryResourceId?: string
  errorCode?: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
}

export type CreatePresentationRequest = PresentationContext & PresentationConfig & {
  clientRequestId: string
}

export type UpdatePresentationOutlineRequest = {
  slides: PresentationSlideOutline[]
  clientRequestId: string
}

export type StartPresentationGenerationRequest = {
  clientRequestId: string
}

export type SavePresentationToLibraryRequest = {
  libraryId: EntityId
  clientRequestId: string
}

export type PresentationOutlineJob = AsyncJob<{
  presentationId: string
  outline: PresentationSlideOutline[]
}>

export type PresentationGenerationJob = AsyncJob<{
  presentationId: string
}>
