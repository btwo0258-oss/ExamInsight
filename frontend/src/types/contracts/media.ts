import type { AsyncJob } from './common'

export type MediaKind = 'image' | 'audio'
export type MediaSource = 'upload' | 'camera' | 'microphone'
export type MediaPurpose = 'chat-attachment' | 'library-resource' | 'learning-input'
export type MediaAssetStatus = 'uploading' | 'uploaded' | 'processing' | 'ready' | 'failed'
export type ImageRecognitionMode = 'auto' | 'ocr' | 'question'

export type MediaContext = {
  conversationId?: number | null
  libraryId?: number | null
  learningProjectId?: number | null
}

export type MediaAssetDto = MediaContext & {
  id: string
  kind: MediaKind
  source: MediaSource
  purpose: MediaPurpose
  fileName: string
  mimeType: string
  size: number
  status: MediaAssetStatus
  createdAt: string
  updatedAt: string
  errorCode?: string
  errorMessage?: string
}

export type UploadImageRequest = MediaContext & {
  source: Extract<MediaSource, 'upload' | 'camera'>
  purpose: MediaPurpose
  clientRequestId: string
}

export type TranscribeAudioRequest = MediaContext & {
  source: 'microphone'
  purpose: Extract<MediaPurpose, 'chat-attachment' | 'learning-input'>
  clientRequestId: string
  language?: string
  durationMs?: number
}

export type AudioTranscriptionDto = {
  asset: MediaAssetDto
  text: string
  language: string
  durationMs: number
  confidence?: number
}

export type CreateImageRecognitionRequest = MediaContext & {
  mode: ImageRecognitionMode
  prompt?: string
  clientRequestId: string
}

export type ImageRecognitionResult = {
  assetId: string
  mode: ImageRecognitionMode
  text: string
  intent: 'general-image' | 'document-ocr' | 'question-capture'
  confidence?: number
  questionText?: string
  options?: string[]
}

export type ImageRecognitionJob = AsyncJob<ImageRecognitionResult>

export const MEDIA_LIMITS = {
  imageMaxBytes: 10 * 1024 * 1024,
  audioMaxBytes: 25 * 1024 * 1024,
  audioMaxDurationMs: 120_000,
  composerMaxFiles: 5,
} as const

export const IMAGE_ACCEPT = 'image/jpeg,image/png,image/webp,image/heic,image/heif,.jpg,.jpeg,.png,.webp,.heic,.heif'

