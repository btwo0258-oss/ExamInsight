<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import LearningRouteState from '@/components/learning/LearningRouteState.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import type { LearningResource } from '@/mock'
import { presentationRepository } from '@/repositories/presentation'
import { isApiDataSource } from '@/config/dataSource'
import { useLearningStore } from '@/stores/learning'
import { useLearningTutorStore } from '@/stores/learningTutor'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { useLearningPlanRoute } from '@/composables/useLearningPlanRoute'
import { resourcePreviewRoute } from '@/utils/resourcePreview'

type ResourceWithMeta = LearningResource & {
  source?: 'default' | 'ai-conversation'
  updatedAt?: string
}

const route = useRoute()
const router = useRouter()
const learningStore = useLearningStore()
const tutorStore = useLearningTutorStore()
const libraryResourceStore = useLibraryResourceStore()
const { plan, hasPlan, isLoading, loadError, loadPlan } = useLearningPlanRoute()
const query = ref('')
const toastMsg = ref('')
const actionError = ref('')
const displayedActionError = computed(() => actionError.value || learningStore.errorMessage || '')
const operationPendingId = ref<number | null>(null)
const sourceTaskId = computed(() => Number(route.query.task) || undefined)
const sourceStageId = computed(() => plan.value.stages.find((stage) => stage.tasks.some((task) => task.id === sourceTaskId.value))?.id)
let readingTimer: number | undefined
let resourceRefreshTimer: number | undefined
let resourceRefreshAttempts = 0

function flushReadingActivity() {
  if (sourceTaskId.value) void learningStore.flushLearningActivities(plan.value.id, sourceTaskId.value)
}

function handleVisibilityChange() {
  if (document.visibilityState === 'hidden') flushReadingActivity()
}

async function refreshGeneratingResources() {
  if (!isApiDataSource || !hasPlan.value) return
  if (!resources.value.some((resource) => resource.status === '生成中')) {
    resourceRefreshAttempts = 0
    return
  }
  if (resourceRefreshAttempts >= 40) {
    if (resourceRefreshTimer) window.clearInterval(resourceRefreshTimer)
    resourceRefreshTimer = undefined
    actionError.value = '资源仍在生成，可稍后刷新页面继续查看。'
    return
  }
  resourceRefreshAttempts += 1
  try {
    await learningStore.fetchPlan(plan.value.id)
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '资源状态刷新失败'
  }
}

function clearActionError() {
  actionError.value = ''
  learningStore.clearError()
}

const resources = computed<ResourceWithMeta[]>(() => {
  return plan.value.resources as ResourceWithMeta[]
})

const filteredResources = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  if (!keyword) return resources.value
  return resources.value.filter((resource) =>
    [resource.title, resource.fileName, resource.group].some((value) => value?.toLocaleLowerCase().includes(keyword)),
  )
})

const aiGeneratedCount = computed(() => resources.value.filter((resource) => resource.source === 'ai-conversation').length)
const resourceTypes = computed(() => [...new Set(resources.value.map((resource) => typeLabel(resource)))].join('、'))
const latestUpdate = computed(() => resources.value.find((resource) => resource.updatedAt)?.updatedAt ?? '刚刚')

const resourceColors: Record<LearningResource['group'], string> = {
  学习方案: '#6366f1',
  个性化学习手册: '#10b981',
  PPT: '#d4552d',
  思维导图: '#8b5cf6',
  代码案例: '#2563eb',
  图片: '#ec4899',
}

function iconName(group: LearningResource['group']) {
  if (group === '学习方案') return 'notebook'
  if (group === 'PPT') return 'presentation'
  if (group === '思维导图') return 'mind-topic'
  if (group === '代码案例') return 'code'
  if (group === '图片') return 'image'
  return 'book'
}

function resourceStyle(group: LearningResource['group']): Record<string, string> {
  return { '--resource-color': resourceColors[group] }
}

