<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import AppInput from '@/components/common/AppInput.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import { courseLibraries } from '@/mock'
import { useLearningStore } from '@/stores/learning'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { renderMarkdownToHtml } from '@/utils/markdown'
import type { LearningProfileData } from '@/types/contracts/learning'

const route = useRoute()
const router = useRouter()
const learningStore = useLearningStore()
const libraryResourceStore = useLibraryResourceStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const prompt = ref('请帮我创建一个智能学习计划。')
const selectedLibraryId = ref(0)
const selectedProjectId = ref<number | null>(null)
const files = ref<File[]>([])
const mediaAssetIds = ref<string[]>([])
const learningConstraintDraft = ref('')
const learningConstraintTags = ref<string[]>(['考试复习'])
const studyPeriod = ref('3 天')
const foundationLevel = ref('基础一般')
const weakPointDraft = ref('')
const weakPointTags = ref<string[]>([])
const dailyTime = ref('每天 60-90 分钟')
const studyDepth = ref('快速复习')
const questionCountPreset = ref<'30' | '60' | '100' | 'custom'>('60')
const customQuestionCount = ref(60)
const supplementDraft = ref('')
const extraRequirement = ref('')
const confirmationDocument = ref('')
const documentEditing = ref(false)
const documentFullscreen = ref(false)
const confirmGenerateOpen = ref(false)
const profileGenerating = ref(false)
const documentGenerating = ref(false)
const projectCreating = ref(false)
const flowError = ref('')
let documentTypeTimer: ReturnType<typeof window.setInterval> | undefined

const availableLibraries = computed(() => knowledgeBaseStore.list.map((item) => {
  const preset = courseLibraries.find((library) => library.id === item.id)
  return { ...item, course: preset?.course ?? item.name, tags: preset?.tags ?? [] }
}))
const selectedLibrary = computed(() => availableLibraries.value.find((item) => item.id === selectedLibraryId.value))
const selectedProject = computed(() => learningStore.getPlan(selectedProjectId.value ?? -1))
const targetTypeOptions = ['考试复习', '课程作业', '面试准备', '项目实战']
const foundationOptions = ['零基础', '基础薄弱', '基础一般', '有一定基础', '只补薄弱点']
const depthOptions = ['快速复习', '系统学习', '刷题强化', '项目实操']
const targetType = computed(() =>
  targetTypeOptions.find((option) => learningConstraintTags.value.includes(option)) ?? '考试复习',
)
const profileFocusText = computed(() => weakPointTags.value.join(' / ') || selectedLibrary.value?.tags.slice(0, 3).join(' / ') || '待确认')
const learningConstraintText = computed(() => learningConstraintTags.value.join(' / ') || '考试复习')
const plannedQuestionCount = computed(() => questionCountPreset.value === 'custom'
  ? Math.max(10, Math.min(200, Math.round(customQuestionCount.value || 60)))
  : Number(questionCountPreset.value))
const difficultyStrategy = computed(() => {
  if (foundationLevel.value.includes('零基础') || foundationLevel.value.includes('薄弱')) return '基础为主'
  if (foundationLevel.value.includes('有一定') || studyDepth.value.includes('刷题') || studyDepth.value.includes('实操')) return '强化提高'
  return '均衡'
})
const difficultyPreview = computed(() => {
  const ratios = difficultyStrategy.value === '基础为主' ? [0.5, 0.4] : difficultyStrategy.value === '强化提高' ? [0.2, 0.5] : [0.3, 0.5]
  const basic = Math.round(plannedQuestionCount.value * ratios[0]!)
  const advanced = Math.round(plannedQuestionCount.value * ratios[1]!)
  return { basic, advanced, challenge: plannedQuestionCount.value - basic - advanced }
})
const confirmationDocumentHtml = computed(() => renderMarkdownToHtml(confirmationDocument.value))

function addWeakPoint(value = weakPointDraft.value) {
  const tags = value
    .split(/[\s,，、/]+/)
    .map((item) => item.trim())
    .filter(Boolean)
  if (!tags.length) return
  weakPointTags.value = Array.from(new Set([...weakPointTags.value, ...tags]))
  weakPointDraft.value = ''
}

function removeWeakPoint(tag: string) {
  weakPointTags.value = weakPointTags.value.filter((item) => item !== tag)
}

