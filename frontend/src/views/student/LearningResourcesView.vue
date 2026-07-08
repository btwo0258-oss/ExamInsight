<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import { learningPlans } from '@/mock'

const route = useRoute()
const router = useRouter()
const plan = computed(() => learningPlans.find((item) => item.id === Number(route.params.id)) ?? learningPlans[0])
const groups = computed(() => Array.from(new Set(plan.value.resources.map((item) => item.group))))
</script>

<template>
  <StudentShell>
    <div class="resources-page">
      <header class="page-head">
        <button type="button" @click="router.push(`/learning/${plan.id}`)">
          <AppIcon name="chevron-left" :size="18" />
          返回方案
        </button>
        <h1>资源包</h1>
        <p>{{ plan.title }} 生成的个性化学习资源，后续可接入预览、导出、练习和代码运行。</p>
      </header>

      <section v-for="group in groups" :key="group" class="group-panel">
        <div class="group-head">
          <h2>{{ group }}</h2>
          <span>{{ plan.resources.filter((item) => item.group === group).length }} 份资源</span>
        </div>
        <div class="cards">
          <article
            v-for="item in plan.resources.filter((resource) => resource.group === group)"
            :key="item.id"
            class="resource-card"
          >
            <span class="resource-icon">
              <AppIcon
                :name="
                  item.group === '文档'
                    ? 'file'
                    : item.group === '结构图'
                      ? 'mind-topic'
                      : item.group === '练习'
                        ? 'edit'
                        : 'code'
                "
                :size="32"
              />
            </span>
            <div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.desc }}</p>
            </div>
            <button type="button">{{ item.action }}</button>
          </article>
        </div>
      </section>
    </div>
  </StudentShell>
</template>

<style scoped>
.resources-page {
  min-height: 100%;
  padding: 34px 28px 56px;
  background: #fffffc;
}

.resources-page,
.resources-page * {
  box-sizing: border-box;
}

.page-head,
.group-panel {
  max-width: 1040px;
  margin-left: auto;
  margin-right: auto;
}

h1,
h2,
h3,
p {
  margin: 0;
}

.page-head button {
  border: 0;
  background: transparent;
  color: #667085;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-weight: 700;
}

h1 {
  margin-top: 14px;
  font-size: 34px;
  color: #111827;
}

.page-head p {
  margin-top: 10px;
  color: #667085;
}

.group-panel {
  margin-top: 22px;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  padding: 20px;
  background: #fffffc;
}

.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.group-head h2 {
  font-size: 21px;
  color: #1f2937;
}

.group-head span {
  color: #667085;
}

.cards {
  display: grid;
  gap: 12px;
}

.resource-card {
  min-height: 86px;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  padding: 16px;
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) 112px;
  align-items: center;
  gap: 16px;
}

.resource-icon {
  width: 50px;
  height: 50px;
  border-radius: 8px;
  background: #f2f4f7;
  color: #344054;
  display: grid;
  place-items: center;
}

.resource-card h3 {
  color: #111827;
  font-size: 17px;
}

.resource-card p {
  margin-top: 7px;
  color: #667085;
  line-height: 1.5;
}

.resource-card button {
  height: 38px;
  border: 1px solid #cfd7e3;
  border-radius: 8px;
  background: #fffffc;
  color: #111827;
  cursor: pointer;
  font-weight: 700;
}

@media (max-width: 760px) {
  .resource-card {
    grid-template-columns: 52px 1fr;
  }

  .resource-card button {
    grid-column: 2;
    width: fit-content;
  }
}
</style>
