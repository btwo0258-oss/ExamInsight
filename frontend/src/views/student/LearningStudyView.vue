<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import LearningDetailShell from '@/components/student/LearningDetailShell.vue'
import LearningQuestionCard from '@/components/student/LearningQuestionCard.vue'
import LearningTutorPanel from '@/components/student/LearningTutorPanel.vue'
import type { CodeLanguageKey } from '@/mock'
import { evaluateExerciseAnswer, useLearningStore } from '@/stores/learning'

const route = useRoute()
const router = useRouter()
const learningStore = useLearningStore()
const plan = computed(() => learningStore.getPlan(Number(route.params.id)) ?? learningStore.plans[0]!)
const activeStage = computed(() => {
  const stageId = Number(route.query.stage)
  if (stageId) return plan.value.stages.find((stage) => stage.id === stageId) ?? plan.value.stages[0]
  return plan.value.stages.find((stage) => stage.tasks.some((task) => !task.done)) ?? plan.value.stages[0]
})
const activeTaskId = ref<number | null>(null)
const activeTask = computed(() => {
  const tasks = activeStage.value?.tasks ?? []
  return tasks.find((task) => task.id === activeTaskId.value) ?? tasks.find((task) => !task.done) ?? tasks[0]
})
const orderedTasks = computed(() =>
  plan.value.stages.flatMap((stage) => stage.tasks.map((task) => ({ stage, task }))),
)
const currentTaskIndex = computed(() => orderedTasks.value.findIndex((item) => item.task.id === activeTask.value?.id))
const previousTask = computed(() => orderedTasks.value[currentTaskIndex.value - 1])
const nextTask = computed(() => orderedTasks.value[currentTaskIndex.value + 1])
const relatedResource = computed(() => {
  const linkedResource = plan.value.resources.find((resource) => resource.id === activeTask.value?.resourceId)
  if (linkedResource) return linkedResource
  const preferredGroups: Record<string, string[]> = {
    讲解: ['个性化学习手册', '思维导图', 'PPT'],
    资料: ['个性化学习手册'],
    案例: ['代码案例'],
    复盘: ['思维导图', '个性化学习手册'],
  }
  const groups = preferredGroups[activeTask.value?.type ?? ''] ?? []
  return plan.value.resources.find((resource) => groups.includes(resource.group))
})
const taskExercises = computed(() => (activeTask.value?.exerciseIds ?? [])
  .map((id) => plan.value.exercises.find((item) => item.id === id))
  .filter((item): item is NonNullable<typeof item> => Boolean(item)))
const currentExerciseId = ref<number | undefined>()
const exercise = computed(() => taskExercises.value.find((item) => item.id === currentExerciseId.value) ?? taskExercises.value[0])
const exerciseIndex = computed(() => taskExercises.value.findIndex((item) => item.id === exercise.value?.id))
const isExerciseTask = computed(() => activeTask.value?.type === '练习' || activeTask.value?.type === '测验')
const hasCheckpoint = computed(() => activeTask.value?.type === '讲解' && taskExercises.value.length > 0)
const selectedAnswer = ref('')
const groupResult = ref<ReturnType<typeof learningStore.submitExerciseGroup>>()
const followupMode = ref<'repeat' | 'reinforce' | null>(null)
const followupCount = ref(10)
const followupDifficulty = ref<'保持难度' | '逐步提升'>('保持难度')
const groupSubmitted = computed(() => taskExercises.value.length > 0 && taskExercises.value.every((item) => item.submitted))
const visibleGroupResult = computed(() => {
  if (groupResult.value) return groupResult.value
  if (!groupSubmitted.value) return undefined
  const correctCount = taskExercises.value.filter((item) => item.gradingCorrect).length
  return {
    total: taskExercises.value.length,
    correctCount,
    wrongCount: taskExercises.value.length - correctCount,
    correctRate: Math.round((correctCount / taskExercises.value.length) * 100),
    wrongExerciseIds: taskExercises.value.filter((item) => !item.gradingCorrect).map((item) => item.id),
  }
})
const quizResult = computed(() => exercise.value?.submitted
  ? evaluateExerciseAnswer(exercise.value, exercise.value.userAnswer ?? '')
  : undefined)
