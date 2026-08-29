<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StudentShell from '@/components/layout/StudentShell.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { useSmartLearningStore } from '@/stores/smartLearning'
import type { SmartLearningTask } from '@/types/contracts/smartLearning'

const route = useRoute()
const router = useRouter()
const store = useSmartLearningStore()
const projectId = String(route.params.id)
const tutorOpen = ref(false)
const actionError = ref('')
const prepared = computed(() => store.workspace?.resources.filter(resource => resource.status === 'READY').length ?? 0)
const totalResources = computed(() => store.workspace?.resources.length ?? 0)
const pendingResources = computed(() => store.workspace?.resources.some(resource => ['QUEUED', 'FAILED', 'GENERATING'].includes(resource.status)) ?? false)
const sourceAssetIds = computed(() => {
  const assets = store.current?.sources?.assets
  return Array.isArray(assets) ? assets.map(item => typeof item === 'string' ? item : String((item as Record<string, unknown>).assetId ?? '')).filter(Boolean) : []
})

const todayTasks = computed(() => {
  const tasks = store.workspace?.tasks ?? []
  const today = new Date().toISOString().slice(0, 10)
  return tasks.filter(task => task.status !== 'CANCELLED' && (!task.scheduledDate || task.scheduledDate <= today))
})
const laterTasks = computed(() => (store.workspace?.tasks ?? []).filter(task => !todayTasks.value.some(item => item.taskId === task.taskId)))

async function load() {
  actionError.value = ''
  try {
    await Promise.all([store.fetchProject(projectId), store.fetchWorkspace(projectId)])
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '学习工作台加载失败。'
  }
}

async function prepareResources() {
  if (!pendingResources.value || store.runningJobId) return
  actionError.value = ''
  try {
    await store.prepareResources(projectId)
    await store.fetchWorkspace(projectId)
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '资源准备失败，请稍后重试。'
  }
}

function openTask(task: SmartLearningTask) {
  if (task.status === 'PLANNED' && task.scheduledDate && task.scheduledDate > new Date().toISOString().slice(0, 10)) return
  router.push(`/learning/${projectId}/task/${task.taskId}`)
}

onMounted(load)
</script>

<template>
  <StudentShell>
    <main class="workbench-page">
      <header class="workbench-header">
        <button class="back-button" type="button" aria-label="返回学习项目" @click="router.push('/learning')"><AppIcon name="chevron-left" :size="19" /></button>
        <div><span class="eyebrow">智能学习 · 学习工作台</span><h1>{{ store.workspace?.projectName || store.current?.name || '学习项目' }}</h1><p>按今天的学习任务开始，进度会自动保存。</p></div>
        <div class="header-actions"><button class="outline-button" type="button" @click="router.push(`/learning/${projectId}/setup`)">查看准备</button><button class="primary-button" type="button" @click="tutorOpen = true">打开 AI 助教</button></div>
      </header>
      <p v-if="actionError" class="workbench-error" role="alert">{{ actionError }}</p>
      <section class="workbench-summary">
        <div><strong>{{ store.workspace?.progress ?? 0 }}%</strong><span>项目进度</span></div>
        <div><strong>{{ todayTasks.length }}</strong><span>今日任务</span></div>
        <div><strong>{{ prepared }}/{{ totalResources || 0 }}</strong><span>可用学习资源</span></div>
        <button v-if="pendingResources" class="primary-button" type="button" :disabled="Boolean(store.runningJobId)" @click="prepareResources">{{ store.runningJobId ? '资源准备中…' : '准备今日资源' }}</button>
      </section>
      <section class="task-section"><header><div><h2>今天学习</h2><p>优先完成今天可开始的任务，离开后可从原位置继续。</p></div></header><div v-if="!todayTasks.length" class="empty-state">当前没有可开始的任务。请先完成学习准备，或准备今天的资源。</div><div v-else class="task-grid"><button v-for="task in todayTasks" :key="task.taskId" class="task-card" type="button" @click="openTask(task)"><span class="task-type">{{ task.taskType === 'EXERCISE' ? '练习' : task.taskType === 'EXPLANATION' ? '讲解' : '阅读' }}</span><strong>{{ task.title }}</strong><p>{{ task.description }}</p><footer><span>{{ task.durationMinutes }} 分钟</span><em :class="`status-${task.status.toLowerCase()}`">{{ task.status === 'COMPLETED' ? '已完成' : task.status === 'IN_PROGRESS' ? '进行中' : task.status === 'PAUSED' ? '已暂停' : '可开始' }}</em></footer></button></div></section>
      <section v-if="laterTasks.length" class="task-section later-section"><header><div><h2>后续任务</h2><p>按计划日期开放，也可以提前查看任务内容。</p></div></header><div class="later-list"><button v-for="task in laterTasks" :key="task.taskId" class="later-row" type="button" @click="openTask(task)"><span>{{ task.title }}</span><small>{{ task.scheduledDate || '待安排' }} · {{ task.durationMinutes }} 分钟</small><em>{{ task.status === 'PLANNED' ? '计划中' : task.status }}</em></button></div></section>
    </main>
    <SmartLearningTutorDrawer v-if="tutorOpen" :open="tutorOpen" :project-id="projectId" :project-name="store.workspace?.projectName || store.current?.name || '学习项目'" :source-asset-ids="sourceAssetIds" @close="tutorOpen = false" />
  </StudentShell>
