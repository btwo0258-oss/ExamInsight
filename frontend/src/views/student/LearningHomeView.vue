<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import LibrarySelectModal from '@/components/student/LibrarySelectModal.vue'
import UploadMaterialModal from '@/components/student/UploadMaterialModal.vue'
import { courseLibraries, learningPlans } from '@/mock'

const route = useRoute()
const router = useRouter()

const queryLibraryId = Number(route.query.libraryId)
const goal = ref(
  '我想系统学习 Java 面向对象，目标是理解核心概念并能在项目中熟练应用',
)
const selectedLibraryId = ref(
  courseLibraries.some((item) => item.id === queryLibraryId) ? queryLibraryId : 1,
)
const libraryModalOpen = ref(false)
const uploadModalOpen = ref(false)

const selectedLibrary = computed(() =>
  courseLibraries.find((item) => item.id === selectedLibraryId.value),
)
const activePlan = computed(() => learningPlans[0])

const profileItems = [
  { icon: 'graduation', label: '专业方向', value: '计算机科学与技术' },
  { icon: 'book', label: '知识基础', value: '中等' },
  { icon: 'alert-circle', label: '易错点', value: '继承、接口理解' },
  { icon: 'heart', label: '学习偏好', value: '图文 + 代码示例' },
]

const progressSteps = [
  { id: 1, title: '画像分析', desc: '分析你的学习背景与需求', status: '进行中' },
  { id: 2, title: '资料理解', desc: '理解已上传资料与知识点', status: '等待中' },
  { id: 3, title: '资源生成', desc: '生成个性化学习资源包', status: '等待中' },
  { id: 4, title: '路径规划', desc: '规划最优学习路径', status: '等待中' },
]

function selectLibrary(id: number) {
  selectedLibraryId.value = id
  libraryModalOpen.value = false
}

function startAnalysis() {
  router.push(`/learning/${activePlan.value.id}`)
}
</script>

<template>
  <StudentShell>
    <div class="learning-page">
      <header class="page-title">
        <h1>智能学习</h1>
      </header>

      <div class="top-grid">
        <section class="ask-panel">
          <div class="ask-title">
            <span class="icon-box"><AppIcon name="message-square" :size="22" /></span>
            <h2>今天想解决什么学习问题？</h2>
          </div>

          <label class="goal-box">
            <textarea
              v-model="goal"
              maxlength="500"
              placeholder="例如：我想系统学习 Java 面向对象，目标是理解核心概念并能在项目中熟练应用"
            />
            <span>{{ goal.length }}/500</span>
          </label>

          <div class="action-row">
            <button class="outline-btn" type="button" @click="uploadModalOpen = true">
              <AppIcon name="upload-cloud" :size="22" />
              上传资料
            </button>
            <button class="outline-btn" type="button" @click="libraryModalOpen = true">
              <AppIcon name="folder" :size="22" />
              选择资料库
            </button>
            <button class="primary-btn" type="button" @click="startAnalysis">
              <AppIcon name="play" :size="20" />
              开始智能分析
            </button>
          </div>
        </section>

        <aside class="profile-panel">
          <div class="panel-title">
            <AppIcon name="user" :size="22" />
            <h2>个性化依据</h2>
          </div>
          <div class="profile-list">
            <article v-for="item in profileItems" :key="item.label" class="profile-item">
              <AppIcon :name="item.icon" :size="20" />
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </article>
          </div>
        </aside>
      </div>

      <div class="content-grid">
        <section class="panel progress-panel">
          <div class="panel-title">
            <AppIcon name="clock" :size="22" />
            <h2>分析进度</h2>
          </div>
          <div class="progress-list">
            <article v-for="step in progressSteps" :key="step.id" class="progress-step">
              <span class="step-number">{{ step.id }}</span>
              <div>
                <h3>{{ step.title }}</h3>
                <p>{{ step.desc }}</p>
              </div>
              <small :class="{ active: step.id === 1 }">{{ step.status }}</small>
            </article>
          </div>
        </section>

        <section class="panel plan-panel">
          <div class="panel-title title-between">
            <div>
              <AppIcon name="book" :size="22" />
              <h2>学习方案预览</h2>
            </div>
            <button type="button" @click="router.push(`/learning/${activePlan.id}`)">
              查看完整方案
              <AppIcon name="chevron-right" :size="16" />
            </button>
          </div>

          <div class="path-list">
            <article
              v-for="stage in activePlan.stages.slice(0, 3)"
              :key="stage.id"
              class="path-card"
            >
              <span>{{ stage.id }}</span>
              <div>
                <h3>{{ stage.title }} <small>（预计 {{ stage.duration }}）</small></h3>
                <p>{{ stage.goal }}</p>
                <em>知识点 {{ stage.resources.length + 6 }} 个</em>
              </div>
            </article>
          </div>
          <button class="expand-btn" type="button" @click="router.push(`/learning/${activePlan.id}`)">
            展开后续阶段（共 {{ activePlan.stages.length }} 个阶段）
            <AppIcon name="chevron-down" :size="16" />
          </button>
        </section>

        <section class="panel resource-panel">
          <div class="panel-title">
            <AppIcon name="folder" :size="22" />
            <h2>资源包</h2>
          </div>
          <div class="resource-grid">
            <article v-for="item in activePlan.resources" :key="item.id" class="resource-tile">
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
                :size="34"
              />
              <h3>{{ item.title }}</h3>
              <p>{{ item.desc }}</p>
              <span>{{ item.group }}</span>
            </article>
          </div>
          <button class="link-row" type="button" @click="router.push(`/learning/${activePlan.id}/resources`)">
            查看全部资源
            <AppIcon name="chevron-right" :size="16" />
          </button>
        </section>
      </div>

      <div v-if="selectedLibrary" class="selected-source">
        当前资料来源：{{ selectedLibrary.name }}，{{ selectedLibrary.fileCount }} 个文件，{{ selectedLibrary.chunkCount }} 个知识片段
      </div>
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
.learning-page {
  min-height: 100%;
  padding: 38px 28px 56px;
  background: #fffffc;
}