const wrongExercises = computed(() => taskExercises.value.filter((item) => item.submitted && !item.gradingCorrect))
const availableReserveCount = computed(() => {
  const knowledge = new Set(taskExercises.value.map((item) => item.knowledge))
  return plan.value.exercises.filter((item) => item.purpose === '备用题' && !item.submitted && knowledge.has(item.knowledge)).length
})
const masteryRecommendation = computed(() => {
  const rate = visibleGroupResult.value?.correctRate ?? 0
  if (rate < 80) return '当前掌握还不稳定，建议先做同难度错题巩固。'
  if (rate < 90) return '当前已基本达标，建议再练一组并逐步提升难度。'
  return '当前表现已达标，建议进入下一任务；也可继续生成挑战题。'
})
const contentPanel = ref<HTMLElement | null>(null)
const stageDone = computed(() => activeStage.value?.tasks.filter((task) => task.done).length ?? 0)
const stageProgress = computed(() => {
  const total = activeStage.value?.tasks.length ?? 0
  return total ? Math.round((stageDone.value / total) * 100) : 0
})
let readingTimer: number | undefined

function markCurrentDone() {
  const task = activeTask.value
  if (task) learningStore.markTaskDone(plan.value.id, task.id, true)
}

function openTask(stageId: number, taskId: number) {
  activeTaskId.value = taskId
  router.replace({ query: { stage: stageId, task: taskId } })
}

function moveTask(offset: -1 | 1) {
  const target = offset === -1 ? previousTask.value : nextTask.value
  if (target) openTask(target.stage.id, target.task.id)
}

function completeAndContinue() {
  markCurrentDone()
  moveTask(1)
}

function selectAnswer(answer: string) {
  if (!exercise.value || groupSubmitted.value) return
  selectedAnswer.value = answer
  learningStore.saveExerciseDraft(plan.value.id, exercise.value.id, answer)
}

function selectLanguage(language: CodeLanguageKey) {
  if (!exercise.value) return
  selectedAnswer.value = learningStore.selectExerciseLanguage(plan.value.id, exercise.value.id, language) ?? ''
}

function submitQuizGroup() {
  groupResult.value = learningStore.submitExerciseGroup(plan.value.id, taskExercises.value.map((item) => item.id))
}

function openFollowup(mode: 'repeat' | 'reinforce') {
  followupMode.value = mode
  followupCount.value = mode === 'reinforce' ? Math.min(15, Math.max(3, wrongExercises.value.length * 2)) : 10
  followupDifficulty.value = (visibleGroupResult.value?.correctRate ?? 0) >= 80 ? '逐步提升' : '保持难度'
}

function createFollowupTask() {
  if (!activeTask.value || !followupMode.value) return
  const result = learningStore.createAdaptivePracticeTask(plan.value.id, activeTask.value.id, {
    mode: followupMode.value,
    count: followupCount.value,
    difficultyMode: followupDifficulty.value,
  })
  if (!result) return
  followupMode.value = null
  openTask(result.stage.id, result.task.id)
}

function moveExercise(offset: -1 | 1) {
  const target = taskExercises.value[exerciseIndex.value + offset]
  if (!target) return
  currentExerciseId.value = target.id
  selectedAnswer.value = target.draftAnswer ?? target.userAnswer ?? ''
}

function readingProgress() {
  const element = contentPanel.value
  if (!element || element.scrollHeight <= element.clientHeight + 4) return 100
  return Math.min(100, (element.scrollTop / (element.scrollHeight - element.clientHeight)) * 100)
}

function recordReading(secondsDelta = 0) {
  const task = activeTask.value
  if (!task || !['content', 'resource'].includes(task.completionMode ?? '')) return
  learningStore.recordTaskReading(plan.value.id, task.id, readingProgress(), secondsDelta)
}

function runCase() {
  if (activeTask.value) learningStore.completeTaskAction(plan.value.id, activeTask.value.id, 'run-case')
}

