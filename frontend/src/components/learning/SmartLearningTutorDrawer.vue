<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import AppInput from '@/components/common/AppInput.vue'
import MarkdownRenderer from '@/components/chat/message/MarkdownRenderer.vue'
import { getAsset, uploadAsset } from '@/api/assetLibraryV2'
import { getOrCreateSmartLearningTutorThread } from '@/api/smartLearning'
import { useChatV2Store } from '@/stores/chatV2'
import type { ChatMessage, MessageAttachment } from '@/types/contracts/chatV2'

const WIDTH_KEY = 'examinsight.ui.smart-learning-tutor-width'
const DEFAULT_WIDTH = 620
const MIN_WIDTH = 420
const MAX_WIDTH = 920

const props = withDefaults(defineProps<{
  open: boolean
  projectId: string
  projectName: string
  taskId?: string
  taskTitle?: string
  sourceAssetIds?: string[]
  initialQuestion?: string
  initialRequestId?: number
}>(), { taskId: '', taskTitle: '', sourceAssetIds: () => [], initialQuestion: '', initialRequestId: 0 })
const emit = defineEmits<{ close: [] }>()
const router = useRouter()
const chatStore = useChatV2Store()
const conversationId = ref('')
const preparing = ref(false)
const errorMessage = ref('')
const messageArea = ref<HTMLElement | null>(null)
const input = ref<InstanceType<typeof AppInput> | null>(null)
const drawerWidth = ref(Number(localStorage.getItem(WIDTH_KEY)) || DEFAULT_WIDTH)
const resizing = ref(false)
let startX = 0
let startWidth = DEFAULT_WIDTH

const messages = computed(() => chatStore.activeConversation?.id === conversationId.value
  ? chatStore.messages.filter(item => ['USER', 'ASSISTANT'].includes(item.role)).slice(-30)
  : [])
const latestAssistantId = computed(() => [...messages.value].reverse().find(item => item.role === 'ASSISTANT')?.id)
const surfaceStyle = computed(() => ({ '--tutor-width': `${drawerWidth.value}px` }))

function clampWidth(value: number) {
  const maximum = Math.min(MAX_WIDTH, Math.max(320, window.innerWidth - 48))
  return Math.max(Math.min(MIN_WIDTH, maximum), Math.min(maximum, value))
}

function resize(event: PointerEvent) {
  drawerWidth.value = clampWidth(startWidth + startX - event.clientX)
}

function stopResize() {
  if (!resizing.value) return
  resizing.value = false
  localStorage.setItem(WIDTH_KEY, String(Math.round(drawerWidth.value)))
  document.removeEventListener('pointermove', resize)
  document.removeEventListener('pointerup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

function beginResize(event: PointerEvent) {
  if (window.innerWidth <= 700) return
  event.preventDefault()
  startX = event.clientX
  startWidth = drawerWidth.value
  resizing.value = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('pointermove', resize)
  document.addEventListener('pointerup', stopResize)
}

function resizeByKeyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return
  event.preventDefault()
  drawerWidth.value = clampWidth(drawerWidth.value + (event.key === 'ArrowLeft' ? 16 : -16))
  localStorage.setItem(WIDTH_KEY, String(Math.round(drawerWidth.value)))
}

async function scrollBottom() {
  await nextTick()
  if (messageArea.value) messageArea.value.scrollTop = messageArea.value.scrollHeight
}

async function ensureReady() {
  if (preparing.value) return conversationId.value
  preparing.value = true
  errorMessage.value = ''
  try {
    const thread = await getOrCreateSmartLearningTutorThread(props.projectId, props.taskId || null)
    conversationId.value = thread.conversationId
    if (chatStore.activeConversation?.id !== thread.conversationId) await chatStore.load(thread.conversationId)
    await scrollBottom()
    return thread.conversationId
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'AI 助教初始化失败，请稍后重试。'
    return ''
  } finally { preparing.value = false }
}

async function waitForAsset(assetId: string) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    const detail = await getAsset(assetId)
    const version = detail.asset.version
    if (version?.status === 'FAILED' || version?.indexStatus === 'FAILED') throw new Error(`附件 ${detail.asset.name} 处理失败。`)
    if (version?.status === 'READY' && version?.indexStatus !== 'PROCESSING') return detail.asset
    await new Promise(resolve => window.setTimeout(resolve, 1000))
  }
  throw new Error('附件仍在处理中，请稍后再试。')
}

async function uploadFiles(files: File[]) {
  const attachments: MessageAttachment[] = []
  for (const file of files) {
    const uploaded = await uploadAsset(file, null)
    const asset = await waitForAsset(uploaded.completion.asset.assetId)
    const version = asset.version
    if (!version) throw new Error(`附件 ${file.name} 尚未准备完成。`)
    attachments.push({
      assetId: asset.assetId,
      assetVersionId: version.versionId,
      name: asset.name,
      mimeType: version.mimeType || file.type || 'application/octet-stream',
      sizeBytes: version.sizeBytes,
      assetType: (version.mimeType || file.type).startsWith('image/') ? 'IMAGE' : 'FILE',
    })
  }
  return attachments
}

