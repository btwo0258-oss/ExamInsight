<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { Check, ChevronLeft, ChevronRight, X } from 'lucide-vue-next'

import ChatAttachmentList from '@/components/chat/ChatAttachmentList.vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import MessageActions from './MessageActions.vue'
import type { ChatMessage, Citation, MessageVersionGroup } from '@/types/contracts/chatV2'

const props = withDefaults(defineProps<{
  message: ChatMessage
  versionGroup?: MessageVersionGroup
  busy?: boolean
  speechLoading?: boolean
  speechPlaying?: boolean
  speechError?: string
  stageText?: string
}>(), {
  versionGroup: undefined,
  busy: false,
  speechLoading: false,
  speechPlaying: false,
  speechError: '',
  stageText: '',
})

const emit = defineEmits<{
  edit: [messageId: string, content: string]
  regenerate: [messageId: string]
  speak: [message: ChatMessage]
  switchVersion: [branchId: string]
  openAsset: [assetId: string, messageId: string]
  openCitation: [citation: Citation, messageId: string]
}>()

const editing = ref(false)
const draft = ref('')
const editor = ref<HTMLTextAreaElement | null>(null)
const isUser = computed(() => props.message.role === 'USER')
const streaming = computed(() => props.message.status === 'STREAMING')
const waitingLabel = computed(() => {
  // Generation is shown by the reserved card, not by another status under the answer.
  const stage = props.stageText
  if (stage.includes('停止')) return '正在停止'
  return /恢复|检索/.test(stage) ? stage : '正在理解你的需求'
})
const versionIndex = computed(() => props.versionGroup?.versions.findIndex(
  version => version.messageId === props.message.id,
) ?? -1)
const attachmentItems = computed(() => props.message.attachments.map(item => ({
  key: item.assetVersionId || item.assetId,
  assetId: item.assetId,
  name: item.name,
  mimeType: item.mimeType,
  sizeBytes: item.sizeBytes,
  status: 'ready' as const,
})))

async function beginEdit() {
  draft.value = props.message.content
  editing.value = true
  await nextTick()
  editor.value?.focus()
  editor.value?.setSelectionRange(draft.value.length, draft.value.length)
}

function submitEdit() {
  const content = draft.value.trim()
  if (!content || content === props.message.content.trim()) {
    editing.value = false
    return
  }
  editing.value = false
  emit('edit', props.message.id, content)
}

function switchRelative(offset: number) {
  const versions = props.versionGroup?.versions
  if (!versions?.length) return
  const target = versions[versionIndex.value + offset]
  if (target) emit('switchVersion', target.branchId)
}
</script>

<template>
  <article
    class="message-row"
    :class="[isUser ? 'is-user' : 'is-assistant', isUser ? 'user-message' : 'assistant-message']"
    :data-message-id="message.id"
  >
    <div class="message-main">
      <template v-if="isUser">
        <ChatAttachmentList
          v-if="attachmentItems.length"
          class="message-attachments user-message-attachments"
          :items="attachmentItems"
          compact
          @open="emit('openAsset', $event, message.id)"
        />
        <div v-if="editing" class="edit-card">
          <textarea ref="editor" v-model="draft" rows="3" @keydown.meta.enter.prevent="submitEdit" @keydown.ctrl.enter.prevent="submitEdit" />
          <div class="edit-actions">
            <button type="button" title="取消" @click="editing = false"><X :size="15" /></button>
            <button type="button" title="保存并重新生成" :disabled="!draft.trim()" @click="submitEdit"><Check :size="15" /></button>
          </div>
        </div>
        <div v-else-if="message.content" class="user-bubble">{{ message.content }}</div>
      </template>
      <MarkdownRenderer v-else-if="message.content" :content="message.content" :is-streaming="streaming" />

      <div v-if="!isUser && streaming && !message.content.trim()" class="response-loading" role="status" :aria-label="waitingLabel">
        <div class="response-loading__status">
          <span class="response-loading__cursor" aria-hidden="true" />
          <span class="response-loading__label">{{ waitingLabel }}</span>
        </div>
        <div class="response-skeleton" aria-hidden="true">
          <span class="response-skeleton__line response-skeleton__line--wide" />
          <span class="response-skeleton__line" />
          <span class="response-skeleton__line response-skeleton__line--short" />
        </div>
      </div>

      <ChatAttachmentList
        v-if="attachmentItems.length && !isUser"
        class="message-attachments"
        :items="attachmentItems"
        compact
        @open="emit('openAsset', $event, message.id)"
      />

      <div v-if="message.citations.length" class="citations">
        <span>参考来源</span>
        <button
          v-for="citation in message.citations"
          :key="citation.chunkId"
          type="button"
          :title="citation.quotedText"
          @click="emit('openCitation', citation, message.id)"
        >
          {{ citation.number }}. {{ citation.assetName }}
          <small v-if="citation.locator">{{ citation.locator }}</small>
        </button>
      </div>

      <slot />

      <div v-if="message.content && !editing && !streaming" class="message-footer" :class="{ 'is-user': isUser }">
        <MessageActions
          :role="message.role"
          :content="message.content"
          :disabled="busy"
          :speech-loading="speechLoading"
          :speech-playing="speechPlaying"
          :speech-error="speechError"
          @edit="beginEdit"
          @regenerate="emit('regenerate', message.id)"
          @speak="emit('speak', message)"
        />
        <div v-if="!streaming && versionGroup && versionGroup.versions.length > 1 && versionIndex >= 0" class="version-nav" aria-label="消息版本切换">
          <button type="button" title="上一个版本" :disabled="busy || versionIndex === 0" @click="switchRelative(-1)">
            <ChevronLeft :size="15" />
          </button>
          <span>{{ versionIndex + 1 }} / {{ versionGroup.versions.length }}</span>
          <button type="button" title="下一个版本" :disabled="busy || versionIndex === versionGroup.versions.length - 1" @click="switchRelative(1)">
            <ChevronRight :size="15" />
          </button>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.message-row { display: flex; width: 100%; min-width: 0; }
