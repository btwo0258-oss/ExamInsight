<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import AttachmentCard from '@/components/main-area/mode3-chat/input/AttachmentCard.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import LibrarySelectModal from '@/components/student/LibrarySelectModal.vue'
import ProjectSelectModal from '@/components/student/ProjectSelectModal.vue'
import { courseLibraries } from '@/mock'
import type { LearningResource } from '@/mock'
import { useLearningStore } from '@/stores/learning'
import { useLibraryResourceStore } from '@/stores/libraryResource'

const route = useRoute()
const router = useRouter()
const learningStore = useLearningStore()
const libraryResourceStore = useLibraryResourceStore()
const prompt = ref('')
const step = ref<'home' | 'profile'>('home')
const selectedLibraryId = ref(1)
const selectedProjectId = ref<number | null>(null)
const files = ref<File[]>([])
const fileEl = ref<HTMLInputElement | null>(null)
const showUploadError = ref(false)
const uploadErrorMessage = ref('')
const targetType = ref('考试复习')
const preferences = ref(['图文讲解', '代码示例'])
const studyPeriod = ref('3 天')
const foundationLevel = ref('基础一般')
const weakPointDraft = ref('')
const weakPointTags = ref<string[]>([])
const dailyTime = ref('每天 60-90 分钟')
const studyDepth = ref('快速复习')
const questionCountPreset = ref<'30' | '60' | '100' | 'custom'>('60')
const customQuestionCount = ref(60)
const supplementOpen = ref(false)
const supplementDraft = ref('')
const extraRequirement = ref('')
const libraryModalOpen = ref(false)
const projectModalOpen = ref(false)
const modelMenuOpen = ref(false)
const selectedModelKey = ref('qwen-plus')

const selectedLibrary = computed(() => courseLibraries.find((item) => item.id === selectedLibraryId.value))
const selectedProject = computed(() => learningStore.getPlan(selectedProjectId.value ?? -1))
const modelOptions = [
  { key: 'qwen-plus', name: 'Qwen Plus', desc: '最强大的推理能力' },
  { key: 'gpt-4o', name: 'GPT-4 Omni', desc: '适合日常对话' },
]
const selectedModel = computed(() => modelOptions.find((item) => item.key === selectedModelKey.value) ?? modelOptions[0]!)
const targetOptions = ['考试复习', '课程作业', '面试准备', '项目实战']
const preferenceOptions = ['图文讲解', '代码示例', '先练后讲', '先讲后练']
const foundationOptions = ['零基础', '基础薄弱', '基础一般', '有一定基础', '只补薄弱点']
const depthOptions = ['快速复习', '系统学习', '刷题强化', '项目实操']
const resourceGroupMap: Record<string, LearningResource['group'] | undefined> = {
  handbook: '个性化学习手册',
  mindmap: '思维导图',
  ppt: 'PPT',
  code: '代码案例',
}

const resourceOptions = ref([
  { key: 'handbook', label: '个性化学习手册', checked: true },
  { key: 'mindmap', label: '思维导图', checked: true },
  { key: 'ppt', label: 'PPT', checked: false },
  { key: 'code', label: '代码案例', checked: false },
])

const selectedResourceLabels = computed(() =>
  resourceOptions.value.filter((item) => item.checked).map((item) => item.label),
)
const profileFocusText = computed(() => weakPointTags.value.join(' / ') || selectedLibrary.value?.tags.slice(0, 3).join(' / ') || '待确认')
const materialSourceText = computed(() => {
  const sources = [selectedLibrary.value?.name]
  if (files.value.length) sources.push(`${files.value.length} 个上传文件`)
  if (selectedProject.value) sources.push(selectedProject.value.title)
  return sources.filter(Boolean).join('、')
})
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

const quickActions = [
  { icon: 'clipboard', label: '生成学习方案' },
  { icon: 'clipboard-x', label: '错题诊断' },
  { icon: 'edit', label: '生成练习题' },
  { icon: 'presentation', label: '生成 PPT/手册' },
  { icon: 'mind-topic', label: '生成思维导图' },
  { icon: 'camera', label: '拍照问答' },
]

function selectLibrary(id: number) {
  selectedLibraryId.value = id
  libraryModalOpen.value = false
}

