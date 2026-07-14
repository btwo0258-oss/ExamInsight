<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import LearningDetailShell from '@/components/student/LearningDetailShell.vue'
import LearningMindMapPreview from '@/components/student/LearningMindMapPreview.vue'
import type { LearningResource } from '@/mock'
import { useLearningStore } from '@/stores/learning'

const route = useRoute()
const router = useRouter()
const learningStore = useLearningStore()
const plan = computed(() => learningStore.getPlan(Number(route.params.id)) ?? learningStore.plans[0]!)
const firstResourceId = computed(() => plan.value.resources[0]?.id)
const activeResourceId = ref(firstResourceId.value)
const activeResource = computed(() =>
  plan.value.resources.find((item) => item.id === activeResourceId.value) ?? plan.value.resources[0],
)
const sourceTaskId = computed(() => Number(route.query.task) || undefined)
const sourceStageId = computed(() => plan.value.stages.find((stage) => stage.tasks.some((task) => task.id === sourceTaskId.value))?.id)
const editingResourceId = ref<number | null>(null)
const draftContent = reactive<Record<number, string>>({})
const toastMsg = ref('')
let readingTimer: number | undefined

function iconName(group: string) {
  if (group === 'PPT') return 'presentation'
  if (group === '思维导图') return 'mind-topic'
  if (group === '代码案例') return 'code'
  return 'file'
}

function selectResource(resource: LearningResource) {
  activeResourceId.value = resource.id
  editingResourceId.value = null
}

function isMindMapResource(resource?: LearningResource) {
  return resource?.group === '思维导图'
}

function getFallbackTree(resource?: LearningResource) {
  return {
    data: { text: resource?.title || plan.value.title },
    children: plan.value.dashboard.map((item) => ({
      data: { text: item.label },
      children: [],
    })),
  }
}

function getResourceContent(resource?: LearningResource): string {
  if (!resource) return ''
  const existingDraft = draftContent[resource.id]
  if (existingDraft !== undefined) return existingDraft

  if (resource.group === '个性化学习手册') {
    return [
      `# ${resource.title}`,
      '',
      resource.desc,
      '',
      '## 核心概念',
      plan.value.dashboard.map((item) => `- ${item.label}`).join('\n') || '- 待补充',
      '',
      '## 复习清单',
      plan.value.stages.map((stage) => `- ${stage.title}: ${stage.desc}`).join('\n'),
    ].join('\n')
  }

  if (resource.group === 'PPT') {
    return [
      `${resource.title}`,
      '',
      '1. 学习目标与知识框架',
      '2. 关键概念对比',
      '3. 典型例题或案例',
      '4. 阶段复盘与练习建议',
    ].join('\n')
  }

  if (resource.group === '代码案例') {
    return [
      'class Example {',
      '  public static void main(String[] args) {',
      '    System.out.println("Learning by doing");',
      '  }',
      '}',
    ].join('\n')
  }

  return JSON.stringify(resource.mindMapTreeData || getFallbackTree(resource), null, 2) || ''
}

function setToast(message: string) {
  toastMsg.value = message
  window.setTimeout(() => {
    if (toastMsg.value === message) toastMsg.value = ''
  }, 1800)
}

async function generateActiveResource() {
  if (!activeResource.value) return
  await learningStore.generateResource(plan.value.id, activeResource.value.id)
  setToast(activeResource.value.group === '思维导图' ? '思维导图已生成' : '资源已重新生成')
}

async function viewActiveResource() {
  const resource = activeResource.value
  if (!resource) return

  if (resource.status === '未选择') {
    await generateActiveResource()
    return
  }

  if (isMindMapResource(resource) && resource.mindMapId) {
    router.push(`/mindmap/${resource.mindMapId}`)
    return
  }

  editingResourceId.value = null
  setToast('已在当前预览区打开')
}

async function editActiveResource() {
  const resource = activeResource.value
  if (!resource) return

  if (resource.status === '未选择') {
    await generateActiveResource()
  }

  if (isMindMapResource(resource)) {
    if (resource.mindMapId) router.push(`/mindmap/${resource.mindMapId}`)
    return
  }

  draftContent[resource.id] = getResourceContent(resource)
  editingResourceId.value = editingResourceId.value === resource.id ? null : resource.id
}

