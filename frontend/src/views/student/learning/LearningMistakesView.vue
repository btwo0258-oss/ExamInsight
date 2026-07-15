<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import LearningQuestionCard from '@/components/learning/LearningQuestionCard.vue'
import LearningRouteState from '@/components/learning/LearningRouteState.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import type { CodeLanguageKey } from '@/mock'
import { useLearningStore } from '@/stores/learning'
import { useLearningPlanRoute } from '@/composables/useLearningPlanRoute'

const router = useRouter()
const learningStore = useLearningStore()
const { plan, hasPlan, isLoading, loadError, loadPlan } = useLearningPlanRoute()
const activeWrongId = ref<number>()
const activeModule = ref<'records' | 'sets'>('records')
const activeSetId = ref<number>()
const currentSetExerciseId = ref<number>()
const setAnswer = ref('')
const setResult = ref<Awaited<ReturnType<typeof learningStore.submitWrongReviewSet>>>()
const knowledgeFilter = ref('全部知识点')
const statusFilter = ref<'全部' | '需巩固' | '已掌握'>('全部')
const searchText = ref('')
const retakeMode = ref(false)
const retakeAnswer = ref('')
const retakeResult = ref<Awaited<ReturnType<typeof learningStore.reviewWrongQuestion>>>()
const reinforcementOpen = ref(false)
const reinforcementCountOptions = [2, 3, 5] as const
const reinforcementDifficultyOptions = ['保持难度', '逐步提升'] as const
const reinforcementCount = ref(2)
const reinforcementDifficulty = ref<'保持难度' | '逐步提升'>('保持难度')
const openSelectMenu = ref<'knowledge' | 'count' | 'difficulty' | null>(null)
const operationPending = ref(false)
const actionError = ref('')

const knowledgeOptions = computed(() => ['全部知识点', ...new Set(plan.value.wrongQuestions.flatMap((item) => item.knowledge))])
const filteredWrongs = computed(() => plan.value.wrongQuestions.filter((item) => {
  const knowledgeMatched = knowledgeFilter.value === '全部知识点' || item.knowledge.includes(knowledgeFilter.value)
  const statusMatched = statusFilter.value === '全部' || item.status === statusFilter.value
  const keyword = searchText.value.trim().toLowerCase()
  const searchMatched = !keyword || item.title.toLowerCase().includes(keyword) || item.knowledge.some((label) => label.toLowerCase().includes(keyword))
  return knowledgeMatched && statusMatched && searchMatched
}))
const wrong = computed(() => filteredWrongs.value.find((item) => item.id === activeWrongId.value) ?? filteredWrongs.value[0])
const exercise = computed(() => plan.value.exercises.find((item) => item.id === wrong.value?.id))
const wrongReferenceAnswer = computed(() => {
  if (exercise.value?.type !== '代码题') return wrong.value?.correctAnswer ?? ''
  return exercise.value.codeLanguages?.find((item) => item.key === (wrong.value?.answerLanguage ?? exercise.value?.selectedLanguage))?.referenceAnswer
    ?? wrong.value?.correctAnswer
    ?? ''
})
const statusCounts = computed(() => ({
  strengthening: plan.value.wrongQuestions.filter((item) => item.status === '需巩固').length,
  mastered: plan.value.wrongQuestions.filter((item) => item.status === '已掌握').length,
}))
const reviewSets = computed(() => plan.value.wrongReviewSets ?? [])
const activeSet = computed(() => reviewSets.value.find((item) => item.id === activeSetId.value) ?? reviewSets.value[0])
const setExercises = computed(() => (activeSet.value?.exerciseIds ?? []).map((id) => plan.value.exercises.find((item) => item.id === id)).filter((item): item is NonNullable<typeof item> => Boolean(item)))
const setExercise = computed(() => setExercises.value.find((item) => item.id === currentSetExerciseId.value) ?? setExercises.value[0])
const setExerciseIndex = computed(() => setExercises.value.findIndex((item) => item.id === setExercise.value?.id))
const setAnsweredCount = computed(() => setExercises.value.filter((item) => item.draftAnswer || item.userAnswer).length)

watch(filteredWrongs, (items) => {
  if (!items.some((item) => item.id === activeWrongId.value)) activeWrongId.value = items[0]?.id
}, { immediate: true })

watch(wrong, () => {
  retakeMode.value = false
  retakeAnswer.value = ''
  retakeResult.value = undefined
  reinforcementOpen.value = false
})

function selectWrong(id: number) {
  activeWrongId.value = id
}

