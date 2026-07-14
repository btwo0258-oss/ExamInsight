<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import AppInput from '@/components/common/AppInput.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { courseLibraries } from '@/mock'
import { useLearningStore } from '@/stores/learning'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { renderMarkdownToHtml } from '@/utils/markdown'

const route = useRoute()
const router = useRouter()
const learningStore = useLearningStore()
const libraryResourceStore = useLibraryResourceStore()
const prompt = ref('请帮我创建一个智能学习计划。')
const selectedLibraryId = ref(1)
const selectedProjectId = ref<number | null>(null)
const files = ref<File[]>([])
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
let profileGenerateTimer: ReturnType<typeof window.setTimeout> | undefined
let documentTypeTimer: ReturnType<typeof window.setInterval> | undefined

const selectedLibrary = computed(() => courseLibraries.find((item) => item.id === selectedLibraryId.value))
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

function inferProfileFromPrompt(text: string) {
  const inferredConstraints = new Set(learningConstraintTags.value)
  targetTypeOptions.forEach((option) => inferredConstraints.delete(option))
  if (/面试|简历|秋招|春招|offer/i.test(text)) inferredConstraints.add('面试准备')
  else if (/作业|实验|报告|论文|课程设计/i.test(text)) inferredConstraints.add('课程作业')
  else if (/项目|实战|开发|作品/i.test(text)) inferredConstraints.add('项目实战')
  else if (/考|复习|期末|期中|测验|四六级|cet/i.test(text)) inferredConstraints.add('考试复习')
  else inferredConstraints.add('考试复习')

  if (/零基础|从零|完全不会/i.test(text)) foundationLevel.value = '零基础'
  else if (/基础差|不懂|不会|分不清|薄弱|混淆/i.test(text)) foundationLevel.value = '基础薄弱'
  else if (/只.*薄弱|专项|强化|补弱/i.test(text)) foundationLevel.value = '只补薄弱点'
  else if (/熟悉|掌握|有基础/i.test(text)) foundationLevel.value = '有一定基础'

  const periodMatch = text.match(/(\d+)\s*(天|周|个月|月)/)
  if (periodMatch) studyPeriod.value = `${periodMatch[1]} ${periodMatch[2]}`
  else if (/下周/i.test(text)) studyPeriod.value = '1 周'
  else if (/明天/i.test(text)) studyPeriod.value = '1 天'

  const timeMatch = text.match(/每天.{0,8}?(\d+)\s*(分钟|小时)/)
  if (timeMatch) dailyTime.value = `每天 ${timeMatch[1]} ${timeMatch[2]}`
  else if (/周末/i.test(text)) dailyTime.value = '周末集中学习'

  if (/代码|编程|程序|debug|案例/i.test(text)) inferredConstraints.add('代码题强化')
  if (/题|刷题|练习|测验|错题|题海/i.test(text)) inferredConstraints.add('练习驱动')
  if (/图|导图|框架|结构/i.test(text)) inferredConstraints.add('结构化梳理')
  if (/先讲|讲解|概念/i.test(text)) inferredConstraints.add('先讲概念')
  if (/基础|从零|不懂|不会|薄弱/i.test(text)) inferredConstraints.add('先补基础')
  if (/时间少|赶|快速|冲刺/i.test(text)) inferredConstraints.add('高效冲刺')

  if (/刷题|题海|错题/i.test(text)) {
    studyDepth.value = '刷题强化'
    inferredConstraints.add('刷题强化')
  } else if (/项目|实战|开发/i.test(text)) {
    studyDepth.value = '项目实操'
    inferredConstraints.add('项目实操')
  } else if (/系统|完整|从头/i.test(text)) {
    studyDepth.value = '系统学习'
    inferredConstraints.add('系统学习')
  }
  setLearningConstraintTags(Array.from(inferredConstraints))

  const matchedTags = selectedLibrary.value?.tags.filter((tag) => text.includes(tag)) ?? []
  if (matchedTags.length) setWeakPointTags(matchedTags)
  else if (/分不清|混淆|薄弱|不会|不懂/i.test(text)) setWeakPointTags(selectedLibrary.value?.tags.slice(0, 3) ?? [])
}

function submitPrompt() {
  if (!prompt.value.trim()) {
    prompt.value = '我下周要考 Java 面向对象，继承、多态和接口分不清，帮我做 3 天复习。'
  }
  inferProfileFromPrompt(prompt.value)
  profileGenerating.value = true
  confirmationDocument.value = ''
  confirmGenerateOpen.value = false
  documentFullscreen.value = false
  if (profileGenerateTimer) window.clearTimeout(profileGenerateTimer)
  profileGenerateTimer = window.setTimeout(() => {
    profileGenerating.value = false
  }, 720)
}

function handleSupplementInput(text: string) {
  const next = text.trim()
  if (!next) return
  supplementDraft.value = next
  applySupplement()
}

function handleInputUpload(file: File) {
  files.value.push(file)
  libraryResourceStore.addFile(
    file,
    '智能学习上传',
    selectedProjectId.value,
    selectedProject.value?.libraryId ?? null,
  )
}

function applySupplement() {
  const text = supplementDraft.value.trim()
  if (!text) {
    return
  }
  extraRequirement.value = text
  inferProfileFromPrompt(`${prompt.value} ${text}`)
  if (confirmationDocument.value) startConfirmationDocumentGeneration()
}

