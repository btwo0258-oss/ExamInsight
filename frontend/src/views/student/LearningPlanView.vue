<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { courseLibraries } from '@/mock'
import { useLearningStore } from '@/stores/learning'
import { useLibraryResourceStore } from '@/stores/libraryResource'

const route = useRoute()
const router = useRouter()
const learningStore = useLearningStore()
const libraryResourceStore = useLibraryResourceStore()
const plan = computed(() => learningStore.getPlan(Number(route.params.id)) ?? learningStore.plans[0]!)
const library = computed(() => courseLibraries.find((item) => item.id === plan.value.libraryId))
const adjustOpen = ref(false)
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
const constraintOptions = ['考试复习', '先补基础', '练习驱动', '刷题强化', '结构化梳理', '代码题强化']

function profileValue(labels: string[]) {
  return plan.value.profile.find((item) => labels.includes(item.label))?.value ?? ''
}

function openStage(stageId: number) {
  router.push(`/learning/${plan.value.id}/study?stage=${stageId}`)
}

function openResource(group: string) {
  router.push({ path: `/learning/${plan.value.id}/resources`, query: { type: group } })
}

const mindMapResource = computed(() => plan.value.resources.find((resource) => resource.group === '思维导图'))

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
- 资料库：${library.value?.name ?? '未选择资料库'}
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
    currentPlan.relatedProjectId ?? null,
    currentPlan.libraryId,
  )
}

