<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import LearningProjectResourceChips from '@/components/learning/LearningProjectResourceChips.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import { useLearningStore } from '@/stores/learning'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'

type ViewMode = 'grid' | 'list'

const router = useRouter()
const learningStore = useLearningStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const learningPlans = computed(() => learningStore.plans)
const keyword = ref('')
const status = ref('全部状态')
const statusMenuOpen = ref(false)
const viewMode = ref<ViewMode>(localStorage.getItem('examinsight.ui.learning-project-view') === 'list' ? 'list' : 'grid')
const statusOptions = ['全部状态', '待开启', '进行中', '已生成', '已完成', '待完善']

const filteredPlans = computed(() =>
  learningPlans.value.filter((plan) => {
    const hitKeyword = !keyword.value || plan.title.includes(keyword.value)
    const hitStatus = status.value === '全部状态' || plan.status === status.value
    return hitKeyword && hitStatus
  }),
)
const averageCorrectRate = computed(() => learningPlans.value.length
  ? Math.round(learningPlans.value.reduce((sum, plan) => sum + plan.correctRate, 0) / learningPlans.value.length)
  : 0)
const completedTaskCount = computed(() => learningPlans.value.reduce((sum, plan) => sum + plan.taskDone, 0))

function knowledgeBaseName(id: number | null) {
  if (id === null) return '未关联知识库'
  const plan = learningPlans.value.find((item) => item.knowledgeBaseId === id)
  return plan?.profile.find((item) => item.label === '资料来源')?.value
    ?? knowledgeBaseStore.list.find((item) => item.id === id)?.name
    ?? '未关联知识库'
}

function selectStatus(value: string) {
  status.value = value
  statusMenuOpen.value = false
}

function statusIcon(value: string) {
  const iconMap: Record<string, string> = {
    全部状态: 'list-filter',
    待开启: 'edit',
    进行中: 'play',
    已生成: 'file',
    已完成: 'check',
    待完善: 'clock',
  }
  return iconMap[value] ?? 'list-filter'
}

function metaText(plan: { period: string; knowledgeBaseId: number | null; targetType: string }) {
  return `${plan.period}｜${knowledgeBaseName(plan.knowledgeBaseId)}｜${plan.targetType}`
}

function primaryAction(plan: { id: number; status: string }) {
  if (plan.status === '待开启' || plan.status === '待完善') router.push(`/learning/new?projectId=${plan.id}`)
  else router.push(`/learning/${plan.id}/study`)
}

async function loadPlans() {
  try {
    await Promise.all([learningStore.fetchPlans(), knowledgeBaseStore.fetchList()])
  } catch {
    // Store error is rendered by this page.
  }
}

watch(viewMode, (mode) => localStorage.setItem('examinsight.ui.learning-project-view', mode))
onMounted(() => void loadPlans())
</script>

