<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import StudentShell from '@/components/layout/StudentShell.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
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
let resourcePollTimer: number | undefined
let routeLeaving = false

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
  return typeof resource?.content.markdown === 'string' ? normalizeLearningMarkdown(resource.content.markdown) : ''
})
const exerciseItems = computed(() => {
  const resource = task.value?.resources.find(item => item.kind === 'EXERCISE_SET' && item.status === 'READY')
  const items = Array.isArray(resource?.content.items) ? resource.content.items as Array<Record<string, unknown>> : []
  const configured = Number(task.value?.payload.questionCount || store.current?.resourceConfig?.questionCount || resource?.content.questionCount || 0)
  return configured > 0 ? items.slice(0, configured) : items
})
const sourceAssetIds = computed(() => {
  const assets = store.current?.sources?.assets
  return Array.isArray(assets) ? assets.map(item => typeof item === 'string' ? item : String((item as Record<string, unknown>).assetId ?? '')).filter(Boolean) : []
})
function optionsOf(item: Record<string, unknown>) {
  return Array.isArray(item.options) ? item.options.map(value => String(value)) : []
}

function textOf(value: unknown) { return String(value ?? '') }
function looksLikeCode(value: unknown) {
  const text = textOf(value)
  return text.includes('\n') && /[{};]|=>|<\/?[a-z][^>]*>/i.test(text)
}
function questionText(item: Record<string, unknown>) { return looksLikeCode(item.stem) ? '' : textOf(item.stem) }
function questionCode(item: Record<string, unknown>) { return looksLikeCode(item.stem) ? textOf(item.stem) : '' }

function questionIdOf(item: Record<string, unknown>, index: number) {
  const id = String(item.id || `q${index + 1}`)
  return `${id}__${index}`
}

function questionName(index: number) {
  return `exercise-${execution.value?.executionId || taskId}-${index}`
}

const submittedGrade = computed(() => execution.value?.grading || null)
const exerciseSubmitted = computed(() => Boolean(submittedGrade.value || execution.value?.status === 'COMPLETED'))
const unansweredIndexes = computed(() => exerciseItems.value.map((item, index) => ({ item, index })).filter(({ item, index }) => !String(answerDraft.value[questionIdOf(item, index)] ?? '').trim()).map(({ index }) => index))
const answeredCount = computed(() => exerciseItems.value.length - unansweredIndexes.value.length)
const exerciseAccuracy = computed(() => submittedGrade.value?.accuracy ?? execution.value?.score ?? null)
const gradeByIndex = computed(() => new Map((submittedGrade.value?.items || []).map(item => [item.index, item])))
const submitConfirmOpen = ref(false)
const submitMessage = ref('')
const submitting = ref(false)

function normalizeLearningMarkdown(raw: string) {
  let value = raw.replace(/\r\n?/g, '\n').trim()
  if (/^```(?:markdown|md)\s*\n[\s\S]*\n```$/i.test(value)) {
    value = value.replace(/^```(?:markdown|md)\s*\n/i, '').replace(/\n```$/, '').trim()
  }
  const fenceCount = value.split('\n').filter(line => line.trim().startsWith('```')).length
  if (fenceCount % 2) value += '\n```'
  return value
}

const taskTypeLabel = computed(() => ({
  READING: '阅读与理解',
  EXERCISE: '练习与作答',
  REVIEW: '复盘与巩固',
  EXPLANATION: '讲解与理解',
}[task.value?.taskType || ''] ?? '学习任务'))
const activeExecution = computed(() => ['IN_PROGRESS', 'PAUSED'].includes(execution.value?.status || ''))
const isReviewMode = computed(() => route.query.review === '1'
  || Boolean(task.value?.status === 'COMPLETED' && activeExecution.value))
const isEarlyMode = computed(() => route.query.early === '1'
  || Boolean(task.value?.scheduledDate && task.value.scheduledDate > new Date().toISOString().slice(0, 10) && activeExecution.value))
const learningModeLabel = computed(() => isReviewMode.value
  ? '复习模式'
  : isEarlyMode.value ? '提前学习' : '学习任务')

async function load() {
  loading.value = true
  actionError.value = ''
  try {
    await store.fetchProject(projectId)
    if (store.current?.stage !== 'READY') {
      await router.replace(`/learning/${projectId}/setup`)
      return
    }
    await store.fetchTask(projectId, taskId)
    if (!execution.value || execution.value.status === 'PAUSED' || execution.value.status === 'COMPLETED' && route.query.review !== '1') await store.startExecution(projectId, taskId)
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
    scheduleResourcePoll()
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '学习任务加载失败。'
  } finally { loading.value = false }
}

