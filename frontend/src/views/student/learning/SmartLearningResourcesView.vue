<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StudentShell from '@/components/layout/StudentShell.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import MarkdownRenderer from '@/components/chat/message/MarkdownRenderer.vue'
import { useSmartLearningStore } from '@/stores/smartLearning'

const route = useRoute()
const router = useRouter()
const store = useSmartLearningStore()
const projectId = String(route.params.id)
const loading = ref(true)
const errorMessage = ref('')
const activeResourceId = ref('')
const resourceNotice = ref('')
const groupTypes = ['READING', 'EXPLANATION', 'EXERCISE', 'REVIEW'] as const
const activeGroup = computed(() => groupTypes.includes(String(route.query.group) as typeof groupTypes[number])
  ? String(route.query.group)
  : 'READING')
const tasks = computed(() => store.workspace?.tasks ?? [])
const groupTasks = computed(() => tasks.value.filter(task => task.taskType === activeGroup.value))
const groupTaskIds = computed(() => new Set(groupTasks.value.map(task => task.taskId)))
const resources = computed(() => (store.workspace?.resources ?? []).filter(resource => groupTaskIds.value.has(resource.taskId)))
const activeResource = computed(() => resources.value.find(item => item.resourceId === activeResourceId.value) ?? resources.value[0])
const activeTask = computed(() => tasks.value.find(task => task.taskId === activeResource.value?.taskId))
const labels: Record<string, string> = { READING: '阅读', EXPLANATION: '讲解', EXERCISE: '练习', REVIEW: '复盘' }

const content = computed(() => {
  const raw = activeResource.value?.content ?? {}
  return String(raw.markdown ?? raw.content ?? raw.text ?? '')
})

const exerciseItems = computed(() => {
  const raw = activeResource.value?.content.items
  if (!Array.isArray(raw)) return []
  const items = raw as Array<Record<string, unknown>>
  const configured = Number(activeTask.value?.payload.questionCount || store.current?.resourceConfig?.questionCount || activeResource.value?.content.questionCount || 0)
  return configured > 0 ? items.slice(0, configured) : items
})
function textOf(value: unknown) { return String(value ?? '') }
function looksLikeCode(value: unknown) {
  const text = textOf(value)
  return text.includes('\n') && /[{};]|=>|<\/?[a-z][^>]*>/i.test(text)
}
function questionText(item: Record<string, unknown>) { return looksLikeCode(item.stem) ? '' : textOf(item.stem) }
function questionCode(item: Record<string, unknown>) { return looksLikeCode(item.stem) ? textOf(item.stem) : '' }
function optionsOf(item: Record<string, unknown>) { return Array.isArray(item.options) ? item.options.map(textOf) : [] }

function selectGroup(group: string) {
  activeResourceId.value = ''
  void router.replace({ query: { ...route.query, group } })
}

function selectResource(resource: { resourceId: string; status: string }) {
  if (resource.status !== 'READY') {
    resourceNotice.value = resource.status === 'FAILED'
      ? '这份资源生成失败，请返回工作台重试后再进入。'
      : '这份资源还在准备中，生成完成后才能进入。'
    return
  }
  activeResourceId.value = resource.resourceId
}

function openResourceTask() {
  if (!activeResource.value || activeResource.value.status !== 'READY') {
    resourceNotice.value = '资源还没有准备好，暂时不能进入任务。'
    return
  }
  void router.push(`/learning/${projectId}/task/${activeResource.value.taskId}`)
}

function guardResourceNavigation(event: MouseEvent) {
  const button = (event.target as HTMLElement | null)?.closest('button')
  if (button?.textContent?.includes('进入任务') && activeResource.value?.status !== 'READY') {
    event.preventDefault()
    event.stopPropagation()
    resourceNotice.value = '资源还没有准备好，暂时不能进入任务。'
  }
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    await store.fetchProject(projectId)
    if (store.current?.stage !== 'READY') {
      await router.replace(`/learning/${projectId}/setup`)
      return
    }
    await store.fetchWorkspace(projectId)
    activeResourceId.value = resources.value[0]?.resourceId ?? ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '资源包加载失败。'
  } finally {
    loading.value = false
  }
}

watch(activeGroup, () => { activeResourceId.value = resources.value[0]?.resourceId ?? '' })
onMounted(() => {
  void load()
  document.addEventListener('click', guardResourceNavigation, true)
})
onBeforeUnmount(() => document.removeEventListener('click', guardResourceNavigation, true))
</script>

