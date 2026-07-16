import { beforeEach, describe, expect, it } from 'vitest'
import { mediaRepository } from '@/repositories/media'

describe('MockMediaRepository', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('stores image metadata and completes a recognition job', async () => {
    const file = new File(['image'], 'question.png', { type: 'image/png' })
    const asset = await mediaRepository.uploadImage(file, {
      source: 'camera',
      purpose: 'learning-input',
      libraryId: 1,
      clientRequestId: 'image-request-1',
    })

    expect(asset.kind).toBe('image')
    expect(asset.source).toBe('camera')
    expect(asset.status).toBe('ready')

    const created = await mediaRepository.createImageRecognitionJob(asset.id, {
      mode: 'question',
      libraryId: 1,
      clientRequestId: 'recognition-request-1',
    })
    const completed = await mediaRepository.getImageRecognitionJob(created.jobId)

    expect(completed.status).toBe('succeeded')
    expect(completed.result?.intent).toBe('question-capture')
  })

  it('returns editable mock transcription text', async () => {
    const file = new File(['audio'], 'voice.webm', { type: 'audio/webm' })
    const result = await mediaRepository.transcribeAudio(file, {
      source: 'microphone',
      purpose: 'chat-attachment',
      clientRequestId: 'audio-request-1',
      durationMs: 1500,
    })

    expect(result.asset.kind).toBe('audio')
    expect(result.text).toContain('Mock')
    expect(result.durationMs).toBe(1500)
  })

  it('keeps uploaded audio source and library context', async () => {
    const file = new File(['audio'], 'lesson.mp3', { type: 'audio/mpeg' })
    const result = await mediaRepository.transcribeAudio(file, {
      source: 'upload',
      purpose: 'library-resource',
      libraryId: 7,
      clientRequestId: 'audio-upload-request-1',
    })

    expect(result.asset.source).toBe('upload')
    expect(result.asset.purpose).toBe('library-resource')
    expect(result.asset.libraryId).toBe(7)
  })
})
