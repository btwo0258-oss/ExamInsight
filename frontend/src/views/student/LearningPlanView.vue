<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { courseLibraries, learningPlans } from '@/mock'

const route = useRoute()
const router = useRouter()
const plan = computed(() => learningPlans.find((item) => item.id === Number(route.params.id)) ?? learningPlans[0]!)
const library = computed(() => courseLibraries.find((item) => item.id === plan.value.libraryId))

function resourceIcon(group: string) {
  if (group === '思维导图') return 'mind-topic'
  if (group === '练习题') return 'edit'
  if (group === '代码案例') return 'code'
  if (group === 'PPT') return 'presentation'
  if (group === '拓展阅读') return 'book'
  return 'file'
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
          <button class="outline-btn" type="button">调整计划</button>
          <button class="primary-btn" type="button">导出方案</button>
        </div>
      </header>

      <main class="workspace-grid">
        <section class="panel path-panel">
          <header class="panel-head">
            <div>
              <AppIcon name="list" :size="22" />
              <h2>学习路径 List</h2>
            </div>
            <span>已完成 {{ plan.taskDone }}/{{ plan.totalTasks }} 项任务</span>
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
                <input :checked="task.done" type="checkbox" />
                <span>{{ task.title }}</span>
                <small>{{ task.duration }}</small>
              </label>
            </article>
          </div>

          <button class="start-btn" type="button" @click="router.push(`/learning/${plan.id}/study`)">
            <AppIcon name="play" :size="18" />
            开始今日学习
          </button>
        </section>

        <section class="panel resource-panel">
          <header class="panel-head">
            <div>
              <AppIcon name="folder" :size="22" />
              <h2>资源包</h2>
            </div>
            <button type="button" @click="router.push(`/learning/${plan.id}/resources`)">查看全部</button>
          </header>
          <div class="resource-grid">
            <article v-for="resource in plan.resources" :key="resource.id" class="resource-tile">
              <AppIcon :name="resourceIcon(resource.group)" :size="30" />
              <strong>{{ resource.group }}</strong>
              <span :class="{ muted: resource.status === '未选择' }">{{ resource.status }}</span>
            </article>
          </div>
          <p class="resource-note">
            已生成：可直接使用；未选择：可按需生成或跳过。
          </p>
        </section>

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
          <button type="button">查看学习报告</button>
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
      </main>
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
  grid-template-columns: minmax(440px, 1.15fr) minmax(360px, 0.95fr) 320px;
  gap: 18px;
  align-items: start;
}

.panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  padding: 22px;
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
  margin-bottom: 18px;
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

.panel-head button {
  border: 0;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  font-weight: 800;
}

.path-panel {
  grid-row: span 3;
}

.total-progress {
  display: grid;
  grid-template-columns: 1fr 48px;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
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
  gap: 16px;
}

.day-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 16px;
}

.day-card header {
  display: grid;
  grid-template-columns: 74px 1fr;
  gap: 12px;
  align-items: start;
  margin-bottom: 12px;
}

.day-card header > span {
  height: 32px;
  border-radius: 7px;
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 800;
}

.day-card h3 {
  color: var(--color-text);
  font-size: 17px;
}

.day-card p,
.mini-card p,
.tutor-card p,
.resource-note {
  margin-top: 5px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.task-row {
  min-height: 34px;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) 74px;
  gap: 10px;
  align-items: center;
  color: var(--color-text);
}

.task-row small {
  color: var(--color-text-muted);
  text-align: right;
}

.start-btn {
  width: 100%;
  height: 48px;
  margin-top: 22px;
  border: 1px solid var(--color-primary);
  border-radius: 8px;
  background: var(--color-primary);
  color: #fff;
  cursor: pointer;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.resource-tile {
  min-height: 110px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  display: grid;
  place-items: center;
  gap: 6px;
  color: #2563eb;
  text-align: center;
  padding: 12px;
}

.resource-tile strong {
  color: var(--color-text);
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
  gap: 12px;
}

.profile-list article {
  min-height: 52px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 10px 12px;
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
  min-height: 210px;
}

.numbers,
.labels {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  text-align: center;
}

.numbers {
  margin-top: 22px;
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
  height: 38px;
  margin-top: 18px;
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
  margin-top: 18px;
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
  gap: 14px;
}

.bar-list label {
  display: grid;
  grid-template-columns: 52px 1fr 42px;
  align-items: center;
  gap: 10px;
  color: var(--color-text);
}

.tutor-card {
  min-height: 330px;
}

.tutor-card button {
  margin-top: 10px;
  text-align: left;
  padding: 0 12px;
  font-weight: 600;
}

.tutor-card label {
  height: 42px;
  margin-top: 16px;
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

@media (max-width: 1280px) {
  .workspace-grid {
    grid-template-columns: 1fr 1fr;
  }

  .path-panel {
    grid-row: auto;
  }
}

@media (max-width: 900px) {
  .workspace-head,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .resource-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