<template>
  <StudentShell>
    <main class="resources-page">
      <header class="page-header"><button type="button" aria-label="返回学习工作台" @click="router.push(`/learning/${projectId}`)"><AppIcon name="chevron-left" :size="19" /></button><div><span>智能学习 · 资源包</span><h1>{{ store.workspace?.projectName || '学习资源' }}</h1><p>资料按阅读、讲解、练习和复盘分组，不再把所有内容堆在工作台。</p></div></header>
      <p v-if="errorMessage" class="error-copy">{{ errorMessage }}</p>
      <section v-if="loading" class="resource-layout loading-state"><i v-for="n in 4" :key="n" /></section>
      <section v-else class="resource-layout">
        <aside class="resource-nav"><nav><button v-for="group in groupTypes" :key="group" :class="{ active: activeGroup === group }" type="button" @click="selectGroup(group)"><span>{{ labels[group] }}</span><em>{{ tasks.filter(task => task.taskType === group).length }}</em></button></nav><div class="resource-index"><button v-for="resource in resources" :key="resource.resourceId" :class="{ active: activeResource?.resourceId === resource.resourceId }" type="button" @click="selectResource(resource)"><span><AppIcon :name="resource.kind === 'EXERCISE_SET' ? 'check' : 'file'" :size="16" /></span><span><strong>{{ resource.title }}</strong><small>{{ resource.status === 'READY' ? '已就绪' : resource.status === 'FAILED' ? '生成失败' : '准备中' }}</small></span></button><p v-if="!resources.length">这个分组暂时没有资料。</p></div></aside>
        <article class="resource-detail"><template v-if="activeResource"><header><div><span>{{ labels[activeGroup] }}资料</span><h2>{{ activeResource.title }}</h2><p>{{ activeTask?.title }}</p></div><button type="button" @click="router.push(`/learning/${projectId}/task/${activeResource.taskId}`)">进入任务</button></header><section v-if="activeResource.status === 'READY' && activeResource.kind === 'EXERCISE_SET'" class="resource-content exercise-resource"><article v-for="(item, index) in exerciseItems" :key="String(item.id || index)" class="resource-question"><header><span>第 {{ index + 1 }} 题</span><strong>{{ item.knowledgeKey || '练习题' }}</strong></header><MarkdownRenderer v-if="questionText(item)" :content="questionText(item)" /><pre v-if="questionCode(item)" class="resource-code"><code>{{ questionCode(item) }}</code></pre><div class="resource-options"><span v-for="option in optionsOf(item)" :key="option">{{ option }}</span></div><section><small>正确答案</small><p>{{ item.answer || '暂无' }}</p></section><section><small>解析</small><MarkdownRenderer v-if="item.explanation" :content="textOf(item.explanation)" /><p v-else>暂无解析。</p></section></article></section><section v-else-if="activeResource.status === 'READY'" class="resource-content"><MarkdownRenderer v-if="content" :content="content" /><pre v-else>{{ JSON.stringify(activeResource.content, null, 2) }}</pre></section><section v-else-if="activeResource.status === 'FAILED'" class="resource-empty">{{ activeResource.errorMessage || '资源生成失败，请返回工作台重试。' }}</section><section v-else class="resource-empty"><i /><strong>资源正在准备</strong><p>生成完成后会自动出现在这个位置。</p></section></template><section v-else class="resource-empty"><AppIcon name="folder" :size="26" /><strong>暂无资料</strong><p>可以切换其他分组查看。</p></section></article>
      </section>
    </main>
    <ConfirmDialog :open="Boolean(resourceNotice)" title="资源尚未准备好" :message="resourceNotice" confirm-text="知道了" cancel-text="" @close="resourceNotice = ''" @confirm="resourceNotice = ''" />
  </StudentShell>
</template>

