<script setup lang="ts">
import { onMounted, watch, ref, nextTick } from 'vue'
import { renderMarkdownToHtml } from '@/utils/markdown'
import { copyText } from '@/utils/clipboard'

type Props = { content: string; isStreaming?: boolean }
const props = withDefaults(defineProps<Props>(), { isStreaming: false })

const containerRef = ref<HTMLElement | null>(null)
const htmlContent = ref('')

function renderMarkdown() {
  htmlContent.value = renderMarkdownToHtml(props.content)
}

function addCopyButtons() {
  if (containerRef.value) {
    const codeBlocks = containerRef.value.querySelectorAll('pre code')
    codeBlocks.forEach((code) => {
      const preElement = code.parentElement
      if (preElement && !preElement.querySelector('.copy-btn')) {
        let language = code.className.replace('hljs', '').trim() || 'code'
        language = language.replace(/^language-/, '')

        const languageSpan = document.createElement('span')
        languageSpan.className = 'language'
        languageSpan.textContent = language

        const copyBtn = document.createElement('button')
        copyBtn.className = 'copy-btn'
        copyBtn.innerHTML = '<svg class="icon" width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="9" y="9" width="13" height="13" rx="2" ry="2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>'
        copyBtn.title = '复制代码'

        copyBtn.addEventListener('click', (event) => {
          event.stopPropagation()
          const codeText = code.textContent || ''
          copyText(codeText)
        })

        preElement.insertBefore(languageSpan, code)
        preElement.insertBefore(copyBtn, code)
      }
    })
  }
}

function doRender() {
  renderMarkdown()
  nextTick(() => {
    addCopyButtons()
  })
}

onMounted(() => {
  doRender()
})

watch(() => props.content, () => {
  doRender()
}, { immediate: true })
</script>

<template>
  <div ref="containerRef" class="markdown" v-html="htmlContent"></div>
</template>

<style scoped>
.markdown pre {
  position: relative;
  margin: 10px 0;
  padding: 30px 12px 12px;
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  overflow: auto;
}

.markdown code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono',
    'Courier New', monospace;
}

/* 确保代码块的样式正确 */
.markdown pre code {
  display: block;
  padding: 0;
  background: none;
  border: none;
  color: inherit;
}
</style>

<style>
.markdown .language {
  position: absolute;
  top: 8px;
  left: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono',
    'Courier New', monospace;
  font-size: 12px;
  color: var(--color-text-muted);
  z-index: 10;
}

.markdown .copy-btn {
  position: absolute !important;
  top: 8px !important;
  right: 12px !important;
  background: transparent !important;
  background-color: transparent !important;
  border: none !important;
  cursor: pointer;
  padding: 4px !important;
  margin: 0 !important;
  color: var(--color-text-muted);
  display: flex !important;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  z-index: 10;
  outline: none !important;
  box-shadow: none !important;
  -webkit-appearance: none !important;
  -moz-appearance: none !important;
  appearance: none !important;
  border-radius: 0 !important;
  width: auto !important;
  height: auto !important;
  min-width: 0 !important;
  min-height: 0 !important;
}

.markdown .copy-btn .icon {
  width: 14px !important;
  height: 14px !important;
  font-size: 14px !important;
}

.markdown .copy-btn:hover {
  color: var(--color-primary);
}

.markdown .copy-btn:active,
.markdown .copy-btn:focus,
.markdown .copy-btn:focus-visible {
  background: transparent !important;
  background-color: transparent !important;
  border: none !important;
  outline: none !important;
  box-shadow: none !important;
}
</style>
