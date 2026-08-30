<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Check, ExternalLink, FileText, Image, LoaderCircle, Network, Pencil, Presentation, Save } from 'lucide-vue-next'

import ChatAttachmentList from '@/components/chat/ChatAttachmentList.vue'
import MarkdownRenderer from '@/components/chat/message/MarkdownRenderer.vue'
import type { Artifact, MindMapNode, PresentationSlide } from '@/types/contracts/chatV2'

const props = defineProps<{ artifact: Artifact; busy?: boolean }>()
const emit = defineEmits<{
  save: [artifact: Artifact, done: (success: boolean) => void]
  confirm: [artifact: Artifact]
  retry: [artifact: Artifact]
  openEditor: [artifact: Artifact]
  openAsset: [assetId: string]
}>()

const editing = ref(false)
const saving = ref(false)
const title = ref('')
const markdown = ref('')

watch(() => props.artifact, (value) => {
  title.value = value.title
  markdown.value = String(value.content.markdown ?? '')
}, { immediate: true, deep: true })

const isDraft = computed(() => ['DRAFT', 'READY'].includes(props.artifact.status))
const isFailed = computed(() => ['FAILED', 'CANCELLED'].includes(props.artifact.status))
const isGenerating = computed(() => ['QUEUED', 'GENERATING', 'RUNNING', 'PROCESSING'].includes(props.artifact.status))
const generationLabel = computed(() => {
  if (props.artifact.id.startsWith('optimistic-artifact-')) {
    return props.artifact.type === 'IMAGE' ? '正在准备图片描述' : 'AI 正在组织内容'
  }
  return props.artifact.type === 'IMAGE' ? 'AI 正在绘制图片，完成后会自动保存' : '正在保存生成结果'
})
const generationError = computed(() => ({
  IMAGE_QUEUE_FULL: '图片生成任务较多，请稍后重试。本次还未调用图片模型。',
  IMAGE_TIMEOUT: '图片模型响应超时，请稍后重试。',
  IMAGE_UNAVAILABLE: '暂时无法连接图片模型服务，请稍后重试。',
  IMAGE_RATE_LIMITED: '图片模型请求过于频繁，请稍后重试。',
  IMAGE_QUOTA_EXHAUSTED: '图片模型额度不足，请检查模型服务账户的额度。',
  IMAGE_AUTHENTICATION: '图片模型的密钥或权限配置异常，请检查服务配置。',
  IMAGE_CONTENT_SAFETY: '图片描述未通过安全检查，请调整描述。',
  IMAGE_GENERATION_INTERRUPTED: '图片生成已中断，请手动重新生成。',
  IMAGE_INTERRUPTED: '图片生成已中断，请手动重新生成。',
}[props.artifact.errorCode || ''] || '生成任务失败，请重新生成。'))
const imageStatusLabel = computed(() => {
  if (props.artifact.status === 'GENERATING') return '图片生成中'
  if (props.artifact.status === 'CANCELLED') return '图片生成已取消'
  if (props.artifact.status === 'FAILED') return '图片生成失败'
  return '图片已生成'
})
const typeLabel = computed(() => ({
  DOCUMENT: '文档草稿',
  MINDMAP: '思维导图草稿',
  PRESENTATION: '演示文稿草稿',
  IMAGE: '生成图片',
}[props.artifact.type]))
const slides = computed(() => (props.artifact.content.slides ?? []) as PresentationSlide[])
const mindMapRoot = computed(() => props.artifact.content.root as MindMapNode | undefined)
const imageItems = computed(() => props.artifact.confirmedAssetId ? [{
  key: props.artifact.confirmedAssetId,
  assetId: props.artifact.confirmedAssetId,
  name: props.artifact.title,
  mimeType: 'image/png',
  sizeBytes: 0,
  status: 'ready' as const,
}] : [])

function saveDocument(): Promise<boolean> {
  if (saving.value || props.busy) return Promise.resolve(false)
  saving.value = true
  return new Promise((resolve) => {
    emit('save', {
      ...props.artifact,
      title: title.value.trim(),
      content: { ...props.artifact.content, markdown: markdown.value },
    }, (success) => {
      saving.value = false
      if (success) editing.value = false
      resolve(success)
    })
  })
}

