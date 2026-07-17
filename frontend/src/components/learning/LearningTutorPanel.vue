<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import AppInput from '@/components/common/AppInput.vue'
import MarkdownRenderer from '@/components/chat/message/MarkdownRenderer.vue'
import type { Exercise, LearningPlan, LearningStage, LearningTask } from '@/mock'
import { useLearningTutorStore } from '@/stores/learningTutor'
import { useMessageStore, type ChatMessage } from '@/stores/message'

const DRAWER_WIDTH_STORAGE_KEY = 'examinsight.ui.learning-tutor-drawer-width'
const DEFAULT_DRAWER_WIDTH = 620
const MIN_DRAWER_WIDTH = 420
const MAX_DRAWER_WIDTH = 920

function readDrawerWidth() {
  const raw = localStorage.getItem(DRAWER_WIDTH_STORAGE_KEY)
  if (!raw) return DEFAULT_DRAWER_WIDTH
  const stored = Number(raw)
  return Number.isFinite(stored) ? stored : DEFAULT_DRAWER_WIDTH
}

const props = withDefaults(defineProps<{
  plan: LearningPlan
  stage?: LearningStage
  task?: LearningTask
  exercise?: Exercise
  mode?: 'inline' | 'drawer'
  open?: boolean
  initialQuestion?: string
  initialFiles?: File[]
  initialRequestId?: number
}>(), {
  mode: 'inline',
  open: true,
  initialQuestion: '',
  initialFiles: () => [],
  initialRequestId: 0,
})

const emit = defineEmits<{ close: [] }>()
const router = useRouter()
const tutorStore = useLearningTutorStore()
const messageStore = useMessageStore()
const conversationId = ref<number | null>(null)
const pendingQuestion = ref('')
const pendingFileNames = ref<string[]>([])
const errorMessage = ref('')
const isPreparing = ref(false)
const messageArea = ref<HTMLElement | null>(null)
const drawerWidth = ref(readDrawerWidth())
const isResizing = ref(false)
let handledInitialRequestId = 0
let resizeStartX = 0
let resizeStartWidth = DEFAULT_DRAWER_WIDTH

const messages = computed(() => {
  if (!conversationId.value) return []
  const allMessages = messageStore.byConversation[String(conversationId.value)] ?? []
  const filtered = allMessages.filter((message) => {
    if ((message.role !== 'user' && message.role !== 'assistant') || message.kind) return false
    if (!message.turnId) return true
    const activeQuestion = messageStore.getActiveQVersion(conversationId.value!, message.turnId)
    const questionVersion = message.qVersion ?? 0
    if (message.role === 'user') return questionVersion === activeQuestion
    return questionVersion === activeQuestion
      && (message.aVersion ?? 0) === messageStore.getActiveAVersion(conversationId.value!, message.turnId, activeQuestion)
  })
  const turnStartTimes: Record<string, number> = {}
  allMessages.forEach((message) => {
    if (!message.turnId) return
    const currentStartTime = turnStartTimes[message.turnId]
    if (currentStartTime === undefined || message.createTime < currentStartTime) {
      turnStartTimes[message.turnId] = message.createTime
    }
  })
  return filtered.sort((first, second) => {
    const firstTime = first.turnId ? turnStartTimes[first.turnId]! : first.createTime
    const secondTime = second.turnId ? turnStartTimes[second.turnId]! : second.createTime
    if (firstTime !== secondTime) return firstTime - secondTime
    if (first.role !== second.role) return first.role === 'user' ? -1 : 1
    return first.createTime - second.createTime
  }).slice(-12)
})
const hasMessages = computed(() => messages.value.length > 0 || Boolean(pendingQuestion.value))
const contextLabel = computed(() => props.task ? `当前任务 · ${props.task.title}` : `当前项目 · ${props.plan.title}`)
const latestAssistantId = computed(() => [...messages.value].reverse().find((message) => message.role === 'assistant')?.id)
const drawerSurfaceStyle = computed(() => props.mode === 'drawer'
  ? { '--tutor-drawer-width': `${drawerWidth.value}px` }
  : undefined)

