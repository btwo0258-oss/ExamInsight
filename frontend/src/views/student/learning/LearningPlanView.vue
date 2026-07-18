<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import AppInput from '@/components/common/AppInput.vue'
import AppSelectMenu from '@/components/common/AppSelectMenu.vue'
import LearningTutorPanel from '@/components/learning/LearningTutorPanel.vue'
import LearningRouteState from '@/components/learning/LearningRouteState.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import { courseKnowledgeBases } from '@/mock'
import type { LearningResource, LearningTask } from '@/mock'
import { useLearningStore } from '@/stores/learning'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { useLearningPlanRoute } from '@/composables/useLearningPlanRoute'

const router = useRouter()
const learningStore = useLearningStore()
const libraryResourceStore = useLibraryResourceStore()
const { plan, hasPlan, isLoading, loadError, loadPlan } = useLearningPlanRoute()
const library = computed(() => courseKnowledgeBases.find((item) => item.id === plan.value.knowledgeBaseId))
const adjustOpen = ref(false)
const adjustSaving = ref(false)
const adjustError = ref('')
const tutorDrawerOpen = ref(false)
const tutorInitialQuestion = ref('')
const tutorInitialFiles = ref<File[]>([])
const tutorRequestId = ref(0)
const adjustForm = ref({
  targetType: '',
  period: '',
  dailyTime: '',
  weakPoints: '',
  preferences: [] as string[],
  keepExercises: true,
  keepProgress: true,
})
const targetOptions = ['考试复习', '课程作业', '面试准备', '项目实战', '补弱']
const targetSelectOptions = targetOptions.map((option) => ({ value: option, label: option }))
const constraintOptions = ['考试复习', '先补基础', '练习驱动', '刷题强化', '结构化梳理', '代码题强化']

type ResourceWithMeta = LearningResource & { source?: 'default' | 'ai-conversation' }

const displayedResources = computed<ResourceWithMeta[]>(() => {
  return (plan.value.resources as ResourceWithMeta[]).filter((resource) => resource.status !== '未选择')
})

function profileValue(labels: string[]) {
  return plan.value.profile.find((item) => labels.includes(item.label))?.value ?? ''
}

function openStage(stageId: number) {
  const stage = plan.value.stages.find((item) => item.id === stageId)
  const task = stage?.tasks.find((item) => item.status !== '已锁定')
  if (!task) return
  router.push(`/learning/${plan.value.id}/study?stage=${stageId}&task=${task.id}`)
}

function openResourcePackage() {
  router.push(`/learning/${plan.value.id}/resources`)
}

function askTutor(
  question: string,
  files: File[] = [],
  complete?: (success?: boolean) => void,
) {
  const nextQuestion = question.trim()
  if (!nextQuestion && files.length === 0) {
    complete?.(false)
    return
  }
  tutorInitialQuestion.value = nextQuestion
  tutorInitialFiles.value = files
  tutorRequestId.value += 1
  tutorDrawerOpen.value = true
  complete?.(true)
}

const resourceColors: Record<LearningResource['group'], string> = {
  学习方案: '#6366f1',
  个性化学习手册: '#10b981',
  PPT: '#d4552d',
  思维导图: '#8b5cf6',
  代码案例: '#2563eb',
  图片: '#ec4899',
  文档: '#0f766e',
  电子表格: '#15803d',
  音频: '#c2410c',
  其他文件: '#64748b',
}

function resourceIcon(group: LearningResource['group']) {
  if (group === '思维导图') return 'mind-topic'
  if (group === 'PPT') return 'presentation'
  if (group === '代码案例') return 'code'
  if (group === '图片') return 'image'
  if (group === '学习方案') return 'notebook'
  return 'book'
}

function resourceStyle(group: LearningResource['group']): Record<string, string> {
  return { '--resource-color': resourceColors[group] }
}

const taskTypeAppearance: Record<LearningTask['type'], { icon: string; color: string }> = {
  讲解: { icon: 'book', color: '#6366f1' },
  资料: { icon: 'file', color: '#10b981' },
  练习: { icon: 'edit', color: '#2563eb' },
  测验: { icon: 'check', color: '#d4552d' },
  案例: { icon: 'code', color: '#2563eb' },
}

