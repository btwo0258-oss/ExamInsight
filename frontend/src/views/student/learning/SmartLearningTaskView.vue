<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StudentShell from '@/components/layout/StudentShell.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import MarkdownRenderer from '@/components/chat/message/MarkdownRenderer.vue'
import SmartLearningTutorDrawer from '@/components/learning/SmartLearningTutorDrawer.vue'
import { useSmartLearningStore } from '@/stores/smartLearning'

const route = useRoute()
const router = useRouter()
const store = useSmartLearningStore()
const projectId = String(route.params.id)
const taskId = String(route.params.taskId)
const loading = ref(true)
const actionError = ref('')
const offline = ref(typeof navigator !== 'undefined' && !navigator.onLine)
const tutorOpen = ref(false)
const answerDraft = ref<Record<string, unknown>>({})
const reading = ref<HTMLElement | null>(null)
let heartbeatTimer: number | undefined
let progressTimer: number | undefined
let sequence = 0
let checkpointTimer: number | undefined

const checkpointKey = `examinsight.learning.execution.${projectId}.${taskId}`
type LocalCheckpoint = {
  executionId: string
  answers: Record<string, unknown>
  progress: number
  position: Record<string, unknown>
  savedAt: number
}

function readCheckpoint(): LocalCheckpoint | null {
  try {
    const raw = localStorage.getItem(checkpointKey)
    if (!raw) return null
    const value = JSON.parse(raw) as Partial<LocalCheckpoint>
    if (typeof value.executionId !== 'string') return null
    return {
      executionId: value.executionId,
      answers: value.answers && typeof value.answers === 'object' ? value.answers as Record<string, unknown> : {},
      progress: Number(value.progress) || 0,
      position: value.position && typeof value.position === 'object' ? value.position as Record<string, unknown> : {},
      savedAt: Number(value.savedAt) || 0,
    }
  } catch { return null }
}

function writeCheckpoint() {
  if (!execution.value) return
  try {
    localStorage.setItem(checkpointKey, JSON.stringify({
      executionId: execution.value.executionId,
      answers: answerDraft.value,
      progress: progress(),
      position: currentPosition(),
      savedAt: Date.now(),
    } satisfies LocalCheckpoint))
  } catch { /* local persistence is a best-effort offline fallback */ }
}

function currentPosition() {
  return task.value?.taskType === 'EXERCISE'
    ? { kind: 'exercise' }
    : { kind: 'reading', scrollTop: reading.value?.scrollTop || 0 }
}

const task = computed(() => store.currentTask)
const execution = computed(() => task.value?.execution)
const readingMarkdown = computed(() => {
  const resource = task.value?.resources.find(item => item.kind === 'READING' && item.status === 'READY')
  return typeof resource?.content.markdown === 'string' ? resource.content.markdown : ''
})
const exerciseItems = computed(() => {
  const resource = task.value?.resources.find(item => item.kind === 'EXERCISE_SET' && item.status === 'READY')
  return Array.isArray(resource?.content.items) ? resource.content.items as Array<Record<string, unknown>> : []
})
const sourceAssetIds = computed(() => {
  const assets = store.current?.sources?.assets
  return Array.isArray(assets) ? assets.map(item => typeof item === 'string' ? item : String((item as Record<string, unknown>).assetId ?? '')).filter(Boolean) : []
})
function optionsOf(item: Record<string, unknown>) {
  return Array.isArray(item.options) ? item.options.map(value => String(value)) : []
}

async function load() {
  loading.value = true
  actionError.value = ''
  try {
    await store.fetchProject(projectId)
    await store.fetchTask(projectId, taskId)
    if (!execution.value || execution.value.status === 'PAUSED') await store.startExecution(projectId, taskId)
    const checkpoint = readCheckpoint()
    const usableCheckpoint = checkpoint && checkpoint.executionId === execution.value?.executionId ? checkpoint : null
    const remoteAnswers = execution.value?.answers ?? {}
    answerDraft.value = usableCheckpoint && usableCheckpoint.savedAt > 0 && Object.keys(remoteAnswers).length === 0
      ? { ...usableCheckpoint.answers }
      : { ...remoteAnswers }
    sequence = execution.value?.lastHeartbeatSeq ?? 0
    await nextTick()
    const position = execution.value?.position && Object.keys(execution.value.position).length ? execution.value.position : usableCheckpoint?.position ?? {}
    if (reading.value && position.kind === 'reading') reading.value.scrollTop = Math.max(0, Number(position.scrollTop) || 0)
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '学习任务加载失败。'
  } finally { loading.value = false }
}

function progress() {
  if (task.value?.taskType === 'EXERCISE') {
    const total = exerciseItems.value.length
    return total ? Object.keys(answerDraft.value).filter(key => String(answerDraft.value[key]).trim()).length / total * 100 : 0
  }
  const el = reading.value
  if (!el || el.scrollHeight <= el.clientHeight + 4) return 100
  return Math.min(100, el.scrollTop / (el.scrollHeight - el.clientHeight) * 100)
}

