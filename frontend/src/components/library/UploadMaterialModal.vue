<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CheckCircle2, FileUp, LoaderCircle, Plus, X, XCircle } from 'lucide-vue-next'
import AppButton from '@/components/common/AppButton.vue'
import AppModal from '@/components/common/AppModal.vue'
import AppSelectMenu from '@/components/common/AppSelectMenu.vue'
import V2KnowledgeBaseModal from '@/components/library/V2KnowledgeBaseModal.vue'
import { getAsset, uploadAsset } from '@/api/assetLibraryV2'
import { useAssetLibraryV2Store } from '@/stores/assetLibraryV2'
import type { KnowledgeBase } from '@/types/contracts/assetLibraryV2'

type UploadEntry = {
  key: string
  file: File
  status: 'waiting' | 'uploading' | 'processing' | 'done' | 'failed'
  percentage: number
  message: string
}

const props = withDefaults(defineProps<{
  open: boolean
  knowledgeBaseId?: string | null
}>(), { knowledgeBaseId: null })
const emit = defineEmits<{ close: []; uploaded: [assetIds: string[]] }>()
const store = useAssetLibraryV2Store()
const fileInput = ref<HTMLInputElement | null>(null)
const entries = ref<UploadEntry[]>([])
const selectedKnowledgeBaseId = ref<string | null>(null)
const createOpen = ref(false)
const uploading = ref(false)
const generalError = ref('')
const uploadedAssetIds = ref<string[]>([])

const completeCount = computed(() => entries.value.filter((entry) => entry.status === 'done').length)
const hasUnfinished = computed(() => entries.value.some((entry) => ['waiting', 'failed'].includes(entry.status)))
const knowledgeBaseOptions = computed<Array<{ value: string | null; label: string }>>(() => [
  { value: null, label: '暂不加入知识库' },
  ...store.knowledgeBases.map((knowledgeBase) => ({
    value: knowledgeBase.knowledgeBaseId,
    label: knowledgeBase.name,
  })),
])
const accept = '.pdf,.docx,.pptx,.xlsx,.txt,.md,.csv,.jpg,.jpeg,.png,.webp'
const allowedExtensions = new Set(['pdf', 'docx', 'pptx', 'xlsx', 'txt', 'md', 'csv', 'jpg', 'jpeg', 'png', 'webp'])
const maximumBytes = 100 * 1024 * 1024

watch(() => props.open, (open) => {
  if (!open) return
  entries.value = []
  selectedKnowledgeBaseId.value = props.knowledgeBaseId
  uploadedAssetIds.value = []
  generalError.value = ''
})

function chooseFiles() {
  fileInput.value?.click()
}

function addFiles(files: File[]) {
  generalError.value = ''
  for (const file of files) {
    const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
    if (!allowedExtensions.has(extension)) {
      generalError.value = `“${file.name}”格式暂不支持。`
      continue
    }
    if (file.size < 1 || file.size > maximumBytes) {
      generalError.value = `“${file.name}”必须大于 0 且不超过 100 MB。`
      continue
    }
    const duplicate = entries.value.some(
      (entry) => entry.file.name === file.name && entry.file.size === file.size && entry.file.lastModified === file.lastModified,
    )
    if (!duplicate) {
      entries.value.push({
        key: `${file.name}-${file.size}-${file.lastModified}`,
        file,
        status: 'waiting',
        percentage: 0,
        message: '等待上传',
      })
    }
  }
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  addFiles(Array.from(input.files ?? []))
  input.value = ''
}

function removeEntry(key: string) {
  if (uploading.value) return
  entries.value = entries.value.filter((entry) => entry.key !== key)
}

function statusLabel(entry: UploadEntry) {
  if (entry.status === 'uploading') return `上传中 ${entry.percentage}%`
  if (entry.status === 'processing') return '已上传，等待安全检查'
  if (entry.status === 'done') return entry.message || '上传完成'
  if (entry.status === 'failed') return entry.message
  return '等待上传'
}

