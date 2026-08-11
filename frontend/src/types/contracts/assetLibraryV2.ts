export type LibraryView = 'library' | 'trash'

export type CursorPage<T> = {
  items: T[]
  nextCursor: string | null
}

export type AssetVersion = {
  versionId: string
  versionNumber: number
  status: string
  mimeType: string
  sizeBytes: number
  chunkCount: number
  indexedChunkCount: number
  failedChunkCount: number
  indexStatus: string
  createdAt: string
}

export type LibraryAsset = {
  assetId: string
  name: string
  assetType: string
  sourceType: string
  status: string
  knowledgeBaseCount: number
  version: AssetVersion | null
  trashedAt: string | null
  createdAt: string
  updatedAt: string
}

export type KnowledgeBaseReference = {
  knowledgeBaseId: string
  name: string
}

export type PurgeJob = {
  jobId: string
  status: string
  errorCode: string | null
  requestedAt: string
  finishedAt: string | null
}

export type LibraryAssetDetail = {
  asset: LibraryAsset
  knowledgeBases: KnowledgeBaseReference[]
  purgeJob: PurgeJob | null
}

export type KnowledgeBase = {
  knowledgeBaseId: string
  name: string
  description: string
  status: string
  assetCount: number
  trashedAt: string | null
  createdAt: string
  updatedAt: string
}

export type KnowledgeBaseDetail = {
  knowledgeBase: KnowledgeBase
}

export type UploadSession = {
  uploadId: string
  originalFilename: string
  status: string
  expectedSize: number
  uploadedBytes: number
  partSize: number
  expectedPartCount: number
  expiresAt: string
}

export type UploadCompletion = {
  uploadId: string
  status: string
  asset: { assetId: string; name: string; status: string }
  version: {
    versionId: string
    versionNumber: number
    status: string
    mimeType: string
    sizeBytes: number
    sha256: string
  }
  securityScanJob: { jobId: string; status: string; stage: string }
}

export type UploadResult = {
  completion: UploadCompletion
  associationWarning: string | null
}

export type UploadProgress = {
  uploadedBytes: number
  totalBytes: number
  percentage: number
}