function setWeakPointTags(tags: string[]) {
  weakPointTags.value = Array.from(new Set(tags.map((item) => item.trim()).filter(Boolean)))
}

function normalizeTags(tags: string[], fallback = ['考试复习']) {
  const next = Array.from(new Set(tags.map((item) => item.trim()).filter(Boolean)))
  return next.length ? next : fallback
}

function addLearningConstraint(value = learningConstraintDraft.value) {
  const tags = value
    .split(/[\s,，、/]+/)
    .map((item) => item.trim())
    .filter(Boolean)
  if (!tags.length) return
  learningConstraintTags.value = normalizeTags([...learningConstraintTags.value, ...tags])
  learningConstraintDraft.value = ''
}

function removeLearningConstraint(tag: string) {
  const next = learningConstraintTags.value.filter((item) => item !== tag)
  learningConstraintTags.value = normalizeTags(next)
}

function setLearningConstraintTags(tags: string[]) {
  learningConstraintTags.value = normalizeTags(tags)
}

function currentLearningProfile(): LearningProfileData {
  return {
    goal: targetType.value,
    subject: selectedLibrary.value?.course ?? '待识别',
    foundation: foundationLevel.value,
    weakPoints: weakPointTags.value,
    period: studyPeriod.value,
    dailyTime: dailyTime.value,
    preferences: learningConstraintTags.value,
    source: selectedLibrary.value?.name ?? '无',
    extra: extraRequirement.value,
  }
}

function applyGeneratedProfile(profile: LearningProfileData) {
  const goalMap: Record<string, string> = {
    '考试备考': '考试复习',
    '作业 / 科研': '课程作业',
    '职业技能': '面试准备',
    '项目实践': '项目实战',
  }
  setLearningConstraintTags([goalMap[profile.goal] || profile.goal, ...profile.preferences])
  foundationLevel.value = profile.foundation === '尚未接触' ? '零基础' : profile.foundation
  studyPeriod.value = profile.period
  dailyTime.value = profile.dailyTime
  setWeakPointTags(profile.weakPoints)
  extraRequirement.value = profile.extra
  if (profile.preferences.includes('项目实操')) studyDepth.value = '项目实操'
  else if (profile.preferences.includes('练习驱动')) studyDepth.value = '刷题强化'
}

async function requestGeneratedProfile(text: string) {
  return learningStore.generateLearningProfile({
    libraryId: selectedLibraryId.value,
    text,
    currentProfile: currentLearningProfile(),
    source: selectedLibrary.value?.name,
    subject: selectedLibrary.value?.course,
    knowledgeTags: selectedLibrary.value?.tags,
    supplementalRequirement: extraRequirement.value,
    mediaAssetIds: mediaAssetIds.value,
  })
}

async function submitPrompt() {
  if (!prompt.value.trim()) {
    prompt.value = '我下周要考 Java 面向对象，继承、多态和接口分不清，帮我做 3 天复习。'
  }
  profileGenerating.value = true
  confirmationDocument.value = ''
  confirmGenerateOpen.value = false
  documentFullscreen.value = false
  flowError.value = ''
  try {
    const result = await requestGeneratedProfile(prompt.value)
    applyGeneratedProfile(result.profile)
  } catch (error) {
    flowError.value = error instanceof Error ? error.message : '学习画像生成失败'
  } finally {
    profileGenerating.value = false
  }
}

async function handleSupplementInput(
  text: string,
  attachedFiles: File[] = [],
  complete?: (success?: boolean) => void,
) {
  const next = text.trim()
  if (!next && !attachedFiles.length) {
    complete?.(false)
    return
  }
  flowError.value = ''
  if (attachedFiles.length) {
    try {
      const uploaded = await libraryResourceStore.uploadFiles(
        attachedFiles,
        '智能学习上传',
        selectedProjectId.value,
        selectedLibraryId.value || null,
      )
      const nextMediaIds = uploaded
        .filter((item) => item.category === 'image' && item.externalKey)
        .map((item) => item.externalKey!)
      mediaAssetIds.value = [...new Set([...mediaAssetIds.value, ...nextMediaIds])]
      files.value.push(...attachedFiles)
    } catch (error) {
      flowError.value = error instanceof Error ? error.message : '学习资料上传失败'
      complete?.(false)
      return
    }
  }
  if (!next) {
    complete?.(true)
    return
  }
  supplementDraft.value = next
  complete?.(await applySupplement())
}

