import { sessionFetch } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { documentRepository } from '@/repositories/document'
import type { DocumentDto } from '@/repositories/document'
import { downloadBlob } from '@/utils/download'

export type Document = DocumentDto

export const getDocuments = documentRepository.list.bind(documentRepository)
export const uploadDocument = documentRepository.upload.bind(documentRepository)
export const deleteDocument = documentRepository.remove.bind(documentRepository)
export const getDocumentStatus = documentRepository.status.bind(documentRepository)

export async function downloadDocument(id: number, fileName: string): Promise<void> {
  if (isMockDataSource) throw new Error('Mock 文件只保存元数据，没有可下载的真实文件')
  const response = await sessionFetch(`${import.meta.env.VITE_API_BASE_URL ?? ''}/api/doc/download/${id}`)
  if (!response.ok) throw new Error('下载失败')
  downloadBlob(await response.blob(), fileName)
}
