<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { Check, ChevronLeft, ChevronRight, X } from 'lucide-vue-next'

import ChatAttachmentList from '@/components/chat/ChatAttachmentList.vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import MessageActions from './MessageActions.vue'
import type { ChatMessage, MessageVersionGroup } from '@/types/contracts/chatV2'

const props = withDefaults(defineProps<{
  message: ChatMessage
  versionGroup?: MessageVersionGroup
  busy?: boolean
  speechLoading?: boolean
  speechPlaying?: boolean
}>(), {
  versionGroup: undefined,
  busy: false,
  speechLoading: false,
  speechPlaying: false,
})

const emit = defineEmits<{
  edit: [messageId: string, content: string]
  regenerate: [messageId: string]
  speak: [message: ChatMessage]
  switchVersion: [branchId: string]
  openAsset: [assetId: string]
}>()

const editing = ref(false)
const draft = ref('')
const editor = ref<HTMLTextAreaElement | null>(null)
const isUser = computed(() => props.message.role === 'USER')
const streaming = computed(() => props.message.status === 'STREAMING')
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
  <article class="message-row" :class="isUser ? 'is-user' : 'is-assistant'" :data-message-id="message.id">
    <div class="message-main">
      <template v-if="isUser">
        <div v-if="editing" class="edit-card">
          <textarea ref="editor" v-model="draft" rows="3" @keydown.meta.enter.prevent="submitEdit" @keydown.ctrl.enter.prevent="submitEdit" />
          <div class="edit-actions">
            <button type="button" title="取消" @click="editing = false"><X :size="15" /></button>
            <button type="button" title="保存并重新生成" :disabled="!draft.trim()" @click="submitEdit"><Check :size="15" /></button>
          </div>
        </div>
        <div v-else-if="message.content" class="user-bubble">{{ message.content }}</div>
      </template>
      <MarkdownRenderer v-else :content="message.content" :is-streaming="streaming" />

      <ChatAttachmentList
        v-if="attachmentItems.length"
        class="message-attachments"
        :items="attachmentItems"
        compact
        @open="emit('openAsset', $event)"
      />

      <div v-if="message.citations.length" class="citations">
        <span>参考来源</span>
        <button
          v-for="citation in message.citations"
          :key="citation.chunkId"
          type="button"
          :title="citation.quotedText"
          @click="emit('openAsset', citation.assetId)"
        >
          {{ citation.number }}. {{ citation.assetName }}
          <small v-if="citation.locator">{{ citation.locator }}</small>
        </button>
      </div>

      <slot />

      <div v-if="!streaming && !editing" class="message-footer" :class="{ 'is-user': isUser }">
        <MessageActions
          :role="message.role"
          :content="message.content"
          :disabled="busy"
          :speech-loading="speechLoading"
          :speech-playing="speechPlaying"
          @edit="beginEdit"
          @regenerate="emit('regenerate', message.id)"
          @speak="emit('speak', message)"
        />
        <div v-if="versionGroup && versionIndex >= 0" class="version-nav" aria-label="消息版本切换">
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