async function confirmCurrentArtifact() {
  if (saving.value || props.busy) return
  if (props.artifact.type === 'DOCUMENT' && editing.value && !(await saveDocument())) return
  emit('confirm', props.artifact)
}
</script>

<template>
  <article class="artifact-card" :class="`artifact-${artifact.type.toLowerCase()}`" :data-artifact-id="artifact.id">
    <header>
      <span class="artifact-icon">
        <FileText v-if="artifact.type === 'DOCUMENT'" :size="18" />
        <Network v-else-if="artifact.type === 'MINDMAP'" :size="18" />
        <Presentation v-else-if="artifact.type === 'PRESENTATION'" :size="18" />
        <Image v-else :size="18" />
      </span>
      <div>
        <small>{{ typeLabel }}</small>
        <strong>{{ artifact.title }}</strong>
      </div>
      <button
        v-if="artifact.type === 'DOCUMENT' && isDraft && !editing"
        class="header-icon-button"
        type="button"
        :disabled="busy"
        aria-label="放大编辑文档"
        title="放大编辑"
        @click="emit('openEditor', artifact)"
      ><ExternalLink :size="16" /></button>
      <span v-if="artifact.status === 'CONFIRMED'" class="confirmed"><Check :size="14" /> 已确认</span>
    </header>

    <div v-if="isGenerating" class="artifact-skeleton" aria-live="polite">
      <div class="skeleton-line skeleton-line--wide" />
      <div class="skeleton-line" />
      <div class="skeleton-line skeleton-line--short" />
      <div class="skeleton-block" :class="`skeleton-block--${artifact.type.toLowerCase()}`">
        <LoaderCircle class="spin" :size="18" />
      </div>
      <span>{{ generationLabel }}</span>
    </div>

    <template v-else-if="artifact.type === 'DOCUMENT'">
      <div v-if="editing" class="document-editor">
        <input v-model="title" maxlength="120" aria-label="文档标题" />
        <textarea v-model="markdown" aria-label="文档内容" />
      </div>
      <div v-else class="document-preview">
        <MarkdownRenderer :content="String(artifact.content.markdown ?? '')" />
      </div>
    </template>

    <div v-else-if="artifact.type === 'MINDMAP'" class="artifact-summary">
      <strong>{{ mindMapRoot?.text || artifact.title }}</strong>
      <span>{{ mindMapRoot?.children?.length || 0 }} 个一级节点</span>
    </div>

    <div v-else-if="artifact.type === 'PRESENTATION'" class="artifact-summary">
      <strong>{{ slides.length }} 页演示文稿</strong>
      <span>{{ slides.slice(0, 3).map(slide => slide.title).join(' · ') }}</span>
    </div>

    <div v-else class="artifact-summary">
      <strong>{{ imageStatusLabel }}</strong>
      <span>{{ isFailed ? generationError : artifact.content.prompt }}</span>
    </div>

    <ChatAttachmentList
      v-if="artifact.type === 'IMAGE' && imageItems.length"
      class="artifact-image-preview"
      :items="imageItems"
      compact
      @open="emit('openAsset', $event)"
    />

    <footer>
      <button
        v-if="artifact.type === 'DOCUMENT' && isDraft && !editing"
        type="button"
        :disabled="busy"
        @click="editing = true"
      ><Pencil :size="15" />编辑</button>
      <button
        v-if="artifact.type === 'DOCUMENT' && editing"
        type="button"
        :disabled="busy || saving || !title.trim() || !markdown.trim()"
        @click="saveDocument"
      ><Save :size="15" />{{ saving ? '保存中…' : '保存草稿' }}</button>
      <button
        v-if="['MINDMAP', 'PRESENTATION'].includes(artifact.type) && isDraft"
        type="button"
        @click="emit('openEditor', artifact)"
      ><ExternalLink :size="15" />打开编辑</button>
      <button
        v-if="isDraft && artifact.type !== 'IMAGE'"
        class="primary"
        type="button"
        :disabled="busy || saving"
        @click="confirmCurrentArtifact"
      >确认并存入资料库</button>
      <button
        v-if="artifact.confirmedAssetId"
        type="button"
        @click="emit('openAsset', artifact.confirmedAssetId)"
      ><ExternalLink :size="15" />查看文件</button>
      <button
        v-if="artifact.type === 'IMAGE' && isFailed"
        type="button"
        @click="emit('retry', artifact)"
      >重新生成</button>
    </footer>
  </article>
