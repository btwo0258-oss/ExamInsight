<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { learningPlans } from '@/mock'

const route = useRoute()
const router = useRouter()
const plan = computed(() => learningPlans.find((item) => item.id === Number(route.params.id)) ?? learningPlans[0])
const groups = computed(() => Array.from(new Set(plan.value.resources.map((item) => item.group))))
</script>

<template>
  <StudentShell>
    <div class="page">
      <header class="head">
        <div>
          <h1>{{ plan.title }} · 资源包</h1>
          <p>这些资源由本次学习分析生成，后续可接入导出、预览、练习和代码查看。</p>
        </div>
        <AppButton variant="secondary" @click="router.push(`/learning/${plan.id}`)">返回方案</AppButton>
      </header>

      <section v-for="group in groups" :key="group" class="resource-section">
        <h2>{{ group }}</h2>
        <div class="cards">
          <article
            v-for="item in plan.resources.filter((resource) => resource.group === group)"
            :key="item.id"
            class="resource-card"
          >
            <div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.desc }}</p>
            </div>
            <AppButton variant="secondary">{{ item.action }}</AppButton>
          </article>
        </div>
      </section>
    </div>
  </StudentShell>
</template>

<style scoped>
.page {
  width: min(1080px, calc(100% - 48px));
  margin: 0 auto;
  padding: 34px 0 56px;
}

.head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  margin-bottom: 22px;
}

.head h1,
.resource-section h2,
.resource-card h3 {
  margin: 0;
}

.head h1 {
  font-size: 24px;
}

.head p,
.resource-card p {
  color: var(--color-text-muted);
}

.head p {
  margin: 8px 0 0;
}

.resource-section {
  margin-top: 18px;
}

.resource-section h2 {
  font-size: 17px;
  margin-bottom: 10px;
}

.cards {
  display: grid;
  gap: 10px;
}

.resource-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 16px;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
}

.resource-card h3 {
  font-size: 16px;
}

.resource-card p {
  margin: 7px 0 0;
}
</style>