async function applySupplement() {
  const text = supplementDraft.value.trim()
  if (!text) {
    return false
  }
  extraRequirement.value = text
  profileGenerating.value = true
  flowError.value = ''
  try {
    const result = await requestGeneratedProfile(`${prompt.value} ${text}`)
    applyGeneratedProfile(result.profile)
    if (confirmationDocument.value) await startConfirmationDocumentGeneration()
    return true
  } catch (error) {
    flowError.value = error instanceof Error ? error.message : '补充要求分析失败'
    return false
  } finally {
    profileGenerating.value = false
  }
}

async function startConfirmationDocumentGeneration() {
  if (!selectedLibrary.value || documentGenerating.value) return
  if (documentTypeTimer) window.clearInterval(documentTypeTimer)
  confirmationDocument.value = ''
  documentEditing.value = false
  documentGenerating.value = true
  confirmGenerateOpen.value = false
  flowError.value = ''
  let fullDocument = ''
  try {
    fullDocument = await learningStore.generateLearningConfirmation({
      libraryId: selectedLibraryId.value,
      goal: prompt.value.trim(),
      profile: currentLearningProfile(),
      uploadedFileNames: files.value.map((file) => file.name),
      mediaAssetIds: mediaAssetIds.value,
      relatedProjectName: selectedProject.value?.title,
      questionCount: plannedQuestionCount.value,
      difficultyStrategy: difficultyStrategy.value,
    })
  } catch (error) {
    documentGenerating.value = false
    flowError.value = error instanceof Error ? error.message : '学习方案确认稿生成失败'
    return
  }
  let cursor = 0
  documentTypeTimer = window.setInterval(() => {
    cursor += 6
    confirmationDocument.value = fullDocument.slice(0, cursor)
    if (cursor >= fullDocument.length) {
      if (documentTypeTimer) window.clearInterval(documentTypeTimer)
      documentTypeTimer = undefined
      confirmationDocument.value = fullDocument
      documentGenerating.value = false
    }
  }, 18)
}

function regenerateConfirmationDocument() {
  void startConfirmationDocumentGeneration()
}

async function copyConfirmationDocument() {
  await navigator.clipboard?.writeText(confirmationDocument.value)
}

