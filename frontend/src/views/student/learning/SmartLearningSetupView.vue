<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import { listAssets, listKnowledgeBases, retryAssetProcessing } from '@/api/assetLibraryV2'
import type { KnowledgeBase, LibraryAsset } from '@/types/contracts/assetLibraryV2'
import { useSmartLearningStore } from '@/stores/smartLearning'
import type { SmartLearningProjectDetail, SmartLearningStage } from '@/types/contracts/smartLearning'

type Json = Record<string, unknown>
type SourceChoice = { assetId: string; versionId: string | null; purpose: string }
type ScopeNode = { id: string; title: string; parentId?: string | null; priority?: string; reason?: string; evidence?: unknown[] }
type PlanTask = { id: string; title: string; conceptIds?: string[]; reason?: string; durationMinutes?: number; completionCriteria?: string; date?: string | null; dependencies?: string[] }

const route = useRoute()
const router = useRouter()
const store = useSmartLearningStore()
const projectId = String(route.params.id || '')
const activeStep = ref(0)
const localError = ref('')
const saving = ref(false)
const assets = ref<LibraryAsset[]>([])
const knowledgeBases = ref<KnowledgeBase[]>([])
const sourceKnowledgeBaseId = ref<string | null>(null)
const selectedAssets = ref<SourceChoice[]>([])
const manualScope = ref('')
const scopeNodes = ref<ScopeNode[]>([])
const diagnosisQuestions = ref<Json[]>([])
const answers = reactive<Record<string, string>>({})
const syncingAnswers = ref(false)
let answerSaveTimer: number | null = null
const skipReason = ref('')
const planTasks = ref<PlanTask[]>([])
const resourceConfig = reactive({ mode: 'rolling', effectiveDays: 2, includeMockExam: false, difficulty: '基础到进阶', questionCount: 20 })

const steps = [
  { title: '学习目标', description: '目标、时间和可用安排', icon: 'target' },
  { title: '学习资料', description: '选择本项目实际使用的文件', icon: 'folder' },
  { title: '学习范围', description: '查看并编辑知识点范围', icon: 'list' },
  { title: '基础诊断', description: '了解当前水平和未知部分', icon: 'bar-chart' },
  { title: '学习计划', description: '调整任务后再确认', icon: 'calendar' },
  { title: '资源配置', description: '决定哪些资源需要准备', icon: 'layers' },
]

const detail = computed(() => store.current)
const stageIndex = computed(() => {
  const indexes: Record<SmartLearningStage, number> = { TARGET_REQUIRED: 0, SOURCES_REQUIRED: 1, SCOPE_REQUIRED: 2, DIAGNOSTIC_REQUIRED: 3, PLAN_REQUIRED: 4, RESOURCE_CONFIG_REQUIRED: 5, READY: 6, ARCHIVED: -1 }
  return detail.value ? indexes[detail.value.stage] : 0
})
function expectedJobKind(step: number) {
  return step === 2 ? 'SCOPE_ANALYSIS' : step === 3 ? 'DIAGNOSIS_GENERATION' : step === 4 ? 'PLAN_GENERATION' : ''
}
const currentJob = computed(() => {
  const job = detail.value?.activeJob
  const expected = expectedJobKind(activeStep.value)
  return job && expected && job.kind === expected ? job : null
})
const isJobRunning = computed(() => {
  const expected = expectedJobKind(activeStep.value)
  return Boolean(expected && store.runningJobId && store.runningJobKind === expected)
    || currentJob.value?.status === 'QUEUED' || currentJob.value?.status === 'RUNNING'
})
const jobKind = computed(() => store.runningJobKind || currentJob.value?.kind || '')

const target = reactive({
  examName: '', examDate: '', timezone: 'Asia/Shanghai', targetScore: '', weeklyMinutes: 300,
  availableDays: ['MONDAY', 'WEDNESDAY', 'SATURDAY'] as string[], blackouts: '', foundation: '基础一般', notes: '',
})

