<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import MarkdownRenderer from '@/components/main-area/mode3-chat/message/MarkdownRenderer.vue'
import type { Exercise, LearningPlan, LearningStage, LearningTask } from '@/mock'
import { useLearningTutorStore } from '@/stores/learningTutor'
import { useMessageStore } from '@/stores/message'

const props = withDefaults(defineProps<{
  plan: LearningPlan
  stage?: LearningStage
  task?: LearningTask
  exercise?: Exercise
  mode?: 'inline' | 'drawer'
  open?: boolean
  initialQuestion?: string
}>(), {
  mode: 'inline',
  open: true,
  initialQuestion: '',
})

const emit = defineEmits<{ close: [] }>()
const router = useRouter()
const tutorStore = useLearningTutorStore()
const messageStore = useMessageStore()
const conversationId = ref<number | null>(null)
const question = ref('')
const pendingQuestion = ref('')
const messageArea = ref<HTMLElement | null>(null)
let handledInitialQuestion = ''

const messages = computed(() => {
  if (!conversationId.value) return []
  return (messageStore.byConversation[String(conversationId.value)] ?? [])
    .filter((message) => (message.role === 'user' || message.role === 'assistant') && !message.kind)
    .slice(-12)
})
const hasMessages = computed(() => messages.value.length > 0 || Boolean(pendingQuestion.value))
const contextLabel = computed(() => props.task ? `当前任务 · ${props.task.title}` : `当前项目 · ${props.plan.title}`)

async function ensureReady() {
  conversationId.value = await tutorStore.ensureConversation(props.plan)
}

async function scrollToBottom() {
  await nextTick()
  if (messageArea.value) messageArea.value.scrollTop = messageArea.value.scrollHeight
}

