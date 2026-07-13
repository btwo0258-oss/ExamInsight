<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CodeLanguageKey, Exercise } from '@/mock'

type QuestionResult = {
  correct: boolean
  explanation: string
  correctAnswer: string
  score?: number
  feedback?: string
}

const props = withDefaults(defineProps<{
  exercise: Exercise
  index: number
  total: number
  modelValue: string
  scene: 'checkpoint' | 'practice' | 'assessment'
  result?: QuestionResult
  answeredCount?: number
  submitted?: boolean
}>(), {
  answeredCount: 0,
  submitted: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  updateLanguage: [value: CodeLanguageKey]
  submitGroup: []
  previous: []
  next: []
}>()

function sceneTitle() {
  if (props.scene === 'checkpoint') return '快速理解检查'
  if (props.scene === 'assessment') return '阶段测验'
  return '专项练习'
}

type CodeRunState = {
  status: 'idle' | 'passed' | 'compile_error' | 'runtime_error' | 'timeout' | 'wrong_answer'
  title: string
  message: string
  duration?: string
  actual?: string
}

const codeRun = ref<CodeRunState>({ status: 'idle', title: '', message: '' })
const multiAnswers = computed(() => props.modelValue ? props.modelValue.split('||') : [])
const codeLanguage = computed(() => props.exercise.codeLanguages?.find((item) => item.key === props.exercise.selectedLanguage) ?? props.exercise.codeLanguages?.[0])
const starterCode = computed(() => codeLanguage.value?.starterCode ?? props.exercise.starterCode ?? '')
const referenceAnswer = computed(() => codeLanguage.value?.referenceAnswer ?? props.exercise.answer)

function toggleMultiple(option: string) {
  if (props.submitted) return
  const selected = new Set(multiAnswers.value)
  if (selected.has(option)) selected.delete(option)
  else selected.add(option)
  const answer = props.exercise.options.filter((item) => selected.has(item)).join('||')
  emit('update:modelValue', answer)
}

function updateText(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement | HTMLTextAreaElement).value)
}

