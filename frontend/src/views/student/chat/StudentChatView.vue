<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import {
  ArrowUp, FileImage, FileText, Image, LoaderCircle, Network, Paperclip,
  Plus, Presentation, RotateCcw, Square, Volume2,
} from 'lucide-vue-next'

import ChatArtifactCard from '@/components/artifact/ChatArtifactCard.vue'
import ChatAttachmentList from '@/components/chat/ChatAttachmentList.vue'
import ChatSourceSelector from '@/components/chat/input/ChatSourceSelector.vue'
import VoiceRecorder from '@/components/capture/VoiceRecorder.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import { getAsset, uploadAsset } from '@/api/assetLibraryV2'
import { synthesizeSpeech } from '@/api/chatV2'
import { useAssetLibraryV2Store } from '@/stores/assetLibraryV2'
import { useAuthStore } from '@/stores/auth'
import { useChatV2Store } from '@/stores/chatV2'
import type { Artifact, ChatMessage, MessageAttachment } from '@/types/contracts/chatV2'
import type { LibraryAsset } from '@/types/contracts/assetLibraryV2'

type DraftAttachmentStatus = 'uploading' | 'processing' | 'ready' | 'failed'
type DraftAttachment = {
  key: string
  assetId?: string
  assetVersionId?: string
  assetType?: string
  name: string
  mimeType: string
  sizeBytes: number
  status: DraftAttachmentStatus
  progress: number
  error?: string
  previewUrl?: string
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatV2Store()
const assetLibraryStore = useAssetLibraryV2Store()

const prompt = ref('')
const selectedKnowledgeBaseId = ref<string | null>(null)
const draftAttachments = ref<DraftAttachment[]>([])
const scrollContainer = ref<HTMLElement | null>(null)
const composer = ref<HTMLTextAreaElement | null>(null)
const generalFileInput = ref<HTMLInputElement | null>(null)
const imageFileInput = ref<HTMLInputElement | null>(null)
const mobileAttachmentMenuOpen = ref(false)
const artifactBusyId = ref('')
const voiceError = ref('')
const speechLoadingMessageId = ref('')
const speechPlayingMessageId = ref('')
let speechAbortController: AbortController | null = null
let speechAudio: HTMLAudioElement | null = null
let speechObjectUrl = ''
let destroyed = false

const MAX_ATTACHMENTS = 20
const MAX_FILE_BYTES = 100 * 1024 * 1024

const routeConversationId = computed(() => typeof route.params.id === 'string' ? route.params.id : '')
const hasMessages = computed(() => chatStore.messages.some(message => ['USER', 'ASSISTANT'].includes(message.role)))
const readyAttachments = computed(() => draftAttachments.value.filter(item => item.status === 'ready' && item.assetId))
const hasBusyAttachments = computed(() => draftAttachments.value.some(item => ['uploading', 'processing'].includes(item.status)))
const hasFailedAttachments = computed(() => draftAttachments.value.some(item => item.status === 'failed'))
const canSend = computed(() => (
  !chatStore.sending
  && !chatStore.loading
  && !hasBusyAttachments.value
  && !hasFailedAttachments.value
  && (prompt.value.trim().length > 0 || readyAttachments.value.length > 0)
))

const quickActions = [
  { icon: FileText, title: '生成文档', description: '生成可编辑文档，确认后存入资料库', prompt: '请根据以下要求生成一份可编辑文档：' },
  { icon: Network, title: '生成思维导图', description: '先补充主题或关联资料，再生成结构化导图', prompt: '请根据以下主题生成一份思维导图：' },
  { icon: Presentation, title: '生成 PPT', description: '生成可调整的大纲与演示文稿', prompt: '请根据以下要求生成一份 PPT：' },
  { icon: Image, title: '生成图片', description: '描述画面、比例与使用场景', prompt: '请生成一张图片，画面要求如下：' },
]

function assetStatus(asset: LibraryAsset): DraftAttachmentStatus {
  const status = asset.version?.status || asset.status
  if (status === 'READY') return 'ready'
  if (status === 'FAILED') return 'failed'
  return 'processing'
}

function draftFromAsset(asset: LibraryAsset): DraftAttachment {
  return {
    key: asset.assetId,
    assetId: asset.assetId,
    assetVersionId: asset.version?.versionId,
    assetType: asset.assetType,
    name: asset.name,
    mimeType: asset.version?.mimeType || 'application/octet-stream',
    sizeBytes: asset.version?.sizeBytes || 0,
    status: assetStatus(asset),
    progress: asset.version?.status === 'READY' ? 100 : 0,
  }
}

function revokeDraftPreview(item: DraftAttachment) {
  if (item.previewUrl?.startsWith('blob:')) URL.revokeObjectURL(item.previewUrl)
}

function removeDraftAttachment(key: string) {
  const item = draftAttachments.value.find(candidate => candidate.key === key)
  if (item) revokeDraftPreview(item)
  draftAttachments.value = draftAttachments.value.filter(candidate => candidate.key !== key)
}

function clearDraftAttachments(items: DraftAttachment[] = draftAttachments.value) {
  items.forEach(revokeDraftPreview)
  if (items === draftAttachments.value) draftAttachments.value = []
}

function attachmentItemsForMessage(message: ChatMessage) {
  return (message.attachments ?? []).map(item => ({
    key: item.assetVersionId || item.assetId,
    assetId: item.assetId,
    name: item.name,
    mimeType: item.mimeType,
    sizeBytes: item.sizeBytes,
    status: 'ready' as const,
  }))
}

async function waitUntilAssetSettled(assetId: string, key: string) {
  for (let attempt = 0; attempt < 75 && !destroyed; attempt += 1) {
    const detail = await getAsset(assetId)
    const current = draftAttachments.value.find(item => item.key === key)
    if (!current) return
    current.mimeType = detail.asset.version?.mimeType || current.mimeType
    current.sizeBytes = detail.asset.version?.sizeBytes || current.sizeBytes
    const status = assetStatus(detail.asset)
    current.status = status
    if (status === 'ready') {
      current.progress = 100
      assetLibraryStore.upsertUploadedAsset(detail.asset)
      return
    }
    if (status === 'failed') {
      current.error = '文件处理失败，请移除后重试。'
      return
    }
    await new Promise(resolve => window.setTimeout(resolve, 1200))
  }
  const current = draftAttachments.value.find(item => item.key === key)
  if (current && current.status === 'processing') {
    current.status = 'failed'
    current.error = '文件处理超时，请移除后重试。'
  }
}

async function uploadOne(file: File) {
  const key = crypto.randomUUID()
  const item: DraftAttachment = {
    key,
    name: file.name,
    mimeType: file.type || 'application/octet-stream',
    sizeBytes: file.size,
    status: 'uploading',
    progress: 0,
    previewUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined,
  }
  draftAttachments.value.push(item)
  try {
    const result = await uploadAsset(file, selectedKnowledgeBaseId.value, progress => {
      const current = draftAttachments.value.find(candidate => candidate.key === key)
      if (current) current.progress = progress.percentage
    })
    const current = draftAttachments.value.find(candidate => candidate.key === key)
    if (!current) return
    current.assetId = result.completion.asset.assetId
    current.assetVersionId = result.completion.version.versionId
    current.mimeType = result.completion.version.mimeType || current.mimeType
    current.assetType = current.mimeType.startsWith('image/') ? 'IMAGE' : 'FILE'
    current.sizeBytes = result.completion.version.sizeBytes
    current.status = result.completion.version.status === 'READY' ? 'ready' : 'processing'
    current.progress = 100
    if (result.associationWarning) {
      current.status = 'failed'
      current.error = result.associationWarning
      return
    }
    if (current.status === 'processing') {
      await waitUntilAssetSettled(current.assetId, key)
    } else {
      const detail = await getAsset(current.assetId)
      assetLibraryStore.upsertUploadedAsset(detail.asset)
    }
  } catch (error) {
    const current = draftAttachments.value.find(candidate => candidate.key === key)
    if (current) {
      current.status = 'failed'
      current.error = error instanceof Error ? error.message : '上传失败，请重试。'
    }
  }
}

async function handleFiles(files: FileList | null) {
  mobileAttachmentMenuOpen.value = false
  if (!files?.length) return
  if (!authStore.isAuthed) {
    authStore.openAuthModal(route.fullPath)
    return
  }
  const remaining = MAX_ATTACHMENTS - draftAttachments.value.length
  const accepted = Array.from(files).slice(0, Math.max(remaining, 0))
  const oversized = accepted.filter(file => file.size > MAX_FILE_BYTES)
  const uploadable = accepted.filter(file => file.size <= MAX_FILE_BYTES)
  oversized.forEach(file => {
    draftAttachments.value.push({
      key: crypto.randomUUID(),
      name: file.name,
      mimeType: file.type || 'application/octet-stream',
      sizeBytes: file.size,
      status: 'failed',
      progress: 0,
      error: '单个文件不能超过 100 MB。',
    })
  })
  await Promise.all(uploadable.map(uploadOne))
  if (generalFileInput.value) generalFileInput.value.value = ''
  if (imageFileInput.value) imageFileInput.value.value = ''
}

function openGeneralFilePicker() {
  mobileAttachmentMenuOpen.value = false
  generalFileInput.value?.click()
}

function openImageFilePicker() {
  mobileAttachmentMenuOpen.value = false
  imageFileInput.value?.click()
}

function artifactsFor(message: ChatMessage) {
  if (!message.runId) return []
  return chatStore.artifacts.filter(artifact => artifact.runId === message.runId)
}

function renderMarkdown(content: string) {
  const html = marked.parse(content || '', { async: false }) as string
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } })
}

