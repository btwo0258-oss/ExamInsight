export type ResourceVisualType =
  | 'knowledge'
  | 'plan'
  | 'manual'
  | 'presentation'
  | 'spreadsheet'
  | 'mindmap'
  | 'image'
  | 'pdf'
  | 'document'
  | 'markdown'
  | 'text'
  | 'code'
  | 'archive'
  | 'audio'
  | 'other'

export type ResourceVisual = {
  icon: string
  color: string
}

export const resourceVisuals: Record<ResourceVisualType, ResourceVisual> = {
  knowledge: { icon: 'folder', color: '#f59e0b' },
  plan: { icon: 'notebook', color: '#6366f1' },
  manual: { icon: 'book', color: '#10b981' },
  presentation: { icon: 'presentation', color: '#d4552d' },
  spreadsheet: { icon: 'grid', color: '#16824b' },
  mindmap: { icon: 'mind-topic', color: '#8b5cf6' },
  image: { icon: 'image', color: '#ec4899' },
  pdf: { icon: 'pdf', color: '#ef4444' },
  document: { icon: 'word', color: '#3b82f6' },
  markdown: { icon: 'markdown', color: '#6366f1' },
  text: { icon: 'txt', color: '#6b7280' },
  code: { icon: 'code', color: '#2563eb' },
  archive: { icon: 'file', color: '#a06a14' },
  audio: { icon: 'microphone', color: '#7c5cce' },
  other: { icon: 'file', color: '#9ca3af' },
}

const imageExtensions = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg'])
const audioExtensions = new Set(['mp3', 'wav', 'm4a', 'aac', 'ogg', 'flac'])
const archiveExtensions = new Set(['zip', 'rar', '7z', 'tar', 'gz'])
const codeExtensions = new Set(['js', 'jsx', 'ts', 'tsx', 'vue', 'java', 'py', 'go', 'rs', 'c', 'cc', 'cpp', 'h', 'hpp', 'cs', 'php', 'rb', 'sql', 'html', 'css', 'scss'])

function fileExtension(name: string) {
  const normalized = name.trim().toLocaleLowerCase()
  const lastDot = normalized.lastIndexOf('.')
  return lastDot >= 0 ? normalized.slice(lastDot + 1) : ''
}

export function resourceVisualTypeFromFile(
  name: string,
  mimeType = '',
  fileType = '',
): ResourceVisualType {
  const normalizedFileType = fileType.toLocaleLowerCase()
  if (normalizedFileType === 'mindmap') return 'mindmap'
  if (normalizedFileType === 'presentation') return 'presentation'
  if (normalizedFileType === 'spreadsheet') return 'spreadsheet'
  if (normalizedFileType === 'image') return 'image'
  if (normalizedFileType === 'pdf') return 'pdf'
  if (normalizedFileType === 'audio') return 'audio'
  if (normalizedFileType === 'archive') return 'archive'

  const extension = fileExtension(name)
  if (extension === 'pdf' || mimeType.includes('pdf')) return 'pdf'
  if (extension === 'ppt' || extension === 'pptx' || mimeType.includes('presentation')) return 'presentation'
  if (extension === 'xls' || extension === 'xlsx' || extension === 'csv' || mimeType.includes('spreadsheet') || mimeType.includes('excel')) return 'spreadsheet'
  if (extension === 'md' || extension === 'markdown') return 'markdown'
  if (extension === 'doc' || extension === 'docx' || mimeType.includes('word')) return 'document'
  if (extension === 'txt' || mimeType.startsWith('text/plain')) return 'text'
  if (imageExtensions.has(extension) || mimeType.startsWith('image/')) return 'image'
  if (audioExtensions.has(extension) || mimeType.startsWith('audio/')) return 'audio'
  if (archiveExtensions.has(extension)) return 'archive'
  if (codeExtensions.has(extension)) return 'code'
  if (normalizedFileType === 'document') return 'document'
  return 'other'
}

export function resourceVisualTypeFromLearningGroup(group: string): ResourceVisualType {
  if (group === '学习方案') return 'plan'
  if (group === '个性化学习手册') return 'manual'
  if (group === 'PPT') return 'presentation'
  if (group === '思维导图') return 'mindmap'
  if (group === '代码案例') return 'code'
  if (group === '图片') return 'image'
  if (group === '文档') return 'document'
  if (group === '电子表格') return 'spreadsheet'
  if (group === '音频') return 'audio'
  return 'other'
}
