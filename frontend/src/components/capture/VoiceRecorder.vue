<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { LoaderCircle, Mic, Square } from 'lucide-vue-next'
import { transcribeChatAudio, type ChatTranscription } from '@/api/chatV2'
import { mediaRepository } from '@/repositories/media'
import {
  MEDIA_LIMITS,
  type AudioTranscriptionDto,
  type MediaContext,
  type MediaPurpose,
} from '@/types/contracts/media'

type VoiceState = 'idle' | 'requesting-permission' | 'recording' | 'transcribing'

const props = withDefaults(defineProps<{
  disabled?: boolean
  purpose?: Extract<MediaPurpose, 'chat-attachment' | 'learning-input'>
  context?: MediaContext
  chatV2?: boolean
}>(), {
  disabled: false,
  purpose: 'chat-attachment',
  context: () => ({}),
  chatV2: false,
})

const emit = defineEmits<{
  transcribed: [text: string, result: AudioTranscriptionDto | ChatTranscription]
  error: [message: string]
}>()

const state = ref<VoiceState>('idle')
const elapsedSeconds = ref(0)
let recorder: MediaRecorder | null = null
let stream: MediaStream | null = null
let chunks: Blob[] = []
let startedAt = 0
let elapsedTimer: number | null = null
let abortController: AbortController | null = null
let disposed = false

const title = computed(() => {
  if (state.value === 'requesting-permission') return '正在请求麦克风权限'
  if (state.value === 'recording') return '停止录音并识别'
  if (state.value === 'transcribing') return '正在识别语音'
  return '语音输入'
})

const statusText = computed(() => {
  if (state.value === 'requesting-permission') return '授权中'
  if (state.value === 'recording') return `${elapsedSeconds.value}s`
  if (state.value === 'transcribing') return '识别中'
  return ''
})

function clientRequestId() {
  return globalThis.crypto?.randomUUID?.() ?? `media-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function clearElapsedTimer() {
  if (elapsedTimer !== null) window.clearInterval(elapsedTimer)
  elapsedTimer = null
}

function releaseStream() {
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
}

function friendlyError(error: unknown) {
  const name = error instanceof DOMException ? error.name : ''
  if (name === 'NotAllowedError' || name === 'SecurityError') return '麦克风权限被拒绝，请在浏览器设置中允许后重试'
  if (name === 'NotFoundError') return '未检测到可用麦克风'
  if (name === 'NotReadableError') return '麦克风正被其他应用占用'
  return error instanceof Error ? error.message : '语音录制或识别失败，请重试'
}

function preferredMimeType() {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus']
  return candidates.find((type) => MediaRecorder.isTypeSupported(type))
}

async function transcribe() {
  if (disposed || !chunks.length) {
    state.value = 'idle'
    return
  }
  const durationMs = Date.now() - startedAt
  const type = recorder?.mimeType || chunks[0]?.type || 'audio/webm'
  const extension = type.includes('mp4') ? 'm4a' : type.includes('ogg') ? 'ogg' : 'webm'
  const file = new File(chunks, `voice-${Date.now()}.${extension}`, { type })
  chunks = []
  if (!file.size) {
    state.value = 'idle'
    emit('error', '没有录到有效语音，请重试')
    return
  }
  if (file.size > MEDIA_LIMITS.audioMaxBytes) {
    state.value = 'idle'
    emit('error', '录音文件超过 25MB 限制')
    return
  }

  state.value = 'transcribing'
  abortController = new AbortController()
  try {
    const result = props.chatV2
      ? await transcribeChatAudio(file, abortController.signal)
      : await mediaRepository.transcribeAudio(file, {
        ...props.context,
        source: 'microphone',
        purpose: props.purpose,
        clientRequestId: clientRequestId(),
        language: 'zh-CN',
        durationMs,
      }, abortController.signal)
    if (!disposed) emit('transcribed', result.text, result)
  } catch (error) {
    if (!disposed && !(error instanceof DOMException && error.name === 'AbortError')) {
      emit('error', friendlyError(error))
    }
  } finally {
    abortController = null
    if (!disposed) state.value = 'idle'
  }
}

function stopRecording() {
  if (recorder?.state === 'recording') recorder.stop()
}

async function startRecording() {
  if (props.disabled || state.value !== 'idle') return
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    emit('error', '当前浏览器不支持录音，请使用最新版 Chrome、Edge 或 Safari')
    return
  }

  state.value = 'requesting-permission'
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    if (disposed) {
      releaseStream()
      return
    }
    const mimeType = preferredMimeType()
    recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream)
    chunks = []
    recorder.ondataavailable = (event) => {
      if (event.data.size) chunks.push(event.data)
    }
    recorder.onstop = () => {
      clearElapsedTimer()
      releaseStream()
      void transcribe()
    }
    recorder.onerror = () => {
      clearElapsedTimer()
      releaseStream()
      chunks = []
      state.value = 'idle'
      emit('error', '录音过程中发生错误，请重试')
    }
    startedAt = Date.now()
    elapsedSeconds.value = 0
    recorder.start(250)
    state.value = 'recording'
    elapsedTimer = window.setInterval(() => {
      elapsedSeconds.value = Math.floor((Date.now() - startedAt) / 1000)
      if (Date.now() - startedAt >= MEDIA_LIMITS.audioMaxDurationMs) stopRecording()
    }, 500)
  } catch (error) {
    releaseStream()
    state.value = 'idle'
    emit('error', friendlyError(error))
  }
}

function toggleRecording() {
  if (state.value === 'recording') stopRecording()
  else void startRecording()
}

onBeforeUnmount(() => {
  disposed = true
  clearElapsedTimer()
  abortController?.abort()
  if (recorder?.state === 'recording') recorder.stop()
  releaseStream()
})
</script>

<template>
  <div class="voice-control">
    <span v-if="statusText" class="voice-status" aria-live="polite">{{ statusText }}</span>
    <button
      class="voice-button"
      :class="{ 'voice-button--recording': state === 'recording' }"
      type="button"
      :title="title"
      :aria-label="title"
      :aria-pressed="state === 'recording'"
      :disabled="disabled || state === 'requesting-permission' || state === 'transcribing'"
      @click="toggleRecording"
    >
      <LoaderCircle v-if="state === 'requesting-permission' || state === 'transcribing'" class="spin" :size="19" />
      <Square v-else-if="state === 'recording'" :size="15" fill="currentColor" />
      <Mic v-else :size="20" :stroke-width="1.9" />
    </button>
  </div>
</template>

<style scoped>
.voice-control {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.voice-status {
  font-size: 12px;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.voice-button {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}

.voice-button:hover:not(:disabled) {
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
}

.voice-button--recording {
  background: color-mix(in srgb, var(--color-danger) 12%, transparent);
  color: var(--color-danger);
}

.voice-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
