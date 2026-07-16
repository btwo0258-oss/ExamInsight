import type { MediaSource } from '@/types/contracts/media'

const sources = new WeakMap<File, Extract<MediaSource, 'upload' | 'camera'>>()

export function markMediaSource(file: File, source: Extract<MediaSource, 'upload' | 'camera'>) {
  sources.set(file, source)
  return file
}

export function getMediaSource(file: File) {
  return sources.get(file) ?? 'upload'
}

export function isImageFile(file: File) {
  if (file.type.startsWith('image/')) return true
  const extension = file.name.slice(file.name.lastIndexOf('.')).toLowerCase()
  return ['.jpg', '.jpeg', '.png', '.webp', '.heic', '.heif'].includes(extension)
}