<template>
  <StudentShell>
    <div class="projects-page" @click="statusMenuOpen = false">
      <header class="page-head">
        <div>
          <h1>学习项目</h1>
          <p>管理你创建的个性化学习项目，继续学习或查看资源与进度。</p>
        </div>
        <div class="head-actions">
          <label class="search">
            <AppIcon name="search" :size="18" />
            <input v-model="keyword" placeholder="搜索项目" />
          </label>
          <div class="status-menu-wrap" @click.stop>
            <button class="status-trigger" type="button" @click="statusMenuOpen = !statusMenuOpen">
              <AppIcon :name="statusIcon(status)" :size="17" />
              {{ status }}
              <AppIcon name="chevron-down" :size="14" />
            </button>
            <div v-if="statusMenuOpen" class="status-menu ui-menu-panel">
              <button
                v-for="option in statusOptions"
                :key="option"
                class="ui-menu-item"
                type="button"
                :aria-selected="status === option"
                @click="selectStatus(option)"
              >
                <span class="ui-menu-icon"><AppIcon :name="statusIcon(option)" :size="16" /></span>
                {{ option }}
              </button>
            </div>
          </div>
          <div class="view-switch" aria-label="切换展示方式">
            <button
              class="ui-icon-action"
              type="button"
              :class="{ active: viewMode === 'grid' }"
              title="网格展示"
              @click="viewMode = 'grid'"
            >
              <AppIcon name="grid" :size="17" />
            </button>
            <button
              class="ui-icon-action"
              type="button"
              :class="{ active: viewMode === 'list' }"
              title="列表展示"
              @click="viewMode = 'list'"
            >
              <AppIcon name="list" :size="17" />
            </button>
          </div>
          <button type="button" @click="router.push('/learning/new')">新建学习</button>
        </div>
      </header>

      <section class="projects-view">
        <div class="summary-grid">
          <article>
            <span class="summary-icon summary-icon--blue"><AppIcon name="file" :size="28" /></span>
            <div><small>项目总数</small><strong>{{ learningPlans.length }}</strong></div>
          </article>
          <article>
            <span class="summary-icon summary-icon--green"><AppIcon name="play" :size="28" /></span>
            <div><small>进行中</small><strong>{{ learningPlans.filter((item) => item.status === '进行中').length }}</strong></div>
          </article>
          <article>
            <span class="summary-icon summary-icon--purple"><AppIcon name="clock" :size="28" /></span>
            <div><small>已完成任务</small><strong>{{ completedTaskCount }}</strong></div>
          </article>
          <article>
            <span class="summary-icon summary-icon--orange"><AppIcon name="bar-chart" :size="28" /></span>
            <div><small>平均正确率</small><strong>{{ averageCorrectRate }}%</strong></div>
          </article>
        </div>

        <section v-if="learningStore.errorMessage" class="project-state project-state--error" role="alert">
          <strong>学习项目加载失败</strong>
          <span>{{ learningStore.errorMessage }}</span>
          <button type="button" @click="loadPlans">重试</button>
        </section>
        <section v-else-if="learningStore.isLoading" class="project-state" aria-live="polite">
          <strong>正在加载学习项目…</strong>
        </section>
        <section v-else-if="!filteredPlans.length" class="project-state">
          <strong>{{ keyword || status !== '全部状态' ? '没有匹配的学习项目' : '还没有学习项目' }}</strong>
          <span>{{ keyword || status !== '全部状态' ? '请调整搜索词或状态筛选' : '从新建学习开始制定第一份学习方案' }}</span>
          <button v-if="!keyword && status === '全部状态'" type="button" @click="router.push('/learning/new')">新建学习</button>
        </section>

        <div v-else class="project-list" :class="`project-list--${viewMode}`">
          <article v-for="plan in filteredPlans" :key="plan.id" class="project-card">
            <header class="card-header">
              <h2 :title="plan.title">{{ plan.title }}</h2>
              <span :class="`status status--${plan.status}`">{{ plan.status }}</span>
            </header>
            <p class="meta" :title="metaText(plan)">{{ metaText(plan) }}</p>

            <div class="progress-row">
              <span>已完成 {{ plan.progress }}%</span>
              <i><b :style="{ width: `${plan.progress}%` }" /></i>
            </div>

            <div class="stats" :title="`任务 ${plan.taskDone}/${plan.totalTasks}，练习 ${plan.exerciseDone}/${plan.totalExercises}，正确率 ${plan.correctRate}%`">
              <div><small>任务</small><strong>{{ plan.taskDone }}/{{ plan.totalTasks }}</strong></div>
              <div><small>练习</small><strong>{{ plan.exerciseDone }}/{{ plan.totalExercises }}</strong></div>
              <div><small>正确率</small><strong>{{ plan.correctRate }}%</strong></div>
            </div>

            <LearningProjectResourceChips v-if="plan.resources.length" :resources="plan.resources" />

            <footer>
              <button class="primary-btn" type="button" @click="primaryAction(plan)">
                {{ plan.status === '待开启' ? '开启对话' : plan.status === '已生成' ? '开始学习' : plan.status === '待完善' ? '继续配置' : plan.status === '已完成' ? '回顾学习' : '继续学习' }}
              </button>
              <button class="outline-btn" type="button" @click="router.push(`/learning/${plan.id}`)">
                {{ plan.status === '已生成' ? '查看详情' : '查看工作台' }}
              </button>
            </footer>
          </article>
        </div>
      </section>
    </div>
  </StudentShell>
</template>

<style scoped>
.projects-page {
  min-height: 100%;
  padding: 34px 36px 56px;
  background: var(--color-bg);
}

.projects-page,
.projects-page * {
  box-sizing: border-box;
}

h1,
h2,
p {
  margin: 0;
}

button,
input {
  font: inherit;
}

