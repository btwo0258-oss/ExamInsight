<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Check,
  ChevronLeft,
  ChevronRight,
  Download,
  FileText,
  FolderPlus,
  LoaderCircle,
  Presentation,
  RefreshCw,
  Sparkles,
  X,
} from 'lucide-vue-next'
import PresentationOutlineEditor from '@/components/presentation/PresentationOutlineEditor.vue'
import PresentationSlidePreview from '@/components/presentation/PresentationSlidePreview.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import { isMockDataSource } from '@/config/dataSource'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useLearningStore } from '@/stores/learning'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { usePresentationStore } from '@/stores/presentation'
import type {
  PresentationAspectRatio,
  PresentationAudience,
  PresentationOutlineMode,
  PresentationSlideOutline,
  PresentationStyle,
} from '@/types/contracts/presentation'

type WorkspaceStep = 'config' | 'outline' | 'generating' | 'preview'

const route = useRoute()
const router = useRouter()
const presentationStore = usePresentationStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const learningStore = useLearningStore()
const libraryResourceStore = useLibraryResourceStore()

const step = ref<WorkspaceStep>('config')
const outline = ref<PresentationSlideOutline[]>([])
const selectedSlideIndex = ref(0)
const selectedLibraryId = ref<number | null>(numberQuery('libraryId'))
const localError = ref('')
const successMessage = ref('')

const config = reactive({
  topic: stringQuery('topic'),
  title: stringQuery('title'),
  pageCount: 8,
  outlineMode: 'confirm' as PresentationOutlineMode,
  templateId: 'ink-focus',
  aspectRatio: '16:9' as PresentationAspectRatio,
  style: 'academic' as PresentationStyle,
  audience: 'student' as PresentationAudience,
  language: 'zh-CN',
  sourceText: '',
})

const steps: Array<{ key: WorkspaceStep; label: string }> = [
  { key: 'config', label: '配置' },
  { key: 'outline', label: '大纲' },
  { key: 'generating', label: '生成' },
  { key: 'preview', label: '预览' },
]

const currentStepIndex = computed(() => steps.findIndex((item) => item.key === step.value))
const currentPresentation = computed(() => presentationStore.current)
const selectedTemplate = computed(() => presentationStore.templates.find((item) => item.id === config.templateId))
const selectedPage = computed(() => currentPresentation.value?.previewPages[selectedSlideIndex.value] ?? null)
const isLearningSource = computed(() => numberQuery('learningProjectId') !== null)
const canSaveToLibrary = computed(() => currentPresentation.value?.status === 'ready' && selectedLibraryId.value !== null)