function taskAppearance(task: LearningTask) {
  const resource = task.learningResourceId ? plan.value.resources.find((item) => item.id === task.learningResourceId) : undefined
  if (resource) return { icon: resourceIcon(resource.group), color: resourceColors[resource.group] }
  return taskTypeAppearance[task.type]
}

function taskStyle(task: LearningTask): Record<string, string> {
  return { '--task-color': taskAppearance(task).color }
}

const profileItems = computed(() => [
  { label: '学习目标', value: profileValue(['学习目标', '目标']) || plan.value.targetType },
  { label: '当前基础', value: profileValue(['当前基础', '知识基础']) || '待确认' },
  { label: '重点知识', value: profileValue(['重点知识', '薄弱点']) || plan.value.dashboard.map((item) => item.label).join(' / ') || '待确认' },
  { label: '时间安排', value: profileValue(['时间安排']) || `${plan.value.period}，${profileValue(['节奏']) || '按计划推进'}` },
  { label: '学习方式', value: profileValue(['学习方式', '学习约束', '学习偏好']) || '按学习路径推进' },
])

function markdownContent() {
  const currentPlan = plan.value
  const profile = currentPlan.profile.map((item) => `- ${item.label}：${item.value}`).join('\n')
  const stages = currentPlan.stages.map((stage) => {
    const tasks = stage.tasks.map((task) => `  - [${task.done ? 'x' : ' '}] ${task.title}（${task.duration}）`).join('\n')
    return `## 阶段 ${stage.id} ${stage.title}\n\n${stage.desc}\n\n排期：${stage.scheduleLabel ?? '待安排'}\n\n${tasks}`
  }).join('\n\n')
  const resources = currentPlan.resources.map((resource) =>
    `- ${resource.group}：${resource.title}（${resource.status}）`,
  ).join('\n')
  const dashboard = currentPlan.dashboard.map((item) => `- ${item.label}：${item.value}%`).join('\n')

  return `# ${currentPlan.title}

## 学习目标

${currentPlan.goal}

## 基本信息

- 计划周期：${currentPlan.period}
- 关联知识库：${library.value?.name ?? '未关联知识库'}
- 目标类型：${currentPlan.targetType}
- 当前进度：${currentPlan.progress}%

## 个性化画像

${profile || '- 暂无画像'}

## 学习路径

${stages || '- 暂无学习路径'}

## 资源包

${resources || '- 暂无资源'}

## 掌握度

${dashboard || '- 暂无掌握度数据'}
`
}

