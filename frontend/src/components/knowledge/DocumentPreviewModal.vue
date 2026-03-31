<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import AppModal from '@/components/common/AppModal.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import AppButton from '@/components/common/AppButton.vue'
import { useDocumentStore } from '@/stores/document'
import { renderMarkdownToHtml } from '@/utils/markdown'
import mammoth from 'mammoth'

const props = defineProps<{
  open: boolean
  documentId: number
  fileName: string
}>()

const emit = defineEmits(['close'])

const docStore = useDocumentStore()
const loading = ref(true)
const error = ref('')
const previewData = ref<{ type: string; content: string | Blob | null }>({
  type: '',
  content: null
})

const pdfUrl = ref('')
const htmlContent = ref('')
const textContent = ref('')
const originalText = ref('') // 用于编辑
const isEditing = ref(false)
const isDrawing = ref(false)

// 画布相关
const canvasRef = ref<HTMLCanvasElement | null>(null)
const ctx = ref<CanvasRenderingContext2D | null>(null)
const drawing = ref(false)
const brushColor = ref('#ff4d4f')
const brushSize = ref(2)

onMounted(async () => {
  try {
    const res = await docStore.getPreview(props.documentId)
    previewData.value = res
    
    if (res.type === 'pdf' && res.content instanceof Blob) {
      pdfUrl.value = URL.createObjectURL(res.content)
    } else if (res.type === 'docx' && res.content instanceof Blob) {
      const arrayBuffer = await res.content.arrayBuffer()
      const result = await mammoth.convertToHtml({ arrayBuffer })
      htmlContent.value = result.value
      // 如果需要提取纯文本供编辑
      const textResult = await mammoth.extractRawText({ arrayBuffer })
      originalText.value = textResult.value
    } else if (res.type === 'text') {
      originalText.value = res.content as string
      textContent.value = renderMarkdownToHtml(res.content as string)
    }
  } catch (err) {
    error.value = '加载预览失败'
    console.error(err)
  } finally {
    loading.value = false
  }
})

// 监听涂鸦模式，初始化画布
watch(isDrawing, (val) => {
  if (val) {
    setTimeout(initCanvas, 100)
  }
})

function initCanvas() {
  if (!canvasRef.value) return
  const canvas = canvasRef.value
  const parent = canvas.parentElement
  if (!parent) return
  
  canvas.width = parent.clientWidth
  canvas.height = parent.clientHeight
  ctx.value = canvas.getContext('2d')
  if (ctx.value) {
    ctx.value.lineCap = 'round'
    ctx.value.lineJoin = 'round'
  }
}

function startDrawing(e: MouseEvent) {
  if (!isDrawing.value || !ctx.value) return
  drawing.value = true
  const rect = canvasRef.value!.getBoundingClientRect()
  ctx.value.beginPath()
  ctx.value.moveTo(e.clientX - rect.left, e.clientY - rect.top)
}

function draw(e: MouseEvent) {
  if (!drawing.value || !isDrawing.value || !ctx.value) return
  const rect = canvasRef.value!.getBoundingClientRect()
  ctx.value.strokeStyle = brushColor.value
  ctx.value.lineWidth = brushSize.value
  ctx.value.lineTo(e.clientX - rect.left, e.clientY - rect.top)
  ctx.value.stroke()
}

function stopDrawing() {
  drawing.value = false
}

function clearCanvas() {
  if (!ctx.value || !canvasRef.value) return
  ctx.value.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
}

async function handleSave() {
  try {
    loading.value = true
    if (isEditing.value) {
      // 保存文本修改
      await docStore.saveContent(props.documentId, originalText.value)
      if (previewData.value.type === 'text') {
        textContent.value = renderMarkdownToHtml(originalText.value)
      } else if (previewData.value.type === 'docx') {
        htmlContent.value = originalText.value
      }
      isEditing.value = false
    }
    // 涂鸦层目前仅在前端展示，如需保存需要转为图片或数据
  } catch (err) {
    error.value = '保存失败'
  } finally {
    loading.value = false
  }
}

function handleDownload() {
  docStore.download(props.documentId, props.fileName)
}

function handleClose() {
  if (pdfUrl.value) {
    URL.revokeObjectURL(pdfUrl.value)
  }
  emit('close')
}
</script>

