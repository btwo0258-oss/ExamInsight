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
const preferenceOptions = ['图文讲解', '代码示例', '先练后讲', '先讲后练']

function resourceIcon(group: string) {
  if (group === '思维导图') return 'mind-topic'
  if (group === '练习题') return 'edit'
  if (group === '代码案例') return 'code'
  if (group === 'PPT') return 'presentation'
  if (group === '推荐阅读') return 'book'
  return 'file'
}

function profileValue(labels: string[]) {
  return plan.value.profile.find((item) => labels.includes(item.label))?.value ?? ''
}

function openDay(dayId: number) {
  router.push(`/learning/${plan.value.id}/study?day=${dayId}`)
}

function openResource(group: string) {
  if (group === '练习题') {
    router.push(`/learning/${plan.value.id}/practice`)
    return
  }
  router.push(`/learning/${plan.value.id}/resources`)
}

function markdownContent() {
  const currentPlan = plan.value
  const profile = currentPlan.profile.map((item) => `- ${item.label}：${item.value}`).join('\n')
  const days = currentPlan.days.map((day) => {
    const tasks = day.tasks.map((task) => `  - [${task.done ? 'x' : ' '}] ${task.title}（${task.duration}）`).join('\n')
    return `## Day ${day.id} ${day.title}\n\n${day.desc}\n\n${tasks}`
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

${days || '- 暂无学习路径'}

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
    preferences: profileValue(['学习偏好']).split(/\s*\+\s*|\s*\/\s*|、/).filter(Boolean),
    keepExercises: true,
    keepProgress: true,
  }
  adjustOpen.value = true
}

function toggleAdjustPreference(value: string) {
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
            <article v-for="day in plan.days" :key="day.id" class="day-card">
              <header>
                <span>Day {{ day.id }}</span>
                <div>
                  <h3>{{ day.title }}</h3>
                  <p>{{ day.desc }}</p>
                </div>
              </header>
              <label v-for="task in day.tasks" :key="task.id" class="task-row">
                <input
                  :checked="task.done"
                  type="checkbox"
                  @change="learningStore.markTaskDone(plan.id, task.id, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ task.title }}</span>
                <small>{{ task.duration }}</small>
              </label>
              <button class="day-study-btn" type="button" @click="openDay(day.id)">
                <AppIcon name="play" :size="16" />
                {{ day.tasks.every((task) => task.done) ? '查看任务' : day.tasks.some((task) => task.done) ? '继续学习' : '开始学习' }}
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
              <button type="button" @click="router.push(`/learning/${plan.id}/resources`)">查看全部</button>
            </header>
            <div class="resource-grid">
              <button v-for="resource in plan.resources" :key="resource.id" class="resource-tile" type="button" @click="openResource(resource.group)">
                <AppIcon :name="resourceIcon(resource.group)" :size="24" />
                <strong>{{ resource.group }}</strong>
                <span :class="{ muted: resource.status === '未选择' }">{{ resource.status }}</span>
              </button>
            </div>
            <p class="resource-note">
              学习材料可在资源包查看；练习题进入习题训练完成作答和解析。
            </p>
          </section>

          <section class="panel mini-card practice-card">
            <header>
              <AppIcon name="edit" :size="22" />
              <h2>习题训练</h2>
            </header>
            <p>专项练习与巩固提升</p>
            <div class="numbers">
              <strong>{{ plan.totalExercises }}</strong>
              <strong>{{ plan.exerciseDone }}</strong>
              <strong>{{ plan.correctRate }}%</strong>
            </div>
            <div class="labels">
              <span>总题数</span>
              <span>已完成</span>
              <span>正确率</span>
            </div>
            <button type="button" @click="router.push(`/learning/${plan.id}/practice`)">进入练习</button>
          </section>

          <section class="panel mini-card wrong-card">
            <header>
              <AppIcon name="alert-circle" :size="22" />
              <h2>错题整理</h2>
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
            <h3>学习偏好</h3>
            <div class="adjust-chips">
              <button
                v-for="option in preferenceOptions"
                :key="option"
                :class="{ selected: adjustForm.preferences.includes(option) }"
                type="button"
                @click="toggleAdjustPreference(option)"
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
  min-height: 26px;
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) 62px;
  gap: 8px;
  align-items: center;
  color: var(--color-text);
  font-size: 13px;
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

.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.resource-tile {
  min-height: 78px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  display: grid;
  place-items: center;
  gap: 4px;
  color: #2563eb;
  text-align: center;
  padding: 8px;
  cursor: pointer;
}

.resource-tile strong {
  color: var(--color-text);
  font-size: 13px;
}

.resource-tile span {
  border-radius: 6px;
  padding: 3px 8px;
  background: #ecfdf3;
  color: #16a34a;
  font-size: 12px;
  font-weight: 800;
}

.resource-tile span.muted {
  background: #f3f4f6;
  color: var(--color-text-muted);
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

  .resource-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .adjust-grid {
    grid-template-columns: 1fr;
  }
}
</style>
