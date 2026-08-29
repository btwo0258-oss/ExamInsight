import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import * as api from '@/api/smartLearning'
import { listAssets, listKnowledgeBases, retryAssetProcessing } from '@/api/assetLibraryV2'
import type { KnowledgeBase, LibraryAsset } from '@/types/contracts/assetLibraryV2'
import type { SmartLearningProjectDetail, SmartLearningStage } from '@/types/contracts/smartLearning'
import { useAuthStore } from '@/stores/auth'
import { useLearningDrafts } from './useLearningDrafts'
import { learningErrorMessage } from '@/utils/learningErrors'

type Json = Record<string, unknown>
type Source = { assetId: string; versionId: string | null; purpose: string }
type Node = { id: string; title: string; parentId?: string | null; priority?: string; reason?: string; evidence?: unknown[] }
type Task = { id: string; title: string; conceptIds?: string[]; reason?: string; durationMinutes?: number; completionCriteria?: string; date?: string | null; dependencies?: string[] }
type JobKind = 'scope' | 'diagnosis' | 'plan'
export const preparationSteps = [
  { title: '学习目标', description: '目标、时间和可用安排', icon: 'target' },
  { title: '学习资料', description: '选择本项目实际使用的文件', icon: 'folder' },
  { title: '学习范围', description: '查看并编辑知识点范围', icon: 'list' },
  { title: '基础诊断', description: '了解当前水平和未知部分', icon: 'bar-chart' },
  { title: '学习计划', description: '调整任务后再确认', icon: 'calendar' },
  { title: '资源配置', description: '决定哪些资源需要准备', icon: 'layers' },
]
const indexes: Record<SmartLearningStage, number> = { TARGET_REQUIRED: 0, SOURCES_REQUIRED: 1, SCOPE_REQUIRED: 2, DIAGNOSTIC_REQUIRED: 3, PLAN_REQUIRED: 4, RESOURCE_CONFIG_REQUIRED: 5, READY: 6, ARCHIVED: -1 }
const sectionKeys = ['target', 'sources', 'scope', 'diagnosis', 'plan', 'resources']
const jobKinds: Record<JobKind, string> = { scope: 'SCOPE_ANALYSIS', diagnosis: 'DIAGNOSIS_GENERATION', plan: 'PLAN_GENERATION' }
const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
export const asText = (value: unknown) => value == null ? '' : String(value)
export function localDate() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` }

export function useLearningPreparation(projectId: string) {
  const router = useRouter()
  const route = useRoute()
  const accountId = useAuthStore().user?.id || 'anonymous'
  const storageKey = `examinsight.learning.draft.${accountId}.${projectId}`
  const stepKey = `examinsight.learning.step.${accountId}.${projectId}`
  const detail = ref<SmartLearningProjectDetail | null>(null)
  const loading = ref(true), busy = ref(false), activeStep = ref(0), error = ref('')
  const assets = ref<LibraryAsset[]>([]), knowledgeBases = ref<KnowledgeBase[]>([])
  const assetsLoading = ref(false), assetsError = ref(''), assetCursor = ref<string | null>(null)
  const kbLoading = ref(false), kbError = ref(''), kbCursor = ref<string | null>(null)
  const retryingAssets = reactive(new Set<string>())
  const sourceKnowledgeBaseId = ref<string | null>(null), selectedAssets = ref<Source[]>([]), manualScope = ref('')
  const scopeNodes = ref<Node[]>([]), planTasks = ref<Task[]>([]), questions = ref<Json[]>([])
  const scopeMeta = ref<Json>({}), planMeta = ref<Json>({})
  const answers = ref<Record<string, string>>({}), skipReason = ref(''), skipRequested = ref(false)
  const resourceConfig = reactive({ mode: 'rolling', effectiveDays: 2, includeMockExam: false, difficulty: '基础到进阶', questionCount: 20 })
  const target = reactive({ examName: '', examDate: '', timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai', targetScore: '', weeklyHours: 5, availableDays: ['MONDAY', 'WEDNESDAY', 'SATURDAY'], blackouts: [] as string[], foundation: '基础一般', notes: '' })
  const fieldErrors = ref<Record<string, string>>({})
  const pendingJob = ref<JobKind | ''>(''), jobIssue = ref(''), jobIssueKind = ref('')
  let mounted = true, pollTimer: ReturnType<typeof setTimeout> | undefined, pollEpoch = 0

  const stageIndex = computed(() => detail.value ? indexes[detail.value.stage] : 0)
  const values = computed<Record<string, Json>>(() => ({
    target: { ...target, weeklyMinutes: Math.round(Number(target.weeklyHours) * 60), weeklyHours: undefined, blackouts: target.blackouts.join(', ') },
    sources: { knowledgeBaseId: sourceKnowledgeBaseId.value, assets: selectedAssets.value, manualScope: manualScope.value },
    scope: { ...scopeMeta.value, nodes: scopeNodes.value },
    diagnosis: { answers: Object.entries(answers.value).map(([questionId, answer]) => ({ questionId, answer })), skipReason: skipReason.value, skipRequested: skipRequested.value },
    plan: { ...planMeta.value, tasks: planTasks.value },
    resources: { ...resourceConfig },
  }))
  const savers: Record<string, (id: string, data: Json) => Promise<unknown>> = { target: api.saveSmartLearningTarget, sources: api.saveSmartLearningSources, scope: api.saveSmartLearningScope, diagnosis: api.saveSmartLearningDiagnosisAnswers, plan: api.saveSmartLearningPlan, resources: api.saveSmartLearningResourceConfig }
  const drafts = useLearningDrafts({ storageKey, values, version: (section) => {
    const v = detail.value?.versions || {}
    const upstream = ['target', 'sources', 'scope', 'diagnosis', 'plan', 'resourceConfig'].slice(0, sectionKeys.indexOf(section) + 1)
    return upstream.map(key => v[key] ?? 0).join(':') + (section === 'diagnosis' ? `:${questions.value.map(q => q.id).join(',')}` : '')
  }, save: (section, data) => savers[section]!(projectId, data) })

  function restoreSection(section: string, data: Json) {
    if (section === 'target') Object.assign(target, { examName: asText(data.examName), examDate: asText(data.examDate), timezone: asText(data.timezone) || Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai', targetScore: asText(data.targetScore), weeklyHours: data.weeklyMinutes == null ? 5 : Number(data.weeklyMinutes) / 60, availableDays: Array.isArray(data.availableDays) ? data.availableDays.map(String) : ['MONDAY', 'WEDNESDAY', 'SATURDAY'], blackouts: Array.isArray(data.blackouts) ? data.blackouts.map(String) : asText(data.blackouts).split(/[,，\s]+/).filter(Boolean), foundation: asText(data.foundation) || '基础一般', notes: asText(data.notes) })
    if (section === 'sources') {
      sourceKnowledgeBaseId.value = Object.hasOwn(data, 'knowledgeBaseId') ? asText(data.knowledgeBaseId) || null : detail.value?.knowledgeBaseId || null
      selectedAssets.value = Array.isArray(data.assets) ? data.assets.map((item: any) => typeof item === 'string' ? { assetId: item, versionId: null, purpose: '' } : { assetId: asText(item.assetId), versionId: item.versionId ? asText(item.versionId) : null, purpose: asText(item.purpose) }) : []
      manualScope.value = asText(data.manualScope)
    }
    if (section === 'scope') { const { nodes, ...meta } = data; scopeMeta.value = meta; scopeNodes.value = Array.isArray(nodes) ? clone(nodes) as Node[] : [] }
    if (section === 'diagnosis') {
      answers.value = Object.fromEntries((Array.isArray(data.answers) ? data.answers : []).map((item: any) => [asText(item.questionId), asText(item.answer)]))
      skipReason.value = asText(data.skipReason); skipRequested.value = Boolean(data.skipRequested)
    }
    if (section === 'plan') { const { tasks, ...meta } = data; planMeta.value = meta; planTasks.value = Array.isArray(tasks) ? clone(tasks) as Task[] : [] }
    if (section === 'resources') Object.assign(resourceConfig, { mode: asText(data.mode) || 'rolling', effectiveDays: Number(data.effectiveDays ?? 2), includeMockExam: Boolean(data.includeMockExam), difficulty: asText(data.difficulty) || '基础到进阶', questionCount: Number(data.questionCount ?? 20) })
  }

  function hydrate(project: SmartLearningProjectDetail, only?: string[]) {
    detail.value = project
    questions.value = Array.isArray(project.diagnosisCandidate?.questions) ? clone(project.diagnosisCandidate.questions) as Json[] : []
    const prefer = (draft: Json, confirmed: Json) => Object.keys(draft || {}).length ? draft : confirmed || {}
    const sections: Record<string, Json> = { target: prefer(project.targetDraft, project.target), sources: prefer(project.sourcesDraft, project.sources), scope: prefer(project.scopeCandidate, project.scope), diagnosis: prefer(project.diagnosisAnswersDraft, { answers: project.diagnosis?.items || [] }), plan: prefer(project.planCandidate, project.plan), resources: prefer(project.resourceConfigDraft, project.resourceConfig) }
    for (const key of only || sectionKeys) restoreSection(key, sections[key]!)
  }
  function canOpen(index: number) { return Boolean(detail.value) && index <= stageIndex.value }
  function rememberStep(index: number) { activeStep.value = index; try { sessionStorage.setItem(stepKey, String(index)) } catch { /* navigation still works */ } }
  async function selectStep(index: number) {
    if (!canOpen(index) || busy.value) return
    await drafts.flush()
    rememberStep(index); error.value = ''; fieldErrors.value = {}
  }

  async function loadProject() {
    loading.value = true; error.value = ''
    try {
      const project = await api.getSmartLearningProject(projectId)
      if (!mounted) return
      drafts.initialize(() => hydrate(project), restoreSection)
      let saved = Math.min(5, stageIndex.value)
      try { const raw = sessionStorage.getItem(stepKey); if (raw != null && /^\d$/.test(raw)) saved = Number(raw) } catch { /* optional preference */ }
      const requestedStep = typeof route.query.step === 'string' && /^[0-5]$/.test(route.query.step) ? Number(route.query.step) : null
      if (requestedStep != null) saved = requestedStep
      if (route.query.extend === '1') {
        saved = 4
        planMeta.value = { ...planMeta.value, extensionMode: true, basePlanVersion: project.planVersion }
        drafts.touch('plan')
      }
      rememberStep(Math.max(0, Math.min(5, stageIndex.value, saved)))
      if (project.activeJob && ['QUEUED', 'RUNNING'].includes(project.activeJob.status)) resumeJob(project.activeJob.jobId)
      else if (project.activeJob && ['FAILED', 'UNKNOWN', 'CANCELLED'].includes(project.activeJob.status)) {
        jobIssueKind.value = Object.entries(jobKinds).find(([, value]) => value === project.activeJob?.kind)?.[0] || ''
        jobIssue.value = learningErrorMessage(new Error(project.activeJob.errorMessage || ''), '上一次生成未完成，可以重新查询或重试。')
      }
    } catch (cause) { if (mounted) error.value = learningErrorMessage(cause, '学习项目暂时无法读取，请重试。') }
    finally { if (mounted) loading.value = false }
  }
  async function loadAssets(more = false) {
    if (assetsLoading.value || more && !assetCursor.value) return
    assetsLoading.value = true; assetsError.value = ''
    try {
      const page = await listAssets('library', more ? assetCursor.value : null, 100)
      if (!mounted) return
      assets.value = more ? [...assets.value, ...page.items.filter(item => !assets.value.some(old => old.assetId === item.assetId))] : page.items
      assetCursor.value = page.nextCursor
    } catch (cause) { if (mounted) assetsError.value = learningErrorMessage(cause, '资料列表加载失败，请重试。') }
    finally { if (mounted) assetsLoading.value = false }
  }
  async function loadKnowledgeBases(more = false) {
    if (kbLoading.value || more && !kbCursor.value) return
    kbLoading.value = true; kbError.value = ''
    try {
      const page = await listKnowledgeBases('library', more ? kbCursor.value : null, 100)
      if (!mounted) return
      knowledgeBases.value = more ? [...knowledgeBases.value, ...page.items.filter(item => !knowledgeBases.value.some(old => old.knowledgeBaseId === item.knowledgeBaseId))] : page.items
      kbCursor.value = page.nextCursor
    } catch (cause) { if (mounted) kbError.value = learningErrorMessage(cause, '知识库列表加载失败，请重试。') }
    finally { if (mounted) kbLoading.value = false }
  }
  const knowledgeBaseOptions = computed(() => [
    { value: null, label: '不关联知识库', description: '仍可从资料库选择任意可用文件' },
    ...knowledgeBases.value.map(kb => ({ value: kb.knowledgeBaseId, label: kb.name, description: `${kb.assetCount} 个文件 · 具体资料仍需自行勾选` })),
    ...(sourceKnowledgeBaseId.value && !knowledgeBases.value.some(kb => kb.knowledgeBaseId === sourceKnowledgeBaseId.value) ? [{ value: sourceKnowledgeBaseId.value, label: '已关联的知识库', description: '加载列表后查看名称' }] : []),
  ])
  function isSelected(id: string) { return selectedAssets.value.some(item => item.assetId === id) }
  function assetReady(asset: LibraryAsset) { return asset.status === 'ACTIVE' && asset.version?.status === 'READY' }
  function assetState(asset: LibraryAsset) { if (assetReady(asset)) return '可以选择'; if (/FAIL|REJECT|BLOCK/.test(asset.status + asset.version?.status)) return '处理失败，暂不可选'; return '正在处理，完成后可选' }
  function toggleAsset(asset: LibraryAsset) { if (isSelected(asset.assetId)) selectedAssets.value = selectedAssets.value.filter(item => item.assetId !== asset.assetId); else if (assetReady(asset)) selectedAssets.value.push({ assetId: asset.assetId, versionId: asset.version?.versionId || null, purpose: '' }) }
  async function retryAsset(asset: LibraryAsset) {
    if (retryingAssets.has(asset.assetId)) return
    retryingAssets.add(asset.assetId)
    try { await retryAssetProcessing(asset.assetId); await loadAssets() } catch (cause) { assetsError.value = learningErrorMessage(cause, '资料重新处理失败，请稍后重试。') } finally { retryingAssets.delete(asset.assetId) }
  }
  function validate(section: string) {
    const errors: Record<string, string> = {}
    if (section === 'target') {
      if (!target.examName.trim()) errors.examName = '请填写一个具体的学习目标。'
      if (target.examDate && target.examDate < localDate()) errors.examDate = '截止日期不能早于今天，请重新选择。'
      if (!Number.isFinite(Number(target.weeklyHours)) || Number(target.weeklyHours) <= 0 || Number(target.weeklyHours) > 168) errors.weeklyHours = '每周学习时间应大于 0 小时，且不超过 168 小时。'
      if (!target.availableDays.length) errors.availableDays = '请至少选择一个可以学习的日子。'
    }
    if (section === 'sources' && !selectedAssets.value.length && !manualScope.value.trim()) errors.sources = '请选择至少一个文件，或填写手动学习范围。'
    fieldErrors.value = errors
    if (Object.keys(errors).length) { error.value = '还有内容需要调整，请查看表单中的提示。'; return false }
    return true
  }
  async function confirm(section: string) {
    if (busy.value || pendingJob.value || !validate(section)) return
    busy.value = true; error.value = ''
    try {
      drafts.touch(section)
      if (!await drafts.flush()) return
      const confirmers: Record<string, (id: string) => Promise<SmartLearningProjectDetail>> = { target: api.confirmSmartLearningTarget, sources: api.confirmSmartLearningSources, scope: api.confirmSmartLearningScope, plan: api.confirmSmartLearningPlan, resources: api.confirmSmartLearningResourceConfig }
      const project = await confirmers[section]!(projectId)
      if (!mounted) return
      drafts.replace(() => hydrate(project))
      if (section === 'resources') {
        // The confirmed state is enough to enter the workbench. Resource
        // preparation continues there and is safe to resume after refresh.
        busy.value = false
        const returnTo = typeof route.query.returnTo === 'string' && route.query.returnTo.startsWith('/')
          ? route.query.returnTo
          : `/learning/${projectId}`
        await router.replace(returnTo)
        void api.prepareSmartLearningResources(projectId).catch(() => undefined)
        return
      }
      rememberStep(Math.min(5, stageIndex.value))
    } catch (cause) { if (mounted) error.value = learningErrorMessage(cause) } finally { if (mounted) busy.value = false }
  }
  async function submitDiagnosis(skip = false) {
    if (busy.value || pendingJob.value) return
    if (skip && !skipReason.value.trim()) { error.value = '请说明暂时跳过诊断的原因。'; return }
    busy.value = true; error.value = ''
    try {
      drafts.touch('diagnosis')
      if (!await drafts.flush()) return
      const project = skip ? await api.skipSmartLearningDiagnosis(projectId, skipReason.value) : await api.submitSmartLearningDiagnosis(projectId, values.value.diagnosis!)
      if (!mounted) return
      drafts.replace(() => hydrate(project)); rememberStep(4)
    } catch (cause) { if (mounted) error.value = learningErrorMessage(cause) } finally { if (mounted) busy.value = false }
  }
  function resumeJob(jobId: string) {
    const epoch = ++pollEpoch
    const kind = (pendingJob.value || Object.entries(jobKinds).find(([, value]) => value === detail.value?.activeJob?.kind)?.[0]) as JobKind
    pendingJob.value = kind
    jobIssueKind.value = kind
    let attempts = 0
    async function poll() {
      if (!mounted || epoch !== pollEpoch) return
      try {
        const job = await api.getSmartLearningJob(jobId)
        if (!mounted || epoch !== pollEpoch) return
        if (detail.value) detail.value.activeJob = job
        if (job.status === 'SUCCEEDED') {
          const project = await api.getSmartLearningProject(projectId)
          if (!mounted || epoch !== pollEpoch) return
          const sections = kind === 'diagnosis' ? ['diagnosis'] : [kind]
          drafts.replace(() => hydrate(project, sections), sections)
          pendingJob.value = ''; jobIssue.value = ''; return
        }
        if (['FAILED', 'CANCELLED', 'UNKNOWN'].includes(job.status)) {
          pendingJob.value = ''; jobIssue.value = learningErrorMessage(new Error(job.errorMessage || ''), '本次生成未完成，可以重新尝试。'); return
        }
        if (++attempts >= 180) { pendingJob.value = ''; jobIssue.value = '任务仍在处理中，可点击“查询进度”继续查看，不会重复生成。'; return }
        pollTimer = setTimeout(() => { void poll() }, 1500)
      } catch (cause) { if (mounted) { pendingJob.value = ''; jobIssue.value = learningErrorMessage(cause, '暂时无法查询生成进度，请恢复连接后查询；不会自动重复生成。') } }
    }
    void poll()
  }
  async function generate(kind: JobKind) {
    if (busy.value || pendingJob.value) return
    pendingJob.value = kind; jobIssueKind.value = kind; jobIssue.value = ''; error.value = ''
    if (!await drafts.flush()) { pendingJob.value = ''; return }
    try {
      const accepted = await ({ scope: api.startSmartLearningScope, diagnosis: api.startSmartLearningDiagnosis, plan: api.startSmartLearningPlan }[kind])(projectId)
      if (!mounted) return
      resumeJob(accepted.jobId)
    } catch (cause) { if (mounted) { pendingJob.value = ''; jobIssue.value = learningErrorMessage(cause, '未能开始生成，请重试。') } }
  }
  const jobForStep = computed(() => activeStep.value === 2 ? 'scope' : activeStep.value === 3 ? 'diagnosis' : activeStep.value === 4 ? 'plan' : '')
  const jobError = computed(() => jobIssueKind.value === jobForStep.value ? jobIssue.value : '')
  const isGenerating = computed(() => Boolean(pendingJob.value && pendingJob.value === jobForStep.value))
  const jobUnresolved = computed(() => Boolean(detail.value?.activeJob && ['QUEUED', 'RUNNING', 'UNKNOWN'].includes(detail.value.activeJob.status) && detail.value.activeJob.kind === jobKinds[jobForStep.value as JobKind]))
  const diagnosisState = computed(() => isGenerating.value ? 'generating' : detail.value?.diagnosis.skipped ? 'skipped' : detail.value?.diagnosis.submittedAt ? 'submitted' : questions.value.length ? 'answering' : 'idle')
  function addNode() { scopeNodes.value.push({ id: `manual-${crypto.randomUUID()}`, title: '', parentId: null, priority: '普通', reason: '用户补充' }) }
  function removeNode(index: number) { const id = scopeNodes.value[index]?.id; scopeNodes.value.splice(index, 1); scopeNodes.value.forEach(node => { if (node.parentId === id) node.parentId = null }) }
  function addTask() { planTasks.value.push({ id: `manual-${crypto.randomUUID()}`, title: '', durationMinutes: 30, completionCriteria: '', date: null, dependencies: [] }) }
  function removeTask(index: number) { const id = planTasks.value[index]?.id; planTasks.value.splice(index, 1); planTasks.value.forEach(task => { task.dependencies = task.dependencies?.filter(value => value !== id) }) }
  async function leave() { if (busy.value) return false; await drafts.flush(); return !drafts.backupError.value }
  onBeforeRouteLeave(leave)
  onBeforeRouteUpdate(leave)
  function online() { void drafts.flush() }
  onMounted(() => { void loadProject(); void loadAssets(); void loadKnowledgeBases(); window.addEventListener('online', online) })
  onBeforeUnmount(() => { mounted = false; pollEpoch++; clearTimeout(pollTimer); drafts.dispose(); window.removeEventListener('online', online) })

  return { detail, loading, busy, activeStep, error, fieldErrors, stageIndex, target, sourceKnowledgeBaseId, selectedAssets, manualScope, scopeNodes, planTasks, questions, answers, skipReason, skipRequested, resourceConfig, assets, assetsLoading, assetsError, assetCursor, knowledgeBaseOptions, kbLoading, kbError, kbCursor, retryingAssets, drafts, canOpen, selectStep, loadProject, loadAssets, loadKnowledgeBases, isSelected, assetReady, assetState, toggleAsset, retryAsset, confirm, submitDiagnosis, generate, isGenerating, pendingJob, jobError, jobForStep, jobUnresolved, resumeJob, diagnosisState, addNode, removeNode, addTask, removeTask }
}
