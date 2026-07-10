<script setup lang="ts">
import { ref, onMounted } from 'vue'
import ModelSwitch from './ModelSwitch.vue'
import AttachmentCard from '@/components/main-area/mode3-chat/input/AttachmentCard.vue'
import ConfirmDialog from './ConfirmDialog.vue'

interface AppInputProps {
  disabled?: boolean
  isStreaming?: boolean
  placeholder?: string
}

withDefaults(defineProps<AppInputProps>(), {
  disabled: false,
  isStreaming: false,
  placeholder: '给“助手”发送消息'
})

const emit = defineEmits<{ 
  send: [text: string, files: File[]],
  upload: [file: File]
}>()

const text = ref('')
const files = ref<File[]>([])
const areaEl = ref<HTMLTextAreaElement | null>(null)
const fileEl = ref<HTMLInputElement | null>(null)

// 错误弹窗状态
const showError = ref(false)
const errorMessage = ref('')

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) {
    const selectedFiles = Array.from(input.files)
    
    // 检查限制
    const maxSize = 21 * 1024 * 1024 // 21MB
    const maxFiles = 5
    const allowedExtensions = ['.pdf', '.docx', '.md', '.txt']
    
    if (files.value.length + selectedFiles.length > maxFiles) {
      errorMessage.value = `最多只能上传 ${maxFiles} 个文件`
      showError.value = true
      return
    }

    for (const file of selectedFiles) {
      const extension = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
      
      if (file.size > maxSize) {
        errorMessage.value = `文件 ${file.name} 超过 21MB 限制，无法解析`
        showError.value = true
        continue
      }
      
      if (!allowedExtensions.includes(extension)) {
        errorMessage.value = `文件 ${file.name} 格式不支持，仅支持 PDF、Word、Markdown、TXT`
        showError.value = true
        continue
      }
      
      files.value.push(file)
      emit('upload', file) // 触发上传事件给父组件（如果需要立即处理）
    }
  }
  input.value = ''
}

function triggerUpload() {
  fileEl.value?.click()
}

function removeFile(index: number) {
  files.value.splice(index, 1)
}

function send() {
  const trimmedText = text.value.trim()
  if (!trimmedText && files.value.length === 0) return
  
  emit('send', trimmedText, [...files.value])
  text.value = ''
  files.value = []
  if (areaEl.value) areaEl.value.style.height = 'auto'
}

function autosize() {
  if (!areaEl.value) return
  areaEl.value.style.height = 'auto'
  const scrollH = areaEl.value.scrollHeight
  // 限制最大高度为 200px (约 8-10 行)，超过后开始内部滚动
  const maxH = 200 
  areaEl.value.style.height = (scrollH > maxH ? maxH : scrollH) + 'px'
}

onMounted(() => {
  if (areaEl.value) autosize()
})
</script>

<template>
  <div class="chat-composer">
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
        :disabled="disabled || isStreaming"
        @input="autosize"
        @keydown.enter.exact.prevent="send"
        rows="1"
      ></textarea>

      <!-- 底部工具栏 (使用 pointer-events:none 确保不影响 textarea 滚动交互) -->
      <div class="toolbar-wrapper">
        <div class="toolbar">
          <div class="toolbar-left">
            <button class="icon-action-btn" @click="triggerUpload" type="button">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
              </svg>
            </button>
            <ModelSwitch />
          </div>

          <div class="toolbar-right">
            <button class="circle-send-btn" :disabled="(!text.trim() && files.length === 0) || isStreaming" @click="send">
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
  min-height: 52px;
  max-height: 200px;
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
  background: var(--color-hover);
  color: var(--color-text);
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

.footer-hint {
  text-align: center;
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 14px;
}
</style>
