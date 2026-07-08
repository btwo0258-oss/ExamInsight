<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { courseLibraries, learningPlans } from '@/mock'

const route = useRoute()
const router = useRouter()
const plan = computed(() => learningPlans.find((item) => item.id === Number(route.params.id)) ?? learningPlans[0])
const library = computed(() => courseLibraries.find((item) => item.id === plan.value.libraryId))
const agentProgress = computed(() => {
  if (plan.value.agents.length <= 1) return 100
  const completed = plan.value.agents.filter((agent) => agent.status === 'done').length
  return Math.max(0, ((completed - 1) / (plan.value.agents.length - 1)) * 100)
})
</script>

<template>
  <StudentShell>
    <div class="plan-page">
      <header class="hero">
        <button class="back-btn" type="button" @click="router.push('/learning')">
          <AppIcon name="chevron-left" :size="18" />
          返回智能学习
        </button>
        <div class="hero-body">
          <div>
            <span class="eyebrow">{{ library?.name }}</span>
            <h1>{{ plan.title }}</h1>
            <p>{{ plan.goal }}</p>
          </div>
          <div class="hero-actions">
            <button class="outline-btn" type="button" @click="router.push('/chat')">进入答疑</button>
            <button class="outline-btn" type="button">开始练习</button>
            <button class="primary-btn" type="button" @click="router.push(`/learning/${plan.id}/resources`)">
              查看资源包
            </button>
          </div>
        </div>
      </header>

      <section class="agent-strip">
        <div class="agent-track">
          <span class="agent-track-fill" :style="{ width: `${agentProgress}%` }" />
        </div>
        <article
          v-for="(agent, index) in plan.agents"
          :key="agent.name"
          class="agent-step"
          :class="`agent-step--${agent.status}`"
        >
          <span class="agent-dot">{{ index + 1 }}</span>
          <strong>{{ agent.name }}</strong>
          <p>{{ agent.desc }}</p>
        </article>
      </section>

      <div class="layout-grid">
        <aside class="panel profile-panel">
          <div class="panel-title">
            <AppIcon name="user" :size="22" />
            <h2>个性化依据</h2>
          </div>
          <div class="profile-list">
            <article v-for="item in plan.profile" :key="item.label">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </article>
          </div>
        </aside>

        <section class="panel path-panel">
          <div class="panel-title">
            <AppIcon name="book" :size="22" />
            <h2>学习路径</h2>
          </div>
          <div class="timeline">
            <article
              v-for="stage in plan.stages"
              :key="stage.id"
              class="stage"
              :class="`stage--${stage.status}`"
            >
              <span class="stage-index">{{ stage.id }}</span>
              <div class="stage-card">
                <div class="stage-head">
                  <h3>{{ stage.title }}</h3>
                  <small>预计 {{ stage.duration }}</small>
                </div>
                <p>{{ stage.goal }}</p>
                <div class="chips">
                  <span v-for="resource in stage.resources" :key="resource">{{ resource }}</span>
                </div>
              </div>
            </article>
          </div>
        </section>

        <section class="panel resource-panel">
          <div class="panel-title title-between">
            <div>
              <AppIcon name="folder" :size="22" />
              <h2>资源包预览</h2>
            </div>
            <button type="button" @click="router.push(`/learning/${plan.id}/resources`)">
              查看全部
              <AppIcon name="chevron-right" :size="16" />
            </button>
          </div>
          <div class="resource-grid">
            <article v-for="item in plan.resources" :key="item.id">
              <span>{{ item.group }}</span>
              <h3>{{ item.title }}</h3>
              <p>{{ item.desc }}</p>
            </article>
          </div>
        </section>
      </div>
    </div>
  </StudentShell>
</template>

<style scoped>
.plan-page {
  min-height: 100%;
  padding: 34px 28px 56px;
  background: #fffffc;
}

.plan-page,
.plan-page * {
  box-sizing: border-box;
}

.hero,
.agent-strip,
.layout-grid {
  max-width: 1260px;
  margin-left: auto;
  margin-right: auto;
}

h1,
h2,
h3,
p {
  margin: 0;
}

.back-btn {
  height: 28px;
  border: 0;
  background: transparent;
  color: #667085;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
}

.back-btn .icon {
  width: 14px;
  height: 14px;
}

.hero-body {
  margin-top: 14px;
  padding: 24px;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  background: #fffffc;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: center;
}

