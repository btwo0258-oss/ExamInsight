import { MEDIA_LIMITS, type MediaSource } from '@/types/contracts/media'

const MEBIBYTE = 1024 * 1024
const STANDARD_ATTACHMENT_MAX_BYTES = 21 * MEBIBYTE

export const DOCUMENT_ATTACHMENT_EXTENSIONS = [
  '.pdf',
  '.doc',
  '.docx',
  '.md',
  '.txt',
] as const

export const SPREADSHEET_ATTACHMENT_EXTENSIONS = ['.xls', '.xlsx'] as const
export const PRESENTATION_ATTACHMENT_EXTENSIONS = ['.ppt', '.pptx'] as const
export const ARCHIVE_ATTACHMENT_EXTENSIONS = ['.zip'] as const
export const AUDIO_ATTACHMENT_EXTENSIONS = ['.mp3', '.wav', '.m4a', '.aac', '.ogg', '.flac'] as const

const IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp', '.heic', '.heif'] as const
const IMAGE_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif']
const AUDIO_MIME_TYPES = [
  'audio/mpeg',
  'audio/mp3',
  'audio/wav',
  'audio/x-wav',
  'audio/mp4',
  'audio/x-m4a',
  'audio/aac',
  'audio/ogg',
  'audio/flac',
  'audio/x-flac',
  'audio/webm',
]

const NON_MEDIA_ATTACHMENT_EXTENSIONS = [
  ...DOCUMENT_ATTACHMENT_EXTENSIONS,
  ...SPREADSHEET_ATTACHMENT_EXTENSIONS,
  ...PRESENTATION_ATTACHMENT_EXTENSIONS,
  ...ARCHIVE_ATTACHMENT_EXTENSIONS,
] as readonly string[]

const AUDIO_EXTENSIONS = AUDIO_ATTACHMENT_EXTENSIONS as readonly string[]
const SUPPORTED_IMAGE_EXTENSIONS = IMAGE_EXTENSIONS as readonly string[]
const mediaSources = new WeakMap<File, Extract<MediaSource, 'upload' | 'camera'>>()

export const ATTACHMENT_ACCEPT = [
  ...NON_MEDIA_ATTACHMENT_EXTENSIONS,
  ...IMAGE_EXTENSIONS,
  ...AUDIO_ATTACHMENT_EXTENSIONS,
  ...IMAGE_MIME_TYPES,
  ...AUDIO_MIME_TYPES,
].join(',')

export function fileExtension(file: Pick<File, 'name'>) {
  const dotIndex = file.name.lastIndexOf('.')
  return dotIndex === -1 ? '' : file.name.slice(dotIndex).toLowerCase()
}

export function markMediaSource(file: File, source: Extract<MediaSource, 'upload' | 'camera'>) {
  mediaSources.set(file, source)
  return file
}

export function getMediaSource(file: File) {
  return mediaSources.get(file) ?? 'upload'
}

export function isImageFile(file: File) {
  return IMAGE_MIME_TYPES.includes(file.type.toLowerCase()) || SUPPORTED_IMAGE_EXTENSIONS.includes(fileExtension(file))
}

export function isAudioFile(file: File) {
  if (file.type.startsWith('video/')) return false
  return AUDIO_MIME_TYPES.includes(file.type.toLowerCase()) || AUDIO_EXTENSIONS.includes(fileExtension(file))
}

export function isSupportedAttachment(file: File) {
  if (file.type.startsWith('video/')) return false
  return isImageFile(file) || isAudioFile(file) || NON_MEDIA_ATTACHMENT_EXTENSIONS.includes(fileExtension(file))
}

export function attachmentMaxBytes(file: File) {
  if (isImageFile(file)) return MEDIA_LIMITS.imageMaxBytes
  if (isAudioFile(file)) return MEDIA_LIMITS.audioMaxBytes
  return STANDARD_ATTACHMENT_MAX_BYTES
}

export function attachmentSizeLimitLabel(file: File) {
  if (isImageFile(file)) return '10MB'
  if (isAudioFile(file)) return '25MB'
  return '21MB'
}
