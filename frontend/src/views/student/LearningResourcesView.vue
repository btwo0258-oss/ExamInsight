<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import LearningMindMapPreview from '@/components/student/LearningMindMapPreview.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import type { LearningResource } from '@/mock'
import { useLearningStore } from '@/stores/learning'
import { useLearningTutorStore } from '@/stores/learningTutor'
import { renderMarkdownToHtml } from '@/utils/markdown'

type ResourceWithMeta = LearningResource & {
  source?: 'default' | 'ai-conversation'
  updatedAt?: string
}

const route = useRoute()
const router = useRouter()
const learningStore = useLearningStore()
const tutorStore = useLearningTutorStore()
const plan = computed(() => learningStore.getPlan(Number(route.params.id)) ?? learningStore.plans[0]!)
const query = ref('')
const previewResource = ref<ResourceWithMeta | null>(null)
const toastMsg = ref('')
const sourceTaskId = computed(() => Number(route.query.task) || undefined)
const sourceStageId = computed(() => plan.value.stages.find((stage) => stage.tasks.some((task) => task.id === sourceTaskId.value))?.id)
let readingTimer: number | undefined

const planResource = computed<ResourceWithMeta>(() => {
  return plan.value.resources.find((resource) => resource.group === '学习方案') as ResourceWithMeta | undefined ?? {
    id: -Math.abs(plan.value.id),
    group: '学习方案',
    title: `${plan.value.title}学习方案`,
    desc: '最终确认的学习目标、学习画像与阶段安排。',
    status: '已生成',
    action: '查看',
    fileName: `${plan.value.title}-学习方案.md`,
    content: plan.value.goal,
    source: 'default',
  }
})

const mindMapResource = computed<ResourceWithMeta>(() => {
  return plan.value.resources.find((resource) => resource.group === '思维导图') as ResourceWithMeta | undefined ?? {
    id: -Math.abs(plan.value.id) - 10000,
    group: '思维导图',
    title: `${plan.value.title}知识结构图`,
    desc: '根据最终学习方案自动生成的知识结构图。',
    status: '已生成',
    action: '打开',
    fileName: `${plan.value.title}-思维导图.xmind`,
    source: 'default',
  }
})

