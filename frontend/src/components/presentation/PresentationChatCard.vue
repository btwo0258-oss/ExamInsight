<script setup lang="ts">
import { computed } from 'vue'
import {
  Check,
  Download,
  ExternalLink,
  FilePlus2,
  FolderPlus,
  LoaderCircle,
  RefreshCw,
  Settings2,
  Sparkles,
  X,
} from 'lucide-vue-next'
import ResourceTypeIcon from '@/components/common/ResourceTypeIcon.vue'
import type { PresentationChatCardDto } from '@/types/contracts/presentation'

const props = withDefaults(defineProps<{
  data: PresentationChatCardDto
  busy?: boolean
  error?: string
}>(), {
  busy: false,
  error: '',
})

const emit = defineEmits<{
  update: [data: PresentationChatCardDto]
  cancel: []
  moreSettings: []
  generateOutline: []
  open: []
  download: []
  associate: []
  retry: []
}>()

const isProposal = computed(() => props.data.view === 'proposal')
const canGenerate = computed(() => props.data.config.topic.trim().length > 0 && !props.busy)
const pageMeta = computed(() => props.data.previewPageCount || props.data.config.pageCount)

function updateTopic(topic: string) {
  const oldTopic = props.data.config.topic
  emit('update', {
    ...props.data,
    config: {
      ...props.data.config,
      topic,
      title: !props.data.config.title.trim() || props.data.config.title === oldTopic
        ? topic
        : props.data.config.title,
    },
  })
}

function onTopicInput(event: Event) {
  updateTopic((event.target as HTMLInputElement).value)
}

function updatePageCount(value: string) {
  const pageCount = Math.min(30, Math.max(3, Number(value) || 8))
  emit('update', { ...props.data, config: { ...props.data.config, pageCount } })
}

function onPageCountChange(event: Event) {
  updatePageCount((event.target as HTMLInputElement).value)
}

</script>

<template>
  <section class="presentation-card" :class="`presentation-card--${data.view}`">
    <header class="presentation-card__header">
      <ResourceTypeIcon class="presentation-card__icon" type="presentation" :size="19" :container-size="36" />
      <div>
        <strong>{{ isProposal ? '生成 PPT' : data.status === 'failed' ? 'PPT 生成失败' : 'PPT 已生成' }}</strong>
        <small v-if="isProposal">确认后再进入大纲与页面生成</small>
        <small v-else>{{ data.fileName || `${data.config.title || data.config.topic}.pptx` }}</small>
      </div>
      <span v-if="!isProposal && data.status === 'ready'" class="presentation-card__status">
        <Check :size="14" /> 完成
      </span>
    </header>

    <template v-if="isProposal">
      <label class="presentation-field presentation-field--topic">
        <span>演示主题</span>
        <input
          :value="data.config.topic"
          maxlength="120"
          placeholder="输入 PPT 主题"
          :disabled="busy"
          @input="onTopicInput"
        />
      </label>

      <div class="presentation-card__controls">
        <label class="presentation-field presentation-field--pages">
          <span>页数</span>
          <input
            :value="data.config.pageCount"
            type="number"
            min="3"
            max="30"
            :disabled="busy"
            @change="onPageCountChange"
          />
        </label>
      </div>

      <p v-if="data.knowledgeBaseId || data.projectId" class="presentation-card__context">
        <FolderPlus :size="14" />
        <span>
          {{ data.knowledgeBaseId ? '已关联当前知识库' : '' }}{{ data.knowledgeBaseId && data.projectId ? ' · ' : '' }}{{ data.projectId ? '已关联当前学习项目' : '' }}
        </span>
      </p>

      <p v-if="error || data.errorMessage" class="presentation-card__error">{{ error || data.errorMessage }}</p>

      <footer class="presentation-card__actions">
        <button class="card-button card-button--quiet" type="button" :disabled="busy" @click="emit('cancel')">
          <X :size="16" />取消
        </button>
        <button class="card-button" type="button" :disabled="busy" @click="emit('moreSettings')">
          <Settings2 :size="16" />更多设置
        </button>
        <button
          v-if="data.presentationId && data.status === 'outline_ready'"
          class="card-button card-button--primary"
          type="button"
          @click="emit('open')"
        >
          <ExternalLink :size="16" />查看大纲
        </button>
        <button
          v-else
          class="card-button card-button--primary"
          type="button"
          :disabled="!canGenerate"
          @click="emit('generateOutline')"
        >
          <LoaderCircle v-if="busy" class="spin" :size="16" />
          <Sparkles v-else :size="16" />
          {{ busy ? '正在生成' : '生成大纲' }}
        </button>
      </footer>
    </template>

    <template v-else>
      <dl class="presentation-card__summary">
        <div><dt>主题</dt><dd>{{ data.config.title || data.config.topic }}</dd></div>
        <div><dt>页面</dt><dd>{{ pageMeta }} 页 · {{ data.config.aspectRatio }}</dd></div>
        <div><dt>归档</dt><dd>已自动进入资料库</dd></div>
      </dl>
      <p v-if="error || data.errorMessage" class="presentation-card__error">{{ error || data.errorMessage }}</p>
      <footer class="presentation-card__actions">
        <button v-if="data.status === 'failed'" class="card-button card-button--primary" type="button" :disabled="busy" @click="emit('retry')">
          <LoaderCircle v-if="busy" class="spin" :size="16" />
          <RefreshCw v-else :size="16" />重试
        </button>
        <template v-else>
          <button class="card-button" type="button" @click="emit('open')"><ExternalLink :size="16" />预览</button>
          <button class="card-button" type="button" :disabled="busy" @click="emit('download')"><Download :size="16" />下载</button>
          <button class="card-button card-button--primary" type="button" :disabled="busy || Boolean(data.knowledgeBaseId)" @click="emit('associate')">
            <FilePlus2 :size="16" />{{ data.knowledgeBaseId ? '已加入知识库' : '添加到知识库' }}
          </button>
        </template>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.presentation-card {
  width: min(620px, 100%);
  margin: 12px 0 24px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.presentation-card__header {
  min-height: 64px;
  padding: 12px 14px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--color-border);
}

