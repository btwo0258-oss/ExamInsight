import { request } from '@/api/request'
import { isMockDataSource } from '@/config/dataSource'
import { mockSession } from '@/mock/storage'
import type {
  AudioTranscriptionDto,
  CreateImageRecognitionRequest,
  ImageRecognitionJob,
  ImageRecognitionResult,
  MediaAssetDto,
  TranscribeAudioRequest,
  UploadImageRequest,
} from '@/types/contracts/media'

export interface MediaRepository {
  uploadImage(file: File, input: UploadImageRequest, signal?: AbortSignal): Promise<MediaAssetDto>
  transcribeAudio(file: File, input: TranscribeAudioRequest, signal?: AbortSignal): Promise<AudioTranscriptionDto>
  createImageRecognitionJob(assetId: string, input: CreateImageRecognitionRequest): Promise<ImageRecognitionJob>
  getImageRecognitionJob(jobId: string): Promise<ImageRecognitionJob>
}

const ASSET_DOMAIN = 'media.assets'
const JOB_DOMAIN = 'media.jobs'

function unwrap<T>(response: { data?: unknown }): T {
  const body = response.data as { data?: T } | T | undefined
  if (body && typeof body === 'object' && 'data' in body) return (body as { data: T }).data
  return body as T
}

function wait(ms: number, signal?: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }
    const timer = window.setTimeout(resolve, ms)
    signal?.addEventListener('abort', () => {
      window.clearTimeout(timer)
      reject(new DOMException('Aborted', 'AbortError'))
    }, { once: true })
  })
}

function now() {
  return new Date().toISOString()
}

function mediaId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function getAssets() {
  return mockSession.get<MediaAssetDto[]>(ASSET_DOMAIN, [])
}

function saveAsset(asset: MediaAssetDto) {
  const assets = getAssets().filter((item) => item.id !== asset.id)
  assets.unshift(asset)
  mockSession.set(ASSET_DOMAIN, assets)
}

function getJobs() {
  return mockSession.get<ImageRecognitionJob[]>(JOB_DOMAIN, [])
}

function saveJob(job: ImageRecognitionJob) {
  const jobs = getJobs().filter((item) => item.jobId !== job.jobId)
  jobs.unshift(job)
  mockSession.set(JOB_DOMAIN, jobs)
}

const mockMediaRepository: MediaRepository = {
  async uploadImage(file, input, signal) {
    await wait(220, signal)
    const timestamp = now()
    const asset: MediaAssetDto = {
      id: mediaId('mock-image'),
      kind: 'image',
      source: input.source,
      purpose: input.purpose,
      fileName: file.name,
      mimeType: file.type || 'image/jpeg',
      size: file.size,
      status: 'ready',
      conversationId: input.conversationId ?? null,
      knowledgeBaseId: input.knowledgeBaseId ?? null,
      projectId: input.projectId ?? null,
      createdAt: timestamp,
      updatedAt: timestamp,
    }
    saveAsset(asset)
    return asset
  },

  async transcribeAudio(file, input, signal) {
    await wait(650, signal)
    const timestamp = now()
    const asset: MediaAssetDto = {
      id: mediaId('mock-audio'),
      kind: 'audio',
      source: input.source,
      purpose: input.purpose,
      fileName: file.name,
      mimeType: file.type || 'audio/webm',
      size: file.size,
      status: 'ready',
      conversationId: input.conversationId ?? null,
      knowledgeBaseId: input.knowledgeBaseId ?? null,
      projectId: input.projectId ?? null,
      createdAt: timestamp,
      updatedAt: timestamp,
    }
    saveAsset(asset)
    return {
      asset,
      text: '这是 Mock 语音识别文本，请根据实际内容继续编辑。',
      language: input.language || 'zh-CN',
      durationMs: input.durationMs ?? 0,
      confidence: 0.96,
    }
  },

  async createImageRecognitionJob(assetId, input) {
    const asset = getAssets().find((item) => item.id === assetId)
    if (!asset) throw new Error('图片资源不存在')
    const job: ImageRecognitionJob = {
      jobId: mediaId('mock-media-job'),
      status: 'pending',
      progress: 0,
      result: {
        assetId,
        mode: input.mode,
        text: input.mode === 'question'
          ? 'Mock 已识别为题目图片，正式环境由后端视觉模型返回结构化题目。'
          : 'Mock 图片识别结果，仅用于验证页面状态和数据流。',
        intent: input.mode === 'question' ? 'question-capture' : input.mode === 'ocr' ? 'document-ocr' : 'general-image',
        confidence: 0.93,
      },
    }
    saveJob(job)
    return job
  },

  async getImageRecognitionJob(jobId) {
    const job = getJobs().find((item) => item.jobId === jobId)
    if (!job) throw new Error('图片识别任务不存在')
    if (job.status === 'pending' || job.status === 'running') {
      job.status = 'succeeded'
      job.progress = 100
      saveJob(job)
    }
    return job
  },
}

const apiMediaRepository: MediaRepository = {
  async uploadImage(file, input, signal) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('metadata', new Blob([JSON.stringify(input)], { type: 'application/json' }))
    const response = await request.post('/api/media/images', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      signal,
    })
    return unwrap<MediaAssetDto>(response)
  },

  async transcribeAudio(file, input, signal) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('metadata', new Blob([JSON.stringify(input)], { type: 'application/json' }))
    const response = await request.post('/api/media/audio/transcriptions', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      signal,
      timeout: 120_000,
    })
    return unwrap<AudioTranscriptionDto>(response)
  },

  async createImageRecognitionJob(assetId, input) {
    const response = await request.post(`/api/media/images/${assetId}/recognition-jobs`, input)
    return unwrap<ImageRecognitionJob>(response)
  },

  async getImageRecognitionJob(jobId) {
    const response = await request.get(`/api/media/jobs/${jobId}`)
    return unwrap<ImageRecognitionJob>(response)
  },
}

export const mediaRepository = isMockDataSource ? mockMediaRepository : apiMediaRepository