async function sendQuestion(value = question.value) {
  const text = value.trim()
  if (!text || messageStore.isStreaming || pendingQuestion.value) return
  question.value = ''
  pendingQuestion.value = text
  await scrollToBottom()
  try {
    if (!conversationId.value) await ensureReady()
    conversationId.value = await tutorStore.send(props.plan, text, {
      stage: props.stage,
      task: props.task,
      exercise: props.exercise,
    })
  } finally {
    pendingQuestion.value = ''
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
  const id = conversationId.value ?? await tutorStore.ensureConversation(props.plan)
  await router.push({
    path: `/chat/${id}`,
    query: { learningProjectId: String(props.plan.id), tutor: '1' },
  })
}

watch(messages, (items) => {
  if (pendingQuestion.value && items.some((message) => message.role === 'user' && message.content === pendingQuestion.value)) {
    pendingQuestion.value = ''
  }
  void scrollToBottom()
}, { deep: true })
watch(
  () => [props.open, props.initialQuestion] as const,
  async ([open, initialQuestion]) => {
    if (!open) return
    if (initialQuestion && initialQuestion !== handledInitialQuestion) {
      handledInitialQuestion = initialQuestion
      await sendQuestion(initialQuestion)
      return
    }
    await ensureReady()
  },
  { immediate: true },
)
onMounted(ensureReady)
</script>

<template>
  <div v-if="open" :class="['learning-tutor', `learning-tutor--${mode}`]">
    <div v-if="mode === 'drawer'" class="tutor-backdrop" @click="emit('close')" />
    <section class="tutor-surface">
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
        <div v-if="!hasMessages" class="tutor-welcome">
          <AppIcon name="sparkle" :size="22" />
          <strong>{{ task ? '我会结合当前任务回答' : '我会结合整个学习项目回答' }}</strong>
          <p>{{ task ? '可以让我换个例子、生成图解、给出提示，或检查你的理解。' : '可以询问学习顺序、薄弱点、项目进度和资源内容。' }}</p>
        </div>
        <article v-for="message in messages" :key="message.id" :class="['tutor-message', `is-${message.role}`]">
          <div v-if="message.role === 'assistant'" class="assistant-avatar"><AppIcon name="robot" :size="15" /></div>
          <div class="message-bubble">
            <small v-if="message.role === 'user' && message.tutorSource" class="message-source">
              来自：{{ message.tutorSource.label }}
            </small>
            <MarkdownRenderer v-if="message.content" :content="message.content" :is-streaming="message.streaming" />
            <span v-else class="thinking">正在思考<span>...</span></span>
          </div>
        </article>
        <article v-if="pendingQuestion" class="tutor-message is-user is-pending">
          <div class="message-bubble">{{ pendingQuestion }}</div>
        </article>
      </div>

      <div class="tutor-quick-actions">
        <button type="button" @click="quickQuestion('example')">换个例子</button>
        <button type="button" @click="quickQuestion('diagram')">生成图解</button>
        <button type="button" @click="quickQuestion('quiz')">出 3 道题</button>
        <button v-if="task" type="button" @click="quickQuestion('hint')">给我提示</button>
      </div>

      <form class="tutor-composer" @submit.prevent="sendQuestion()">
        <textarea v-model="question" rows="2" :placeholder="task ? '继续追问当前知识点…' : '问问当前学习项目…'" @keydown.enter.exact.prevent="sendQuestion()" />
        <button type="submit" :disabled="!question.trim() || messageStore.isStreaming || Boolean(pendingQuestion)" aria-label="发送问题">
          <AppIcon name="arrow-up" :size="18" />
        </button>
      </form>
      <footer><span>AI 会结合当前学习上下文回答</span><button type="button" @click="openFullChat">进入完整对话</button></footer>
    </section>
  </div>
</template>

<style scoped>
.learning-tutor, .learning-tutor * { box-sizing: border-box; }
.learning-tutor--inline { width: 100%; }
.learning-tutor--drawer { position: fixed; inset: 0; z-index: 60; display: flex; justify-content: flex-end; }
.tutor-backdrop { position: absolute; inset: 0; background: rgba(15, 23, 42, .28); }
.tutor-surface { position: relative; display: flex; flex-direction: column; min-height: 540px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); box-shadow: var(--shadow-sm); overflow: hidden; }
.learning-tutor--drawer .tutor-surface { width: min(620px, 96vw); height: 100%; border-width: 0 0 0 1px; border-radius: 0; box-shadow: -18px 0 42px rgba(15, 23, 42, .14); }
.tutor-header { min-height: 68px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 18px; border-bottom: 1px solid var(--color-border); }
.tutor-header > div, .tutor-header > div > span:last-child { display: flex; align-items: center; gap: 10px; }
.tutor-header > div > span:last-child { min-width: 0; align-items: flex-start; flex-direction: column; gap: 2px; }
.tutor-header small { max-width: 390px; overflow: hidden; color: var(--color-text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.tutor-icon, .assistant-avatar { display: grid; place-items: center; border-radius: 50%; background: color-mix(in srgb, var(--color-info) 12%, var(--color-surface)); color: var(--color-info); }
.tutor-icon { width: 36px; height: 36px; }
.tutor-header-actions { flex-direction: row !important; }
.tutor-header-actions button { width: 32px; height: 32px; display: grid; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--color-text); cursor: pointer; }
.tutor-header-actions button:hover { background: var(--ui-hover-bg); }
.tutor-messages { flex: 1; min-height: 280px; max-height: 520px; overflow: auto; padding: 16px 18px; }
.learning-tutor--drawer .tutor-messages { max-height: none; }
.tutor-welcome { min-height: 190px; display: grid; align-content: center; justify-items: center; gap: 8px; color: var(--color-info); text-align: center; }
.tutor-welcome p { max-width: 310px; margin: 0; color: var(--color-text-muted); font-size: 13px; line-height: 1.6; }
.tutor-message { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 14px; }
.tutor-message.is-user { justify-content: flex-end; }
.assistant-avatar { width: 28px; height: 28px; flex: 0 0 auto; }
.message-bubble { max-width: 88%; border-radius: 10px; background: var(--color-hover); color: var(--color-text); padding: 10px 12px; font-size: 13px; line-height: 1.65; overflow-wrap: anywhere; }
.is-user .message-bubble { background: var(--color-primary); color: var(--color-on-primary); }
.message-source { display: block; margin-bottom: 4px; color: inherit; font-size: 11px; line-height: 1.4; opacity: .72; }
.thinking { color: var(--color-text-muted); }
.tutor-quick-actions { display: flex; flex-wrap: wrap; gap: 8px; padding: 12px 18px 0; border-top: 1px solid var(--color-border); }
.tutor-quick-actions button { min-height: 30px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-surface); color: var(--color-text); padding: 0 9px; cursor: pointer; font-size: 12px; }
.tutor-quick-actions button:hover { background: var(--ui-hover-bg); }
.tutor-composer { position: relative; margin: 10px 18px; }
.tutor-composer textarea { width: 100%; min-height: 72px; resize: none; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); color: var(--color-text); padding: 10px 42px 10px 11px; outline: 0; font: inherit; line-height: 1.5; }
.tutor-composer textarea:focus { border-color: var(--color-primary); }
.tutor-composer button { position: absolute; right: 8px; bottom: 8px; width: 30px; height: 30px; display: grid; place-items: center; border: 0; border-radius: 50%; background: var(--color-primary); color: var(--color-on-primary); cursor: pointer; }
.tutor-composer button:disabled { opacity: .4; cursor: not-allowed; }
.tutor-surface footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 0 18px 14px; color: var(--color-text-muted); font-size: 11px; }
.tutor-surface footer button { border: 0; background: transparent; color: var(--color-info); cursor: pointer; font-weight: 700; }
@media (max-width: 700px) { .learning-tutor--drawer .tutor-surface { width: 100%; } }
</style>