function typeLabel(resource: ResourceWithMeta) {
  if (resource.group === '学习方案' || resource.group === '个性化学习手册') return 'Markdown'
  if (resource.group === '代码案例') return '代码文件'
  return resource.group
}

function sourceLabel(resource: ResourceWithMeta) {
  return resource.source === 'ai-conversation' ? 'AI 对话生成' : '项目默认生成'
}

function setToast(message: string) {
  toastMsg.value = message
  window.setTimeout(() => {
    if (toastMsg.value === message) toastMsg.value = ''
  }, 1800)
}

function openPresentationResource(resource: ResourceWithMeta) {
  if (resource.presentationId) {
    void router.push({
      path: `/presentations/${resource.presentationId}`,
      query: { returnTo: route.fullPath },
    })
    return
  }

  void router.push({
    path: '/presentations/new',
    query: {
      topic: resource.title || plan.value.title,
      title: resource.fileName?.replace(/\.pptx$/i, '') || resource.title,
      knowledgeBaseId: String(plan.value.knowledgeBaseId),
      projectId: String(plan.value.id),
      learningResourceId: String(resource.id),
      returnTo: route.fullPath,
    },
  })
}

function openResource(resource: ResourceWithMeta) {
  if (resource.status !== '已生成') {
    if (resource.group === 'PPT') openPresentationResource(resource)
    return
  }
  if (!resource.resourceId) {
    actionError.value = '该资源尚未同步到资料库，请刷新后重试。'
    return
  }
  if (resource.group === 'PPT' && !resource.presentationId) {
    openPresentationResource(resource)
    return
  }
  void router.push(resourcePreviewRoute(resource.resourceId, route.fullPath, 'learning'))
}