function runSampleCode() {
  const source = props.modelValue.trim()
  const runtime = codeLanguage.value?.runtime ?? props.exercise.runtime ?? '当前语言'
  if (!source) {
    codeRun.value = { status: 'compile_error', title: '无法运行', message: '请先编写代码，再运行公开示例。' }
    return
  }
  if (/syntax_error|\blog\s*$/.test(source)) {
    codeRun.value = { status: 'compile_error', title: '编译错误', message: `${runtime}：第 1 行附近存在无法识别的语法或缺少完整程序结构。` }
    return
  }
  if (/while\s*\(\s*true\s*\)|while\s+True\s*:|for\s*\(\s*;\s*;\s*\)/.test(source)) {
    codeRun.value = { status: 'timeout', title: '执行超时', message: '程序超过 2000 ms 时间限制，可能存在死循环。', duration: '> 2000 ms' }
    return
  }
  if (/throw\s+new|raise\s+\w+|panic\s*\(/.test(source)) {
    codeRun.value = { status: 'runtime_error', title: '运行时错误', message: `${runtime}：公开示例执行时触发未处理异常。`, duration: '18 ms' }
    return
  }
  const requirements = codeLanguage.value?.requiredCodePatterns ?? props.exercise.requiredCodePatterns ?? []
  const matched = requirements.filter((pattern) => source.includes(pattern)).length
  if (matched !== requirements.length) {
    codeRun.value = {
      status: 'wrong_answer',
      title: '答案错误',
      message: `公开示例 1/${props.exercise.sampleTests?.length ?? 1} 未通过，请检查返回值和边界条件。`,
      duration: '12 ms',
      actual: source.includes('return 0') || source.includes('return false') ? '0 / false' : '输出与预期不一致',
    }
    return
  }
  codeRun.value = {
    status: 'passed',
    title: '公开示例全部通过',
    message: `${props.exercise.sampleTests?.length ?? 0} 组公开示例通过；隐藏用例将在统一交卷后执行。`,
    duration: '16 ms',
  }
}

function changeLanguage(event: Event) {
  emit('updateLanguage', (event.target as HTMLSelectElement).value as CodeLanguageKey)
}

watch([() => props.exercise.id, () => props.exercise.selectedLanguage], () => {
  codeRun.value = { status: 'idle', title: '', message: '' }
})
</script>

<template>
  <article class="question-card" :class="`question-card--${scene}`">
    <header class="question-head">
      <div>
        <span v-if="scene === 'checkpoint'" class="scene-label">可选理解检查</span>
        <h2>{{ sceneTitle() }}</h2>
        <small>题目 {{ index + 1 }}/{{ total }} · {{ exercise.difficulty }} · {{ exercise.type }}</small>
      </div>
    </header>

    <p class="question-title">{{ exercise.title }}</p>
    <pre v-if="exercise.code"><code>{{ exercise.code }}</code></pre>
    <div v-if="exercise.type === '单选题' || exercise.type === '判断题'" class="option-list">
      <label v-for="option in exercise.options" :key="option" :class="{ selected: modelValue === option }">
        <input
          :checked="modelValue === option"
          :value="option"
          name="learning-question"
          type="radio"
          :disabled="submitted"
          @change="emit('update:modelValue', option)"
        />
        <span>{{ option }}</span>
      </label>
    </div>
    <div v-else-if="exercise.type === '多选题'" class="option-list">
      <label v-for="option in exercise.options" :key="option" :class="{ selected: multiAnswers.includes(option) }">
        <input
          :checked="multiAnswers.includes(option)"
          type="checkbox"
          :disabled="submitted"
          @change="toggleMultiple(option)"
        />
        <span>{{ option }}</span>
      </label>
    </div>
    <label v-else-if="exercise.type === '填空题'" class="text-answer text-answer--single">
      <span>填写答案</span>
      <input :value="modelValue" :disabled="submitted" placeholder="请输入答案" @input="updateText" />
    </label>
    <label v-else-if="exercise.type === '简答题'" class="text-answer">
      <span>你的回答</span>
      <textarea :value="modelValue" :disabled="submitted" rows="7" placeholder="结合题目说明你的理解，统一交卷后由评分标准辅助判定。" @input="updateText" />
      <small v-if="exercise.gradingRubric?.length">评分关注：{{ exercise.gradingRubric.join('；') }}</small>
    </label>
    <section v-else class="code-answer">
      <header>
        <strong>代码编辑器</strong>
        <div class="code-toolbar">
          <label>
            <span>语言</span>
            <select :value="exercise.selectedLanguage ?? codeLanguage?.key" :disabled="submitted" @change="changeLanguage">
              <option v-for="language in exercise.codeLanguages" :key="language.key" :value="language.key">{{ language.label }} · {{ language.runtime }}</option>
            </select>
          </label>
          <small>运行公开示例不会提交答案</small>
        </div>
      </header>
      <textarea
        :value="modelValue || starterCode"
        :disabled="submitted"
        spellcheck="false"
        rows="15"
        @input="updateText"
      />
      <div class="code-actions">
        <button class="outline-btn" type="button" :disabled="submitted" @click="runSampleCode">运行公开示例</button>
        <span v-if="exercise.sampleTests?.length">{{ exercise.sampleTests.length }} 组公开示例 · 隐藏用例在交卷后执行</span>
      </div>
      <section v-if="codeRun.status !== 'idle'" class="code-run-result" :class="`code-run-result--${codeRun.status}`">
        <header><strong>{{ codeRun.title }}</strong><span v-if="codeRun.duration">{{ codeRun.duration }}</span></header>
        <p>{{ codeRun.message }}</p>
        <div v-if="codeRun.status === 'wrong_answer'" class="failed-case">
          <span>公开用例 1</span>
          <dl><dt>输入</dt><dd>{{ exercise.sampleTests?.[0]?.input ?? '公开输入' }}</dd><dt>预期输出</dt><dd>{{ exercise.sampleTests?.[0]?.expected ?? '正确结果' }}</dd><dt>实际输出</dt><dd>{{ codeRun.actual }}</dd></dl>
        </div>
      </section>
    </section>

    <p v-if="submitted && result" class="answer-feedback" :class="{ correct: result.correct }">
      <strong>{{ result.correct ? '回答正确' : exercise.type === '简答题' ? `本题得分 ${result.score ?? 0}，尚未达到通过标准` : `回答错误，参考答案是 ${exercise.type === '代码题' ? referenceAnswer : result.correctAnswer}` }}</strong>
      <span>{{ result.feedback ?? result.explanation }}</span>
    </p>

    <footer class="question-navigation">
      <button class="outline-btn" type="button" :disabled="index <= 0" @click="emit('previous')">上一题</button>
      <span>{{ answeredCount }}/{{ total }} 已作答</span>
      <button v-if="index < total - 1" class="primary-btn" type="button" @click="emit('next')">下一题</button>
      <button v-else-if="!submitted" class="primary-btn" type="button" :disabled="answeredCount < total" @click="emit('submitGroup')">提交本组</button>
      <span v-else class="submitted-label">本组已交卷</span>
    </footer>
  </article>
</template>

<style scoped>
.question-card {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  padding: 20px;
}

.question-card--checkpoint {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.question-head,
.question-navigation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.question-head > div {
  display: grid;
  gap: 5px;
}

h2,
p,
pre {
  margin: 0;
}

h2 {
  color: var(--color-text);
  font-size: 21px;
}

small,
.question-navigation span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.scene-label {
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
}

.question-title {
  margin-top: 20px;
  color: var(--color-text);
  font-size: 16px;
  line-height: 1.7;
}

pre {
  margin-top: 14px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #f8fafc;
  color: var(--color-text);
  overflow: auto;
  line-height: 1.55;
}

.option-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.option-list label {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 13px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.option-list label.selected {
  border-color: #93c5fd;
  background: #eff6ff;
}

.text-answer,
.code-answer {
  display: grid;
  gap: 9px;
  margin-top: 16px;
}

.text-answer > span,
.code-answer header strong {
  color: var(--color-text);
  font-size: 13px;
  font-weight: 800;
}

.text-answer input,
.text-answer textarea,
.code-answer textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  outline: 0;
  box-sizing: border-box;
  font: inherit;
}

.text-answer input {
  height: 44px;
  padding: 0 13px;
}

.text-answer textarea {
  resize: vertical;
  padding: 12px 13px;
  line-height: 1.65;
}

.text-answer input:focus,
.text-answer textarea:focus,
.code-answer textarea:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px #dbeafe;
}

.code-answer {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: #0f172a;
}

.code-answer header,
.code-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  background: #f8fafc;
}