.learning-page,
.learning-page * {
  box-sizing: border-box;
}

.page-title {
  max-width: 1260px;
  margin: 0 auto 26px;
}

h1,
h2,
h3,
p {
  margin: 0;
}

h1 {
  font-size: 34px;
  font-weight: 800;
  color: #111827;
}

.top-grid,
.content-grid,
.selected-source {
  max-width: 1260px;
  margin-left: auto;
  margin-right: auto;
}

.top-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 314px;
  gap: 16px;
  margin-bottom: 24px;
}

.ask-panel,
.profile-panel,
.panel {
  background: #fffffc;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.03);
}

.ask-panel {
  padding: 24px;
}

.ask-title,
.panel-title,
.panel-title > div {
  display: flex;
  align-items: center;
  gap: 12px;
}

.icon-box {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #f2f4f7;
  color: #111827;
}

h2 {
  font-size: 20px;
  font-weight: 800;
  color: #1f2937;
}

.goal-box {
  position: relative;
  display: block;
  margin-top: 18px;
}

.goal-box textarea {
  width: 100%;
  min-height: 128px;
  resize: none;
  border: 1px solid #cfd7e3;
  border-radius: 8px;
  padding: 18px 18px 34px;
  color: #1f2937;
  background: #fffffc;
  outline: none;
  line-height: 1.7;
}

.goal-box textarea:focus {
  border-color: #111827;
  box-shadow: 0 0 0 3px rgba(17, 24, 39, 0.08);
}

.goal-box span {
  position: absolute;
  right: 16px;
  bottom: 12px;
  color: #7b8494;
  font-size: 13px;
}

.action-row {
  margin-top: 26px;
  display: grid;
  grid-template-columns: 160px 180px 1fr;
  gap: 16px;
  align-items: center;
}

.outline-btn,
.primary-btn,
.title-between button,
.expand-btn,
.link-row {
  height: 52px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  font-weight: 700;
}

.outline-btn {
  border: 1px solid #cfd7e3;
  background: #fffffc;
  color: #1f2937;
}