function safeFileName(name: string) {
  return name.replace(/[\\/:*?"<>|]/g, '-')
}

function exportPlanMarkdown() {
  const currentPlan = plan.value
  const fileName = `${safeFileName(currentPlan.title)}-学习方案.md`
  const blob = new Blob([markdownContent()], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
  libraryResourceStore.addPlanExportMarkdown(
    fileName,
    currentPlan.id,
    currentPlan.id,
    currentPlan.knowledgeBaseId,
  )
}

function openAdjustModal() {
  adjustForm.value = {
    targetType: plan.value.targetType,
    period: plan.value.period,
    dailyTime: profileValue(['时间安排', '节奏']),
    weakPoints: profileValue(['重点知识', '薄弱点']),
    preferences: profileValue(['学习方式', '学习约束', '学习偏好']).split(/\s*\+\s*|\s*\/\s*|、/).filter(Boolean),
    keepExercises: true,
    keepProgress: true,
  }
  adjustOpen.value = true
}

function toggleAdjustConstraint(value: string) {
  adjustForm.value.preferences = adjustForm.value.preferences.includes(value)
    ? adjustForm.value.preferences.filter((item) => item !== value)
    : [...adjustForm.value.preferences, value]
}

async function applyAdjustPlan() {
  if (adjustSaving.value) return
  adjustSaving.value = true
  adjustError.value = ''
  try {
    await learningStore.updatePlanConfig(plan.value.id, adjustForm.value)
    adjustOpen.value = false
  } catch (error) {
    adjustError.value = error instanceof Error ? error.message : '学习计划调整失败'
  } finally {
    adjustSaving.value = false
  }
}
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
    <div v-if="hasPlan && !isLoading && !loadError" class="workspace-page">
      <header class="workspace-head">
        <button class="back-btn" type="button" @click="router.push('/learning/projects')">
          <AppIcon name="chevron-left" :size="20" />
        </button>
        <div>
          <h1>{{ plan.title }}</h1>
          <p>{{ plan.period }}｜知识库：{{ library?.name ?? '未关联' }}｜目标：{{ plan.targetType }}</p>
        </div>
        <div v-if="false" class="head-actions">
          <button class="outline-btn" type="button" @click="openAdjustModal">调整计划</button>
          <button class="primary-btn" type="button" @click="exportPlanMarkdown">导出方案</button>
        </div>
      </header>

      <main class="workspace-grid">
        <section class="panel path-panel">
          <header class="panel-head">
            <div>
              <AppIcon name="list" :size="22" />
              <h2>学习路径</h2>
            </div>
          </header>
          <p class="panel-description path-description">按阶段完成讲解、资料与练习，进度自动同步。</p>
          <div class="path-head-actions">
            <span>已完成 {{ plan.taskDone }}/{{ plan.totalTasks }} 项任务</span>
            <button type="button" @click="router.push(`/learning/${plan.id}/study`)">
              <AppIcon name="play" :size="15" />
              当前任务
            </button>
          </div>
          <div class="total-progress">
            <i><b :style="{ width: `${plan.progress}%` }" /></i>
            <strong>{{ plan.progress }}%</strong>
          </div>

          <div class="day-list">
            <article v-for="stage in plan.stages" :key="stage.id" class="day-card">
              <header>
                <span>阶段 {{ stage.id }}</span>
                <div>
                  <h3>{{ stage.title }}</h3>
                  <p>{{ stage.desc }}</p>
                  <small>{{ stage.scheduleLabel }}</small>
                </div>
              </header>
              <button v-for="task in stage.tasks" :key="task.id" class="task-row" type="button" :disabled="task.status === '已锁定'" @click="router.push(`/learning/${plan.id}/study?stage=${stage.id}&task=${task.id}`)">
                <i :class="{ done: task.done, active: task.status === '进行中' }" />
                <span class="task-main">
                  <b class="task-type-icon" :style="taskStyle(task)"><AppIcon :name="taskAppearance(task).icon" :size="13" /></b>
                  <span class="task-title">{{ task.title }}</span>
                </span>
                <small>{{ task.done ? '已完成' : task.status ?? '未开始' }}</small>
              </button>
              <button class="day-study-btn" type="button" :disabled="stage.tasks.every((task) => task.status === '已锁定')" @click="openStage(stage.id)">
                <AppIcon name="play" :size="16" />
                {{ stage.tasks.every((task) => task.done) ? '查看阶段' : stage.tasks.some((task) => task.done || task.status === '进行中') ? '继续学习' : '开始阶段' }}
              </button>
            </article>
          </div>
        </section>

        <div class="workspace-column">
          <section class="panel resource-panel">
            <header class="panel-head">
              <div>
                <AppIcon name="folder" :size="22" />
                <h2>资源包</h2>
              </div>
            </header>
            <p class="panel-description">最终学习方案和生成内容统一收纳在这里，进入详情后可预览。</p>
            <div class="resource-list">
              <article v-for="resource in displayedResources" :key="resource.id" class="resource-entry">
                <span class="resource-entry-icon" :style="resourceStyle(resource.group)"><AppIcon :name="resourceIcon(resource.group)" :size="21" /></span>
                <span>
                  <strong>{{ resource.group }}</strong>
                  <small>{{ resource.title }}</small>
                </span>
                <em :class="{ pending: resource.status !== '已生成' }">{{ resource.status }}</em>
              </article>
            </div>
            <p v-if="!displayedResources.length" class="empty-copy">当前项目还没有生成资源。</p>
            <button class="resource-detail-btn" type="button" @click="openResourcePackage">查看详情</button>
          </section>

          <section class="panel mini-card wrong-card">
            <header class="panel-head">
              <div>
                <AppIcon name="alert-circle" :size="22" />
                <h2>错题本</h2>
              </div>
            </header>
            <p class="panel-description">集中整理作答错误和关联知识点，方便后续针对性巩固。</p>
            <div class="wrong-card-content">
              <p>共 {{ plan.wrongQuestions.length }} 道错题</p>
              <div class="tag-list">
                <span v-for="wrong in plan.wrongQuestions" :key="wrong.id">{{ wrong.knowledge[0] }}</span>
              </div>
            </div>
            <button type="button" @click="router.push(`/learning/${plan.id}/mistakes`)">查看错题本</button>
          </section>
        </div>

        <div class="workspace-column">
          <aside class="panel profile-panel">
            <header class="panel-head">
              <div>
                <AppIcon name="user" :size="22" />
                <h2>个性化画像</h2>
              </div>
            </header>
            <p class="panel-description">汇总最终确认的目标、基础、重点和学习方式，作为计划生成依据。</p>
            <div class="profile-list">
              <article v-for="item in profileItems" :key="item.label">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </article>
            </div>
          </aside>

          <section class="panel tutor-card">
            <header class="panel-head">
              <div>
                <AppIcon name="robot" :size="22" />
                <h2>AI 助教</h2>
              </div>
            </header>
            <p class="panel-description">围绕当前学习项目提问，快速理解概念、例题和易错点。</p>
            <button type="button" @click="askTutor('继承和组合的区别是什么？')">继承和组合的区别是什么？</button>
            <button type="button" @click="askTutor('多态的实现原理是什么？')">多态的实现原理是什么？</button>
            <button type="button" @click="askTutor('如何理解向上转型？')">如何理解向上转型？</button>
            <AppInput
              class="tutor-card-input"
              variant="compact"
              :show-footer-hint="false"
              placeholder="问问当前项目..."
              :media-enabled="true"
              media-purpose="learning-input"
              :media-context="{
                knowledgeBaseId: plan.knowledgeBaseId ?? null,
                projectId: plan.id,
              }"
              @send="askTutor"
            />
          </section>
        </div>
      </main>

      <div v-if="adjustOpen" class="modal-backdrop" @click.self="adjustOpen = false">
        <section class="adjust-modal" role="dialog" aria-modal="true">
          <header>
            <div>
              <h2>调整计划</h2>
              <p>本次只做最小化调整，不重新生成全部内容。</p>
            </div>
            <button type="button" @click="adjustOpen = false">
              <AppIcon name="close" :size="18" />
            </button>
          </header>

          <div class="adjust-grid">
            <label>
              <span>目标类型</span>
              <AppSelectMenu v-model="adjustForm.targetType" :options="targetSelectOptions" aria-label="选择目标类型" />
            </label>
            <label>
              <span>学习天数 / 周期</span>
              <input v-model="adjustForm.period" placeholder="例如：5 天" />
            </label>
            <label>
              <span>每日时间</span>
              <input v-model="adjustForm.dailyTime" placeholder="例如：每天 60 分钟" />
            </label>
            <label>
              <span>薄弱知识点</span>
              <input v-model="adjustForm.weakPoints" placeholder="例如：继承 / 多态 / 接口" />
            </label>
          </div>

          <section class="adjust-section">
            <h3>学习约束</h3>
            <div class="adjust-chips">
              <button
                v-for="option in constraintOptions"
                :key="option"
                :class="{ selected: adjustForm.preferences.includes(option) }"
                type="button"
                @click="toggleAdjustConstraint(option)"
              >
                {{ option }}
              </button>
            </div>
          </section>

          <section class="adjust-section">
            <h3>影响范围</h3>
            <label class="check-row">
              <input v-model="adjustForm.keepProgress" type="checkbox" />
              <span>保留已完成任务和学习进度</span>
            </label>
            <label class="check-row">
              <input v-model="adjustForm.keepExercises" type="checkbox" />
              <span>保留已有习题、错题和作答记录</span>
            </label>
          </section>

          <p v-if="adjustError" class="adjust-error" role="alert">{{ adjustError }}</p>

          <footer>
            <button class="outline-btn" type="button" @click="adjustOpen = false">取消</button>
            <button class="primary-btn" type="button" :disabled="adjustSaving" @click="applyAdjustPlan">
              {{ adjustSaving ? '保存中…' : '应用调整' }}
            </button>
          </footer>
        </section>
      </div>
      <LearningTutorPanel
        :plan="plan"
        mode="drawer"
        :open="tutorDrawerOpen"
        :initial-question="tutorInitialQuestion"
        :initial-files="tutorInitialFiles"
        :initial-request-id="tutorRequestId"
        @close="tutorDrawerOpen = false"
      />
    </div>
  </StudentShell>
</template>

<style scoped>
.workspace-page {
  min-height: 100%;
  padding: 28px 36px 42px;
  background: var(--color-bg);
}

.workspace-page,
.workspace-page * {
  box-sizing: border-box;
}

h1,
h2,
h3,
p {
  margin: 0;
}

button,
input {
  font: inherit;
}

.workspace-head,
.workspace-grid {
  max-width: 1500px;
  margin-left: auto;
  margin-right: auto;
}

.workspace-head {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
}

.back-btn {
  width: 44px;
  height: 44px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.back-btn:hover {
  background: var(--ui-hover-strong-bg);
}

h1 {
  color: var(--color-text);
  font-size: 30px;
  font-weight: 800;
}

.workspace-head p {
  margin-top: 6px;
  color: var(--color-text-muted);
}

.head-actions {
  display: flex;
  gap: 12px;
}

.primary-btn,
.outline-btn {
  height: 44px;
  border-radius: 8px;
  padding: 0 18px;
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

.workspace-grid {
  margin-top: 28px;
  display: grid;
  grid-template-columns: minmax(340px, 0.95fr) minmax(340px, 1fr) minmax(340px, 1fr);
  gap: 18px;
  align-items: start;
}

.path-panel {
  min-width: 0;
}

.workspace-column {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  padding: 18px;
}

.panel-head,
.panel-head > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-head {
  justify-content: space-between;
  margin-bottom: 8px;
}

.path-panel .panel-head h2 {
  white-space: nowrap;
  font-size: 18px;
}

h2 {
  color: var(--color-text);
  font-size: 20px;
  font-weight: 800;
}

.panel-head span {
  color: var(--color-text-muted);
  font-size: 13px;
}

.panel-description {
  margin-bottom: 14px;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.path-description {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-head button,
.path-head-actions button {
  border: 0;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  font-weight: 800;
}

.panel-head button {
  border-radius: var(--ui-hover-radius);
  padding: 6px 8px;
}

.panel-head button:hover {
  background: var(--ui-hover-bg);
}

.path-head-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.path-head-actions button {
  height: 32px;
  border: 1px solid var(--color-primary);
  border-radius: 8px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
}

.total-progress {
  display: grid;
  grid-template-columns: 1fr 48px;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}

.total-progress i,
.bar-list i {
  display: block;
  height: 6px;
  border-radius: 999px;
  background: var(--color-border);
  overflow: hidden;
}

.total-progress b,
.bar-list b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-primary);
}

.day-list {
  display: grid;
  gap: 10px;
}

.day-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 10px;
}

.day-card header {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  margin-bottom: 8px;
}

.day-card header > span {
  height: 26px;
  border-radius: 7px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
}

.day-card h3 {
  color: var(--color-text);
  font-size: 14px;
  line-height: 1.35;
}

.day-card p,
.mini-card p,
.tutor-card p,
.resource-note {
  margin-top: 4px;
  color: var(--color-text-muted);
  line-height: 1.45;
}

.day-card p {
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.day-card header small {
  display: block;
  margin-top: 2px;
  color: var(--color-text-muted);
  font-size: 11px;
  line-height: 1.35;
}

.task-row {
  width: 100%;
  min-height: 30px;
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) 62px;
  gap: 8px;
  align-items: center;
  border: 0;
  background: transparent;
  color: var(--color-text);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  border-radius: var(--ui-hover-radius);
}

.task-row:hover:not(:disabled) {
  background: var(--ui-hover-bg);
}

.task-row:disabled,
.day-study-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.task-row > i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-border);
}

.task-row > i.active {
  background: #f59e0b;
}

.task-row > i.done {
  background: #22c55e;
}

.task-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 7px;
}

