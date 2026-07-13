<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { useLearningStore } from '@/stores/learning'

const route = useRoute()
const router = useRouter()
const learningStore = useLearningStore()
const plan = computed(() => learningStore.getPlan(Number(route.params.id)) ?? learningStore.plans[0]!)
const today = computed(() => plan.value.days.find((day) => day.tasks.some((task) => !task.done)) ?? plan.value.days[0])
const exercise = computed(() => plan.value.exercises[0])
const selectedAnswer = ref('')
const quizResult = ref<ReturnType<typeof learningStore.submitExercise>>()
const todayDone = computed(() => today.value?.tasks.filter((task) => task.done).length ?? 0)
const todayProgress = computed(() => {
  const total = today.value?.tasks.length ?? 0
  return total ? Math.round((todayDone.value / total) * 100) : 0
})

function setTaskDone(taskId: number, done: boolean) {
  learningStore.markTaskDone(plan.value.id, taskId, done)
}

function markCurrentDone() {
  const task = today.value?.tasks.find((item) => !item.done)
  if (task) setTaskDone(task.id, true)
}

function submitQuiz() {
  if (!exercise.value || !selectedAnswer.value) return
  quizResult.value = learningStore.submitExercise(plan.value.id, exercise.value.id, selectedAnswer.value)
}
</script>

<template>
  <StudentShell>
    <div class="study-page">
      <header class="topbar">
        <div class="breadcrumb">
          <AppIcon name="sidebar-left" :size="18" />
          <span>{{ plan.title }}</span>
          <AppIcon name="chevron-right" :size="14" />
          <span>Day {{ today?.id }}</span>
          <AppIcon name="chevron-right" :size="14" />
          <strong>{{ today?.tasks[0]?.title }}</strong>
        </div>
        <div class="top-actions">
          <button class="outline-btn" type="button" @click="router.push(`/learning/${plan.id}`)">返回工作台</button>
          <button class="primary-btn" type="button" @click="markCurrentDone">标记完成</button>
        </div>
      </header>

      <main class="study-layout">
        <aside class="task-panel panel">
          <h2>今日任务</h2>
          <label
            v-for="(task, index) in today?.tasks"
            :key="task.id"
            class="task-row"
            :class="{ active: index === 0 }"
          >
            <input
              :checked="task.done"
              type="checkbox"
              @change="setTaskDone(task.id, ($event.target as HTMLInputElement).checked)"
            />
            <span>{{ task.title }}</span>
          </label>
          <div class="today-progress">
            <strong>{{ todayProgress }}%</strong>
            <span>{{ todayDone }} / {{ today?.tasks.length ?? 0 }} 完成</span>
            <small>学习时长 18 分钟</small>
            <small>预计完成 32 分钟</small>
          </div>
        </aside>

        <section class="content-panel panel">
          <h1>{{ exercise?.knowledge ?? today?.title }}：核心概念讲解</h1>
          <nav class="tabs">
            <button class="active" type="button">讲解</button>
            <button type="button">例题</button>
            <button type="button">练习</button>
            <button type="button">问答</button>
          </nav>

          <section class="lesson-card">
            <div class="lesson-copy">
              <h2>1. 概念讲解</h2>
              <p>
                {{ exercise?.explanation ?? today?.desc }}
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

          <section class="quiz-card">
            <header>
              <h2>随堂小测 1/3</h2>
              <button class="primary-btn" type="button" :disabled="!selectedAnswer" @click="submitQuiz">提交答案</button>
            </header>
            <p>{{ exercise?.title }}</p>
            <label v-for="option in exercise?.options" :key="option">
              <input v-model="selectedAnswer" :value="option" name="quiz" type="radio" />
              <span>{{ option }}</span>
            </label>
            <p v-if="quizResult" class="quiz-feedback" :class="{ correct: quizResult.correct }">
              {{ quizResult.correct ? '回答正确。' : `回答错误，正确答案是 ${quizResult.correctAnswer}。` }}
              {{ quizResult.explanation }}
            </p>
          </section>
        </section>

        <aside class="tutor-panel panel">
          <header>
            <span><AppIcon name="brain" :size="20" /></span>
            <h2>AI 助教</h2>
          </header>
          <div class="assistant-card">
            <p>你现在卡在方法重写和运行时绑定，我会用代码例子解释。</p>
            <strong>关键要点</strong>
            <ul>
              <li>方法重写让子类提供新的实现</li>
              <li>动态绑定确保运行时调用子类实现</li>
            </ul>
          </div>
          <label>
            <textarea placeholder="继续追问当前知识点..." />
            <AppIcon name="send" :size="18" />
          </label>
          <div class="quick-row">
            <button type="button">换个例子</button>
            <button type="button">生成图解</button>
            <button type="button">出 3 道题</button>
          </div>
        </aside>
      </main>
    </div>
  </StudentShell>
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
  min-height: 48px;
  margin-top: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text);
}

.task-row.active {
  border-color: var(--color-primary);
  background: #f4f6f8;
}

.today-progress {
  margin-top: 28px;
  border-top: 1px solid var(--color-border);
  padding-top: 20px;
  display: grid;
  gap: 8px;
}

.today-progress strong {
  color: #2563eb;
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
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 18px;
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
  padding: 18px;
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
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  color: #2563eb;
  text-align: center;
}

.diagram span {
  min-width: 110px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
  color: var(--color-text);
}

pre {
  margin: 0;
  padding: 18px;
  background: #f8fafc;
  overflow: auto;
  color: var(--color-text);
}

.quiz-card {
  margin-top: 18px;
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
  background: #eff6ff;
  color: #2563eb;
}

.assistant-card {
  margin-top: 18px;
  background: #f8fafc;
}

.assistant-card strong {
  display: block;
  margin-top: 12px;
  color: #2563eb;
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

@media (max-width: 1280px) {
  .study-layout,
  .lesson-card {
    grid-template-columns: 1fr;
  }
}
</style>
