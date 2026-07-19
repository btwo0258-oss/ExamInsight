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

function writeAscii(view: DataView, offset: number, text: string) {
  for (let index = 0; index < text.length; index += 1) {
    view.setUint8(offset + index, text.charCodeAt(index))
  }
}

function encodeMonoWav(samples: Float32Array, sampleRate: number) {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  writeAscii(view, 0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeAscii(view, 8, 'WAVE')
  writeAscii(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeAscii(view, 36, 'data')
  view.setUint32(40, samples.length * 2, true)
  for (let index = 0; index < samples.length; index += 1) {
    const value = Math.max(-1, Math.min(1, samples[index] ?? 0))
    view.setInt16(44 + index * 2, value < 0 ? value * 0x8000 : value * 0x7fff, true)
  }
  return buffer
}

async function prepareRecognitionAudio(file: File) {
  const AudioContextClass = globalThis.AudioContext
  if (!AudioContextClass) throw new Error('当前浏览器无法转换录音格式，请上传 16kHz 单声道 WAV 或 MP3')
  const context = new AudioContextClass()
  try {
    const decoded = await context.decodeAudioData(await file.arrayBuffer())
    const sampleRate = 16_000
    const frameCount = Math.max(1, Math.ceil(decoded.duration * sampleRate))
    const offline = new OfflineAudioContext(1, frameCount, sampleRate)
    const source = offline.createBufferSource()
    source.buffer = decoded
    source.connect(offline.destination)
    source.start()
    const rendered = await offline.startRendering()
    const wav = encodeMonoWav(rendered.getChannelData(0), sampleRate)
    const baseName = file.name.replace(/\.[^.]+$/, '') || 'audio'
    return new File([wav], `${baseName}.wav`, { type: 'audio/wav' })
  } catch (error) {
    throw new Error(error instanceof Error
      ? `录音格式转换失败：${error.message}`
      : '录音格式转换失败，请上传 16kHz 单声道 WAV 或 MP3')
  } finally {
    await context.close().catch(() => undefined)
  }
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
    const recognitionFile = await prepareRecognitionAudio(file)
    const formData = new FormData()
    formData.append('file', recognitionFile)
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
