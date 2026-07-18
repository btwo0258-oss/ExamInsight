<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Check, Download, ExternalLink, LoaderCircle, Pencil, RefreshCw, XCircle } from 'lucide-vue-next'
import ResourceTypeIcon from '@/components/common/ResourceTypeIcon.vue'
import MindMapStaticPreview from './MindMapStaticPreview.vue'
import type { ChatArtifactDto } from '@/types/contracts/artifact'
import type { ArtifactInlinePreview } from '@/types/contracts/artifact'
import { resourceVisualTypeFromFile } from '@/utils/resourceVisual'
import { previewLibraryResource } from '@/api/libraryResource'
import { subscribeResourcePreviewUpdates } from '@/utils/resourcePreviewSync'

const props = withDefaults(defineProps<{
  artifact: ChatArtifactDto
  busy?: boolean
}>(), { busy: false })

const emit = defineEmits<{
  open: []
  edit: []
  download: []
  retry: []
}>()

const livePreview = ref<ArtifactInlinePreview | null>(null)
const preview = computed(() => livePreview.value ?? props.artifact.preview)

async function refreshMindMapPreview() {
  if (props.artifact.fileType !== 'mindmap' || props.artifact.status !== 'ready' || !props.artifact.resourceId) return
  try {
    const latest = await previewLibraryResource(props.artifact.resourceId)
    if (latest.status === 'ready' && latest.previewData?.mindMap) livePreview.value = latest.previewData
  } catch {
    // The embedded artifact preview remains the offline/degraded fallback.
  }
}

const unsubscribePreviewUpdates = subscribeResourcePreviewUpdates((update) => {
  if (update.resourceId !== props.artifact.resourceId) return
  if (update.preview?.mindMap) livePreview.value = update.preview
  else void refreshMindMapPreview()
})

onMounted(() => void refreshMindMapPreview())
onBeforeUnmount(unsubscribePreviewUpdates)

const visualType = computed(() => resourceVisualTypeFromFile(
  props.artifact.fileName,
  props.artifact.mimeType,
  props.artifact.fileType,
))
const isPending = computed(() => props.artifact.status === 'queued' || props.artifact.status === 'generating')
const isReady = computed(() => props.artifact.status === 'ready')
const isFailed = computed(() => props.artifact.status === 'failed' || props.artifact.status === 'cancelled')
const statusText = computed(() => {
  if (props.artifact.status === 'ready') return '已生成'
  if (props.artifact.status === 'failed') return '生成失败'
  if (props.artifact.status === 'cancelled') return '已取消'
  if (props.artifact.status === 'queued') return '等待生成'
  return '正在生成'
})
const sizeText = computed(() => {
  const size = props.artifact.sizeBytes ?? 0
  if (!size) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
})
const previewFallback = computed(() => ({
  spreadsheet: '电子表格已生成，可预览工作表内容或下载文件。',
  presentation: '演示文稿已生成，可预览幻灯片或继续编辑。',
  document: '文档已生成，可打开统一预览查看完整内容。',
  mindmap: '思维导图已生成，可打开预览或进入详情页编辑。',
  image: '图片已生成，可打开查看原图。',
  none: '',
}[preview.value.kind]))
</script>

<template>
  <article class="artifact-card" :class="`artifact-card--${artifact.status}`">
    <header class="artifact-card__header">
      <ResourceTypeIcon :type="visualType" :size="19" :container-size="38" />
      <div class="artifact-card__identity">
        <strong>{{ artifact.fileName }}</strong>
        <span>{{ artifact.format }}<template v-if="sizeText"> · {{ sizeText }}</template></span>
      </div>
      <span class="artifact-card__status">
        <LoaderCircle v-if="isPending" class="spin" :size="15" />
        <Check v-else-if="isReady" :size="15" />
        <XCircle v-else :size="15" />
        {{ statusText }}
      </span>
    </header>

    <div v-if="isPending" class="artifact-card__progress">
      <div><span :style="{ width: `${Math.max(4, artifact.progress ?? 8)}%` }" /></div>
      <p>正在整理内容并生成 {{ artifact.format }} 文件<template v-if="artifact.progress"> · {{ artifact.progress }}%</template></p>
    </div>

    <div v-else-if="isReady && preview.kind !== 'none'" class="artifact-card__preview">
      <img v-if="preview.kind === 'image' && preview.imageUrl" :src="preview.imageUrl" :alt="artifact.title" />
      <MindMapStaticPreview v-else-if="preview.kind === 'mindmap' && preview.mindMap" :tree="preview.mindMap" :render-config="preview.mindMapConfig" compact />
      <div v-else-if="preview.kind === 'spreadsheet' && preview.table" class="artifact-table">
        <table>
          <thead><tr><th v-for="column in preview.table.columns" :key="column">{{ column }}</th></tr></thead>
          <tbody><tr v-for="(row, rowIndex) in preview.table.rows.slice(0, 5)" :key="rowIndex"><td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td></tr></tbody>
        </table>
      </div>
      <div v-else-if="preview.kind === 'presentation' && preview.slides?.length" class="artifact-slides">
        <div v-for="(slide, index) in preview.slides.slice(0, 3)" :key="`${slide.title}-${index}`"><small>{{ index + 1 }}</small><strong>{{ slide.title }}</strong></div>
      </div>
      <p v-else class="artifact-document">{{ preview.text || previewFallback }}</p>
    </div>

    <p v-if="isFailed" class="artifact-card__error">{{ artifact.errorMessage || '文件生成失败，请重试。' }}</p>

    <footer v-if="!isPending" class="artifact-card__actions">
      <button v-if="isFailed" class="primary" type="button" :disabled="busy" @click="emit('retry')"><RefreshCw :size="15" />重试</button>
      <template v-else>
        <button v-if="artifact.editable" type="button" @click="emit('edit')"><Pencil :size="15" />编辑</button>
        <button type="button" :disabled="busy || !artifact.resourceId" @click="emit('download')"><Download :size="15" />下载</button>
        <button class="primary" type="button" :disabled="!artifact.resourceId" @click="emit('open')"><ExternalLink :size="15" />预览</button>
      </template>
    </footer>
  </article>