</template>

<script lang="ts">
import SmartLearningTutorDrawer from '@/components/learning/SmartLearningTutorDrawer.vue'
export default { components: { SmartLearningTutorDrawer } }
</script>

<style scoped>
.workbench-page { min-height: 100%; padding: 28px 34px 90px; background: var(--color-bg, #f7f7f5); color: var(--color-text, #202124); }
.workbench-header { width: min(1280px, 100%); margin: 0 auto 22px; display: grid; grid-template-columns: 42px minmax(0, 1fr) auto; gap: 14px; align-items: center; }
.back-button { width: 40px; height: 40px; display: grid; place-items: center; border: 1px solid var(--color-border, #e2e2df); border-radius: 10px; background: var(--color-surface, #fff); cursor: pointer; }
.eyebrow { color: var(--color-text-muted, #707070); font-size: 12px; font-weight: 700; }.workbench-header h1 { margin: 4px 0 2px; font-size: 28px; }.workbench-header p { margin: 0; color: var(--color-text-muted, #707070); }.header-actions { display: flex; gap: 9px; }.primary-button, .outline-button { border-radius: 10px; padding: 10px 15px; font: inherit; cursor: pointer; }.primary-button { border: 0; color: #fff; background: #303030; }.outline-button { border: 1px solid var(--color-border, #d8d8d5); color: var(--color-text, #202124); background: var(--color-surface, #fff); }
.workbench-summary, .task-section { width: min(1280px, 100%); margin-inline: auto; border: 1px solid var(--color-border, #e2e2df); border-radius: 16px; background: var(--color-surface, #fff); }.workbench-summary { display: flex; align-items: center; gap: 34px; padding: 18px 22px; }.workbench-summary div { display: grid; gap: 3px; }.workbench-summary strong { font-size: 22px; }.workbench-summary span { color: var(--color-text-muted, #707070); font-size: 12px; }.workbench-summary .primary-button { margin-left: auto; }.task-section { margin-top: 18px; padding: 24px; }.task-section header h2 { margin: 0; font-size: 19px; }.task-section header p { margin: 5px 0 18px; color: var(--color-text-muted, #707070); font-size: 13px; }.task-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }.task-card { min-height: 170px; display: flex; flex-direction: column; align-items: flex-start; padding: 17px; border: 1px solid var(--color-border, #e2e2df); border-radius: 13px; background: var(--color-surface, #fff); color: inherit; text-align: left; cursor: pointer; transition: border-color .16s, transform .16s; }.task-card:hover { border-color: #303030; transform: translateY(-1px); }.task-type { color: var(--color-text-muted, #707070); font-size: 12px; }.task-card strong { margin-top: 8px; font-size: 16px; }.task-card p { flex: 1; margin: 9px 0; color: var(--color-text-muted, #707070); font-size: 13px; line-height: 1.5; }.task-card footer { width: 100%; display: flex; justify-content: space-between; color: var(--color-text-muted, #707070); font-size: 12px; }.task-card em, .later-row em { font-style: normal; }.status-completed { color: #27734e; }.status-in_progress, .status-paused { color: #966b20; }.empty-state { padding: 36px 0; color: var(--color-text-muted, #707070); text-align: center; }.later-list { display: grid; gap: 8px; }.later-row { display: grid; grid-template-columns: minmax(0, 1fr) 170px 70px; align-items: center; gap: 12px; width: 100%; padding: 13px 15px; border: 1px solid var(--color-border, #e2e2df); border-radius: 10px; background: transparent; color: inherit; text-align: left; cursor: pointer; }.later-row small, .later-row em { color: var(--color-text-muted, #707070); font-size: 12px; }.workbench-error { width: min(1280px, 100%); margin: 0 auto 12px; padding: 11px 14px; border: 1px solid #efc2bb; border-radius: 10px; background: #fff4f2; color: #b43b2f; }
@media (max-width: 900px) { .workbench-page { padding-inline: 16px; }.workbench-header { grid-template-columns: 42px 1fr; }.header-actions { grid-column: 1 / -1; }.task-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 560px) { .workbench-summary { flex-wrap: wrap; gap: 18px; }.workbench-summary .primary-button { width: 100%; margin-left: 0; }.task-grid { grid-template-columns: 1fr; }.later-row { grid-template-columns: 1fr; gap: 4px; } }
</style>
