<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import { copyText } from '@/utils/clipboard'
import { renderMarkdownToHtml } from '@/utils/markdown'

const props = withDefaults(defineProps<{
  content: string
  isStreaming?: boolean
}>(), { isStreaming: false })

const container = ref<HTMLElement | null>(null)
const html = computed(() => renderMarkdownToHtml(props.content))

const languageNames: Record<string, string> = {
  js: 'JavaScript', javascript: 'JavaScript',
  ts: 'TypeScript', typescript: 'TypeScript',
  py: 'Python', python: 'Python',
  java: 'Java', json: 'JSON', html: 'HTML', css: 'CSS',
  bash: 'Shell', shell: 'Shell', sql: 'SQL', vue: 'Vue',
}

function enhanceCodeBlocks() {
  container.value?.querySelectorAll('pre').forEach((pre) => {
    if (pre.dataset.enhanced === 'true') return
    const code = pre.querySelector('code')
    if (!code) return
    const languageClass = [...code.classList].find(name => name.startsWith('language-'))
    const language = languageClass?.slice('language-'.length) || 'code'
    const toolbar = document.createElement('div')
    toolbar.className = 'code-toolbar'
    toolbar.innerHTML = `
      <span class="code-language">${languageNames[language.toLowerCase()] || language}</span>
      <span class="code-toolbar-actions">
        <button type="button" data-code-action="copy" title="复制代码" aria-label="复制代码">
          <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
        </button>
      </span>`
    pre.prepend(toolbar)
    pre.dataset.enhanced = 'true'
  })
}

async function onClick(event: MouseEvent) {
  const button = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-code-action]')
  if (!button) return
  const pre = button.closest('pre')
  const code = pre?.querySelector('code')
  if (!pre || !code) return
  await copyText(code.textContent || '')
  button.classList.add('copied')
  button.title = '已复制'
  window.setTimeout(() => {
    button.classList.remove('copied')
    button.title = '复制代码'
  }, 1200)
}

watch(html, async () => {
  await nextTick()
  enhanceCodeBlocks()
}, { immediate: true })
</script>

<template>
  <div
    ref="container"
    class="markdown"
    :class="{ 'is-streaming': isStreaming }"
    @click="onClick"
    v-html="html"
  />
</template>

<style scoped>
.markdown { width: 100%; min-width: 0; color: var(--color-text); font-size: 15px; line-height: 1.75; overflow-wrap: anywhere; }
.markdown :deep(> :first-child) { margin-top: 0; }
.markdown :deep(> :last-child) { margin-bottom: 0; }
.markdown :deep(p), .markdown :deep(ul), .markdown :deep(ol), .markdown :deep(blockquote) { margin: .65em 0; }
.markdown :deep(h1), .markdown :deep(h2), .markdown :deep(h3) { margin: 1.2em 0 .55em; line-height: 1.35; }
.markdown :deep(a) { color: var(--color-primary); text-underline-offset: 3px; }
.markdown :deep(blockquote) { padding-left: 14px; border-left: 3px solid var(--color-border); color: var(--color-text-muted); }
.markdown :deep(code) { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.markdown :deep(:not(pre) > code) { padding: 2px 5px; border-radius: 5px; background: var(--color-surface); font-size: .9em; }
.markdown :deep(pre) { position: relative; max-height: none; margin: 14px 0; padding: 50px 12px 14px; border: 1px solid var(--color-border); border-radius: 14px; background: color-mix(in srgb, var(--color-text) 5%, var(--color-bg)); overflow: auto; }
.markdown :deep(pre code) { display: block; padding: 0; border: 0; background: transparent; font-size: 13px; line-height: 1.65; }
.markdown :deep(.code-toolbar) { position: absolute; inset: 0 0 auto; display: flex; height: 38px; align-items: center; justify-content: space-between; padding: 0 10px 0 12px; border-bottom: 1px solid var(--color-border); color: var(--color-text-muted); background: color-mix(in srgb, var(--color-text) 4%, var(--color-bg)); font-size: 12px; }
.markdown :deep(.code-toolbar-actions) { display: flex; gap: 2px; }
.markdown :deep(.code-toolbar button) { display: grid; width: 28px; height: 28px; padding: 0; place-items: center; border: 0; border-radius: 7px; color: inherit; background: transparent; cursor: pointer; }
.markdown :deep(.code-toolbar button:hover), .markdown :deep(.code-toolbar button.copied) { color: var(--color-text); background: var(--color-surface); }
.markdown :deep(.code-toolbar svg) { width: 15px; height: 15px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.markdown.is-streaming:empty::after { content: ''; display: inline-block; width: 7px; height: 18px; border-radius: 2px; background: currentColor; animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: .25; } }
</style>