<style scoped>
.resources-page,.resources-page *{box-sizing:border-box}.resources-page{min-height:100%;padding:28px 34px 80px;color:var(--color-text);background:var(--color-bg)}.page-header,.resource-layout,.error-copy{width:min(1280px,100%);margin-inline:auto}.page-header{display:grid;grid-template-columns:42px 1fr;align-items:center;gap:14px}.page-header>button{width:40px;height:40px;display:grid;place-items:center;border:1px solid var(--color-border);border-radius:10px;color:inherit;background:var(--color-surface);cursor:pointer}.page-header span{color:var(--color-text-muted);font-size:12px}.page-header h1{margin:3px 0;font-size:28px}.page-header p{margin:0;color:var(--color-text-muted);font-size:13px}.resource-layout{display:grid;grid-template-columns:330px minmax(0,1fr);align-items:start;gap:16px;margin-top:22px}.resource-nav,.resource-detail{height:max-content;min-width:0;border:1px solid var(--color-border);border-radius:15px;background:var(--color-surface)}.resource-nav{padding:10px}.resource-nav nav{display:grid;grid-template-columns:1fr 1fr;gap:6px;padding-bottom:10px;border-bottom:1px solid var(--color-border)}.resource-nav nav button{display:flex;justify-content:space-between;padding:9px 11px;border:1px solid var(--color-border);border-radius:9px;color:inherit;background:transparent;cursor:pointer}.resource-nav nav button.active{border-color:var(--color-text);background:var(--ui-hover-strong-bg);box-shadow:0 0 0 2px color-mix(in srgb,var(--color-text) 8%,transparent);font-weight:700}.resource-nav em{color:var(--color-text-muted);font-size:11px;font-style:normal}.resource-index{display:grid;gap:4px;padding-top:8px}.resource-index>button{display:grid;grid-template-columns:32px minmax(0,1fr);align-items:center;gap:9px;padding:10px;border:1px solid var(--color-border);border-radius:10px;color:inherit;background:transparent;text-align:left;cursor:pointer}.resource-index>button:hover,.resource-index>button.active{border-color:var(--color-text-muted);background:var(--ui-hover-bg)}.resource-index>button>span:first-child{width:30px;height:30px;display:grid;place-items:center;border-radius:8px;background:var(--ui-hover-strong-bg)}.resource-index>button>span:last-child{min-width:0;display:grid;gap:3px}.resource-index strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.resource-index small,.resource-index>p{color:var(--color-text-muted);font-size:10px}.resource-detail{overflow:hidden}.resource-detail>header{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:20px 24px;border-bottom:1px solid var(--color-border)}.resource-detail header span,.resource-detail header p{color:var(--color-text-muted);font-size:11px}.resource-detail h2{margin:4px 0;font-size:20px}.resource-detail header p{margin:0}.resource-detail header button{padding:8px 12px;border:1px solid var(--color-border);border-radius:9px;color:inherit;background:transparent;cursor:pointer}.resource-content{padding:26px 30px}.resource-content pre{overflow:auto;white-space:pre-wrap}.resource-empty{min-height:220px;display:grid;align-content:center;justify-items:center;gap:8px;color:var(--color-text-muted)}.resource-empty>i{width:180px;height:10px;border-radius:7px;background:var(--ui-hover-strong-bg);animation:pulse 1.2s infinite}.resource-empty p{margin:0}.loading-state{grid-template-columns:1fr}.loading-state i{height:100px;border-radius:15px;background:var(--ui-hover-bg);animation:pulse 1.2s infinite}.error-copy{margin-top:14px;color:var(--color-danger)}.exercise-resource{display:grid;gap:12px}.resource-question{display:grid;gap:10px;padding:16px;border:1px solid var(--color-border);border-radius:12px;background:var(--color-bg)}.resource-question>header{display:flex;justify-content:space-between;align-items:center;gap:10px}.resource-question>header span{color:var(--color-text-muted);font-size:12px}.resource-question>header strong{font-size:12px}.resource-question :deep(p){margin:0;line-height:1.65}.resource-code{margin:0;padding:12px;overflow:auto;border-radius:9px;color:var(--color-text);background:var(--color-surface);font:12px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap}.resource-options{display:grid;gap:6px}.resource-options span{padding:8px 10px;border:1px solid var(--color-border);border-radius:8px;background:var(--color-surface)}.resource-question>section{padding-top:10px;border-top:1px solid var(--color-border)}.resource-question>section small{color:var(--color-text-muted);font-size:11px}.resource-question>section p{margin:5px 0 0}.resource-question>section :deep(p){margin-top:5px}@keyframes pulse{50%{opacity:.5}}@media(max-width:760px){.resources-page{padding-inline:16px}.resource-layout{grid-template-columns:1fr}.resource-detail{min-height:0}.resource-content{padding:20px 16px}}
.resources-page{background:var(--ui-page-canvas-bg)}
</style>