function text(value: unknown) { return value == null ? '' : String(value) }
function syncFromDetail(value: SmartLearningProjectDetail) {
  const targetValue = (Object.keys(value.targetDraft).length ? value.targetDraft : value.target) as Json
  Object.assign(target, {
    examName: text(targetValue.examName), examDate: text(targetValue.examDate), timezone: text(targetValue.timezone) || 'Asia/Shanghai',
    targetScore: text(targetValue.targetScore), weeklyMinutes: Number(targetValue.weeklyMinutes || 300),
    availableDays: Array.isArray(targetValue.availableDays) ? targetValue.availableDays.map(String) : ['MONDAY', 'WEDNESDAY', 'SATURDAY'],
    blackouts: text(targetValue.blackouts), foundation: text(targetValue.foundation) || '基础一般', notes: text(targetValue.notes),
  })
  const sources = (Object.keys(value.sourcesDraft).length ? value.sourcesDraft : value.sources) as Json
  sourceKnowledgeBaseId.value = text(sources.knowledgeBaseId || value.knowledgeBaseId) || null
  selectedAssets.value = Array.isArray(sources.assets) ? sources.assets.map((item: any) => typeof item === 'string' ? { assetId: item, versionId: null, purpose: '' } : { assetId: text(item.assetId), versionId: item.versionId ? text(item.versionId) : null, purpose: text(item.purpose) }) : []
  manualScope.value = text(sources.manualScope)
  const scope = (Object.keys(value.scopeCandidate).length ? value.scopeCandidate : value.scope) as Json
  scopeNodes.value = Array.isArray(scope.nodes) ? scope.nodes as ScopeNode[] : []
  const diagnosis = value.diagnosisCandidate as Json
  diagnosisQuestions.value = Array.isArray(diagnosis.questions) ? diagnosis.questions as Json[] : []
  syncingAnswers.value = true
  Object.keys(answers).forEach(key => delete answers[key])
  const savedAnswers = value.diagnosisAnswersDraft?.answers
  if (Array.isArray(savedAnswers)) {
    savedAnswers.forEach((item: any) => {
      if (item?.questionId != null && item?.answer != null) answers[text(item.questionId)] = text(item.answer)
    })
  }
  syncingAnswers.value = false
  const plan = (Object.keys(value.planCandidate).length ? value.planCandidate : value.plan) as Json
  planTasks.value = Array.isArray(plan.tasks) ? plan.tasks as PlanTask[] : []
  const config = (Object.keys(value.resourceConfigDraft).length ? value.resourceConfigDraft : value.resourceConfig) as Json
  if (Object.keys(config).length) Object.assign(resourceConfig, { mode: text(config.mode) || 'rolling', effectiveDays: Number(config.effectiveDays || 2), includeMockExam: Boolean(config.includeMockExam), difficulty: text(config.difficulty) || '基础到进阶', questionCount: Number(config.questionCount || 20) })
}

function canOpen(index: number) { return index <= stageIndex.value || Boolean(detail.value && index < stageIndex.value) }
function stepStatus(index: number) { if (index < stageIndex.value) return '已完成'; if (index === stageIndex.value) return detail.value?.stage === 'READY' ? '已完成' : '当前'; return '未解锁' }
function selectStep(index: number) { if (canOpen(index)) { activeStep.value = index; localStorage.setItem(`examinsight.smart-learning.step.${projectId}`, String(index)) } }

async function run(action: () => Promise<unknown>) {
  saving.value = true; localError.value = ''
  try { await action() } catch (error) { localError.value = error instanceof Error ? error.message : '操作失败，请重试。' } finally { saving.value = false }
}

async function saveTarget() { await run(async () => { await store.saveTarget(projectId, { ...target }); syncFromDetail(store.current!) }) }
async function confirmTarget() { await run(async () => { await store.saveTarget(projectId, { ...target }); await store.confirmTarget(projectId); syncFromDetail(store.current!); activeStep.value = 1; localStorage.setItem(`examinsight.smart-learning.step.${projectId}`, '1') }) }
async function saveSources() { await run(async () => { await store.saveSources(projectId, { knowledgeBaseId: sourceKnowledgeBaseId.value, assets: selectedAssets.value, manualScope: manualScope.value }); syncFromDetail(store.current!) }) }
async function confirmSources() { await run(async () => { await store.saveSources(projectId, { knowledgeBaseId: sourceKnowledgeBaseId.value, assets: selectedAssets.value, manualScope: manualScope.value }); await store.confirmSources(projectId); syncFromDetail(store.current!); activeStep.value = 2; localStorage.setItem(`examinsight.smart-learning.step.${projectId}`, '2') }) }
async function generateScope() { await run(async () => { await store.startJob(projectId, 'scope'); syncFromDetail(store.current!) }) }
async function saveScope() { await run(async () => { await store.saveScope(projectId, { nodes: scopeNodes.value }); syncFromDetail(store.current!) }) }
async function confirmScope() { await run(async () => { await store.saveScope(projectId, { nodes: scopeNodes.value }); await store.confirmScope(projectId); syncFromDetail(store.current!); activeStep.value = 3; localStorage.setItem(`examinsight.smart-learning.step.${projectId}`, '3') }) }
async function generateDiagnosis() { await run(async () => { await store.startJob(projectId, 'diagnosis'); syncFromDetail(store.current!) }) }
function clearAnswerSaveTimer() {
  if (answerSaveTimer != null) window.clearTimeout(answerSaveTimer)
  answerSaveTimer = null
}
async function submitDiagnosis() { await run(async () => { clearAnswerSaveTimer(); await store.submitDiagnosis(projectId, { answers: Object.entries(answers).map(([questionId, answer]) => ({ questionId, answer })) }); syncFromDetail(store.current!); activeStep.value = 4; localStorage.setItem(`examinsight.smart-learning.step.${projectId}`, '4') }) }
async function skipDiagnosis() { await run(async () => { await store.skipDiagnosis(projectId, skipReason.value); syncFromDetail(store.current!); activeStep.value = 4; localStorage.setItem(`examinsight.smart-learning.step.${projectId}`, '4') }) }
async function generatePlan() { await run(async () => { await store.startJob(projectId, 'plan'); syncFromDetail(store.current!) }) }
async function savePlan() { await run(async () => { await store.savePlan(projectId, { tasks: planTasks.value }); syncFromDetail(store.current!) }) }
async function confirmPlan() { await run(async () => { await store.savePlan(projectId, { tasks: planTasks.value }); await store.confirmPlan(projectId); syncFromDetail(store.current!); activeStep.value = 5; localStorage.setItem(`examinsight.smart-learning.step.${projectId}`, '5') }) }
async function saveResources() { await run(async () => { await store.saveResourceConfig(projectId, { ...resourceConfig }); syncFromDetail(store.current!) }) }
async function confirmResources() { await run(async () => { await store.saveResourceConfig(projectId, { ...resourceConfig }); await store.confirmResourceConfig(projectId); syncFromDetail(store.current!) }) }