function selectProject(id: number | null) {
  selectedProjectId.value = id
  const project = id === null ? undefined : learningStore.getPlan(id)
  if (project) selectedLibraryId.value = project.libraryId
  files.value.forEach((file) => {
    libraryResourceStore.addFile(file, '智能学习上传', id, project?.libraryId ?? null)
  })
  projectModalOpen.value = false
}

function triggerUpload() {
  fileEl.value?.click()
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const selectedFiles = Array.from(input.files ?? [])
  const allowedExtensions = ['.pdf', '.docx', '.md', '.txt']
  const maxSize = 21 * 1024 * 1024

  if (files.value.length + selectedFiles.length > 5) {
    uploadErrorMessage.value = '最多只能上传 5 个文件'
    showUploadError.value = true
    input.value = ''
    return
  }

  for (const file of selectedFiles) {
    const extension = file.name.slice(file.name.lastIndexOf('.')).toLowerCase()
    if (!allowedExtensions.includes(extension)) {
      uploadErrorMessage.value = `文件 ${file.name} 格式不支持，仅支持 PDF、DOCX、Markdown、TXT`
      showUploadError.value = true
      continue
    }
    if (file.size > maxSize) {
      uploadErrorMessage.value = `文件 ${file.name} 超过 21MB 限制`
      showUploadError.value = true
      continue
    }
    files.value.push(file)
    libraryResourceStore.addFile(
      file,
      '智能学习上传',
      selectedProjectId.value,
      selectedProject.value?.libraryId ?? null,
    )
  }
  input.value = ''
}

function removeFile(index: number) {
  files.value.splice(index, 1)
}

function selectModel(key: string) {
  selectedModelKey.value = key
  modelMenuOpen.value = false
}

function togglePreference(value: string) {
  preferences.value = preferences.value.includes(value)
    ? preferences.value.filter((item) => item !== value)
    : [...preferences.value, value]
}

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

function setResourceChecked(key: string, checked = true) {
  const option = resourceOptions.value.find((item) => item.key === key)
  if (option) option.checked = checked
}

function inferProfileFromPrompt(text: string) {
  if (/面试|简历|秋招|春招|offer/i.test(text)) targetType.value = '面试准备'
  else if (/作业|实验|报告|论文|课程设计/i.test(text)) targetType.value = '课程作业'
  else if (/项目|实战|开发|作品/i.test(text)) targetType.value = '项目实战'
  else if (/考|复习|期末|期中|测验|四六级|cet/i.test(text)) targetType.value = '考试复习'

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

  const inferredPreferences = new Set(preferences.value)
  if (/代码|编程|程序|debug|案例/i.test(text)) inferredPreferences.add('代码示例')
  if (/题|刷题|练习|测验|错题/i.test(text)) inferredPreferences.add('先练后讲')
  if (/图|导图|框架|结构/i.test(text)) inferredPreferences.add('图文讲解')
  if (/先讲|讲解|概念/i.test(text)) inferredPreferences.add('先讲后练')
  preferences.value = Array.from(inferredPreferences)

  if (/不要\s*ppt|不需要\s*ppt|不用\s*ppt|别.*ppt/i.test(text)) setResourceChecked('ppt', false)
  else if (/ppt|演示|汇报/i.test(text)) setResourceChecked('ppt')
  if (/导图|思维导图|结构图/i.test(text)) setResourceChecked('mindmap')
  if (/代码|案例|编程/i.test(text)) setResourceChecked('code')

  if (/刷题|题海|错题/i.test(text)) studyDepth.value = '刷题强化'
  else if (/项目|实战|开发/i.test(text)) studyDepth.value = '项目实操'
  else if (/系统|完整|从头/i.test(text)) studyDepth.value = '系统学习'

  const matchedTags = selectedLibrary.value?.tags.filter((tag) => text.includes(tag)) ?? []
  if (matchedTags.length) setWeakPointTags(matchedTags)
  else if (/分不清|混淆|薄弱|不会|不懂/i.test(text)) setWeakPointTags(selectedLibrary.value?.tags.slice(0, 3) ?? [])
}

function submitPrompt() {
  if (!prompt.value.trim()) {
    prompt.value = '我下周要考 Java 面向对象，继承、多态和接口分不清，帮我做 3 天复习。'
  }
  inferProfileFromPrompt(prompt.value)
  step.value = 'profile'
}

