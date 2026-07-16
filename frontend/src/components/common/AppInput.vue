<script setup lang="ts">
import { nextTick, ref } from 'vue'
import ModelSwitch from './ModelSwitch.vue'
import AttachmentCard from '@/components/chat/input/AttachmentCard.vue'
import ImageCaptureUploader from '@/components/capture/ImageCaptureUploader.vue'
import VoiceRecorder from '@/components/capture/VoiceRecorder.vue'
import ConfirmDialog from './ConfirmDialog.vue'
import { MEDIA_LIMITS, type MediaContext, type MediaPurpose } from '@/types/contracts/media'

interface AppInputProps {
  disabled?: boolean
  isStreaming?: boolean
  placeholder?: string
  mediaEnabled?: boolean
  mediaPurpose?: Extract<MediaPurpose, 'chat-attachment' | 'learning-input'>
  mediaContext?: MediaContext
}

const props = withDefaults(defineProps<AppInputProps>(), {
  disabled: false,
  isStreaming: false,
  placeholder: '给“助手”发送消息',
  mediaEnabled: false,
  mediaPurpose: 'chat-attachment',
  mediaContext: () => ({}),
})

const emit = defineEmits<{ 
  send: [text: string, files: File[], complete?: (success?: boolean) => void],
  upload: [file: File],
  stop: []
}>()

const text = ref('')
const files = ref<File[]>([])
const submitting = ref(false)
const areaEl = ref<HTMLTextAreaElement | null>(null)
const fileEl = ref<HTMLInputElement | null>(null)

// 错误弹窗状态
const showError = ref(false)
const errorMessage = ref('')

function showInputError(message: string) {
  errorMessage.value = message
  showError.value = true
}

function addFiles(selectedFiles: File[], allowImages: boolean) {
  if (files.value.length + selectedFiles.length > MEDIA_LIMITS.composerMaxFiles) {
    showInputError(`最多只能上传 ${MEDIA_LIMITS.composerMaxFiles} 个文件`)
    return
  }

  const allowedExtensions = ['.pdf', '.docx', '.md', '.txt']
  for (const file of selectedFiles) {
    const extension = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
    const isImage = file.type.startsWith('image/') || ['.jpg', '.jpeg', '.png', '.webp', '.heic', '.heif'].includes(extension)
    const maxSize = isImage ? MEDIA_LIMITS.imageMaxBytes : 21 * 1024 * 1024

    if (file.size > maxSize) {
      showInputError(`文件 ${file.name} 超过 ${isImage ? '10MB' : '21MB'} 限制`)
      continue
    }
    if ((!allowImages || !isImage) && !allowedExtensions.includes(extension)) {
      showInputError(`文件 ${file.name} 格式不支持`)
      continue
    }
    files.value.push(file)
    emit('upload', file)
  }
}

function onFileChange(e: Event) {
  if (props.disabled || props.isStreaming || submitting.value) return
  const input = e.target as HTMLInputElement
  if (input.files) {
    addFiles(Array.from(input.files), false)
  }
  input.value = ''
}

function onImagesSelected(selectedFiles: File[]) {
  if (props.disabled || props.isStreaming || submitting.value) return
  addFiles(selectedFiles, true)
}

function triggerUpload() {
  if (props.disabled || props.isStreaming || submitting.value) return
  fileEl.value?.click()
}

function setText(nextText: string) {
  text.value = nextText
  nextTick(() => {
    areaEl.value?.focus()
  })
}

function appendTranscript(transcript: string) {
  const next = transcript.trim()
  if (!next) return
  const area = areaEl.value
  const start = area?.selectionStart ?? text.value.length
  const end = area?.selectionEnd ?? text.value.length
  const before = text.value.slice(0, start)
  const after = text.value.slice(end)
  const prefix = before && !/\s$/.test(before) ? ' ' : ''
  const suffix = after && !/^\s/.test(after) ? ' ' : ''
  text.value = `${before}${prefix}${next}${suffix}${after}`
  nextTick(() => {
    const cursor = start + prefix.length + next.length + suffix.length
    area?.focus()
    area?.setSelectionRange(cursor, cursor)
  })
}

defineExpose({ triggerUpload, setText })

function removeFile(index: number) {
  files.value.splice(index, 1)
}

function send() {
  if (props.disabled || props.isStreaming || submitting.value) return
  const trimmedText = text.value.trim()
  if (!trimmedText && files.value.length === 0) return

  if (!props.mediaEnabled) {
    emit('send', trimmedText, [...files.value])
    text.value = ''
    files.value = []
    return
  }

  submitting.value = true
  emit('send', trimmedText, [...files.value], (success = true) => {
    if (success) {
      text.value = ''
      files.value = []
    }
    submitting.value = false
    if (!success) nextTick(() => areaEl.value?.focus())
  })
}
</script>

