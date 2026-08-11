import axios from 'axios'
import { request } from '@/api/request'
import type {
  CursorPage,
  AssetPreviewDescriptor,
  KnowledgeBase,
  KnowledgeBaseDetail,
  LibraryAsset,
  LibraryAssetDetail,
  LibraryView,
  PurgeJob,
  UploadCompletion,
  UploadProgress,
  UploadResult,
  UploadSession,
} from '@/types/contracts/assetLibraryV2'

type ApiErrorBody = {
  error?: { code?: string; message?: string; requestId?: string }
  message?: string
}

export class AssetLibraryApiError extends Error {
  readonly code: string
  readonly requestId: string | null

  constructor(message: string, code = 'REQUEST_FAILED', requestId: string | null = null) {
    super(message)
    this.name = 'AssetLibraryApiError'
    this.code = code
    this.requestId = requestId
  }
}

export function libraryError(error: unknown, fallback: string) {
  if (error instanceof AssetLibraryApiError) return error
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    const body = error.response?.data
    return new AssetLibraryApiError(
      body?.error?.message || body?.message || fallback,
      body?.error?.code,
      body?.error?.requestId ?? null,
    )
  }
  return new AssetLibraryApiError(error instanceof Error ? error.message : fallback)
}

async function call<T>(operation: () => Promise<{ data: T }>, fallback: string) {
  try {
    return (await operation()).data
  } catch (error) {
    throw libraryError(error, fallback)
  }
}

export function listAssets(view: LibraryView, cursor?: string | null, limit = 100) {
  return call(
    () => request.get<CursorPage<LibraryAsset>>('/api/v2/assets', {
      params: { view, limit, cursor: cursor || undefined },
    }),
    '获取资料列表失败。',
  )
}

export function getAsset(assetId: string) {
  return call(
    () => request.get<LibraryAssetDetail>(`/api/v2/assets/${assetId}`),
    '获取资料详情失败。',
  )
}

export function getAssetPreview(assetId: string) {
  return call(
    () => request.get<AssetPreviewDescriptor>(`/api/v2/assets/${assetId}/preview`),
    '获取资料预览信息失败。',
  )
}

export function renameAsset(assetId: string, name: string) {
  return call(
    () => request.patch<LibraryAsset>(`/api/v2/assets/${assetId}`, { name }),
    '重命名资料失败。',
  )
}

export function trashAsset(assetId: string) {
  return call(
    () => request.post<LibraryAsset>(`/api/v2/assets/${assetId}/trash`),
    '移入回收站失败。',
  )
}

export function restoreAsset(assetId: string) {
  return call(
    () => request.post<LibraryAsset>(`/api/v2/assets/${assetId}/restore`),
    '恢复资料失败。',
  )
}

export function purgeAsset(assetId: string) {
  return call(
    () => request.delete<PurgeJob>(`/api/v2/assets/${assetId}`),
    '提交彻底删除任务失败。',
  )
}

export function getAssetPurgeJob(assetId: string) {
  return call(
    () => request.get<PurgeJob>(`/api/v2/assets/${assetId}/purge-job`),
    '获取删除进度失败。',
  )
}

export function listKnowledgeBases(view: LibraryView, cursor?: string | null, limit = 100) {
  return call(
    () => request.get<CursorPage<KnowledgeBase>>('/api/v2/knowledge-bases', {
      params: { view, limit, cursor: cursor || undefined },
    }),
    '获取知识库列表失败。',
  )
}

export function getKnowledgeBase(knowledgeBaseId: string) {
  return call(
    () => request.get<KnowledgeBaseDetail>(`/api/v2/knowledge-bases/${knowledgeBaseId}`),
    '获取知识库详情失败。',
  )
}

export function createKnowledgeBase(name: string, description: string) {
  return call(
    () => request.post<KnowledgeBaseDetail>('/api/v2/knowledge-bases', { name, description }),
    '创建知识库失败。',
  )
}

export function updateKnowledgeBase(
  knowledgeBaseId: string,
  payload: { name?: string; description?: string },
) {
  return call(
    () => request.patch<KnowledgeBaseDetail>(`/api/v2/knowledge-bases/${knowledgeBaseId}`, payload),
    '保存知识库失败。',
  )
}

export function trashKnowledgeBase(knowledgeBaseId: string) {
  return call(
    () => request.post<KnowledgeBaseDetail>(`/api/v2/knowledge-bases/${knowledgeBaseId}/trash`),
    '移入回收站失败。',
  )
}

