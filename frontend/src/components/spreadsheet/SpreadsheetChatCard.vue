<script setup lang="ts">
import { computed } from 'vue'
import { Check, Download, ExternalLink, LoaderCircle, RefreshCw } from 'lucide-vue-next'
import ResourceTypeIcon from '@/components/common/ResourceTypeIcon.vue'
import type { SpreadsheetChatCardDto } from '@/types/contracts/spreadsheet'

const props = withDefaults(defineProps<{
  data: SpreadsheetChatCardDto
  busy?: boolean
  error?: string
}>(), { busy: false, error: '' })

const emit = defineEmits<{
  open: []
  download: []
  retry: []
}>()

const isGenerating = computed(() => props.data.status === 'generating')
const isReady = computed(() => props.data.status === 'ready')
const isFailed = computed(() => props.data.status === 'failed' || props.data.status === 'cancelled')
const displayName = computed(() => props.data.fileName || `${props.data.config.title || props.data.config.topic}.xlsx`)
</script>

<template>
  <section class="sheet-card" :class="`sheet-card--${data.status}`">
    <header>
      <ResourceTypeIcon class="sheet-card__icon" type="spreadsheet" :size="19" :container-size="36" />
      <div>
        <strong>{{ isGenerating ? '正在生成电子表格' : isFailed ? '电子表格生成失败' : '电子表格已生成' }}</strong>
        <small>{{ isGenerating ? 'AI 正在读取当前对话、附件和已关联资料' : displayName }}</small>
      </div>
      <span v-if="isReady" class="sheet-card__status"><Check :size="14" />完成</span>
      <LoaderCircle v-else-if="isGenerating" class="spin sheet-card__loader" :size="18" />
    </header>

    <div class="sheet-card__body">
      <dl>
        <div><dt>要求</dt><dd>{{ data.config.topic }}</dd></div>
        <div v-if="isReady"><dt>工作表</dt><dd>{{ data.config.sheetCount }} 个</dd></div>
        <div><dt>上下文</dt><dd>{{ data.projectId ? '学习项目' : data.knowledgeBaseId ? '知识库' : '当前对话' }}{{ data.knowledgeBaseId && data.projectId ? ' · 知识库' : '' }}</dd></div>
        <div v-if="isReady"><dt>归档</dt><dd>已自动进入资料库</dd></div>
      </dl>
      <p v-if="error || data.errorMessage" class="sheet-card__error">{{ error || data.errorMessage }}</p>
    </div>

    <footer v-if="!isGenerating">
      <button v-if="isFailed" class="sheet-button sheet-button--primary" type="button" :disabled="busy" @click="emit('retry')">
        <RefreshCw :size="16" />重试生成
      </button>
      <template v-else>
        <button class="sheet-button sheet-button--primary" type="button" @click="emit('open')"><ExternalLink :size="16" />预览</button>
        <button class="sheet-button" type="button" :disabled="busy" @click="emit('download')"><Download :size="16" />下载</button>
      </template>
    </footer>
  </section>
</template>

<style scoped>
.sheet-card {
  width: min(620px, 100%);
  margin: 12px 0 24px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.sheet-card > header {
  min-height: 64px;
  padding: 12px 14px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--color-border);
}

.sheet-card header strong,
.sheet-card header small { display: block; }
.sheet-card header strong { font-size: 14px; }
.sheet-card header small { margin-top: 2px; color: var(--color-text-muted); font-size: 12px; }
.sheet-card__status { display: flex; align-items: center; gap: 4px; color: var(--color-success); font-size: 12px; font-weight: 700; }
.sheet-card__loader { color: var(--color-text-muted); }
.sheet-card__body { padding: 6px 14px; }
.sheet-card dl { margin: 0; }
.sheet-card dl div { min-height: 38px; display: grid; grid-template-columns: 70px minmax(0, 1fr); align-items: center; gap: 8px; border-bottom: 1px solid var(--color-border); }
.sheet-card dl div:last-child { border-bottom: 0; }
.sheet-card dt { color: var(--color-text-muted); font-size: 12px; }
.sheet-card dd { margin: 0; font-size: 13px; font-weight: 600; overflow-wrap: anywhere; }
.sheet-card__error { margin: 8px 0; color: var(--color-danger); font-size: 12px; line-height: 18px; }
.sheet-card > footer { padding: 12px 14px; display: flex; justify-content: flex-end; gap: 8px; border-top: 1px solid var(--color-border); }
.sheet-button { min-height: 34px; padding: 0 12px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; border: 1px solid var(--color-border); border-radius: 6px; background: var(--color-surface); color: var(--color-text); cursor: pointer; font: inherit; font-size: 12px; font-weight: 700; }
.sheet-button:hover:not(:disabled) { background: var(--color-hover); }
.sheet-button--primary { border-color: var(--color-text); background: var(--color-text); color: var(--color-surface); }
.sheet-button:disabled { cursor: not-allowed; opacity: .55; }
.spin { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 640px) {
  .sheet-card > footer { flex-wrap: wrap; }
  .sheet-button { flex: 1 1 auto; }
}
</style>
