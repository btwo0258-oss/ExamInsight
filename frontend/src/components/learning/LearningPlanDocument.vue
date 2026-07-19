<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { renderMarkdownToHtml } from '@/utils/markdown'
import { downloadBlob } from '@/utils/download'

const props = defineProps<{ content: string; loading?: boolean }>()
const emit = defineEmits<{ update: [content: string] }>()

const editing = ref(false)
const fullscreen = ref(false)
const draft = ref(props.content)
const html = computed(() => renderMarkdownToHtml(props.content))

watch(() => props.content, (value) => { if (!editing.value) draft.value = value })

function toggleEdit() {
  if (editing.value) emit('update', draft.value)
  else draft.value = props.content
  editing.value = !editing.value
}

async function copyDocument() { await navigator.clipboard?.writeText(props.content) }
function downloadDocument() {
  downloadBlob(
    new Blob([props.content], { type: 'text/markdown;charset=utf-8' }),
    '个性化学习方案确认稿.md',
  )
}
</script>

<template>
  <section class="plan-document" :class="{ 'plan-document--fullscreen': fullscreen }">
    <header class="plan-document__toolbar">
      <button class="edit-button" type="button" :disabled="loading" @click="toggleEdit">
        <AppIcon :name="editing ? 'eye' : 'edit'" :size="16" />
        {{ editing ? '完成编辑' : '编辑' }}
      </button>
      <div>
        <button type="button" title="复制" :disabled="loading" @click="copyDocument"><AppIcon name="copy" :size="17" /></button>
        <button type="button" title="下载 Markdown" :disabled="loading" @click="downloadDocument"><AppIcon name="download" :size="17" /></button>
        <button type="button" :title="fullscreen ? '退出全屏' : '全屏'" @click="fullscreen = !fullscreen"><AppIcon :name="fullscreen ? 'minimize' : 'maximize'" :size="17" /></button>
      </div>
    </header>

    <div v-if="loading" class="document-skeleton">
      <span v-for="item in 12" :key="item" :style="{ width: `${92 - (item % 4) * 13}%` }" />
    </div>
    <textarea v-else-if="editing" v-model="draft" class="document-editor" spellcheck="false" />
    <article v-else class="document-preview" v-html="html" />
  </section>
</template>

<style scoped>
.plan-document { --document-bg: color-mix(in srgb, var(--color-surface) 92%, var(--color-text) 8%); position: relative; min-height: 420px; border: 1px solid color-mix(in srgb, var(--color-border) 76%, transparent); border-radius: 20px; background: var(--document-bg); box-shadow: var(--shadow-sm); }
.plan-document__toolbar { position: sticky; top: 0; z-index: 8; min-height: 54px; margin: 0; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between; border-radius: 19px 19px 0 0; background: var(--document-bg); box-shadow: 0 1px 0 color-mix(in srgb, var(--color-border) 76%, transparent); }
.plan-document__toolbar > div { display: flex; gap: 3px; }
.plan-document__toolbar button { min-width: 36px; height: 36px; padding: 0 10px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; border: 1px solid transparent; border-radius: 999px; background: transparent; color: var(--color-text-muted); cursor: pointer; }
.plan-document__toolbar button:hover { background: var(--color-hover); color: var(--color-text); }
.plan-document__toolbar .edit-button { border-color: var(--color-border); color: var(--color-text); font-weight: 650; }
.document-preview { max-width: 720px; margin: 0 auto; padding: 34px 44px 58px; color: var(--color-text); font-size: 15px; line-height: 1.85; }
.document-preview :deep(h1) { margin: 0 0 26px; font-size: 28px; line-height: 1.35; }
.document-preview :deep(h2) { margin-top: 36px; padding-top: 24px; border-top: 1px solid color-mix(in srgb, var(--color-border) 76%, transparent); font-size: 21px; }
.document-preview :deep(h3) { margin-top: 24px; font-size: 17px; }
.document-preview :deep(li) { margin: 5px 0; }
.document-editor { display: block; width: calc(100% - 48px); min-height: 620px; margin: 12px 24px 32px; padding: 28px; border: 1px solid var(--color-border); border-radius: 12px; outline: 0; resize: vertical; box-sizing: border-box; background: color-mix(in srgb, var(--document-bg) 86%, var(--color-bg)); color: var(--color-text); font: 14px/1.75 ui-monospace, SFMono-Regular, Consolas, monospace; }
.document-skeleton { padding: 34px 28px 50px; display: grid; gap: 15px; }
.document-skeleton span { height: 15px; border-radius: 6px; background: linear-gradient(90deg, var(--color-surface-subtle), var(--color-hover-strong), var(--color-surface-subtle)); background-size: 220% 100%; animation: shimmer 1.25s infinite linear; }
.plan-document--fullscreen { position: fixed; inset: 0; z-index: 10020; overflow: auto; border: 0; border-radius: 0; }
@media (max-width: 680px) { .document-preview { padding: 26px 20px 44px; } }
@keyframes shimmer { to { background-position: -220% 0; } }
</style>