.primary-btn {
  justify-self: end;
  min-width: 178px;
  border: 1px solid #111827;
  background: #111827;
  color: #fff;
  font-size: 16px;
  box-shadow: 0 10px 22px rgba(17, 24, 39, 0.12);
}

.profile-panel {
  padding: 24px 18px;
}

.profile-list {
  display: grid;
  gap: 12px;
  margin-top: 22px;
}

.profile-item {
  min-height: 56px;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  padding: 0 12px;
  display: grid;
  grid-template-columns: 24px 1fr auto;
  align-items: center;
  gap: 10px;
}

.profile-item span {
  color: #344054;
  font-weight: 700;
}

.profile-item strong {
  max-width: 132px;
  padding: 5px 10px;
  border-radius: 6px;
  background: #f2f4f7;
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.content-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr) 400px;
  gap: 16px;
  align-items: start;
}

.panel {
  padding: 24px;
}

.panel-title {
  margin-bottom: 22px;
}

.title-between {
  justify-content: space-between;
}

.title-between button,
.expand-btn,
.link-row {
  border: 1px solid #dbe2ec;
  background: #f8fafc;
  color: #344054;
  height: 34px;
  padding: 0 12px;
  white-space: nowrap;
}

.plan-panel .panel-title h2 {
  white-space: nowrap;
}

.progress-list {
  display: grid;
  gap: 18px;
}

.progress-step {
  display: grid;
  grid-template-columns: 36px 1fr auto;
  gap: 14px;
  align-items: start;
}

.step-number {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  border: 1px solid #d4dce8;
  background: #fffffc;
  color: #667085;
  font-weight: 800;
}

.progress-step:first-child .step-number {
  background: #111827;
  border-color: #111827;
  color: #fff;
}

.progress-step h3 {
  font-size: 16px;
  color: #1f2937;
}

.progress-step p {
  margin-top: 5px;
  color: #7b8494;
  font-size: 13px;
}

.progress-step small {
  align-self: center;
  border: 1px solid #dbe2ec;
  border-radius: 6px;
  padding: 4px 8px;
  color: #7b8494;
  white-space: nowrap;
}

.progress-step small.active {
  background: #eef6ff;
  border-color: #bfdbfe;
  color: #2563eb;
}

.path-list {
  display: grid;
  gap: 12px;
}

.path-card {
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 14px;
  align-items: start;
}

.path-card > span {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: #111827;
  color: #fff;
  font-weight: 800;
}

.path-card > div {
  border: 1px solid #dbe2ec;
  background: #f8fafc;
  border-radius: 8px;
  padding: 14px 16px;
}

.path-card h3 {
  font-size: 16px;
  color: #1f2937;
}

.path-card h3 small {
  color: #7b8494;
  font-weight: 500;
}

.path-card p {
  margin-top: 8px;
  color: #4b5563;
  line-height: 1.6;
}

.path-card em {
  display: block;
  margin-top: 10px;
  color: #667085;
  font-size: 13px;
  font-style: normal;
}

.expand-btn {
  width: fit-content;
  margin: 16px auto 0;
  border-color: transparent;
  background: transparent;
  color: #111827;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.resource-tile {
  min-height: 206px;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 8px;
  color: #344054;
}

.resource-tile h3 {
  font-size: 16px;
  color: #1f2937;
}

.resource-tile p {
  flex: 1;
  color: #7b8494;
  line-height: 1.5;
}

.resource-tile span {
  margin-top: auto;
  padding: 4px 10px;
  border-radius: 6px;
  background: #f2f4f7;
  color: #667085;
  font-size: 13px;
}

.link-row {
  width: fit-content;
  margin: 22px auto 0;
  border-color: transparent;
  background: transparent;
  color: #111827;
}

.selected-source {
  margin-top: 18px;
  color: #667085;
  font-size: 13px;
}

@media (max-width: 1180px) {
  .top-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .primary-btn {
    justify-self: stretch;
  }
}

@media (max-width: 760px) {
  .learning-page {
    padding: 24px 16px 40px;
  }

  .action-row,
  .resource-grid {
    grid-template-columns: 1fr;
  }
}
</style>
