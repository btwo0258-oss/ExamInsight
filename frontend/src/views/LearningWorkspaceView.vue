<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import TheSidebar from '@/components/sidebar/TheSidebar.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import AppButton from '@/components/common/AppButton.vue'
import { useAppState } from '@/stores/appState'

type AgentStatus = 'pending' | 'running' | 'done'

type AgentStep = {
  key: string
  name: string
  desc: string
  status: AgentStatus
}

const appState = useAppState()
const sidebarOpen = ref(true)
const selectedCourse = ref('Java 面向对象程序设计')
const learningNeed = ref(
  '我是计算机专业大二学生，正在复习 Java 面向对象。继承、多态和接口容易混淆，希望两周内补齐薄弱点，并配套一些代码练习。',
)
const isGenerating = ref(false)
const generated = ref(false)

const resourceTypes = ref([
  { key: 'doc', label: '讲解文档', checked: true },
  { key: 'mindmap', label: '思维导图', checked: true },
  { key: 'quiz', label: '练习题', checked: true },
  { key: 'case', label: '代码案例', checked: true },
  { key: 'reading', label: '推荐阅读', checked: true },
  { key: 'slides', label: 'PPT 大纲', checked: false },
])

const agents = ref<AgentStep[]>([
  { key: 'profile', name: '画像智能体', desc: '抽取基础、目标、偏好和薄弱点', status: 'pending' },
  { key: 'knowledge', name: '知识检索智能体', desc: '从课程知识库召回相关内容', status: 'pending' },
  { key: 'resource', name: '资源生成智能体', desc: '生成讲义、题目和案例', status: 'pending' },
  { key: 'planner', name: '路径规划智能体', desc: '组织学习顺序和阶段目标', status: 'pending' },
  { key: 'safety', name: '内容审核智能体', desc: '检查引用、事实和安全风险', status: 'pending' },
])

const profileTags = [
  { label: '专业方向', value: '计算机科学与技术' },
  { label: '知识基础', value: 'Java 基础一般，OOP 概念不稳定' },
  { label: '学习目标', value: '两周内完成期末复习' },
  { label: '认知风格', value: '偏好图解 + 代码示例' },
  { label: '易错点', value: '继承、多态、接口、抽象类' },
  { label: '实践能力', value: '能读代码，需要更多手写练习' },
]

const learningPath = [
  {
    title: '第 1 阶段：概念补齐',
    duration: '2 天',
    goal: '分清类、对象、封装、构造方法的职责',
    resources: ['讲解文档', '概念速查卡'],
  },
  {
    title: '第 2 阶段：继承与多态',
    duration: '4 天',
    goal: '理解方法重写、向上转型和动态绑定',
    resources: ['思维导图', '代码案例', '练习题'],
  },
  {
    title: '第 3 阶段：接口与抽象类',
    duration: '3 天',
    goal: '掌握接口设计、抽象类复用和适用场景',
    resources: ['对比讲义', '编程题'],
  },
  {
    title: '第 4 阶段：综合项目练习',
    duration: '5 天',
    goal: '完成一个学生成绩管理小案例并复盘错题',
    resources: ['实践项目', '测验', '错题反馈'],
  },
]

const generatedResources = computed(() =>
  [
    { key: 'doc', icon: 'notebook', title: '个性化课程讲义', desc: '按薄弱点重排继承、多态、接口的讲解顺序' },
    { key: 'mindmap', icon: 'mind-topic', title: '知识点思维导图', desc: '把 OOP 核心概念整理为可视化结构' },
    { key: 'quiz', icon: 'bar-chart', title: '分层练习题', desc: '包含选择题、判断题、代码阅读题' },
    { key: 'case', icon: 'tool', title: '代码实操案例', desc: '学生成绩管理案例，覆盖继承与接口' },
    { key: 'reading', icon: 'book', title: '推荐阅读材料', desc: '推荐设计原则、Java 官方文档片段' },
    { key: 'slides', icon: 'monitor', title: 'PPT 大纲', desc: '可用于课堂展示或演示视频素材' },
  ].filter((item) => resourceTypes.value.find((type) => type.key === item.key)?.checked),
)