function stringQuery(key: string) {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function numberQuery(key: string) {
  const value = route.query[key]
  const number = typeof value === 'string' ? Number(value) : Number.NaN
  return Number.isFinite(number) ? number : null
}

function showMessage(message: string) {
  successMessage.value = message
  window.setTimeout(() => {
    if (successMessage.value === message) successMessage.value = ''
  }, 2200)
}

function displayKnowledgeBaseName(name: string) {
  return name.replace(/资料库/g, '知识库')
}

function setError(error: unknown, fallback: string) {
  localError.value = error instanceof Error ? error.message : fallback
}

function selectTemplate(templateId: string) {
  config.templateId = templateId
  const template = presentationStore.templates.find((item) => item.id === templateId)
  if (template) config.style = template.style
}

function updateOutline(next: PresentationSlideOutline[]) {
  outline.value = next
}

function cloneOutline(slides: PresentationSlideOutline[]) {
  return slides.map((slide) => ({ ...slide, points: [...slide.points] }))
}

async function createOutline() {
  if (!config.topic.trim() || presentationStore.isSaving) return
  localError.value = ''
  try {
    const presentation = await presentationStore.createAndGenerateOutline({
      topic: config.topic.trim(),
      title: config.title.trim() || config.topic.trim(),
      pageCount: Math.min(30, Math.max(3, Number(config.pageCount) || 8)),
      outlineMode: config.outlineMode,
      templateId: config.templateId,
      aspectRatio: config.aspectRatio,
      style: config.style,
      audience: config.audience,
      language: config.language,
      sourceText: config.sourceText.trim() || undefined,
      conversationId: numberQuery('conversationId'),
      libraryId: selectedLibraryId.value,
      learningProjectId: numberQuery('learningProjectId'),
      learningResourceId: numberQuery('learningResourceId'),
    })
    outline.value = cloneOutline(presentation.outline)
    await router.replace({ path: `/presentations/${presentation.id}`, query: route.query })
    if (config.outlineMode === 'auto') await generatePresentation()
    else step.value = 'outline'
  } catch (error) {
    setError(error, 'PPT 大纲生成失败')
  }
}

async function generatePresentation() {
  if (!outline.value.length || presentationStore.isSaving) return
  localError.value = ''
  step.value = 'generating'
  try {
    await presentationStore.saveOutline(outline.value)
    const presentation = await presentationStore.generate()
    await attachLearningResource(presentation.id, presentation.fileName || `${presentation.config.title}.pptx`)
    selectedSlideIndex.value = 0
    step.value = 'preview'
  } catch (error) {
    setError(error, 'PPT 生成失败')
  }
}

async function retryGeneration() {
  localError.value = ''
  step.value = 'generating'
  try {
    const presentation = await presentationStore.retry()
    await attachLearningResource(presentation.id, presentation.fileName || `${presentation.config.title}.pptx`)
    step.value = 'preview'
  } catch (error) {
    setError(error, 'PPT 重试失败')
  }
}

async function attachLearningResource(presentationId: string, fileName: string) {
  const planId = numberQuery('learningProjectId')
  const resourceId = numberQuery('learningResourceId')
  if (planId === null || resourceId === null) return
  await learningStore.attachPresentationResult(planId, resourceId, presentationId, fileName)
}

async function downloadPresentation() {
  if (!currentPresentation.value) return
  localError.value = ''
  try {
    const blob = await presentationStore.download()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = currentPresentation.value.fileName || `${currentPresentation.value.config.title}.pptx`
    link.click()
    URL.revokeObjectURL(url)
    showMessage('PPT 已开始下载')
  } catch (error) {
    setError(error, 'PPT 下载失败')
  }
}

async function saveToLibrary() {
  if (selectedLibraryId.value === null || !currentPresentation.value) return
  localError.value = ''
  try {
    const presentation = await presentationStore.saveToLibrary(selectedLibraryId.value)
    if (isMockDataSource) {
      libraryResourceStore.addPresentation(
        presentation.id,
        presentation.fileName || `${presentation.config.title}.pptx`,
        isLearningSource.value ? '智能学习生成' : '聊天生成',
        selectedLibraryId.value,
        numberQuery('learningProjectId'),
      )
    } else {
      await libraryResourceStore.fetchList(selectedLibraryId.value)
    }
    showMessage('已保存到知识库')
  } catch (error) {
    setError(error, '保存到知识库失败')
  }
}

function back() {
  const returnTo = stringQuery('returnTo')
  if (returnTo.startsWith('/')) void router.push(returnTo)
  else void router.push('/chat')
}

function returnToOutline() {
  outline.value = cloneOutline(currentPresentation.value?.outline ?? [])
  step.value = 'outline'
}

async function loadExisting(id: string) {
  const presentation = await presentationStore.load(id)
  Object.assign(config, presentation.config)
  config.outlineMode = presentation.config.outlineMode ?? 'confirm'
  selectedLibraryId.value = presentation.libraryId ?? selectedLibraryId.value
  outline.value = cloneOutline(presentation.outline)
  if (presentation.status === 'ready') step.value = 'preview'
  else if (presentation.status === 'outline_ready' || presentation.status === 'cancelled') step.value = 'outline'
  else if (presentation.status === 'outlining' || presentation.status === 'generating') {
    step.value = 'generating'
    try {
      const completed = await presentationStore.resumeActiveJob()
      outline.value = cloneOutline(completed?.outline ?? [])
      step.value = completed?.status === 'ready' ? 'preview' : 'outline'
    } catch (error) {
      setError(error, '恢复 PPT 生成任务失败')
    }
  } else if (presentation.status === 'failed') step.value = 'generating'
}

watch(() => config.topic, (topic) => {
  if (!config.title.trim()) config.title = topic
})

watch(() => currentPresentation.value?.previewPages.length, () => {
  const count = currentPresentation.value?.previewPages.length ?? 0
  if (selectedSlideIndex.value >= count) selectedSlideIndex.value = Math.max(0, count - 1)
})

onMounted(async () => {
  localError.value = ''
  try {
    await Promise.all([
      presentationStore.loadTemplates(),
      knowledgeBaseStore.isInitialized ? Promise.resolve() : knowledgeBaseStore.fetchList(),
    ])
    if (!config.title && config.topic) config.title = config.topic
    const id = typeof route.params.id === 'string' ? route.params.id : ''
    if (id) await loadExisting(id)
  } catch (error) {
    setError(error, 'PPT 工作区加载失败')
  }
})
</script>

<template>
  <StudentShell>
    <div class="presentation-workspace">
      <header class="workspace-header">
        <button class="icon-command" type="button" title="返回" @click="back"><ArrowLeft :size="19" /></button>
        <div>
          <span>PPT 工作区</span>
          <h1>{{ currentPresentation?.config.title || config.title || '新建演示文稿' }}</h1>
        </div>
        <div class="header-status">
          <span v-if="currentPresentation">{{ currentPresentation.config.pageCount }} 页</span>
          <span>{{ config.aspectRatio }}</span>
        </div>
      </header>

      <nav class="stepper" aria-label="PPT 生成步骤">
        <div v-for="(item, index) in steps" :key="item.key" :class="{ active: step === item.key, done: currentStepIndex > index }">
          <span><Check v-if="currentStepIndex > index" :size="14" /><template v-else>{{ index + 1 }}</template></span>
          <strong>{{ item.label }}</strong>
        </div>
      </nav>

      <div v-if="localError || presentationStore.errorMessage" class="workspace-error" role="alert">
        <span>{{ localError || presentationStore.errorMessage }}</span>
        <button type="button" title="关闭" @click="localError = ''; presentationStore.clearError()"><X :size="16" /></button>
      </div>

      <main v-if="step === 'config'" class="config-layout">
        <section class="config-main">
          <div class="section-heading"><FileText :size="19" /><div><h2>内容配置</h2><p>先固定主题和演示要求，再生成可编辑大纲。</p></div></div>
          <label class="field field--wide"><span>演示主题</span><input v-model="config.topic" maxlength="120" placeholder="例如：Java 多态的核心原理与应用" /></label>
          <label class="field field--wide"><span>PPT 标题</span><input v-model="config.title" maxlength="120" placeholder="默认使用演示主题" /></label>
          <div class="field-grid">
            <label class="field"><span>页数</span><input v-model.number="config.pageCount" type="number" min="3" max="30" /></label>
            <label class="field"><span>受众</span><select v-model="config.audience"><option value="student">学生</option><option value="teacher">教师</option><option value="general">通用听众</option><option value="business">答辩/汇报</option></select></label>
            <label class="field"><span>风格</span><select v-model="config.style"><option value="academic">教学清晰</option><option value="minimal">简洁克制</option><option value="vibrant">重点鲜明</option><option value="professional">专业汇报</option></select></label>
            <label class="field"><span>知识库</span><select v-model="selectedLibraryId"><option :value="null">不关联</option><option v-for="library in knowledgeBaseStore.list" :key="library.id" :value="library.id">{{ displayKnowledgeBaseName(library.name) }}</option></select></label>
          </div>
          <div class="field field--wide"><span>生成方式</span><div class="segmented"><button type="button" :class="{ active: config.outlineMode === 'confirm' }" @click="config.outlineMode = 'confirm'">先确认大纲</button><button type="button" :class="{ active: config.outlineMode === 'auto' }" @click="config.outlineMode = 'auto'">自动生成</button></div></div>
          <div class="field field--wide"><span>页面比例</span><div class="segmented"><button type="button" :class="{ active: config.aspectRatio === '16:9' }" @click="config.aspectRatio = '16:9'">16:9 宽屏</button><button type="button" :class="{ active: config.aspectRatio === '4:3' }" @click="config.aspectRatio = '4:3'">4:3 标准</button></div></div>
          <label class="field field--wide"><span>补充资料或要求</span><textarea v-model="config.sourceText" rows="5" maxlength="6000" placeholder="可以粘贴重点、课程要求、汇报背景或必须覆盖的内容。" /></label>
        </section>

        <aside class="template-panel">
          <div class="section-heading"><Presentation :size="19" /><div><h2>选择模板</h2><p>模板只控制视觉表达，不改变内容权限和生成逻辑。</p></div></div>
          <div class="template-list">
            <button v-for="template in presentationStore.templates" :key="template.id" type="button" :class="{ selected: config.templateId === template.id }" @click="selectTemplate(template.id)">
              <span class="template-swatch" :style="{ background: template.backgroundColor, color: template.textColor, borderColor: template.accentColor }"><i :style="{ background: template.accentColor }" /><b>Aa</b></span>
              <span><strong>{{ template.name }}</strong><small>{{ template.description }}</small></span>
              <Check v-if="config.templateId === template.id" :size="17" />
            </button>
          </div>
          <button class="primary-action" type="button" :disabled="!config.topic.trim() || presentationStore.isSaving" @click="createOutline">
            <LoaderCircle v-if="presentationStore.isSaving" class="spin" :size="18" />
            <Sparkles v-else :size="18" />
            {{ presentationStore.isSaving ? `正在生成 ${presentationStore.progress}%` : (config.outlineMode === 'auto' ? '自动生成 PPT' : '生成页面大纲') }}
          </button>
        </aside>
      </main>

      <main v-else-if="step === 'outline'" class="outline-layout">
        <section class="outline-pane">
          <div class="pane-heading"><div><span>步骤 2</span><h2>检查并编辑页面大纲</h2><p>可修改标题、要点、备注和页面顺序。确认后才生成 PPT。</p></div><strong>{{ outline.length }} 页</strong></div>
          <PresentationOutlineEditor :slides="outline" :disabled="presentationStore.isSaving" @update="updateOutline" />
        </section>
        <aside class="outline-summary">
          <div class="summary-template" :style="{ borderColor: selectedTemplate?.accentColor }"><Presentation :size="24" /><strong>{{ selectedTemplate?.name }}</strong><span>{{ config.aspectRatio }} · {{ outline.length }} 页</span></div>
          <dl><div><dt>主题</dt><dd>{{ config.topic }}</dd></div><div><dt>受众</dt><dd>{{ config.audience }}</dd></div><div><dt>知识库</dt><dd>{{ displayKnowledgeBaseName(knowledgeBaseStore.list.find((item) => item.id === selectedLibraryId)?.name || '未关联') }}</dd></div></dl>
          <button class="primary-action" type="button" :disabled="outline.length < 3 || presentationStore.isSaving" @click="generatePresentation"><Sparkles :size="18" />确认大纲并生成</button>
          <button class="secondary-action" type="button" :disabled="presentationStore.isSaving" @click="step = 'config'">返回配置</button>
        </aside>
      </main>

      <main v-else-if="step === 'generating'" class="generating-state">
        <div class="generation-icon"><LoaderCircle v-if="presentationStore.isSaving" class="spin" :size="34" /><RefreshCw v-else :size="32" /></div>
        <span>步骤 3</span>
        <h2>{{ presentationStore.isSaving ? '正在生成演示文稿' : '生成任务已停止' }}</h2>
        <p>{{ presentationStore.isSaving ? '后端将根据已确认大纲生成页面，并保存可恢复的任务状态。' : (localError || presentationStore.errorMessage || '可以重试当前任务或返回大纲。') }}</p>
        <div class="progress-track"><i :style="{ width: `${presentationStore.progress}%` }" /></div>
        <strong>{{ presentationStore.progress }}%</strong>
        <div class="generation-actions">
          <button v-if="presentationStore.isSaving" class="secondary-action" type="button" @click="presentationStore.cancel">停止生成</button>
          <template v-else><button class="secondary-action" type="button" @click="step = 'outline'">返回大纲</button><button class="primary-action" type="button" @click="retryGeneration"><RefreshCw :size="17" />重试生成</button></template>
        </div>
      </main>

      <main v-else class="preview-layout">
        <aside class="slide-list">
          <header><span>页面</span><strong>{{ currentPresentation?.previewPages.length }}</strong></header>
          <button v-for="(page, index) in currentPresentation?.previewPages" :key="page.id" type="button" :class="{ selected: selectedSlideIndex === index }" @click="selectedSlideIndex = index">
            <span>{{ index + 1 }}</span>
            <PresentationSlidePreview :page="page" :aspect-ratio="currentPresentation!.config.aspectRatio" compact />
          </button>
        </aside>

        <section class="slide-stage">
          <div class="preview-toolbar"><div><span>步骤 4</span><strong>{{ selectedPage?.title }}</strong></div><div><button class="icon-command" type="button" title="上一页" :disabled="selectedSlideIndex === 0" @click="selectedSlideIndex--"><ChevronLeft :size="18" /></button><span>{{ selectedSlideIndex + 1 }} / {{ currentPresentation?.previewPages.length }}</span><button class="icon-command" type="button" title="下一页" :disabled="selectedSlideIndex >= (currentPresentation?.previewPages.length || 1) - 1" @click="selectedSlideIndex++"><ChevronRight :size="18" /></button></div></div>
          <PresentationSlidePreview v-if="selectedPage && currentPresentation" :page="selectedPage" :aspect-ratio="currentPresentation.config.aspectRatio" />
          <p v-if="selectedPage?.speakerNotes" class="speaker-notes"><strong>演讲者备注</strong>{{ selectedPage.speakerNotes }}</p>
        </section>

        <aside class="export-panel">
          <div class="result-status"><span><Check :size="18" /></span><div><strong>生成完成</strong><small>{{ currentPresentation?.fileName }}</small></div></div>
          <button class="primary-action" type="button" @click="downloadPresentation"><Download :size="18" />下载 PPTX</button>
          <label class="field"><span>保存到知识库</span><select v-model="selectedLibraryId"><option :value="null">选择知识库</option><option v-for="library in knowledgeBaseStore.list" :key="library.id" :value="library.id">{{ displayKnowledgeBaseName(library.name) }}</option></select></label>
          <button class="secondary-action" type="button" :disabled="!canSaveToLibrary" @click="saveToLibrary"><FolderPlus :size="17" />{{ currentPresentation?.libraryResourceId ? '已保存，重新关联' : '保存到知识库' }}</button>
          <button class="text-action" type="button" @click="returnToOutline">返回修改大纲</button>
        </aside>
      </main>

      <p v-if="successMessage" class="workspace-toast">{{ successMessage }}</p>
    </div>
  </StudentShell>
</template>

<style scoped>
.presentation-workspace,
.presentation-workspace * {
  box-sizing: border-box;
}

.presentation-workspace {
  min-height: 100%;
  padding: 0 28px 48px;
  background: var(--color-bg);
  color: var(--color-text);
}

.workspace-header {
  min-height: 74px;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--color-border);
}