async function exportResource(resource: ResourceWithMeta) {
  if (resource.status !== '已生成' || operationPendingId.value !== null) return
  const extensionMap: Record<LearningResource['group'], string> = {
    学习方案: 'md',
    个性化学习手册: 'md',
    PPT: 'pptx',
    思维导图: 'json',
    代码案例: 'java',
    图片: 'png',
  }
  operationPendingId.value = resource.id
  actionError.value = ''
  try {
    const blob = resource.group === 'PPT' && resource.presentationId
      ? await presentationRepository.download(resource.presentationId)
      : await learningStore.downloadResource(plan.value.id, resource.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = resource.fileName || `${resource.title}.${extensionMap[resource.group]}`
    link.click()
    URL.revokeObjectURL(url)
    setToast('资源已导出')
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '资源下载失败'
  } finally {
    operationPendingId.value = null
  }
}

async function retryResource(resource: ResourceWithMeta) {
  if (resource.group === 'PPT') {
    openPresentationResource(resource)
    return
  }
  if (operationPendingId.value !== null) return
  operationPendingId.value = resource.id
  actionError.value = ''
  try {
    await learningStore.generateResource(plan.value.id, resource.id)
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '资源重新生成失败'
  } finally {
    operationPendingId.value = null
  }
}

async function openTutorChat() {
  actionError.value = ''
  try {
    const conversationId = await tutorStore.ensureConversation(plan.value)
    await router.push({
      path: `/chat/${conversationId}`,
      query: { projectId: String(plan.value.id), tutor: '1' },
    })
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '打开 AI 对话失败'
  }
}

watch(
  [() => route.query.type, resources],
  ([type]) => {
    if (typeof type !== 'string') return
    const matched = resources.value.find((resource) => resource.group === type)
    if (matched) openResource(matched)
  },
  { immediate: true },
)

watch(
  [hasPlan, resources],
  ([ready, items]) => {
    if (isApiDataSource || !ready) return
    items
      .filter((resource) => resource.status !== '未选择' && !resource.resourceId)
      .forEach((resource) => {
        libraryResourceStore.addGeneratedResource(
          resource,
          plan.value.id,
          plan.value.id,
          plan.value.knowledgeBaseId,
        )
      })
  },
  { immediate: true },
)

onMounted(() => {
  readingTimer = window.setInterval(() => {
    if (sourceTaskId.value && document.visibilityState === 'visible') {
      learningStore.recordTaskReading(plan.value.id, sourceTaskId.value, 100, 1)
    }
  }, 1000)
  resourceRefreshTimer = window.setInterval(() => void refreshGeneratingResources(), 3000)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  if (readingTimer) window.clearInterval(readingTimer)
  if (resourceRefreshTimer) window.clearInterval(resourceRefreshTimer)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  flushReadingActivity()
})
</script>

<template>
  <StudentShell>
    <LearningRouteState
      :loading="isLoading"
      :error="loadError"
      :has-plan="hasPlan"
      @retry="loadPlan"
      @back="router.push('/learning/projects')"
    />
    <div v-if="hasPlan && !isLoading && !loadError" class="resources-page">
      <header class="hero">
        <button class="back-btn" type="button" @click="router.push(`/learning/${plan.id}`)">
          <AppIcon name="chevron-left" :size="18" />
          返回学习项目
        </button>
        <div class="hero-card">
          <div>
            <span class="eyebrow">学习资源包</span>
            <h1>{{ plan.title }}资源包</h1>
            <p>集中保存当前学习项目的最终方案、默认思维导图和 AI 对话生成文件。</p>
            <div class="tags">
              <span>AI 生成</span>
              <span>项目专属</span>
              <span v-for="type in [...new Set(resources.map((resource) => typeLabel(resource)))]" :key="type">{{ type }}</span>
            </div>
          </div>
          <div class="hero-actions">
            <button class="outline-btn" type="button" @click="openTutorChat">
              <AppIcon name="robot" :size="18" />
              进入 AI 对话
            </button>
            <button class="primary-btn" type="button" @click="router.push({ path: `/learning/${plan.id}/study`, query: sourceTaskId ? { stage: sourceStageId, task: sourceTaskId } : {} })">
              <AppIcon name="play" :size="17" />
              {{ sourceTaskId ? '返回当前任务' : '继续学习' }}
            </button>
          </div>
        </div>
      </header>

      <div v-if="displayedActionError" class="resource-error" role="alert">
        <span>{{ displayedActionError }}</span>
        <button type="button" @click="clearActionError">关闭</button>
      </div>

      <section class="stats">
        <article><strong>{{ resources.length }}</strong><span>资源文件</span></article>
        <article><strong>{{ aiGeneratedCount }}</strong><span>AI 对话新增</span></article>
        <article><strong>{{ latestUpdate }}</strong><span>最近更新时间</span></article>
      </section>

      <div class="content-grid">
        <section class="panel files-panel">
          <div class="section-head">
            <div><h2>资源文件</h2><small>默认生成学习方案和思维导图，其他资源可按需创建</small></div>
            <label>
              <AppIcon name="search" :size="18" />
              <input v-model="query" placeholder="搜索资源" />
            </label>
          </div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>文件名</th><th>类型</th><th>状态</th><th>来源</th><th>更新时间</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="resource in filteredResources" :key="resource.id" :class="{ disabled: resource.status !== '已生成' && resource.group !== 'PPT' }" @click="openResource(resource)">
                  <td><span class="resource-type-icon" :style="resourceStyle(resource.group)"><AppIcon :name="iconName(resource.group)" :size="18" /></span><span>{{ resource.fileName || resource.title }}</span></td>
                  <td>{{ typeLabel(resource) }}</td>
                  <td><span class="status" :class="{ active: resource.status === '生成中', failed: resource.status === '生成失败' }">{{ resource.status }}</span></td>
                  <td>{{ sourceLabel(resource) }}</td>
                  <td>{{ resource.updatedAt || '刚刚' }}</td>
                  <td class="row-actions" @click.stop>
                    <button class="icon-btn" type="button" title="预览" :disabled="resource.status !== '已生成' || !resource.resourceId" @click="openResource(resource)"><AppIcon name="eye" :size="17" /></button>
                    <button class="icon-btn" type="button" title="下载" :disabled="resource.status !== '已生成' || (resource.group === 'PPT' && !resource.presentationId) || operationPendingId !== null" @click="exportResource(resource)"><AppIcon name="download" :size="17" /></button>
                    <button v-if="resource.group === 'PPT' && !resource.presentationId" class="text-btn" type="button" @click="openPresentationResource(resource)">生成</button>
                    <button v-else-if="resource.status === '未选择'" class="text-btn" type="button" :disabled="operationPendingId !== null" @click="retryResource(resource)">生成</button>
                    <button v-if="resource.status === '生成失败'" class="text-btn" type="button" :disabled="operationPendingId !== null" @click="retryResource(resource)">重试</button>
                  </td>
                </tr>
                <tr v-if="!filteredResources.length" class="empty-row"><td colspan="6">没有匹配的资源</td></tr>
              </tbody>
            </table>
          </div>
        </section>

        <aside class="panel summary-panel">
          <div class="panel-title"><AppIcon name="folder" :size="22" /><h2>资源包摘要</h2></div>
          <p>该资源包只保存当前项目已经生成的学习成果。通过 AI 对话生成的新文件会继续追加到这里。</p>
          <div class="summary-list">
            <article><span>所属项目</span><strong>{{ plan.title }}</strong></article>
            <article><span>学习目标</span><strong>{{ plan.targetType }}</strong></article>
            <article><span>当前类型</span><strong>{{ resourceTypes }}</strong></article>
            <article><span>最近更新</span><strong>{{ latestUpdate }}</strong></article>
          </div>
        </aside>
      </div>

      <p v-if="toastMsg" class="toast">{{ toastMsg }}</p>
    </div>
  </StudentShell>
</template>

<style scoped>
.resources-page, .resources-page * { box-sizing: border-box; }
.resources-page { min-height: 100%; padding: 34px 28px 56px; background: var(--color-bg); color: var(--color-text); }
.hero, .stats, .content-grid { max-width: 1180px; margin-inline: auto; }
.resource-error { max-width: 1180px; min-height: 38px; margin: 14px auto 0; padding: 8px 10px; display: flex; align-items: center; justify-content: space-between; gap: 10px; border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--color-border)); border-radius: 8px; background: var(--color-surface); color: var(--color-danger); font-size: 13px; }
.resource-error button { border: 0; background: transparent; color: inherit; cursor: pointer; }
h1, h2, p { margin: 0; }
button, input { font: inherit; }
.back-btn { height: 28px; border: 0; border-radius: var(--ui-hover-radius); background: transparent; color: var(--color-text-muted); display: inline-flex; align-items: center; gap: 6px; padding: 0 8px; cursor: pointer; font-size: 13px; }
.back-btn:hover, .outline-btn:hover { background: var(--ui-hover-bg); }
.hero-card { margin-top: 14px; border: 1px solid var(--color-border); border-radius: 8px; padding: 24px; background: var(--color-surface); display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 24px; align-items: center; }
.eyebrow { display: block; margin-bottom: 6px; color: var(--color-info); font-size: 12px; font-weight: 800; }
h1 { font-size: 30px; }
.hero-card p { margin-top: 10px; color: var(--color-text-muted); line-height: 1.6; }
.tags { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.tags span { padding: 5px 10px; border-radius: 6px; background: var(--color-hover); color: var(--color-text-muted); font-size: 13px; }
.hero-actions { display: flex; gap: 10px; }
.outline-btn, .primary-btn { height: 42px; border-radius: 8px; padding: 0 16px; display: inline-flex; align-items: center; gap: 8px; cursor: pointer; font-weight: 700; }
.outline-btn { border: 1px solid var(--color-border); background: var(--color-surface); color: var(--color-text); }
.primary-btn { border: 1px solid var(--color-primary); background: var(--color-primary); color: var(--color-on-primary); }
.stats { margin-top: 18px; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.stats article, .panel { border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-surface); box-shadow: var(--shadow-sm); }
.stats article { min-height: 96px; padding: 18px; display: grid; gap: 5px; align-content: center; }
.stats strong { font-size: 26px; }
.stats span { color: var(--color-text-muted); }
.content-grid { margin-top: 18px; display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 18px; align-items: start; }
.panel { padding: 20px; }
.section-head, .panel-title { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 14px; }
.section-head > div { display: grid; gap: 3px; }
.section-head small { color: var(--color-text-muted); }
.panel-title { justify-content: flex-start; }
h2 { font-size: 21px; }
.section-head label { width: 220px; height: 38px; border: 1px solid var(--color-border); border-radius: 8px; display: flex; align-items: center; gap: 8px; padding: 0 10px; color: var(--color-text-muted); }
.section-head input { min-width: 0; width: 100%; border: 0; outline: 0; background: transparent; color: var(--color-text); }
.table-wrap { overflow-x: auto; }
table { width: 100%; min-width: 760px; border-collapse: collapse; }
th, td { height: 48px; border-top: 1px solid var(--color-border); text-align: left; font-size: 13px; }
th { color: var(--color-text-muted); font-size: 12px; }
tbody tr:not(.empty-row) { cursor: pointer; }
tbody tr:not(.empty-row):hover { background: var(--ui-hover-bg); }
td:first-child { min-width: 210px; display: flex; align-items: center; gap: 9px; }
td:first-child span { max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.resource-type-icon { width: 30px; height: 30px; flex: 0 0 auto; display: grid !important; place-items: center; border-radius: 8px; background: color-mix(in srgb, var(--resource-color) 12%, var(--color-surface)); color: var(--resource-color); }
.status { padding: 4px 9px; border-radius: 999px; background: color-mix(in srgb, var(--color-success) 15%, var(--color-surface)); color: var(--color-success); white-space: nowrap; }
.status.active { background: color-mix(in srgb, var(--color-info) 12%, var(--color-surface)); color: var(--color-info); }
.status.failed { background: color-mix(in srgb, var(--color-danger) 12%, var(--color-surface)); color: var(--color-danger); }
.disabled { cursor: default !important; opacity: .72; }
.row-actions { white-space: nowrap; }
.icon-btn { border: 0; border-radius: var(--ui-hover-radius); background: transparent; color: var(--color-text-muted); padding: 6px; cursor: pointer; }
.icon-btn:hover { background: var(--ui-hover-strong-bg); color: var(--color-text); }
.icon-btn:disabled { opacity: .4; cursor: not-allowed; }
.icon-btn:disabled:hover { background: transparent; color: var(--color-text-muted); }
.text-btn { min-height: 28px; padding: 0 9px; border: 1px solid var(--color-border); border-radius: 7px; background: transparent; color: var(--color-text); cursor: pointer; }
.empty-row td { height: 110px; text-align: center; color: var(--color-text-muted); }
.summary-panel p { color: var(--color-text-muted); line-height: 1.7; }
.summary-list { margin-top: 16px; display: grid; gap: 12px; }
.summary-list article { border-top: 1px solid var(--color-border); padding-top: 12px; }
.summary-list span { display: block; margin-bottom: 5px; color: var(--color-text-muted); font-size: 13px; }
.summary-list strong { line-height: 1.5; }
.toast { position: fixed; right: 24px; bottom: 24px; z-index: 90; border-radius: 8px; background: var(--color-primary); color: var(--color-on-primary); padding: 10px 14px; box-shadow: var(--shadow-md); }
@media (max-width: 980px) { .hero-card, .stats, .content-grid { grid-template-columns: 1fr; } .hero-actions { flex-wrap: wrap; } .summary-panel { order: -1; } }
@media (max-width: 620px) { .resources-page { padding: 22px 14px 42px; } .hero-card { padding: 18px; } .section-head { align-items: stretch; flex-direction: column; } .section-head label { width: 100%; } }
</style>
