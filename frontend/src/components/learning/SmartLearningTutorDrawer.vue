<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore, type ChatMessage } from '@/stores/message'

const props = withDefaults(defineProps<{
  open: boolean
  projectId: string
  projectName: string
  taskTitle?: string
  sourceAssetIds?: string[]
}>(), { taskTitle: '', sourceAssetIds: () => [] })
const emit = defineEmits<{ close: [] }>()
const conversationStore = useConversationStore()
const messageStore = useMessageStore()
const conversationId = ref<number | null>(null)
const input = ref('')
const errorMessage = ref('')
const list = ref<HTMLElement | null>(null)

const messages = computed(() => conversationId.value
  ? (messageStore.byConversation[String(conversationId.value)] ?? []).filter(item => item.role === 'user' || item.role === 'assistant')
  : [])

async function ensureConversation() {
  if (conversationId.value) return conversationId.value
  conversationStore.init()
  const id = await conversationStore.create({
    title: `${props.projectName} · AI 助教`,
    navigate: false,
    conversationType: 'general',
  })
  conversationId.value = Number(id)
  await messageStore.ensureLoaded(conversationId.value)
  return conversationId.value
}

async function scrollBottom() {
  await nextTick()
  if (list.value) list.value.scrollTop = list.value.scrollHeight
}

async function send() {
  const text = input.value.trim()
  if (!text || messageStore.isStreaming) return
  errorMessage.value = ''
  input.value = ''
  try {
    const id = await ensureConversation()
    const context = `当前学习项目：${props.projectName}\n当前任务：${props.taskTitle || '项目总览'}\n请优先结合这个学习上下文回答。\n\n用户问题：${text}`
    await messageStore.sendMessage(id, context, undefined, undefined, undefined, [], false, {
      runtime: 'v2-general',
      sourceAssetExternalIds: props.sourceAssetIds,
    })
    await scrollBottom()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'AI 助教暂时不可用，请稍后重试。'
  }
}

function stop() {
  messageStore.stopStreaming()
}

watch(() => props.open, value => { if (value) void scrollBottom() })
</script>

<template>
  <div v-if="open" class="smart-tutor-drawer" role="dialog" aria-modal="true" aria-label="AI 助教">
    <button class="smart-tutor-backdrop" type="button" aria-label="关闭 AI 助教" @click="emit('close')" />
    <section class="smart-tutor-surface">
      <header>
        <div><strong>AI 助教</strong><small>{{ taskTitle || projectName }}</small></div>
        <button type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>
      <div ref="list" class="smart-tutor-messages">
        <div v-if="!messages.length" class="smart-tutor-empty">围绕当前任务提问，助教会结合已选资料回答。</div>
        <article v-for="message in messages" :key="message.id" :class="['smart-tutor-message', `is-${message.role}`]">
          <p>{{ message.content || (message.streaming ? '正在思考…' : '') }}</p>
        </article>
        <p v-if="errorMessage" class="smart-tutor-error">{{ errorMessage }}</p>
      </div>
      <footer>
        <textarea v-model="input" rows="2" placeholder="问问当前任务…" @keydown.enter.exact.prevent="send" />
        <button v-if="messageStore.isStreaming" class="smart-tutor-stop" type="button" @click="stop">停止</button>
        <button v-else class="smart-tutor-send" type="button" :disabled="!input.trim()" @click="send">发送</button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.smart-tutor-drawer { position: fixed; inset: 0; z-index: 250; display: flex; justify-content: flex-end; }
.smart-tutor-backdrop { position: absolute; inset: 0; width: 100%; border: 0; background: rgba(15, 23, 42, .18); cursor: default; }
.smart-tutor-surface { position: relative; width: min(620px, calc(100vw - 48px)); height: 100%; display: grid; grid-template-rows: auto minmax(0, 1fr) auto; background: var(--color-surface, #fff); border-left: 1px solid var(--color-border, #e5e7eb); box-shadow: -14px 0 38px rgba(15, 23, 42, .14); }
.smart-tutor-surface header, .smart-tutor-surface footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 22px; border-bottom: 1px solid var(--color-border, #e5e7eb); }
.smart-tutor-surface header strong { display: block; color: var(--color-text, #111827); font-size: 16px; }
.smart-tutor-surface header small { display: block; margin-top: 4px; color: var(--color-text-muted, #6b7280); font-size: 12px; }
.smart-tutor-surface header button { border: 0; background: transparent; color: var(--color-text-muted, #6b7280); font-size: 26px; cursor: pointer; }
.smart-tutor-messages { min-height: 0; overflow: auto; padding: 22px; }
.smart-tutor-empty { color: var(--color-text-muted, #6b7280); font-size: 14px; line-height: 1.7; }
.smart-tutor-message { max-width: 88%; margin: 0 0 14px; padding: 11px 14px; border-radius: 14px; color: var(--color-text, #111827); font-size: 14px; line-height: 1.65; white-space: pre-wrap; }
.smart-tutor-message p { margin: 0; }
.smart-tutor-message.is-user { margin-left: auto; background: #303030; color: #fff; }
.smart-tutor-message.is-assistant { background: var(--color-bg-subtle, #f4f4f3); }
.smart-tutor-error { color: #c0392b; font-size: 13px; }
.smart-tutor-surface footer { border-top: 1px solid var(--color-border, #e5e7eb); border-bottom: 0; }
.smart-tutor-surface textarea { flex: 1; resize: none; border: 1px solid var(--color-border, #d1d5db); border-radius: 10px; padding: 10px 12px; color: var(--color-text, #111827); background: var(--color-surface, #fff); font: inherit; }
.smart-tutor-send, .smart-tutor-stop { flex: none; border: 0; border-radius: 9px; padding: 10px 14px; color: #fff; background: #303030; cursor: pointer; }
.smart-tutor-send:disabled { opacity: .45; cursor: not-allowed; }
@media (max-width: 700px) { .smart-tutor-surface { width: 100%; } .smart-tutor-surface header, .smart-tutor-surface footer { padding-inline: 16px; } }
</style>