.workspace-header h1,
.workspace-header span,
.section-heading h2,
.section-heading p,
.pane-heading h2,
.pane-heading p,
.generating-state h2,
.generating-state p {
  margin: 0;
}

.workspace-header > div:nth-child(2) > span,
.pane-heading span,
.generating-state > span,
.preview-toolbar > div > span {
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 700;
}

.workspace-header h1 {
  margin-top: 2px;
  font-size: 18px;
}

.header-status {
  display: flex;
  gap: 7px;
}

.header-status span {
  padding: 5px 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  color: var(--color-text-muted);
  font-size: 11px;
}

.icon-command {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}

.icon-command:hover:not(:disabled) {
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
}

.icon-command:disabled {
  opacity: 0.35;
}

.stepper {
  max-width: 760px;
  min-height: 70px;
  margin: 0 auto 22px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  align-items: center;
}

.stepper div {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.stepper div:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 50%;
  left: calc(50% + 38px);
  right: calc(-50% + 38px);
  height: 1px;
  background: var(--color-border);
}

.stepper div > span {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-surface);
}

.stepper .active,
.stepper .done {
  color: var(--color-text);
}

.stepper .active > span,
.stepper .done > span {
  border-color: var(--color-text);
  background: var(--color-text);
  color: var(--color-bg);
}