function applyQuickAction(value: string) {
  prompt.value = value
  nextTick(() => {
    composer.value?.focus()
    resizeComposer()
  })
}

function resizeComposer() {
  const element = composer.value
  if (!element) return
  element.style.height = '0px'
  element.style.height = `${Math.min(Math.max(element.scrollHeight, 48), 180)}px`
}

function handleTranscribed(text: string) {
  const value = text.trim()
  if (!value) return
  prompt.value = prompt.value.trim() ? `${prompt.value.trim()} ${value}` : value
  voiceError.value = ''
  nextTick(() => {
    composer.value?.focus()
    resizeComposer()
  })
}

function stopSpeech() {
  speechAbortController?.abort()
  speechAbortController = null
  speechAudio?.pause()
  speechAudio = null
  if (speechObjectUrl) URL.revokeObjectURL(speechObjectUrl)
  speechObjectUrl = ''
  speechLoadingMessageId.value = ''
  speechPlayingMessageId.value = ''
}

async function toggleSpeech(message: ChatMessage) {
  if (speechPlayingMessageId.value === message.id || speechLoadingMessageId.value === message.id) {
    stopSpeech()
    return
  }
  stopSpeech()
  voiceError.value = ''
  speechLoadingMessageId.value = message.id
  speechAbortController = new AbortController()
  try {
    const audioBlob = await synthesizeSpeech(message.content, speechAbortController.signal)
    speechAbortController = null
    speechObjectUrl = URL.createObjectURL(audioBlob)
    speechAudio = new Audio(speechObjectUrl)
    speechAudio.onended = stopSpeech
    speechAudio.onerror = () => {
      voiceError.value = '回答朗读失败，请稍后重试。'
      stopSpeech()
    }
    speechLoadingMessageId.value = ''
    speechPlayingMessageId.value = message.id
    await speechAudio.play()
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      voiceError.value = error instanceof Error ? error.message : '回答朗读失败，请稍后重试。'
    }
    stopSpeech()
  }
}