function toggleAsset(asset: LibraryAsset) {
  const index = selectedAssets.value.findIndex(item => item.assetId === asset.assetId)
  if (index >= 0) selectedAssets.value.splice(index, 1)
  else selectedAssets.value.push({ assetId: asset.assetId, versionId: asset.version?.versionId || null, purpose: '' })
}
function isSelected(assetId: string) { return selectedAssets.value.some(item => item.assetId === assetId) }

watch(answers, () => {
  if (syncingAnswers.value || !diagnosisQuestions.value.length) return
  clearAnswerSaveTimer()
  answerSaveTimer = window.setTimeout(() => {
    void store.saveDiagnosisAnswers(projectId, {
      answers: Object.entries(answers).map(([questionId, answer]) => ({ questionId, answer })),
    }).catch(() => undefined)
  }, 450)
}, { deep: true })

async function retryAsset(asset: LibraryAsset) {
  localError.value = ''
  try {
    await retryAssetProcessing(asset.assetId)
    assets.value = (await listAssets('library', null, 200)).items
  } catch (error) {
    localError.value = error instanceof Error ? error.message : '重新处理资料失败。'
  }
}
function removeNode(index: number) { scopeNodes.value.splice(index, 1) }
function addNode() { scopeNodes.value.push({ id: `manual-${Date.now()}`, title: '新知识点', parentId: null, priority: '普通', reason: '用户补充' }) }
function addTask() { planTasks.value.push({ id: `manual-${Date.now()}`, title: '新学习任务', durationMinutes: 30, completionCriteria: '完成练习并能复述要点', date: null, dependencies: [] }) }
function jobLabel() { if (currentJob.value?.status === 'FAILED' && !store.runningJobId) return currentJob.value.errorMessage || '分析失败，可重试。'; if (isJobRunning.value) return jobKind.value === 'DIAGNOSIS_GENERATION' ? '正在准备诊断题目…' : jobKind.value === 'PLAN_GENERATION' ? '正在整理学习计划…' : '正在分析学习范围…'; return '' }

onMounted(async () => {
  try {
    const [project, assetPage, kbPage] = await Promise.all([store.fetchProject(projectId), listAssets('library', null, 200), listKnowledgeBases('library', null, 100)])
    syncFromDetail(project); assets.value = assetPage.items; knowledgeBases.value = kbPage.items
    activeStep.value = Math.min(stageIndex.value, Number(localStorage.getItem(`examinsight.smart-learning.step.${projectId}`) || stageIndex.value))
  } catch { /* store error is rendered below */ }
})

onBeforeUnmount(() => {
  clearAnswerSaveTimer()
})
</script>