function applySupplement() {
  const text = supplementDraft.value.trim()
  if (!text) {
    supplementOpen.value = false
    return
  }
  extraRequirement.value = text
  inferProfileFromPrompt(`${prompt.value} ${text}`)
  supplementOpen.value = false
}

function createProject() {
  const resourceGroups = resourceOptions.value
    .filter((item) => item.checked)
    .map((item) => resourceGroupMap[item.key])
    .filter((group): group is LearningResource['group'] => Boolean(group))
  const plan = learningStore.createPlan({
    prompt: prompt.value.trim(),
    libraryId: selectedLibraryId.value,
    projectId: selectedProjectId.value,
    targetType: targetType.value,
    preferences: preferences.value,
    resourceGroups,
    period: studyPeriod.value,
    foundation: foundationLevel.value,
    weakPoints: profileFocusText.value,
    dailyTime: dailyTime.value,
    studyDepth: studyDepth.value,
    questionCount: plannedQuestionCount.value,
    supplementalRequirement: extraRequirement.value,
  })
  router.push(`/learning/${plan.id}`)
}

watch(
  () => route.query.libraryId,
  (value) => {
    const libraryId = Number(value)
    if (courseLibraries.some((item) => item.id === libraryId)) selectedLibraryId.value = libraryId
  },
  { immediate: true },
)
</script>