function buildConfirmationDocument() {
  const focusList = (weakPointTags.value.length ? weakPointTags.value : selectedLibrary.value?.tags.slice(0, 4) ?? [])
    .map((item) => `- ${item}`)
    .join('\n') || '- 待根据学习材料进一步识别'
  const stageDays = studyPeriod.value || '待确认'

  return [
    '# 个性化学习方案确认稿',
    '',
    '## 1. 学习目标',
    prompt.value.trim() || `围绕${selectedLibrary.value?.course ?? '当前课程'}完成阶段性学习。`,
    '',
    '## 2. 资料来源',
    `- 主资料库：${selectedLibrary.value?.name ?? '未选择资料库'}`,
    files.value.length ? `- 上传材料：${files.value.map((file) => file.name).join('、')}` : '- 上传材料：暂无',
    selectedProject.value ? `- 关联项目：${selectedProject.value.title}` : '- 关联项目：无',
    '',
    '## 3. 学习画像',
    `- 学习约束：${learningConstraintText.value}`,
    `- 当前基础：${foundationLevel.value}`,
    `- 每日时间：${dailyTime.value}`,
    `- 输出深度：${studyDepth.value}`,
    extraRequirement.value ? `- 补充要求：${extraRequirement.value}` : '- 补充要求：无',
    '',
    '## 4. 重点知识模块',
    focusList,
    '',
    '## 5. 学习周期与练习安排',
    `- 计划周期：${stageDays}`,
    `- 计划练习总量：${plannedQuestionCount.value} 题`,
    `- 难度策略：${difficultyStrategy.value}`,
    `- 预计分布：基础 ${difficultyPreview.value.basic} 题、进阶 ${difficultyPreview.value.advanced} 题、挑战 ${difficultyPreview.value.challenge} 题`,
    '',
    '## 6. 推荐学习顺序',
    '1. 先完成核心概念讲解，建立最小知识框架。',
    '2. 结合例题或案例拆解高频考点与易错点。',
    '3. 进入专项练习，按知识点分组训练。',
    '4. 统一提交阶段测验，并把错题归因到具体知识点。',
    '5. 对错题生成同类巩固题，直到连续正确。',
    '',
    '## 7. 阶段划分',
    `### 阶段一：基础确认`,
    '- 目标：补齐核心定义、公式、概念边界或语法规则。',
    '- 产出：完成基础理解检查。',
    '',
    `### 阶段二：核心强化`,
    '- 目标：围绕薄弱点做例题拆解和专项训练。',
    '- 产出：完成主要练习题组。',
    '',
    `### 阶段三：综合复盘`,
    '- 目标：用综合测验检查迁移能力，并整理错题原因。',
    '- 产出：完成阶段测验和复盘清单。',
  ].join('\n')
}

function startConfirmationDocumentGeneration() {
  const fullDocument = buildConfirmationDocument()
  if (documentTypeTimer) window.clearInterval(documentTypeTimer)
  confirmationDocument.value = ''
  documentEditing.value = false
  documentGenerating.value = true
  confirmGenerateOpen.value = false
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
  startConfirmationDocumentGeneration()
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
  const plan = learningStore.createPlan({
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
  router.push(`/learning/${plan.id}`)
}

onBeforeUnmount(() => {
  if (profileGenerateTimer) window.clearTimeout(profileGenerateTimer)
  if (documentTypeTimer) window.clearInterval(documentTypeTimer)
})

watch(
  () => route.query.libraryId,
  (value) => {
    const libraryId = Number(value)
    if (courseLibraries.some((item) => item.id === libraryId)) selectedLibraryId.value = libraryId
  },
  { immediate: true },
)

watch(
  () => route.query.projectId,
  (value) => {
    const projectId = Number(value)
    const project = learningStore.getPlan(projectId)
    if (!project) return
    selectedProjectId.value = project.id
    if (courseLibraries.some((item) => item.id === project.libraryId)) {
      selectedLibraryId.value = project.libraryId
    }
    prompt.value = project.goal && !project.goal.startsWith('待通过')
      ? project.goal
      : `为「${project.title}」创建智能学习计划。`
    submitPrompt()
  },
  { immediate: true },
)
</script>

<template>
  <StudentShell>
    <div class="learning-home">
      <section class="profile-flow">
        <div class="chat-row chat-row--user">
          <div class="message message--user">{{ prompt }}</div>
          <span class="avatar">学</span>
        </div>
        <div class="chat-row">
          <span class="avatar avatar--ai"><AppIcon name="brain" :size="18" /></span>
          <div class="message">我会根据你的对话提取学习约束和学习画像。没提到的部分先用默认标签，确认后在下方生成可编辑确认稿。</div>
        </div>

        <div class="profile-chat-input">
          <AppInput placeholder="继续补充要求" @send="handleSupplementInput" @upload="handleInputUpload" />
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
            <button class="primary-btn" type="button" :disabled="profileGenerating" @click="startConfirmationDocumentGeneration">生成学习方案确认稿</button>
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
              <button class="primary-btn" type="button" :disabled="documentGenerating" @click="confirmGenerateOpen = true">确认生成</button>
            </footer>
          </section>
        </section>
      </section>
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
  background: #f2f4f7;
  color: var(--color-text);
  font-weight: 800;
  flex: 0 0 auto;
}

.avatar--ai {
  background: #eff6ff;
  color: #2563eb;
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
  background: #f4f6f8;
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
  color: #fff;
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
  background: #eff6ff;
  color: #2563eb;
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
  background: linear-gradient(90deg, var(--color-hover), #eef2f7, var(--color-hover));
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
  background: var(--color-hover);
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
  color: #fff;
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