.page-head,
.projects-view {
  max-width: 1320px;
  margin-left: auto;
  margin-right: auto;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

h1 {
  color: var(--color-text);
  font-size: 32px;
  font-weight: 800;
}

.page-head p {
  margin-top: 8px;
  color: var(--color-text-muted);
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.search,
.status-trigger {
  height: 44px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.search {
  width: 210px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  color: var(--color-text-muted);
}

.search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
}

.status-menu-wrap {
  position: relative;
}

.status-trigger {
  min-width: 142px;
  padding: 0 12px;
  cursor: pointer;
  color: var(--color-text);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-weight: 700;
}

.status-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 30;
  width: 178px;
}

.status-menu button {
  height: var(--ui-menu-item-height);
}

.view-switch {
  height: 44px;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  display: flex;
  align-items: center;
  gap: 4px;
}

.view-switch button {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
}

.view-switch button:hover,
.view-switch button.active {
  background: var(--color-hover);
  color: var(--color-text);
}

.head-actions > button,
.primary-btn {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.head-actions > button {
  height: 44px;
  border-radius: 8px;
  padding: 0 22px;
  cursor: pointer;
  font-weight: 800;
}

.summary-grid {
  margin-top: 30px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.summary-grid article,
.project-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.summary-grid article {
  min-height: 104px;
  padding: 22px;
  display: flex;
  align-items: center;
  gap: 18px;
}

.summary-icon {
  width: 56px;
  height: 56px;
  border-radius: 999px;
  display: grid;
  place-items: center;
}

.summary-icon--blue { background: color-mix(in srgb, #2563eb 11%, var(--color-surface)); color: var(--color-info); }
.summary-icon--green { background: color-mix(in srgb, #16a34a 11%, var(--color-surface)); color: var(--color-success); }
.summary-icon--purple { background: color-mix(in srgb, #7c3aed 11%, var(--color-surface)); color: #7c3aed; }
.summary-icon--orange { background: color-mix(in srgb, #f97316 10%, var(--color-surface)); color: var(--color-warning); }

.summary-grid small,
.stats small {
  display: block;
  color: var(--color-text-muted);
}

.summary-grid strong {
  display: block;
  margin-top: 4px;
  color: var(--color-text);
  font-size: 30px;
}

.project-list {
  margin-top: 26px;
  padding: 2px 2px 12px;
}

.project-state {
  min-height: 300px;
  margin-top: 26px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 9px;
  color: var(--color-text-muted);
  text-align: center;
}

.project-state strong {
  color: var(--color-text);
  font-size: 17px;
}

.project-state button {
  min-height: 36px;
  margin-top: 5px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.project-state--error span {
  color: var(--color-danger);
  overflow-wrap: anywhere;
}

.project-list--grid {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}

.project-list--list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.project-card {
  height: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.project-list--grid .project-card {
  flex: 0 0 calc((100% - 54px) / 4);
}

.project-list--list .project-card {
  width: 100%;
  height: auto;
  min-height: 128px;
  padding: 18px 20px;
  position: relative;
}

.card-header {
  min-height: 24px;
  max-height: 24px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.project-card h2 {
  flex: 1 1 auto;
  min-width: 0;
  color: var(--color-text);
  font-size: 17px;
  line-height: 1.4;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status {
  flex: 0 0 auto;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 800;
}

.status--待开启 { background: color-mix(in srgb, #7c3aed 11%, var(--color-surface)); color: #7c3aed; }
.status--进行中 { background: color-mix(in srgb, #2563eb 11%, var(--color-surface)); color: var(--color-info); }
.status--已生成,
.status--已完成 { background: color-mix(in srgb, #16a34a 11%, var(--color-surface)); color: var(--color-success); }
.status--待完善 { background: color-mix(in srgb, #f97316 10%, var(--color-surface)); color: var(--color-warning); }

.meta {
  margin-top: 14px;
  min-height: 22px;
  max-height: 22px;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 22px;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-row {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.progress-row i {
  display: block;
  height: 6px;
  border-radius: 999px;
  background: var(--color-border);
  overflow: hidden;
}

.progress-row b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-primary);
}

.stats {
  margin-top: 18px;
  display: flex;
  gap: 8px;
  text-align: center;
}

.stats > div {
  flex: 1 1 0;
  min-width: 0;
  border-right: 1px solid var(--color-border);
}

.stats > div:last-child {
  border-right: 0;
}

.stats strong {
  display: block;
  margin-top: 4px;
  color: var(--color-text);
  font-size: 18px;
}

.resource-chips {
  margin-top: 18px;
}

.project-card footer {
  margin-top: 18px;
  display: flex;
  gap: 12px;
}

.project-list--list .card-header {
  min-height: 24px;
  max-height: 24px;
  align-items: center;
  padding-right: 210px;
  justify-content: flex-start;
}

.project-list--list .project-card h2 {
  flex: 0 1 auto;
  font-size: 16px;
  line-height: 1.5;
  display: block;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.project-list--list .status {
  margin-left: 8px;
}

.project-list--list .meta {
  margin-top: 8px;
  min-height: 22px;
  max-height: 22px;
  line-height: 22px;
  display: block;
  white-space: nowrap;
  text-overflow: ellipsis;
  padding-right: 210px;
}

.project-list--list .resource-chips {
  margin-top: 12px;
  min-height: 30px;
  max-height: 30px;
  flex-wrap: nowrap;
  overflow: hidden;
  padding-right: 210px;
}

.project-list--list .progress-row,
.project-list--list .stats {
  display: none;
}

.project-list--list .project-card footer {
  position: absolute;
  right: 20px;
  top: 58px;
  width: 190px;
  margin-top: 0;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.project-list--list .primary-btn,
.project-list--list .outline-btn {
  height: 34px;
  flex: 0 0 auto;
  padding: 0 12px;
  font-size: 13px;
}

.primary-btn,
.outline-btn {
  height: 42px;
  flex: 1 1 0;
  min-width: 0;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 800;
}

.outline-btn {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
}

@media (max-width: 1280px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .project-list--grid .project-card {
    flex-basis: calc((100% - 18px) / 2);
  }
}

@media (max-width: 860px) {
  .page-head,
  .head-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .project-list--grid .project-card {
    flex-basis: 100%;
  }

}
</style>