watch(
  activeStage,
  (stage) => {
    const queryTaskId = Number(route.query.task)
    activeTaskId.value = stage?.tasks.find((task) => task.id === queryTaskId)?.id
      ?? stage?.tasks.find((task) => !task.done)?.id
      ?? stage?.tasks[0]?.id
      ?? null
  },
  { immediate: true },
)

watch(activeTask, (task) => {
  if (task) learningStore.startTask(plan.value.id, task.id)
  currentExerciseId.value = taskExercises.value.find((item) => !item.submitted)?.id ?? taskExercises.value[0]?.id
  selectedAnswer.value = exercise.value?.draftAnswer ?? exercise.value?.userAnswer ?? ''
  groupResult.value = undefined
  followupMode.value = null
  contentPanel.value?.scrollTo({ top: 0 })
}, { immediate: true })

watch(exercise, (item) => {
  selectedAnswer.value = item?.draftAnswer ?? item?.userAnswer ?? ''
})

onMounted(() => {
  readingTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') recordReading(1)
  }, 1000)
})

onBeforeUnmount(() => {
  if (readingTimer) window.clearInterval(readingTimer)
})
</script>

<template>
  <LearningDetailShell
    eyebrow="学习路径"
    :title="activeTask?.title ?? activeStage?.title ?? '当前学习任务'"
    :subtitle="`阶段 ${activeStage?.id} · ${activeStage?.title} · ${activeTask?.duration ?? ''}`"
    :progress="plan.progress"
    :show-footer="!isExerciseTask"
    @back="router.push(`/learning/${plan.id}`)"
  >
    <template #actions>
      <button v-if="!isExerciseTask" class="primary-btn" type="button" @click="markCurrentDone">手动完成</button>
    </template>

    <template #navigation>
      <aside class="task-panel panel">
        <header class="task-nav-head">
          <h2>学习路径</h2>
          <span>{{ plan.taskDone }}/{{ plan.totalTasks }}</span>
        </header>
        <section v-for="stage in plan.stages" :key="stage.id" class="day-section" :class="{ active: stage.id === activeStage?.id }">
          <button class="day-title" type="button" @click="router.replace({ query: { stage: stage.id } })">
            <span>阶段 {{ stage.id }}</span>
            <strong>{{ stage.title }}</strong>
            <small>{{ stage.scheduleLabel }}</small>
          </button>
          <button
            v-for="task in stage.tasks"
            v-show="stage.id === activeStage?.id"
            :key="task.id"
            class="task-row"
            :class="{ active: task.id === activeTask?.id }"
            type="button"
            @click="openTask(stage.id, task.id)"
          >
            <i :class="{ done: task.done, running: task.status === '进行中' }" />
            <span :title="task.title"><small>{{ task.type }}</small><b>{{ task.title }}</b></span>
            <em>{{ task.done ? '已完成' : task.status ?? '未开始' }}</em>
          </button>
        </section>
        <div class="today-progress">
          <strong>{{ stageProgress }}%</strong>
          <span>本阶段 {{ stageDone }} / {{ activeStage?.tasks.length ?? 0 }} 完成</span>
        </div>
      </aside>
    </template>

    <section ref="contentPanel" class="content-panel panel" @scroll.passive="recordReading()">
      <div class="content-context">
        <span>{{ activeTask?.type ?? '任务' }}</span>
        <p>{{ activeTask?.completionSource ?? (activeTask?.status === '进行中' ? '正在记录有效学习行为' : '切换任务不会修改完成状态') }}</p>
      </div>

          <template v-if="isExerciseTask">
          <LearningQuestionCard
            v-if="exercise"
            :model-value="selectedAnswer"
            class="quiz-card--main"
            :exercise="exercise"
            :index="exerciseIndex"
            :total="taskExercises.length"
            :scene="activeTask?.type === '测验' ? 'assessment' : 'practice'"
            :result="quizResult"
            :answered-count="taskExercises.filter((item) => item.draftAnswer || item.userAnswer).length"
            :submitted="groupSubmitted"
              @update:model-value="selectAnswer"
              @update-language="selectLanguage"
            @submit-group="submitQuizGroup"
            @previous="moveExercise(-1)"
            @next="moveExercise(1)"
          />
          <section v-if="visibleGroupResult" class="inline-result-summary">
            <header>
              <div><strong>{{ visibleGroupResult.correctRate }}%</strong><span>本组正确率</span></div>
              <p>完成 {{ visibleGroupResult.total }} 题，答对 {{ visibleGroupResult.correctCount }} 题，答错 {{ visibleGroupResult.wrongCount }} 题。</p>
            </header>
            <p class="mastery-recommendation">{{ masteryRecommendation }}</p>
            <div v-if="wrongExercises.length" class="inline-wrong-review">
              <article v-for="(item, index) in wrongExercises" :key="item.id">
                <span>错题 {{ index + 1 }} · {{ item.knowledge }}</span>
                <strong>{{ item.title }}</strong>
                <p>你的答案：{{ item.userAnswer }}；正确答案：{{ item.answer }}</p>
              </article>
            </div>
            <div class="result-actions">
              <button class="primary-btn" type="button" :disabled="!nextTask" @click="moveTask(1)">进入下一任务</button>
              <button class="outline-btn" type="button" @click="openFollowup('repeat')">再练一组</button>
              <button class="outline-btn" type="button" :disabled="!wrongExercises.length" @click="openFollowup('reinforce')">错题巩固</button>
              <button class="text-action" type="button" @click="router.push(`/learning/${plan.id}/mistakes`)">查看错题本</button>
            </div>
            <section v-if="followupMode" class="followup-config">
              <div>
                <strong>{{ followupMode === 'reinforce' ? '生成错题巩固任务' : '再练一组' }}</strong>
                <small v-if="followupMode === 'repeat'">当前阶段还有 {{ availableReserveCount }} 道匹配备用题，不足部分由 AI 补充。</small>
                <small v-else>默认每道错题生成 2 道变式题，单次最多 15 题。</small>
              </div>
              <label><span>题量</span><input v-model.number="followupCount" type="number" min="3" :max="followupMode === 'reinforce' ? 15 : 40" /></label>
              <label><span>难度</span><select v-model="followupDifficulty"><option>保持难度</option><option>逐步提升</option></select></label>
              <button class="primary-btn" type="button" @click="createFollowupTask">创建并开始</button>
              <button class="text-action" type="button" @click="followupMode = null">取消</button>
            </section>
          </section>

          <section v-if="!exercise" class="lesson-card lesson-card--single"><div class="lesson-copy"><h2>题组准备中</h2><p>当前任务还没有可用题目，不会自动判定完成。</p></div></section>
          </template>

          <section v-else-if="activeTask?.type === '资料'" class="lesson-card lesson-card--single resource-in-task">
            <div class="lesson-copy">
              <span class="inline-resource-label">关联资料 · {{ relatedResource?.group ?? '学习内容' }}</span>
              <h2>{{ relatedResource?.title ?? '阅读个性化学习手册' }}</h2>
              <p>{{ activeStage?.desc }}</p>
              <div class="reading-outline"><strong>阅读重点</strong><span>核心概念与定义</span><span>高频误区与辨析</span><span>典型例子与应用场景</span></div>
              <blockquote>{{ relatedResource?.desc ?? '先阅读系统按薄弱点整理的核心讲解，把不懂的概念标记出来。' }}</blockquote>
              <button v-if="relatedResource" class="outline-btn inline-action" type="button" @click="router.push({ path: `/learning/${plan.id}/resources`, query: { type: relatedResource.group, task: activeTask?.id } })">查看完整资源</button>
            </div>
          </section>

          <section v-else-if="activeTask?.type === '案例'" class="lesson-card">
            <div class="lesson-copy">
              <h2>代码案例拆解</h2>
              <p>通过可运行的代码示例观察概念在实际程序里的表现，重点看输入、调用链和输出结果。</p>
              <ul>
                <li>先读父类和子类的职责边界。</li>
                <li>再预测输出，最后对照解析。</li>
              </ul>
              <button class="primary-btn inline-action" type="button" @click="runCase">运行案例并查看结果</button>
            </div>
            <pre><code>Animal animal = new Dog();