function statusText(status: AgentStatus) {
  if (status === 'running') return '执行中'
  if (status === 'done') return '完成'
  return '等待'
}

function resetAgents() {
  agents.value = agents.value.map((agent) => ({ ...agent, status: 'pending' }))
}

async function generatePlan() {
  isGenerating.value = true
  generated.value = false
  resetAgents()

  for (const agent of agents.value) {
    agent.status = 'running'
    await new Promise((resolve) => window.setTimeout(resolve, 450))
    agent.status = 'done'
  }

  generated.value = true
  isGenerating.value = false
}

onMounted(() => {
  const raw = localStorage.getItem('llm.sidebar.open')
  if (raw === '0') sidebarOpen.value = false
  appState.setMode('learning')
})

watch(sidebarOpen, (open) => {
  localStorage.setItem('llm.sidebar.open', open ? '1' : '0')
})
</script>

<template>
  <div class="layout" :class="{ 'layout--open': sidebarOpen }">
    <aside class="drawer" :class="{ 'drawer--open': sidebarOpen }">
      <TheSidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    </aside>

    <main class="content">
      <div class="workspace">
        <header class="page-head">
          <div>
            <h1>学习工作台</h1>
            <p>用一个入口串起学生画像、多智能体协作、学习路径和资源生成。</p>
          </div>
          <AppButton variant="secondary" @click="generated = false">
            <template #icon><AppIcon name="edit" :size="16" /></template>
            重新填写
          </AppButton>
        </header>

        <section class="setup-panel">
          <div class="field">
            <label>课程</label>
            <select v-model="selectedCourse">
              <option>Java 面向对象程序设计</option>
              <option>人工智能导论</option>
              <option>数据结构</option>
              <option>机器学习基础</option>
            </select>
          </div>

          <div class="field field--wide">
            <label>学生学习需求</label>
            <textarea v-model="learningNeed" rows="5" />
          </div>

          <div class="field field--wide">
            <label>本次生成资源</label>
            <div class="checks">
              <label v-for="item in resourceTypes" :key="item.key" class="check-item">
                <input v-model="item.checked" type="checkbox" />
                <span>{{ item.label }}</span>
              </label>
            </div>
          </div>

          <div class="actions">
            <AppButton variant="primary" :loading="isGenerating" @click="generatePlan">
              <template #icon><AppIcon name="zap" :size="16" /></template>
              生成个性化方案
            </AppButton>
          </div>
        </section>

        <section class="agent-strip">
          <div
            v-for="agent in agents"
            :key="agent.key"
            class="agent-step"
            :class="`agent-step--${agent.status}`"
          >
            <div class="agent-top">
              <span class="status-dot" />
              <strong>{{ agent.name }}</strong>
              <span>{{ statusText(agent.status) }}</span>
            </div>
            <p>{{ agent.desc }}</p>
          </div>
        </section>

        <section class="result-grid" :class="{ 'result-grid--muted': !generated }">
          <div class="panel profile-panel">
            <div class="panel-head">
              <AppIcon name="users" :size="18" />
              <h2>学生画像</h2>
            </div>
            <div class="profile-list">
              <div v-for="item in profileTags" :key="item.label" class="profile-row">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </div>

          <div class="panel path-panel">
            <div class="panel-head">
              <AppIcon name="graduation" :size="18" />
              <h2>学习路径</h2>
            </div>
            <div class="path-list">
              <article v-for="stage in learningPath" :key="stage.title" class="path-item">
                <div class="path-main">
                  <h3>{{ stage.title }}</h3>
                  <p>{{ stage.goal }}</p>
                </div>
                <div class="path-meta">
                  <span>{{ stage.duration }}</span>
                  <small>{{ stage.resources.join(' / ') }}</small>
                </div>
              </article>
            </div>
          </div>

          <div class="panel resource-panel">
            <div class="panel-head">
              <AppIcon name="folder" :size="18" />
              <h2>生成资源</h2>
            </div>
            <div class="resource-list">
              <article v-for="item in generatedResources" :key="item.key" class="resource-item">
                <AppIcon :name="item.icon" :size="20" />
                <div>
                  <h3>{{ item.title }}</h3>
                  <p>{{ item.desc }}</p>
                </div>
              </article>
            </div>
          </div>
        </section>
      </div>
    </main>

    <div v-if="!sidebarOpen" class="mini">
      <button class="mini__btn" type="button" @click="sidebarOpen = true">
        <AppIcon name="sidebar-left" :size="20" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  background: var(--color-bg);
  transition: padding-left 180ms ease;
}

