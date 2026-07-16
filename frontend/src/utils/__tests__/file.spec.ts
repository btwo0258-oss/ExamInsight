import { describe, expect, it } from 'vitest'
import {
  ATTACHMENT_ACCEPT,
  attachmentMaxBytes,
  isAudioFile,
  isSupportedAttachment,
} from '@/utils/file'

describe('attachment file rules', () => {
  it.each([
    ['legacy.doc', 'application/msword'],
    ['sheet.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
    ['slides.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation'],
    ['materials.zip', 'application/zip'],
    ['recording.mp3', 'audio/mpeg'],
    ['photo.png', 'image/png'],
  ])('accepts supported attachment %s', (name, type) => {
    expect(isSupportedAttachment(new File(['content'], name, { type }))).toBe(true)
  })

  it('rejects video even when the container name resembles audio', () => {
    const video = new File(['video'], 'clip.webm', { type: 'video/webm' })
    expect(isAudioFile(video)).toBe(false)
    expect(isSupportedAttachment(video)).toBe(false)
  })

  it('rejects uncontracted image and audio formats', () => {
    expect(isSupportedAttachment(new File(['svg'], 'diagram.svg', { type: 'image/svg+xml' }))).toBe(false)
    expect(isSupportedAttachment(new File(['midi'], 'music.mid', { type: 'audio/midi' }))).toBe(false)
  })

  it('uses the media-specific upload limits', () => {
    expect(attachmentMaxBytes(new File([], 'photo.jpg', { type: 'image/jpeg' }))).toBe(10 * 1024 * 1024)
    expect(attachmentMaxBytes(new File([], 'voice.wav', { type: 'audio/wav' }))).toBe(25 * 1024 * 1024)
    expect(attachmentMaxBytes(new File([], 'slides.pptx'))).toBe(21 * 1024 * 1024)
    expect(ATTACHMENT_ACCEPT).not.toContain('video/')
  })
})
