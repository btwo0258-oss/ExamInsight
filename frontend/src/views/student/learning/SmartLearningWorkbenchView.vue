<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StudentShell from '@/components/layout/StudentShell.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import SmartLearningTutorDrawer from '@/components/learning/SmartLearningTutorDrawer.vue'
import { getSmartLearningJob, prepareSmartLearningResources } from '@/api/smartLearning'
import { useSmartLearningStore } from '@/stores/smartLearning'
import type { SmartLearningTask } from '@/types/contracts/smartLearning'

const route = useRoute()
const router = useRouter()
const store = useSmartLearningStore()
const projectId = String(route.params.id)
const loading = ref(true)
const tutorOpen = ref(false)
const tutorQuestion = ref('')
const tutorRequestId = ref(0)
const actionError = ref('')
const preparing = ref(false)
const continueChoiceOpen = ref(false)
const resourceNoticeOpen = ref(false)
const resourceNotice = ref('')
let disposed = false
let pollTimer: number | undefined

const workspace = computed(() => store.workspace)
const tasks = computed(() => workspace.value?.tasks ?? [])
const resources = computed(() => workspace.value?.resources ?? [])
const pendingResources = computed(() => resources.value.filter(item => ['QUEUED', 'GENERATING'].includes(item.status)))
const failedResources = computed(() => resources.value.filter(item => item.status === 'FAILED'))
const sourceAssetIds = computed(() => {
  const assets = store.current?.sources?.assets
  return Array.isArray(assets)
    ? assets.map(item => typeof item === 'string' ? item : String((item as Record<string, unknown>).assetId ?? '')).filter(Boolean)
    : []
})

const today = new Date().toISOString().slice(0, 10)
const todayTasks = computed(() => tasks.value.filter(task => task.status !== 'CANCELLED'
  && (['IN_PROGRESS', 'PAUSED'].includes(task.status) || !task.scheduledDate || task.scheduledDate <= today)))
const laterTasks = computed(() => tasks.value.filter(task => !todayTasks.value.some(item => item.taskId === task.taskId)))
const continueTask = computed(() => {
  const activeId = workspace.value?.activeExecution?.taskId
  return tasks.value.find(item => item.taskId === activeId)
    ?? tasks.value.find(item => ['IN_PROGRESS', 'PAUSED'].includes(item.status))
    ?? todayTasks.value.find(item => !['COMPLETED', 'SKIPPED'].includes(item.status))
})
const nextFutureTask = computed(() => laterTasks.value.find(item => !['COMPLETED', 'SKIPPED'].includes(item.status)))
const reviewTask = computed(() => [...tasks.value].reverse().find(item => item.status === 'COMPLETED'))
const resourceGroups = computed(() => (['READING', 'EXPLANATION', 'EXERCISE', 'REVIEW'] as const).map(type => {
  const groupTasks = tasks.value.filter(task => task.taskType === type)
  const taskIds = new Set(groupTasks.map(task => task.taskId))
  const groupResources = resources.value.filter(resource => taskIds.has(resource.taskId))
  return {
    type,
    label: taskTypeLabel(type),
    count: groupResources.length,
    ready: groupResources.filter(item => item.status === 'READY').length,
    pending: groupResources.filter(item => ['QUEUED', 'GENERATING'].includes(item.status)).length,
  }
}))

const profileItems = computed(() => {
  const target = (workspace.value?.profile?.target ?? {}) as Record<string, unknown>
  const diagnosis = (workspace.value?.profile?.diagnosis ?? {}) as Record<string, unknown>
  const scope = (workspace.value?.profile?.scope ?? {}) as Record<string, unknown>
  const nodes = Array.isArray(scope.nodes) ? scope.nodes : []
  const weeklyHours = Number(target.weeklyHours || 0)
  const weeklyMinutes = Number(target.weeklyMinutes || 0)
  return [
    { label: '学习目标', value: String(target.examName || target.goal || store.current?.name || '待完善') },
    { label: '当前基础', value: String(target.selfLevel || diagnosis.level || diagnosis.summary || '根据诊断持续更新') },
    { label: '知识范围', value: nodes.length ? `${nodes.length} 个知识点` : '按确认范围学习' },
    { label: '每周时间', value: weeklyHours ? `${weeklyHours} 小时` : weeklyMinutes ? `${Math.round(weeklyMinutes / 60 * 10) / 10} 小时` : '按计划安排' },
  ]
})