async function startUpload() {
  if (!entries.value.length || uploading.value) return
  uploading.value = true
  generalError.value = ''
  const completed: string[] = []
  for (const entry of entries.value) {
    if (entry.status === 'done') continue
    entry.status = 'uploading'
    entry.percentage = 0
    entry.message = ''
    try {
      const result = await uploadAsset(entry.file, selectedKnowledgeBaseId.value, (progress) => {
        entry.percentage = progress.percentage
      })
      entry.status = 'processing'
      const detail = await getAsset(result.completion.asset.assetId)
      store.upsertUploadedAsset(detail.asset)
      completed.push(detail.asset.assetId)
      uploadedAssetIds.value.push(detail.asset.assetId)
      entry.status = 'done'
      entry.message = result.associationWarning || '上传完成，正在安全检查与解析'
    } catch (cause) {
      entry.status = 'failed'
      entry.message = cause instanceof Error ? cause.message : '上传失败，请重试。'
    }
  }
  uploading.value = false
  if (completed.length) emit('uploaded', completed)
}

function handleKnowledgeBaseSaved(item: KnowledgeBase) {
  selectedKnowledgeBaseId.value = item.knowledgeBaseId
  createOpen.value = false
}

function close() {
  if (uploading.value) return
  emit('close')
}
</script>

<template>
  <AppModal
    :open="open"
    width="min(720px, 100%)"
    :close-on-backdrop="!uploading"
    title="上传学习资料"
    @close="close"
  >
    <div class="upload-dialog">
      <button
        class="drop-zone"
        type="button"
        :disabled="uploading"
        @click="chooseFiles"
        @dragover.prevent
        @drop.prevent="addFiles(Array.from($event.dataTransfer?.files ?? []))"
      >
        <FileUp :size="28" />
        <strong>拖入文件，或点击选择</strong>
        <span>支持 PDF、DOCX、PPTX、XLSX、TXT、MD、CSV、JPG、PNG、WebP，单个不超过 100 MB</span>
      </button>
      <input ref="fileInput" hidden multiple type="file" :accept="accept" @change="onFileChange" />

      <div class="knowledge-select">
        <span>上传后加入知识库 <small>可选，不选择则只进入个人资料库</small></span>
        <div class="knowledge-select__row">
          <AppSelectMenu
            v-model="selectedKnowledgeBaseId"
            class="knowledge-select__menu"
            :options="knowledgeBaseOptions"
            :disabled="uploading"
            aria-label="上传后加入知识库"
          />
          <button class="knowledge-select__create" type="button" :disabled="uploading" @click="createOpen = true">
            <Plus :size="15" />新建
          </button>
        </div>
      </div>

      <div v-if="entries.length" class="upload-list">
        <article v-for="entry in entries" :key="entry.key" :class="`is-${entry.status}`">
          <div class="file-status-icon">
            <LoaderCircle v-if="entry.status === 'uploading' || entry.status === 'processing'" class="spin" :size="18" />
            <CheckCircle2 v-else-if="entry.status === 'done'" :size="18" />
            <XCircle v-else-if="entry.status === 'failed'" :size="18" />
            <FileUp v-else :size="18" />
          </div>
          <div class="file-copy">
            <strong>{{ entry.file.name }}</strong>
            <span>{{ statusLabel(entry) }}</span>
            <div v-if="entry.status === 'uploading'" class="progress-track">
              <i :style="{ width: `${entry.percentage}%` }" />
            </div>
          </div>
          <button
            v-if="!uploading && entry.status !== 'done'"
            class="remove-file"
            type="button"
            aria-label="移除文件"
            @click="removeEntry(entry.key)"
          >
            <X :size="16" />
          </button>
        </article>
      </div>

      <p v-if="generalError" class="upload-error" role="alert">{{ generalError }}</p>
      <p v-if="completeCount" class="upload-note">
        已完成 {{ completeCount }} 个文件。文件会先进入安全检查与解析，完成后才能预览和用于 AI 检索。
      </p>
    </div>

    <template #footer>
      <div class="upload-actions">
        <AppButton variant="ghost" :disabled="uploading" @click="close">
          {{ completeCount && !hasUnfinished ? '完成' : '取消' }}
        </AppButton>
        <AppButton
          :loading="uploading"
          :disabled="!entries.length || !hasUnfinished"
          @click="startUpload"
        >
          {{ entries.some((entry) => entry.status === 'failed') ? '重试失败项' : '开始上传' }}
        </AppButton>
      </div>
    </template>
  </AppModal>

  <V2KnowledgeBaseModal
    :open="createOpen"
    @close="createOpen = false"
    @saved="handleKnowledgeBaseSaved"
  />