async function scrollToBottom(behavior: ScrollBehavior = 'smooth') {
  await nextTick()
  scrollContainer.value?.scrollTo({ top: scrollContainer.value.scrollHeight, behavior })
}

async function applyRouteSources() {
  const knowledgeBaseId = typeof route.query.knowledgeBaseId === 'string' ? route.query.knowledgeBaseId : null
  const sourceAssetIds = typeof route.query.sourceAssetIds === 'string'
    ? route.query.sourceAssetIds.split(',').map(value => value.trim()).filter(Boolean).slice(0, 20)
    : []
  if (knowledgeBaseId) selectedKnowledgeBaseId.value = knowledgeBaseId
  if (!sourceAssetIds.length) return
  const existingIds = new Set(draftAttachments.value.map(item => item.assetId).filter(Boolean))
  const hydrated = await Promise.all(sourceAssetIds
    .filter(assetId => !existingIds.has(assetId))
    .map(assetId => getAsset(assetId).then(detail => draftFromAsset(detail.asset)).catch(() => null)))
  draftAttachments.value.push(...hydrated.filter((item): item is DraftAttachment => item !== null))
}

async function loadConversation(conversationId: string) {
  if (!authStore.isAuthed || !conversationId) return
  if (chatStore.activeConversation?.id === conversationId && chatStore.messages.length) return
  await chatStore.load(conversationId)
  selectedKnowledgeBaseId.value = chatStore.activeConversation?.knowledgeBaseId ?? null
  await scrollToBottom('auto')
}