<template>
  <StudentShell>
    <div class="learning-home">
      <section v-if="step === 'home'" class="home-center">
        <h1>今天想解决什么学习问题？</h1>
        <p>选择资料库、上传文件或直接提问，AI 会自动生成学习路径和资源包。</p>

        <div class="upload-row">
          <span>也可以直接上传：</span>
          <button type="button" @click="triggerUpload">PDF</button>
          <button type="button" @click="triggerUpload">DOCX</button>
          <button type="button" @click="triggerUpload">Markdown</button>
          <button type="button" @click="triggerUpload">TXT</button>
        </div>

        <div class="prompt-box">
          <div v-if="files.length" class="attachment-previews">
            <AttachmentCard v-for="(file, index) in files" :key="`${file.name}-${file.lastModified}`" :file="file" @remove="removeFile(index)" />
          </div>
          <textarea
            v-model="prompt"
            placeholder="例如：基于 Java 面向对象资料库，帮我做 3 天复习计划，并生成练习题和思维导图"
            @keydown.ctrl.enter.prevent="submitPrompt"
          />
          <span v-if="!prompt && !files.length" class="prompt-placeholder">处理任何事务</span>
          <div class="prompt-toolbar">
            <div class="prompt-tools prompt-tools--left">
              <button type="button" title="上传文件" @click="triggerUpload">
                <AppIcon name="paperclip" :size="21" />
              </button>
              <button type="button" title="语音输入">
                <AppIcon name="microphone" :size="20" />
              </button>
              <button type="button" title="图片识别">
                <AppIcon name="eye" :size="20" />
              </button>
              <button class="library-chip" type="button" @click="libraryModalOpen = true">
                <AppIcon name="folder" :size="17" />
                {{ selectedLibrary?.name }}
                <AppIcon name="close" :size="14" />
              </button>
            </div>
            <div class="prompt-tools prompt-tools--right">
              <div class="model-switch" @click.stop>
                <button class="model-trigger" type="button" @click="modelMenuOpen = !modelMenuOpen">
                  <span>{{ selectedModel.name }}</span>
                  <AppIcon :name="modelMenuOpen ? 'chevron-up' : 'chevron-down'" :size="13" />
                </button>
                <transition name="model-menu">
                  <div v-if="modelMenuOpen" class="model-menu">
                    <button
                      v-for="item in modelOptions"
                      :key="item.key"
                      class="model-option"
                      :class="{ 'is-active': item.key === selectedModelKey }"
                      type="button"
                      @click="selectModel(item.key)"
                    >
                      <span>
                        <strong>{{ item.name }}</strong>
                        <small>{{ item.desc }}</small>
                      </span>
                      <AppIcon v-if="item.key === selectedModelKey" name="check" :size="16" />
                    </button>
                  </div>
                </transition>
              </div>
              <span class="model-label">5.6 Sol 轻度</span>
              <button type="button" title="璇煶杈撳叆">
                <AppIcon name="microphone" :size="20" />
              </button>
              <button class="send-btn" type="button" @click="submitPrompt">
                <AppIcon name="upload" :size="21" />
              </button>
            </div>
          </div>
        </div>
        <input ref="fileEl" hidden multiple type="file" accept=".pdf,.docx,.md,.txt" @change="onFileChange" />

        <div class="prompt-subbar">
          <div class="subbar-left">
            <button class="subbar-action" type="button" @click="projectModalOpen = true">
              <AppIcon name="folder" :size="17" />
              <span>{{ selectedProject ? selectedProject.title : '选择项目 · 无' }}</span>
              <AppIcon name="chevron-right" :size="13" />
            </button>
            <button class="subbar-action" type="button">
              <AppIcon name="activity" :size="16" />
              <span>连接插件</span>
            </button>
          </div>
        </div>

        <div class="action-chips">
          <button v-for="item in quickActions" :key="item.label" type="button" @click="prompt = item.label">
            <span class="action-icon"><AppIcon :name="item.icon" :size="18" /></span>
            <span>{{ item.label }}</span>
          </button>
        </div>

      </section>

      <section v-else class="profile-flow">
        <header class="flow-head">
          <button type="button" @click="step = 'home'">
            <AppIcon name="chevron-left" :size="18" />
          </button>
          <div>
            <h1>补全学习画像</h1>
            <p>通过少量确认，让系统生成更贴合你的学习项目。</p>
          </div>
        </header>

        <div class="chat-row chat-row--user">
          <div class="message message--user">{{ prompt }}</div>
          <span class="avatar">学</span>
        </div>
        <div class="chat-row">
          <span class="avatar avatar--ai"><AppIcon name="brain" :size="18" /></span>
          <div class="message">我已根据你的对话预填学习画像，请确认目标、偏好、资源和学习约束。</div>
        </div>

        <div class="choice-grid">
          <article class="choice-card">
            <span class="number">1</span>
            <h2>目标类型</h2>
            <div class="chips">
              <button
                v-for="option in targetOptions"
                :key="option"
                :class="{ selected: targetType === option }"
                type="button"
                @click="targetType = option"
              >
                {{ option }}
              </button>
            </div>
          </article>
          <article class="choice-card">
            <span class="number">2</span>
            <h2>学习偏好</h2>
            <div class="chips">
              <button
                v-for="option in preferenceOptions"
                :key="option"
                :class="{ selected: preferences.includes(option) }"
                type="button"
                @click="togglePreference(option)"
              >
                {{ option }}
              </button>
            </div>
          </article>
          <article class="choice-card">
            <span class="number">3</span>
            <h2>资源类型（可选）</h2>
            <div class="check-grid">
              <label v-for="item in resourceOptions" :key="item.key">
                <input v-model="item.checked" type="checkbox" />
                <span>{{ item.label }}</span>
              </label>
            </div>
            <p class="training-note">资源包只保存学习材料；练习题将按学习约束生成并直接分配到学习路径。</p>
          </article>
        </div>

        <section class="detail-panel">
          <div class="panel-title">
            <span class="number">4</span>
            <h2>学习约束</h2>
          </div>
          <div class="detail-grid">
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
          </div>
        </section>

        <section class="confirm-panel">
          <h2>生成配置确认</h2>
          <div class="confirm-list">
            <span>资料来源</span>
            <strong>{{ materialSourceText }}</strong>
            <span>计划周期</span>
            <strong>{{ studyPeriod }}</strong>
            <span>目标类型</span>
            <strong>{{ targetType }}</strong>
            <span>当前基础</span>
            <strong>{{ foundationLevel }}</strong>
            <span>重点知识</span>
            <strong>{{ profileFocusText }}</strong>
            <span>学习偏好</span>
            <strong>{{ preferences.join('、') || '待确认' }}</strong>
            <span>每日时间</span>
            <strong>{{ dailyTime }}</strong>
            <span>输出深度</span>
            <strong>{{ studyDepth }}</strong>
            <span>生成资源</span>
            <strong>{{ selectedResourceLabels.join('、') || '暂不生成独立资源' }}</strong>
            <span>计划练习</span>
            <strong>{{ plannedQuestionCount }} 题 · {{ difficultyStrategy }}</strong>
            <span>预计难度分布</span>
            <strong>基础 {{ difficultyPreview.basic }} 题、进阶 {{ difficultyPreview.advanced }} 题、挑战 {{ difficultyPreview.challenge }} 题</strong>
            <span v-if="extraRequirement">补充要求</span>
            <strong v-if="extraRequirement">{{ extraRequirement }}</strong>
          </div>
          <div v-if="supplementOpen" class="supplement-box">
            <textarea
              v-model="supplementDraft"
              rows="3"
              placeholder="例如：每天最多 40 分钟，不要 PPT，多给代码题，重点按老师课件第三章来。"
            />
            <button class="outline-btn" type="button" @click="applySupplement">应用补充</button>
          </div>
          <footer>
            <button class="primary-btn" type="button" @click="createProject">开始生成学习项目</button>
            <button class="outline-btn" type="button" @click="supplementOpen = !supplementOpen">
              {{ supplementOpen ? '收起补充' : '继续补充要求' }}
            </button>
          </footer>
        </section>
      </section>
    </div>

    <LibrarySelectModal
      :open="libraryModalOpen"
      :selected-id="selectedLibraryId"
      @close="libraryModalOpen = false"
      @select="selectLibrary"
    />
    <ProjectSelectModal
      :open="projectModalOpen"
      :selected-id="selectedProjectId"
      @close="projectModalOpen = false"
      @select="selectProject"
    />
    <ConfirmDialog
      :open="showUploadError"
      title="上传提示"
      :message="uploadErrorMessage"
      confirm-text="知道了"
      cancel-text=""
      @close="showUploadError = false"
      @confirm="showUploadError = false"
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