async function saveProgress() {
  if (!execution.value || ['COMPLETED', 'SKIPPED'].includes(execution.value.status)) return
  const value = progress()
  writeCheckpoint()
  try { await store.saveExecutionProgress(execution.value.executionId, value, 0) } catch { /* retry after reconnect */ }
}

async function saveAnswers() {
  if (!execution.value) return
  writeCheckpoint()
  try { await store.saveExecutionAnswers(execution.value.executionId, answerDraft.value) } catch (error) {
    if (!offline.value) actionError.value = error instanceof Error ? error.message : '答案保存失败，已暂存在本机。'
  }
}

async function savePosition() {
  if (!execution.value || ['COMPLETED', 'SKIPPED'].includes(execution.value.status)) return
  const position = currentPosition()
  writeCheckpoint()
  try { await store.saveExecutionPosition(execution.value.executionId, position) } catch { /* retry after reconnect */ }
}

function scheduleCheckpoint() {
  if (checkpointTimer) window.clearTimeout(checkpointTimer)
  checkpointTimer = window.setTimeout(() => { void savePosition() }, 250)
}

function setOffline() { offline.value = true }
function setOnline() {
  offline.value = false
  actionError.value = ''
  void saveAnswers()
  void saveProgress()
  void savePosition()
}

async function togglePause() {
  if (!execution.value) return
  actionError.value = ''
  try {
    if (execution.value.status === 'PAUSED') await store.resumeExecution(execution.value.executionId)
    else await store.pauseExecution(execution.value.executionId)
  } catch (error) { actionError.value = error instanceof Error ? error.message : '更新学习状态失败。' }
}

async function complete() {
  if (!execution.value) return
  actionError.value = ''
  try { await saveAnswers(); await store.saveExecutionProgress(execution.value.executionId, 100, 0); await store.completeExecution(execution.value.executionId) }
  catch (error) { actionError.value = error instanceof Error ? error.message : '完成任务失败。' }
}

onMounted(async () => {
  await load()
  window.addEventListener('offline', setOffline)
  window.addEventListener('online', setOnline)
  heartbeatTimer = window.setInterval(() => {
    if (!execution.value || execution.value.status !== 'IN_PROGRESS' || document.visibilityState !== 'visible') return
    sequence += 1
    void store.heartbeatExecution(execution.value.executionId, sequence, 5)
  }, 5000)
  progressTimer = window.setInterval(() => { void saveProgress(); void savePosition() }, 4000)
})
onBeforeUnmount(() => {
  if (heartbeatTimer) window.clearInterval(heartbeatTimer)
  if (progressTimer) window.clearInterval(progressTimer)
  if (checkpointTimer) window.clearTimeout(checkpointTimer)
  window.removeEventListener('offline', setOffline)
  window.removeEventListener('online', setOnline)
  writeCheckpoint()
  void saveProgress(); void saveAnswers(); void savePosition()
})
</script>

<template>
  <StudentShell>
    <main class="task-page">
      <header class="task-header"><button class="back-button" type="button" aria-label="返回学习工作台" @click="router.push(`/learning/${projectId}`)"><AppIcon name="chevron-left" :size="19" /></button><div><span class="eyebrow">学习任务</span><h1>{{ task?.title || '任务' }}</h1><p>{{ task?.durationMinutes || 30 }} 分钟 · {{ task?.taskType === 'EXERCISE' ? '练习' : '阅读与理解' }}</p></div><div class="task-header-actions"><button class="outline-button" type="button" @click="tutorOpen = true">问 AI 助教</button><button class="outline-button" type="button" :disabled="!execution" @click="togglePause">{{ execution?.status === 'PAUSED' ? '继续学习' : '暂停学习' }}</button></div></header>
      <p v-if="actionError" class="task-error" role="alert">{{ actionError }}</p>
      <p v-if="offline" class="task-offline" role="status">当前处于离线状态，修改会暂存在本机，恢复连接后自动同步。</p>
      <section v-if="loading" class="task-loading"><span class="loading-bar" /><span class="loading-bar short" /><span class="loading-block" /></section>
      <section v-else-if="task" class="task-layout">
        <article ref="reading" class="task-content" @scroll.passive="scheduleCheckpoint"><div class="task-intro"><span>完成标准</span><p>{{ task.completionCriteria }}</p></div><MarkdownRenderer v-if="readingMarkdown" :content="readingMarkdown" /><div v-else-if="task.resources.some(item => item.status === 'GENERATING' || item.status === 'QUEUED')" class="resource-pending">学习资源正在准备，完成后会自动出现在这里。</div><div v-else-if="task.resources.some(item => item.status === 'FAILED')" class="resource-failed">资源准备失败，可以返回工作台重试。</div><section v-if="exerciseItems.length" class="exercise-list"><h2>练习题</h2><article v-for="(item, index) in exerciseItems" :key="String(item.id || index)" class="exercise-item"><strong>{{ index + 1 }}. {{ item.stem }}</strong><label v-for="option in optionsOf(item)" :key="option"><input v-model="answerDraft[String(item.id || index)]" type="radio" :name="String(item.id || index)" :value="option" @change="saveAnswers" /> {{ option }}</label></article></section></article>
        <aside class="task-aside"><div class="progress-card"><span>当前进度</span><strong>{{ Math.round(execution?.progress || 0) }}%</strong><i><b :style="{ width: `${execution?.progress || 0}%` }" /></i><small>有效学习 {{ Math.floor((execution?.accumulatedSeconds || 0) / 60) }} 分钟</small></div><button class="complete-button" type="button" :disabled="execution?.status === 'COMPLETED'" @click="complete">{{ execution?.status === 'COMPLETED' ? '已完成' : '完成任务' }}</button></aside>
      </section>
    </main>
    <SmartLearningTutorDrawer v-if="tutorOpen" :open="tutorOpen" :project-id="projectId" :project-name="store.current?.name || '学习项目'" :task-title="task?.title" :source-asset-ids="sourceAssetIds" @close="tutorOpen = false" />
  </StudentShell>