function toggleSelectMenu(menu: Exclude<typeof openSelectMenu.value, null>) {
  openSelectMenu.value = openSelectMenu.value === menu ? null : menu
}

function selectKnowledgeFilter(value: string) {
  knowledgeFilter.value = value
  openSelectMenu.value = null
}

function selectReinforcementCount(value: number) {
  reinforcementCount.value = value
  openSelectMenu.value = null
}

function selectReinforcementDifficulty(value: '保持难度' | '逐步提升') {
  reinforcementDifficulty.value = value
  openSelectMenu.value = null
}

function startRetake() {
  if (!wrong.value) return
  retakeMode.value = true
  retakeAnswer.value = exercise.value?.type === '代码题' && exercise.value.selectedLanguage
    ? exercise.value.codeDrafts?.[exercise.value.selectedLanguage] ?? ''
    : ''
  retakeResult.value = undefined
}

function selectRetakeAnswer(answer: string) {
  retakeAnswer.value = answer
  if (exercise.value?.type === '代码题') {
    learningStore.saveExerciseDraft(plan.value.id, exercise.value.id, answer, true)
  }
}

function selectRetakeLanguage(language: CodeLanguageKey) {
  if (!exercise.value) return
  retakeAnswer.value = learningStore.selectExerciseLanguage(plan.value.id, exercise.value.id, language, true) ?? ''
}

async function submitRetake() {
  if (!wrong.value || !retakeAnswer.value || operationPending.value) return
  operationPending.value = true
  actionError.value = ''
  try {
    retakeResult.value = await learningStore.reviewWrongQuestion(plan.value.id, wrong.value.id, retakeAnswer.value)
    retakeMode.value = false
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '提交答案失败'
  } finally {
    operationPending.value = false
  }
}

async function createReinforcement() {
  if (!wrong.value || operationPending.value) return
  operationPending.value = true
  actionError.value = ''
  try {
    const set = await learningStore.createWrongReviewSet(plan.value.id, [wrong.value.id], {
      count: reinforcementCount.value,
      difficultyMode: reinforcementDifficulty.value,
    })
    if (!set) return
    reinforcementOpen.value = false
    activeModule.value = 'sets'
    openSet(set.id)
    learningStore.startWrongReviewSet(plan.value.id, set.id)
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '巩固题组生成失败'
  } finally {
    operationPending.value = false
  }
}

function openSet(setId: number) {
  activeSetId.value = setId
  const set = reviewSets.value.find((item) => item.id === setId)
  currentSetExerciseId.value = set?.exerciseIds[0]
  setAnswer.value = ''
  setResult.value = undefined
}

function startSet() {
  if (!activeSet.value) return
  learningStore.startWrongReviewSet(plan.value.id, activeSet.value.id)
  currentSetExerciseId.value = activeSet.value.exerciseIds[0]
  setAnswer.value = ''
  setResult.value = undefined
}

function selectSetAnswer(answer: string) {
  if (!setExercise.value || activeSet.value?.status === '已完成') return
  setAnswer.value = answer
  learningStore.saveExerciseDraft(plan.value.id, setExercise.value.id, answer)
}

function selectSetLanguage(language: CodeLanguageKey) {
  if (!setExercise.value) return
  setAnswer.value = learningStore.selectExerciseLanguage(plan.value.id, setExercise.value.id, language) ?? ''
}

function moveSetExercise(offset: -1 | 1) {
  const target = setExercises.value[setExerciseIndex.value + offset]
  if (!target) return
  currentSetExerciseId.value = target.id
  setAnswer.value = target.draftAnswer ?? target.userAnswer ?? ''
}

async function submitSet() {
  if (!activeSet.value || operationPending.value) return
  operationPending.value = true
  actionError.value = ''
  try {
    setResult.value = await learningStore.submitWrongReviewSet(plan.value.id, activeSet.value.id)
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '提交巩固题组失败'
  } finally {
    operationPending.value = false
  }
}