</template>

<style scoped>
.upload-dialog { display: grid; gap: 18px; }
.drop-zone { min-height: 148px; width: 100%; display: grid; place-items: center; align-content: center; gap: 8px; border: 1px dashed var(--color-border); border-radius: 13px; background: var(--color-bg); color: var(--color-text); cursor: pointer; padding: 20px; }
.drop-zone:hover:not(:disabled) { border-color: var(--color-text-muted); background: var(--color-hover); }
.drop-zone span { max-width: 540px; color: var(--color-text-muted); font-size: 12px; line-height: 1.55; text-align: center; }
.knowledge-select { display: grid; gap: 8px; color: var(--color-text); font-size: 14px; font-weight: 650; }
.knowledge-select small { color: var(--color-text-muted); font-size: 12px; font-weight: 500; }
.knowledge-select__row { display: flex; align-items: stretch; gap: 8px; }
.knowledge-select__menu { min-width: 0; flex: 1; }
.knowledge-select__menu :deep(.app-select-menu__trigger) {
  height: 44px;
  justify-content: flex-start;
  gap: 6px;
  padding: 0 14px;
  border-radius: 12px;
  background: var(--color-surface);
  font-weight: 500;
}
.knowledge-select__menu :deep(.app-select-menu__chevron) { color: var(--color-text); }
.knowledge-select__create { min-height: 44px; display: inline-flex; align-items: center; gap: 5px; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-surface); color: var(--color-text); padding: 0 14px; cursor: pointer; font-weight: 600; }
.knowledge-select__create:hover:not(:disabled) { background: var(--color-hover); }
.knowledge-select__create:disabled { opacity: .48; cursor: not-allowed; }
.upload-list { max-height: 310px; overflow: auto; display: grid; gap: 7px; }
.upload-list article { display: flex; align-items: flex-start; gap: 10px; min-height: 58px; box-sizing: border-box; padding: 10px 11px; border: 1px solid var(--color-border); border-radius: 10px; color: var(--color-text); }
.file-status-icon { width: 24px; height: 24px; display: grid; place-items: center; color: var(--color-text-muted); }
.is-done .file-status-icon { color: var(--color-success); }
.is-failed .file-status-icon { color: var(--color-danger); }
.file-copy { min-width: 0; flex: 1; display: grid; gap: 4px; }
.file-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.file-copy span { color: var(--color-text-muted); font-size: 12px; overflow-wrap: anywhere; }
.is-failed .file-copy span { color: var(--color-danger); }
.progress-track { height: 3px; overflow: hidden; border-radius: 99px; background: var(--color-border); }
.progress-track i { display: block; height: 100%; background: var(--color-text); transition: width .15s ease; }
.remove-file { width: 28px; height: 28px; display: grid; place-items: center; border: 0; border-radius: 7px; background: transparent; color: var(--color-text-muted); cursor: pointer; }
.remove-file:hover { background: var(--color-hover); color: var(--color-text); }
.upload-error, .upload-note { margin: 0; padding: 10px 12px; border-radius: 9px; font-size: 12px; line-height: 1.5; }
.upload-error { background: color-mix(in srgb, var(--color-danger) 9%, transparent); color: var(--color-danger); }
.upload-note { background: var(--color-bg); color: var(--color-text-muted); }
.upload-actions { width: 100%; display: flex; justify-content: flex-end; gap: 9px; }
.spin { animation: spin .85s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
