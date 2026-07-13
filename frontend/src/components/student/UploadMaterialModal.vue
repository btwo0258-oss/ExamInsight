<script setup lang="ts">
import { ref, watch } from 'vue'
import AppButton from '@/components/common/AppButton.vue'
import AttachmentCard from '@/components/main-area/mode3-chat/input/AttachmentCard.vue'
import { courseLibraries } from '@/mock'
import { useLibraryResourceStore } from '@/stores/libraryResource'

const props = defineProps<{ open: boolean; libraryId?: number | null }>()
const emit = defineEmits<{ close: [] }>()
const libraryResourceStore = useLibraryResourceStore()
const fileEl = ref<HTMLInputElement | null>(null)
const files = ref<File[]>([])
const selectedLibraryId = ref<number | null>(props.libraryId ?? courseLibraries[0]?.id ?? null)
const errorMessage = ref('')

function triggerUpload() {
  fileEl.value?.click()
}

function addSelectedFiles(selectedFiles: File[]) {
  const allowedExtensions = ['.pdf', '.doc', '.docx', '.md', '.txt', '.png', '.jpg', '.jpeg']
  errorMessage.value = ''
  if (files.value.length + selectedFiles.length > 5) {
    errorMessage.value = '最多只能上传 5 个文件'
    return
  }
  for (const file of selectedFiles) {
    const extension = file.name.slice(file.name.lastIndexOf('.')).toLowerCase()
    if (!allowedExtensions.includes(extension)) {
      errorMessage.value = `文件 ${file.name} 格式不支持`
      continue
    }
    if (file.size > 21 * 1024 * 1024) {
      errorMessage.value = `文件 ${file.name} 超过 21MB 限制`
      continue
    }
    files.value.push(file)
  }
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  addSelectedFiles(Array.from(input.files ?? []))
  input.value = ''
}

function onDrop(event: DragEvent) {
  addSelectedFiles(Array.from(event.dataTransfer?.files ?? []))
}

function removeFile(index: number) {
  files.value.splice(index, 1)
}

function startUpload() {
  if (!files.value.length) {
    errorMessage.value = '请先选择文件'
    return
  }
  libraryResourceStore.addFiles(files.value, '资料库上传', null, selectedLibraryId.value)
  files.value = []
  emit('close')
}

watch(() => props.open, (open) => {
  if (open && props.libraryId !== undefined) selectedLibraryId.value = props.libraryId
})
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal">
      <header>
        <h2>上传学习资料</h2>
        <button type="button" @click="emit('close')">×</button>
      </header>

      <button class="drop-zone" type="button" @click="triggerUpload" @dragover.prevent @drop.prevent="onDrop">
        <strong>拖拽文件到这里，或点击选择文件</strong>
        <span>支持 PDF / Word / TXT / Markdown / 图片</span>
      </button>
      <input ref="fileEl" hidden multiple type="file" accept=".pdf,.doc,.docx,.md,.txt,.png,.jpg,.jpeg" @change="onFileChange" />

      <div v-if="files.length" class="attachment-previews">
        <AttachmentCard v-for="(file, index) in files" :key="`${file.name}-${file.lastModified}`" :file="file" @remove="removeFile(index)" />
      </div>
      <p v-if="errorMessage" class="upload-error">{{ errorMessage }}</p>

      <label class="field">
        <span>归属资料库</span>
        <select v-model="selectedLibraryId">
          <option :value="null">无</option>
          <option v-for="item in courseLibraries" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
      </label>

      <label class="check">
        <input type="checkbox" checked />
        <span>上传后立即用于本次智能学习分析</span>
      </label>

      <footer>
        <AppButton variant="secondary" @click="emit('close')">取消</AppButton>
        <AppButton variant="primary" @click="startUpload">开始上传</AppButton>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.32);
  z-index: 200;
  display: grid;
  place-items: center;
  padding: 24px;
}

.modal {
  width: min(540px, 100%);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 18px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.22);
}

header,
footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

header h2 {
  margin: 0;
  font-size: 18px;
}

header button {
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: 24px;
  color: var(--color-text-muted);
}

.drop-zone {
  width: 100%;
  margin: 16px 0;
  min-height: 130px;
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  background: var(--color-bg);
  text-align: center;
  color: var(--color-text);
  cursor: pointer;
}

.attachment-previews { display: flex; flex-wrap: wrap; gap: 8px; margin: 0 0 14px; }
.upload-error { margin: -4px 0 14px; color: #dc2626; font-size: 13px; }

.drop-zone span,
.field span,
.check {
  color: var(--color-text-muted);
  font-size: 13px;
}

.field {
  display: grid;
  gap: 8px;
}

select {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--color-bg);
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 14px 0 18px;
}

footer {
  justify-content: flex-end;
}
</style>