.training-note {
  margin-top: 12px;
  padding: 9px 10px;
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  line-height: 1.55;
}

.home-center {
  max-width: 770px;
  margin: 112px auto 0;
  text-align: center;
}

.home-center h1,
.profile-flow h1 {
  color: var(--color-text);
  font-size: 28px;
  font-weight: 600;
  letter-spacing: 0;
}

.home-center > h1 {
  font-size: 0;
}

.home-center > h1::before {
  content: "我们该做什么？";
  font-size: 28px;
  font-weight: 600;
}

.home-center > p {
  display: none;
}

.home-center p,
.flow-head p {
  margin-top: 14px;
  color: var(--color-text-muted);
  font-size: 16px;
}

.prompt-box {
  position: relative;
  margin-top: 32px;
  min-height: 150px;
  border: 1px solid var(--color-border);
  border-radius: 24px;
  background: var(--color-surface);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.08);
  padding: 0;
  text-align: left;
  box-sizing: border-box;
}

.prompt-box textarea {
  width: 100%;
  min-height: 118px;
  max-height: 200px;
  resize: none;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text);
  font-size: 16px;
  line-height: 1.6;
  padding: 14px 12px 60px 16px;
  box-sizing: border-box;
  border-radius: 24px;
  display: block;
}

.attachment-previews {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 14px 16px 0;
}

.prompt-box textarea::placeholder {
  color: transparent;
}

.prompt-placeholder {
  position: absolute;
  left: 16px;
  top: 15px;
  color: var(--color-text-muted);
  font-size: 16px;
  line-height: 1.6;
  pointer-events: none;
}

.prompt-toolbar,
.prompt-tools,
.action-chips,
.upload-row,
.flow-head,
.chat-row,
.chips,
.confirm-panel footer {
  display: flex;
  align-items: center;
}

.prompt-toolbar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  justify-content: space-between;
  gap: 16px;
  min-height: 48px;
  padding: 8px 10px;
  background: linear-gradient(to top, var(--color-surface) 70%, transparent);
  border-bottom-left-radius: 24px;
  border-bottom-right-radius: 24px;
  pointer-events: none;
}

.prompt-tools {
  gap: 8px;
  pointer-events: auto;
}