watch(setExercise, (item) => { setAnswer.value = item?.draftAnswer ?? item?.userAnswer ?? '' })
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
    <div v-if="hasPlan && !isLoading && !loadError" class="mistakes-page" @click="openSelectMenu = null">
      <header class="page-head">
        <button class="back-button" type="button" aria-label="返回学习项目" @click="router.push(`/learning/${plan.id}`)">
          <AppIcon name="chevron-left" :size="18" />
        </button>
        <div>
          <h1>错题本</h1>
          <p>统一交卷后答错的题目会自动收录，巩固结果只更新错题掌握状态。</p>
        </div>
      </header>
      <div v-if="actionError" class="mistakes-error" role="alert">
        <span>{{ actionError }}</span>
        <button type="button" @click="actionError = ''">关闭</button>
      </div>
      <nav class="module-tabs">
        <button type="button" :class="{ active: activeModule === 'records' }" @click="activeModule = 'records'">错题记录 <span>{{ plan.wrongQuestions.length }}</span></button>
        <button type="button" :class="{ active: activeModule === 'sets' }" @click="activeModule = 'sets'">巩固题组 <span>{{ reviewSets.length }}</span></button>
      </nav>

      <main v-if="activeModule === 'records' && plan.wrongQuestions.length" class="mistakes-content">
        <section class="mistake-layout">
          <aside class="wrong-index">
            <header><strong>错题索引</strong><span>{{ filteredWrongs.length }} 道</span></header>
            <div class="index-filters">
              <label class="search-box"><AppIcon name="search" :size="16" /><input v-model="searchText" placeholder="搜索题目或知识点" /></label>
              <div class="select-menu select-menu--wide" @click.stop>
                <button
                  class="select-trigger"
                  type="button"
                  aria-label="知识点筛选"
                  :aria-expanded="openSelectMenu === 'knowledge'"
                  @click="toggleSelectMenu('knowledge')"
                >
                  <span>{{ knowledgeFilter }}</span>
                  <AppIcon :name="openSelectMenu === 'knowledge' ? 'chevron-up' : 'chevron-down'" :size="15" />
                </button>
                <div v-if="openSelectMenu === 'knowledge'" class="select-panel ui-menu-panel" role="listbox">
                  <button
                    v-for="item in knowledgeOptions"
                    :key="item"
                    class="ui-menu-item select-option"
                    :class="{ 'select-option--selected': knowledgeFilter === item }"
                    type="button"
                    role="option"
                    :aria-selected="knowledgeFilter === item"
                    @click="selectKnowledgeFilter(item)"
                  >
                    <span>{{ item }}</span>
                  </button>
                </div>
              </div>
              <div class="status-filters">
                <button type="button" :class="{ active: statusFilter === '全部' }" @click="statusFilter = '全部'">全部 {{ plan.wrongQuestions.length }}</button>
                <button type="button" :class="{ active: statusFilter === '需巩固' }" @click="statusFilter = '需巩固'">需巩固 {{ statusCounts.strengthening }}</button>
                <button type="button" :class="{ active: statusFilter === '已掌握' }" @click="statusFilter = '已掌握'">已掌握 {{ statusCounts.mastered }}</button>
              </div>
            </div>
            <button v-for="item in filteredWrongs" :key="item.id" class="wrong-item" :class="{ active: item.id === wrong?.id }" type="button" @click="selectWrong(item.id)">
              <span class="status-dot" :class="`status-${item.status}`" />
              <div><strong>{{ item.title }}</strong><small>{{ item.knowledge.join(' / ') }}</small></div>
              <footer><span>{{ item.status ?? '需巩固' }}<em v-if="(item.errorCount ?? 1) >= 2">反复出错</em></span><small>错 {{ item.errorCount ?? 1 }} 次 · {{ item.lastWrongAt ?? '刚刚' }}</small></footer>
            </button>
          </aside>

          <article v-if="wrong && exercise" class="wrong-detail">
            <header class="detail-head">
              <div><span>{{ exercise.knowledge }}</span><span>{{ exercise.difficulty }}</span><span>{{ exercise.type }}</span></div>
              <div class="detail-status"><em v-if="(wrong.errorCount ?? 1) >= 2">反复出错</em><strong :class="`status-label status-${wrong.status}`">{{ wrong.status ?? '需巩固' }}</strong></div>
            </header>
            <section v-if="!retakeMode" class="question-section">
              <small>原题</small>
              <h2>{{ exercise.title }}</h2>
              <pre v-if="exercise.code"><code>{{ exercise.code }}</code></pre>
              <pre v-else-if="exercise.type === '代码题' && exercise.starterCode"><code>{{ exercise.starterCode }}</code></pre>
              <div v-if="exercise.options.length" class="option-list">
                <label v-for="option in exercise.options" :key="option" :class="{ selected: retakeAnswer === option, wrong: !retakeMode && option === wrong.userAnswer, right: !retakeMode && option === exercise.answer }">
                  <input v-if="retakeMode" v-model="retakeAnswer" type="radio" name="retake-answer" :value="option" />
                  <span>{{ option }}</span>
                </label>
              </div>
            </section>
            <section v-else class="retake-card-wrap">
              <LearningQuestionCard
                :exercise="exercise"
                :index="0"
                :total="1"
                :model-value="retakeAnswer"
                scene="practice"
                :answered-count="retakeAnswer ? 1 : 0"
                @update:model-value="selectRetakeAnswer"
                @update-language="selectRetakeLanguage"
                @submit-group="submitRetake"
              />
              <button class="outline-btn" type="button" @click="retakeMode = false">取消重新作答</button>
            </section>

            <template v-if="!retakeMode">
              <section v-if="retakeResult" class="retake-feedback" :class="{ correct: retakeResult.correct }">
                <strong>{{ retakeResult.correct ? '本次回答正确' : '本次仍未答对' }}</strong>
                <p>{{ retakeResult.correct ? (wrong.status === '已掌握' ? '已连续正确两次，自动标记为已掌握。' : '已正确一次，再正确一次即可标记为已掌握。') : '连续正确次数已重置，建议重新阅读错因后再练习。' }}</p>
              </section>
              <div class="answer-comparison">
                <section class="answer-wrong"><small>当时的答案</small><pre v-if="exercise.type === '代码题'"><code>{{ wrong.userAnswer }}</code></pre><strong v-else>{{ wrong.userAnswer }}</strong></section>
                <section class="answer-right"><small>{{ exercise.type === '简答题' || exercise.type === '代码题' ? '参考答案' : '正确答案' }}</small><pre v-if="exercise.type === '代码题'"><code>{{ wrongReferenceAnswer }}</code></pre><strong v-else>{{ wrong.correctAnswer }}</strong></section>
              </div>
              <section class="analysis-section"><small>解题与错因</small><h3>{{ wrong.reason.split('，')[0] }}</h3><p>{{ wrong.reason }}</p><p>{{ exercise.explanation }}</p></section>
              <section class="knowledge-section"><small>关联知识点</small><div><span v-for="item in wrong.knowledge" :key="item">{{ item }}</span></div></section>
              <section v-if="wrong.reviewHistory?.length" class="history-section"><small>复习记录</small><div v-for="(item, index) in wrong.reviewHistory" :key="index"><span>{{ item.date }}</span><strong :class="{ correct: item.correct }">{{ item.correct ? '回答正确' : '回答错误' }}</strong></div></section>
              <footer class="detail-actions">
                <button class="primary-btn" type="button" @click="startRetake">重新作答</button>
                <button class="outline-btn" type="button" @click="reinforcementOpen = !reinforcementOpen">生成巩固题</button>
              </footer>
              <section v-if="reinforcementOpen" class="reinforcement-config">
                <div><strong>生成当前错题的变式练习</strong><small>不复制原题，改变场景和表达方式后直接开始作答。</small></div>
                <div class="reinforcement-field">
                  <span>题量</span>
                  <div class="select-menu" @click.stop>
                    <button class="select-trigger" type="button" :aria-expanded="openSelectMenu === 'count'" @click="toggleSelectMenu('count')">
                      <span>{{ reinforcementCount }} 题</span>
                      <AppIcon :name="openSelectMenu === 'count' ? 'chevron-up' : 'chevron-down'" :size="15" />
                    </button>
                    <div v-if="openSelectMenu === 'count'" class="select-panel ui-menu-panel" role="listbox">
                      <button
                        v-for="count in reinforcementCountOptions"
                        :key="count"
                        class="ui-menu-item select-option"
                        :class="{ 'select-option--selected': reinforcementCount === count }"
                        type="button"
                        role="option"
                        :aria-selected="reinforcementCount === count"
                        @click="selectReinforcementCount(count)"
                      >
                        <span>{{ count }} 题</span>
                      </button>
                    </div>
                  </div>
                </div>
                <div class="reinforcement-field">
                  <span>难度</span>
                  <div class="select-menu" @click.stop>
                    <button class="select-trigger" type="button" :aria-expanded="openSelectMenu === 'difficulty'" @click="toggleSelectMenu('difficulty')">
                      <span>{{ reinforcementDifficulty }}</span>
                      <AppIcon :name="openSelectMenu === 'difficulty' ? 'chevron-up' : 'chevron-down'" :size="15" />
                    </button>
                    <div v-if="openSelectMenu === 'difficulty'" class="select-panel ui-menu-panel" role="listbox">
                      <button
                        v-for="difficulty in reinforcementDifficultyOptions"
                        :key="difficulty"
                        class="ui-menu-item select-option"
                        :class="{ 'select-option--selected': reinforcementDifficulty === difficulty }"
                        type="button"
                        role="option"
                        :aria-selected="reinforcementDifficulty === difficulty"
                        @click="selectReinforcementDifficulty(difficulty)"
                      >
                        <span>{{ difficulty }}</span>
                      </button>
                    </div>
                  </div>
                </div>
                <button class="primary-btn" type="button" :disabled="operationPending" @click="createReinforcement">
                  {{ operationPending ? '生成中…' : '生成并开始' }}
                </button>
              </section>
            </template>
          </article>
          <section v-else class="wrong-detail filtered-detail-empty">
            <h2>当前筛选条件下没有错题</h2>
            <p>可以更换知识点、状态或搜索关键词。</p>
          </section>
        </section>
      </main>

      <main v-else-if="activeModule === 'records'" class="empty-state">
        <span><AppIcon name="check-circle" :size="34" /></span>
        <h2>暂时没有错题</h2>
        <p>完成学习路径中的练习或测验并统一交卷后，答错的题目会自动收录在这里。系统会继续记录错因、关联知识点和后续复习表现。</p>
        <button class="primary-btn" type="button" @click="router.push(`/learning/${plan.id}/study`)">继续学习</button>
      </main>

      <main v-else class="sets-content">
        <section v-if="reviewSets.length" class="mistake-layout">
          <aside class="wrong-index set-index">
            <header><strong>巩固题组</strong><span>{{ reviewSets.length }} 组</span></header>
            <button v-for="set in reviewSets" :key="set.id" class="wrong-item set-item" :class="{ active: set.id === activeSet?.id }" type="button" @click="openSet(set.id)">
              <span class="status-dot" :class="{ mastered: set.status === '已完成' }" />
              <div><strong>{{ set.title }}</strong><small>来源：{{ set.sourceWrongIds.length }} 道错题 · {{ set.difficultyMode }}</small></div>
              <footer><span>{{ set.status }}</span><small>{{ set.createdAt }}</small></footer>
            </button>
          </aside>
          <article v-if="activeSet" class="wrong-detail set-detail">
            <header class="set-head"><div><small>错题本巩固题组</small><h2>{{ activeSet.title }}</h2><p>完成结果只更新对应错题的复习状态，不计入学习路径进度。</p></div><strong>{{ activeSet.status }}</strong></header>
            <section v-if="activeSet.status === '待作答'" class="set-start"><span>{{ setExercises.length }} 题</span><p>题目基于 {{ activeSet.sourceWrongIds.length }} 道错题生成，会改变场景和提问方式。</p><button class="primary-btn" type="button" @click="startSet">开始作答</button></section>
            <template v-else-if="activeSet.status === '作答中' && setExercise">
              <div class="set-answer-grid"><button v-for="(item, index) in setExercises" :key="item.id" type="button" :class="{ active: item.id === setExercise.id, answered: item.draftAnswer }" @click="currentSetExerciseId = item.id">{{ index + 1 }}</button></div>
              <LearningQuestionCard :exercise="setExercise" :index="setExerciseIndex" :total="setExercises.length" scene="practice" :model-value="setAnswer" :answered-count="setAnsweredCount" @update:model-value="selectSetAnswer" @update-language="selectSetLanguage" @previous="moveSetExercise(-1)" @next="moveSetExercise(1)" @submit-group="submitSet" />
            </template>
            <section v-else class="set-result"><strong>{{ setResult?.correctRate ?? activeSet.correctRate ?? 0 }}%</strong><h3>本组已完成</h3><p>复习结果已记录到来源错题。</p><button class="outline-btn" type="button" @click="startSet">再做一次</button></section>
          </article>
        </section>
        <section v-else class="empty-state set-empty"><span><AppIcon name="edit" :size="32" /></span><h2>还没有巩固题组</h2><p>在错题详情中选择“生成巩固题”，题组会保存在这里，不会加入学习路径。</p><button class="outline-btn" type="button" @click="activeModule = 'records'">返回错题记录</button></section>
      </main>
    </div>
  </StudentShell>