.eyebrow {
  color: #111827;
  font-weight: 800;
  font-size: 13px;
}

h1 {
  margin-top: 8px;
  font-size: 30px;
  color: #111827;
}

.hero-body p {
  margin-top: 10px;
  color: #667085;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  gap: 10px;
}

.outline-btn,
.primary-btn {
  height: 42px;
  border-radius: 8px;
  padding: 0 16px;
  cursor: pointer;
  font-weight: 700;
}

.outline-btn {
  border: 1px solid #cfd7e3;
  background: #fffffc;
  color: #1f2937;
}

.primary-btn {
  border: 1px solid #111827;
  background: #111827;
  color: #fff;
}

.agent-strip {
  position: relative;
  margin-top: 18px;
  padding: 18px 16px 16px;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  background: #fffffc;
}

.agent-track {
  position: absolute;
  left: 44px;
  right: 44px;
  top: 34px;
  height: 3px;
  border-radius: 999px;
  background: #e6ebf3;
  overflow: hidden;
}

.agent-track-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #111827;
}

.agent-step {
  position: relative;
  z-index: 1;
  padding: 0 12px;
  text-align: center;
}

.agent-dot {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  margin: 0 auto;
  border: 1px solid #d4dce8;
  background: #fffffc;
  color: #667085;
  font-size: 13px;
  font-weight: 800;
}

.agent-step--done .agent-dot {
  background: #111827;
  border-color: #111827;
  color: #fff;
}

.agent-strip strong {
  display: block;
  margin-top: 12px;
  color: #1f2937;
}

.agent-strip p {
  margin-top: 5px;
  color: #667085;
  font-size: 13px;
  line-height: 1.5;
}

.layout-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
}

.panel {
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  padding: 22px;
  background: #fffffc;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.03);
}

.panel-title,
.panel-title > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-title {
  margin-bottom: 18px;
}

.panel-title h2 {
  font-size: 20px;
  color: #1f2937;
}

.profile-panel {
  align-self: start;
}

.profile-list {
  display: grid;
  gap: 12px;
}

.profile-list article {
  border-bottom: 1px solid #e6ebf3;
  padding-bottom: 12px;
}

.profile-list span {
  display: block;
  color: #667085;
  font-size: 13px;
  margin-bottom: 5px;
}

.profile-list strong {
  color: #1f2937;
  line-height: 1.6;
}

.path-panel,
.resource-panel {
  grid-column: 2;
}

.timeline {
  display: grid;
  gap: 14px;
}

.stage {
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 14px;
}

.stage-index {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: #f8fafc;
  border: 1px solid #d4dce8;
  color: #667085;
  font-weight: 800;
}

.stage--done .stage-index,
.stage--active .stage-index {
  background: #111827;
  border-color: #111827;
  color: #fff;
}

.stage-card {
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  padding: 16px;
  background: #fffffc;
}

.stage--active .stage-card {
  border-color: #dbe2ec;
  background: #f8fafc;
}

.stage-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.stage-head h3 {
  color: #111827;
  font-size: 17px;
}

.stage-head small {
  color: #667085;
  white-space: nowrap;
}

.stage-card p {
  margin-top: 8px;
  color: #4b5563;
  line-height: 1.6;
}

.chips {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chips span,
.resource-grid article > span {
  padding: 4px 9px;
  border: 1px solid #dbe2ec;
  border-radius: 6px;
  background: #f8fafc;
  color: #667085;
  font-size: 12px;
}

.title-between {
  justify-content: space-between;
}

.title-between button {
  border: 0;
  background: transparent;
  color: #111827;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-weight: 700;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.resource-grid article {
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  padding: 16px;
  min-height: 138px;
  display: flex;
  flex-direction: column;
}

.resource-grid h3 {
  margin-top: 12px;
  color: #111827;
  font-size: 16px;
}

.resource-grid p {
  flex: 1;
  margin-top: 7px;
  color: #667085;
  line-height: 1.5;
}

.resource-grid article > span {
  width: fit-content;
  margin-top: auto;
}

@media (max-width: 1080px) {
  .hero-body,
  .agent-strip,
  .layout-grid,
  .resource-grid {
    grid-template-columns: 1fr;
  }

  .path-panel,
  .resource-panel {
    grid-column: auto;
  }

  .hero-actions {
    flex-wrap: wrap;
  }
}
</style>