animal.sound();

// 运行时对象是 Dog，所以调用 Dog.sound()</code></pre>
          </section>

          <section v-else-if="activeTask?.type === '讲解'" class="lesson-card">
            <div class="lesson-copy">
              <span class="inline-resource-label">概念讲解 · {{ relatedResource?.title ?? '智能生成内容' }}</span>
              <h2>1. 概念讲解</h2>
              <p>
                {{ activeStage?.desc }}
              </p>
              <ul>
                <li>先结合资料明确核心概念和适用场景。</li>
                <li>再通过练习获得反馈，并根据错题调整复习计划。</li>
              </ul>
              <div class="diagram">
                <span>资料理解<br />{{ exercise?.knowledge }}</span>
                <AppIcon name="chevron-right" :size="22" />
                <span>专项练习<br />提交答案</span>
                <AppIcon name="chevron-right" :size="22" />
                <span>反馈复盘<br />更新掌握度</span>
              </div>
            </div>
            <pre v-if="exercise?.code"><code>class Animal {
  void speak() {
    System.out.println("Animal");
  }
}

class Dog extends Animal {
  @Override
  void speak() {
    System.out.println("Dog");
  }
}</code></pre>
          </section>

          <LearningQuestionCard
            v-if="hasCheckpoint && exercise"
            :model-value="selectedAnswer"
            :exercise="exercise"
            :index="exerciseIndex"
            :total="taskExercises.length"
            scene="checkpoint"
            :result="quizResult"
            :answered-count="taskExercises.filter((item) => item.draftAnswer || item.userAnswer).length"
            :submitted="groupSubmitted"
              @update:model-value="selectAnswer"
              @update-language="selectLanguage"
            @submit-group="submitQuizGroup"
            @previous="moveExercise(-1)"
            @next="moveExercise(1)"
          />
    </section>

    <template #aside>
      <LearningTutorPanel
        :plan="plan"
        :stage="activeStage"
        :task="activeTask"
        :exercise="exercise"
        mode="inline"
      />
    </template>

    <template #footer>
      <span class="footer-hint">{{ activeTask?.done ? activeTask.completionSource ?? '当前任务已完成' : '系统将根据阅读、作答或操作行为自动完成任务' }}</span>
      <button class="outline-btn" type="button" :disabled="!previousTask" @click="moveTask(-1)">上一个任务</button>
      <button class="primary-btn" type="button" :disabled="!nextTask" @click="activeTask?.done ? moveTask(1) : completeAndContinue()">{{ activeTask?.done ? '下一个任务' : '手动完成并继续' }}</button>
    </template>
  </LearningDetailShell>