.prompt-tools button,
.action-chips button,
.upload-row button,
.chips button {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.prompt-tools--left > button:not(:first-child) {
  display: none;
}

.prompt-tools > button {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  border: 0;
  background: transparent;
  color: var(--color-text);
}

.prompt-toolbar .library-chip {
  display: none !important;
}

.model-label {
  color: var(--color-text-muted);
  font-size: 0;
  white-space: nowrap;
}

.model-label::before {
  content: "5.6 Sol 轻度⌄";
  font-size: 16px;
}

.send-btn {
  width: 32px !important;
  height: 32px !important;
  border: 0 !important;
  border-radius: 50% !important;
  background: var(--color-hover-strong) !important;
  color: var(--color-text-muted) !important;
  display: grid;
  place-items: center;
  cursor: pointer;
}

.prompt-subbar {
  width: calc(100% - 40px);
  min-height: 42px;
  margin: 0 auto;
  padding: 0 12px;
  border-radius: 0 0 16px 16px;
  background: var(--color-hover);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.04);
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.subbar-left {
  display: flex;
  align-items: center;
  gap: 0;
}


.subbar-left .subbar-action:not(:first-child) {
  display: none;
}

.subbar-action {
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 4px;
  font-size: 14px;
}

.subbar-action:hover {
  background: var(--color-hover-strong);
}

/* kept for the profile step and hidden in the home input toolbar */
.library-chip {
  width: auto !important;
  height: 36px !important;
  padding: 0 12px;
  border: 1px solid var(--color-border) !important;
  border-radius: 10px !important;
  background: var(--color-surface) !important;
  display: inline-flex !important;
  align-items: center;
  gap: 8px;
  color: var(--color-text) !important;
  font-size: 13px;
  font-weight: 700;
}

.action-chips {
  width: 260px;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
  gap: 10px;
  margin: 22px 0 0 28px;
}

.action-chips button {
  height: 42px;
  border-radius: 8px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  border-color: transparent;
  background: transparent;
  color: #8a8a8a;
  font-weight: 500;
  text-align: left;
}

.action-chips button:hover {
  background: var(--color-hover);
  color: var(--color-text);
}

.action-icon {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.action-chips button:nth-child(1) .action-icon { color: #2563eb; }
.action-chips button:nth-child(2) .action-icon { color: #ef4444; }
.action-chips button:nth-child(3) .action-icon { color: #16a34a; }
.action-chips button:nth-child(4) .action-icon { color: #f97316; }
.action-chips button:nth-child(5) .action-icon { color: #7c3aed; }
.action-chips button:nth-child(6) .action-icon { color: #0ea5e9; }

.action-chips button span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-row {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin: 24px 0 -10px;
  color: var(--color-text-muted);
}

.upload-row button {
  height: 34px;
  border-radius: 8px;
  padding: 0 14px;
}

.prompt-tools--right .model-label {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 8px;
  border-radius: 8px;
  color: var(--color-text-muted);
  cursor: default;
}

.prompt-tools--right .model-label:hover {
  background: var(--color-hover);
  color: var(--color-text);
}

.prompt-tools--right .model-label::before {
  content: "Qwen Plus⌄";
  font-size: 14px;
  font-weight: 500;
}

.prompt-tools--right > .model-label {
  display: none !important;
}

.model-switch {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.model-trigger {
  height: 32px !important;
  width: auto !important;
  padding: 0 8px !important;
  border: 0 !important;
  border-radius: 8px !important;
  background: transparent !important;
  color: var(--color-text) !important;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 14px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
}

.model-trigger:hover {
  background: var(--color-hover) !important;
}

.model-menu {
  position: absolute;
  left: 0;
  bottom: calc(100% + 8px);
  width: 276px;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.14);
  z-index: 30;
}

.model-option {
  width: 100% !important;
  height: auto !important;
  min-height: 52px;
  padding: 8px 10px !important;
  border: 0 !important;
  border-radius: 7px !important;
  background: transparent !important;
  color: var(--color-text) !important;
  display: flex !important;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  cursor: pointer;
}

.model-option:hover,
.model-option.is-active {
  background: var(--color-hover) !important;
}

.model-option span {
  display: grid;
  gap: 3px;
}

.model-option strong {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.model-option small {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.2;
}

.model-option svg {
  color: #10a37f;
}

.model-menu-enter-active,
.model-menu-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.model-menu-enter-from,
.model-menu-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.profile-flow {
  max-width: 1260px;
  margin: 0 auto;
}

.flow-head {
  gap: 14px;
  margin-bottom: 36px;
}

.flow-head > button {
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  color: var(--color-text);
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

.confirm-panel footer {
  justify-content: center;
  gap: 18px;
  margin-top: 24px;
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
  .home-center {
    margin-top: 40px;
  }

  .choice-grid {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-field--wide {
    grid-column: auto;
  }
}
</style>
