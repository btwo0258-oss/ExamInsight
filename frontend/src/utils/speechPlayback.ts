/** Each playback owns its audio element. Aborting must silence it before settling the promise. */
export function playSpeechBlob(blob: Blob, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const abortError = () => new DOMException('朗读已停止', 'AbortError')
    if (signal.aborted) return reject(abortError())

    const url = URL.createObjectURL(blob)
    const audio = new Audio(url)
    let settled = false
    const finish = (error?: Error) => {
      if (settled) return
      settled = true
      signal.removeEventListener('abort', cancel)
      audio.onended = null
      audio.onerror = null
      // Keep the element reference until playback (including a pending play()) is stopped.
      audio.pause()
      audio.removeAttribute('src')
      audio.load()
      URL.revokeObjectURL(url)
      if (error) reject(error)
      else resolve()
    }
    const cancel = () => finish(abortError())
    const fail = () => finish(signal.aborted ? abortError() : new Error('音频播放失败，请检查浏览器音频权限。'))
    signal.addEventListener('abort', cancel, { once: true })
    audio.onended = () => finish()
    audio.onerror = fail
    try {
      void audio.play().catch(fail)
    } catch {
      fail()
    }
  })
}