async function submit() {
  const content = prompt.value.trim()
  if (!canSend.value) return
  if (!authStore.isAuthed) {
    authStore.openAuthModal(route.fullPath)
    return
  }

  const submittedDrafts = [...readyAttachments.value]
  const sourceAssetIds = submittedDrafts.flatMap(item => item.assetId ? [item.assetId] : [])
  const submittedAttachments = optimisticAttachments(submittedDrafts)
  prompt.value = ''
  draftAttachments.value = draftAttachments.value.filter(item => !submittedDrafts.includes(item))
  resizeComposer()
  let optimisticConversationId = ''
  let conversationPersisted = true
  try {
    let conversation = chatStore.activeConversation
    if (!conversation || (routeConversationId.value && routeConversationId.value !== conversation.id)) {
      conversationPersisted = false
      conversation = chatStore.beginConversation({
        title: '新对话',
        knowledgeBaseId: selectedKnowledgeBaseId.value,
      })
      optimisticConversationId = conversation.id
      chatStore.beginOptimisticTurn(content, sourceAssetIds, submittedAttachments)
      await router.replace({ name: 'chat-detail', params: { id: conversation.id } })
      await scrollToBottom('auto')
      conversation = await chatStore.create({
        conversationId: conversation.id,
        title: '新对话',
        knowledgeBaseId: selectedKnowledgeBaseId.value,
      })
      conversationPersisted = true
    } else {
      chatStore.beginOptimisticTurn(content, sourceAssetIds, submittedAttachments)
      await scrollToBottom('auto')
      await chatStore.setKnowledgeBase(selectedKnowledgeBaseId.value)
    }
    await chatStore.send(content, sourceAssetIds, submittedAttachments)
    clearDraftAttachments(submittedDrafts)
    await scrollToBottom()
  } catch {
    if (optimisticConversationId && !conversationPersisted) {
      chatStore.discardConversation(optimisticConversationId)
      await router.replace({ name: 'chat' })
    }
    prompt.value = content
    draftAttachments.value = [...submittedDrafts, ...draftAttachments.value]
    resizeComposer()
  }
}

async function saveArtifact(artifact: Artifact) {
  artifactBusyId.value = artifact.id
  try { await chatStore.saveArtifact(artifact) } finally { artifactBusyId.value = '' }
}

async function confirmArtifact(artifact: Artifact) {
  artifactBusyId.value = artifact.id
  try { await chatStore.confirmArtifact(artifact) } finally { artifactBusyId.value = '' }
}

function openArtifactEditor(artifact: Artifact) {
  void router.push({ name: 'artifact-editor', params: { artifactId: artifact.id } })
}

function openAsset(assetId: string) {
  void router.push({ name: 'resource-preview', params: { resourceId: assetId } })
}

function openCitation(assetId: string) {
  openAsset(assetId)
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    void submit()
  }
}

function optimisticAttachments(items: DraftAttachment[]): MessageAttachment[] {
  return items.flatMap((item) => item.assetId ? [{
    assetId: item.assetId,
    assetVersionId: item.assetVersionId || '',
    name: item.name,
    mimeType: item.mimeType,
    sizeBytes: item.sizeBytes,
    assetType: item.assetType || (item.mimeType.startsWith('image/') ? 'IMAGE' : 'FILE'),
  }] : [])
}

onMounted(async () => {
  await authStore.init()
  await applyRouteSources()
  if (authStore.isAuthed) {
    await chatStore.loadList().catch(() => undefined)
    if (routeConversationId.value) await loadConversation(routeConversationId.value)
  }
})

onBeforeUnmount(() => {
  destroyed = true
  stopSpeech()
  clearDraftAttachments()
})

watch(routeConversationId, async (conversationId) => {
  if (conversationId) await loadConversation(conversationId)
  else chatStore.clearActive()
})

watch(() => chatStore.messages.map(message => message.content.length).join(','), () => {
  void scrollToBottom(chatStore.sending ? 'auto' : 'smooth')
})
</script>

