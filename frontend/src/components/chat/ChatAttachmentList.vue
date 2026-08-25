<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, watch } from 'vue'
import { AlertCircle, File, LoaderCircle, X } from 'lucide-vue-next'

import { fetchAssetContent } from '@/api/assetLibraryV2'

type AttachmentItem = {
  key: string
  assetId?: string
  name: string
  mimeType: string
  sizeBytes: number
  status?: 'uploading' | 'processing' | 'ready' | 'failed'
  progress?: number
  error?: string
  previewUrl?: string
}

const props = withDefaults(defineProps<{
  items: AttachmentItem[]
  removable?: boolean
  compact?: boolean
}>(), {
  removable: false,
  compact: false,
})

const emit = defineEmits<{
  remove: [key: string]
  open: [assetId: string]
}>()

const fetchedUrls = reactive<Record<string, string>>({})
const loadingImages = new Set<string>()

const imageItems = computed(() => props.items.filter(item => item.mimeType.startsWith('image/')))
const fileItems = computed(() => props.items.filter(item => !item.mimeType.startsWith('image/')))

function formatSize(value: number) {
  if (!Number.isFinite(value) || value <= 0) return ''
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`
  return `${(value / 1024 / 1024).toFixed(value < 10 * 1024 * 1024 ? 1 : 0)} MB`
}

function previewFor(item: AttachmentItem) {
  return item.previewUrl || fetchedUrls[item.key] || ''
}

async function ensureImagePreviews() {
  for (const item of imageItems.value) {
    if (item.previewUrl || fetchedUrls[item.key] || !item.assetId || loadingImages.has(item.key)) continue
    loadingImages.add(item.key)
    try {
      const blob = await fetchAssetContent(item.assetId, 'inline')
      fetchedUrls[item.key] = URL.createObjectURL(blob)
    } catch {
      // The file card remains usable even if a thumbnail cannot be loaded.
    } finally {
      loadingImages.delete(item.key)
    }
  }
}

function handleOpen(item: AttachmentItem) {
  if (item.status && item.status !== 'ready') return
  if (item.assetId) emit('open', item.assetId)
}

watch(() => props.items.map(item => `${item.key}:${item.assetId ?? ''}:${item.status ?? ''}`).join('|'), () => {
  void ensureImagePreviews()
}, { immediate: true })

onBeforeUnmount(() => {
  Object.values(fetchedUrls).forEach(url => URL.revokeObjectURL(url))
})
</script>

<template>
  <div class="attachment-list" :class="{ compact }">
    <div v-if="imageItems.length" class="image-grid" :class="`count-${Math.min(imageItems.length, 4)}`">
      <article
        v-for="item in imageItems"
        :key="item.key"
        class="image-card"
        :class="{ interactive: item.assetId && (!item.status || item.status === 'ready') }"
        :title="item.name"
        :tabindex="item.assetId && (!item.status || item.status === 'ready') ? 0 : undefined"
        @click="handleOpen(item)"
        @keydown.enter="handleOpen(item)"
      >
        <img v-if="previewFor(item)" :src="previewFor(item)" :alt="item.name" />
        <div v-else class="image-fallback"><File :size="25" /></div>
        <span v-if="item.status === 'uploading' || item.status === 'processing'" class="status-cover">
          <LoaderCircle class="spin" :size="20" />
          <small>{{ item.status === 'uploading' ? `${item.progress ?? 0}%` : '处理中' }}</small>
        </span>
        <span v-else-if="item.status === 'failed'" class="status-cover failed">
          <AlertCircle :size="20" />
          <small>失败</small>
        </span>
        <button
          v-if="removable"
          class="remove-button"
          type="button"
          :aria-label="`移除 ${item.name}`"
          @click.stop="emit('remove', item.key)"
        ><X :size="14" /></button>
      </article>
    </div>

    <div v-if="fileItems.length" class="file-grid">
      <article
        v-for="item in fileItems"
        :key="item.key"
        class="file-card"
        :class="{ interactive: item.assetId && (!item.status || item.status === 'ready') }"
        :title="item.error || item.name"
        :tabindex="item.assetId && (!item.status || item.status === 'ready') ? 0 : undefined"
        @click="handleOpen(item)"
        @keydown.enter="handleOpen(item)"
      >
        <span class="file-icon">
          <LoaderCircle
            v-if="item.status === 'uploading' || item.status === 'processing'"
            class="spin"
            :size="21"
          />
          <AlertCircle v-else-if="item.status === 'failed'" :size="21" />
          <File v-else :size="21" />
        </span>
        <span class="file-copy">
          <strong>{{ item.name }}</strong>
          <small v-if="item.status === 'uploading'">正在上传 · {{ item.progress ?? 0 }}%</small>
          <small v-else-if="item.status === 'processing'">正在处理</small>
          <small v-else-if="item.status === 'failed'">{{ item.error || '处理失败' }}</small>
          <small v-else>{{ formatSize(item.sizeBytes) }}</small>
        </span>
        <button
          v-if="removable"
          class="remove-button"
          type="button"
          :aria-label="`移除 ${item.name}`"
          @click.stop="emit('remove', item.key)"
        ><X :size="14" /></button>
      </article>
    </div>
  </div>
</template>

<style scoped>
.attachment-list { display: grid; gap: 8px; width: min(100%, 620px); }
.image-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 180px)); gap: 6px; }
.image-grid.count-1 { grid-template-columns: minmax(120px, 260px); }
.image-card {
  position: relative; min-width: 0; aspect-ratio: 4 / 3; overflow: hidden; padding: 0;
  border: 1px solid var(--color-border); border-radius: 14px; background: var(--color-surface);
}
.image-card img { display: block; width: 100%; height: 100%; object-fit: cover; }
.image-fallback { display: grid; width: 100%; height: 100%; place-items: center; color: var(--color-text-muted); }
.status-cover {
  position: absolute; inset: 0; display: grid; align-content: center; justify-items: center; gap: 4px;
  color: white; background: rgb(0 0 0 / 48%);
}
.status-cover.failed { color: #fff; background: rgb(145 34 28 / 72%); }
.file-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.file-card {
  position: relative; display: flex; width: min(292px, 100%); min-height: 64px; align-items: center; gap: 10px;
  padding: 9px 36px 9px 10px; border: 1px solid var(--color-border); border-radius: 14px;
  color: inherit; background: var(--color-bg); text-align: left;
}
.file-card.interactive, .image-card.interactive { cursor: pointer; }
.file-card.interactive:hover, .image-card.interactive:hover { border-color: var(--color-text-muted); }
.file-icon {
  display: grid; width: 42px; height: 42px; flex: 0 0 42px; place-items: center;
  border-radius: 11px; color: var(--color-text); background: var(--color-surface);
}
.file-copy { display: grid; min-width: 0; gap: 3px; }
.file-copy strong { overflow: hidden; font-size: 13px; font-weight: 550; text-overflow: ellipsis; white-space: nowrap; }
.file-copy small { overflow: hidden; color: var(--color-text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.remove-button {
  position: absolute; top: 6px; right: 6px; display: grid; width: 24px; height: 24px; padding: 0;
  place-items: center; border: 0; border-radius: 50%; color: white; background: rgb(25 25 25 / 80%); cursor: pointer;
}
.compact .image-grid { grid-template-columns: repeat(2, minmax(0, 132px)); }
.compact .image-grid.count-1 { grid-template-columns: minmax(110px, 200px); }
.compact .file-card { width: min(250px, 100%); min-height: 58px; }
.spin { animation: spin .9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 560px) {
  .attachment-list { width: 100%; }
  .image-grid, .compact .image-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .file-card { width: 100%; }
}
</style>