export function restoreKnowledgeBase(knowledgeBaseId: string) {
  return call(
    () => request.post<KnowledgeBaseDetail>(`/api/v2/knowledge-bases/${knowledgeBaseId}/restore`),
    '恢复知识库失败。',
  )
}

export async function purgeKnowledgeBase(knowledgeBaseId: string) {
  try {
    await request.delete(`/api/v2/knowledge-bases/${knowledgeBaseId}`)
  } catch (error) {
    throw libraryError(error, '彻底删除知识库失败。')
  }
}

export function listKnowledgeBaseAssets(
  knowledgeBaseId: string,
  cursor?: string | null,
  limit = 100,
) {
  return call(
    () => request.get<CursorPage<LibraryAsset>>(
      `/api/v2/knowledge-bases/${knowledgeBaseId}/assets`,
      { params: { limit, cursor: cursor || undefined } },
    ),
    '获取知识库资料失败。',
  )
}

export function addAssetToKnowledgeBase(knowledgeBaseId: string, assetId: string) {
  return call(
    () => request.put<KnowledgeBaseDetail>(
      `/api/v2/knowledge-bases/${knowledgeBaseId}/assets/${assetId}`,
    ),
    '加入知识库失败。',
  )
}

export async function removeAssetFromKnowledgeBase(knowledgeBaseId: string, assetId: string) {
  try {
    await request.delete(`/api/v2/knowledge-bases/${knowledgeBaseId}/assets/${assetId}`)
  } catch (error) {
    throw libraryError(error, '从知识库移除资料失败。')
  }
}

export async function fetchAssetContent(assetId: string, disposition: 'inline' | 'attachment') {
  try {
    const response = await request.get<Blob>(`/api/v2/assets/${assetId}/content`, {
      params: { disposition },
      responseType: 'blob',
      timeout: 120_000,
    })
    return response.data
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
      try {
        const body = JSON.parse(await error.response.data.text()) as ApiErrorBody
        throw new AssetLibraryApiError(
          body.error?.message || '读取文件失败。',
          body.error?.code,
          body.error?.requestId ?? null,
        )
      } catch (blobError) {
        if (blobError instanceof AssetLibraryApiError) throw blobError
      }
    }
    throw libraryError(error, disposition === 'inline' ? '预览文件失败。' : '下载文件失败。')
  }
}

export async function uploadAsset(
  file: File,
  knowledgeBaseId: string | null,
  onProgress?: (progress: UploadProgress) => void,
): Promise<UploadResult> {
  let session: UploadSession | null = null
  let completed = false
  try {
    session = await call(
      () => request.post<UploadSession>('/api/v2/uploads', {
        uploadKey: crypto.randomUUID(),
        originalFilename: file.name,
        declaredMime: file.type || undefined,
        expectedSize: file.size,
      }),
      '创建上传任务失败。',
    )

    for (let partNumber = 1; partNumber <= session.expectedPartCount; partNumber += 1) {
      const start = (partNumber - 1) * session.partSize
      const end = Math.min(start + session.partSize, file.size)
      const part = file.slice(start, end)
      await request.put(`/api/v2/uploads/${session.uploadId}/parts/${partNumber}`, part, {
        headers: { 'Content-Type': 'application/octet-stream' },
        timeout: 120_000,
        onUploadProgress(event) {
          const partLoaded = Math.min(event.loaded, part.size)
          const uploadedBytes = Math.min(start + partLoaded, file.size)
          onProgress?.({
            uploadedBytes,
            totalBytes: file.size,
            percentage: Math.round((uploadedBytes / file.size) * 100),
          })
        },
      })
    }

    const completion = await call(
      () => request.post<UploadCompletion>(`/api/v2/uploads/${session!.uploadId}/complete`),
      '完成文件上传失败。',
    )
    completed = true
    onProgress?.({ uploadedBytes: file.size, totalBytes: file.size, percentage: 100 })

    let associationWarning: string | null = null
    if (knowledgeBaseId) {
      try {
        await addAssetToKnowledgeBase(knowledgeBaseId, completion.asset.assetId)
      } catch (error) {
        associationWarning = libraryError(error, '资料已上传，但加入知识库失败。').message
      }
    }
    return { completion, associationWarning }
  } catch (error) {
    if (session && !completed) {
      await request.delete(`/api/v2/uploads/${session.uploadId}`).catch(() => undefined)
    }
    throw libraryError(error, '上传资料失败。')
  }
}
