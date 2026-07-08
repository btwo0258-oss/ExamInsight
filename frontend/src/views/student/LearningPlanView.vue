<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { courseLibraries, learningPlans } from '@/mock'

const route = useRoute()
const router = useRouter()
const plan = computed(() => learningPlans.find((item) => item.id === Number(route.params.id)) ?? learningPlans[0])
const library = computed(() => courseLibraries.find((item) => item.id === plan.value.libraryId))
</script>

<template>
  <StudentShell>
    <div class="page">
      <header class="plan-head">
        <div>
          <div class="eyebrow">{{ library?.name }}</div>
          <h1>{{ plan.title }}</h1>
          <p>{{ plan.goal }}</p>
        </div>
        <div class="head-actions">
          <AppButton variant="secondary" @click="router.push('/chat')">进入答疑</AppButton>
          <AppButton variant="secondary">开始练习</AppButton>
          <AppButton variant="primary" @click="router.push(`/learning/${plan.id}/resources`)">查看资源包</AppButton>
        </div>
      </header>

      <section class="agent-panel">
        <article v-for="agent in plan.agents" :key="agent.name" class="agent-item">
          <span class="dot" />
          <strong>{{ agent.name }}</strong>
          <small>{{ agent.desc }}</small>
        </article>
      </section>

      <div class="layout-grid">
        <section class="panel">
          <div class="panel-title">
            <AppIcon name="users" :size="18" />
            <h2>个性化依据</h2>
          </div>
          <div class="profile-grid">
            <div v-for="item in plan.profile" :key="item.label" class="profile-item">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </section>

        <section class="panel path-panel">
          <div class="panel-title">
            <AppIcon name="graduation" :size="18" />
            <h2>学习路径</h2>
          </div>
          <div class="timeline">
            <article
              v-for="stage in plan.stages"
              :key="stage.id"
              class="stage"
              :class="`stage--${stage.status}`"
            >
              <div class="stage-marker">{{ stage.id }}</div>
              <div class="stage-body">
                <div class="stage-head">
                  <h3>{{ stage.title }}</h3>
                  <span>{{ stage.duration }}</span>
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
          <div class="panel-title">
            <AppIcon name="folder" :size="18" />
            <h2>资源包预览</h2>
          </div>
          <div class="resource-grid">
            <article v-for="item in plan.resources" :key="item.id" class="resource-card">
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
.page {
  width: min(1180px, calc(100% - 48px));
  margin: 0 auto;
  padding: 34px 0 56px;
}

.plan-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 18px;
}

.eyebrow,
.plan-head p,
.agent-item small,
.profile-item span,
.stage-body p,
.resource-card p {
  color: var(--color-text-muted);
}

.eyebrow {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 8px;
}

.plan-head h1,
.panel-title h2,
.stage-body h3,
.resource-card h3 {
  margin: 0;
}

.plan-head h1 {
  font-size: 26px;
}

.plan-head p {
  margin: 8px 0 0;
}

.head-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.agent-panel,
.panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
}

.agent-panel {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  margin-bottom: 16px;
}

.agent-item {
  padding: 14px;
  display: grid;
  gap: 5px;
  border-right: 1px solid var(--color-border);
}

.agent-item:last-child {
  border-right: 0;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #10b981;
}

.layout-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
}

.panel {
  padding: 18px;
}

.path-panel,
.resource-panel {
  grid-column: 2;
}

.panel-title {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 14px;
}

.panel-title h2 {
  font-size: 17px;
}

.profile-grid {
  display: grid;
  gap: 10px;
}

.profile-item {
  display: grid;
  gap: 5px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border);
}

.profile-item strong {
  font-size: 14px;
  line-height: 1.5;
}

.timeline {
  display: grid;
  gap: 12px;
}

.stage {
  display: flex;
  gap: 12px;
}

.stage-marker {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  display: grid;
  place-items: center;
  font-weight: 800;
  background: var(--color-bg);
}

.stage--done .stage-marker,
.stage--active .stage-marker {
  background: var(--color-text);
  color: var(--color-bg);
}

.stage-body {
  flex: 1;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--color-bg);
}

.stage-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.stage-head span {
  font-size: 13px;
  font-weight: 700;
}

.stage-body p,
.resource-card p {
  margin: 7px 0 0;
  line-height: 1.5;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.chips span,
.resource-card span {
  font-size: 12px;
  color: var(--color-text-muted);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 3px 8px;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.resource-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  padding: 12px;
}

.resource-card h3 {
  margin-top: 10px;
  font-size: 15px;
}

@media (max-width: 980px) {
  .agent-panel,
  .layout-grid,
  .resource-grid {
    grid-template-columns: 1fr;
  }

  .path-panel,
  .resource-panel {
    grid-column: auto;
  }
}
</style>