.workspace-error {
  max-width: 1120px;
  min-height: 40px;
  margin: -8px auto 16px;
  padding: 8px 10px 8px 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--color-border));
  border-radius: 7px;
  background: var(--color-surface);
  color: var(--color-danger);
  font-size: 13px;
}

.workspace-error button {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.config-layout,
.outline-layout,
.preview-layout {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  align-items: start;
  gap: 20px;
}

.config-layout {
  grid-template-columns: minmax(0, 1fr) 350px;
}

.config-main,
.template-panel,
.outline-pane,
.outline-summary,
.slide-list,
.slide-stage,
.export-panel {
  min-width: 0;
}

.config-main,
.template-panel,
.outline-summary,
.export-panel {
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 18px;
}

.section-heading h2,
.pane-heading h2,
.generating-state h2 {
  font-size: 17px;
}

.section-heading p,
.pane-heading p,
.generating-state p {
  margin-top: 4px;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field,
.field--wide {
  display: grid;
  gap: 7px;
}

.field--wide {
  margin-bottom: 14px;
}

.field > span,
.field--wide > span {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.field input,
.field select,
.field textarea,
.field--wide input,
.field--wide select,
.field--wide textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  background: var(--color-bg);
  color: var(--color-text);
  font: inherit;
}

.field input,
.field select,
.field--wide input,
.field--wide select {
  height: 40px;
  padding: 0 11px;
}

.field textarea,
.field--wide textarea {
  padding: 10px 11px;
  line-height: 1.55;
  resize: vertical;
}

.segmented {
  display: inline-grid;
  grid-template-columns: repeat(2, minmax(110px, 1fr));
  width: min(300px, 100%);
  padding: 3px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}

.segmented button {
  min-height: 32px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}

.segmented button.active {
  background: var(--color-surface);
  color: var(--color-text);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
}

.template-list {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
}

.template-list > button {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr) 20px;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  background: transparent;
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
}

.template-list > button:hover,
.template-list > button.selected {
  border-color: var(--color-text-muted);
  background: var(--ui-hover-bg);
}

.template-list > button > span:nth-child(2) {
  display: grid;
  gap: 3px;
}

.template-list small {
  color: var(--color-text-muted);
  font-size: 10px;
  line-height: 1.45;
}

.template-swatch {
  position: relative;
  height: 40px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid;
  border-radius: 5px;
}

.template-swatch i {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
}

.primary-action,
.secondary-action,
.text-action {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 14px;
  border-radius: 7px;
  cursor: pointer;
  font-weight: 600;
}

.primary-action {
  width: 100%;
  border: 1px solid var(--color-text);
  background: var(--color-text);
  color: var(--color-bg);
}

.secondary-action {
  width: 100%;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text);
}