.layout--open {
  padding-left: var(--sidebar-width);
}

.drawer {
  position: fixed;
  inset: 0 auto 0 0;
  width: var(--sidebar-width);
  background: var(--color-sidebar);
  border-right: 1px solid var(--color-border);
  transform: translateX(-100%);
  transition: transform 180ms ease;
  z-index: 30;
}

.drawer--open {
  transform: translateX(0);
}

.content {
  flex: 1;
  min-width: 0;
  height: 100vh;
  overflow: auto;
}

.workspace {
  width: min(1180px, calc(100% - 48px));
  margin: 0 auto;
  padding: 32px 0 48px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-head h1 {
  margin: 0 0 8px;
  font-size: 26px;
}

.page-head p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 14px;
}

.setup-panel,
.panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.setup-panel {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 16px;
  padding: 18px;
  margin-bottom: 16px;
}

.field {
  display: grid;
  gap: 8px;
}

.field--wide {
  grid-column: 1 / -1;
}

.field label {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-muted);
}

select,
textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text);
  padding: 10px 12px;
  outline: none;
}

textarea {
  resize: vertical;
  line-height: 1.6;
}

.checks {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.check-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  font-size: 14px;
}

.actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
}

.agent-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.agent-step {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--color-surface);
}

.agent-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.agent-top strong {
  flex: 1;
}

.agent-top span:last-child {
  color: var(--color-text-muted);
  font-size: 12px;
}

.agent-step p {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--color-border);
}

.agent-step--running .status-dot {
  background: #f59e0b;
}

.agent-step--done .status-dot {
  background: #10b981;
}

.result-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  transition: opacity 0.2s;
}

.result-grid--muted {
  opacity: 0.48;
}

.profile-panel {
  grid-row: span 2;
}

.resource-panel {
  grid-column: 2;
}

.panel {
  padding: 18px;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.panel-head h2 {
  margin: 0;
  font-size: 17px;
}

.profile-list,
.path-list,
.resource-list {
  display: grid;
  gap: 10px;
}

.profile-row {
  display: grid;
  gap: 4px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border);
}

.profile-row span,
.path-meta small,
.resource-item p {
  color: var(--color-text-muted);
  font-size: 12px;
}

.profile-row strong {
  font-size: 14px;
  line-height: 1.5;
}

.path-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}

.path-main h3,
.resource-item h3 {
  margin: 0 0 6px;
  font-size: 15px;
}

.path-main p,
.resource-item p {
  margin: 0;
  line-height: 1.5;
}

.path-meta {
  min-width: 130px;
  display: grid;
  gap: 6px;
  text-align: right;
}

.path-meta span {
  font-weight: 700;
}

.resource-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.resource-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}

.mini {
  position: fixed;
  top: 12px;
  left: 12px;
  padding: 8px 10px;
  border-radius: 999px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  z-index: 25;
}

.mini__btn {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: grid;
  place-items: center;
}

@media (max-width: 920px) {
  .setup-panel,
  .result-grid,
  .resource-list {
    grid-template-columns: 1fr;
  }

  .resource-panel {
    grid-column: auto;
  }

  .agent-strip {
    grid-template-columns: 1fr;
  }

  .page-head {
    flex-direction: column;
  }
}
</style>