async function sendQuestion(text: string, files: File[] = [], complete?: (success?: boolean) => void) {
  const question = text.trim() || (files.length ? '请分析我上传的附件。' : '')
  if (!question || chatStore.sending) { complete?.(false); return }
  errorMessage.value = ''
  let succeeded = false
  try {
    if (!await ensureReady()) return
    const uploaded = await uploadFiles(files)
    const sourceIds = [...new Set([...props.sourceAssetIds, ...uploaded.map(item => item.assetId)])]
    const sending = chatStore.send(question, sourceIds, uploaded)
    await scrollBottom()
    await sending
    succeeded = true
    await scrollBottom()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '消息发送失败，请稍后重试。'
  } finally { complete?.(succeeded) }
}

function quickQuestion(kind: 'example' | 'quiz' | 'hint') {
  const topic = props.taskTitle || props.projectName
  const prompts = {
    example: `请围绕“${topic}”换一个更容易理解的例子。`,
    quiz: `请围绕“${topic}”出 3 道由浅入深的练习题，先不要公布答案。`,
    hint: `我在“${topic}”这里卡住了，请只给下一步提示，不要直接给答案。`,
  }
  input.value?.setText(prompts[kind])
}

async function regenerate(message: ChatMessage) {
  try { await chatStore.regenerateMessage(message.id); await scrollBottom() }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '重新生成失败。' }
}

async function openFullChat() {
  const id = conversationId.value || await ensureReady()
  if (id) await router.push({ path: `/chat/${id}`, query: { tutor: '1', projectId: props.projectId, taskId: props.taskId || undefined } })
}

watch(() => props.open, (value) => { if (value) void ensureReady() }, { immediate: true })
watch(() => props.initialRequestId, async value => {
  if (!value || !props.initialQuestion) return
  await nextTick()
  input.value?.setText(props.initialQuestion)
}, { immediate: true })
watch(() => messages.value.map(message => `${message.id}:${message.content.length}:${message.status}`).join('|'), () => { void scrollBottom() })
onBeforeUnmount(stopResize)
</script>

<template>
  <div v-if="open" class="tutor-drawer" role="dialog" aria-modal="true" aria-label="AI 助教">
    <button class="tutor-backdrop" type="button" aria-label="关闭 AI 助教" @click="emit('close')" />
    <section class="tutor-surface" :class="{ resizing }" :style="surfaceStyle">
      <div class="resize-handle" role="separator" aria-label="调整助教宽度" tabindex="0" @pointerdown="beginResize" @keydown="resizeByKeyboard" />
      <header class="tutor-header"><div><span class="tutor-icon"><AppIcon name="robot" :size="18" /></span><span><strong>AI 助教</strong><small>{{ taskTitle ? `当前任务 · ${taskTitle}` : `当前项目 · ${projectName}` }}</small></span></div><button type="button" aria-label="关闭" @click="emit('close')"><AppIcon name="close" :size="17" /></button></header>
      <div ref="messageArea" class="tutor-messages">
        <div v-if="preparing && !messages.length" class="tutor-loading"><i /><i /><i /></div>
        <div v-else-if="!messages.length" class="tutor-welcome"><span class="tutor-icon large"><AppIcon name="robot" :size="24" /></span><strong>围绕当前{{ taskTitle ? '任务' : '项目' }}提问</strong><p>助教会使用绑定的学习目标、进度和已选资料，不会混用其他项目的上下文。</p></div>
        <p v-if="errorMessage || chatStore.error" class="tutor-error">{{ errorMessage || chatStore.error }}</p>
        <article v-for="message in messages" :key="message.id" :class="['tutor-message', `is-${message.role.toLowerCase()}`]">
          <div class="message-bubble"><span v-if="message.role === 'USER'" class="user-copy">{{ message.content }}</span><MarkdownRenderer v-else-if="message.content" :content="message.content" :is-streaming="message.status === 'STREAMING'" /><span v-else>正在思考…</span></div>
          <button v-if="message.role === 'ASSISTANT' && message.id === latestAssistantId && message.status !== 'STREAMING'" class="regenerate" type="button" title="重新生成" @click="regenerate(message)"><AppIcon name="refresh-single" :size="14" />重新生成</button>
        </article>
      </div>
      <div class="quick-actions"><button type="button" @click="quickQuestion('example')">换个例子</button><button type="button" @click="quickQuestion('quiz')">出 3 道题</button><button v-if="taskId" type="button" @click="quickQuestion('hint')">给我提示</button></div>
      <AppInput ref="input" class="tutor-composer" variant="compact" :show-footer-hint="false" :disabled="preparing" :is-streaming="chatStore.sending" :placeholder="taskId ? '继续追问当前任务…' : '问问当前学习项目…'" :media-enabled="true" media-purpose="learning-input" @send="sendQuestion" @stop="chatStore.cancel" />
      <footer><span>AI 会结合当前学习上下文回答</span><button type="button" @click="openFullChat">进入完整对话</button></footer>
    </section>
  </div>
