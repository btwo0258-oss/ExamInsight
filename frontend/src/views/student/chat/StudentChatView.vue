<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowUp, FileText, Image, LoaderCircle, Network, Paperclip,
  MessageSquare, Plus, Presentation, RotateCcw, Square,
} from 'lucide-vue-next'

import ChatArtifactCard from '@/components/artifact/ChatArtifactCard.vue'
import ChatAttachmentList from '@/components/chat/ChatAttachmentList.vue'
import ChatSourceSelector from '@/components/chat/input/ChatSourceSelector.vue'
import MessageList from '@/components/chat/message/MessageList.vue'
import SegmentPanel from '@/components/chat/SegmentPanel.vue'
import ImageCaptureUploader from '@/components/capture/ImageCaptureUploader.vue'
import VoiceRecorder from '@/components/capture/VoiceRecorder.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import {
  addAssetToKnowledgeBase,
  getAsset,
  retryAssetProcessing,
  uploadAsset,
} from '@/api/assetLibraryV2'
import { synthesizeSpeech } from '@/api/chatV2'
import { playSpeechBlob } from '@/utils/speechPlayback'
import { clearPendingPreviewReturn, pendingPreviewReturnFor, rememberChatPreviewReturn } from '@/utils/previewReturn'
import { useAssetLibraryV2Store } from '@/stores/assetLibraryV2'
import { useAuthStore } from '@/stores/auth'
import { useChatV2Store } from '@/stores/chatV2'
import type { Artifact, ArtifactType, ChatMessage, Citation, MessageAttachment } from '@/types/contracts/chatV2'
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
  indexStatus?: string
  progress: number
  error?: string
  previewUrl?: string
  pendingKnowledgeBaseId?: string
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatV2Store()
const assetLibraryStore = useAssetLibraryV2Store()

const prompt = ref('')
const requestedArtifactType = ref<ArtifactType | null>(null)
const selectedKnowledgeBaseId = ref<string | null>(null)
const draftAttachments = ref<DraftAttachment[]>([])
const scrollContainer = ref<HTMLElement | null>(null)
const composerDock = ref<HTMLElement | null>(null)
const composerReserve = ref(260)
const messageList = ref<InstanceType<typeof MessageList> | null>(null)
const activeSegmentIndex = ref(0)
const autoFollowLatest = ref(true)
const restoringPreview = ref(false)
const composer = ref<HTMLTextAreaElement | null>(null)
const generalFileInput = ref<HTMLInputElement | null>(null)
const mobileAttachmentMenuOpen = ref(false)
const artifactBusyId = ref('')
const voiceInputError = ref('')
const speechError = ref('')
const speechErrorMessageId = ref('')
const speechLoadingMessageId = ref('')
const speechPlayingMessageId = ref('')
let speechAbortController: AbortController | null = null
let composerResizeObserver: ResizeObserver | null = null
let destroyed = false
let previewRestoreTask: Promise<boolean> | null = null

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