<template>
  <StudentShell>
    <div class="setup-page">
      <header class="setup-header">
        <button class="back-button" type="button" aria-label="返回学习项目" @click="router.push('/learning')"><AppIcon name="chevron-left" :size="19" /></button>
        <div class="heading"><span class="eyebrow">智能学习 · 准备阶段</span><h1>{{ detail?.name || '学习项目' }}</h1><p>{{ detail?.nextStep || '正在读取项目状态…' }}</p></div>
        <div class="header-state"><span v-if="saving"><span class="dot-spinner" />保存中</span><span v-else-if="detail">{{ detail.stage === 'READY' ? '准备已完成' : '修改会自动保留为草稿' }}</span></div>
      </header>

      <div v-if="store.errorMessage || localError" class="setup-error" role="alert"><AppIcon name="alert-circle" :size="17" /><span>{{ localError || store.errorMessage }}</span><button type="button" @click="localError = ''; store.fetchProject(projectId).then(syncFromDetail)">重试</button></div>
      <div v-if="jobLabel()" class="job-status" :class="{ failed: currentJob?.status === 'FAILED' }"><span v-if="isJobRunning" class="dot-spinner" /><AppIcon v-else name="alert-circle" :size="17" /><span>{{ jobLabel() }}</span><button v-if="currentJob?.status === 'FAILED'" type="button" @click="currentJob?.kind === 'SCOPE_ANALYSIS' ? generateScope() : currentJob?.kind === 'DIAGNOSIS_GENERATION' ? generateDiagnosis() : generatePlan()">重试</button></div>

      <div class="setup-layout">
        <nav class="step-navigation" aria-label="学习准备步骤">
          <button v-for="(step, index) in steps" :key="step.title" type="button" class="step-nav-item" :class="{ active: activeStep === index, completed: index < stageIndex, locked: !canOpen(index) }" :disabled="!canOpen(index)" @click="selectStep(index)">
            <span class="step-number"><AppIcon v-if="index < stageIndex" name="check" :size="15" /><span v-else>{{ index + 1 }}</span></span><span class="step-copy"><strong>{{ step.title }}</strong><small>{{ step.description }}</small></span><em>{{ stepStatus(index) }}</em>
          </button>
        </nav>

        <main class="step-content">
          <section v-if="activeStep === 0" class="step-card">
            <header><span class="step-icon"><AppIcon name="target" :size="19" /></span><div><h2>学习目标</h2><p>先告诉我想达到什么，以及现实中能投入多少时间。</p></div></header>
            <div class="form-grid"><label class="field field-wide"><span>考试或学习目标</span><input v-model="target.examName" placeholder="例如：高数期末考试" /></label><label class="field"><span>截止日期</span><input v-model="target.examDate" type="date" /></label><label class="field"><span>时区</span><select v-model="target.timezone"><option>Asia/Shanghai</option><option>Asia/Tokyo</option><option>UTC</option></select></label><label class="field"><span>目标分数或水平</span><input v-model="target.targetScore" placeholder="例如：85 分 / 能独立完成项目" /></label><label class="field"><span>每周可用分钟</span><input v-model.number="target.weeklyMinutes" type="number" min="1" max="10080" /></label></div>
            <div class="field"><span>通常可以学习的日子</span><div class="day-options"><label v-for="day in [['MONDAY','周一'],['TUESDAY','周二'],['WEDNESDAY','周三'],['THURSDAY','周四'],['FRIDAY','周五'],['SATURDAY','周六'],['SUNDAY','周日']]" :key="day[0]"><input v-model="target.availableDays" type="checkbox" :value="day[0]" />{{ day[1] }}</label></div></div>
            <div class="form-grid"><label class="field field-wide"><span>不可安排的日期 <small>可选，用逗号分隔</small></span><input v-model="target.blackouts" placeholder="例如：2026-09-01, 2026-09-15" /></label><label class="field"><span>自评基础</span><select v-model="target.foundation"><option>尚未接触</option><option>刚入门</option><option>基础薄弱</option><option>基础一般</option><option>较熟练</option></select></label></div>
            <label class="field"><span>补充要求 <small>可选</small></span><textarea v-model="target.notes" rows="3" placeholder="例如：更重视考试真题，不安排晚于 22 点的任务。" /></label>
            <footer><button class="secondary-button" type="button" @click="saveTarget">保存草稿</button><button class="primary-button" type="button" :disabled="saving" @click="confirmTarget">确认目标，选择资料 <AppIcon name="chevron-right" :size="16" /></button></footer>
          </section>

          <section v-else-if="activeStep === 1" class="step-card">
            <header><span class="step-icon"><AppIcon name="folder" :size="19" /></span><div><h2>学习资料</h2><p>只选择本项目真正会使用的文件，知识库本身不会自动全部加入。</p></div></header>
            <label class="field"><span>可选知识库</span><select v-model="sourceKnowledgeBaseId"><option :value="null">不关联知识库</option><option v-for="kb in knowledgeBases" :key="kb.knowledgeBaseId" :value="kb.knowledgeBaseId">{{ kb.name }}</option></select></label>
            <div class="source-toolbar"><strong>资料库文件</strong><span>已选择 {{ selectedAssets.length }} 个</span></div>
            <div class="asset-list"><label v-for="asset in assets" :key="asset.assetId" class="asset-choice" :class="{ selected: isSelected(asset.assetId) }"><input type="checkbox" :checked="isSelected(asset.assetId)" @change="toggleAsset(asset)" /><span class="asset-icon"><AppIcon name="file" :size="17" /></span><span class="asset-choice-copy"><strong>{{ asset.name }}</strong><small>{{ asset.version?.status === 'READY' ? '可用于分析' : '处理中或暂不可用' }}<button v-if="asset.version && asset.version.status !== 'READY'" type="button" @click.stop.prevent="retryAsset(asset)">重试处理</button></small></span><AppIcon v-if="isSelected(asset.assetId)" name="check" :size="16" /></label><div v-if="!assets.length" class="inline-empty">资料库中还没有文件，可以先上传后回来选择。</div></div>
            <label class="field"><span>手动范围 <small>没有可用资料时可以填写</small></span><textarea v-model="manualScope" rows="4" placeholder="例如：变量、函数、DOM 事件和异步编程。" /></label>
            <footer><button class="secondary-button" type="button" @click="saveSources">保存草稿</button><button class="primary-button" type="button" :disabled="saving" @click="confirmSources">确认资料，分析范围 <AppIcon name="chevron-right" :size="16" /></button></footer>
          </section>

          <section v-else-if="activeStep === 2" class="step-card">
            <header><span class="step-icon"><AppIcon name="list" :size="19" /></span><div><h2>学习范围</h2><p>这是基于已确认资料生成的候选范围，可以编辑后再确认。</p></div></header>
            <div v-if="!scopeNodes.length && !isJobRunning" class="empty-step"><AppIcon name="sparkle" :size="25" /><strong>还没有学习范围</strong><span>开始分析后，系统会根据资料整理知识点。</span><button class="primary-button" type="button" @click="generateScope">开始分析范围</button></div>
            <div v-else class="editable-list"><div v-for="(node, index) in scopeNodes" :key="node.id || index" class="editable-row"><span class="row-index">{{ index + 1 }}</span><input v-model="node.title" aria-label="知识点名称" /><select v-model="node.priority"><option>核心</option><option>普通</option><option>了解</option></select><small :title="node.reason">{{ node.reason || '可补充依据' }}</small><button class="icon-button" type="button" title="删除知识点" @click="removeNode(index)"><AppIcon name="trash" :size="16" /></button></div><button class="add-row" type="button" @click="addNode"><AppIcon name="plus" :size="15" />添加知识点</button></div>
            <footer v-if="scopeNodes.length"><button class="secondary-button" type="button" @click="saveScope">保存候选</button><button class="primary-button" type="button" :disabled="saving" @click="confirmScope">确认范围，开始诊断 <AppIcon name="chevron-right" :size="16" /></button></footer>
          </section>

          <section v-else-if="activeStep === 3" class="step-card">
            <header><span class="step-icon"><AppIcon name="bar-chart" :size="19" /></span><div><h2>基础诊断</h2><p>用一轮短诊断了解当前水平；提交前不会显示答案和解析。</p></div></header>
            <div v-if="!diagnosisQuestions.length && !detail?.diagnosis.skipped && !isJobRunning" class="empty-step"><AppIcon name="sparkle" :size="25" /><strong>还没有诊断题目</strong><span>生成后可以保存进度，离开页面也能继续。</span><button class="primary-button" type="button" @click="generateDiagnosis">生成诊断题目</button></div>
            <div v-else-if="diagnosisQuestions.length" class="question-list"><article v-for="(question, index) in diagnosisQuestions" :key="text(question.id) || index" class="question-item"><div><strong>{{ index + 1 }}. {{ question.stem }}</strong><small>{{ question.type === 'short_answer' ? '简答题' : '选择题' }}</small></div><div v-if="Array.isArray(question.options)" class="options"><label v-for="option in question.options" :key="text(option)"><input v-model="answers[text(question.id)]" type="radio" :name="text(question.id)" :value="text(option)" />{{ option }}</label></div><input v-else v-model="answers[text(question.id)]" class="answer-input" placeholder="填写你的答案" /></article><footer><button class="primary-button" type="button" :disabled="saving" @click="submitDiagnosis">提交诊断</button></footer></div>
            <div v-else class="skipped-box"><AppIcon name="info" :size="18" /><span>你已跳过这次诊断：{{ detail?.diagnosis.reason }}</span></div>
            <div v-if="!diagnosisQuestions.length && !detail?.diagnosis.skipped" class="skip-row"><input v-model="skipReason" placeholder="如果暂时不做诊断，请填写原因" /><button class="text-button" type="button" :disabled="!skipReason.trim()" @click="skipDiagnosis">跳过诊断</button></div>
          </section>

          <section v-else-if="activeStep === 4" class="step-card">
            <header><span class="step-icon"><AppIcon name="calendar" :size="19" /></span><div><h2>学习计划</h2><p>先调整候选任务，再确认成为当前计划。</p></div></header>
            <div v-if="!planTasks.length && !isJobRunning" class="empty-step"><AppIcon name="sparkle" :size="25" /><strong>还没有计划候选</strong><span>计划会根据目标、范围和诊断结果安排。</span><button class="primary-button" type="button" @click="generatePlan">生成计划候选</button></div>
            <div v-else class="task-list"><article v-for="(task, index) in planTasks" :key="task.id || index" class="task-row"><span class="row-index">{{ index + 1 }}</span><div class="task-fields"><input v-model="task.title" aria-label="任务名称" /><div><input v-model="task.date" type="date" aria-label="任务日期" /><input v-model.number="task.durationMinutes" type="number" min="15" max="180" aria-label="预计分钟" /><span>分钟</span></div><textarea v-model="task.completionCriteria" rows="2" aria-label="完成标准" /></div><button class="icon-button" type="button" title="删除任务" @click="planTasks.splice(index, 1)"><AppIcon name="trash" :size="16" /></button></article><button class="add-row" type="button" @click="addTask"><AppIcon name="plus" :size="15" />添加任务</button><footer><button class="secondary-button" type="button" @click="savePlan">保存候选</button><button class="primary-button" type="button" :disabled="saving" @click="confirmPlan">确认计划，配置资源 <AppIcon name="chevron-right" :size="16" /></button></footer></div>
          </section>

          <section v-else class="step-card">
            <header><span class="step-icon"><AppIcon name="layers" :size="19" /></span><div><h2>资源配置</h2><p>决定哪些学习资源需要准备，错题变式等内容会在后续按需生成。</p></div></header>
            <div class="config-options"><label class="config-choice" :class="{ selected: resourceConfig.mode === 'rolling' }"><input v-model="resourceConfig.mode" type="radio" value="rolling" /><strong>滚动准备</strong><small>先准备接下来几个有效学习日的资源</small></label><label class="config-choice" :class="{ selected: resourceConfig.mode === 'all' }"><input v-model="resourceConfig.mode" type="radio" value="all" /><strong>一次准备可确定资源</strong><small>提前准备计划中可以确定的内容</small></label></div><div class="form-grid"><label class="field"><span>滚动准备天数</span><input v-model.number="resourceConfig.effectiveDays" type="number" min="1" max="14" /></label><label class="field"><span>练习题数量</span><input v-model.number="resourceConfig.questionCount" type="number" min="0" max="200" /></label><label class="field"><span>题目难度</span><select v-model="resourceConfig.difficulty"><option>基础到进阶</option><option>以基础为主</option><option>以考试难度为主</option></select></label><label class="config-switch"><input v-model="resourceConfig.includeMockExam" type="checkbox" /><span><strong>需要模拟考试</strong><small>资源准备阶段包含一套模拟卷</small></span></label></div><footer><button class="secondary-button" type="button" @click="saveResources">保存草稿</button><button class="primary-button" type="button" :disabled="saving" @click="confirmResources">确认配置，完成准备 <AppIcon name="check" :size="16" /></button></footer>
            <div v-if="detail?.stage === 'READY'" class="ready-summary"><AppIcon name="check" :size="19" /><div><strong>学习准备已完成</strong><span>计划已确认，资源配置已确认，资源待准备。</span></div></div>
          </section>
        </main>
      </div>
    </div>
  </StudentShell>