function exportActiveResource() {
  const resource = activeResource.value
  if (!resource) return

  const extensionMap: Record<LearningResource['group'], string> = {
    个性化学习手册: 'md',
    PPT: 'md',
    思维导图: 'json',
    代码案例: 'java',
  }
  const blob = new Blob([getResourceContent(resource)], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${resource.fileName || resource.title}.${extensionMap[resource.group]}`
  link.click()
  URL.revokeObjectURL(url)
  setToast('已导出当前资源')
}

watch(
  () => route.query.type,
  (type) => {
    const matched = plan.value.resources.find((item) => item.group === type)
    if (matched) activeResourceId.value = matched.id
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
  <LearningDetailShell
    eyebrow="学习资源"
    title="资源包"
    :subtitle="`${plan.title} · 查看详情、重新生成、导出和编辑`"
    :progress="plan.progress"
    @back="router.push(`/learning/${plan.id}`)"
  >
    <template #actions>
      <button
        class="outline-btn"
        type="button"
        @click="router.push({ path: `/learning/${plan.id}/study`, query: sourceTaskId ? { stage: sourceStageId, task: sourceTaskId } : {} })"
      >
        {{ sourceTaskId ? '返回当前任务' : '继续学习' }}
      </button>
    </template>

    <template #navigation>
      <aside class="resource-list panel">
        <header class="panel-title">
          <div>
            <AppIcon name="folder" :size="20" />
            <h2>资源目录</h2>
          </div>
          <span>{{ plan.resources.length }} 项</span>
        </header>
        <button
          v-for="resource in plan.resources"
          :key="resource.id"
          class="resource-row"
          :class="{ active: resource.id === activeResource?.id }"
          type="button"
          @click="selectResource(resource)"
        >
          <AppIcon :name="iconName(resource.group)" :size="20" />
          <span>
            <strong>{{ resource.group }}</strong>
            <small>{{ resource.title }}</small>
          </span>
          <em :class="{ muted: resource.status === '未选择' }">{{ resource.status }}</em>
        </button>
        <p v-if="!plan.resources.length" class="empty-copy">当前项目还没有生成资源。</p>
      </aside>
    </template>

    <section class="resource-workspace panel">
      <header class="resource-head">
        <div>
          <span class="resource-icon">
            <AppIcon :name="iconName(activeResource?.group ?? '')" :size="24" />
          </span>
          <span>
            <h2>{{ activeResource?.title ?? '未选择资源' }}</h2>
            <p>{{ activeResource?.desc }}</p>
          </span>
        </div>
        <em :class="{ muted: activeResource?.status === '未选择' }">{{ activeResource?.status ?? '未选择' }}</em>
      </header>

      <div class="summary-strip">
        <article>
          <span>资源类型</span>
          <strong>{{ activeResource?.group ?? '-' }}</strong>
        </article>
        <article>
          <span>文件名</span>
          <strong>{{ activeResource?.fileName ?? '等待生成' }}</strong>
        </article>
        <article>
          <span>关联项目</span>
          <strong>{{ plan.title }}</strong>
        </article>
      </div>

      <div class="resource-toolbar">
        <button class="primary-btn" type="button" @click="viewActiveResource">
          <AppIcon name="eye" :size="16" />
          查看详情
        </button>
        <button
          class="outline-btn"
          type="button"
          :disabled="activeResource?.status === '生成中'"
          @click="generateActiveResource"
        >
          <AppIcon name="refresh" :size="16" />
          {{ activeResource?.status === '生成中' ? '生成中...' : activeResource?.status === '未选择' ? '生成资源' : '重新生成' }}
        </button>
        <button class="outline-btn" type="button" @click="exportActiveResource">
          <AppIcon name="download" :size="16" />
          导出
        </button>
        <button class="outline-btn" type="button" @click="editActiveResource">
          <AppIcon name="edit" :size="16" />
          {{ editingResourceId === activeResource?.id ? '完成编辑' : '编辑' }}
        </button>
      </div>

      <section v-if="activeResource?.group === '个性化学习手册'" class="resource-preview handbook-preview">
        <textarea
          v-if="editingResourceId === activeResource.id"
          v-model="draftContent[activeResource.id]"
          class="resource-editor"
        />
        <article v-else>
          <span class="preview-label">学习手册</span>
          <h3>{{ activeResource.title }}</h3>
          <p>{{ activeResource.desc }}</p>
          <h4>核心概念</h4>
          <ul>
            <li v-for="item in plan.dashboard" :key="item.label">{{ item.label }}</li>
          </ul>
          <h4>复习清单</h4>
          <p>{{ plan.stages.map((stage) => stage.title).join('、') }}</p>
        </article>
      </section>

      <section v-else-if="activeResource?.group === 'PPT'" class="resource-preview ppt-preview">
        <textarea
          v-if="editingResourceId === activeResource.id"
          v-model="draftContent[activeResource.id]"
          class="resource-editor"
        />
        <article v-else>
          <span class="preview-label">幻灯片大纲</span>
          <h3>{{ activeResource.title }}</h3>
          <div class="slide-points">
            <span>学习目标</span>
            <span>概念框架</span>
            <span>典型案例</span>
            <span>复盘练习</span>
          </div>
        </article>
      </section>

      <section v-else-if="activeResource?.group === '思维导图'" class="resource-preview mindmap-preview">
        <LearningMindMapPreview
          :title="activeResource.title"
          :tree-data="activeResource.mindMapTreeData || getFallbackTree(activeResource)"
        />
      </section>

      <section v-else-if="activeResource?.group === '代码案例'" class="resource-preview code-preview">
        <textarea
          v-if="editingResourceId === activeResource.id"
          v-model="draftContent[activeResource.id]"
          class="resource-editor code-editor"
        />
        <template v-else>
          <header><span>Example.java</span></header>
          <pre><code>{{ getResourceContent(activeResource) }}</code></pre>
        </template>
      </section>

      <p v-if="toastMsg" class="toast">{{ toastMsg }}</p>
    </section>
  </LearningDetailShell>
</template>

<style scoped>
.resource-workspace,
.resource-workspace * {
  box-sizing: border-box;
}

h2,
h3,
h4,
p {
  margin: 0;
}

button,
textarea {
  font: inherit;
}

.panel {
  border: 1px solid #e1e5ec;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.06);
  padding: 18px;
}

.empty-copy {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}

h2 {
  color: #111827;
  font-size: 18px;
  font-weight: 800;
}

h3 {
  color: #111827;
  font-size: 22px;
  font-weight: 800;
}

h4 {
  margin-top: 22px;
  color: #111827;
  font-size: 15px;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-title > div {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #2563eb;
}

.panel-title > span {
  color: #6b7280;
  font-size: 13px;
}

.resource-row {
  width: 100%;
  min-height: 68px;
  margin-top: 8px;
  border: 1px solid #e1e5ec;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  text-align: left;
  cursor: pointer;
}

.resource-row.active,
.resource-row:hover {
  border-color: #93c5fd;
  background: #eff6ff;
  box-shadow: inset 3px 0 0 #2563eb;
}

.resource-row > span {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.resource-row strong,
.resource-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-row small {
  color: #6b7280;
}

.resource-row em,
.resource-head em {
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: #dcfce7;
  color: #15803d;
  padding: 4px 8px;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.resource-row em.muted,
.resource-head em.muted {
  background: #f3f4f6;
  color: #6b7280;
}

.resource-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid #edf0f4;
  padding-bottom: 16px;
}

.resource-head > div {
  min-width: 0;
  display: flex;
  gap: 12px;
}

.resource-icon {
  width: 46px;
  height: 46px;
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.resource-head span {
  min-width: 0;
}

.resource-head em {
  width: fit-content;
  flex: 0 0 auto;
}

.resource-head p {
  margin-top: 5px;
  color: #6b7280;
  line-height: 1.5;
}

.summary-strip {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.summary-strip article {
  min-height: 68px;
  border: 1px solid #e1e5ec;
  border-radius: 8px;
  background: #f8fafc;
  padding: 10px 12px;
  display: grid;
  gap: 5px;
  align-content: center;
}

.summary-strip span {
  color: #6b7280;
  font-size: 12px;
  font-weight: 800;
}

.summary-strip strong {
  min-width: 0;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-toolbar {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.primary-btn,
.outline-btn {
  height: 38px;
  border-radius: 8px;
  padding: 0 13px;
  cursor: pointer;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.primary-btn {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #fff;
}

.outline-btn {
  border: 1px solid #d8dde5;
  background: #ffffff;
  color: #111827;
}

.outline-btn:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.resource-preview {
  min-height: 360px;
  margin-top: 16px;
  border: 1px solid #e1e5ec;
  border-radius: 8px;
  background: #f8fafc;
  overflow: hidden;
}

.preview-label {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.handbook-preview article,
.ppt-preview article {
  padding: 28px;
}

.handbook-preview p,
.ppt-preview p {
  margin-top: 10px;
  color: #475569;
  line-height: 1.75;
}

.handbook-preview ul {
  margin: 10px 0 0;
  padding-left: 20px;
  color: #475569;
  line-height: 1.8;
}

.slide-points {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 26px;
}

.slide-points span {
  min-height: 92px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #dbeafe;
  color: #1d4ed8;
  font-weight: 800;
  text-align: center;
}

.mindmap-preview {
  height: 430px;
  background-image: radial-gradient(#cbd5e1 1px, transparent 1px);
  background-size: 18px 18px;
}

.code-preview header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #1e293b;
  color: #e2e8f0;
}

.code-preview pre {
  min-height: 310px;
  margin: 0;
  padding: 24px;
  background: #0f172a;
  color: #dbeafe;
  line-height: 1.7;
  overflow: auto;
}

.resource-editor {
  width: 100%;
  min-height: 360px;
  border: 0;
  outline: 0;
  resize: vertical;
  padding: 22px;
  background: #ffffff;
  color: #111827;
  line-height: 1.7;
}

.code-editor {
  background: #0f172a;
  color: #dbeafe;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
}

.toast {
  position: fixed;
  right: 30px;
  bottom: 84px;
  z-index: 20;
  border-radius: 999px;
  background: #111827;
  color: #ffffff;
  padding: 10px 16px;
  font-size: 13px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.18);
}

@media (max-width: 900px) {
  .summary-strip,
  .slide-points {
    grid-template-columns: 1fr;
  }
}
</style>