<template>
  <StudentShell>
    <div class="chat-page">
      <div ref="scrollContainer" class="chat-scroll">
        <section v-if="chatStore.loading" class="center-state">
          <LoaderCircle class="spin" :size="24" />
          <span>正在加载对话</span>
        </section>

        <section v-else-if="!hasMessages" class="chat-home">
          <div class="home-heading">
            <h1>今天想完成什么？</h1>
            <p>直接提问，或关联你自己的知识库与资料后开始。</p>
          </div>
          <div class="quick-grid">
            <button
              v-for="action in quickActions"
              :key="action.title"
              type="button"
              @click="applyQuickAction(action.prompt)"
            >
              <span><component :is="action.icon" :size="20" /></span>
              <strong>{{ action.title }}</strong>
              <small>{{ action.description }}</small>
            </button>
          </div>
        </section>

        <section v-else class="message-list" aria-live="polite">
          <template v-for="message in chatStore.messages" :key="message.id">
            <article v-if="message.role === 'USER'" class="message-row user-row">
              <div v-if="message.content" class="user-message">{{ message.content }}</div>
              <ChatAttachmentList
                v-if="message.attachments?.length"
                class="message-attachments user-attachments"
                :items="attachmentItemsForMessage(message)"
                compact
                @open="openAsset"
              />
            </article>
            <article v-else-if="message.role === 'ASSISTANT'" class="message-row assistant-row">
              <ChatAttachmentList
                v-if="message.attachments?.length"
                class="message-attachments"
                :items="attachmentItemsForMessage(message)"
                compact
                @open="openAsset"
              />
              <div class="assistant-message markdown-body" v-html="renderMarkdown(message.content)" />
              <div v-if="message.citations.length" class="citations">
                <span>参考来源</span>
                <button
                  v-for="citation in message.citations"
                  :key="citation.chunkId"
                  type="button"
                  :title="citation.quotedText"
                  @click="openCitation(citation.assetId)"
                >{{ citation.number }}. {{ citation.assetName }}<small v-if="citation.locator">{{ citation.locator }}</small></button>
              </div>
              <div v-if="message.content && message.finalizedAt" class="message-actions">
                <button
                  type="button"
                  :title="speechPlayingMessageId === message.id ? '停止朗读' : '朗读回答'"
                  :aria-label="speechPlayingMessageId === message.id ? '停止朗读' : '朗读回答'"
                  :aria-pressed="speechPlayingMessageId === message.id"
                  @click="toggleSpeech(message)"
                >
                  <LoaderCircle v-if="speechLoadingMessageId === message.id" class="spin" :size="16" />
                  <Square v-else-if="speechPlayingMessageId === message.id" :size="13" fill="currentColor" />
                  <Volume2 v-else :size="16" />
                </button>
              </div>
              <div v-if="artifactsFor(message).length" class="artifact-list">
                <ChatArtifactCard
                  v-for="artifact in artifactsFor(message)"
                  :key="artifact.id"
                  :artifact="artifact"
                  :busy="artifactBusyId === artifact.id"
                  @save="saveArtifact"
                  @confirm="confirmArtifact"
                  @open-editor="openArtifactEditor"
                  @open-asset="openAsset"
                />
              </div>
            </article>
          </template>

          <div v-if="chatStore.sending" class="run-status">
            <LoaderCircle class="spin" :size="16" />
            <span>{{ chatStore.stageText || '正在处理' }}</span>
          </div>
          <div v-if="chatStore.error" class="chat-error">
            <span>{{ chatStore.error }}</span>
            <button type="button" @click="chatStore.error = ''"><RotateCcw :size="15" />关闭</button>
          </div>
        </section>
      </div>

      <div class="composer-dock">
        <div class="composer-context-shell">
          <ChatSourceSelector
            v-model:knowledge-base-id="selectedKnowledgeBaseId"
            :disabled="chatStore.sending"
          />
          <div class="composer-box">
            <ChatAttachmentList
              v-if="draftAttachments.length"
              class="draft-attachments"
              :items="draftAttachments"
              removable
              compact
              @remove="removeDraftAttachment"
              @open="openAsset"
            />
            <textarea
              ref="composer"
              v-model="prompt"
              rows="1"
              placeholder="输入消息"
              aria-label="输入消息"
              @input="resizeComposer"
              @keydown="handleComposerKeydown"
            />
            <p v-if="voiceError" class="voice-error" role="alert">{{ voiceError }}</p>
            <div class="composer-toolbar">
              <div class="attachment-entry">
                <button
                  type="button"
                  class="attachment-button desktop-attachment"
                  :disabled="chatStore.sending || draftAttachments.length >= MAX_ATTACHMENTS"
                  title="上传附件"
                  aria-label="上传附件"
                  @click="openGeneralFilePicker"
                ><Paperclip :size="20" /></button>
                <button
                  type="button"
                  class="attachment-button mobile-attachment"
                  :disabled="chatStore.sending || draftAttachments.length >= MAX_ATTACHMENTS"
                  title="添加附件"
                  aria-label="添加附件"
                  :aria-expanded="mobileAttachmentMenuOpen"
                  @click="mobileAttachmentMenuOpen = !mobileAttachmentMenuOpen"
                ><Plus :size="21" /></button>
                <div v-if="mobileAttachmentMenuOpen" class="mobile-attachment-menu">
                  <button type="button" @click="openGeneralFilePicker"><Paperclip :size="18" />上传文件</button>
                  <button type="button" @click="openImageFilePicker"><FileImage :size="18" />上传图片</button>
                </div>
                <input
                  ref="generalFileInput"
                  class="file-input"
                  type="file"
                  multiple
                  @change="handleFiles(($event.target as HTMLInputElement).files)"
                />
                <input
                  ref="imageFileInput"
                  class="file-input"
                  type="file"
                  accept="image/*"
                  multiple
                  @change="handleFiles(($event.target as HTMLInputElement).files)"
                />
              </div>
              <div class="composer-actions">
                <VoiceRecorder
                  chat-v2
                  :disabled="chatStore.sending"
                  @transcribed="handleTranscribed"
                  @error="voiceError = $event"
                />
                <button v-if="chatStore.sending" type="button" class="send-button" title="停止生成" @click="chatStore.cancel"><Square :size="15" fill="currentColor" /></button>
                <button v-else type="button" class="send-button" :disabled="!canSend" title="发送" @click="submit"><ArrowUp :size="20" /></button>
              </div>
            </div>
          </div>
        </div>
        <small>AI 可能会出错，请核对重要信息。</small>
      </div>
    </div>
  </StudentShell>
