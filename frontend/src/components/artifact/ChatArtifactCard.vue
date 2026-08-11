<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Check, ExternalLink, FileText, Image, Network, Pencil, Presentation, Save } from 'lucide-vue-next'

import type { Artifact, MindMapNode, PresentationSlide } from '@/types/contracts/chatV2'

const props = defineProps<{ artifact: Artifact; busy?: boolean }>()
const emit = defineEmits<{
  save: [artifact: Artifact]
  confirm: [artifact: Artifact]
  openEditor: [artifact: Artifact]
  openAsset: [assetId: string]
}>()

const editing = ref(false)
const title = ref('')
const markdown = ref('')

watch(() => props.artifact, (value) => {
  title.value = value.title
  markdown.value = String(value.content.markdown ?? '')
}, { immediate: true, deep: true })

const isDraft = computed(() => ['DRAFT', 'READY'].includes(props.artifact.status))
const typeLabel = computed(() => ({
  DOCUMENT: '文档草稿',
  MINDMAP: '思维导图草稿',
  PRESENTATION: '演示文稿草稿',
  IMAGE: '生成图片',
}[props.artifact.type]))
const slides = computed(() => (props.artifact.content.slides ?? []) as PresentationSlide[])
const mindMapRoot = computed(() => props.artifact.content.root as MindMapNode | undefined)

function saveDocument() {
  emit('save', {
    ...props.artifact,
    title: title.value.trim(),
    content: { ...props.artifact.content, markdown: markdown.value },
  })
  editing.value = false
}
</script>

<template>
  <article class="artifact-card" :class="`artifact-${artifact.type.toLowerCase()}`">
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
      <span v-if="artifact.status === 'CONFIRMED'" class="confirmed"><Check :size="14" /> 已确认</span>
    </header>

    <template v-if="artifact.type === 'DOCUMENT'">
      <div v-if="editing" class="document-editor">
        <input v-model="title" maxlength="120" aria-label="文档标题" />
        <textarea v-model="markdown" aria-label="文档内容" />
      </div>
      <pre v-else class="document-preview">{{ artifact.content.markdown }}</pre>
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
      <strong>{{ artifact.status === 'GENERATING' ? '图片生成中' : '图片已生成' }}</strong>
      <span>{{ artifact.content.prompt }}</span>
    </div>

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
        :disabled="busy || !title.trim() || !markdown.trim()"
        @click="saveDocument"
      ><Save :size="15" />保存草稿</button>
      <button
        v-if="['MINDMAP', 'PRESENTATION'].includes(artifact.type) && isDraft"
        type="button"
        @click="emit('openEditor', artifact)"
      ><ExternalLink :size="15" />打开编辑</button>
      <button
        v-if="isDraft && artifact.type !== 'IMAGE'"
        class="primary"
        type="button"
        :disabled="busy"
        @click="emit('confirm', artifact)"
      >确认并存入资料库</button>
      <button
        v-if="artifact.confirmedAssetId"
        type="button"
        @click="emit('openAsset', artifact.confirmedAssetId)"
      ><ExternalLink :size="15" />查看文件</button>
    </footer>
  </article>
</template>

<style scoped>
.artifact-card { width: min(680px, 100%); overflow: hidden; border: 1px solid var(--color-border); border-radius: 18px; background: var(--color-bg); }
.artifact-card header { display: flex; align-items: center; gap: 11px; padding: 15px 16px; border-bottom: 1px solid var(--color-border); }
.artifact-card header > div { display: grid; min-width: 0; flex: 1; }
.artifact-card header small { color: var(--color-text-muted); font-size: 12px; }
.artifact-card header strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.artifact-icon { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 11px; background: var(--color-surface); }
.confirmed { display: inline-flex; align-items: center; gap: 4px; color: var(--color-text-muted); font-size: 12px; }
.document-preview { max-height: 300px; overflow: auto; margin: 0; padding: 18px; color: inherit; background: transparent; font: 14px/1.75 inherit; white-space: pre-wrap; }
.document-editor { display: grid; gap: 10px; padding: 14px; }
.document-editor input, .document-editor textarea { width: 100%; border: 1px solid var(--color-border); border-radius: 11px; color: inherit; background: var(--color-bg); font: inherit; }
.document-editor input { height: 40px; padding: 0 11px; }
.document-editor textarea { min-height: 280px; resize: vertical; padding: 12px; line-height: 1.7; }
.artifact-summary { display: grid; gap: 6px; min-height: 106px; padding: 20px; }
.artifact-summary span { overflow: hidden; color: var(--color-text-muted); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.artifact-card footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 14px; border-top: 1px solid var(--color-border); }
.artifact-card button { display: inline-flex; align-items: center; gap: 6px; min-height: 36px; padding: 0 13px; border: 1px solid var(--color-border); border-radius: 999px; color: inherit; background: var(--color-bg); cursor: pointer; }
.artifact-card button.primary { border-color: var(--color-text); color: var(--color-bg); background: var(--color-text); }
.artifact-card button:disabled { cursor: not-allowed; opacity: .46; }
</style>