const taskTypeLabel = (type: string) => ({ READING: '阅读', EXERCISE: '练习', REVIEW: '复盘', EXPLANATION: '讲解' }[type] ?? '学习')
const taskIcon = (type: string) => ({ READING: 'file', EXERCISE: 'check', REVIEW: 'refresh-single', EXPLANATION: 'message-square' }[type] ?? 'file')

function openTask(task?: SmartLearningTask) {
  if (task) void router.push(`/learning/${projectId}/task/${task.taskId}`)
}

function continueLearning() {
  if (continueTask.value) return openTask(continueTask.value)
  continueChoiceOpen.value = true
}

function chooseFutureLearning() {
  continueChoiceOpen.value = false
  if (nextFutureTask.value) {
    void router.push({ path: `/learning/${projectId}/task/${nextFutureTask.value.taskId}`, query: { early: '1' } })
    return
  }
  void router.push({ path: `/learning/${projectId}/setup`, query: { step: '4', extend: '1', returnTo: `/learning/${projectId}` } })
}

function chooseReview() {
  continueChoiceOpen.value = false
  if (reviewTask.value) void router.push({ path: `/learning/${projectId}/task/${reviewTask.value.taskId}`, query: { review: '1' } })
}

function openResourceGroup(type: string) {
  const group = resourceGroups.value.find(item => item.type === type)
  if (!group?.ready) {
    resourceNotice.value = group?.pending
      ? '这个分组的资源还在准备中，完成后才能查看。'
      : '这个分组暂时没有可查看的资源。'
    resourceNoticeOpen.value = true
    return
  }
  void router.push({ path: `/learning/${projectId}/resources`, query: { group: type } })
}

function openResourceHub() {
  const firstReady = resourceGroups.value.find(item => item.ready > 0)
  if (!firstReady) {
    resourceNotice.value = pendingResources.value.length
      ? '资源正在准备中，完成后才能进入资源包。'
      : '当前还没有可查看的资源。'
    resourceNoticeOpen.value = true
    return
  }
  openResourceGroup(firstReady.type)
}

function isEarlyTask(task: SmartLearningTask) {
  return Boolean(task.scheduledDate && task.scheduledDate > today && ['IN_PROGRESS', 'PAUSED'].includes(task.status))
}

function taskStatusLabel(task: SmartLearningTask) {
  if (task.status === 'COMPLETED') return '已完成'
  if (isEarlyTask(task)) return '提前学习'
  if (task.status === 'IN_PROGRESS') return '进行中'
  if (task.status === 'PAUSED') return '已暂停'
  return '待学习'
}

function askTutor(question = '') {
  tutorQuestion.value = question
  tutorRequestId.value += 1
  tutorOpen.value = true
}

async function refreshWorkspace() {
  await store.fetchWorkspace(projectId)
}

function schedulePoll(jobId: string) {
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(async () => {
    if (disposed) return
    try {
      const job = await getSmartLearningJob(jobId)
      await refreshWorkspace()
      if (['QUEUED', 'RUNNING'].includes(job.status)) return schedulePoll(jobId)
      preparing.value = false
      if (job.status === 'FAILED') actionError.value = job.errorMessage || '学习资源准备失败，可以重新尝试。'
    } catch (error) {
      preparing.value = false
      actionError.value = error instanceof Error ? error.message : '资源状态同步失败，请刷新后重试。'
    }
  }, 1200)
}