.task-type-icon {
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: color-mix(in srgb, var(--task-color) 12%, var(--color-surface));
  color: var(--task-color);
}

.task-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-row small {
  color: var(--color-text-muted);
  text-align: right;
  font-size: 12px;
}

.day-study-btn {
  width: 100%;
  height: 32px;
  margin-top: 8px;
  border: 1px solid var(--color-primary);
  border-radius: 8px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  cursor: pointer;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.path-panel .day-card:nth-child(n + 5) {
  display: none;
}

.resource-list {
  display: grid;
  gap: 8px;
}

.resource-entry {
  width: 100%;
  min-height: 64px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  color: var(--color-text);
  text-align: left;
  padding: 9px 10px;
}

.resource-entry-icon {
  width: 36px;
  height: 36px;
  display: grid !important;
  place-items: center;
  border-radius: 8px;
  background: color-mix(in srgb, var(--resource-color) 12%, var(--color-surface));
  color: var(--resource-color);
}

.resource-entry > span:nth-child(2) {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.resource-entry strong,
.resource-entry small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-entry strong {
  color: var(--color-text);
  font-size: 14px;
}

.resource-entry small {
  color: var(--color-text-muted);
  font-size: 12px;
}

.resource-entry em {
  border-radius: 6px;
  padding: 3px 8px;
  background: color-mix(in srgb, #16a34a 11%, var(--color-surface));
  color: var(--color-success);
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.resource-entry em.pending {
  background: var(--color-hover);
  color: var(--color-text-muted);
}

.resource-detail-btn {
  width: 100%;
  height: 36px;
  margin-top: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  font-weight: 800;
}

.resource-detail-btn:hover {
  background: var(--ui-hover-bg);
}

.empty-copy {
  color: var(--color-text-muted);
  font-size: 13px;
}

.profile-list {
  display: grid;
  gap: 8px;
}

.profile-list article {
  min-height: 38px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 8px 10px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.profile-list span {
  color: var(--color-text-muted);
}

.profile-list strong {
  color: var(--color-text);
  text-align: right;
}

.mini-card {
  min-height: 218px;
}

.wrong-card {
  display: flex;
  flex-direction: column;
}

.wrong-card-content {
  flex: 1;
}

.wrong-card > button {
  margin-top: 18px;
}

.numbers,
.labels {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  text-align: center;
}

.numbers {
  margin-top: 18px;
}

.numbers strong {
  color: var(--color-text);
  font-size: 24px;
}

.labels {
  margin-top: 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.mini-card button,
.tutor-card button {
  width: 100%;
  height: 36px;
  margin-top: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  font-weight: 800;
}

.mini-card button:hover,
.tutor-card button:hover,
.outline-btn:hover,
.adjust-chips button:hover {
  background: var(--ui-hover-bg);
}

.practice-card button:hover {
  background: var(--color-primary);
}

.practice-card button {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
}

.tag-list {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-list span {
  border-radius: 6px;
  background: color-mix(in srgb, #f97316 10%, var(--color-surface));
  color: var(--color-warning);
  padding: 5px 9px;
  font-size: 13px;
  font-weight: 800;
}

.bar-list {
  display: grid;
  gap: 12px;
}

.bar-list label {
  display: grid;
  grid-template-columns: 52px 1fr 42px;
  align-items: center;
  gap: 10px;
  color: var(--color-text);
}

.tutor-card {
  min-height: 218px;
}

.tutor-card button {
  margin-top: 10px;
  text-align: left;
  padding: 0 12px;
  font-weight: 600;
}

.tutor-card-input {
  margin-top: 18px;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(15, 23, 42, 0.28);
  display: grid;
  place-items: center;
  padding: 24px;
}

.adjust-modal {
  width: min(720px, 100%);
  max-height: calc(100vh - 48px);
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 22px 48px rgba(15, 23, 42, 0.18);
  padding: 22px;
}

.adjust-modal > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.adjust-modal > header p {
  margin-top: 6px;
  color: var(--color-text-muted);
}

.adjust-modal > header button {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
}

.adjust-modal > header button:hover {
  background: var(--ui-hover-strong-bg);
}

.adjust-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.adjust-grid label {
  display: grid;
  gap: 8px;
}

.adjust-grid span,
.adjust-section h3 {
  color: var(--color-text);
  font-size: 14px;
  font-weight: 800;
}

.adjust-grid input {
  height: 40px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text);
  padding: 0 12px;
  outline: 0;
}

.adjust-section {
  margin-top: 18px;
}

.adjust-chips {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.adjust-chips button {
  height: 34px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 0 12px;
  cursor: pointer;
  font-weight: 800;
}

.adjust-chips button.selected {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--ui-hover-strong-bg);
  box-shadow: inset 0 0 0 1px var(--color-primary);
}

.check-row {
  min-height: 34px;
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text);
}

.adjust-modal footer {
  margin-top: 22px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 1280px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .workspace-column {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    align-items: stretch;
  }

  .path-panel .day-card:nth-child(n + 5) {
    display: block;
  }
}

@media (max-width: 900px) {
  .workspace-head,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .workspace-column {
    grid-template-columns: 1fr;
  }

  .path-head-actions {
    width: 100%;
    justify-content: space-between;
  }

  .adjust-grid {
    grid-template-columns: 1fr;
  }
}
</style>
