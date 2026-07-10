<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { courseLibraries, learningPlans } from '@/mock'

const router = useRouter()
const keyword = ref('')
const status = ref('全部状态')

const filteredPlans = computed(() =>
  learningPlans.filter((plan) => {
    const hitKeyword = !keyword.value || plan.title.includes(keyword.value)
    const hitStatus = status.value === '全部状态' || plan.status === status.value
    return hitKeyword && hitStatus
  }),
)

function libraryName(id: number) {
  return courseLibraries.find((item) => item.id === id)?.name ?? '未选择资料库'
}
</script>

<template>
  <StudentShell>
    <div class="projects-page">
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
          <select v-model="status">
            <option>全部状态</option>
            <option>进行中</option>
            <option>已生成</option>
            <option>已完成</option>
            <option>待完善</option>
          </select>
          <button type="button" @click="router.push('/learning/new')">新建学习</button>
        </div>
      </header>

      <section class="summary-grid">
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
      </section>

      <section class="project-grid">
        <article v-for="plan in filteredPlans" :key="plan.id" class="project-card">
          <header>
            <h2>{{ plan.title }}</h2>
            <span :class="`status status--${plan.status}`">{{ plan.status }}</span>
          </header>
          <p class="meta">{{ plan.period }}｜{{ libraryName(plan.libraryId) }}｜{{ plan.targetType }}</p>

          <div class="progress-row">
            <span>已完成 {{ plan.progress }}%</span>
            <i><b :style="{ width: `${plan.progress}%` }" /></i>
          </div>

          <div class="stats">
            <div><small>任务</small><strong>{{ plan.taskDone }}/{{ plan.totalTasks }}</strong></div>
            <div><small>练习</small><strong>{{ plan.exerciseDone }}/{{ plan.totalExercises }}</strong></div>
            <div><small>正确率</small><strong>{{ plan.correctRate }}%</strong></div>
          </div>

          <div class="resource-chips">
            <span v-for="resource in plan.resources.slice(0, 3)" :key="resource.id">
              <AppIcon
                :name="resource.group === '思维导图' ? 'mind-topic' : resource.group === '代码案例' ? 'code' : resource.group === 'PPT' ? 'presentation' : 'file'"
                :size="15"
              />
              {{ resource.group }}
            </span>
          </div>

          <footer>
            <button class="primary-btn" type="button" @click="router.push(`/learning/${plan.id}/study`)">
              {{ plan.status === '已生成' ? '开始学习' : plan.status === '待完善' ? '继续配置' : plan.status === '已完成' ? '查看报告' : '继续学习' }}
            </button>
            <button class="outline-btn" type="button" @click="router.push(`/learning/${plan.id}`)">
              {{ plan.status === '已生成' ? '查看详情' : '查看工作台' }}
            </button>
          </footer>
        </article>
      </section>

      <section class="recent-panel">
        <header>
          <h2>最近更新</h2>
          <button type="button">查看全部项目 <AppIcon name="chevron-right" :size="15" /></button>
        </header>
        <div class="recent-table">
          <div class="table-head">
            <span>项目名称</span>
            <span>最后更新</span>
            <span>进度</span>
          </div>
          <div v-for="plan in learningPlans.slice(0, 3)" :key="plan.id" class="table-row">
            <span>{{ plan.title }}</span>
            <span>{{ plan.updatedAt }}</span>
            <span class="mini-progress">{{ plan.progress }}% <i><b :style="{ width: `${plan.progress}%` }" /></i></span>
          </div>
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
input,
select {
  font: inherit;
}

.page-head,
.summary-grid,
.project-grid,
.recent-panel {
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
.head-actions select {
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

.head-actions select {
  padding: 0 12px;
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
.project-card,
.recent-panel {
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

.project-grid {
  margin-top: 26px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.project-card {
  padding: 20px;
}

.project-card header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.project-card h2 {
  color: var(--color-text);
  font-size: 17px;
  line-height: 1.4;
}

.status {
  flex: 0 0 auto;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 800;
}

.status--进行中 { background: #eff6ff; color: #2563eb; }
.status--已生成,
.status--已完成 { background: #ecfdf3; color: #16a34a; }
.status--待完善 { background: #fff7ed; color: #f97316; }

.meta {
  margin-top: 14px;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.progress-row {
  margin-top: 20px;
  display: grid;
  gap: 8px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.progress-row i,
.mini-progress i {
  display: block;
  height: 6px;
  border-radius: 999px;
  background: #e5e7eb;
  overflow: hidden;
}

.progress-row b,
.mini-progress b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-primary);
}

.stats {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  text-align: center;
}

.stats > div {
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
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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
}

.project-card footer {
  margin-top: 20px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.primary-btn,
.outline-btn {
  height: 42px;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 800;
}

.outline-btn {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
}

.recent-panel {
  margin-top: 22px;
  padding: 20px;
}

.recent-panel header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.recent-panel header h2 {
  color: var(--color-text);
  font-size: 18px;
}

.recent-panel header button {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  height: 34px;
  padding: 0 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.recent-table {
  margin-top: 18px;
}

.table-head,
.table-row {
  display: grid;
  grid-template-columns: 1fr 180px 260px;
  gap: 18px;
  align-items: center;
  min-height: 44px;
  border-bottom: 1px solid var(--color-border);
}

.table-head {
  color: var(--color-text-muted);
  font-size: 13px;
}

.table-row {
  color: var(--color-text);
}

.mini-progress {
  display: grid;
  grid-template-columns: 52px 1fr;
  align-items: center;
  gap: 12px;
}

@media (max-width: 1280px) {
  .summary-grid,
  .project-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .page-head,
  .head-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid,
  .project-grid {
    grid-template-columns: 1fr;
  }
}
</style>
