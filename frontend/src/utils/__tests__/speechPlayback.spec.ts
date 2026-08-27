import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { playSpeechBlob } from '../speechPlayback'

class FakeAudio {
  static instances: FakeAudio[] = []
  onended: (() => void) | null = null
  onerror: (() => void) | null = null
  playing = false
  rejectPlay?: (error: Error) => void
  play = vi.fn(() => {
    this.playing = true
    return new Promise<void>((_resolve, reject) => { this.rejectPlay = reject })
  })
  pause = vi.fn(() => { this.playing = false })
  removeAttribute = vi.fn()
  load = vi.fn()
  constructor(public src: string) { FakeAudio.instances.push(this) }
}

describe('speech playback cancellation', () => {
  beforeEach(() => {
    FakeAudio.instances = []
    vi.stubGlobal('Audio', FakeAudio)
    vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:speech'), revokeObjectURL: vi.fn() })
  })
  afterEach(() => vi.unstubAllGlobals())

  it('silences the real audio synchronously before releasing playback', async () => {
    const controller = new AbortController()
    const result = playSpeechBlob(new Blob(['audio']), controller.signal).catch(error => error)
    const audio = FakeAudio.instances[0]!
    expect(audio.playing).toBe(true)
    controller.abort()
    expect(audio.playing).toBe(false)
    expect(audio.removeAttribute).toHaveBeenCalledWith('src')
    expect(audio.load).toHaveBeenCalledOnce()
    expect((await result).name).toBe('AbortError')
    expect(URL.revokeObjectURL).toHaveBeenCalledOnce()
  })

  it('does not start a late synthesis result after cancellation', async () => {
    const controller = new AbortController()
    controller.abort()
    await expect(playSpeechBlob(new Blob(), controller.signal)).rejects.toHaveProperty('name', 'AbortError')
    expect(FakeAudio.instances).toHaveLength(0)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it('a cancelled segment never advances to another segment', async () => {
    const controller = new AbortController()
    const playSegments = async () => {
      for (const text of ['first', 'second']) await playSpeechBlob(new Blob([text]), controller.signal)
    }
    const done = playSegments().catch(error => error)
    controller.abort()
    await done
    expect(FakeAudio.instances).toHaveLength(1)
  })

  it('late errors from an old audio cannot stop the replacement audio', async () => {
    const oldController = new AbortController()
    const oldResult = playSpeechBlob(new Blob(), oldController.signal).catch(error => error)
    const old = FakeAudio.instances[0]!
    oldController.abort()
    const newController = new AbortController()
    const newResult = playSpeechBlob(new Blob(), newController.signal).catch(error => error)
    const current = FakeAudio.instances[1]!
    old.rejectPlay!(new Error('late play error'))
    await oldResult
    await Promise.resolve()
    expect(current.playing).toBe(true)
    expect(current.pause).not.toHaveBeenCalled()
    newController.abort()
    await newResult
  })

  it('releases ended or failed audio exactly once', async () => {
    const controller = new AbortController()
    const result = playSpeechBlob(new Blob(), controller.signal)
    FakeAudio.instances[0]!.onended!()
    await result
    controller.abort()
    expect(URL.revokeObjectURL).toHaveBeenCalledOnce()
    expect(FakeAudio.instances[0]!.pause).toHaveBeenCalledOnce()
  })
})
