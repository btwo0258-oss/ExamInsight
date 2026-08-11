import { fetchAssetContent, getAssetPreview } from '@/api/assetLibraryV2'
import type { ResourcePreviewDto } from '@/types/contracts/library'
import type { SpreadsheetSheetDraft } from '@/types/contracts/spreadsheet'
import { parseCsv, parseXlsx } from './previewParsers'
import { buildAssetPreviewDto, resolveAssetPreview } from './previewRegistry'

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

export function disposePreparedV2AssetPreview(prepared: PreparedV2AssetPreview) {
  if (prepared.objectUrl) URL.revokeObjectURL(prepared.objectUrl)
}
