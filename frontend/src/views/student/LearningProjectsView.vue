<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { courseLibraries } from '@/mock'
import { useLearningStore } from '@/stores/learning'

type ViewMode = 'grid' | 'list'

const router = useRouter()
const learningStore = useLearningStore()
const learningPlans = computed(() => learningStore.plans)
const keyword = ref('')
const status = ref('全部状态')
const statusMenuOpen = ref(false)
const viewMode = ref<ViewMode>('grid')
const statusOptions = ['全部状态', '待开启', '进行中', '已生成', '已完成', '待完善']

const filteredPlans = computed(() =>
  learningPlans.value.filter((plan) => {
    const hitKeyword = !keyword.value || plan.title.includes(keyword.value)
    const hitStatus = status.value === '全部状态' || plan.status === status.value
    return hitKeyword && hitStatus
  }),
)

function libraryName(id: number) {
  return courseLibraries.find((item) => item.id === id)?.name ?? '未选择资料库'
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

function resourceIcon(group: string) {
  if (group === '思维导图') return 'mind-topic'
  if (group === '代码案例') return 'code'
  if (group === 'PPT') return 'presentation'
  return 'file'
}

function metaText(plan: { period: string; libraryId: number; targetType: string; updatedAt: string }) {
  return `${plan.period}｜${libraryName(plan.libraryId)}｜${plan.targetType}｜${plan.updatedAt}`
}

function resourceText(plan: { resources: Array<{ group: string }> }) {
  return plan.resources.map((resource) => resource.group).join('、') || '暂无资源'
}

function primaryAction(plan: { id: number; status: string }) {
  if (plan.status === '待开启') router.push(`/learning/new?projectId=${plan.id}`)
  else router.push(`/learning/${plan.id}/study`)
}
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
            <div v-if="statusMenuOpen" class="status-menu">
              <button
                v-for="option in statusOptions"
                :key="option"
                type="button"
                @click="selectStatus(option)"
              >
                <AppIcon :name="statusIcon(option)" :size="18" />
                {{ option }}
              </button>
            </div>
          </div>
          <div class="view-switch" aria-label="切换展示方式">
            <button
              type="button"
              :class="{ active: viewMode === 'grid' }"
              title="网格展示"
              @click="viewMode = 'grid'"
            >
              <AppIcon name="grid" :size="17" />
            </button>
            <button
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
            <div><small>本周学习</small><strong>6.5h</strong></div>
          </article>
          <article>
            <span class="summary-icon summary-icon--orange"><AppIcon name="bar-chart" :size="28" /></span>
            <div><small>平均正确率</small><strong>72%</strong></div>
          </article>
        </div>

        <div class="project-list" :class="`project-list--${viewMode}`">
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

            <div class="resource-chips" :title="resourceText(plan)">
              <span v-for="resource in plan.resources" :key="resource.id">
                <AppIcon :name="resourceIcon(resource.group)" :size="15" />
                {{ resource.group }}
              </span>
            </div>

            <footer>
              <button class="primary-btn" type="button" @click="primaryAction(plan)">
                {{ plan.status === '待开启' ? '开启对话' : plan.status === '已生成' ? '开始学习' : plan.status === '待完善' ? '继续配置' : plan.status === '已完成' ? '查看报告' : '继续学习' }}
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
  padding: 8px;
  border: 1px solid #dbe2ec;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 18px 46px rgba(15, 23, 42, 0.14);
}

.status-menu button {
  width: 100%;
  height: 42px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #111827;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  font-weight: 600;
  text-align: left;
}

.status-menu button:hover {
  background: #f2f4f7;
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
  background: #f2f4f7;
  color: var(--color-text);
}

.head-actions > button,
.primary-btn {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
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

.summary-icon--blue { background: #eff6ff; color: #2563eb; }
.summary-icon--green { background: #ecfdf3; color: #16a34a; }
.summary-icon--purple { background: #f5f3ff; color: #7c3aed; }
.summary-icon--orange { background: #fff7ed; color: #f97316; }

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
  height: 392px;
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
  min-height: 50px;
  max-height: 50px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.project-card h2 {
  min-width: 0;
  color: var(--color-text);
  font-size: 17px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.status {
  flex: 0 0 auto;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 800;
}

.status--待开启 { background: #f5f3ff; color: #7c3aed; }
.status--进行中 { background: #eff6ff; color: #2563eb; }
.status--已生成,
.status--已完成 { background: #ecfdf3; color: #16a34a; }
.status--待完善 { background: #fff7ed; color: #f97316; }

.meta {
  margin-top: 14px;
  min-height: 42px;
  max-height: 42px;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
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
  background: #e5e7eb;
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
  max-height: 68px;
  min-height: 68px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  overflow: hidden;
}

.resource-chips span {
  height: 30px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  padding: 0 9px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #4b5563;
  font-size: 13px;
  max-width: 100%;
  white-space: nowrap;
}

.project-card footer {
  margin-top: auto;
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
