<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import LibrarySelectModal from '@/components/student/LibrarySelectModal.vue'
import UploadMaterialModal from '@/components/student/UploadMaterialModal.vue'
import { courseLibraries, learningPlans } from '@/mock'

const router = useRouter()
const goal = ref('我正在复习 Java 面向对象，继承和多态总是搞混，希望两周内完成期末复习，并多做一些代码练习。')
const selectedLibraryId = ref(1)
const libraryModalOpen = ref(false)
const uploadModalOpen = ref(false)

const selectedLibrary = computed(() =>
  courseLibraries.find((item) => item.id === selectedLibraryId.value),
)

function selectLibrary(id: number) {
  selectedLibraryId.value = id
  libraryModalOpen.value = false
}

function startAnalysis() {
  router.push(`/learning/${learningPlans[0].id}`)
}
</script>

<template>
  <StudentShell>
    <div class="page">
      <header class="page-head">
        <div>
          <h1>智能学习</h1>
          <p>输入学习目标，选择课程资料，生成个性化学习路径和资源包。</p>
        </div>
      </header>

      <section class="hero-card">
        <label class="field field--wide">
          <span>今天想解决什么学习问题？</span>
          <textarea v-model="goal" rows="6" />
        </label>

        <div class="form-row">
          <label class="field">
            <span>课程方向</span>
            <select>
              <option>Java 面向对象程序设计</option>
              <option>人工智能导论</option>
              <option>数据结构</option>
              <option>大学英语四级</option>
            </select>
          </label>

          <div class="field">
            <span>课程资料</span>
            <div class="selected-library">
              <AppIcon name="folder" :size="18" />
              <div>
                <strong>{{ selectedLibrary?.name }}</strong>
                <small>{{ selectedLibrary?.fileCount }} 个文件 · {{ selectedLibrary?.chunkCount }} 个知识片段</small>
              </div>
            </div>
          </div>
        </div>

        <footer class="hero-actions">
          <AppButton variant="secondary" @click="uploadModalOpen = true">
            <template #icon><AppIcon name="upload-cloud" :size="16" /></template>
            上传资料
          </AppButton>
          <AppButton variant="secondary" @click="libraryModalOpen = true">
            <template #icon><AppIcon name="folder" :size="16" /></template>
            选择资料库
          </AppButton>
          <AppButton variant="primary" @click="startAnalysis">
            <template #icon><AppIcon name="zap" :size="16" /></template>
            开始智能分析
          </AppButton>
        </footer>
      </section>

      <section class="plans">
        <div class="section-head">
          <h2>最近学习方案</h2>
          <span>用于快速继续上一次学习</span>
        </div>
        <article v-for="plan in learningPlans" :key="plan.id" class="plan-card">
          <div>
            <h3>{{ plan.title }}</h3>
            <p>{{ plan.goal }}</p>
            <div class="plan-meta">
              <span>已生成 {{ plan.resources.length }} 类资源</span>
              <span>当前阶段：{{ plan.stages.find((stage) => stage.status === 'active')?.title }}</span>
            </div>
          </div>
          <AppButton variant="secondary" @click="router.push(`/learning/${plan.id}`)">继续学习</AppButton>
        </article>
      </section>
    </div>

    <LibrarySelectModal
      :open="libraryModalOpen"
      :selected-id="selectedLibraryId"
      @close="libraryModalOpen = false"
      @select="selectLibrary"
    />
    <UploadMaterialModal :open="uploadModalOpen" @close="uploadModalOpen = false" />
  </StudentShell>
</template>

<style scoped>
.page {
  width: min(1080px, calc(100% - 48px));
  margin: 0 auto;
  padding: 34px 0 56px;
}

.page-head {
  margin-bottom: 22px;
}

.page-head h1,
.section-head h2,
.plan-card h3 {
  margin: 0;
}

.page-head h1 {
  font-size: 28px;
}

.page-head p,
.section-head span,
.plan-card p,
.plan-meta,
.selected-library small,
.field span {
  color: var(--color-text-muted);
}

.page-head p {
  margin: 8px 0 0;
}

.hero-card,
.plan-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  box-shadow: var(--shadow-sm);
}

.hero-card {
  padding: 20px;
  display: grid;
  gap: 18px;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  font-size: 13px;
  font-weight: 700;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

textarea,
select {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  padding: 11px 12px;
  outline: none;
}

textarea {
  line-height: 1.7;
  resize: vertical;
}

.selected-library {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}

.selected-library strong,
.selected-library small {
  display: block;
}

.hero-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.plans {
  margin-top: 28px;
  display: grid;
  gap: 12px;
}

.section-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
}

.section-head h2 {
  font-size: 18px;
}

.plan-card {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
}

.plan-card h3 {
  font-size: 16px;
}

.plan-card p {
  margin: 8px 0;
}

.plan-meta {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 13px;
}

@media (max-width: 760px) {
  .form-row,
  .plan-card {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