</template>

<style scoped>
.study-page {
  min-height: 100%;
  background: var(--color-bg);
  padding: 18px 24px 34px;
}

.study-page,
.study-page * {
  box-sizing: border-box;
}

h1,
h2,
p,
ul {
  margin: 0;
}

button,
textarea {
  font: inherit;
}

.task-nav-head,
.content-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-nav-head > span {
  color: var(--color-text-muted);
  font-size: 13px;
}

.day-section {
  margin-top: 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.day-section.active {
  border-color: #bfdbfe;
}

.day-title {
  width: 100%;
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  padding: 10px;
  border: 0;
  background: transparent;
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
}

.day-title:hover {
  background: var(--ui-hover-bg);
}

.day-section.active .day-title {
  background: var(--ui-hover-strong-bg);
}

.day-title > small {
  grid-column: 2;
  color: var(--color-text-muted);
  font-size: 11px;
}

.day-title span {
  color: var(--color-info);
  font-size: 12px;
  font-weight: 800;
}

.day-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.task-row > span {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.task-row small {
  color: var(--color-info);
  font-size: 10px;
  font-weight: 800;
}

.task-row b {
  min-width: 0;
  overflow: hidden;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content-context {
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--color-border);
}

.content-context span,
.inline-resource-label {
  border-radius: 999px;
  background: color-mix(in srgb, #2563eb 11%, var(--color-surface));
  color: var(--color-info);
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 800;
}

.content-context p {
  color: var(--color-text-muted);
  font-size: 13px;
}

.reading-outline {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 22px;
}

.reading-outline strong {
  grid-column: 1 / -1;
}

.reading-outline span {
  min-height: 68px;
  display: grid;
  place-items: center;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: color-mix(in srgb, #2563eb 7%, var(--color-surface));
  color: var(--color-text);
  text-align: center;
  font-size: 13px;
  font-weight: 700;
}

.resource-in-task blockquote {
  margin: 20px 0 0;
  padding: 15px 18px;
  border-left: 3px solid #2563eb;
  background: color-mix(in srgb, #2563eb 11%, var(--color-surface));
  color: var(--color-text);
  line-height: 1.7;
}

.footer-hint {
  margin-right: auto;
  color: var(--color-text-muted);
  font-size: 13px;
}

.topbar,
.study-layout {
  max-width: 1500px;
  margin: 0 auto;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 48px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--color-text-muted);
}

.breadcrumb strong {
  color: var(--color-text);
}

.top-actions {
  display: flex;
  gap: 10px;
}

.primary-btn,
.outline-btn {
  height: 38px;
  border-radius: 8px;
  padding: 0 18px;
  cursor: pointer;
  font-weight: 800;
}

.outline-btn:hover {
  background: var(--ui-hover-bg);
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

.study-layout {
  margin-top: 18px;
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 360px;
  gap: 18px;
  align-items: start;
}

.panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  padding: 20px;
}

.task-panel h2,
.tutor-panel h2,
.content-panel h2 {
  color: var(--color-text);
  font-size: 20px;
}

.task-row {
  width: 100%;
  min-height: 48px;
  margin-top: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text);
  background: var(--color-surface);
  text-align: left;
  cursor: pointer;
}

.task-row > i {
  width: 9px;
  height: 9px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--color-border);
}

.task-row > i.running {
  background: #f59e0b;
  box-shadow: 0 0 0 3px #fef3c7;
}

.task-row > i.done {
  background: #22c55e;
  box-shadow: 0 0 0 3px #dcfce7;
}

.task-row > span {
  min-width: 0;
  flex: 1;
}

.task-row > em {
  flex: 0 0 auto;
  color: var(--color-text-muted);
  font-size: 11px;
  font-style: normal;
  white-space: nowrap;
}

.task-row.active {
  border-color: var(--color-primary);
  background: var(--ui-hover-strong-bg);
}

.task-row:hover:not(.active) {
  background: var(--ui-hover-bg);
}

.today-progress {
  margin-top: 28px;
  border-top: 1px solid var(--color-border);
  padding-top: 20px;
  display: grid;
  gap: 8px;
}

.today-progress strong {
  color: var(--color-info);
  font-size: 28px;
}

.today-progress span {
  color: var(--color-text);
  font-weight: 800;
}

.today-progress small {
  color: var(--color-text-muted);
}

.content-panel h1 {
  color: var(--color-text);
  font-size: 26px;
  font-weight: 800;
}

.content-panel {
  max-height: calc(100vh - 190px);
  overflow-y: auto;
}

.tabs {
  margin-top: 18px;
  display: flex;
  gap: 34px;
  border-bottom: 1px solid var(--color-border);
}

.tabs button {
  height: 40px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  font-weight: 800;
}

.tabs button.active {
  color: var(--color-text);
  border-bottom-color: var(--color-primary);
}

.lesson-card {
  margin-top: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
}

.lesson-card--single {
  grid-template-columns: 1fr;
}

.lesson-copy,
.quiz-card,
pre,
.assistant-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.lesson-copy,
.quiz-card,
.assistant-card {
  padding: 24px;
}

.lesson-copy > .inline-resource-label {
  display: inline-flex;
  align-items: center;
  margin-bottom: 10px;
}

.lesson-copy > h2 {
  line-height: 1.35;
}

.lesson-copy p,
.quiz-card p,
.assistant-card p {
  margin-top: 12px;
  color: var(--color-text);
  line-height: 1.8;
}

.lesson-copy ul,
.assistant-card ul {
  margin-top: 12px;
  color: var(--color-text-muted);
  line-height: 1.8;
  padding-left: 20px;
}

.diagram {
  margin-top: 22px;
  border: 1px dashed #bfdbfe;
  border-radius: 8px;
  padding: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--color-info);
  text-align: center;
}

.diagram span {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
  color: var(--color-text);
}

pre {
  margin: 0;
  padding: 18px;
  background: var(--color-hover);
  overflow: auto;
  color: var(--color-text);
}

.quiz-card {
  margin-top: 18px;
}

.quiz-card--main {
  min-height: 430px;
  margin-top: 18px;
}

.content-panel > .question-card {
  margin-top: 18px;
}

.inline-result-summary {
  display: grid;
  gap: 12px;
  margin-top: 14px;
  padding: 20px;
  border: 1px solid #bbf7d0;
  border-radius: 10px;
  background: color-mix(in srgb, #16a34a 9%, var(--color-surface));
}

.inline-result-summary header {
  display: flex;
  align-items: center;
  gap: 18px;
}

.inline-result-summary header > div {
  display: grid;
}

.inline-result-summary strong {
  color: var(--color-success);
  font-size: 21px;
}

.inline-result-summary span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.inline-result-summary header p,
.inline-wrong-review p {
  margin: 0;
  color: var(--color-text-muted);
}

.mastery-recommendation {
  margin: 0;
  color: var(--color-success);
  font-size: 13px;
}

.inline-wrong-review {
  display: grid;
  gap: 8px;
}

.inline-wrong-review article {
  display: grid;
  gap: 5px;
  padding: 11px 0;
  border-top: 1px solid #d1fae5;
}

.result-actions,
.followup-config {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.text-action {
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  border-radius: var(--ui-hover-radius);
  padding: 6px 8px;
}

.text-action:hover {
  background: var(--ui-hover-bg);
  color: var(--color-text);
}

.followup-config {
  padding-top: 14px;
  border-top: 1px solid #bbf7d0;
}

.followup-config > div {
  display: grid;
  min-width: 260px;
  margin-right: auto;
}

.followup-config small {
  color: var(--color-text-muted);
}

.followup-config label {
  display: grid;
  gap: 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.followup-config input,
.followup-config select {
  min-height: 36px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: var(--color-surface);
}

.quiz-card header > div {
  display: grid;
  gap: 5px;
}

.quiz-card header small {
  color: var(--color-text-muted);
}

.quiz-navigation {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.quiz-navigation span {
  color: var(--color-text-muted);
  text-align: center;
  font-size: 12px;
}

.checkpoint-card {
  border-color: #bfdbfe;
  background: color-mix(in srgb, #2563eb 7%, var(--color-surface));
}

.checkpoint-label {
  color: var(--color-info);
  font-size: 11px;
  font-weight: 800;
}

.primary-btn:disabled,
.outline-btn:disabled {
  opacity: .45;
  cursor: not-allowed;
}

.quiz-card header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.quiz-card label {
  min-height: 42px;
  margin-top: 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.inline-action {
  margin-top: 18px;
}

.review-tags {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-tags span {
  border-radius: 6px;
  background: color-mix(in srgb, #f97316 10%, var(--color-surface));
  color: var(--color-warning);
  padding: 5px 9px;
  font-weight: 800;
}

.tutor-panel header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tutor-panel header span {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, #2563eb 11%, var(--color-surface));
  color: var(--color-info);
}

.assistant-card {
  margin-top: 18px;
  background: var(--color-hover);
}

.assistant-card strong {
  display: block;
  margin-top: 12px;
  color: var(--color-info);
}

.tutor-panel label {
  min-height: 96px;
  margin-top: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  display: grid;
  grid-template-columns: 1fr 26px;
  align-items: end;
  padding: 12px;
}

.tutor-panel textarea {
  min-width: 0;
  min-height: 72px;
  border: 0;
  outline: 0;
  resize: none;
  background: transparent;
}

.quick-row {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.quick-row button {
  height: 34px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.quick-row button:hover {
  background: var(--ui-hover-bg);
}

@media (max-width: 1280px) {
  .study-layout,
  .lesson-card {
    grid-template-columns: 1fr;
  }
}
</style>
