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
const currentExerciseId = ref(plan.value.exercises[0]?.id)
const exercise = computed(() => plan.value.exercises.find((item) => item.id === currentExerciseId.value) ?? plan.value.exercises[0])
const selectedAnswer = ref('')
const result = ref<ReturnType<typeof learningStore.submitExercise>>()
const generationMessage = ref('')
const correctCount = computed(() => Math.round((plan.value.correctRate / 100) * plan.value.exerciseDone))

function submitAnswer() {
  if (!exercise.value || !selectedAnswer.value) return
  result.value = learningStore.submitExercise(plan.value.id, exercise.value.id, selectedAnswer.value)
}

function generateSimilar() {
  if (!exercise.value) return
  const generated = learningStore.generateSimilarExercise(plan.value.id, exercise.value.id)
  if (!generated) return
  currentExerciseId.value = generated.id
  selectedAnswer.value = ''
  result.value = undefined
  generationMessage.value = '已生成 1 道同类题并切换到新题。'
}
</script>

<template>
  <StudentShell>
    <div class="practice-page">
      <header class="page-head">
        <button type="button" @click="router.push(`/learning/${plan.id}`)">
          <AppIcon name="chevron-left" :size="18" />
        </button>
        <div>
          <h1>继承与多态专项练习</h1>
          <p>按知识点、难度和题型训练，提交后同步更新掌握度面板。</p>
        </div>
      </header>

      <main class="practice-layout">
        <section class="question-panel panel">
          <div class="filters">
            <label>知识点：<select><option>多态</option><option>继承</option><option>接口</option></select></label>
            <label>难度：<select><option>中等</option><option>基础</option><option>提高</option></select></label>
            <label>题型：<select><option>单选/代码题</option><option>判断题</option></select></label>
          </div>

          <article class="question-card">
            <header>
              <div>
                <h2>题目 {{ plan.exercises.findIndex((item) => item.id === exercise?.id) + 1 }}/{{ plan.exercises.length }}</h2>
                <span>{{ exercise?.difficulty }}</span>
              </div>
              <button type="button"><AppIcon name="star" :size="16" /> 收藏</button>
            </header>
            <p>{{ exercise?.title }}</p>
            <pre v-if="exercise?.code"><code>{{ exercise.code }}</code></pre>
            <label v-for="option in exercise?.options" :key="option">
              <input v-model="selectedAnswer" :value="option" name="answer" type="radio" />
              <span>{{ option }}</span>
            </label>
            <footer>
              <button class="primary-btn" type="button" :disabled="!selectedAnswer" @click="submitAnswer">提交答案</button>
              <button class="outline-btn" type="button" @click="result = result ?? (exercise?.submitted ? { correct: exercise.userAnswer === exercise.answer, explanation: exercise.explanation, correctAnswer: exercise.answer } : undefined)">看解析</button>
              <button class="outline-btn" type="button" @click="generateSimilar">生成同类题</button>
            </footer>
            <p v-if="result" class="answer-feedback" :class="{ correct: result.correct }">
              {{ result.correct ? '回答正确。' : `回答错误，正确答案是 ${result.correctAnswer}。` }} {{ result.explanation }}
            </p>
            <p v-if="generationMessage" class="generation-message">{{ generationMessage }}</p>
          </article>
        </section>

        <aside class="result-panel panel">
          <h2>本组表现</h2>
          <div class="rate-circle">
            <strong>{{ plan.correctRate }}%</strong>
            <span>{{ plan.exerciseDone }}/{{ plan.totalExercises }}</span>
          </div>
          <div class="stat-row"><span>正确数</span><strong>{{ correctCount }}</strong></div>
          <div class="stat-row"><span>错误数</span><strong>{{ plan.wrongQuestions.length }}</strong></div>

          <section class="weak-section">
            <h3>薄弱知识点 TOP3</h3>
            <article v-for="wrong in plan.wrongQuestions" :key="wrong.id">
              <span>{{ wrong.knowledge[0] }}</span>
              <small>错 1</small>
            </article>
          </section>

          <section class="mastery">
            <h3>预计掌握度</h3>
            <strong>中等</strong>
            <p>再练 6 题可提升至高掌握</p>
          </section>
        </aside>
      </main>
    </div>
  </StudentShell>
</template>

<style scoped>
.practice-page {
  min-height: 100%;
  padding: 28px 34px 42px;
  background: var(--color-bg);
}

.practice-page,
.practice-page * {
  box-sizing: border-box;
}

h1,
h2,
h3,
p {
  margin: 0;
}

button,
select {
  font: inherit;
}

.page-head,
.practice-layout {
  max-width: 1280px;
  margin: 0 auto;
}

.page-head {
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 16px;
  align-items: center;
}

.page-head > button {
  width: 42px;
  height: 42px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  cursor: pointer;
}

h1 {
  color: var(--color-text);
  font-size: 30px;
  font-weight: 800;
}

.page-head p {
  margin-top: 7px;
  color: var(--color-text-muted);
}

.practice-layout {
  margin-top: 24px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
}

.panel,
.question-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.panel {
  padding: 20px;
}

.filters {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.filters label {
  height: 42px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-muted);
}

.filters select {
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text);
}

.question-card {
  margin-top: 18px;
  padding: 24px;
}

.question-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.question-card h2,
.result-panel h2 {
  color: var(--color-text);
  font-size: 22px;
}

.question-card header span {
  display: inline-block;
  margin-top: 6px;
  border-radius: 6px;
  padding: 3px 8px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.question-card header button {
  height: 32px;
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
}

.question-card p {
  margin-top: 22px;
  color: var(--color-text);
  line-height: 1.8;
}

pre {
  margin: 16px 0;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #f8fafc;
  overflow: auto;
}

.question-card label {
  min-height: 44px;
  margin-top: 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
}

.question-card footer {
  margin-top: 26px;
  display: flex;
  gap: 12px;
}

.primary-btn,
.outline-btn {
  height: 42px;
  border-radius: 8px;
  padding: 0 24px;
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

.rate-circle {
  width: 136px;
  height: 136px;
  margin: 26px auto;
  border: 10px solid #dbeafe;
  border-top-color: #2563eb;
  border-radius: 999px;
  display: grid;
  place-items: center;
  text-align: center;
}

.rate-circle strong {
  color: #2563eb;
  font-size: 26px;
}

.rate-circle span {
  color: var(--color-text-muted);
}

.stat-row {
  min-height: 38px;
  display: flex;
  justify-content: space-between;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-muted);
}

.stat-row strong {
  color: var(--color-text);
}

.weak-section,
.mastery {
  margin-top: 22px;
}

.weak-section h3,
.mastery h3 {
  color: var(--color-text);
  font-size: 16px;
  margin-bottom: 12px;
}

.weak-section article {
  min-height: 34px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.weak-section small {
  border-radius: 6px;
  background: #fff7ed;
  color: #f97316;
  padding: 3px 8px;
  font-weight: 800;
}

.mastery {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}

.mastery strong {
  color: #f59e0b;
  font-size: 26px;
}

.mastery p {
  margin-top: 8px;
  color: var(--color-text-muted);
}

@media (max-width: 980px) {
  .practice-layout,
  .filters {
    grid-template-columns: 1fr;
  }
}
</style>
