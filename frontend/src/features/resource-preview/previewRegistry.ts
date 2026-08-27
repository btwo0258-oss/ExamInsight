import type {
  AssetPreviewDescriptor,
  AssetPreviewRenderer,
} from '@/types/contracts/assetLibraryV2'
import type {
  ResourceFileType,
  ResourcePreviewDto,
  ResourcePreviewKind,
} from '@/types/contracts/library'

export type PreviewLoadStrategy =
  | 'text'
  | 'docx'
  | 'pptx'
  | 'mindmap'
  | 'csv'
  | 'xlsx'
  | 'object-url'
  | 'unsupported'

export type PreviewDescriptor = {
  kind: ResourcePreviewKind
  fileType: ResourceFileType
  strategy: PreviewLoadStrategy
}

const RENDERERS: Record<AssetPreviewRenderer, PreviewDescriptor> = {
  pdf: { kind: 'pdf', fileType: 'pdf', strategy: 'object-url' },
  image: { kind: 'image', fileType: 'image', strategy: 'object-url' },
  docx: { kind: 'word', fileType: 'document', strategy: 'docx' },
  pptx: { kind: 'presentation', fileType: 'presentation', strategy: 'pptx' },
  mindmap: { kind: 'mindmap', fileType: 'mindmap', strategy: 'mindmap' },
  xlsx: { kind: 'spreadsheet', fileType: 'spreadsheet', strategy: 'xlsx' },
  csv: { kind: 'spreadsheet', fileType: 'spreadsheet', strategy: 'csv' },
  markdown: { kind: 'text', fileType: 'document', strategy: 'text' },
  text: { kind: 'text', fileType: 'document', strategy: 'text' },
  audio: { kind: 'audio', fileType: 'audio', strategy: 'object-url' },
  unsupported: { kind: 'unsupported', fileType: 'other', strategy: 'unsupported' },
}

export function resolveAssetPreview(descriptor: AssetPreviewDescriptor): PreviewDescriptor {
  return RENDERERS[descriptor.renderer] ?? RENDERERS.unsupported
}

export function buildAssetPreviewDto(
  descriptor: AssetPreviewDescriptor,
  preview: PreviewDescriptor,
): ResourcePreviewDto {
  return {
    resource: {
      resourceId: descriptor.assetId,
      name: descriptor.name,
      format: descriptor.extension.toUpperCase() || 'FILE',
      fileType: preview.fileType,
      mimeType: descriptor.mimeType,
      sizeBytes: descriptor.sizeBytes,
      status: descriptor.status === 'ready' ? 'ready' : 'processing',
      updatedAt: descriptor.updatedAt,
      sourceType: descriptor.sourceType === 'AI_GENERATED' ? 'generated' : 'uploaded',
      origin: 'resource-library',
      projectId: null,
      knowledgeBaseId: null,
    },
    status: descriptor.status,
    previewKind: preview.kind,
    errorMessage: descriptor.reason ?? undefined,
    canDownload: descriptor.downloadAvailable,
  }
}