</template>

<style scoped>
.setup-page { min-height: 100%; padding: 24px 30px 80px; background: var(--color-bg); color: var(--color-text); } .setup-header { width: min(1230px,100%); min-height: 76px; display: flex; align-items: center; gap: 14px; margin: 0 auto; } .back-button, .icon-button { display: grid; place-items: center; border: 0; background: transparent; color: var(--color-text-muted); } .back-button { width: 40px; height: 40px; border: 1px solid var(--color-border); border-radius: 9px; background: var(--color-surface); } .back-button:hover, .icon-button:hover { color: var(--color-text); background: var(--color-hover); } .heading { flex: 1; min-width: 0; } .eyebrow { color: var(--color-primary); font-size: 12px; font-weight: 800; letter-spacing: .08em; } h1,h2,p { margin: 0; } h1 { margin-top: 4px; font-size: 25px; } .heading p { margin-top: 4px; color: var(--color-text-muted); font-size: 14px; } .header-state { color: var(--color-text-muted); font-size: 13px; } .dot-spinner { display: inline-block; width: 13px; height: 13px; margin-right: 6px; border: 2px solid var(--color-border); border-top-color: var(--color-primary); border-radius: 50%; vertical-align: -2px; animation: spin .8s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
.setup-error, .job-status { width: min(1230px,100%); display: flex; align-items: center; gap: 8px; margin: 12px auto 0; padding: 10px 13px; border: 1px solid color-mix(in srgb, var(--color-danger) 30%, var(--color-border)); border-radius: 8px; background: color-mix(in srgb, var(--color-danger) 6%, var(--color-surface)); color: var(--color-danger); } .setup-error span, .job-status span:nth-child(2) { flex: 1; } .setup-error button, .job-status button { border: 0; background: transparent; color: inherit; cursor: pointer; } .job-status { border-color: var(--color-border); background: var(--color-surface); color: var(--color-text-muted); } .job-status.failed { color: var(--color-danger); }
.setup-layout { width: min(1230px,100%); display: grid; grid-template-columns: 255px minmax(0,1fr); gap: 18px; align-items: start; margin: 22px auto 0; } .step-navigation { position: sticky; top: 18px; display: grid; gap: 7px; } .step-nav-item { min-height: 67px; display: grid; grid-template-columns: 30px minmax(0,1fr) auto; align-items: center; gap: 9px; padding: 10px 11px; border: 1px solid transparent; border-radius: 9px; background: transparent; color: var(--color-text-muted); text-align: left; cursor: pointer; } .step-nav-item:not(:disabled):hover { background: var(--color-hover); color: var(--color-text); } .step-nav-item.active { border-color: var(--color-border); background: var(--color-surface); color: var(--color-text); box-shadow: var(--shadow-sm); } .step-nav-item.locked { opacity: .58; cursor: not-allowed; } .step-number { width: 28px; height: 28px; display: grid; place-items: center; border: 1px solid var(--color-border); border-radius: 50%; font-size: 12px; } .step-nav-item.completed .step-number { border-color: var(--color-primary); background: var(--color-primary); color: var(--color-on-primary); } .step-copy { min-width: 0; display: grid; gap: 3px; } .step-copy strong { font-size: 14px; } .step-copy small { overflow: hidden; color: var(--color-text-muted); text-overflow: ellipsis; white-space: nowrap; } .step-nav-item em { font-style: normal; font-size: 11px; white-space: nowrap; }
.step-card { padding: 24px; border: 1px solid var(--color-border); border-radius: 11px; background: var(--color-surface); box-shadow: var(--shadow-sm); } .step-card > header { display: flex; gap: 12px; align-items: flex-start; padding-bottom: 20px; border-bottom: 1px solid var(--color-border); } .step-icon { width: 38px; height: 38px; display: grid; place-items: center; flex: 0 0 38px; border-radius: 9px; background: var(--color-hover); color: var(--color-primary); } .step-card h2 { font-size: 20px; } .step-card header p { margin-top: 5px; color: var(--color-text-muted); font-size: 13px; line-height: 1.5; }
.form-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; margin-top: 20px; } .field { display: grid; gap: 7px; margin-top: 18px; color: var(--color-text); font-weight: 700; } .field-wide { grid-column: 1 / -1; } .field small { color: var(--color-text-muted); font-weight: 400; } .field input, .field select, .field textarea, .answer-input, .skip-row input, .task-fields input, .task-fields textarea, .editable-row input, .editable-row select { width: 100%; border: 1px solid var(--color-border); border-radius: 7px; outline: 0; background: var(--color-bg); color: var(--color-text); } .field input, .field select, .skip-row input, .editable-row input, .editable-row select, .task-fields input { height: 40px; padding: 0 10px; } .field textarea, .task-fields textarea, .answer-input { min-height: 76px; padding: 10px; resize: vertical; } .field input:focus, .field select:focus, .field textarea:focus, .skip-row input:focus { border-color: var(--color-primary); }
.day-options { display: flex; flex-wrap: wrap; gap: 8px; } .day-options label { display: inline-flex; align-items: center; gap: 6px; min-height: 34px; padding: 0 10px; border: 1px solid var(--color-border); border-radius: 7px; background: var(--color-bg); font-size: 13px; font-weight: 400; } .day-options input { accent-color: var(--color-primary); } .step-card footer { display: flex; justify-content: flex-end; gap: 9px; margin-top: 24px; padding-top: 18px; border-top: 1px solid var(--color-border); } .primary-button, .secondary-button { min-height: 39px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; padding: 0 15px; border-radius: 8px; font: inherit; font-weight: 700; cursor: pointer; } .primary-button { border: 1px solid var(--color-primary); background: var(--color-primary); color: var(--color-on-primary); } .secondary-button { border: 1px solid var(--color-border); background: var(--color-surface); color: var(--color-text); } button:disabled { opacity: .55; cursor: not-allowed; }
.source-toolbar { display: flex; justify-content: space-between; align-items: center; margin-top: 20px; } .source-toolbar span { color: var(--color-text-muted); font-size: 13px; } .asset-list { display: grid; gap: 7px; max-height: 340px; overflow: auto; margin-top: 10px; padding: 3px; } .asset-choice { display: flex; align-items: center; gap: 10px; min-height: 58px; padding: 9px 11px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-bg); cursor: pointer; } .asset-choice.selected { border-color: var(--color-primary); background: color-mix(in srgb, var(--color-primary) 6%, var(--color-bg)); } .asset-choice input { accent-color: var(--color-primary); } .asset-icon { width: 31px; height: 31px; display: grid; place-items: center; border-radius: 7px; background: var(--color-hover); } .asset-choice-copy { flex: 1; min-width: 0; display: grid; gap: 3px; } .asset-choice strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } .asset-choice small { display: flex; align-items: center; gap: 8px; color: var(--color-text-muted); } .asset-choice small button { padding: 0; border: 0; background: transparent; color: var(--color-primary); font: inherit; cursor: pointer; } .inline-empty { padding: 20px; color: var(--color-text-muted); text-align: center; }
.empty-step { min-height: 270px; display: grid; place-items: center; align-content: center; gap: 9px; color: var(--color-text-muted); text-align: center; } .empty-step strong { color: var(--color-text); } .empty-step .primary-button { margin-top: 6px; } .editable-list, .task-list { display: grid; gap: 8px; margin-top: 20px; } .editable-row { display: grid; grid-template-columns: 26px minmax(0,1fr) 84px minmax(100px,.65fr) 30px; align-items: center; gap: 8px; } .row-index { color: var(--color-text-muted); font-size: 13px; text-align: center; } .editable-row small { overflow: hidden; color: var(--color-text-muted); text-overflow: ellipsis; white-space: nowrap; } .editable-row input, .editable-row select { background: var(--color-surface); } .add-row { justify-self: start; min-height: 34px; display: inline-flex; align-items: center; gap: 5px; margin-top: 4px; border: 0; background: transparent; color: var(--color-primary); cursor: pointer; }
.question-list { display: grid; gap: 12px; margin-top: 20px; } .question-item { padding: 15px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-bg); } .question-item > div:first-child { display: grid; gap: 5px; line-height: 1.5; } .question-item small { color: var(--color-text-muted); } .options { display: grid; gap: 8px; margin-top: 12px; } .options label { display: flex; gap: 8px; align-items: center; color: var(--color-text); } .options input { accent-color: var(--color-primary); } .answer-input { min-height: 40px; margin-top: 12px; } .skipped-box, .ready-summary { display: flex; align-items: flex-start; gap: 9px; margin-top: 22px; padding: 14px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-bg); color: var(--color-text-muted); } .skip-row { display: flex; gap: 8px; align-items: center; margin-top: 18px; } .skip-row input { flex: 1; }
.task-row { display: grid; grid-template-columns: 26px minmax(0,1fr) 30px; align-items: start; gap: 8px; padding: 12px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-bg); } .task-fields { display: grid; gap: 8px; } .task-fields > div { display: flex; align-items: center; gap: 7px; color: var(--color-text-muted); } .task-fields > div input:first-child { flex: 1; } .task-fields > div input:nth-child(2) { width: 100px; } .task-fields textarea { min-height: 54px; } .config-options { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 11px; margin-top: 20px; } .config-choice { display: grid; gap: 5px; padding: 15px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-bg); cursor: pointer; } .config-choice.selected { border-color: var(--color-primary); background: color-mix(in srgb, var(--color-primary) 6%, var(--color-bg)); } .config-choice input { position: absolute; opacity: 0; } .config-choice small, .config-switch small { color: var(--color-text-muted); } .config-switch { display: flex; align-items: center; gap: 9px; margin-top: 18px; } .config-switch input { accent-color: var(--color-primary); } .config-switch span { display: grid; gap: 3px; } .ready-summary { color: var(--color-success); } .ready-summary div { display: grid; gap: 4px; } .ready-summary span { color: var(--color-text-muted); }
@media (max-width: 850px) { .setup-page { padding: 18px 16px 70px; } .setup-header { align-items: flex-start; } .header-state { display: none; } .setup-layout { grid-template-columns: 1fr; } .step-navigation { position: static; display: flex; overflow-x: auto; padding-bottom: 2px; } .step-nav-item { min-width: 170px; grid-template-columns: 28px minmax(0,1fr); } .step-nav-item em { display: none; } .step-copy small { display: none; } .form-grid, .config-options { grid-template-columns: 1fr; } .field-wide { grid-column: auto; } .editable-row { grid-template-columns: 24px minmax(0,1fr) 75px 30px; } .editable-row small { display: none; } .step-card { padding: 18px; } }
</style>