.presentation-card__header strong,
.presentation-card__header small {
  display: block;
}

.presentation-card__header strong {
  color: var(--color-text);
  font-size: 14px;
  line-height: 20px;
}

.presentation-card__header small {
  margin-top: 2px;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 17px;
}

.presentation-card__status {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--color-success);
  font-size: 12px;
  font-weight: 700;
}

.presentation-field {
  display: grid;
  gap: 6px;
}

.presentation-field > span {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.presentation-field input {
  height: 38px;
  min-width: 0;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-bg);
  color: var(--color-text);
  font: inherit;
  font-size: 13px;
  outline: none;
}

.presentation-field input:focus {
  border-color: var(--color-text-muted);
  box-shadow: 0 0 0 2px var(--color-hover);
}

.presentation-field--topic {
  padding: 14px 14px 0;
}

.presentation-card__controls {
  padding: 12px 14px 0;
  width: 118px;
}

.presentation-card__context,
.presentation-card__error {
  margin: 10px 14px 0;
  font-size: 12px;
  line-height: 18px;
}

.presentation-card__context {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
}

.presentation-card__error {
  color: var(--color-danger, #b42318);
}

.presentation-card__summary {
  margin: 0;
  padding: 6px 14px;
}

.presentation-card__summary > div {
  min-height: 38px;
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--color-border);
}

.presentation-card__summary > div:last-child {
  border-bottom: 0;
}

.presentation-card__summary dt {
  color: var(--color-text-muted);
  font-size: 12px;
}

.presentation-card__summary dd {
  margin: 0;
  color: var(--color-text);
  font-size: 13px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.presentation-card__actions {
  padding: 12px 14px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid var(--color-border);
}

.presentation-card--proposal .presentation-card__actions {
  margin-top: 14px;
}

.card-button {
  min-height: 34px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
}

.card-button:hover:not(:disabled) {
  background: var(--color-hover);
}

.card-button--quiet {
  margin-right: auto;
  color: var(--color-text-muted);
}

.card-button--primary {
  border-color: var(--color-text);
  background: var(--color-text);
  color: var(--color-surface);
}

.card-button--primary:hover:not(:disabled) {
  background: var(--color-text-muted);
  color: var(--color-surface);
}

.card-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 640px) {
  .presentation-card__actions {
    flex-wrap: wrap;
  }

  .card-button {
    flex: 1 1 auto;
  }

  .card-button--quiet {
    margin-right: 0;
  }
}
</style>