</template>

<style scoped>
.artifact-card { width: min(640px, 100%); margin: 12px 0 22px; overflow: hidden; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-surface); box-shadow: var(--shadow-sm); }
.artifact-card__header { min-height: 66px; padding: 12px 14px; display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; align-items: center; gap: 11px; }
.artifact-card__identity { min-width: 0; }
.artifact-card__identity strong, .artifact-card__identity span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.artifact-card__identity strong { color: var(--color-text); font-size: 14px; }
.artifact-card__identity span { margin-top: 3px; color: var(--color-text-muted); font-size: 11px; }
.artifact-card__status { display: inline-flex; align-items: center; gap: 5px; color: var(--color-text-muted); font-size: 11px; font-weight: 700; }
.artifact-card--ready .artifact-card__status { color: var(--color-success); }
.artifact-card--failed .artifact-card__status, .artifact-card--cancelled .artifact-card__status { color: var(--color-danger); }
.artifact-card__progress { padding: 0 14px 14px; }
.artifact-card__progress > div { height: 5px; overflow: hidden; border-radius: 999px; background: var(--color-hover); }
.artifact-card__progress > div span { display: block; height: 100%; border-radius: inherit; background: var(--color-primary); transition: width .3s ease; }
.artifact-card__progress p { margin: 8px 0 0; color: var(--color-text-muted); font-size: 11px; }
.artifact-card__preview { max-height: 250px; overflow: hidden; border-top: 1px solid var(--color-border); background: var(--color-surface-subtle); }
.artifact-card__preview > img { display: block; width: 100%; max-height: 250px; object-fit: cover; }
.artifact-document { margin: 0; padding: 18px; color: var(--color-text-muted); font-size: 13px; line-height: 1.7; white-space: pre-wrap; }
.artifact-table { overflow: hidden; padding: 10px; }
.artifact-table table { width: 100%; border-collapse: collapse; background: var(--color-surface); font-size: 11px; }
.artifact-table th, .artifact-table td { max-width: 150px; padding: 7px 8px; overflow: hidden; border: 1px solid var(--color-border); text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.artifact-table th { background: var(--color-hover); }
.artifact-slides { padding: 14px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.artifact-slides > div { aspect-ratio: 16 / 9; padding: 10px; display: flex; flex-direction: column; justify-content: space-between; border: 1px solid var(--color-border); border-radius: 7px; background: linear-gradient(145deg, var(--color-surface), color-mix(in srgb, #d4552d 8%, var(--color-surface))); }
.artifact-slides small { color: var(--color-text-muted); }
.artifact-slides strong { font-size: 11px; line-height: 1.35; }
.artifact-card__error { margin: 0; padding: 4px 14px 14px; color: var(--color-danger); font-size: 12px; }
.artifact-card__actions { padding: 11px 14px; display: flex; justify-content: flex-end; gap: 8px; border-top: 1px solid var(--color-border); }
.artifact-card__actions button { min-height: 34px; padding: 0 12px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; border: 1px solid var(--color-border); border-radius: 7px; background: var(--color-surface); color: var(--color-text); cursor: pointer; font: inherit; font-size: 12px; font-weight: 700; }
.artifact-card__actions button:hover:not(:disabled) { background: var(--color-hover); }
.artifact-card__actions button.primary { border-color: var(--color-text); background: var(--color-text); color: var(--color-surface); }
.artifact-card__actions button:disabled { cursor: not-allowed; opacity: .5; }
.spin { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 560px) { .artifact-card__header { grid-template-columns: 38px minmax(0, 1fr); } .artifact-card__status { grid-column: 2; } .artifact-slides { grid-template-columns: repeat(2, 1fr); } .artifact-card__actions { flex-wrap: wrap; } .artifact-card__actions button { flex: 1; } }
</style>
