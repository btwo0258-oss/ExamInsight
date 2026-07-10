<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { learningPlans } from '@/mock'

const route = useRoute()
const router = useRouter()
const plan = computed(() => learningPlans.find((item) => item.id === Number(route.params.id)) ?? learningPlans[0]!)
const wrong = computed(() => plan.value.wrongQuestions[0])
const exercise = computed(() => plan.value.exercises[0])
</script>

<template>
  <StudentShell>
    <div class="mistakes-page">
      <header class="page-head">
        <button type="button" @click="router.push(`/learning/${plan.id}`)">
          <AppIcon name="chevron-left" :size="18" />
        </button>
        <div>
          <h1>错题详情</h1>
          <p>AI 会归因错题、关联知识点，并把复习任务同步回学习计划。</p>
        </div>
      </header>

      <main class="mistake-layout">
        <section class="detail-panel panel">
          <header>
            <div>
              <h2>题目 5（单选题）</h2>
              <span>中等</span>
            </div>
          </header>

          <p>{{ wrong?.title }}</p>
          <pre v-if="exercise?.code"><code>{{ exercise.code }}</code></pre>

          <div class="answer answer--wrong">
            <strong>你的答案</strong>
            <span>{{ wrong?.userAnswer }}</span>
          </div>
          <div class="answer answer--right">
            <strong>正确答案</strong>
            <span>{{ wrong?.correctAnswer }}</span>
          </div>

          <section class="reason-box">
            <h3>错因：{{ wrong?.reason.split('，')[0] }}</h3>
            <p>{{ wrong?.reason }}</p>
          </section>

          <section class="knowledge-box">
            <h3>关联知识点</h3>
            <div>
              <span v-for="item in wrong?.knowledge" :key="item">{{ item }}</span>
            </div>
          </section>

          <footer>
            <button class="primary-btn" type="button">加入复习计划</button>
            <button class="outline-btn" type="button">生成 5 道同类题</button>
            <button class="outline-btn" type="button" @click="router.push(`/learning/${plan.id}`)">返回错题本</button>
          </footer>

          <div v-if="wrong?.synced" class="sync-tip">
            <AppIcon name="check" :size="18" />
            已同步更新学习面板
            <button type="button">查看学习面板 <AppIcon name="chevron-right" :size="14" /></button>
          </div>
        </section>

        <aside class="list-panel panel">
          <h2>错题整理</h2>
          <button
            v-for="item in plan.wrongQuestions"
            :key="item.id"
            class="wrong-row"
            :class="{ active: item.id === wrong?.id }"
            type="button"
          >
            <span>{{ item.title }}</span>
            <small>{{ item.knowledge.join(' / ') }}</small>
          </button>

          <section class="weak-card">
            <h3>薄弱知识点</h3>
            <label v-for="item in plan.dashboard" :key="item.label">
              <span>{{ item.label }}</span>
              <i><b :style="{ width: `${item.value}%` }" /></i>
              <strong>{{ item.value }}%</strong>
            </label>
          </section>
        </aside>
      </main>
    </div>
  </StudentShell>
</template>

<style scoped>
.mistakes-page {
  min-height: 100%;
  padding: 28px 34px 42px;
  background: var(--color-bg);
}

.mistakes-page,
.mistakes-page * {
  box-sizing: border-box;
}

h1,
h2,
h3,
p {
  margin: 0;
}

button {
  font: inherit;
}

.page-head,
.mistake-layout {
  max-width: 1260px;
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

.mistake-layout {
  margin-top: 24px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 18px;
}

.panel {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  padding: 22px;
}

.detail-panel > header {
  display: flex;
  justify-content: space-between;
}

.detail-panel h2,
.list-panel h2 {
  color: var(--color-text);
  font-size: 22px;
}

.detail-panel header span {
  display: inline-block;
  margin-top: 8px;
  border-radius: 6px;
  padding: 3px 9px;
  background: #fff7ed;
  color: #f97316;
  font-weight: 800;
  font-size: 12px;
}

.detail-panel > p {
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

.answer {
  min-height: 52px;
  margin-top: 12px;
  border-radius: 8px;
  padding: 12px 14px;
  display: grid;
  gap: 6px;
}

.answer strong {
  color: var(--color-text);
}

.answer--wrong {
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #dc2626;
}

.answer--right {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #16a34a;
}

.reason-box,
.knowledge-box,
.sync-tip {
  margin-top: 16px;
  border-radius: 8px;
  padding: 16px;
}

.reason-box {
  border: 1px solid #fecaca;
  background: #fff7f7;
}

.reason-box h3 {
  color: #dc2626;
  font-size: 18px;
}

.reason-box p {
  margin-top: 10px;
  color: var(--color-text);
  line-height: 1.8;
}

.knowledge-box {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
}

.knowledge-box h3 {
  color: #2563eb;
  font-size: 18px;
}

.knowledge-box div {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.knowledge-box span {
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  background: #fff;
  color: #2563eb;
  padding: 5px 10px;
  font-weight: 800;
}

.detail-panel footer {
  margin-top: 22px;
  display: flex;
  gap: 12px;
}

.primary-btn,
.outline-btn {
  height: 42px;
  border-radius: 8px;
  padding: 0 22px;
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

.sync-tip {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #16a34a;
  display: flex;
  align-items: center;
  gap: 10px;
}

.sync-tip button {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.wrong-row {
  width: 100%;
  min-height: 62px;
  margin-top: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 10px 12px;
  text-align: left;
  display: grid;
  gap: 5px;
  cursor: pointer;
}

.wrong-row.active,
.wrong-row:hover {
  border-color: var(--color-primary);
  background: #f4f6f8;
}

.wrong-row small {
  color: var(--color-text-muted);
}

.weak-card {
  margin-top: 24px;
  border-top: 1px solid var(--color-border);
  padding-top: 20px;
}

.weak-card h3 {
  color: var(--color-text);
  font-size: 18px;
  margin-bottom: 14px;
}

.weak-card label {
  min-height: 36px;
  display: grid;
  grid-template-columns: 52px 1fr 42px;
  align-items: center;
  gap: 10px;
}

.weak-card span,
.weak-card strong {
  color: var(--color-text);
}

.weak-card i {
  height: 6px;
  border-radius: 999px;
  background: #e5e7eb;
  overflow: hidden;
}

.weak-card b {
  display: block;
  height: 100%;
  background: var(--color-primary);
  border-radius: inherit;
}

@media (max-width: 980px) {
  .mistake-layout {
    grid-template-columns: 1fr;
  }
}
</style>