.message-main { width: 100%; min-width: 0; }
.response-loading { display: grid; gap: 14px; padding: 6px 0 14px; color: var(--color-text-muted); }
.response-loading__status { display: flex; align-items: center; gap: 10px; min-height: 22px; }
.response-loading__cursor { flex: 0 0 auto; width: 4px; height: 18px; border-radius: 1px; background: var(--color-text); animation: response-blink 1s ease-in-out infinite; }
.response-loading__label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.response-skeleton { display: grid; gap: 10px; width: 100%; }
.response-skeleton__line { position: relative; display: block; width: 76%; height: 13px; overflow: hidden; border-radius: 7px; background: var(--color-hover-strong); }
.response-skeleton__line--wide { width: 92%; }
.response-skeleton__line--short { width: 54%; }
.response-skeleton__line::after { position: absolute; inset: 0; background: linear-gradient(100deg, transparent 15%, rgb(255 255 255 / 28%) 48%, transparent 82%); content: ''; animation: response-shimmer 1.45s linear infinite; }
@keyframes response-shimmer { to { transform: translateX(100%); } }
@keyframes response-blink { 50% { opacity: .2; } }
@media (prefers-reduced-motion: reduce) {
  .response-loading__cursor, .response-skeleton__line::after { animation: none; }
}
.is-user { justify-content: flex-end; }
.is-user .message-main { display: grid; justify-items: end; }
.user-bubble { max-width: min(72%, 620px); padding: 11px 16px; border-radius: 20px; color: var(--color-bg); background: var(--color-text); line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.edit-card { width: min(72%, 620px); padding: 10px; border: 1px solid var(--color-border); border-radius: 18px; background: var(--color-bg); box-shadow: 0 10px 28px rgb(0 0 0 / 8%); }
.edit-card textarea { display: block; width: 100%; min-height: 88px; padding: 5px; resize: vertical; border: 0; outline: 0; color: var(--color-text); background: transparent; font: inherit; line-height: 1.6; }
.edit-actions { display: flex; justify-content: flex-end; gap: 5px; }
.edit-actions button, .version-nav button { display: grid; width: 28px; height: 28px; padding: 0; place-items: center; border: 0; border-radius: 8px; color: var(--color-text-muted); background: transparent; cursor: pointer; }
.edit-actions button:hover:not(:disabled), .version-nav button:hover:not(:disabled) { color: var(--color-text); background: var(--color-surface); }
.edit-actions button:disabled, .version-nav button:disabled { opacity: .35; cursor: not-allowed; }
.message-attachments { margin-top: 8px; }
.is-user .message-attachments { justify-self: end; }
.user-message-attachments { margin-top: 0; margin-bottom: 8px; }
.user-message-attachments :deep(.image-grid), .user-message-attachments :deep(.file-grid) { direction: rtl; justify-content: flex-start; }
.citations { display: flex; flex-wrap: wrap; gap: 7px; width: 100%; margin-top: 14px; }
.citations > span { flex-basis: 100%; color: var(--color-text-muted); font-size: 12px; }
.citations button { display: inline-flex; gap: 5px; padding: 7px 10px; border: 1px solid var(--color-border); border-radius: 10px; color: inherit; background: var(--color-bg); cursor: pointer; }
.citations button:hover { background: var(--color-surface); }
.citations small { color: var(--color-text-muted); }
.message-footer { display: flex; align-items: center; gap: 6px; }
.message-footer.is-user { justify-content: flex-end; }
.version-nav { display: flex; align-items: center; gap: 1px; margin-top: 7px; color: var(--color-text-muted); font-size: 12px; }
@media (max-width: 720px) {
  .user-bubble, .edit-card { max-width: 88%; }
}
</style>