const resources = computed<ResourceWithMeta[]>(() => {
  const aiResources = (plan.value.resources as ResourceWithMeta[]).filter((resource) =>
    resource.source === 'ai-conversation' && resource.group !== '学习方案' && resource.group !== '思维导图',
  )
  return [planResource.value, mindMapResource.value, ...aiResources]
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

function iconName(group: string) {
  if (group === '学习方案') return 'notebook'
  if (group === 'PPT') return 'presentation'
  if (group === '思维导图') return 'mind-topic'
  if (group === '代码案例') return 'code'
  if (group === '图片') return 'image'
  return 'file'
}

function typeLabel(resource: ResourceWithMeta) {
  if (resource.group === '学习方案' || resource.group === '个性化学习手册') return 'Markdown'
  if (resource.group === '代码案例') return '代码文件'
  return resource.group
}

function sourceLabel(resource: ResourceWithMeta) {
  return resource.source === 'ai-conversation' ? 'AI 对话生成' : '项目默认生成'
}

function getFallbackTree(resource?: ResourceWithMeta) {
  return {
    data: { text: resource?.title || plan.value.title },
    children: plan.value.dashboard.map((item) => ({ data: { text: item.label }, children: [] })),
  }
}

function getResourceContent(resource: ResourceWithMeta) {
  if (resource.group === '学习方案') {
    return [
      `# ${plan.value.title}学习方案`, '',
      '## 学习目标', plan.value.goal, '',
      '## 个性化画像', plan.value.profile.map((item) => `- ${item.label}：${item.value}`).join('\n'), '',
      '## 学习路径',
      plan.value.stages.map((stage) => `### ${stage.title}\n${stage.desc}\n\n${stage.tasks.map((task) => `- ${task.title}（${task.duration}）`).join('\n')}`).join('\n\n'),
    ].join('\n')
  }
  if (resource.content) return resource.content
  if (resource.group === 'PPT') return `${resource.title}\n\n1. 学习目标\n2. 概念框架\n3. 典型案例\n4. 复盘练习`
  if (resource.group === '代码案例') return resource.content || '// AI 生成的代码内容'
  return resource.desc
}

function setToast(message: string) {
  toastMsg.value = message
  window.setTimeout(() => {
    if (toastMsg.value === message) toastMsg.value = ''
  }, 1800)
}

function openResource(resource: ResourceWithMeta) {
  if (resource.group === '思维导图' && resource.mindMapId) {
    void router.push(`/mindmap/${resource.mindMapId}`)
    return
  }
  previewResource.value = resource
}

function exportResource(resource: ResourceWithMeta) {
  const extensionMap: Record<LearningResource['group'], string> = {
    学习方案: 'md',
    个性化学习手册: 'md',
    PPT: 'pptx',
    思维导图: 'json',
    代码案例: 'java',
    图片: 'png',
  }
  const content = resource.group === '思维导图'
    ? JSON.stringify(resource.mindMapTreeData || getFallbackTree(resource), null, 2)
    : getResourceContent(resource)
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = resource.fileName || `${resource.title}.${extensionMap[resource.group]}`
  link.click()
  URL.revokeObjectURL(url)
  setToast('资源已导出')
}

async function openTutorChat() {
  const conversationId = await tutorStore.ensureConversation(plan.value)
  await router.push({
    path: `/chat/${conversationId}`,
    query: { learningProjectId: String(plan.value.id), tutor: '1' },
  })
}

watch(
  () => route.query.type,
  (type) => {
    if (typeof type !== 'string') return
    const matched = resources.value.find((resource) => resource.group === type)
    if (matched) openResource(matched)
  },
  { immediate: true },
)

onMounted(() => {
  readingTimer = window.setInterval(() => {
    if (sourceTaskId.value && document.visibilityState === 'visible') {
      learningStore.recordTaskReading(plan.value.id, sourceTaskId.value, 100, 1)
    }
  }, 1000)
})

onBeforeUnmount(() => {
  if (readingTimer) window.clearInterval(readingTimer)
})
</script>

<template>
  <StudentShell>
    <div class="resources-page">
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

      <section class="stats">
        <article><strong>{{ resources.length }}</strong><span>资源文件</span></article>
        <article><strong>{{ aiGeneratedCount }}</strong><span>AI 对话新增</span></article>
        <article><strong>{{ latestUpdate }}</strong><span>最近更新时间</span></article>
      </section>

      <div class="content-grid">
        <section class="panel files-panel">
          <div class="section-head">
            <div><h2>资源文件</h2><small>仅展示已生成内容</small></div>
            <label>
              <AppIcon name="search" :size="18" />
              <input v-model="query" placeholder="搜索资源" />
            </label>
          </div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>文件名</th><th>类型</th><th>状态</th><th>来源</th><th>更新时间</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="resource in filteredResources" :key="resource.id" @click="openResource(resource)">
                  <td><AppIcon :name="iconName(resource.group)" :size="18" /><span>{{ resource.fileName || resource.title }}</span></td>
                  <td>{{ typeLabel(resource) }}</td>
                  <td><span class="status" :class="{ active: resource.status === '生成中' }">{{ resource.status }}</span></td>
                  <td>{{ sourceLabel(resource) }}</td>
                  <td>{{ resource.updatedAt || '刚刚' }}</td>
                  <td class="row-actions" @click.stop>
                    <button class="icon-btn" type="button" title="查看" @click="openResource(resource)"><AppIcon name="eye" :size="17" /></button>
                    <button class="icon-btn" type="button" title="下载" @click="exportResource(resource)"><AppIcon name="download" :size="17" /></button>
                    <button class="icon-btn" type="button" title="更多"><AppIcon name="more-horizontal" :size="18" /></button>
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

      <div v-if="previewResource" class="preview-backdrop" @click.self="previewResource = null">
        <section class="preview-dialog" role="dialog" aria-modal="true">
          <header>
            <div><AppIcon :name="iconName(previewResource.group)" :size="22" /><span><strong>{{ previewResource.title }}</strong><small>{{ typeLabel(previewResource) }} · {{ sourceLabel(previewResource) }}</small></span></div>
            <div><button type="button" title="下载" @click="exportResource(previewResource)"><AppIcon name="download" :size="18" /></button><button type="button" title="关闭" @click="previewResource = null"><AppIcon name="close" :size="18" /></button></div>
          </header>
          <div class="preview-body">
            <LearningMindMapPreview v-if="previewResource.group === '思维导图'" :title="previewResource.title" :tree-data="previewResource.mindMapTreeData || getFallbackTree(previewResource)" />
            <img v-else-if="previewResource.group === '图片' && previewResource.previewUrl" :src="previewResource.previewUrl" :alt="previewResource.title" />
            <pre v-else-if="previewResource.group === '代码案例'"><code>{{ getResourceContent(previewResource) }}</code></pre>
            <article v-else class="markdown-body" v-html="renderMarkdownToHtml(getResourceContent(previewResource))" />
          </div>
        </section>
      </div>

      <p v-if="toastMsg" class="toast">{{ toastMsg }}</p>
    </div>
  </StudentShell>
</template>

<style scoped>
.resources-page, .resources-page * { box-sizing: border-box; }
.resources-page { min-height: 100%; padding: 34px 28px 56px; background: var(--color-bg); color: var(--color-text); }
.hero, .stats, .content-grid { max-width: 1180px; margin-inline: auto; }
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
.status { padding: 4px 9px; border-radius: 999px; background: color-mix(in srgb, var(--color-success) 15%, var(--color-surface)); color: var(--color-success); white-space: nowrap; }
.status.active { background: color-mix(in srgb, var(--color-info) 12%, var(--color-surface)); color: var(--color-info); }
.row-actions { white-space: nowrap; }
.icon-btn { border: 0; border-radius: var(--ui-hover-radius); background: transparent; color: var(--color-text-muted); padding: 6px; cursor: pointer; }
.icon-btn:hover { background: var(--ui-hover-strong-bg); color: var(--color-text); }
.empty-row td { height: 110px; text-align: center; color: var(--color-text-muted); }
.summary-panel p { color: var(--color-text-muted); line-height: 1.7; }
.summary-list { margin-top: 16px; display: grid; gap: 12px; }
.summary-list article { border-top: 1px solid var(--color-border); padding-top: 12px; }
.summary-list span { display: block; margin-bottom: 5px; color: var(--color-text-muted); font-size: 13px; }
.summary-list strong { line-height: 1.5; }
.preview-backdrop { position: fixed; inset: 0; z-index: 80; display: grid; place-items: center; padding: 30px; background: rgba(15, 23, 42, .34); }
.preview-dialog { width: min(980px, 94vw); max-height: 90vh; display: flex; flex-direction: column; overflow: hidden; border-radius: 12px; background: var(--color-surface); box-shadow: 0 24px 70px rgba(15, 23, 42, .24); }
.preview-dialog > header { min-height: 66px; display: flex; align-items: center; justify-content: space-between; gap: 18px; border-bottom: 1px solid var(--color-border); padding: 12px 18px; }
.preview-dialog > header > div { display: flex; align-items: center; gap: 10px; }
.preview-dialog header span { display: grid; gap: 3px; }
.preview-dialog header small { color: var(--color-text-muted); }
.preview-dialog header button { width: 34px; height: 34px; display: grid; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--color-text); cursor: pointer; }
.preview-dialog header button:hover { background: var(--ui-hover-bg); }
.preview-body { min-height: 480px; overflow: auto; padding: 28px; }
.preview-body .markdown-body { max-width: 800px; margin: 0 auto; }
.preview-body pre { min-height: 430px; margin: 0; border-radius: 8px; background: #0f172a; color: #dbeafe; padding: 24px; overflow: auto; line-height: 1.7; }
.preview-body img { display: block; max-width: 100%; max-height: 70vh; margin: 0 auto; object-fit: contain; }
.toast { position: fixed; right: 24px; bottom: 24px; z-index: 90; border-radius: 8px; background: var(--color-primary); color: var(--color-on-primary); padding: 10px 14px; box-shadow: var(--shadow-md); }
@media (max-width: 980px) { .hero-card, .stats, .content-grid { grid-template-columns: 1fr; } .hero-actions { flex-wrap: wrap; } .summary-panel { order: -1; } }
@media (max-width: 620px) { .resources-page { padding: 22px 14px 42px; } .hero-card { padding: 18px; } .section-head { align-items: stretch; flex-direction: column; } .section-head label { width: 100%; } .preview-backdrop { padding: 0; } .preview-dialog { width: 100%; max-height: 100vh; height: 100%; border-radius: 0; } }
</style>
