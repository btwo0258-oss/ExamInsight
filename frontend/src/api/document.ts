import { getStoredToken, request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { documentRepository } from '@/repositories/document'
import type { DocumentDto } from '@/repositories/document'

export type Document = DocumentDto

export const getDocuments = documentRepository.list.bind(documentRepository)
export const uploadDocument = documentRepository.upload.bind(documentRepository)
export const deleteDocument = documentRepository.remove.bind(documentRepository)
export const getDocumentStatus = documentRepository.status.bind(documentRepository)

function authorizedHeaders() {
  const headers = new Headers()
  const token = getStoredToken()
  if (token) headers.append('Authorization', `Bearer ${token}`)
  return headers
}

export async function downloadDocument(id: number, fileName: string): Promise<void> {
  if (isMockDataSource) throw new Error('Mock 文件只保存元数据，没有可下载的真实文件')
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL ?? ''}/api/doc/download/${id}`, {
    headers: authorizedHeaders(),
  })
  if (!response.ok) throw new Error('下载失败')
  const url = URL.createObjectURL(await response.blob())
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  URL.revokeObjectURL(url)
}

export async function getDocumentPreview(id: number): Promise<{ type: string; content: string | Blob | null }> {
  if (isMockDataSource) return { type: 'text', content: 'Mock 环境只保存文件元数据。' }
  const detailResponse = await request.get(`/api/doc/${id}`)
  const detail = (detailResponse.data?.data ?? detailResponse.data) as { fileName: string }
  const extension = detail.fileName.slice(detail.fileName.lastIndexOf('.')).toLowerCase()
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL ?? ''}/api/doc/download/${id}`, {
    headers: authorizedHeaders(),
  })
  if (!response.ok) throw new Error('获取预览失败')
  if (extension === '.pdf') return { type: 'pdf', content: await response.blob() }
  if (extension === '.docx' || extension === '.doc') return { type: 'docx', content: await response.blob() }
  return { type: 'text', content: await response.text() }
}

export async function saveDocumentContent(_id: number, _content: string | Blob): Promise<void> {
  if (!isMockDataSource) throw new Error('后端尚未提供文档内容保存接口')
}