</template>

<style scoped>
.mistakes-error{max-width:1480px;min-height:38px;margin:14px auto 0;padding:8px 10px;display:flex;align-items:center;justify-content:space-between;gap:10px;border:1px solid color-mix(in srgb,var(--color-danger) 35%,var(--color-border));border-radius:8px;background:var(--color-surface);color:var(--color-danger);font-size:13px}.mistakes-error button{border:0;background:transparent;color:inherit;cursor:pointer}
.mistakes-page{min-height:100%;padding:28px 34px 48px;background:var(--color-bg);box-sizing:border-box}.mistakes-page *{box-sizing:border-box}h1,h2,h3,p{margin:0}button,input,select{font:inherit}.page-head,.mistakes-content,.empty-state{max-width:1480px;margin:0 auto}.page-head{display:grid;grid-template-columns:42px 1fr auto;align-items:center;gap:16px}.back-button{width:42px;height:42px;border:1px solid var(--color-border);border-radius:10px;background:var(--color-surface);cursor:pointer}.page-head h1{color:var(--color-text);font-size:30px}.page-head p{margin-top:6px;color:var(--color-text-muted)}.primary-btn,.outline-btn{min-height:40px;border-radius:9px;padding:0 18px;font-weight:750;cursor:pointer}.primary-btn{border:1px solid var(--color-primary);background:var(--color-primary);color:var(--color-on-primary)}.outline-btn{border:1px solid var(--color-border);background:var(--color-surface);color:var(--color-text)}button:disabled{opacity:.45;cursor:not-allowed}.mistakes-content{margin-top:22px}.mistake-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:12px}.filter-group{display:flex;gap:8px;flex-wrap:wrap}.filter-group select,.search-box{height:38px;border:1px solid var(--color-border);border-radius:9px;background:var(--color-surface);color:var(--color-text)}.filter-group select{padding:0 30px 0 10px}.search-box{display:flex;align-items:center;gap:7px;padding:0 10px}.search-box input{width:210px;border:0;outline:0;background:transparent;color:var(--color-text)}.mistake-toolbar>p{color:var(--color-text-muted);font-size:13px}.mistake-layout{display:grid;grid-template-columns:340px minmax(0,1fr);gap:14px;align-items:start}.wrong-index,.wrong-detail,.filtered-empty,.empty-state{border:1px solid var(--color-border);border-radius:12px;background:var(--color-surface);box-shadow:var(--shadow-sm)}.wrong-index{max-height:calc(100vh - 190px);overflow:auto;padding:10px}.wrong-index>header{position:sticky;top:-10px;z-index:2;display:flex;justify-content:space-between;padding:10px 8px 12px;background:var(--color-surface);color:var(--color-text)}.wrong-index>header span{color:var(--color-text-muted)}.wrong-item{width:100%;display:grid;grid-template-columns:8px 1fr;gap:9px;padding:12px 9px;border:0;border-top:1px solid var(--color-border);background:transparent;text-align:left;cursor:pointer}.wrong-item.active{border-radius:9px;background:var(--color-hover)}.wrong-item>div{display:grid;gap:5px;min-width:0}.wrong-item strong{overflow:hidden;color:var(--color-text);font-size:14px;text-overflow:ellipsis;white-space:nowrap}.wrong-item small{color:var(--color-text-muted);font-size:12px}.wrong-item footer{grid-column:2;display:flex;justify-content:space-between;gap:8px}.wrong-item footer>span{color:var(--color-text-muted);font-size:12px;font-weight:700}.status-dot{width:7px;height:7px;margin-top:5px;border-radius:50%;background:#f59e0b}.status-dot.status-已掌握{background:#22c55e}.status-dot.status-复习中{background:#3b82f6}.status-dot.status-反复出错{background:#ef4444}.wrong-detail{min-height:680px;padding:26px 30px}.detail-head{display:flex;align-items:center;justify-content:space-between}.detail-head>div{display:flex;gap:8px}.detail-head span,.knowledge-section span{border-radius:999px;padding:5px 10px;background:var(--color-hover);color:var(--color-text-muted);font-size:12px}.status-label{font-size:13px;color:#d97706}.status-label.status-已掌握{color:var(--color-success)}.status-label.status-反复出错{color:var(--color-danger)}.question-section{margin-top:24px}.question-section>small,.analysis-section>small,.knowledge-section>small,.history-section>small{color:var(--color-text-muted);font-weight:700}.question-section h2{max-width:920px;margin-top:8px;color:var(--color-text);font-size:22px;line-height:1.55}.question-section pre{margin:16px 0;padding:16px;border-radius:9px;background:var(--color-hover);overflow:auto}.option-list{display:grid;gap:9px;margin-top:18px}.option-list label{display:flex;align-items:center;gap:10px;min-height:46px;padding:10px 13px;border:1px solid var(--color-border);border-radius:9px;color:var(--color-text)}.option-list label.selected{border-color:var(--color-primary);background:color-mix(in srgb,#2563eb 11%,var(--color-surface))}.option-list label.wrong{border-color:#fecaca;background:color-mix(in srgb,#dc2626 9%,var(--color-surface));color:var(--color-danger)}.option-list label.right{border-color:#bbf7d0;background:color-mix(in srgb,#16a34a 9%,var(--color-surface));color:var(--color-success)}.answer-comparison{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:20px}.answer-comparison section{display:grid;gap:7px;padding:14px;border-radius:9px}.answer-wrong{border:1px solid #fecaca;background:color-mix(in srgb,#dc2626 9%,var(--color-surface));color:var(--color-danger)}.answer-right{border:1px solid #bbf7d0;background:color-mix(in srgb,#16a34a 9%,var(--color-surface));color:var(--color-success)}.analysis-section,.knowledge-section,.history-section{margin-top:18px;padding-top:18px;border-top:1px solid var(--color-border)}.analysis-section h3{margin-top:9px;color:var(--color-text);font-size:18px}.analysis-section p{margin-top:8px;color:var(--color-text-muted);line-height:1.75}.knowledge-section div{display:flex;gap:8px;margin-top:10px}.history-section>div{display:flex;justify-content:space-between;padding:9px 0;border-bottom:1px solid var(--color-border);color:var(--color-text-muted)}.history-section strong{color:var(--color-danger)}.history-section strong.correct{color:var(--color-success)}.detail-actions,.retake-actions{display:flex;gap:10px;margin-top:22px}.retake-actions{justify-content:flex-end}.retake-feedback{margin-top:18px;padding:13px;border:1px solid #fecaca;border-radius:9px;background:color-mix(in srgb,#dc2626 9%,var(--color-surface));color:var(--color-danger)}.retake-feedback.correct{border-color:#bbf7d0;background:color-mix(in srgb,#16a34a 9%,var(--color-surface));color:var(--color-success)}.retake-feedback p{margin-top:5px}.reinforcement-config{display:flex;align-items:end;gap:10px;flex-wrap:wrap;margin-top:14px;padding:15px;border-radius:9px;background:var(--color-hover)}.reinforcement-config>div{display:grid;gap:4px;min-width:280px;margin-right:auto;color:var(--color-text)}.reinforcement-config small{color:var(--color-text-muted)}.reinforcement-config label{display:grid;gap:4px;color:var(--color-text-muted);font-size:12px}.reinforcement-config select{height:38px;border:1px solid var(--color-border);border-radius:8px;background:var(--color-surface);padding:0 9px}.filtered-empty,.empty-state{display:grid;justify-items:center;text-align:center;padding:70px 24px}.filtered-empty p,.empty-state p{max-width:620px;margin:10px 0 20px;color:var(--color-text-muted);line-height:1.7}.empty-state{margin-top:28px;min-height:520px;align-content:center}.empty-state>span{display:grid;place-items:center;width:66px;height:66px;margin-bottom:16px;border-radius:50%;background:color-mix(in srgb,#16a34a 11%,var(--color-surface));color:var(--color-success)}.empty-state h2,.filtered-empty h2{color:var(--color-text)}
.page-head,.module-tabs,.mistakes-content,.sets-content,.empty-state{max-width:1480px;margin-left:auto;margin-right:auto}.page-head{grid-template-columns:42px 1fr}.module-tabs{display:flex;gap:24px;margin-top:20px;border-bottom:1px solid var(--color-border)}.module-tabs button{display:flex;align-items:center;gap:7px;padding:0 2px 12px;border:0;border-bottom:2px solid transparent;background:transparent;color:var(--color-text-muted);font-weight:750;cursor:pointer}.module-tabs button.active{border-bottom-color:var(--color-primary);color:var(--color-text)}.module-tabs span{display:grid;place-items:center;min-width:21px;height:21px;border-radius:999px;background:var(--color-hover);font-size:12px}.mistakes-content,.sets-content{margin-top:14px}.index-filters{display:grid;gap:8px;padding:0 2px 12px}.index-filters .search-box{width:100%;background:var(--color-bg)}.index-filters .search-box input{width:100%}.index-filters>select{width:100%;height:38px;padding:0 10px;border:1px solid var(--color-border);border-radius:9px;background:var(--color-bg);color:var(--color-text)}.status-filters{display:flex;flex-wrap:wrap;gap:6px}.status-filters button{min-height:28px;padding:0 9px;border:1px solid var(--color-border);border-radius:999px;background:transparent;color:var(--color-text-muted);font-size:11px;cursor:pointer}.status-filters button.active{border-color:#cbd5e1;background:var(--color-hover);color:var(--color-text);font-weight:750}.wrong-index{max-height:calc(100vh - 180px)}.wrong-item footer>span{display:flex;align-items:center;gap:6px}.wrong-item footer em,.detail-status em{border-radius:999px;padding:2px 6px;background:color-mix(in srgb,#dc2626 9%,var(--color-surface));color:var(--color-danger);font-size:10px;font-style:normal;font-weight:750}.detail-status{display:flex;align-items:center;gap:8px}.status-dot.status-已掌握{background:#22c55e}.status-label.status-已掌握{color:var(--color-success)}.filtered-detail-empty{display:grid;align-content:center;justify-items:center;min-height:680px;text-align:center}.filtered-detail-empty h2{color:var(--color-text);font-size:20px}.filtered-detail-empty p{margin-top:10px;color:var(--color-text-muted)}.sets-content .wrong-detail{min-height:620px}.status-dot.mastered{background:#22c55e}.set-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.set-head small,.set-head p{color:var(--color-text-muted)}.set-head h2{margin:5px 0;color:var(--color-text)}.set-head>strong{color:#d97706}.set-start,.set-result{display:grid;justify-items:center;align-content:center;min-height:450px;text-align:center}.set-start>span,.set-result>strong{font-size:46px;font-weight:800;color:var(--color-text)}.set-start p,.set-result p{max-width:540px;margin:10px 0 20px;color:var(--color-text-muted)}.set-answer-grid{display:flex;flex-wrap:wrap;gap:7px;margin:22px 0 12px}.set-answer-grid button{width:34px;height:34px;border:1px solid var(--color-border);border-radius:8px;background:var(--color-surface);color:var(--color-text);cursor:pointer}.set-answer-grid button.answered{background:color-mix(in srgb,#16a34a 11%,var(--color-surface));color:var(--color-success)}.set-answer-grid button.active{border-color:var(--color-primary);box-shadow:0 0 0 2px #dbeafe}.set-detail .question-card{margin-top:8px}.set-empty{margin-top:14px}@media(max-width:1000px){.mistake-layout{grid-template-columns:1fr}.wrong-index{max-height:320px}.page-head{grid-template-columns:42px 1fr}.answer-comparison{grid-template-columns:1fr}}@media(max-width:640px){.mistakes-page{padding:20px 14px}.wrong-detail{padding:20px 16px}.search-box{width:100%}.search-box input{width:100%}.page-head{align-items:start}}
.retake-card-wrap{display:grid;gap:10px;margin-top:20px}.retake-card-wrap>.outline-btn{justify-self:end}.answer-comparison pre{max-height:240px;margin:0;padding:10px;border-radius:7px;background:var(--color-surface);overflow:auto;white-space:pre-wrap}.answer-comparison code{font-family:"Cascadia Code",Consolas,monospace;font-size:12px}

.back-button {
  border-radius: var(--ui-hover-radius);
}

.back-button:hover {
  background: var(--ui-hover-strong-bg);
}

.outline-btn {
  border-radius: var(--ui-hover-radius);
}

.outline-btn:hover,
.wrong-item:hover,
.set-answer-grid button:hover {
  background: var(--ui-hover-bg);
}

.wrong-item {
  border-radius: var(--ui-hover-radius);
}

.wrong-item.active,
.set-answer-grid button.active {
  background: var(--ui-hover-strong-bg);
}

.select-menu {
  position: relative;
  min-width: 112px;
}

.select-menu--wide {
  width: 100%;
}

.select-trigger {
  width: 100%;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: var(--color-bg);
  color: var(--color-text);
  cursor: pointer;
  text-align: left;
}

.select-trigger span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.select-trigger:hover,
.select-trigger[aria-expanded="true"] {
  border-color: var(--color-border-strong);
  background: var(--ui-hover-bg);
}

.select-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 120;
  width: 100%;
  min-width: max-content;
  max-height: 280px;
  overflow-y: auto;
}

.select-option.ui-menu-item {
  width: 100%;
  min-width: 100%;
  justify-content: flex-start;
  text-align: left;
}

.select-option--selected.ui-menu-item {
  background: var(--ui-hover-bg);
  color: var(--color-text);
  font-weight: 600;
}

.reinforcement-config .select-trigger {
  background: var(--color-surface);
}

.reinforcement-field {
  display: grid;
  gap: 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}
</style>