function clampDrawerWidth(width: number) {
  const viewportMaximum = Math.max(320, window.innerWidth - 48)
  const maximum = Math.min(MAX_DRAWER_WIDTH, viewportMaximum)
  const minimum = Math.min(MIN_DRAWER_WIDTH, maximum)
  return Math.max(minimum, Math.min(maximum, width))
}

function resizeDrawer(event: PointerEvent) {
  drawerWidth.value = clampDrawerWidth(resizeStartWidth + resizeStartX - event.clientX)
}

function stopDrawerResize() {
  if (!isResizing.value) return
  isResizing.value = false
  localStorage.setItem(DRAWER_WIDTH_STORAGE_KEY, String(Math.round(drawerWidth.value)))
  document.removeEventListener('pointermove', resizeDrawer)
  document.removeEventListener('pointerup', stopDrawerResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

function startDrawerResize(event: PointerEvent) {
  if (props.mode !== 'drawer' || window.innerWidth <= 700) return
  event.preventDefault()
  resizeStartX = event.clientX
  resizeStartWidth = (event.currentTarget as HTMLElement).parentElement?.getBoundingClientRect().width ?? drawerWidth.value
  isResizing.value = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('pointermove', resizeDrawer)
  document.addEventListener('pointerup', stopDrawerResize)
}

function resizeDrawerByKeyboard(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  event.preventDefault()
  drawerWidth.value = clampDrawerWidth(drawerWidth.value + (event.key === 'ArrowLeft' ? 16 : -16))
  localStorage.setItem(DRAWER_WIDTH_STORAGE_KEY, String(Math.round(drawerWidth.value)))
}

async function ensureReady() {
  if (conversationId.value || isPreparing.value) return
  isPreparing.value = true
  errorMessage.value = ''
  try {
    conversationId.value = await tutorStore.ensureConversation(props.plan)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'AI 助教初始化失败'
  } finally {
    isPreparing.value = false
  }
}

async function scrollToBottom() {
  await nextTick()
  if (messageArea.value) messageArea.value.scrollTop = messageArea.value.scrollHeight
}

async function sendQuestion(
  value = '',
  files: File[] = [],
  complete?: (success?: boolean) => void,
) {
  const text = value.trim()
  if ((!text && files.length === 0) || messageStore.isStreaming || pendingQuestion.value) {
    complete?.(false)
    return
  }
  const pendingText = text || '请分析上传的图片或文件内容'
  pendingQuestion.value = pendingText
  pendingFileNames.value = files.map((file) => file.name)
  let succeeded = false
  await scrollToBottom()
  try {
    if (!conversationId.value) await ensureReady()
    if (!conversationId.value) return
    conversationId.value = await tutorStore.send(props.plan, text, {
      stage: props.stage,
      task: props.task,
      exercise: props.exercise,
    }, files)
    succeeded = true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '消息发送失败'
  } finally {
    pendingQuestion.value = ''
    pendingFileNames.value = []
    complete?.(succeeded)
    await scrollToBottom()
  }
}

function quickQuestion(action: 'example' | 'diagram' | 'quiz' | 'hint') {
  const topic = props.task?.title || props.stage?.title || props.plan.title
  const prompts = {
    example: `请围绕“${topic}”换一个更容易理解的例子，并说明例子和概念如何对应。`,
    diagram: `请为“${topic}”生成一个简洁的 Mermaid 图解，并解释图中关系。`,
    quiz: `请围绕“${topic}”出 3 道由浅入深的练习题，先不要公布答案，等我作答。`,
    hint: `我在“${topic}”这里卡住了。请只给我下一步提示，通过提问引导我思考，不要直接给答案。`,
  }
  void sendQuestion(prompts[action])
}

async function openFullChat() {
  errorMessage.value = ''
  try {
    const id = conversationId.value ?? await tutorStore.ensureConversation(props.plan)
    await router.push({
      path: `/chat/${id}`,
      query: { projectId: String(props.plan.id), tutor: '1' },
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '打开完整对话失败'
  }
}

async function regenerateResponse(message: ChatMessage) {
  if (!conversationId.value || messageStore.isStreaming) return
  errorMessage.value = ''
  try {
    await messageStore.regenerate(conversationId.value, message.turnId || message.id)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '重新生成失败'
  }
}

watch(messages, (items) => {
  if (pendingQuestion.value && items.some((message) => message.role === 'user' && message.content === pendingQuestion.value)) {
    pendingQuestion.value = ''
  }
  void scrollToBottom()
}, { deep: true })
watch(
  () => [props.open, props.initialRequestId] as const,
  async ([open, initialRequestId]) => {
    if (!open) return
    if (initialRequestId && initialRequestId !== handledInitialRequestId) {
      handledInitialRequestId = initialRequestId
      await sendQuestion(props.initialQuestion, props.initialFiles)
      return
    }
    await ensureReady()
  },
  { immediate: true },
)

onBeforeUnmount(stopDrawerResize)
</script>

<template>
  <Transition name="tutor-drawer">
  <div v-if="open" :class="['learning-tutor', `learning-tutor--${mode}`]">
    <div v-if="mode === 'drawer'" class="tutor-backdrop" @click="emit('close')" />
    <section class="tutor-surface" :class="{ 'is-resizing': isResizing }" :style="drawerSurfaceStyle">
      <div
        v-if="mode === 'drawer'"
        class="tutor-resize-handle"
        role="separator"
        aria-label="调整 AI 助教抽屉宽度"
        aria-orientation="vertical"
        tabindex="0"
        @pointerdown="startDrawerResize"
        @keydown="resizeDrawerByKeyboard"
      />
      <header class="tutor-header">
        <div>
          <span class="tutor-icon"><AppIcon name="robot" :size="20" /></span>
          <span><strong>AI 助教</strong><small>{{ contextLabel }}</small></span>
        </div>
        <div class="tutor-header-actions">
          <button type="button" title="进入完整对话" @click="openFullChat"><AppIcon name="maximize" :size="17" /></button>
          <button v-if="mode === 'drawer'" type="button" title="关闭" @click="emit('close')"><AppIcon name="close" :size="18" /></button>
        </div>
      </header>

      <div ref="messageArea" class="tutor-messages">
        <div v-if="errorMessage" class="tutor-error" role="alert">
          <span>{{ errorMessage }}</span>
          <button type="button" @click="ensureReady">重试</button>
        </div>
        <div v-if="!hasMessages" class="tutor-welcome">
          <AppIcon name="sparkle" :size="22" />
          <strong>{{ task ? '我会结合当前任务回答' : '我会结合整个学习项目回答' }}</strong>
          <p>{{ task ? '可以让我换个例子、生成图解、给出提示，或检查你的理解。' : '可以询问学习顺序、薄弱点、项目进度和资源内容。' }}</p>
        </div>
        <article v-for="message in messages" :key="message.id" :class="['tutor-message', `is-${message.role}`]">
          <div v-if="message.role === 'assistant'" class="assistant-avatar"><AppIcon name="robot-black" :size="15" /></div>
          <div class="tutor-message-content">
            <div class="message-bubble">
              <small v-if="message.role === 'user' && message.tutorSource" class="message-source">
                来自：{{ message.tutorSource.label }}
              </small>
              <div v-if="message.role === 'user' && message.files?.length" class="message-files">
                <span v-for="file in message.files" :key="`${message.id}-${file.name}`">
                  <AppIcon name="file" :size="13" />{{ file.name }}
                </span>
              </div>
              <MarkdownRenderer v-if="message.content" :content="message.content" :is-streaming="message.streaming" />
              <span v-else class="thinking">正在思考<span>...</span></span>
            </div>
            <button
              v-if="message.role === 'assistant' && message.id === latestAssistantId && !message.streaming"
              class="regenerate-button"
              type="button"
              :disabled="messageStore.isStreaming"
              title="重新生成"
              @click="regenerateResponse(message)"
            >
              <AppIcon name="refresh-single" :size="14" />
              重新生成
            </button>
          </div>
        </article>
        <article v-if="pendingQuestion" class="tutor-message is-user is-pending">
          <div class="message-bubble">
            <div v-if="pendingFileNames.length" class="message-files">
              <span v-for="fileName in pendingFileNames" :key="fileName"><AppIcon name="file" :size="13" />{{ fileName }}</span>
            </div>
            {{ pendingQuestion }}
          </div>
        </article>
      </div>

      <div class="tutor-quick-actions">
        <button type="button" @click="quickQuestion('example')">换个例子</button>
        <button type="button" @click="quickQuestion('diagram')">生成图解</button>
        <button type="button" @click="quickQuestion('quiz')">出 3 道题</button>
        <button v-if="task" type="button" @click="quickQuestion('hint')">给我提示</button>
      </div>

      <div class="tutor-composer">
        <AppInput
          variant="compact"
          :show-footer-hint="false"
          :disabled="isPreparing || Boolean(pendingQuestion)"
          :is-streaming="messageStore.isStreaming"
          :placeholder="task ? '继续追问当前知识点…' : '问问当前学习项目…'"
          :media-enabled="true"
          media-purpose="learning-input"
          :media-context="{
            conversationId,
            knowledgeBaseId: plan.knowledgeBaseId || null,
            projectId: plan.id,
          }"
          @send="sendQuestion"
          @stop="messageStore.stopStreaming"
        />
      </div>
      <footer><span>AI 会结合当前学习上下文回答</span><button type="button" @click="openFullChat">进入完整对话</button></footer>
    </section>
  </div>
  </Transition>
</template>

<style scoped>
.learning-tutor, .learning-tutor * { box-sizing: border-box; }
.learning-tutor--inline { width: 100%; }
.learning-tutor--drawer { position: fixed; inset: 0; z-index: 250; display: flex; justify-content: flex-end; }
.tutor-backdrop { position: absolute; inset: 0; background: rgba(15, 23, 42, .28); }
.tutor-surface { position: relative; display: flex; flex-direction: column; min-height: 540px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); box-shadow: var(--shadow-sm); overflow: hidden; }
.learning-tutor--drawer .tutor-surface { width: var(--tutor-drawer-width, 620px); max-width: calc(100vw - 48px); height: 100%; border-width: 1px 0 1px 1px; border-radius: 14px 0 0 14px; box-shadow: -18px 0 42px rgba(15, 23, 42, .14); transition: width .18s ease; }
.learning-tutor--drawer .tutor-surface.is-resizing { transition: none; }
.tutor-resize-handle { position: absolute; z-index: 5; top: 14px; bottom: 14px; left: 0; width: 8px; cursor: col-resize; touch-action: none; outline: 0; }
.tutor-resize-handle::after { content: ''; position: absolute; top: 50%; left: 2px; width: 3px; height: 44px; border-radius: 999px; background: var(--color-border); opacity: 0; transform: translateY(-50%); transition: opacity .18s ease, background .18s ease; }
.tutor-resize-handle:hover::after, .tutor-resize-handle:focus-visible::after, .is-resizing .tutor-resize-handle::after { opacity: 1; background: var(--color-text-muted); }
.tutor-header { min-height: 68px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 18px; border-bottom: 1px solid var(--color-border); }
.tutor-header > div, .tutor-header > div > span:last-child { display: flex; align-items: center; gap: 10px; }
.tutor-header > div > span:last-child { min-width: 0; align-items: flex-start; flex-direction: column; gap: 2px; }
.tutor-header small { max-width: 390px; overflow: hidden; color: var(--color-text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.tutor-icon, .assistant-avatar { display: grid; place-items: center; border-radius: 50%; background: var(--color-hover); color: var(--color-text); }
.tutor-icon { width: 36px; height: 36px; }
.tutor-header-actions { flex-direction: row !important; }
.tutor-header-actions button { width: 32px; height: 32px; display: grid; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--color-text); cursor: pointer; }
.tutor-header-actions button:hover { background: var(--ui-hover-bg); }
.tutor-messages { flex: 1; min-height: 280px; max-height: 520px; overflow: auto; padding: 16px 18px; }
.learning-tutor--drawer .tutor-messages { max-height: none; }
.tutor-welcome { min-height: 190px; display: grid; align-content: center; justify-items: center; gap: 8px; color: var(--color-text); text-align: center; }
.tutor-welcome p { max-width: 310px; margin: 0; color: var(--color-text-muted); font-size: 13px; line-height: 1.6; }
.tutor-error { min-height: 36px; margin-bottom: 12px; padding: 8px 10px; display: flex; align-items: center; justify-content: space-between; gap: 8px; border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--color-border)); border-radius: 8px; color: var(--color-danger); font-size: 12px; }
.tutor-error button { border: 0; background: transparent; color: inherit; cursor: pointer; }
.tutor-message { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 14px; }
.tutor-message.is-user { justify-content: flex-end; }
.assistant-avatar { width: 28px; height: 28px; flex: 0 0 auto; }
.tutor-message-content { max-width: 88%; display: grid; justify-items: start; gap: 5px; }
.message-bubble { max-width: 88%; border-radius: 10px; background: var(--color-hover); color: var(--color-text); padding: 10px 12px; font-size: 13px; line-height: 1.65; overflow-wrap: anywhere; }
.tutor-message-content .message-bubble { max-width: 100%; }
.is-user .message-bubble { background: var(--color-primary); color: var(--color-on-primary); }
.message-source { display: block; margin-bottom: 4px; color: inherit; font-size: 11px; line-height: 1.4; opacity: .72; }
.message-files { display: flex; flex-wrap: wrap; gap: 5px; margin-bottom: 6px; }
.message-files span { max-width: 220px; min-height: 24px; display: inline-flex; align-items: center; gap: 4px; overflow: hidden; border: 1px solid color-mix(in srgb, currentColor 22%, transparent); border-radius: 6px; padding: 2px 6px; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.regenerate-button { min-height: 26px; display: inline-flex; align-items: center; gap: 5px; border: 0; border-radius: 6px; background: transparent; color: var(--color-text-muted); padding: 0 7px; cursor: pointer; font-size: 11px; }
.regenerate-button:hover:not(:disabled) { background: var(--ui-hover-bg); color: var(--color-text); }
.regenerate-button:disabled { opacity: .45; cursor: not-allowed; }
.thinking { color: var(--color-text-muted); }
.tutor-quick-actions { display: flex; flex-wrap: wrap; gap: 8px; padding: 12px 18px 0; border-top: 1px solid var(--color-border); }
.tutor-quick-actions button { min-height: 30px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-surface); color: var(--color-text); padding: 0 9px; cursor: pointer; font-size: 12px; }
.tutor-quick-actions button:hover { background: var(--ui-hover-bg); }
.tutor-composer { position: relative; z-index: 3; margin: 16px 18px 10px; }
.tutor-surface footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 0 18px 14px; color: var(--color-text-muted); font-size: 11px; }
.tutor-surface footer button { border: 0; border-radius: 6px; background: transparent; color: var(--color-text); cursor: pointer; padding: 5px 7px; font-weight: 700; }
.tutor-surface footer button:hover { background: var(--ui-hover-bg); }
.tutor-drawer-enter-active, .tutor-drawer-leave-active { transition: visibility .34s; }
.tutor-drawer-enter-active .tutor-backdrop, .tutor-drawer-leave-active .tutor-backdrop { transition: opacity .22s ease; }
.learning-tutor--drawer.tutor-drawer-enter-active .tutor-surface, .learning-tutor--drawer.tutor-drawer-leave-active .tutor-surface { transition: width .18s ease, transform .34s cubic-bezier(.4, 0, .2, 1), opacity .24s ease; }
.tutor-drawer-enter-from .tutor-backdrop, .tutor-drawer-leave-to .tutor-backdrop { opacity: 0; }
.learning-tutor--drawer.tutor-drawer-enter-from .tutor-surface, .learning-tutor--drawer.tutor-drawer-leave-to .tutor-surface { opacity: .76; transform: translateX(32px); }
@media (max-width: 700px) {
  .learning-tutor--drawer .tutor-surface { width: 100%; max-width: none; border-width: 0; border-radius: 0; }
  .tutor-resize-handle { display: none; }
}
@media (prefers-reduced-motion: reduce) {
  .tutor-drawer-enter-active,
  .tutor-drawer-leave-active,
  .tutor-drawer-enter-active .tutor-backdrop,
  .tutor-drawer-leave-active .tutor-backdrop,
  .learning-tutor--drawer.tutor-drawer-enter-active .tutor-surface,
  .learning-tutor--drawer.tutor-drawer-leave-active .tutor-surface {
    transition-duration: 0.01ms !important;
  }
}
</style>