async function ensureResourcePreparation() {
  if (!store.current || store.current.stage !== 'READY' || preparing.value) return
  const active = store.current.activeJob
  if (active?.kind === 'RESOURCE_PREPARATION' && ['QUEUED', 'RUNNING'].includes(active.status)) {
    preparing.value = true
    schedulePoll(active.jobId)
    return
  }
  if (!resources.value.length || pendingResources.value.length || failedResources.value.length) {
    try {
      preparing.value = true
      const accepted = await prepareSmartLearningResources(projectId)
      schedulePoll(accepted.jobId)
    } catch (error) {
      preparing.value = false
      actionError.value = error instanceof Error ? error.message : '资源准备没有启动，请重试。'
    }
  }
}

async function load() {
  loading.value = true
  actionError.value = ''
  try {
    await store.fetchProject(projectId)
    if (store.current?.stage !== 'READY') {
      await router.replace(`/learning/${projectId}/setup`)
      return
    }
    await refreshWorkspace()
    await ensureResourcePreparation()
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '学习工作台加载失败。'
  } finally { loading.value = false }
}

onMounted(load)
onBeforeUnmount(() => {
  disposed = true
  if (pollTimer) window.clearTimeout(pollTimer)
})
</script>

<template>
  <StudentShell>
    <main class="workbench-page">
      <header class="workbench-header">
        <button class="icon-button" type="button" aria-label="返回学习项目" @click="router.push('/learning')"><AppIcon name="chevron-left" :size="19" /></button>
        <div class="heading-copy"><span>智能学习 · 学习工作台</span><h1>{{ workspace?.projectName || store.current?.name || '学习项目' }}</h1><p>按计划继续学习，任务、资源和错题会自动同步。</p></div>
        <div class="header-actions"><button type="button" @click="router.push({ path: `/learning/${projectId}/setup`, query: { returnTo: `/learning/${projectId}` } })">调整准备</button></div>
      </header>

      <p v-if="actionError" class="workbench-error" role="alert"><span>{{ actionError }}</span><button type="button" @click="actionError = ''; ensureResourcePreparation()">重试</button></p>

      <section v-if="loading" class="workspace-grid workspace-skeleton" aria-label="正在加载学习工作台">
        <div class="panel skeleton path-skeleton"><i /><i class="wide" /><i /><b v-for="n in 5" :key="n" /></div>
        <div class="workspace-column"><div class="panel skeleton"><i /><b v-for="n in 3" :key="n" /></div><div class="panel skeleton small"><i /><b /></div></div>
        <div class="workspace-column"><div class="panel skeleton"><i /><b v-for="n in 4" :key="n" /></div><div class="panel skeleton small"><i /><b v-for="n in 2" :key="n" /></div></div>
      </section>

      <section v-else class="workspace-grid">
        <section class="panel path-panel">
          <header class="panel-head"><div><AppIcon name="list" :size="21" /><h2>每日任务</h2></div><span>{{ workspace?.completedTaskCount || 0 }}/{{ workspace?.totalTaskCount || 0 }}</span></header>
          <p class="panel-description">今天的阅读、讲解、练习与复盘集中在这里，完成后自动推进计划。</p>
          <div class="path-summary"><div class="path-summary-copy"><span>项目进度</span><strong>{{ workspace?.progress || 0 }}%</strong><small>已完成 {{ workspace?.completedTaskCount || 0 }} / {{ workspace?.totalTaskCount || 0 }} 项</small></div><i><b :style="{ width: `${workspace?.progress || 0}%` }" /></i><button type="button" :disabled="!tasks.length" @click="continueLearning"><AppIcon name="play" :size="15" />{{ continueTask ? (['IN_PROGRESS', 'PAUSED'].includes(continueTask.status) ? '继续学习' : '开始学习') : '继续学习' }}</button></div>
          <div class="task-group"><h3>今日</h3><p v-if="!todayTasks.length" class="empty-copy">今天暂无可开始任务，资源准备完成后会自动出现。</p><button v-for="task in todayTasks" :key="task.taskId" class="task-row" type="button" @click="openTask(task)"><span class="task-icon"><AppIcon :name="taskIcon(task.taskType)" :size="15" /></span><span class="task-copy"><strong>{{ task.title }}</strong><small>{{ taskTypeLabel(task.taskType) }} · {{ task.durationMinutes }} 分钟</small></span><em :class="{ done: task.status === 'COMPLETED', early: isEarlyTask(task) }">{{ taskStatusLabel(task) }}</em></button></div>
          <div v-if="laterTasks.length" class="task-group later"><h3>后续计划</h3><button v-for="task in laterTasks" :key="task.taskId" class="task-row" type="button" @click="openTask(task)"><span class="task-icon"><AppIcon :name="taskIcon(task.taskType)" :size="15" /></span><span class="task-copy"><strong>{{ task.title }}</strong><small>{{ task.scheduledDate || '待安排' }} · {{ taskTypeLabel(task.taskType) }}</small></span><em>计划中</em></button></div>
        </section>

        <div class="workspace-column">
          <section class="panel resource-panel">
            <header class="panel-head"><div><AppIcon name="folder" :size="21" /><h2>资源包</h2></div><span>{{ resources.filter(item => item.status === 'READY').length }}/{{ resources.length }}</span></header>
            <p class="panel-description">按学习环节分类收好，进入分组后再查看每份资料。</p>
            <div class="resource-groups">
              <button v-for="group in resourceGroups" :key="group.type" type="button" @click="openResourceGroup(group.type)"><span class="task-icon"><AppIcon :name="taskIcon(group.type)" :size="16" /></span><span><strong>{{ group.label }}资料</strong><small>{{ group.pending ? `${group.pending} 项准备中` : group.count ? `${group.ready}/${group.count} 项已就绪` : '暂无资料' }}</small></span><AppIcon name="chevron-right" :size="15" /></button>
            </div>
            <div v-if="preparing && !resources.length" class="resource-group-skeleton"><i v-for="n in 4" :key="n" /></div>
            <button class="panel-action" type="button" @click="openResourceHub">进入资源</button>
          </section>

          <section class="panel wrong-card">
            <header class="panel-head"><div><AppIcon name="alert-circle" :size="21" /><h2>错题本</h2></div></header>
            <p class="panel-description">练习交卷后答错的题目会自动收录。</p>
            <strong class="metric">{{ workspace?.wrongItemCount || 0 }} <small>道错题</small></strong>
            <p>{{ workspace?.pendingWrongItemCount || 0 }} 道待复习，已掌握的记录仍会保留。</p>
            <button class="panel-action" type="button" @click="router.push(`/learning/${projectId}/mistakes`)">查看错题本</button>
          </section>
        </div>

        <div class="workspace-column">
          <section class="panel profile-panel">
            <header class="panel-head"><div><AppIcon name="user" :size="21" /><h2>个性化学习画像</h2></div></header>
            <p class="panel-description">使用你确认过的目标、诊断和学习约束，不跨项目混用。</p>
            <div class="profile-list"><article v-for="item in profileItems" :key="item.label"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></article></div>
          </section>

          <section class="panel tutor-card">
            <header class="panel-head"><div><AppIcon name="robot" :size="21" /><h2>AI 助教</h2></div></header>
            <p class="panel-description">围绕当前项目提问；任务页会自动切换到对应任务会话。</p>
            <div class="quick-questions"><button type="button" @click="askTutor('帮我梳理今天的学习重点')">梳理今日重点</button><button type="button" @click="askTutor('根据我的薄弱点给一个学习建议')">针对薄弱点建议</button></div>
            <button class="tutor-entry" type="button" @click="askTutor()"><span>问问当前项目…</span><AppIcon name="arrow-up" :size="15" /></button>
          </section>
        </div>
      </section>
    </main>

    <SmartLearningTutorDrawer v-if="tutorOpen" :open="tutorOpen" :project-id="projectId" :project-name="workspace?.projectName || store.current?.name || '学习项目'" :source-asset-ids="sourceAssetIds" :initial-question="tutorQuestion" :initial-request-id="tutorRequestId" @close="tutorOpen = false" />
    <ConfirmDialog :open="resourceNoticeOpen" title="资源暂不可查看" :message="resourceNotice" confirm-text="知道了" cancel-text="" @close="resourceNoticeOpen = false" @confirm="resourceNoticeOpen = false" />
    <div v-if="continueChoiceOpen" class="choice-backdrop" role="presentation" @click.self="continueChoiceOpen = false">
      <section class="choice-dialog" role="dialog" aria-modal="true" aria-labelledby="continue-choice-title"><button class="choice-close" type="button" aria-label="关闭" @click="continueChoiceOpen = false">×</button><span>今日任务已完成</span><h2 id="continue-choice-title">接下来想怎么学？</h2><p>原来的完成记录不会被重置，你可以提前学习新内容，也可以复习已经学过的内容。</p><div class="choice-options"><button type="button" @click="chooseFutureLearning"><AppIcon name="arrow-right" :size="18" /><strong>{{ nextFutureTask ? '提前学习下一项' : '继续学习新内容' }}</strong><small>{{ nextFutureTask ? `将开始「${nextFutureTask.title}」，不改变原计划日期` : '生成扩展计划，确认后追加到当前项目' }}</small></button><button type="button" :disabled="!reviewTask" @click="chooseReview"><AppIcon name="refresh-single" :size="18" /><strong>复习已学内容</strong><small>{{ reviewTask ? `从「${reviewTask.title}」开始复习` : '完成至少一个任务后可以复习' }}</small></button></div></section>
    </div>
  </StudentShell>