function openAdjustModal() {
  adjustForm.value = {
    targetType: plan.value.targetType,
    period: plan.value.period,
    dailyTime: profileValue(['节奏']),
    weakPoints: profileValue(['重点知识', '薄弱点']),
    preferences: profileValue(['学习约束', '学习偏好']).split(/\s*\+\s*|\s*\/\s*|、/).filter(Boolean),
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

function applyAdjustPlan() {
  learningStore.updatePlanConfig(plan.value.id, adjustForm.value)
  adjustOpen.value = false
}
</script>

<template>
  <StudentShell>
    <div class="workspace-page">
      <header class="workspace-head">
        <button class="back-btn" type="button" @click="router.push('/learning/projects')">
          <AppIcon name="chevron-left" :size="20" />
        </button>
        <div>
          <h1>{{ plan.title }}</h1>
          <p>{{ plan.period }}｜资料库：{{ library?.name }}｜目标：{{ plan.targetType }}</p>
        </div>
        <div class="head-actions">
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
            <div class="path-head-actions">
              <span>已完成 {{ plan.taskDone }}/{{ plan.totalTasks }} 项任务</span>
              <button type="button" @click="router.push(`/learning/${plan.id}/study`)">
                <AppIcon name="play" :size="15" />
                当前任务
              </button>
            </div>
          </header>
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
              <button v-for="task in stage.tasks" :key="task.id" class="task-row" type="button" @click="router.push(`/learning/${plan.id}/study?stage=${stage.id}&task=${task.id}`)">
                <i :class="{ done: task.done, active: task.status === '进行中' }" />
                <span>{{ task.title }}</span>
                <small>{{ task.done ? '已完成' : task.status ?? '未开始' }}</small>
              </button>
              <button class="day-study-btn" type="button" @click="openStage(stage.id)">
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
                <AppIcon name="mind-topic" :size="22" />
                <h2>思维导图</h2>
              </div>
              <button type="button" @click="openResource('思维导图')">打开</button>
            </header>
            <button class="mindmap-entry" type="button" @click="openResource('思维导图')">
              <AppIcon name="mind-topic" :size="26" />
              <span>
                <strong>{{ mindMapResource?.title ?? '学习路径思维导图' }}</strong>
                <small>{{ mindMapResource?.status ?? '生成中' }}</small>
              </span>
            </button>
            <p class="resource-note">
              根据确认稿自动生成，用来查看知识结构、阶段关系和复盘优先级。
            </p>
          </section>

          <section class="panel mini-card wrong-card">
            <header>
              <AppIcon name="alert-circle" :size="22" />
              <h2>错题本</h2>
            </header>
            <p>共 {{ plan.wrongQuestions.length }} 道错题</p>
            <div class="tag-list">
              <span v-for="wrong in plan.wrongQuestions" :key="wrong.id">{{ wrong.knowledge[0] }}</span>
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
            <div class="profile-list">
              <article v-for="item in plan.profile.slice(0, 5)" :key="item.label">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </article>
            </div>
            <p>基于你的学习行为，持续优化学习方案。</p>
          </aside>

          <section class="panel mini-card dashboard-card">
            <header>
              <AppIcon name="bar-chart" :size="22" />
              <h2>学习面板</h2>
            </header>
            <div class="bar-list">
              <label v-for="item in plan.dashboard" :key="item.label">
                <span>{{ item.label }}</span>
                <i><b :style="{ width: `${item.value}%` }" /></i>
                <strong>{{ item.value }}%</strong>
              </label>
            </div>
          </section>

          <section class="panel tutor-card">
            <header>
              <AppIcon name="brain" :size="22" />
              <h2>AI 助教</h2>
            </header>
            <p>随时解答你的学习疑问</p>
            <button type="button">继承和组合的区别是什么？</button>
            <button type="button">多态的实现原理是什么？</button>
            <button type="button">如何理解向上转型？</button>
            <label>
              <input placeholder="问问当前项目..." />
              <AppIcon name="send" :size="18" />
            </label>
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
              <select v-model="adjustForm.targetType">
                <option v-for="option in targetOptions" :key="option">{{ option }}</option>
              </select>
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

          <footer>
            <button class="outline-btn" type="button" @click="adjustOpen = false">取消</button>
            <button class="primary-btn" type="button" @click="applyAdjustPlan">应用调整</button>
          </footer>
        </section>
      </div>
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
  color: #fff;
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
.panel-head > div,
.mini-card header,
.tutor-card header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-head {
  justify-content: space-between;
  margin-bottom: 14px;
}

.path-panel .panel-head {
  flex-wrap: wrap;
  align-items: flex-start;
}

.path-panel .panel-head h2 {
  white-space: nowrap;
}

.path-panel .path-head-actions {
  width: 100%;
  justify-content: space-between;
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

.panel-head button,
.path-head-actions button {
  border: 0;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  font-weight: 800;
}

.path-head-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.path-head-actions button {
  height: 32px;
  border: 1px solid var(--color-primary);
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
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
  background: #e5e7eb;
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
  grid-template-columns: 58px 1fr;
  gap: 10px;
  align-items: start;
  margin-bottom: 8px;
}

.day-card header > span {
  height: 28px;
  border-radius: 7px;
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 800;
}

.day-card h3 {
  color: var(--color-text);
  font-size: 15px;
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
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}

.task-row {
  width: 100%;
  min-height: 26px;
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) 62px;
  gap: 8px;
  align-items: center;
  border: 0;
  background: transparent;
  color: var(--color-text);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.task-row:hover {
  background: var(--color-hover);
}

.task-row > i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #cbd5e1;
}

.task-row > i.active {
  background: #f59e0b;
}

.task-row > i.done {
  background: #22c55e;
}

.task-row span {
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
  background: #eff6ff;
  color: #2563eb;
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

.mindmap-entry {
  width: 100%;
  min-height: 78px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  display: flex;
  align-items: center;
  gap: 12px;
  color: #2563eb;
  text-align: left;
  padding: 12px;
  cursor: pointer;
}

.mindmap-entry:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

.mindmap-entry span {
  min-width: 0;
  display: grid;
  gap: 5px;
}

.mindmap-entry strong {
  min-width: 0;
  overflow: hidden;
  color: var(--color-text);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mindmap-entry small {
  width: fit-content;
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  justify-self: center;
  border-radius: 6px;
  padding: 3px 8px;
  background: #ecfdf3;
  color: #16a34a;
  font-size: 12px;
  font-weight: 800;
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

.practice-card button {
  background: var(--color-primary);
  color: #fff;
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
  background: #fff7ed;
  color: #f97316;
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

.tutor-card label {
  height: 38px;
  margin-top: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  display: grid;
  grid-template-columns: 1fr 28px;
  align-items: center;
  padding: 0 10px;
}

.tutor-card input {
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
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
  background: var(--color-hover);
  color: var(--color-text);
  cursor: pointer;
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

.adjust-grid input,
.adjust-grid select {
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