.code-toolbar,
.code-toolbar label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.code-answer > header > strong,
.code-toolbar small {
  white-space: nowrap;
}

.code-toolbar {
  min-width: 0;
  flex: 1;
  justify-content: flex-end;
}

.code-toolbar label > span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.code-toolbar select {
  height: 32px;
  max-width: 190px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 0 28px 0 9px;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
}

.code-answer textarea {
  resize: vertical;
  min-height: 280px;
  border: 0;
  border-radius: 0;
  padding: 16px;
  background: #0f172a;
  color: #e2e8f0;
  font-family: "Cascadia Code", Consolas, monospace;
  line-height: 1.55;
  tab-size: 2;
}

.code-actions span,
.code-answer header small {
  color: var(--color-text-muted);
  font-size: 11px;
}

.code-run-result {
  display: grid;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid #334155;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
  line-height: 1.55;
}

.code-run-result > header {
  padding: 0;
  background: transparent;
  color: inherit;
}

.code-run-result > header strong,
.code-run-result > header span {
  color: inherit;
}

.code-run-result--passed {
  background: #ecfdf3;
  color: #166534;
}

.code-run-result--compile_error,
.code-run-result--runtime_error,
.code-run-result--timeout {
  background: #fef2f2;
  color: #b91c1c;
}

.failed-case {
  display: grid;
  gap: 7px;
  margin-top: 2px;
  padding: 10px;
  border: 1px solid #fed7aa;
  border-radius: 7px;
  background: rgba(255, 255, 255, .65);
}

.failed-case > span {
  font-weight: 800;
}

.failed-case dl {
  display: grid;
  grid-template-columns: 70px 1fr;
  gap: 5px 9px;
  margin: 0;
}

.failed-case dt {
  color: var(--color-text-muted);
}

.failed-case dd {
  margin: 0;
  font-family: "Cascadia Code", Consolas, monospace;
}

.primary-btn,
.outline-btn {
  min-height: 38px;
  border-radius: 8px;
  padding: 0 17px;
  cursor: pointer;
  font: inherit;
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

.primary-btn:disabled,
.outline-btn:disabled {
  opacity: .42;
  cursor: not-allowed;
}

.answer-feedback {
  display: grid;
  gap: 5px;
  margin-top: 16px;
  padding: 13px 15px;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
  line-height: 1.55;
}

.answer-feedback.correct {
  background: #ecfdf3;
  color: #166534;
}

.question-navigation {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.submitted-label {
  color: #15803d !important;
  font-weight: 800;
}
</style>