const quickActions: Array<{ icon: typeof FileText; title: string; description: string; prompt: string; type: ArtifactType }> = [
  { icon: FileText, title: '生成文档', description: '生成可编辑文档，确认后存入资料库', prompt: '请根据以下要求生成一份可编辑文档：', type: 'DOCUMENT' },
  { icon: Network, title: '生成思维导图', description: '先补充主题或关联资料，再生成结构化导图', prompt: '请根据以下主题生成一份思维导图：', type: 'MINDMAP' },
  { icon: Presentation, title: '生成 PPT', description: '生成可调整的大纲与演示文稿', prompt: '请根据以下要求生成一份 PPT：', type: 'PRESENTATION' },
  { icon: Image, title: '生成图片', description: '描述画面、比例与使用场景', prompt: '请生成一张图片，画面要求如下：', type: 'IMAGE' },
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
    indexStatus: asset.version?.indexStatus,
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

async function waitUntilAssetSettled(assetId: string, key: string) {
  for (let attempt = 0; attempt < 75 && !destroyed; attempt += 1) {
    const detail = await getAsset(assetId)
    const current = draftAttachments.value.find(item => item.key === key)
    if (!current) return
    current.mimeType = detail.asset.version?.mimeType || current.mimeType
    current.sizeBytes = detail.asset.version?.sizeBytes || current.sizeBytes
    current.indexStatus = detail.asset.version?.indexStatus
    const status = assetStatus(detail.asset)
    current.status = status
    if (status === 'ready') {
      current.progress = 100
      assetLibraryStore.upsertUploadedAsset(detail.asset)
      if (current.indexStatus !== 'PROCESSING') return
    }
    if (status === 'failed') {
      current.error = '文件处理失败，可以重试。'
      return
    }
    await new Promise(resolve => window.setTimeout(resolve, 1200))
  }
  const current = draftAttachments.value.find(item => item.key === key)
  if (current && current.status === 'processing') {
    current.status = 'failed'
    current.error = '文件处理超时，可以重试。'
  }
}

async function loadConfirmedAsset(assetId: string) {
  for (let attempt = 0; attempt < 8 && !destroyed; attempt += 1) {
    const detail = await getAsset(assetId).catch(() => null)
    if (detail?.asset) return detail.asset
    await new Promise(resolve => window.setTimeout(resolve, 350))
  }
  return null
}

async function retryDraftAttachment(key: string) {
  const current = draftAttachments.value.find(item => item.key === key)
  if (!current?.assetId || current.status === 'uploading' || current.status === 'processing') return
  current.status = 'processing'
  current.error = undefined
  try {
    if (current.pendingKnowledgeBaseId) {
      await addAssetToKnowledgeBase(current.pendingKnowledgeBaseId, current.assetId)
      current.pendingKnowledgeBaseId = undefined
      const detail = await getAsset(current.assetId)
      current.status = assetStatus(detail.asset)
      current.indexStatus = detail.asset.version?.indexStatus
      assetLibraryStore.upsertUploadedAsset(detail.asset)
      if (current.status === 'processing' || current.indexStatus === 'PROCESSING') {
        await waitUntilAssetSettled(current.assetId, key)
      }
      return
    }
    current.indexStatus = 'PROCESSING'
    const detail = await retryAssetProcessing(current.assetId)
    current.status = assetStatus(detail.asset)
    current.indexStatus = detail.asset.version?.indexStatus
    assetLibraryStore.upsertUploadedAsset(detail.asset)
    await waitUntilAssetSettled(current.assetId, key)
  } catch (error) {
    current.status = 'failed'
    current.error = error instanceof Error ? error.message : '重新处理失败。'
  }
}

async function uploadOne(file: File) {
  const key = crypto.randomUUID()
  const targetKnowledgeBaseId = selectedKnowledgeBaseId.value
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
    const result = await uploadAsset(file, targetKnowledgeBaseId, progress => {
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
      current.pendingKnowledgeBaseId = targetKnowledgeBaseId ?? undefined
      const detail = await getAsset(current.assetId)
      current.indexStatus = detail.asset.version?.indexStatus
      assetLibraryStore.upsertUploadedAsset(detail.asset)
      return
    }
    if (current.status === 'processing') {
      await waitUntilAssetSettled(current.assetId, key)
    } else {
      const detail = await getAsset(current.assetId)
      current.status = assetStatus(detail.asset)
      current.indexStatus = detail.asset.version?.indexStatus
      assetLibraryStore.upsertUploadedAsset(detail.asset)
      if (current.indexStatus === 'PROCESSING') {
        await waitUntilAssetSettled(current.assetId, key)
      }
    }
  } catch (error) {
    const current = draftAttachments.value.find(candidate => candidate.key === key)
    if (current) {
      current.status = 'failed'
      current.error = error instanceof Error ? error.message : '上传失败，请重试。'
    }
  }
}

async function handleFiles(files: FileList | File[] | null) {
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
}

function openGeneralFilePicker() {
  mobileAttachmentMenuOpen.value = false
  generalFileInput.value?.click()
}

function artifactsFor(message: ChatMessage) {
  if (message.role !== 'ASSISTANT' || !message.runId) return []
  return chatStore.artifacts.filter(artifact => artifact.runId === message.runId)
}

function applyQuickAction(value: string, type: ArtifactType) {
  prompt.value = value
  requestedArtifactType.value = type
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
  voiceInputError.value = ''
  nextTick(() => {
    composer.value?.focus()
    resizeComposer()
  })
}

function stopSpeech() {
  speechAbortController?.abort()
  speechAbortController = null
  speechLoadingMessageId.value = ''
  speechPlayingMessageId.value = ''
}

watch(routeConversationId, stopSpeech)

function speechTextSegments(content: string) {
  const plainText = content
    .replace(/```[\s\S]*?```/g, '（代码块已省略）')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^\s{0,3}#{1,6}\s+/gm, '')
    .replace(/[>*_`~]/g, '')
    .replace(/\n{2,}/g, '\n')
    .trim()
  const segments: string[] = []
  let remaining = plainText
  while (remaining.length > 520) {
    const window = remaining.slice(0, 520)
    const splitAt = Math.max(window.lastIndexOf('。'), window.lastIndexOf('！'), window.lastIndexOf('？'), window.lastIndexOf('\n'), window.lastIndexOf('. '), window.lastIndexOf('! '), window.lastIndexOf('? '))
    const boundary = splitAt > 180 ? splitAt + 1 : 520
    segments.push(remaining.slice(0, boundary).trim())
    remaining = remaining.slice(boundary).trim()
  }
  if (remaining) segments.push(remaining)
  return segments.filter(Boolean)
}

async function toggleSpeech(message: ChatMessage) {
  if (speechPlayingMessageId.value === message.id || speechLoadingMessageId.value === message.id) {
    stopSpeech()
    return
  }
  stopSpeech()
  speechError.value = ''
  speechErrorMessageId.value = ''
  speechLoadingMessageId.value = message.id
  const controller = new AbortController()
  speechAbortController = controller
  try {
    const segments = speechTextSegments(message.content)
    if (!segments.length) throw new Error('没有可朗读的文本。')
    for (const segment of segments) {
      if (controller.signal.aborted) throw new DOMException('朗读已停止', 'AbortError')
      const audioBlob = await synthesizeSpeech(segment, controller.signal)
      if (controller.signal.aborted) throw new DOMException('朗读已停止', 'AbortError')
      speechLoadingMessageId.value = ''
      speechPlayingMessageId.value = message.id
      await playSpeechBlob(audioBlob, controller.signal)
    }
    if (speechAbortController === controller) stopSpeech()
  } catch (error) {
    if (!controller.signal.aborted && speechAbortController === controller) {
      speechErrorMessageId.value = message.id
      speechError.value = error instanceof Error ? error.message : '朗读服务暂时不可用，请稍后重试。'
      stopSpeech()
    }
    if (controller.signal.aborted && speechAbortController === controller) stopSpeech()
  }
}

async function editMessage(messageId: string, content: string) {
  stopSpeech()
  autoFollowLatest.value = true
  const pending = chatStore.editMessage(messageId, content)
  void scrollToBottom('auto')
  await pending
}

async function regenerateMessage(messageId: string) {
  stopSpeech()
  autoFollowLatest.value = true
  const pending = chatStore.regenerateMessage(messageId)
  void scrollToBottom('auto')
  await pending
}

async function switchMessageVersion(branchId: string) {
  stopSpeech()
  await chatStore.activateMessageBranch(branchId)
}

async function navigateSegment(messageId: string) {
  if (!chatStore.messages.some(message => message.id === messageId)) {
    await chatStore.loadMessagesAround(messageId).catch(() => false)
  }
  await nextTick()
  await messageList.value?.scrollToMessage(messageId)
}

async function loadEarlierMessages() {
  if (!chatStore.hasMoreMessages || chatStore.loadingEarlierMessages) return
  const element = scrollContainer.value
  const previousHeight = element?.scrollHeight ?? 0
  const previousTop = element?.scrollTop ?? 0
  await chatStore.loadEarlierMessages()
  await nextTick()
  if (element) element.scrollTop = previousTop + (element.scrollHeight - previousHeight)
}

async function scrollToBottom(behavior: ScrollBehavior = 'smooth') {
  await nextTick()
  if (destroyed || restoringPreview.value || previewOrigin()) return
  scrollContainer.value?.scrollTo({ top: scrollContainer.value.scrollHeight, behavior })
}

function updateAutoFollowLatest() {
  if (restoringPreview.value || previewOrigin()) return
  const element = scrollContainer.value
  if (!element) return
  autoFollowLatest.value = element.scrollHeight - element.scrollTop - element.clientHeight < 160
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
  const cached = chatStore.activeConversation?.id === conversationId && chatStore.messages.length > 0
  if (!cached) await chatStore.load(conversationId)
  if (destroyed || routeConversationId.value !== conversationId) return
  selectedKnowledgeBaseId.value = chatStore.activeConversation?.knowledgeBaseId ?? null
  // A cached store still mounts a fresh scroll container after preview/editor navigation.
  if (!(await restorePreviewAnchor())) await scrollToBottom('auto')
}

async function submit() {
  const content = prompt.value.trim()
  const generationType = requestedArtifactType.value
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
      chatStore.beginOptimisticTurn(content, sourceAssetIds, submittedAttachments, generationType)
      await router.replace({ name: 'chat-detail', params: { id: conversation.id } })
      await scrollToBottom('auto')
      conversation = await chatStore.create({
        conversationId: conversation.id,
        title: '新对话',
        knowledgeBaseId: selectedKnowledgeBaseId.value,
      })
      conversationPersisted = true
    } else {
      chatStore.beginOptimisticTurn(content, sourceAssetIds, submittedAttachments, generationType)
      await scrollToBottom('auto')
      await chatStore.setKnowledgeBase(selectedKnowledgeBaseId.value)
    }
    await chatStore.send(content, sourceAssetIds, submittedAttachments)
    requestedArtifactType.value = null
    clearDraftAttachments(submittedDrafts)
    await scrollToBottom()
  } catch {
    if (optimisticConversationId && !conversationPersisted) {
      chatStore.discardConversation(optimisticConversationId)
      await router.replace({ name: 'chat' })
    }
    prompt.value = content
    draftAttachments.value = [...submittedDrafts, ...draftAttachments.value]
    requestedArtifactType.value = generationType
    resizeComposer()
  }
}

async function saveArtifact(artifact: Artifact, done?: (success: boolean) => void) {
  artifactBusyId.value = artifact.id
  try {
    await chatStore.saveArtifact(artifact)
    done?.(true)
  } catch (cause) {
    chatStore.error = cause instanceof Error ? cause.message : '保存生成内容失败。'
    done?.(false)
  } finally { artifactBusyId.value = '' }
}

async function confirmArtifact(artifact: Artifact) {
  artifactBusyId.value = artifact.id
  try {
    const confirmed = await chatStore.confirmArtifact(artifact)
    // The conversation card is authoritative as soon as the confirmation API
    // returns. Refresh it immediately; asset metadata can become visible a
    // little later without delaying the user's interaction.
    void chatStore.refreshArtifacts().catch(() => undefined)
    if (confirmed.confirmedAssetId) {
      // Asset creation is eventually consistent. Retry in the background so
      // the card changes immediately while the library receives full metadata
      // as soon as the new asset is readable.
      void loadConfirmedAsset(confirmed.confirmedAssetId).then((confirmedAsset) => {
        if (!confirmedAsset) return
        assetLibraryStore.upsertUploadedAsset(confirmedAsset)
        void assetLibraryStore.loadAssets('library')
          .then(() => assetLibraryStore.upsertUploadedAsset(confirmedAsset))
          .catch(() => undefined)
      })
    }
  } catch (cause) {
    chatStore.error = cause instanceof Error ? cause.message : '确认生成内容失败。'
  } finally { artifactBusyId.value = '' }
}

async function retryArtifact(artifact: Artifact) {
  const sourceMessage = chatStore.messages.find(message => message.runId === artifact.runId)
  if (!sourceMessage) {
    chatStore.error = '找不到原始生成消息，请重新进入对话后重试。'
    return
  }
  try {
    await regenerateMessage(sourceMessage.id)
  } catch {
    // regenerateMessage already exposes the API error through the chat store.
  }
}

function previewOrigin() {
  if (typeof route.query.returnMessageId === 'string' && route.query.returnMessageId) return route.query
  return pendingPreviewReturnFor(route.path)
}

function previewQuery(messageId?: string, artifactId?: string) {
  const messageElement = messageId ? [...(scrollContainer.value?.querySelectorAll<HTMLElement>('[data-message-id]') ?? [])]
    .find(element => element.dataset.messageId === messageId) : undefined
  const artifactElement = artifactId && messageElement
    ? [...messageElement.querySelectorAll<HTMLElement>('[data-artifact-id]')]
      .find(element => element.dataset.artifactId === artifactId)
    : undefined
  const anchor = artifactElement ?? messageElement
  const query = {
    returnTo: route.fullPath,
    ...(messageId ? { returnMessageId: messageId } : {}),
    ...(artifactId ? { returnArtifactId: artifactId } : {}),
    ...(anchor && scrollContainer.value ? {
      returnOffset: String(Math.round(anchor.getBoundingClientRect().top - scrollContainer.value.getBoundingClientRect().top)),
    } : {}),
  }
  if (messageId) rememberChatPreviewReturn(query)
  return query
}

function openArtifactEditor(artifact: Artifact, messageId: string) {
  void router.push({
    name: 'artifact-editor',
    params: { artifactId: artifact.id },
    query: previewQuery(messageId, artifact.id),
  })
}

function openAsset(assetId: string, messageId?: string, artifactId?: string) {
  void router.push({
    name: 'resource-preview',
    params: { resourceId: assetId },
    query: previewQuery(messageId, artifactId),
  })
}

function openCitation(citation: Citation, messageId?: string) {
  const pageMatch = citation.locator?.match(/第\s*(\d+)\s*页/)
  void router.push({
    name: 'resource-preview',
    params: { resourceId: citation.assetId },
    query: {
      ...previewQuery(messageId),
      ...(pageMatch ? { page: pageMatch[1] } : {}),
      citation: String(citation.number),
    },
  })
}

async function restorePreviewAnchor() {
  if (previewRestoreTask) return previewRestoreTask
  const origin = previewOrigin()
  const messageId = origin?.returnMessageId
  const conversationId = routeConversationId.value
  if (typeof messageId !== 'string' || !messageId || chatStore.loading
    || chatStore.activeConversation?.id !== conversationId) return false
  const path = route.path
  const artifactId = typeof origin?.returnArtifactId === 'string' ? origin.returnArtifactId : undefined
  const offset = typeof origin?.returnOffset === 'string' ? Number(origin.returnOffset) : NaN
  autoFollowLatest.value = false
  restoringPreview.value = true
  previewRestoreTask = (async () => {
    // Confirmation updates the store immediately; this also reconciles a preview opened in another tab.
    await chatStore.refreshArtifacts().catch(() => undefined)
    if (!chatStore.messages.some(message => message.id === messageId)) {
      await chatStore.loadMessagesAround(messageId).catch(() => false)
    }
    if (destroyed || routeConversationId.value !== conversationId) return false
    await nextTick()
    const restored = await messageList.value?.scrollToMessage(messageId, 'auto', {
      artifactId,
      offset: Number.isFinite(offset) ? Math.max(-100_000, Math.min(100_000, offset)) : 24,
    })
    if (destroyed || routeConversationId.value !== conversationId) return false
    clearPendingPreviewReturn(path)
    if (route.query.returnMessageId === messageId) {
      const query = { ...route.query }
      delete query.returnMessageId
      delete query.returnArtifactId
      delete query.returnOffset
      await router.replace({ path, query, hash: route.hash })
    }
    return Boolean(restored)
  })()
  try {
    return await previewRestoreTask
  } finally {
    restoringPreview.value = false
    previewRestoreTask = null
  }
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
  composerResizeObserver = new ResizeObserver(() => {
    const height = composerDock.value?.getBoundingClientRect().height ?? 0
    if (height > 0) composerReserve.value = Math.ceil(height + 28)
  })
  if (composerDock.value) composerResizeObserver.observe(composerDock.value)
  await authStore.init()
  await applyRouteSources()
  if (authStore.isAuthed) {
    await chatStore.loadList().catch(() => undefined)
    if (routeConversationId.value) await loadConversation(routeConversationId.value)
  }
})

onBeforeUnmount(() => {
  destroyed = true
  composerResizeObserver?.disconnect()
  composerResizeObserver = null
  stopSpeech()
  clearDraftAttachments()
})

watch(routeConversationId, async (conversationId) => {
  if (conversationId) await loadConversation(conversationId)
  else {
    selectedKnowledgeBaseId.value = null
    chatStore.clearActive()
  }
})

watch(() => route.query.returnMessageId, () => {
  if (routeConversationId.value && !chatStore.loading) void restorePreviewAnchor()
})

watch(() => chatStore.messages.map(message => message.content.length).join(','), () => {
  if (autoFollowLatest.value && !restoringPreview.value && !previewOrigin()) void scrollToBottom(chatStore.sending ? 'auto' : 'smooth')
})
</script>

<template>
  <StudentShell>
    <div class="chat-page" :style="{ '--composer-reserve': `${composerReserve}px` }">
      <header v-if="routeConversationId" class="conversation-header">
        <h1>{{ chatStore.activeConversation?.id === routeConversationId ? chatStore.activeConversation.title : '对话' }}</h1>
      </header>

      <div
        ref="scrollContainer"
        class="chat-scroll"
        :class="{ 'chat-scroll--with-header': Boolean(routeConversationId) }"
        @scroll.passive="updateAutoFollowLatest"
      >
        <section v-if="chatStore.loading" class="center-state">
          <LoaderCircle class="spin" :size="24" />
          <span>正在加载对话</span>
        </section>

        <section v-else-if="routeConversationId && !hasMessages" class="center-state conversation-empty-state">
          <MessageSquare :size="24" />
          <strong>这段对话还没有消息</strong>
          <span>可以在下方继续向 AI 助教提问。</span>
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
              @click="applyQuickAction(action.prompt, action.type)"
            >
              <span><component :is="action.icon" :size="20" /></span>
              <strong>{{ action.title }}</strong>
              <small>{{ action.description }}</small>
            </button>
          </div>
        </section>

        <section v-else class="conversation-content">
          <MessageList
            ref="messageList"
            :messages="chatStore.messages"
            :version-groups="chatStore.versionGroups"
            :busy="chatStore.sending || chatStore.loading"
            :stage-text="chatStore.stageText"
            :speech-loading-message-id="speechLoadingMessageId"
            :speech-playing-message-id="speechPlayingMessageId"
            :speech-error-message-id="speechErrorMessageId"
            :speech-error="speechError"
            :scroll-element="scrollContainer"
            @edit="editMessage"
            @regenerate="regenerateMessage"
            @speak="toggleSpeech"
            @switch-version="switchMessageVersion"
            @open-asset="openAsset"
            @open-citation="openCitation"
            @active-user-index="activeSegmentIndex = $event"
            @reach-top="loadEarlierMessages"
          >
            <template #after-message="{ message }">
              <div v-if="artifactsFor(message).length" class="artifact-list">
                <ChatArtifactCard
                  v-for="artifact in artifactsFor(message)"
                  :key="artifact.id"
                  :artifact="artifact"
                  :busy="artifactBusyId === artifact.id"
                  @save="saveArtifact"
                  @confirm="confirmArtifact"
                  @retry="retryArtifact"
                  @open-editor="(item) => openArtifactEditor(item, message.id)"
                  @open-asset="(assetId) => openAsset(assetId, message.id, artifact.id)"
                />
              </div>
            </template>
          </MessageList>

          <div v-if="chatStore.error" class="chat-error">
            <span>{{ chatStore.error }}</span>
            <button type="button" @click="chatStore.error = ''"><RotateCcw :size="15" />关闭</button>
          </div>
        </section>
      </div>

      <SegmentPanel
        v-if="hasMessages"
        :messages="chatStore.messages"
        :segments="chatStore.segments"
        :active-index="activeSegmentIndex"
        @navigate="navigateSegment"
      />

      <div ref="composerDock" class="composer-dock">
        <div class="composer-context-shell">
          <ChatSourceSelector
            v-model:knowledge-base-id="selectedKnowledgeBaseId"
            :disabled="chatStore.sending"
          />
          <div class="composer-box chat-composer">
            <ChatAttachmentList
              v-if="draftAttachments.length"
              class="draft-attachments"
              :items="draftAttachments"
              removable
              compact
              @remove="removeDraftAttachment"
              @retry="retryDraftAttachment"
              @open="openAsset"
            />
            <textarea
              ref="composer"
              v-model="prompt"
              class="main-textarea"
              rows="1"
              placeholder="输入消息"
              aria-label="输入消息"
              @input="resizeComposer"
              @pointerdown="mobileAttachmentMenuOpen = false"
              @keydown="handleComposerKeydown"
            />
            <p v-if="voiceInputError" class="voice-error" role="alert">{{ voiceInputError }}</p>
            <div class="composer-toolbar">
              <div class="attachment-entry toolbar-left">
                <button
                  type="button"
                  class="attachment-button desktop-attachment"
                  :disabled="chatStore.sending || draftAttachments.length >= MAX_ATTACHMENTS"
                  title="上传附件"
                  aria-label="上传附件"
                  @click="openGeneralFilePicker"
                ><Paperclip :size="20" /></button>
                <div class="mobile-add-control">
                  <div
                    class="mobile-add-pill"
                    :class="{ 'mobile-add-pill--open': mobileAttachmentMenuOpen }"
                  >
                    <div v-if="mobileAttachmentMenuOpen" class="mobile-add-menu" @click.stop>
                      <button
                        class="mobile-menu-action"
                        type="button"
                        title="上传附件"
                        aria-label="上传附件"
                        @click="openGeneralFilePicker"
                      ><Paperclip :size="18" /></button>
                      <ImageCaptureUploader
                        vertical
                        :disabled="chatStore.sending || draftAttachments.length >= MAX_ATTACHMENTS"
                        :remaining-count="MAX_ATTACHMENTS - draftAttachments.length"
                        @select="handleFiles"
                        @error="voiceInputError = $event"
                      />
                    </div>
                    <button
                      type="button"
                      class="attachment-button mobile-attachment mobile-plus"
                      :disabled="chatStore.sending || draftAttachments.length >= MAX_ATTACHMENTS"
                      title="添加附件"
                      aria-label="添加附件"
                      :aria-expanded="mobileAttachmentMenuOpen"
                      @click="mobileAttachmentMenuOpen = !mobileAttachmentMenuOpen"
                    ><Plus :size="21" /></button>
                  </div>
                </div>
                <input
                  ref="generalFileInput"
                  class="file-input"
                  type="file"
                  multiple
                  @change="handleFiles(($event.target as HTMLInputElement).files)"
                />
              </div>
              <div class="composer-actions toolbar-right">
                <VoiceRecorder
                  chat-v2
                  :disabled="chatStore.sending"
                  @transcribed="handleTranscribed"
                  @error="voiceInputError = $event"
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
.chat-page { position: relative; height: 100%; min-height: 0; overflow: hidden; background: color-mix(in srgb, var(--color-text) 3.5%, var(--color-bg)); }
.chat-scroll {
  height: 100%; box-sizing: border-box; overflow: auto; padding: 36px 28px var(--composer-reserve, 260px);
  background: color-mix(in srgb, var(--color-text) 3.5%, var(--color-bg));
  scroll-padding-bottom: var(--composer-reserve, 260px); overscroll-behavior: contain;
}
.conversation-header {
  position: absolute; top: 0; right: 0; left: 0; z-index: 20; display: flex; height: 54px;
  align-items: center; justify-content: center; padding: 0 80px 0 24px; border-bottom: 1px solid var(--color-border);
  color: var(--color-text); background: color-mix(in srgb, var(--color-bg) 90%, transparent);
  backdrop-filter: blur(12px); pointer-events: none;
}
.conversation-header h1 { max-width: min(680px, 100%); margin: 0; overflow: hidden; font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.chat-scroll--with-header { padding-top: 82px; }
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
.conversation-content { width: min(820px, 100%); margin: 0 auto; }
.artifact-list { display: grid; gap: 12px; width: 100%; margin-top: 16px; }
.chat-error { display: flex; justify-content: space-between; gap: 12px; padding: 12px 14px; border-radius: 12px; color: #b42318; background: #fef3f2; }
.chat-error button { display: inline-flex; align-items: center; gap: 5px; border: 0; color: inherit; background: transparent; cursor: pointer; }
.composer-dock { position: absolute; right: 0; bottom: 0; left: 0; z-index: 30; padding: 18px 24px 14px; background: linear-gradient(transparent, color-mix(in srgb, var(--color-text) 3.5%, var(--color-bg)) 25%); }
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
.toolbar-left, .toolbar-right { display: flex; align-items: center; gap: 7px; }
.attachment-button { display: grid; width: 36px; height: 36px; padding: 0; place-items: center; border: 0; border-radius: 50%; color: var(--color-text-muted); background: transparent; cursor: pointer; }
.attachment-button:not(:disabled):hover { color: var(--color-text); background: var(--color-surface); }
.attachment-button:disabled { cursor: default; opacity: .32; }
.mobile-attachment { display: none; }
.mobile-add-control { display: none; position: relative; width: 36px; height: 36px; flex: 0 0 36px; }
.mobile-add-pill {
  position: absolute; bottom: -8px; left: 0; z-index: 48; display: flex; width: 36px;
  flex-direction: column; align-items: center; justify-content: flex-end; padding: 2px;
  overflow: hidden; border: 1px solid transparent; border-radius: 999px;
  background: var(--color-surface); box-sizing: border-box;
  transition: max-height .25s ease, border-color .2s ease, box-shadow .2s ease, background-color .2s ease;
}
.mobile-add-pill--open { max-height: 136px; border-color: var(--color-border); box-shadow: 0 12px 30px rgb(0 0 0 / 16%); }
.mobile-add-menu {
  display: flex; width: 32px; max-height: 104px; flex-direction: column; align-items: center; gap: 4px;
  margin-bottom: 4px; padding: 0; overflow: hidden; opacity: 1;
}
.mobile-menu-action {
  display: inline-flex; width: 32px; height: 32px; flex: 0 0 32px; align-items: center; justify-content: center;
  padding: 0; border: 0; border-radius: 8px; color: var(--color-text-muted); background: transparent; cursor: pointer;
}
.mobile-menu-action:hover { color: var(--color-text); background: var(--color-bg); }
.mobile-add-menu :deep(.image-actions) { display: flex; width: 32px; flex-direction: column; align-items: center; gap: 4px; }
.mobile-add-menu :deep(.image-action) { width: 32px; height: 32px; flex: 0 0 32px; }
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
  .mobile-add-control { display: block; }
  .mobile-attachment { display: grid; }
  .mobile-plus { transform: translateY(-1px); }
}
</style>