function scheduleResourcePoll() {
  if (resourcePollTimer) window.clearTimeout(resourcePollTimer)
  if (!task.value?.resources.some(item => ['QUEUED', 'GENERATING'].includes(item.status))) return
  resourcePollTimer = window.setTimeout(async () => {
    try { await store.fetchTask(projectId, taskId) } catch { /* the visible pending state remains retryable */ }
    scheduleResourcePoll()
  }, 1400)
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

async function saveAnswers(): Promise<boolean> {
  if (!execution.value) return false
  writeCheckpoint()
  try { await store.saveExecutionAnswers(execution.value.executionId, answerDraft.value); return true } catch (error) {
    if (!offline.value) actionError.value = error instanceof Error ? error.message : '答案保存失败，已暂存在本机。'
    return false
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
  if (resourcePollTimer) window.clearTimeout(resourcePollTimer)
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

async function complete() {
  if (!execution.value) return
  actionError.value = ''
  if (task.value?.taskType === 'EXERCISE') {
    if (!exerciseItems.value.length) {
      submitMessage.value = '练习题还在准备中，生成完成后才能提交判卷。'
      return
    }
    const missing = unansweredIndexes.value
    if (missing.length) {
      submitMessage.value = `还有 ${missing.length} 道题未作答，请先完成。`
      await nextTick()
      document.getElementById(`exercise-question-${missing[0]}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      return
    }
    submitConfirmOpen.value = true
    return
  }
  try { await saveAnswers(); await store.saveExecutionProgress(execution.value.executionId, 100, 0); await store.completeExecution(execution.value.executionId) }
  catch (error) { actionError.value = error instanceof Error ? error.message : '完成任务失败。' }
}

async function submitExercise() {
  if (!execution.value || submitting.value) return
  submitting.value = true
  submitMessage.value = ''
  submitConfirmOpen.value = false
  try {
    if (!await saveAnswers()) {
      submitMessage.value = '答案还没有同步成功，请恢复网络后重试。'
      return
    }
    await store.saveExecutionProgress(execution.value.executionId, 100, 0)
    await store.completeExecution(execution.value.executionId)
  } catch (error) {
    submitMessage.value = error instanceof Error ? error.message : '判卷失败，请稍后重试。'
  } finally { submitting.value = false }
}

function scrollToQuestion(index: number) {
  document.getElementById(`exercise-question-${index}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function pauseForNavigation() {
  if (routeLeaving) return
  routeLeaving = true
  writeCheckpoint()
  await saveProgress()
  await saveAnswers()
  await savePosition()
  const current = execution.value
  if (current?.status !== 'IN_PROGRESS') return
  try {
    await store.pauseExecution(current.executionId)
  } catch {
    // Navigation must remain available. The local checkpoint and heartbeat
    // sequence prevent lost work or false time accumulation while offline.
  }
}

onBeforeRouteLeave(async () => {
  await pauseForNavigation()
  return true
})

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
  if (!routeLeaving) void pauseForNavigation()
})
</script>

<template>
  <StudentShell>
    <main class="task-page">
      <header class="task-header"><button class="back-button" type="button" aria-label="返回学习工作台" @click="router.push(`/learning/${projectId}`)"><AppIcon name="chevron-left" :size="19" /></button><div><span class="eyebrow">{{ learningModeLabel }}</span><h1>{{ task?.title || '任务' }}</h1><p>{{ task?.durationMinutes || 30 }} 分钟 · {{ taskTypeLabel }}<template v-if="isEarlyMode"> · 原计划日期 {{ task?.scheduledDate || '待安排' }}</template></p></div></header>
      <p v-if="actionError" class="task-error" role="alert">{{ actionError }}</p>
      <p v-if="offline" class="task-offline" role="status">当前处于离线状态，修改会暂存在本机，恢复连接后自动同步。</p>
      <section v-if="loading" class="task-loading"><span class="loading-bar" /><span class="loading-bar short" /><span class="loading-block" /></section>
      <section v-else-if="task" class="task-layout">
        <article ref="reading" class="task-content" @scroll.passive="scheduleCheckpoint"><div class="task-intro"><span>完成标准</span><p>{{ task.completionCriteria }}</p></div><MarkdownRenderer v-if="readingMarkdown" :content="readingMarkdown" /><div v-else-if="task.resources.some(item => item.status === 'GENERATING' || item.status === 'QUEUED')" class="resource-pending">学习资源正在准备，完成后会自动出现在这里。</div><div v-else-if="task.resources.some(item => item.status === 'FAILED')" class="resource-failed">资源准备失败，可以返回工作台重试。</div><section v-if="exerciseItems.length" class="exercise-list"><header class="exercise-heading"><h2>练习题</h2><span>{{ answeredCount }}/{{ exerciseItems.length }} 已作答</span></header><article v-for="(item, index) in exerciseItems" :id="`exercise-question-${index}`" :key="questionIdOf(item, index)" class="exercise-item" :class="{ answered: Boolean(answerDraft[questionIdOf(item, index)]), correct: gradeByIndex.get(index)?.correct, incorrect: exerciseSubmitted && gradeByIndex.get(index) && !gradeByIndex.get(index)?.correct }"><div class="exercise-question"><strong>{{ index + 1 }}. </strong><MarkdownRenderer v-if="questionText(item)" :content="questionText(item)" /><pre v-if="questionCode(item)" class="exercise-code"><code>{{ questionCode(item) }}</code></pre></div><label v-for="option in optionsOf(item)" :key="option"><input v-model="answerDraft[questionIdOf(item, index)]" type="radio" :name="questionName(index)" :value="option" :disabled="exerciseSubmitted" @change="saveAnswers" /> <span>{{ option }}</span></label><div v-if="gradeByIndex.get(index)" class="exercise-feedback"><span>{{ gradeByIndex.get(index)?.correct ? '回答正确' : '回答错误' }}</span><small>正确答案：{{ gradeByIndex.get(index)?.correctAnswer }}</small><MarkdownRenderer v-if="gradeByIndex.get(index)?.explanation" :content="gradeByIndex.get(index)?.explanation || ''" /></div></article></section></article>
        <aside class="task-aside"><div class="progress-card"><span>当前进度</span><strong>{{ Math.round(execution?.progress || 0) }}%</strong><i><b :style="{ width: `${execution?.progress || 0}%` }" /></i><small>有效学习 {{ Math.floor((execution?.accumulatedSeconds || 0) / 60) }} 分钟</small><button class="complete-button" type="button" :disabled="execution?.status === 'COMPLETED'" @click="complete">{{ execution?.status === 'COMPLETED' ? '已提交' : task?.taskType === 'EXERCISE' ? '提交判卷' : isReviewMode ? '完成本次复习' : '完成任务' }}</button><p v-if="submitMessage" class="submit-message" role="alert">{{ submitMessage }}</p><div v-if="task?.taskType === 'EXERCISE'" class="answer-sheet"><header><strong>答题卡</strong><span>{{ answeredCount }}/{{ exerciseItems.length }}</span></header><div class="question-index-grid"><button v-for="(item, index) in exerciseItems" :key="questionIdOf(item, index)" type="button" :class="{ answered: Boolean(answerDraft[questionIdOf(item, index)]) && !gradeByIndex.has(index), correct: gradeByIndex.get(index)?.correct, incorrect: exerciseSubmitted && gradeByIndex.has(index) && !gradeByIndex.get(index)?.correct }" @click="scrollToQuestion(index)">{{ index + 1 }}</button></div><div v-if="exerciseAccuracy != null && exerciseSubmitted" class="accuracy-summary"><span>正确率</span><strong>{{ exerciseAccuracy }}%</strong><small>{{ submittedGrade?.correct || 0 }}/{{ submittedGrade?.total || exerciseItems.length }} 题正确</small></div></div></div><section class="tutor-side-card"><header><span><AppIcon name="robot" :size="18" /></span><div><strong>AI 助教</strong><small>围绕当前任务提问</small></div></header><div class="quick-questions"><button type="button" @click="tutorOpen = true">梳理这道题</button><button type="button" @click="tutorOpen = true">讲解薄弱点</button></div><button class="tutor-entry" type="button" @click="tutorOpen = true"><span>问问当前任务…</span><AppIcon name="arrow-up" :size="15" /></button></section></aside>
      </section>
    </main>
    <SmartLearningTutorDrawer v-if="tutorOpen" :open="tutorOpen" :project-id="projectId" :project-name="store.current?.name || '学习项目'" :task-id="taskId" :task-title="task?.title" :source-asset-ids="sourceAssetIds" @close="tutorOpen = false" />
    <ConfirmDialog :open="submitConfirmOpen" title="提交判卷" message="提交后将一次性判定全部题目，并显示正确答案和解析；提交后本次作答不能修改。" confirm-text="提交判卷" @close="submitConfirmOpen = false" @confirm="submitExercise" />
  </StudentShell>
</template>

<style scoped>
.task-page { min-height: 100%; padding: 28px 34px 90px; background: var(--color-bg, #f7f7f5); color: var(--color-text, #202124); }.task-header { width: min(1280px, 100%); margin: 0 auto 22px; display: grid; grid-template-columns: 42px minmax(0, 1fr); gap: 14px; align-items: center; }.back-button { width: 40px; height: 40px; display: grid; place-items: center; border: 1px solid var(--color-border, #e2e2df); border-radius: 10px; background: var(--color-surface, #fff); cursor: pointer; }.eyebrow { color: var(--color-text-muted, #707070); font-size: 12px; font-weight: 700; }.task-header h1 { margin: 4px 0 2px; font-size: 26px; }.task-header p { margin: 0; color: var(--color-text-muted, #707070); }.complete-button { border-radius: 10px; padding: 10px 15px; font: inherit; cursor: pointer; }.task-error, .task-offline { width: min(1280px, 100%); margin: 0 auto 12px; padding: 11px 14px; border-radius: 10px; }.task-error { border: 1px solid #efc2bb; color: #b43b2f; background: #fff4f2; }.task-offline { border: 1px solid var(--color-border, #e2e2df); color: var(--color-text-muted, #707070); background: var(--color-hover, #f2f2ef); }.task-layout { width: min(1280px, 100%); margin: auto; display: grid; grid-template-columns: minmax(0, 1fr) 250px; gap: 18px; align-items: start; }.task-content, .progress-card, .tutor-side-card { border: 1px solid var(--color-border, #e2e2df); border-radius: 15px; background: var(--color-surface, #fff); }.task-content { max-height: calc(100vh - 190px); overflow: auto; padding: 28px 32px; }.task-intro { margin-bottom: 22px; padding-bottom: 15px; border-bottom: 1px solid var(--color-border, #e2e2df); }.task-intro span { color: var(--color-text-muted, #707070); font-size: 12px; }.task-intro p { margin: 6px 0 0; }.resource-pending, .resource-failed { padding: 48px 0; text-align: center; color: var(--color-text-muted, #707070); }.resource-failed { color: #b43b2f; }.task-aside { position: sticky; top: 18px; display: grid; gap: 10px; }.progress-card { padding: 18px; }.progress-card span, .progress-card small { display: block; color: var(--color-text-muted, #707070); font-size: 12px; }.progress-card strong { display: block; margin: 8px 0 10px; font-size: 26px; }.progress-card i { display: block; height: 7px; overflow: hidden; border-radius: 999px; background: var(--color-border, #e2e2df); }.progress-card b { display: block; height: 100%; border-radius: inherit; background: #303030; }.progress-card small { margin-top: 10px; }.complete-button { width:100%;margin-top:14px;border:0;color:#fff;background:#303030}.complete-button:disabled{opacity:.55;cursor:default}.tutor-side-card{display:grid;grid-template-columns:34px minmax(0,1fr) 16px;align-items:center;gap:9px;padding:14px;color:inherit;text-align:left;cursor:pointer}.tutor-side-card>span:first-child{width:32px;height:32px;display:grid;place-items:center;border-radius:9px;background:var(--ui-hover-strong-bg)}.tutor-side-card>span:nth-child(2){display:grid;gap:3px}.tutor-side-card small{color:var(--color-text-muted);font-size:11px}.task-loading { width: min(1280px, 100%); margin: auto; padding: 30px; border: 1px solid var(--color-border, #e2e2df); border-radius: 15px; background: var(--color-surface, #fff); }.loading-bar, .loading-block { display: block; height: 14px; margin-bottom: 12px; border-radius: 8px; background: linear-gradient(90deg,#eee,#f7f7f7,#eee); animation: shimmer 1.2s infinite; }.loading-bar { width: 60%; }.loading-bar.short { width: 35%; }.loading-block { height: 260px; width: 100%; }.exercise-list { margin-top: 24px; }.exercise-item { display: grid; gap: 10px; padding: 15px 0; border-top: 1px solid var(--color-border, #e2e2df); }.exercise-item label { color: var(--color-text-muted, #707070); }@keyframes shimmer { 0% { opacity: .65; } 50% { opacity: 1; } 100% { opacity: .65; } }
.exercise-heading { display:flex; align-items:center; justify-content:space-between; gap:10px; margin-bottom:8px; }.exercise-heading h2 { margin:0; font-size:19px; }.exercise-heading span { color:var(--color-text-muted); font-size:12px; }.exercise-item { scroll-margin-top:24px; border:1px solid var(--color-border, #e2e2df); border-radius:12px; margin-top:10px; padding:16px; background:var(--color-surface, #fff); }.exercise-item.answered { border-color:color-mix(in srgb, var(--color-text) 25%, var(--color-border)); }.exercise-item.correct { border-color:#74b994; background:color-mix(in srgb, #74b994 7%, var(--color-surface)); }.exercise-item.incorrect { border-color:#e59a91; background:color-mix(in srgb, #e59a91 7%, var(--color-surface)); }.exercise-question { line-height:1.65; }.exercise-question strong { margin-right:3px; }.exercise-question :deep(p) { display:inline; margin:0; }.exercise-code { margin:10px 0 0; padding:12px; overflow:auto; border-radius:9px; color:var(--color-text); background:var(--color-bg); font:12px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace; white-space:pre-wrap; }.exercise-item label { display:flex; align-items:flex-start; gap:7px; padding:7px 9px; border:1px solid transparent; border-radius:8px; cursor:pointer; }.exercise-item label:hover { border-color:var(--color-border); background:var(--ui-hover-bg); }.exercise-item label input { margin-top:3px; }.exercise-feedback { display:grid; gap:6px; padding-top:10px; border-top:1px solid var(--color-border); }.exercise-feedback > span { color:#2d8556; font-size:12px; font-weight:700; }.exercise-item.incorrect .exercise-feedback > span { color:var(--color-danger); }.exercise-feedback small { color:var(--color-text-muted); }.submit-message { margin:10px 0 0; color:var(--color-danger); font-size:12px; line-height:1.5; }.answer-sheet { margin-top:16px; padding-top:15px; border-top:1px solid var(--color-border); }.answer-sheet header { display:flex; justify-content:space-between; align-items:center; margin-bottom:10px; }.answer-sheet header span { color:var(--color-text-muted); font-size:12px; }.question-index-grid { display:grid; grid-template-columns:repeat(5, 1fr); gap:6px; }.question-index-grid button { width:100%; aspect-ratio:1; border:1px solid var(--color-border); border-radius:7px; color:var(--color-text-muted); background:transparent; cursor:pointer; }.question-index-grid button.answered { border-color:var(--color-text); color:var(--color-text); background:var(--ui-hover-strong-bg); }.question-index-grid button.correct { border-color:#5ba97c; color:#fff; background:#5ba97c; }.question-index-grid button.incorrect { border-color:#d96b5d; color:#fff; background:#d96b5d; }.accuracy-summary { display:grid; grid-template-columns:auto auto; gap:3px 8px; align-items:baseline; margin-top:14px; padding-top:12px; border-top:1px solid var(--color-border); }.accuracy-summary span,.accuracy-summary small { color:var(--color-text-muted); font-size:11px; }.accuracy-summary strong { font-size:20px; }.accuracy-summary small { grid-column:1/-1; }.tutor-side-card { display:grid; gap:11px; padding:16px; text-align:left; }.tutor-side-card > header { display:grid; grid-template-columns:34px minmax(0,1fr); gap:9px; align-items:center; }.tutor-side-card > header > span { width:32px; height:32px; display:grid; place-items:center; border-radius:9px; background:var(--ui-hover-strong-bg); }.tutor-side-card > header > div { display:grid; gap:3px; }.tutor-side-card > header small { color:var(--color-text-muted); font-size:11px; }.tutor-side-card .quick-questions { display:flex; flex-wrap:wrap; gap:6px; }.tutor-side-card .quick-questions button { padding:7px 9px; border:1px solid var(--color-border); border-radius:8px; color:inherit; background:transparent; font-size:11px; cursor:pointer; }.tutor-entry { width:100%; min-height:40px; display:flex; align-items:center; justify-content:space-between; padding:0 11px; border:1px solid var(--color-border); border-radius:9px; color:var(--color-text-muted); background:var(--color-bg); cursor:pointer; }
@media (max-width: 780px) { .task-page { padding-inline: 16px; }.task-header { grid-template-columns: 42px 1fr; }.task-header-actions { grid-column: 1 / -1; }.task-layout { grid-template-columns: 1fr; }.task-aside { position: static; }.task-content { max-height: none; padding: 22px 18px; } }
.question-index-grid button.answered { border-color: var(--color-text); color: var(--color-bg); background: var(--color-text); }
.tutor-side-card { grid-template-columns: 1fr; }
.task-page { background: var(--ui-page-canvas-bg); }
</style>
