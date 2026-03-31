export type FileType = 'pdf' | 'docx' | 'md' | 'txt'

export function getFileType(fileName: string): FileType | null {
  const ext = fileName.split('.').pop()?.toLowerCase()
  if (ext === 'pdf' || ext === 'docx' || ext === 'md' || ext === 'txt') return ext
  return null
}

export function validateUploadFile(file: File, options?: { maxSizeMb?: number }): {
  ok: boolean
  reason?: string
  fileType?: FileType
} {
  const maxSizeMb = options?.maxSizeMb ?? 30
  const fileType = getFileType(file.name)
  if (!fileType) return { ok: false, reason: '不支持的文件类型' }
  if (file.size > maxSizeMb * 1024 * 1024) return { ok: false, reason: `文件不能超过${maxSizeMb}MB` }
  return { ok: true, fileType }
}