<template>
  <div class="chat-composer">
    <div v-if="$slots.context" class="composer-context">
      <slot name="context" />
    </div>
    <!-- 附件预览 -->
    <div v-if="files.length" class="attachment-previews">
      <AttachmentCard v-for="(f, i) in files" :key="i" :file="f" @remove="removeFile(i)" />
    </div>

    <!-- 主输入框容器 -->
    <div class="input-container">
      <textarea
        ref="areaEl"
        v-model="text"
        class="main-textarea"
        :placeholder="placeholder"
        :disabled="disabled || isStreaming || submitting"
        @keydown.enter.exact.prevent="send"
        rows="1"
      ></textarea>

      <!-- 底部工具栏 (使用 pointer-events:none 确保不影响 textarea 滚动交互) -->
      <div class="toolbar-wrapper">
        <div class="toolbar">
          <div class="toolbar-left">
            <button
              class="icon-action-btn"
              type="button"
              title="上传文档"
              :disabled="disabled || isStreaming || submitting"
              @click="triggerUpload"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
              </svg>
            </button>
            <ImageCaptureUploader
              v-if="mediaEnabled"
              :disabled="disabled || isStreaming || submitting"
              :remaining-count="MEDIA_LIMITS.composerMaxFiles - files.length"
              @select="onImagesSelected"
              @error="showInputError"
            />
            <ModelSwitch />
          </div>

          <div class="toolbar-right">
            <VoiceRecorder
              v-if="mediaEnabled"
              :disabled="disabled || isStreaming || submitting"
              :purpose="mediaPurpose"
              :context="mediaContext"
              @transcribed="appendTranscript"
              @error="showInputError"
            />
            <button
              v-if="isStreaming"
              class="circle-send-btn circle-stop-btn"
              type="button"
              title="停止生成"
              aria-label="停止生成"
              @click="emit('stop')"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <rect x="6" y="6" width="12" height="12" rx="1"></rect>
              </svg>
            </button>
            <button
              v-else
              class="circle-send-btn"
              type="button"
              title="发送"
              :disabled="disabled || submitting || (!text.trim() && files.length === 0)"
              @click="send"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" y1="19" x2="12" y2="5"></line>
                <polyline points="5 12 12 5 19 12"></polyline>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <input 
      ref="fileEl" 
      type="file" 
      multiple 
      hidden 
      accept=".pdf,.docx,.md,.txt"
      @change="onFileChange" 
    />
    <div class="footer-hint">内容由 AI 生成，请核实重要信息。</div>

    <ConfirmDialog
      :open="showError"
      title="上传提示"
      :message="errorMessage"
      confirm-text="知道了"
      cancel-text=""
      @close="showError = false"
      @confirm="showError = false"
    />
  </div>
</template>

<style scoped>
.chat-composer {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
  padding: 0 16px 40px 16px; 
}

.attachment-previews {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.composer-context {
  position: relative;
  z-index: 4;
  margin: 0 12px -1px;
}

.input-container {
  position: relative;
  /* 1. 背景色改为指定颜色 */
  background: var(--color-surface);
  border-radius: 24px;
  border: 1px solid var(--color-border);
  transition: all 0.2s ease;
  /* 移除 overflow: hidden 否则会挡住 ModelSwitch 的下拉框 */
  box-sizing: border-box;
}

.main-textarea {
  width: 100%;
  height: 100px;
  min-height: 100px;
  max-height: 100px;
  /* 3. 重要：增加底部 Padding，确保文字不会穿过/被挡住工具栏 */
  padding: 14px 12px 60px 16px; 
  border: none;
  outline: none;
  background: transparent;
  font-size: 16px;
  line-height: 1.6;
  color: var(--color-text);
  resize: none;
  display: block;
  box-sizing: border-box;
  overflow-y: auto; /* 开启滚动 */
  border-radius: 24px;
}

.main-textarea::placeholder {
  color: var(--color-text-muted);
}

/* 2. 滚动条美化：在框内且较短 */
.main-textarea::-webkit-scrollbar {
  width: 6px; /* 较细 */
}

.main-textarea::-webkit-scrollbar-thumb {
  background-color: var(--color-border); /* 灰色小胶囊 */
  border-radius: 10px;
  border: 2px solid transparent; /* 制造留白感 */
  background-clip: content-box;
}

.main-textarea::-webkit-scrollbar-track {
  background: transparent;
}

/* 工具栏容器 */
.toolbar-wrapper {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 8px 10px;
  /* 稍微遮罩背景文字 */
  background: linear-gradient(to top, var(--color-surface) 70%, transparent);
  pointer-events: none; /* 让点击能穿透到底层的 textarea */
  border-bottom-left-radius: 24px;
  border-bottom-right-radius: 24px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  pointer-events: auto; /* 恢复工具栏内部按钮的点击 */
}

.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.icon-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.icon-action-btn:hover {
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
}

.icon-action-btn:disabled {
  color: var(--color-text-muted);
  cursor: not-allowed;
  opacity: 0.5;
}

.icon-action-btn:disabled:hover {
  background: transparent;
}

.circle-send-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: var(--color-text);
  color: var(--color-bg);
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.2s;
}

.circle-send-btn:disabled {
  background: var(--color-hover-strong);
  color: var(--color-text-muted);
  cursor: not-allowed;
}

.circle-stop-btn {
  background: var(--color-text);
}

.footer-hint {
  text-align: center;
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 14px;
}
</style>