<template>
  <AppModal
    :open="open"
    :title="fileName"
    width="80vw"
    max-width="1400px"
    @close="handleClose"
  >
    <div class="preview-container">
      <div v-if="loading" class="loading">
        <AppIcon name="loading" class="spin" :size="32" />
        <p>正在处理...</p>
      </div>
      
      <div v-else-if="error" class="error">
        <AppIcon name="error" :size="48" />
        <p>{{ error }}</p>
        <AppButton variant="primary" @click="handleDownload">下载文件查看</AppButton>
      </div>
      
      <div v-else-if="previewData.type === 'pdf'" class="preview-content preview-content--pdf">
        <!-- PDF 预览 (原生简洁模式) -->
        <iframe 
          v-if="pdfUrl" 
          :src="pdfUrl" 
          class="pdf-viewer"
          frameborder="0"
        ></iframe>
      </div>

      <div v-else class="preview-layout">
        <!-- 工具栏 (非 PDF 显示) -->
        <div class="preview-toolbar">
          <div class="toolbar-group">
            <button 
              class="tool-btn" 
              :class="{ active: !isEditing && !isDrawing }"
              @click="isEditing = false; isDrawing = false"
            >
              <AppIcon name="eye" :size="16" />
              <span>查看</span>
            </button>
            <button 
              class="tool-btn" 
              :class="{ active: isEditing }"
              @click="isEditing = true; isDrawing = false"
            >
              <AppIcon name="edit" :size="16" />
              <span>编辑</span>
            </button>
            <button 
              class="tool-btn" 
              :class="{ active: isDrawing }"
              @click="isDrawing = true; isEditing = false"
            >
              <AppIcon name="pen-tool" :size="16" />
              <span>涂鸦</span>
            </button>
          </div>

          <div class="toolbar-divider" v-if="isDrawing"></div>

          <div class="toolbar-group" v-if="isDrawing">
            <input type="color" v-model="brushColor" class="color-picker" />
            <select v-model="brushSize" class="size-select">
              <option :value="2">细</option>
              <option :value="5">中</option>
              <option :value="10">粗</option>
            </select>
            <button class="tool-btn" @click="clearCanvas">
              <AppIcon name="trash" :size="16" />
              <span>清除</span>
            </button>
          </div>
          
          <div class="toolbar-spacer"></div>
          
          <AppButton v-if="isEditing" variant="primary" @click="handleSave">
            保存修改
          </AppButton>
        </div>

        <div class="preview-content-wrapper" :class="{ 'is-drawing': isDrawing }">
          <!-- 编辑模式 -->
          <textarea 
            v-if="isEditing" 
            v-model="originalText" 
            class="edit-area"
            placeholder="在这里输入内容..."
          ></textarea>

          <!-- 查看模式 / 涂鸦模式底层 -->
          <div v-else class="content-body">
            <!-- Word/HTML 预览 -->
            <div 
              v-if="previewData.type === 'docx'" 
              class="html-viewer markdown" 
              v-html="htmlContent"
            ></div>
            
            <!-- Text/Markdown 预览 -->
            <div 
              v-else-if="previewData.type === 'text'" 
              class="text-viewer markdown" 
              v-html="textContent"
            ></div>
            
            <div v-else class="unsupported">
              <p>该文件类型暂不支持在线预览</p>
              <AppButton variant="primary" @click="handleDownload">下载文件查看</AppButton>
            </div>
          </div>

          <!-- 涂鸦层 -->
          <canvas 
            v-show="isDrawing"
            ref="canvasRef"
            class="drawing-canvas"
            @mousedown="startDrawing"
            @mousemove="draw"
            @mouseup="stopDrawing"
            @mouseleave="stopDrawing"
          ></canvas>
        </div>
      </div>
    </div>
    
    <template #footer>
      <div class="footer-actions">
        <AppButton variant="ghost" @click="handleDownload">
          <AppIcon name="download" :size="16" />
          下载
        </AppButton>
        <AppButton variant="primary" @click="handleClose">关闭</AppButton>
      </div>
    </template>
  </AppModal>
</template>

<style scoped>
.preview-container {
  height: 75vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-alt);
  border-radius: 8px;
  overflow: hidden;
}

.preview-content--pdf {
  height: 100%;
  padding: 0;
}

.preview-layout {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.preview-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-border);
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background: var(--color-border);
  margin: 0 4px;
}

.toolbar-spacer {
  flex: 1;
}

.tool-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 6px;
  color: var(--color-text-muted);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.tool-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text);
}

.tool-btn.active {
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-color: var(--color-primary-light);
}

.color-picker {
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
}

.size-select {
  padding: 2px 4px;
  border-radius: 4px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  font-size: 12px;
}

.preview-content-wrapper {
  flex: 1;
  position: relative;
  overflow: auto;
  background: white;
}

.preview-content-wrapper.is-drawing {
  overflow: hidden; /* 涂鸦模式下禁止滚动，防止错位 */
}

.edit-area {
  width: 100%;
  height: 100%;
  padding: 32px;
  border: none;
  resize: none;
  font-family: inherit;
  font-size: 15px;
  line-height: 1.6;
  outline: none;
  color: var(--color-text);
}

.content-body {
  height: 100%;
  padding: 20px;
}

.content-body--no-padding {
  padding: 0;
}

.drawing-canvas {
  position: absolute;
  top: 0;
  left: 0;
  cursor: crosshair;
  z-index: 10;
}

.loading, .error, .unsupported {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--color-text-light);
}

.pdf-viewer {
  width: 100%;
  height: 100%;
  border: none;
}

.html-viewer, .text-viewer {
  max-width: 800px;
  margin: 0 auto;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