</template>

<style scoped>
.task-page { min-height: 100%; padding: 28px 34px 90px; background: var(--color-bg, #f7f7f5); color: var(--color-text, #202124); }.task-header { width: min(1280px, 100%); margin: 0 auto 22px; display: grid; grid-template-columns: 42px minmax(0, 1fr) auto; gap: 14px; align-items: center; }.back-button { width: 40px; height: 40px; display: grid; place-items: center; border: 1px solid var(--color-border, #e2e2df); border-radius: 10px; background: var(--color-surface, #fff); cursor: pointer; }.eyebrow { color: var(--color-text-muted, #707070); font-size: 12px; font-weight: 700; }.task-header h1 { margin: 4px 0 2px; font-size: 26px; }.task-header p { margin: 0; color: var(--color-text-muted, #707070); }.task-header-actions { display: flex; gap: 8px; }.outline-button, .complete-button { border-radius: 10px; padding: 10px 15px; font: inherit; cursor: pointer; }.outline-button { border: 1px solid var(--color-border, #d8d8d5); background: var(--color-surface, #fff); color: inherit; }.task-error, .task-offline { width: min(1280px, 100%); margin: 0 auto 12px; padding: 11px 14px; border-radius: 10px; }.task-error { border: 1px solid #efc2bb; color: #b43b2f; background: #fff4f2; }.task-offline { border: 1px solid var(--color-border, #e2e2df); color: var(--color-text-muted, #707070); background: var(--color-hover, #f2f2ef); }.task-layout { width: min(1280px, 100%); margin: auto; display: grid; grid-template-columns: minmax(0, 1fr) 250px; gap: 18px; align-items: start; }.task-content, .progress-card { border: 1px solid var(--color-border, #e2e2df); border-radius: 15px; background: var(--color-surface, #fff); }.task-content { max-height: calc(100vh - 190px); overflow: auto; padding: 28px 32px; }.task-intro { margin-bottom: 22px; padding-bottom: 15px; border-bottom: 1px solid var(--color-border, #e2e2df); }.task-intro span { color: var(--color-text-muted, #707070); font-size: 12px; }.task-intro p { margin: 6px 0 0; }.resource-pending, .resource-failed { padding: 48px 0; text-align: center; color: var(--color-text-muted, #707070); }.resource-failed { color: #b43b2f; }.task-aside { position: sticky; top: 18px; display: grid; gap: 10px; }.progress-card { padding: 18px; }.progress-card span, .progress-card small { display: block; color: var(--color-text-muted, #707070); font-size: 12px; }.progress-card strong { display: block; margin: 8px 0 10px; font-size: 26px; }.progress-card i { display: block; height: 7px; overflow: hidden; border-radius: 999px; background: var(--color-border, #e2e2df); }.progress-card b { display: block; height: 100%; border-radius: inherit; background: #303030; }.progress-card small { margin-top: 10px; }.complete-button { border: 0; color: #fff; background: #303030; }.complete-button:disabled { opacity: .55; cursor: default; }.task-loading { width: min(1280px, 100%); margin: auto; padding: 30px; border: 1px solid var(--color-border, #e2e2df); border-radius: 15px; background: var(--color-surface, #fff); }.loading-bar, .loading-block { display: block; height: 14px; margin-bottom: 12px; border-radius: 8px; background: linear-gradient(90deg,#eee,#f7f7f7,#eee); animation: shimmer 1.2s infinite; }.loading-bar { width: 60%; }.loading-bar.short { width: 35%; }.loading-block { height: 260px; width: 100%; }.exercise-list { margin-top: 24px; }.exercise-item { display: grid; gap: 10px; padding: 15px 0; border-top: 1px solid var(--color-border, #e2e2df); }.exercise-item label { color: var(--color-text-muted, #707070); }@keyframes shimmer { 0% { opacity: .65; } 50% { opacity: 1; } 100% { opacity: .65; } }
@media (max-width: 780px) { .task-page { padding-inline: 16px; }.task-header { grid-template-columns: 42px 1fr; }.task-header-actions { grid-column: 1 / -1; }.task-layout { grid-template-columns: 1fr; }.task-aside { position: static; }.task-content { max-height: none; padding: 22px 18px; } }
</style>