.text-action {
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
}

.primary-action:disabled,
.secondary-action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.outline-layout {
  grid-template-columns: minmax(0, 1fr) 280px;
}

.pane-heading {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.pane-heading > strong {
  min-width: 58px;
  color: var(--color-text-muted);
  font-size: 12px;
  text-align: right;
}

.outline-summary {
  position: sticky;
  top: 18px;
  display: grid;
  gap: 12px;
}

.summary-template {
  display: grid;
  gap: 5px;
  padding: 13px;
  border-left: 4px solid;
  background: var(--color-bg);
}

.summary-template span,
.result-status small {
  color: var(--color-text-muted);
  font-size: 11px;
}

.outline-summary dl {
  display: grid;
  gap: 9px;
  margin: 0;
}

.outline-summary dl div {
  display: grid;
  gap: 3px;
}

.outline-summary dt {
  color: var(--color-text-muted);
  font-size: 10px;
}

.outline-summary dd {
  margin: 0;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.generating-state {
  width: min(560px, 100%);
  margin: 70px auto 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.generation-icon {
  width: 66px;
  height: 66px;
  margin-bottom: 18px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-surface);
}

.generating-state p {
  max-width: 470px;
}

.progress-track {
  width: min(420px, 90%);
  height: 8px;
  margin: 24px 0 9px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--color-hover-strong);
}

.progress-track i {
  display: block;
  height: 100%;
  background: var(--color-text);
  transition: width 0.3s ease;
}

.generation-actions {
  width: min(360px, 100%);
  margin-top: 22px;
  display: flex;
  gap: 9px;
}

.preview-layout {
  grid-template-columns: 190px minmax(0, 1fr) 240px;
}

.slide-list {
  max-height: calc(100vh - 190px);
  overflow: auto;
}

.slide-list header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.slide-list > button {
  width: 100%;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: start;
  gap: 6px;
  margin-bottom: 8px;
  padding: 5px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}

.slide-list > button:hover,
.slide-list > button.selected {
  border-color: var(--color-border);
  background: var(--ui-hover-bg);
}

.slide-stage {
  display: grid;
  align-content: start;
  gap: 12px;
}

.preview-toolbar {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.preview-toolbar > div {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 7px;
}

.preview-toolbar > div:first-child {
  display: grid;
  gap: 2px;
}

.preview-toolbar strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-toolbar span {
  color: var(--color-text-muted);
  font-size: 11px;
  white-space: nowrap;
}

.speaker-notes {
  min-height: 58px;
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.55;
}

.speaker-notes strong {
  display: block;
  margin-bottom: 3px;
  color: var(--color-text);
}

.export-panel {
  display: grid;
  gap: 11px;
}

.result-status {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  margin-bottom: 5px;
}

.result-status > span {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-text);
  color: var(--color-bg);
}

.result-status > div {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.result-status small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-toast {
  position: fixed;
  left: 50%;
  bottom: 24px;
  z-index: 400;
  margin: 0;
  padding: 9px 13px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  transform: translateX(-50%);
  font-size: 13px;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 980px) {
  .config-layout,
  .outline-layout {
    grid-template-columns: 1fr;
  }

  .outline-summary {
    position: static;
  }

  .preview-layout {
    grid-template-columns: 150px minmax(0, 1fr);
  }

  .export-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 680px) {
  .presentation-workspace {
    padding: 0 14px 36px;
  }

  .workspace-header {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .header-status {
    display: none;
  }

  .stepper strong {
    display: none;
  }

  .field-grid,
  .preview-layout {
    grid-template-columns: 1fr;
  }

  .slide-list {
    display: flex;
    max-height: none;
    gap: 8px;
    overflow-x: auto;
  }

  .slide-list header {
    display: none;
  }

  .slide-list > button {
    width: 130px;
    flex: 0 0 130px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spin,
  .progress-track i {
    animation: none;
    transition: none;
  }
}
</style>