</template>

<style scoped>
.chat-page { position: relative; height: 100%; min-height: 0; overflow: hidden; background: var(--color-bg); }
.chat-scroll { height: 100%; overflow: auto; padding: 36px 28px 250px; }
.center-state { display: flex; align-items: center; justify-content: center; gap: 10px; height: 60vh; color: var(--color-text-muted); }
.chat-home { display: grid; gap: 34px; width: min(900px, 100%); margin: min(18vh, 150px) auto 0; }
.home-heading { text-align: center; }
.home-heading h1 { margin: 0; font-size: clamp(30px, 4vw, 44px); font-weight: 560; letter-spacing: -1.4px; }
.home-heading p { margin: 12px 0 0; color: var(--color-text-muted); }
.quick-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.quick-grid button { display: grid; align-content: start; min-height: 150px; padding: 18px; border: 1px solid var(--color-border); border-radius: 20px; color: inherit; background: var(--color-bg); text-align: left; cursor: pointer; transition: transform .16s ease, background .16s ease; }
.quick-grid button:hover { transform: translateY(-2px); background: var(--color-surface); }
.quick-grid button > span { display: grid; width: 38px; height: 38px; margin-bottom: 19px; place-items: center; border-radius: 12px; background: var(--color-surface); }
.quick-grid strong { font-size: 15px; }
.quick-grid small { margin-top: 7px; color: var(--color-text-muted); font-size: 12px; line-height: 1.5; }
.message-list { width: min(820px, 100%); margin: 0 auto; }
.message-row { display: grid; margin: 0 0 30px; }
.user-row { justify-items: end; }
.user-message { max-width: min(72%, 620px); padding: 11px 16px; border-radius: 20px; color: var(--color-bg); background: var(--color-text); line-height: 1.65; white-space: pre-wrap; }
.assistant-row { justify-items: start; }
.message-attachments { margin-top: 8px; }
.user-attachments { justify-self: end; }
.assistant-message { width: 100%; min-height: 24px; line-height: 1.75; }
.assistant-message:empty::after { content: ' '; display: inline-block; width: 7px; height: 18px; border-radius: 2px; background: var(--color-text); animation: blink 1s infinite; }
.citations { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 14px; }
.citations > span { flex-basis: 100%; color: var(--color-text-muted); font-size: 12px; }
.citations button { display: inline-flex; gap: 5px; padding: 7px 10px; border: 1px solid var(--color-border); border-radius: 10px; color: inherit; background: var(--color-bg); cursor: pointer; }
.citations button:hover { background: var(--color-surface); }
.citations small { color: var(--color-text-muted); }
.message-actions { margin-top: 8px; }
.message-actions button { display: grid; width: 30px; height: 30px; padding: 0; place-items: center; border: 0; border-radius: 8px; color: var(--color-text-muted); background: transparent; cursor: pointer; }
.message-actions button:hover { color: var(--color-text); background: var(--color-surface); }
.artifact-list { display: grid; gap: 12px; width: 100%; margin-top: 16px; }
.run-status { display: flex; align-items: center; gap: 8px; margin: 4px 0 24px; color: var(--color-text-muted); font-size: 13px; }
.chat-error { display: flex; justify-content: space-between; gap: 12px; padding: 12px 14px; border-radius: 12px; color: #b42318; background: #fef3f2; }
.chat-error button { display: inline-flex; align-items: center; gap: 5px; border: 0; color: inherit; background: transparent; cursor: pointer; }
.composer-dock { position: absolute; right: 0; bottom: 0; left: 0; padding: 18px 24px 14px; background: linear-gradient(transparent, var(--color-bg) 25%); }
.composer-context-shell {
  width: min(820px, 100%); margin: 0 auto; padding: 4px 8px 0; border-radius: 26px;
  background: color-mix(in srgb, var(--color-text) 9%, var(--color-bg));
  box-shadow: 0 8px 30px rgb(0 0 0 / 8%);
}
.composer-box { margin: 3px -8px 0; padding: 10px 12px 9px; border: 1px solid var(--color-border); border-radius: 25px; background: var(--color-bg); }
.draft-attachments { max-height: 170px; margin: 0 8px 6px; overflow: auto; }
.composer-box textarea { display: block; width: 100%; min-height: 48px; max-height: 180px; resize: none; padding: 9px 8px; border: 0; outline: 0; color: inherit; background: transparent; font: 15px/1.55 inherit; }
.voice-error { margin: 0 8px 8px; color: var(--color-danger); font-size: 12px; }
.composer-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.attachment-entry { position: relative; }
.attachment-button { display: grid; width: 36px; height: 36px; padding: 0; place-items: center; border: 0; border-radius: 50%; color: var(--color-text-muted); background: transparent; cursor: pointer; }
.attachment-button:not(:disabled):hover { color: var(--color-text); background: var(--color-surface); }
.attachment-button:disabled { cursor: default; opacity: .32; }
.mobile-attachment { display: none; }
.mobile-attachment-menu {
  position: absolute; bottom: 44px; left: 0; z-index: 48; display: grid; width: 156px; gap: 3px;
  padding: 7px; border: 1px solid var(--color-border); border-radius: 15px; background: var(--color-bg);
  box-shadow: 0 14px 38px rgb(0 0 0 / 16%);
}
.mobile-attachment-menu button { display: flex; align-items: center; gap: 9px; min-height: 40px; padding: 0 10px; border: 0; border-radius: 10px; color: inherit; background: transparent; font: inherit; cursor: pointer; }
.mobile-attachment-menu button:hover { background: var(--color-surface); }
.file-input { display: none; }
.composer-actions { display: flex; gap: 7px; }
.send-button { display: grid; width: 36px; height: 36px; padding: 0; place-items: center; border: 0; border-radius: 50%; cursor: pointer; }
.send-button { color: var(--color-bg); background: var(--color-text); }
.send-button:disabled { cursor: default; opacity: .25; }
.composer-dock > small { display: block; margin-top: 8px; color: var(--color-text-muted); font-size: 11px; text-align: center; }
.spin { animation: spin .9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes blink { 50% { opacity: .2; } }
@media (max-width: 860px) {
  .quick-grid { grid-template-columns: repeat(2, 1fr); }
  .chat-scroll { padding-right: 18px; padding-left: 18px; }
}
@media (max-width: 560px) {
  .quick-grid { grid-template-columns: 1fr; }
  .quick-grid button { min-height: 114px; }
  .chat-home { margin-top: 40px; }
  .user-message { max-width: 88%; }
  .composer-dock { padding-right: 10px; padding-left: 10px; }
  .desktop-attachment { display: none; }
  .mobile-attachment { display: grid; }
}
</style>