function downloadConfirmationDocument() {
  const blob = new Blob([confirmationDocument.value], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${selectedLibrary.value?.course ?? '学习方案'}-确认稿.md`
  link.click()
  URL.revokeObjectURL(url)
}

async function createProject() {
  if (projectCreating.value || !selectedLibrary.value) return
  projectCreating.value = true
  confirmGenerateOpen.value = false
  flowError.value = ''
  try {
  const plan = await learningStore.createPlan({
    prompt: confirmationDocument.value.trim() || prompt.value.trim(),
    libraryId: selectedLibraryId.value,
    projectId: selectedProjectId.value,
    targetType: targetType.value,
    preferences: learningConstraintTags.value,
    resourceGroups: ['思维导图'],
    period: studyPeriod.value,
    foundation: foundationLevel.value,
    weakPoints: profileFocusText.value,
    dailyTime: dailyTime.value,
    studyDepth: studyDepth.value,
    questionCount: plannedQuestionCount.value,
    supplementalRequirement: extraRequirement.value,
  })
  const mindMap = plan.resources.find((resource) => resource.group === '思维导图')
  if (mindMap) {
    void learningStore.generateResource(plan.id, mindMap.id).catch((error) => {
      console.error('Failed to generate mind map resource:', error)
    })
  }
  await router.push(`/learning/${plan.id}`)
  } catch (error) {
    flowError.value = error instanceof Error ? error.message : '学习项目生成失败'
  } finally {
    projectCreating.value = false
  }
}

onBeforeUnmount(() => {
  if (documentTypeTimer) window.clearInterval(documentTypeTimer)
})

async function loadInitialData() {
  try {
    await Promise.all([knowledgeBaseStore.fetchList(), learningStore.fetchPlans()])
    const requestedLibraryId = Number(route.query.libraryId)
    const projectId = Number(route.query.projectId)
    const project = learningStore.getPlan(projectId)
    if (project) selectedProjectId.value = project.id
    const nextLibraryId = project?.libraryId || requestedLibraryId
    selectedLibraryId.value = availableLibraries.value.some((item) => item.id === nextLibraryId)
      ? nextLibraryId
      : availableLibraries.value[0]?.id ?? 0
    if (project) {
      prompt.value = project.goal && !project.goal.startsWith('待通过')
        ? project.goal
        : `为「${project.title}」创建智能学习计划。`
      await submitPrompt()
    }
  } catch (error) {
    flowError.value = error instanceof Error ? error.message : '初始化智能学习失败'
  }
}

onMounted(() => void loadInitialData())

watch(
  () => route.query.libraryId,
  (value) => {
    const libraryId = Number(value)
    if (availableLibraries.value.some((item) => item.id === libraryId)) selectedLibraryId.value = libraryId
  },
  { immediate: true },
)

watch(
  () => route.query.projectId,
  (value) => {
    const projectId = Number(value)
    const project = learningStore.getPlan(projectId)
    if (!project || project.id === selectedProjectId.value) return
    selectedProjectId.value = project.id
    if (availableLibraries.value.some((item) => item.id === project.libraryId)) {
      selectedLibraryId.value = project.libraryId
    }
    prompt.value = project.goal && !project.goal.startsWith('待通过')
      ? project.goal
      : `为「${project.title}」创建智能学习计划。`
    void submitPrompt()
  },
  { immediate: true },
)
</script>

<template>
  <StudentShell>
    <div class="learning-home">
      <div v-if="flowError" class="flow-error" role="alert">
        <span>{{ flowError }}</span>
        <button type="button" aria-label="关闭" @click="flowError = ''"><AppIcon name="close" :size="14" /></button>
      </div>
      <section v-if="!knowledgeBaseStore.isLoading && !availableLibraries.length" class="flow-empty">
        <strong>暂无可用知识库</strong>
        <span>请先在资料库中创建知识库并上传学习资料。</span>
        <button type="button" @click="router.push('/library')">前往资料库</button>
      </section>
      <section v-if="availableLibraries.length" class="profile-flow">
        <div class="chat-row chat-row--user">
          <div class="message message--user">{{ prompt }}</div>
          <span class="avatar">学</span>
        </div>
        <div class="chat-row">
          <span class="avatar avatar--ai"><AppIcon name="brain" :size="18" /></span>
          <div class="message">我会根据你的对话提取学习约束和学习画像。没提到的部分先用默认标签，确认后在下方生成可编辑确认稿。</div>
        </div>

        <div class="profile-chat-input">
          <AppInput
            :disabled="profileGenerating || documentGenerating || projectCreating || !selectedLibrary"
            :media-enabled="true"
            media-purpose="learning-input"
            :media-context="{
              libraryId: selectedLibraryId || null,
              learningProjectId: selectedProjectId,
            }"
            placeholder="继续补充要求"
            @send="handleSupplementInput"
          />
        </div>

        <section class="detail-panel">
          <div class="panel-title">
            <h2>学习画像确认</h2>
          </div>
          <div v-if="profileGenerating" class="detail-grid skeleton-grid">
            <span v-for="item in 8" :key="item"></span>
          </div>
          <div v-else class="detail-grid">
            <label>
              <span>计划周期</span>
              <input v-model="studyPeriod" placeholder="例如：3 天" />
            </label>
            <label>
              <span>当前基础</span>
              <select v-model="foundationLevel">
                <option v-for="option in foundationOptions" :key="option">{{ option }}</option>
              </select>
            </label>
            <label>
              <span>每日时间</span>
              <input v-model="dailyTime" placeholder="例如：每天 60 分钟" />
            </label>
            <label>
              <span>输出深度</span>
              <select v-model="studyDepth">
                <option v-for="option in depthOptions" :key="option">{{ option }}</option>
              </select>
            </label>
            <label>
              <span>计划练习总量</span>
              <select v-model="questionCountPreset">
                <option value="30">30 题 · 轻量复习</option>
                <option value="60">60 题 · 标准学习</option>
                <option value="100">100 题 · 系统训练</option>
                <option value="custom">自定义题量</option>
              </select>
            </label>
            <label v-if="questionCountPreset === 'custom'">
              <span>自定义题量（10～200）</span>
              <input v-model.number="customQuestionCount" type="number" min="10" max="200" />
            </label>
            <label class="detail-field--wide">
              <span>学习目标与约束</span>
              <div class="tag-input">
                <button v-for="tag in learningConstraintTags" :key="tag" type="button" @click="removeLearningConstraint(tag)">
                  {{ tag }}
                  <AppIcon name="close" :size="12" />
                </button>
                <input
                  v-model="learningConstraintDraft"
                  placeholder="输入后回车生成标签"
                  @blur="addLearningConstraint()"
                  @keydown.enter.prevent="addLearningConstraint()"
                />
              </div>
            </label>
            <label class="detail-field--wide">
              <span>薄弱知识点</span>
              <div class="tag-input">
                <button v-for="tag in weakPointTags" :key="tag" type="button" @click="removeWeakPoint(tag)">
                  {{ tag }}
                  <AppIcon name="close" :size="12" />
                </button>
                <input
                  v-model="weakPointDraft"
                  placeholder="输入后回车生成标签"
                  @blur="addWeakPoint()"
                  @keydown.enter.prevent="addWeakPoint()"
                />
              </div>
            </label>
            <label class="detail-field--wide">
              <span>预计难度分布</span>
              <strong class="difficulty-text">基础 {{ difficultyPreview.basic }} 题、进阶 {{ difficultyPreview.advanced }} 题、挑战 {{ difficultyPreview.challenge }} 题 · {{ difficultyStrategy }}</strong>
            </label>
          </div>
          <footer>
            <button class="primary-btn" type="button" :disabled="profileGenerating || documentGenerating || !selectedLibrary" @click="startConfirmationDocumentGeneration">生成学习方案确认稿</button>
          </footer>
        </section>

        <section v-if="confirmationDocument || documentGenerating" class="document-flow" :class="{ fullscreen: documentFullscreen }">
          <section class="document-panel">
            <div class="document-head">
              <div>
                <h2>学习方案确认稿</h2>
                <p>确认后会基于当前文档生成学习路径，思维导图默认自动生成。</p>
              </div>
              <div class="document-actions">
                <button class="outline-btn document-edit-btn" type="button" :disabled="documentGenerating" @click="documentEditing = !documentEditing">
                  <AppIcon name="edit" :size="16" />
                  {{ documentEditing ? '预览' : '编辑' }}
                </button>
                <button type="button" title="复制" :disabled="documentGenerating" @click="copyConfirmationDocument">
                  <AppIcon name="copy" :size="17" />
                </button>
                <button type="button" title="下载 Markdown" :disabled="documentGenerating" @click="downloadConfirmationDocument">
                  <AppIcon name="download" :size="17" />
                </button>
                <button type="button" :title="documentFullscreen ? '退出全屏' : '全屏'" @click="documentFullscreen = !documentFullscreen">
                  <AppIcon :name="documentFullscreen ? 'minimize' : 'maximize'" :size="17" />
                </button>
                <button v-if="documentFullscreen" type="button" title="关闭" @click="documentFullscreen = false">
                  <AppIcon name="close" :size="17" />
                </button>
              </div>
            </div>

            <textarea
              v-if="documentEditing"
              v-model="confirmationDocument"
              class="document-editor"
              spellcheck="false"
            />
            <article v-else class="document-preview" :class="{ typing: documentGenerating }" v-html="confirmationDocumentHtml" />

            <footer class="document-confirm">
              <button class="outline-btn" type="button" @click="documentFullscreen = false">继续补充要求</button>
              <button class="outline-btn" type="button" :disabled="documentGenerating" @click="regenerateConfirmationDocument">重新生成确认稿</button>
              <button class="primary-btn" type="button" :disabled="documentGenerating || projectCreating" @click="confirmGenerateOpen = true">
                {{ projectCreating ? '生成学习项目中…' : '确认生成' }}
              </button>
            </footer>
          </section>
        </section>
      </section>
      <div v-if="projectCreating" class="project-generating" aria-live="polite">
        <AppIcon name="sparkle" :size="18" />
        <span>正在生成学习路径、练习题和资源，请稍候…</span>
      </div>
    </div>

    <ConfirmDialog
      :open="confirmGenerateOpen"
      title="确认生成学习项目"
      message="系统将基于当前确认稿生成学习路径、练习安排和思维导图。确认后会进入学习路径详情页。"
      confirm-text="确认生成"
      cancel-text="继续修改"
      @close="confirmGenerateOpen = false"
      @confirm="createProject"
    />
  </StudentShell>
</template>

<style scoped>
.learning-home {
  min-height: 100%;
  background: var(--color-bg);
  padding: 48px 32px;
}

.learning-home,
.learning-home * {
  box-sizing: border-box;
}

.flow-error,
.flow-empty,
.project-generating {
  width: min(980px, 100%);
  margin: 0 auto 16px;
}

.flow-error,
.project-generating {
  min-height: 40px;
  padding: 9px 11px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.flow-error {
  border-color: color-mix(in srgb, var(--color-danger) 35%, var(--color-border));
  color: var(--color-danger);
}

.flow-error button {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.flow-empty {
  min-height: 420px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 9px;
  color: var(--color-text-muted);
  text-align: center;
}

.flow-empty strong {
  color: var(--color-text);
  font-size: 18px;
}

.flow-empty button {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.project-generating {
  justify-content: flex-start;
  color: var(--color-text);
}

h1,
h2,
p {
  margin: 0;
}

button {
  font: inherit;
}

.chat-row,
.chips,
.confirm-panel footer {
  display: flex;
  align-items: center;
}

.chips button {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.chips button:hover {
  background: var(--ui-hover-bg);
}

.profile-flow {
  max-width: 1260px;
  margin: 0 auto;
}

.profile-chat-input {
  max-width: 780px;
  margin: 18px 0 0 46px;
}

.profile-chat-input :deep(.chat-composer) {
  max-width: 780px;
  margin: 0;
  padding: 0;
}

.profile-chat-input :deep(.footer-hint) {
  display: none;
}

.chat-row {
  gap: 12px;
  margin-top: 14px;
}

.chat-row--user {
  justify-content: flex-end;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: var(--color-hover);
  color: var(--color-text);
  font-weight: 800;
  flex: 0 0 auto;
}

.avatar--ai {
  background: color-mix(in srgb, #2563eb 11%, var(--color-surface));
  color: var(--color-info);
}

.message {
  max-width: 620px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  padding: 14px 18px;
  color: var(--color-text);
  line-height: 1.7;
}

.message--user {
  background: var(--color-hover);
}

.choice-grid {
  margin-top: 28px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.choice-grid--profile {
  grid-template-columns: 1fr;
}

.choice-card,
.detail-panel,
.confirm-panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.choice-card {
  position: relative;
  padding: 24px;
  min-height: 188px;
}

.choice-card--wide {
  min-height: 148px;
}

.choice-card h2,
.detail-panel h2,
.confirm-panel h2 {
  color: var(--color-text);
  font-size: 20px;
  font-weight: 800;
}

.number {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-weight: 800;
  margin-bottom: 14px;
}

.chips {
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 18px;
}

.chips button {
  height: 36px;
  border-radius: 8px;
  padding: 0 14px;
}

.chips button.selected {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--ui-hover-strong-bg);
  box-shadow: inset 0 0 0 1px var(--color-primary);
}

.check-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 13px 18px;
}

.check-grid label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text);
  font-weight: 700;
}

.detail-panel {
  margin-top: 16px;
  padding: 24px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.panel-title .number {
  margin-bottom: 0;
}

.detail-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.detail-grid label {
  display: grid;
  gap: 8px;
}

.detail-grid span {
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 700;
}

.detail-grid select,
.detail-grid input,
.supplement-box textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text);
  outline: 0;
}

.detail-grid select,
.detail-grid input {
  height: 40px;
  padding: 0 12px;
}

.tag-input {
  min-height: 40px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
}

.tag-input--large {
  margin-top: 18px;
  min-height: 48px;
}

.tag-input button {
  height: 28px;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  background: color-mix(in srgb, #2563eb 11%, var(--color-surface));
  color: var(--color-info);
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 8px;
  cursor: pointer;
  font-weight: 800;
}

.tag-input input {
  flex: 1 1 140px;
  min-width: 120px;
  height: 28px;
  border: 0;
  background: transparent;
  padding: 0;
}

.detail-field--wide {
  grid-column: span 2;
}

.difficulty-text {
  min-height: 40px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text);
  display: flex;
  align-items: center;
  padding: 0 12px;
  font-size: 14px;
  font-weight: 700;
}

.confirm-panel {
  margin-top: 24px;
  padding: 24px 28px;
}

.confirm-list {
  margin-top: 22px;
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 0;
  border-top: 1px solid var(--color-border);
}

.confirm-list span,
.confirm-list strong {
  min-height: 48px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
}

.confirm-list span {
  color: var(--color-text-muted);
}

.confirm-list strong {
  color: var(--color-text);
}

.supplement-box {
  margin-top: 18px;
  display: grid;
  gap: 12px;
}

.supplement-box textarea {
  resize: vertical;
  line-height: 1.6;
  padding: 12px;
}

.supplement-box .outline-btn {
  justify-self: end;
}

.confirm-panel footer,
.detail-panel footer {
  justify-content: center;
  gap: 18px;
  margin-top: 24px;
}

.detail-panel footer {
  display: flex;
}

.skeleton-stack,
.confirm-skeleton {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.skeleton-stack span,
.confirm-skeleton span,
.skeleton-grid span {
  display: block;
  min-height: 38px;
  border-radius: 8px;
  background: linear-gradient(90deg, var(--color-hover), var(--color-border), var(--color-hover));
  background-size: 220% 100%;
  animation: skeleton-loading 1.1s ease-in-out infinite;
}

.skeleton-stack {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.skeleton-stack span {
  min-height: 34px;
}

.skeleton-grid {
  margin-top: 18px;
}

.confirm-skeleton {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@keyframes skeleton-loading {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}

.document-flow {
  max-width: 1120px;
  margin: 24px auto 0;
}

.document-flow.fullscreen {
  position: fixed;
  inset: 0;
  z-index: 80;
  max-width: none;
  overflow: auto;
  background: var(--color-bg);
  padding: 24px 42px;
}

.document-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 62px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
}

.document-head h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 20px;
  font-weight: 700;
}

.document-head p {
  margin-top: 6px;
  color: var(--color-text-muted);
}

.icon-button {
  width: 38px;
  height: 38px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.document-panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.document-flow.fullscreen .document-panel {
  min-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
}

.document-edit-btn {
  height: 38px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.document-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.document-actions button:not(.document-edit-btn) {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  display: grid;
  place-items: center;
}

.document-actions button:hover {
  background: var(--ui-hover-strong-bg);
}

.outline-btn:hover {
  background: var(--ui-hover-bg);
}

.document-actions button:disabled,
.document-confirm button:disabled,
.confirm-panel footer button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.document-editor {
  width: 100%;
  min-height: 620px;
  border: 0;
  outline: 0;
  resize: vertical;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 34px 80px 48px;
  font-size: 16px;
  line-height: 1.75;
}

.document-flow.fullscreen .document-editor,
.document-flow.fullscreen .document-preview {
  flex: 1 1 auto;
  min-height: 0;
}

.document-preview {
  max-width: 760px;
  margin: 0 auto;
  padding: 44px 36px 64px;
  color: var(--color-text);
  line-height: 1.75;
}

.document-preview.typing::after {
  content: "";
  display: inline-block;
  width: 7px;
  height: 1em;
  margin-left: 4px;
  background: var(--color-primary);
  vertical-align: -2px;
  animation: cursor-blink 0.8s steps(1) infinite;
}

@keyframes cursor-blink {
  50% { opacity: 0; }
}

.document-preview :deep(h1) {
  margin: 0 0 28px;
  font-size: 30px;
  line-height: 1.25;
}

.document-preview :deep(h2) {
  margin: 34px 0 12px;
  padding-top: 22px;
  border-top: 1px solid var(--color-border);
  font-size: 24px;
}

.document-preview :deep(h3) {
  margin: 22px 0 8px;
  font-size: 19px;
}

.document-preview :deep(p),
.document-preview :deep(li) {
  font-size: 16px;
}

.document-preview :deep(ul),
.document-preview :deep(ol) {
  padding-left: 24px;
}

.document-confirm {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 18px;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
}

.primary-btn,
.outline-btn {
  height: 46px;
  border-radius: 8px;
  padding: 0 42px;
  cursor: pointer;
  font-weight: 800;
}

.primary-btn {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.outline-btn {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
}

@media (max-width: 1020px) {
  .choice-grid,
  .choice-grid--profile {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .document-head,
  .document-confirm {
    flex-direction: column;
    align-items: stretch;
  }

  .document-actions {
    flex-wrap: wrap;
  }

  .document-editor,
  .document-preview {
    padding-inline: 20px;
  }

  .detail-field--wide {
    grid-column: auto;
  }
}
</style>