</template>

<style scoped>
.workbench-page, .workbench-page * { box-sizing: border-box; }
.workbench-page { min-height: 100%; padding: 28px 34px 90px; background: var(--color-bg); color: var(--color-text); }
.workbench-header, .workspace-grid, .workbench-error { width: min(1460px, 100%); margin-inline: auto; }
.workbench-header { display: grid; grid-template-columns: 42px minmax(0, 1fr) auto; align-items: center; gap: 14px; margin-bottom: 22px; }
.icon-button { width: 40px; height: 40px; display: grid; place-items: center; border: 1px solid var(--color-border); border-radius: 10px; color: inherit; background: var(--color-surface); cursor: pointer; }
.heading-copy span { color: var(--color-text-muted); font-size: 12px; font-weight: 700; }.heading-copy h1 { margin: 3px 0 2px; font-size: 28px; }.heading-copy p { margin: 0; color: var(--color-text-muted); font-size: 13px; }
.header-actions { display: flex; gap: 8px; }.header-actions button, .panel-action { min-height: 36px; padding: 0 13px; border: 1px solid var(--color-border); border-radius: 9px; color: inherit; background: var(--color-surface); cursor: pointer; }.header-actions button:hover, .panel-action:hover { background: var(--ui-hover-bg); }
.workbench-error { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 14px; padding: 11px 14px; border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--color-border)); border-radius: 10px; color: var(--color-danger); background: var(--color-surface); }.workbench-error button { border: 0; color: inherit; background: transparent; cursor: pointer; }
.workspace-grid { display: grid; grid-template-columns: minmax(440px, 1.55fr) minmax(280px, .82fr) minmax(300px, .9fr); align-items: start; gap: 16px; }.workspace-column { display: grid; gap: 16px; min-width: 0; }
.panel { min-width: 0; overflow: hidden; border: 1px solid var(--color-border); border-radius: 15px; background: var(--color-surface); box-shadow: var(--shadow-sm); }.path-panel { min-height: 680px; padding: 22px; }.resource-panel, .wrong-card, .profile-panel, .tutor-card { padding: 19px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.panel-head > div { display: flex; align-items: center; gap: 9px; }.panel-head h2 { margin: 0; font-size: 17px; }.panel-head > span { color: var(--color-text-muted); font-size: 12px; }
.panel-description { margin: 7px 0 16px; color: var(--color-text-muted); font-size: 12px; line-height: 1.55; }.empty-copy { color: var(--color-text-muted); font-size: 13px; }
.path-summary { display: grid; grid-template-columns: minmax(150px, auto) minmax(100px, 1fr) auto; align-items: center; gap: 14px; margin: 14px 0 22px; padding: 12px 14px; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-surface); }.path-summary-copy { display: grid; grid-template-columns: auto auto; align-items: baseline; gap: 2px 8px; }.path-summary-copy span, .path-summary-copy small { color: var(--color-text-muted); font-size: 11px; }.path-summary-copy strong { font-size: 19px; }.path-summary-copy small { grid-column: 1 / -1; }.path-summary > i { height: 6px; overflow: hidden; border-radius: 99px; background: var(--color-border); }.path-summary > i b { display: block; height: 100%; border-radius: inherit; background: var(--color-text); }.path-summary > button { display: flex; min-height: 34px; align-items: center; gap: 6px; padding: 0 12px; border: 0; border-radius: 9px; color: var(--color-on-primary, #fff); background: var(--color-primary, #303030); cursor: pointer; }
.task-group + .task-group { margin-top: 24px; }.task-group h3 { margin: 0 0 10px; font-size: 13px; }.task-row { width: 100%; display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; align-items: center; gap: 10px; min-height: 62px; padding: 9px 4px; border: 0; border-top: 1px solid var(--color-border); color: inherit; background: transparent; text-align: left; cursor: pointer; }.task-row:hover { background: var(--ui-hover-bg); }.task-icon { width: 31px; height: 31px; display: grid; place-items: center; border-radius: 9px; background: var(--ui-hover-strong-bg); }.task-copy { min-width: 0; display: grid; gap: 3px; }.task-copy strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.task-copy small, .task-row em { color: var(--color-text-muted); font-size: 11px; font-style: normal; }.task-row em.done { color: #287756; }
.resource-list { display: grid; gap: 8px; }.resource-row { display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; align-items: center; gap: 9px; min-height: 58px; padding: 9px; border: 1px solid var(--color-border); border-radius: 10px; }.resource-row > span { width: 31px; height: 31px; display: grid; place-items: center; border-radius: 8px; background: var(--ui-hover-bg); }.resource-row > div { min-width: 0; display: grid; gap: 3px; }.resource-row strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.resource-row small, .resource-row em { color: var(--color-text-muted); font-size: 10px; font-style: normal; }.resource-row em.is-ready { color: #287756; }.resource-row em.is-failed { color: var(--color-danger); }.resource-panel .panel-action, .wrong-card .panel-action { width: 100%; margin-top: 14px; }
.resource-groups { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }.resource-groups > button { min-width: 0; display: grid; grid-template-columns: 32px minmax(0, 1fr) 15px; align-items: center; gap: 8px; padding: 10px; border: 1px solid var(--color-border); border-radius: 11px; color: inherit; background: transparent; text-align: left; cursor: pointer; }.resource-groups > button:hover { border-color: color-mix(in srgb, var(--color-text) 35%, var(--color-border)); background: var(--ui-hover-bg); }.resource-groups > button > span:nth-child(2) { min-width: 0; display: grid; gap: 3px; }.resource-groups strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.resource-groups small { color: var(--color-text-muted); font-size: 10px; }.resource-group-skeleton { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }.resource-group-skeleton i { height: 58px; border-radius: 11px; background: var(--ui-hover-bg); animation: pulse 1.2s infinite; }
.choice-backdrop { position: fixed; inset: 0; z-index: 90; display: grid; place-items: center; padding: 20px; background: rgba(0,0,0,.32); backdrop-filter: blur(2px); }.choice-dialog { position: relative; width: min(560px, 100%); padding: 24px; border: 1px solid var(--color-border); border-radius: 18px; background: var(--color-surface); box-shadow: 0 18px 60px rgba(0,0,0,.18); }.choice-dialog > span { color: var(--color-text-muted); font-size: 12px; font-weight: 700; }.choice-dialog h2 { margin: 5px 0 8px; font-size: 23px; }.choice-dialog > p { margin: 0 32px 20px 0; color: var(--color-text-muted); line-height: 1.6; }.choice-close { position: absolute; top: 14px; right: 14px; width: 32px; height: 32px; border: 0; border-radius: 8px; color: inherit; background: transparent; font-size: 24px; cursor: pointer; }.choice-close:hover { background: var(--ui-hover-bg); }.choice-options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }.choice-options button { display: grid; grid-template-columns: 22px 1fr; gap: 5px 9px; min-height: 116px; padding: 16px; border: 1px solid var(--color-border); border-radius: 13px; color: inherit; background: transparent; text-align: left; cursor: pointer; }.choice-options button:hover { border-color: var(--color-text); background: var(--ui-hover-bg); }.choice-options strong { align-self: center; }.choice-options small { grid-column: 1 / -1; color: var(--color-text-muted); line-height: 1.5; }.choice-options button:disabled { opacity: .5; cursor: default; }
.wrong-card .metric { display: block; margin: 8px 0 4px; font-size: 27px; }.wrong-card .metric small { color: var(--color-text-muted); font-size: 12px; }.wrong-card > p:not(.panel-description) { margin: 0; color: var(--color-text-muted); font-size: 12px; }
.profile-list { display: grid; gap: 0; }.profile-list article { display: grid; gap: 4px; padding: 11px 0; border-top: 1px solid var(--color-border); }.profile-list span { color: var(--color-text-muted); font-size: 11px; }.profile-list strong { overflow-wrap: anywhere; font-size: 13px; }
.quick-questions { display: flex; flex-wrap: wrap; gap: 6px; }.quick-questions button { padding: 7px 9px; border: 1px solid var(--color-border); border-radius: 8px; color: inherit; background: transparent; font-size: 11px; cursor: pointer; }.tutor-entry { width: 100%; min-height: 42px; display: flex; align-items: center; justify-content: space-between; margin-top: 12px; padding: 0 12px; border: 1px solid var(--color-border); border-radius: 10px; color: var(--color-text-muted); background: var(--color-bg); cursor: pointer; }
.skeleton { display: grid; gap: 13px; padding: 22px; }.skeleton i, .skeleton b, .resource-row-skeleton b, .resource-row-skeleton span { display: block; border-radius: 7px; background: linear-gradient(90deg, var(--color-hover), var(--color-surface), var(--color-hover)); background-size: 200% 100%; animation: shimmer 1.3s infinite; }.skeleton i { width: 34%; height: 17px; }.skeleton i.wide { width: 70%; height: 10px; }.skeleton b { height: 58px; }.path-skeleton { min-height: 680px; }.skeleton.small { min-height: 210px; }.resource-row-skeleton > div b:first-child { width: 80%; height: 10px; }.resource-row-skeleton > div b:last-child { width: 45%; height: 7px; margin-top: 7px; }
@keyframes shimmer { from { background-position: 100% 0; } to { background-position: -100% 0; } }
@media (max-width: 1120px) { .workspace-grid { grid-template-columns: minmax(400px, 1.35fr) minmax(280px, .9fr); }.workspace-column:last-child { grid-column: 1 / -1; grid-template-columns: 1fr 1fr; } }
@media (max-width: 760px) { .workbench-page { padding-inline: 16px; }.workbench-header { grid-template-columns: 42px 1fr; }.header-actions { grid-column: 1 / -1; }.workspace-grid { grid-template-columns: 1fr; }.workspace-column:last-child { grid-column: auto; grid-template-columns: 1fr; }.path-panel { min-height: auto; }.path-summary { grid-template-columns: 1fr auto; }.path-summary > i { grid-column: 1 / -1; grid-row: 2; }.path-summary > button { grid-column: 2; grid-row: 1; }.choice-options, .resource-groups { grid-template-columns: 1fr; } }
</style>
