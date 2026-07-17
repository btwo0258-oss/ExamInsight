<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppButton from '@/components/common/AppButton.vue'
import AppSelectMenu from '@/components/common/AppSelectMenu.vue'
import AttachmentCard from '@/components/chat/input/AttachmentCard.vue'
import LibraryKnowledgeCreateModal from '@/components/library/LibraryKnowledgeCreateModal.vue'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import {
  ATTACHMENT_ACCEPT,
  attachmentMaxBytes,
  attachmentSizeLimitLabel,
  isSupportedAttachment,
} from '@/utils/file'

const props = defineProps<{ open: boolean; knowledgeBaseId?: number | null }>()
const emit = defineEmits<{ close: [] }>()
const libraryResourceStore = useLibraryResourceStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const fileEl = ref<HTMLInputElement | null>(null)
const files = ref<File[]>([])
const selectedKnowledgeBaseId = ref<number | null>(props.knowledgeBaseId ?? null)
const errorMessage = ref('')
const uploading = ref(false)
const knowledgeCreateOpen = ref(false)
const knowledgeBaseOptions = computed(() => [
  { value: null, label: '无', icon: 'close' },
  ...knowledgeBaseStore.list.map((item) => ({ value: item.id, label: item.name, icon: item.icon || 'folder' })),
])

function triggerUpload() {
  fileEl.value?.click()
}

function addSelectedFiles(selectedFiles: File[]) {
  errorMessage.value = ''
  if (files.value.length + selectedFiles.length > 5) {
    errorMessage.value = '最多只能上传 5 个文件'
    return
  }
  for (const file of selectedFiles) {
    if (!isSupportedAttachment(file)) {
      errorMessage.value = `文件 ${file.name} 格式不支持`
      continue
    }
    if (file.size > attachmentMaxBytes(file)) {
      errorMessage.value = `文件 ${file.name} 超过 ${attachmentSizeLimitLabel(file)} 限制`
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

async function startUpload() {
  if (!files.value.length) {
    errorMessage.value = '请先选择文件'
    return
  }
  uploading.value = true
  errorMessage.value = ''
  try {
    await libraryResourceStore.uploadFiles(files.value, 'resource-library', null, selectedKnowledgeBaseId.value)
    files.value = []
    emit('close')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '上传资料失败，请重试'
  } finally {
    uploading.value = false
  }
}

function close() {
  if (uploading.value) return
  knowledgeCreateOpen.value = false
  files.value = []
  errorMessage.value = ''
  emit('close')
}

function handleKnowledgeCreated(id: number) {
  selectedKnowledgeBaseId.value = id
  knowledgeCreateOpen.value = false
}

watch(() => props.open, async (open) => {
  if (!open) return
  files.value = []
  errorMessage.value = ''
  selectedKnowledgeBaseId.value = props.knowledgeBaseId ?? null
  if (!knowledgeBaseStore.isInitialized) {
    try {
      await knowledgeBaseStore.fetchList()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '获取知识库失败'
    }
  }
})
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="close">
    <section class="modal">
      <header>
        <h2>上传学习资料</h2>
        <button type="button" aria-label="关闭" :disabled="uploading" @click="close">×</button>
      </header>

      <button class="drop-zone" type="button" @click="triggerUpload" @dragover.prevent @drop.prevent="onDrop">
        <strong>拖拽文件到这里，或点击选择文件</strong>
        <span>支持 PDF / Word / Excel / PPT / TXT / Markdown / ZIP / 图片 / 音频</span>
      </button>
      <input ref="fileEl" hidden multiple type="file" :accept="ATTACHMENT_ACCEPT" @change="onFileChange" />

      <div v-if="files.length" class="attachment-previews">
        <AttachmentCard v-for="(file, index) in files" :key="`${file.name}-${file.lastModified}`" :file="file" @remove="removeFile(index)" />
      </div>
      <p v-if="errorMessage" class="upload-error">{{ errorMessage }}</p>

      <label class="field">
        <span>归属知识库</span>
        <AppSelectMenu
          v-model="selectedKnowledgeBaseId"
          :options="knowledgeBaseOptions"
          aria-label="选择归属知识库"
          create-label="新建知识库"
          @create="knowledgeCreateOpen = true"
        />
      </label>

      <footer>
        <AppButton variant="secondary" :disabled="uploading" @click="close">取消</AppButton>
        <AppButton variant="primary" :disabled="uploading || !files.length" @click="startUpload">
          {{ uploading ? '上传中…' : '开始上传' }}
        </AppButton>
      </footer>
    </section>
  </div>

  <LibraryKnowledgeCreateModal
    :open="knowledgeCreateOpen"
    @close="knowledgeCreateOpen = false"
    @created="handleKnowledgeCreated"
  />
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
  width: 32px;
  height: 32px;
  border-radius: var(--ui-hover-radius);
}

header button:hover {
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
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
.upload-error { margin: -4px 0 14px; color: var(--color-danger); font-size: 13px; }

.drop-zone span,
.field span {
  color: var(--color-text-muted);
  font-size: 13px;
}

.field {
  display: grid;
  gap: 8px;
}

footer {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
