import { fetchAssetContent, getAssetPreview } from '@/api/assetLibraryV2'
import type { ResourcePreviewDto } from '@/types/contracts/library'
import type { SpreadsheetSheetDraft } from '@/types/contracts/spreadsheet'
import { parseCsv, parseXlsx } from './previewParsers'
import { buildAssetPreviewDto, resolveAssetPreview } from './previewRegistry'
import type { MindMapTreeNode } from '@/types/contracts/artifact'
import { PreviewBudgetExceededError } from './previewParsers'

export type PreparedV2AssetPreview = {
  preview: ResourcePreviewDto
  documentBlob: Blob | null
  presentationData: ArrayBuffer | null
  sheets: SpreadsheetSheetDraft[]
  objectUrl: string | null
}

export async function prepareV2AssetPreview(assetId: string): Promise<PreparedV2AssetPreview> {
  const serverDescriptor = await getAssetPreview(assetId)
  const descriptor = resolveAssetPreview(serverDescriptor)
  const preview = buildAssetPreviewDto(serverDescriptor, descriptor)
  const prepared: PreparedV2AssetPreview = {
    preview,
    documentBlob: null,
    presentationData: null,
    sheets: [],
    objectUrl: null,
  }
  if (preview.status !== 'ready' || !serverDescriptor.contentUrl) return prepared

  const blob = await fetchAssetContent(assetId, 'inline')
  switch (descriptor.strategy) {
    case 'text':
      preview.textContent = await blob.text()
      break
    case 'docx':
      prepared.documentBlob = blob
      break
    case 'pptx':
      prepared.presentationData = await blob.arrayBuffer()
      break
    case 'mindmap': {
      const raw = JSON.parse(await blob.text()) as { root?: unknown }
      const mindmapData = toMindMapTree(raw.root ?? raw)
      preview.previewData = {
        kind: 'mindmap',
        mindMap: mindmapData,
      }
      break
    }
    case 'csv':
      prepared.sheets = parseCsv(await blob.text())
      break
    case 'xlsx':
      prepared.sheets = await parseXlsx(blob)
      break
    case 'object-url':
      prepared.objectUrl = URL.createObjectURL(blob)
      preview.previewUrl = prepared.objectUrl
      break
    case 'unsupported':
      break
  }
  return prepared
}

function toMindMapTree(value: unknown, depth = 0, state = { nodes: 0 }): MindMapTreeNode {
  state.nodes += 1
  if (state.nodes > 10_000 || depth > 100) {
    throw new PreviewBudgetExceededError('思维导图层级或节点数量超过在线预览限制，请下载文件查看。')
  }
  const node = value && typeof value === 'object' ? value as Record<string, unknown> : {}
  const data = node.data && typeof node.data === 'object' ? node.data as Record<string, unknown> : null
  const children = Array.isArray(node.children)
    ? node.children.map((child) => toMindMapTree(child, depth + 1, state))
    : []
  return {
    data: { text: String(data?.text ?? node.text ?? '思维导图') },
    children,
  }
}

export function disposePreparedV2AssetPreview(prepared: PreparedV2AssetPreview) {
  if (prepared.objectUrl) URL.revokeObjectURL(prepared.objectUrl)
}