</template>

<style scoped>
.tutor-drawer, .tutor-drawer * { box-sizing: border-box; }.tutor-drawer { position: fixed; inset: 0; z-index: 250; display: flex; justify-content: flex-end; }.tutor-backdrop { position: absolute; inset: 0; border: 0; background: rgba(15, 23, 42, .24); }.tutor-surface { position: relative; width: var(--tutor-width); max-width: calc(100vw - 48px); height: 100%; display: grid; grid-template-rows: auto minmax(0, 1fr) auto auto auto; overflow: hidden; border: 1px solid var(--color-border); border-right: 0; border-radius: 14px 0 0 14px; background: var(--color-surface); box-shadow: -18px 0 42px rgba(15, 23, 42, .14); transition: width .18s ease; }.tutor-surface.resizing { transition: none; }.resize-handle { position: absolute; z-index: 4; top: 14px; bottom: 14px; left: 0; width: 8px; cursor: col-resize; touch-action: none; }.resize-handle::after { position: absolute; top: 50%; left: 2px; width: 3px; height: 44px; border-radius: 99px; background: var(--color-border); content: ''; opacity: 0; transform: translateY(-50%); }.resize-handle:hover::after, .resize-handle:focus-visible::after, .resizing .resize-handle::after { opacity: 1; background: var(--color-text-muted); }
.tutor-header { min-height: 68px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 18px; border-bottom: 1px solid var(--color-border); }.tutor-header > div, .tutor-header > div > span:last-child { display: flex; align-items: center; gap: 10px; }.tutor-header > div > span:last-child { min-width: 0; align-items: flex-start; flex-direction: column; gap: 2px; }.tutor-header small { max-width: 390px; overflow: hidden; color: var(--color-text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.tutor-header button { width: 32px; height: 32px; display: grid; place-items: center; border: 0; border-radius: 8px; color: inherit; background: transparent; cursor: pointer; }.tutor-header button:hover { background: var(--ui-hover-bg); }.tutor-icon { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 50%; color: var(--color-text); background: var(--ui-hover-strong-bg); }.tutor-icon.large { width: 46px; height: 46px; }
.tutor-messages { min-height: 0; overflow: auto; padding: 18px; }.tutor-welcome { min-height: 280px; display: grid; align-content: center; justify-items: center; gap: 9px; text-align: center; }.tutor-welcome p { max-width: 340px; margin: 0; color: var(--color-text-muted); font-size: 13px; line-height: 1.65; }.tutor-error { padding: 9px 11px; border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--color-border)); border-radius: 9px; color: var(--color-danger); font-size: 12px; }.tutor-message { display: grid; justify-items: start; gap: 5px; margin-bottom: 14px; }.tutor-message.is-user { justify-items: end; }.message-bubble { max-width: 88%; padding: 10px 12px; border-radius: 11px; color: var(--color-text); background: var(--ui-hover-bg); font-size: 13px; line-height: 1.65; overflow-wrap: anywhere; }.is-user .message-bubble { color: var(--color-on-primary, #fff); background: var(--color-primary, #303030); }.regenerate { display: flex; align-items: center; gap: 5px; padding: 3px 5px; border: 0; color: var(--color-text-muted); background: transparent; font-size: 11px; cursor: pointer; }.tutor-loading { display: grid; gap: 11px; padding-top: 18px; }.tutor-loading i { height: 46px; border-radius: 10px; background: var(--ui-hover-bg); animation: pulse 1.2s infinite; }.tutor-loading i:nth-child(2) { width: 76%; }.tutor-loading i:nth-child(3) { width: 88%; }
.quick-actions { display: flex; gap: 7px; padding: 0 18px 10px; overflow-x: auto; }.quick-actions button { flex: none; padding: 7px 10px; border: 1px solid var(--color-border); border-radius: 8px; color: inherit; background: transparent; font-size: 11px; cursor: pointer; }.tutor-composer { width: calc(100% - 36px); margin: 0 18px 12px; }.user-copy{display:block;color:inherit;white-space:pre-wrap}.tutor-surface > footer { min-height: 38px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 0 18px; border-top: 1px solid var(--color-border); color: var(--color-text-muted); font-size: 10px; }.tutor-surface > footer button { border: 0; color: var(--color-text); background: transparent; cursor: pointer; }
@keyframes pulse { 50% { opacity: .55; } }
@media (max-width: 700px) { .tutor-surface { width: 100%; max-width: none; border-radius: 0; }.resize-handle { display: none; } }
</style>