</template>

<style scoped>
.artifact-card { width: min(680px, 100%); overflow: hidden; border: 1px solid color-mix(in srgb, var(--color-text) 16%, var(--color-border)); border-radius: 18px; background: var(--color-bg); box-shadow: 0 5px 18px rgb(15 23 42 / 6%); }
.artifact-document { width: 100%; }
.artifact-card header { display: flex; align-items: center; gap: 11px; padding: 15px 16px; border-bottom: 1px solid var(--color-border); }
.artifact-card header > div { display: grid; min-width: 0; flex: 1; }
.artifact-card header small { color: var(--color-text-muted); font-size: 12px; }
.artifact-card header strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.artifact-card header .header-icon-button { display: grid; width: 30px; min-height: 30px; padding: 0; place-items: center; border: 0; border-radius: 8px; color: var(--color-text-muted); background: transparent; }
.artifact-card header .header-icon-button:hover:not(:disabled) { color: var(--color-text); background: var(--color-surface); }
.artifact-icon { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 11px; background: var(--color-surface); }
.confirmed { display: inline-flex; align-items: center; gap: 4px; color: var(--color-text-muted); font-size: 12px; }
.document-preview {
  max-height: 360px;
  overflow: auto;
  padding: 16px;
  background: var(--color-hover);
}
.document-preview :deep(.markdown) {
  width: 100%;
  min-height: 250px;
  padding: 24px 28px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}
.document-editor { display: grid; gap: 10px; padding: 14px; }
.document-editor input, .document-editor textarea { width: 100%; border: 1px solid var(--color-border); border-radius: 11px; color: inherit; background: var(--color-bg); font: inherit; }
.document-editor input { height: 40px; padding: 0 11px; }
.document-editor textarea { min-height: 280px; resize: vertical; padding: 12px; line-height: 1.7; }
.artifact-summary { display: grid; gap: 6px; min-height: 106px; padding: 20px; }
.artifact-summary span { overflow: hidden; color: var(--color-text-muted); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.artifact-image-preview { margin: 0 18px 14px; }
.artifact-card footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 14px; border-top: 1px solid var(--color-border); }
.artifact-card button { display: inline-flex; align-items: center; gap: 6px; min-height: 36px; padding: 0 13px; border: 1px solid var(--color-border); border-radius: 999px; color: inherit; background: var(--color-bg); cursor: pointer; }
.artifact-card button.primary { border-color: var(--color-text); color: var(--color-bg); background: var(--color-text); }
.artifact-card button:disabled { cursor: not-allowed; opacity: .46; }
.artifact-skeleton { display: grid; gap: 10px; padding: 20px; color: var(--color-text-muted); font-size: 12px; }
.skeleton-line, .skeleton-block { position: relative; overflow: hidden; border-radius: 7px; background: var(--color-hover-strong); }
.skeleton-line { width: 76%; height: 11px; }
.skeleton-line--wide { width: 92%; height: 15px; }
.skeleton-line--short { width: 54%; }
.skeleton-line::after, .skeleton-block::after { position: absolute; inset: 0; background: linear-gradient(100deg, transparent 15%, rgb(255 255 255 / 28%) 48%, transparent 82%); content: ''; animation: artifact-shimmer 1.45s linear infinite; }
.skeleton-block { display: grid; min-height: 114px; place-items: center; color: var(--color-text-muted); }
.skeleton-block .spin { position: relative; z-index: 1; transform-origin: center; animation: artifact-spin .9s linear infinite; }
.skeleton-block--document { min-height: 190px; }
.skeleton-block--presentation { min-height: 150px; aspect-ratio: 16 / 9; }
.skeleton-block--mindmap { min-height: 150px; }
.skeleton-block--image { min-height: 170px; }
.artifact-skeleton > span { margin-top: 2px; }
@keyframes artifact-shimmer { to { transform: translateX(100%); } }
@keyframes artifact-spin { to { transform: rotate(360deg); } }
</style>
