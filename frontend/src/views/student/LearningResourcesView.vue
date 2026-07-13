<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import LearningDetailShell from '@/components/student/LearningDetailShell.vue'
import type { LearningResource } from '@/mock'
import { useLearningStore } from '@/stores/learning'

const route = useRoute()
const router = useRouter()
const learningStore = useLearningStore()
const plan = computed(() => learningStore.getPlan(Number(route.params.id)) ?? learningStore.plans[0]!)
const firstResourceId = computed(() => plan.value.resources[0]?.id)
const activeResourceId = ref(firstResourceId.value)
const activeResource = computed(() =>
  plan.value.resources.find((item) => item.id === activeResourceId.value) ?? plan.value.resources[0],
)
const sourceTaskId = computed(() => Number(route.query.task) || undefined)
const sourceStageId = computed(() => plan.value.stages.find((stage) => stage.tasks.some((task) => task.id === sourceTaskId.value))?.id)
let readingTimer: number | undefined

type ResourceMeta = {
  purpose: string
  usage: string
  primaryAction: string
  actions: string[]
  questions: string[]
}

const metaMap: Record<LearningResource['group'], ResourceMeta> = {
  个性化学习手册: {
    purpose: '按你的薄弱点重新组织资料库内容，适合在每个学习阶段开始前快速建立知识框架。',
    usage: '先读核心概念和常见误区，再进入阶段任务里的练习和测验。',
    primaryAction: '打开手册',
    actions: ['编辑手册', '导出 Markdown', '导出 PDF'],
    questions: ['帮我总结这份手册', '把重点整理成复习清单', '解释我最容易混淆的部分'],
  },
  PPT: {
    purpose: '用于课堂汇报、考前串讲或快速复述知识点，不适合在这里按文档方式阅读。',
    usage: '先看大纲是否匹配目标，再进入 PPT 预览或下载文件。',
    primaryAction: '预览 PPT',
    actions: ['下载 PPT', '重生成大纲', '导出演讲稿'],
    questions: ['帮我检查 PPT 结构', '把这份 PPT 压缩成 5 页', '生成一段汇报讲稿'],
  },
  思维导图: {
    purpose: '用于理解知识结构和概念关系，适合查看全局脉络，不适合塞进文档预览。',
    usage: '打开导图后重点看概念之间的依赖、对比和分支关系。',
    primaryAction: '打开导图',
    actions: ['导出 PNG', '导出 XMind', '加入讲解参考'],
    questions: ['帮我解释这张导图', '把导图转成背诵提纲', '指出哪些分支最重要'],
  },
  代码案例: {
    purpose: '用可运行代码理解概念在程序里的表现，适合进入代码视图或下载案例包。',
    usage: '先预测输出，再运行或阅读解析，对照自己的理解偏差。',
    primaryAction: '打开代码案例',
    actions: ['下载 ZIP', '生成变体案例', '加入练习'],
    questions: ['逐行解释这个案例', '改成另一个例子', '根据案例出一道代码题'],
  },
}

const activeMeta = computed(() => activeResource.value ? metaMap[activeResource.value.group] : undefined)
const relatedStages = computed(() => {
  const group = activeResource.value?.group
  if (!group) return []
  const taskTypeMap: Partial<Record<LearningResource['group'], string[]>> = {
    个性化学习手册: ['资料', '讲解'],
    PPT: ['讲解'],
    思维导图: ['讲解'],
    代码案例: ['案例'],
  }
  const types = taskTypeMap[group] ?? []
  return plan.value.stages.filter((stage) => stage.tasks.some((task) => types.includes(task.type))).slice(0, 3)
})

function iconName(group: string) {
  if (group === 'PPT') return 'presentation'
  if (group === '思维导图') return 'mind-topic'
  if (group === '代码案例') return 'code'
  return 'file'
}

function selectResource(resource: LearningResource) {
  activeResourceId.value = resource.id
}

function runPrimaryAction() {
  if (activeResource.value?.status === '未选择') generateActiveResource()
}

function generateActiveResource() {
  if (activeResource.value) learningStore.generateResource(plan.value.id, activeResource.value.id)
}

watch(
  () => route.query.type,
  (type) => {
    const matched = plan.value.resources.find((item) => item.group === type)
    if (matched) activeResourceId.value = matched.id
  },
  { immediate: true },
)

onMounted(() => {
  readingTimer = window.setInterval(() => {
    if (sourceTaskId.value && document.visibilityState === 'visible') {
      learningStore.recordTaskReading(plan.value.id, sourceTaskId.value, 100, 1)
    }
  }, 1000)
})

onBeforeUnmount(() => {
  if (readingTimer) window.clearInterval(readingTimer)
})
</script>

<template>
  <LearningDetailShell
    eyebrow="学习资源"
    title="资源包"
    :subtitle="`${plan.title} · 独立查看、复用和导出学习资产`"
    :progress="plan.progress"
    @back="router.push(`/learning/${plan.id}`)"
  >
    <template #actions>
      <button class="outline-btn" type="button" @click="router.push({ path: `/learning/${plan.id}/study`, query: sourceTaskId ? { stage: sourceStageId, task: sourceTaskId } : {} })">{{ sourceTaskId ? '返回当前任务' : '继续学习' }}</button>
    </template>

    <template #navigation>
      <aside class="resource-list panel">
          <header class="panel-title">
            <div>
              <AppIcon name="folder" :size="20" />
              <h2>资源目录</h2>
            </div>
            <span>{{ plan.resources.length }} 项</span>
          </header>
          <button
            v-for="resource in plan.resources"
            :key="resource.id"
            class="resource-row"
            :class="{ active: resource.id === activeResource?.id }"
            type="button"
            @click="selectResource(resource)"
          >
            <AppIcon :name="iconName(resource.group)" :size="20" />
            <span>
              <strong>{{ resource.group }}</strong>
              <small>{{ resource.title }}</small>
            </span>
            <em :class="{ muted: resource.status === '未选择' }">{{ resource.status }}</em>
          </button>
          <p v-if="!plan.resources.length" class="empty-copy">当前项目还没有生成资源。</p>
        </aside>
    </template>

    <section class="resource-workspace panel">
          <header class="resource-head">
            <div>
              <span class="resource-icon">
                <AppIcon :name="iconName(activeResource?.group ?? '')" :size="24" />
              </span>
              <span>
                <h2>{{ activeResource?.title ?? '未选择资源' }}</h2>
                <p>{{ activeResource?.desc }}</p>
              </span>
            </div>
            <em :class="{ muted: activeResource?.status === '未选择' }">{{ activeResource?.status ?? '未选择' }}</em>
          </header>

          <div class="summary-strip">
            <article>
              <span>资源类型</span>
              <strong>{{ activeResource?.group ?? '-' }}</strong>
            </article>
            <article>
              <span>文件名</span>
              <strong>{{ activeResource?.fileName ?? '等待生成' }}</strong>
            </article>
            <article>
              <span>建议入口</span>
              <strong>{{ activeMeta?.primaryAction ?? '打开资源' }}</strong>
            </article>
          </div>

          <section v-if="activeResource?.group === '个性化学习手册'" class="resource-preview handbook-preview">
            <aside>
              <strong>本页目录</strong>
              <span>01 核心概念</span>
              <span>02 常见误区</span>
              <span>03 典型示例</span>
              <span>04 复习清单</span>
            </aside>
            <article>
              <span class="preview-label">个性化章节</span>
              <h3>{{ activeResource.title }}</h3>
              <p>{{ activeResource.desc }}</p>
              <h4>核心概念</h4>
              <p>{{ activeMeta?.purpose }}</p>
              <blockquote>先理解概念之间的关系，再用案例和练习验证自己的理解。</blockquote>
            </article>
          </section>

          <section v-else-if="activeResource?.group === 'PPT'" class="resource-preview ppt-preview">
            <aside>
              <button v-for="index in 4" :key="index" type="button"><span>{{ index }}</span><i>章节 {{ index }}</i></button>
            </aside>
            <article>
              <span class="preview-label">幻灯片 1 / 12</span>
              <h3>{{ activeResource.title }}</h3>
              <p>围绕学习目标建立章节结构，用于快速串讲和复述。</p>
              <div class="slide-points"><span>概念框架</span><span>关键区别</span><span>典型案例</span></div>
            </article>
          </section>

          <section v-else-if="activeResource?.group === '思维导图'" class="resource-preview mindmap-preview">
            <div class="mind-node mind-node--root">{{ plan.title }}</div>
            <div class="mind-branches">
              <span v-for="item in plan.dashboard" :key="item.label">{{ item.label }}</span>
            </div>
            <p>点击节点可继续展开概念关系和复习要点。</p>
          </section>

          <section v-else-if="activeResource?.group === '代码案例'" class="resource-preview code-preview">
            <header><span>Example.java</span><button type="button">运行案例</button></header>
            <pre><code>class Example {
  public static void main(String[] args) {
    // 先预测输出，再对照右侧解析
    System.out.println("Learning by doing");
  }
}</code></pre>
            <p>{{ activeMeta?.usage }}</p>
          </section>

          <section class="action-panel">
            <h3>可执行操作</h3>
            <div class="primary-row">
              <button class="primary-btn" type="button" @click="runPrimaryAction">
                <AppIcon name="play" :size="16" />
                {{ activeMeta?.primaryAction ?? '打开资源' }}
              </button>
              <button
                class="outline-btn"
                type="button"
                :disabled="activeResource?.status === '生成中'"
                @click="generateActiveResource"
              >
                <AppIcon name="refresh" :size="16" />
                {{ activeResource?.status === '生成中' ? '生成中...' : activeResource?.status === '未选择' ? '生成资源' : '重新生成' }}
              </button>
            </div>
            <div class="secondary-actions">
              <button v-for="action in activeMeta?.actions" :key="action" type="button">{{ action }}</button>
            </div>
          </section>

          <section class="related-panel">
            <h3>关联学习阶段</h3>
            <button
              v-for="stage in relatedStages"
              :key="stage.id"
              type="button"
              @click="router.push(`/learning/${plan.id}/study?stage=${stage.id}`)"
            >
              <span>阶段 {{ stage.id }}</span>
              <strong>{{ stage.title }}</strong>
              <AppIcon name="chevron-right" :size="15" />
            </button>
            <p v-if="!relatedStages.length">当前资源暂未关联到具体阶段。</p>
          </section>
    </section>

    <template #aside>
      <aside class="agent-panel panel">
          <header>
            <span class="agent-avatar"><AppIcon name="brain" :size="20" /></span>
            <div>
              <h2>AI 资源助教</h2>
              <p>围绕当前选中的 {{ activeResource?.group ?? '资源' }} 解答问题。</p>
            </div>
          </header>

          <div class="agent-context">
            <strong>当前上下文</strong>
            <span>{{ activeResource?.title }}</span>
            <small>{{ activeResource?.fileName ?? '尚未生成文件名' }}</small>
          </div>

          <div class="quick-questions">
            <button v-for="question in activeMeta?.questions" :key="question" type="button">
              {{ question }}
            </button>
          </div>

          <label class="ask-box">
            <textarea placeholder="问问当前资源，比如：这份材料应该先看哪部分？" />
            <button type="button">
              <AppIcon name="send" :size="17" />
            </button>
          </label>
      </aside>
    </template>
  </LearningDetailShell>
</template>

<style scoped>
.resources-page {
  min-height: 100%;
  padding: 28px 34px 42px;
  background: #f6f7f9;
}

.resources-page,
.resources-page * {
  box-sizing: border-box;
}

h1,
h2,
h3,
p {
  margin: 0;
}

button,
textarea {
  font: inherit;
}

.empty-copy {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}

.resource-preview {
  min-height: 320px;
  margin-top: 16px;
  border: 1px solid #e1e5ec;
  border-radius: 10px;
  background: #f8fafc;
  overflow: hidden;
}

.preview-label {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.handbook-preview,
.ppt-preview {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
}

.handbook-preview > aside,
.ppt-preview > aside {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border-right: 1px solid #e1e5ec;
  background: #f1f5f9;
}

.handbook-preview > aside span {
  color: #64748b;
  font-size: 13px;
}

.handbook-preview > article,
.ppt-preview > article {
  padding: 28px;
}

.handbook-preview h3,
.ppt-preview h3 {
  margin-top: 8px;
  font-size: 23px;
}

.handbook-preview h4 {
  margin: 24px 0 8px;
}

.handbook-preview p,
.ppt-preview p,
.code-preview p {
  margin-top: 10px;
  color: #475569;
  line-height: 1.75;
}

.handbook-preview blockquote {
  margin: 22px 0 0;
  padding: 14px 16px;
  border-left: 3px solid #2563eb;
  background: #eff6ff;
  color: #334155;
}

.ppt-preview > aside button {
  min-height: 70px;
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 6px;
  align-items: center;
  border: 1px solid #dbe3ec;
  border-radius: 7px;
  background: #fff;
  color: #334155;
  cursor: pointer;
}

.slide-points {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 42px;
}

.slide-points span {
  min-height: 84px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #dbeafe;
  color: #1d4ed8;
  font-weight: 800;
}

.mindmap-preview {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 24px;
  padding: 32px;
  background-image: radial-gradient(#cbd5e1 1px, transparent 1px);
  background-size: 18px 18px;
}

.mind-node,
.mind-branches span {
  border: 2px solid #60a5fa;
  border-radius: 10px;
  background: #fff;
  color: #1e3a8a;
  padding: 12px 18px;
  font-weight: 800;
}

.mind-node--root {
  background: #2563eb;
  color: #fff;
}

.mind-branches {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 22px;
}

.mindmap-preview p {
  color: #64748b;
}

.code-preview header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #1e293b;
  color: #e2e8f0;
}

.code-preview header button {
  border: 0;
  border-radius: 6px;
  background: #22c55e;
  color: #052e16;
  padding: 7px 12px;
  cursor: pointer;
  font-weight: 800;
}

.code-preview pre {
  min-height: 210px;
  margin: 0;
  padding: 24px;
  background: #0f172a;
  color: #dbeafe;
  line-height: 1.7;
  overflow: auto;
}

.code-preview p {
  padding: 0 20px 18px;
}

.page-head,
.resource-layout {
  max-width: 1380px;
  margin-left: auto;
  margin-right: auto;
}

.page-head {
  display: grid;
  grid-template-columns: 42px 1fr;
  align-items: center;
  gap: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
  padding: 16px;
}

.page-head > button {
  width: 42px;
  height: 42px;
  border: 1px solid #d8dde5;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  cursor: pointer;
}

h1 {
  color: #111827;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0;
}

.page-head p {
  margin-top: 7px;
  color: #6b7280;
}

.resource-layout {
  margin-top: 18px;
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.panel {
  border: 1px solid #e1e5ec;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.06);
  padding: 18px;
}

h2 {
  color: #111827;
  font-size: 18px;
  font-weight: 800;
}

h3 {
  color: #111827;
  font-size: 15px;
  font-weight: 800;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-title > div {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #2563eb;
}

.panel-title > span {
  color: #6b7280;
  font-size: 13px;
}

.resource-row {
  width: 100%;
  min-height: 68px;
  margin-top: 8px;
  border: 1px solid #e1e5ec;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  text-align: left;
  cursor: pointer;
}

.resource-row.active,
.resource-row:hover {
  border-color: #93c5fd;
  background: #eff6ff;
  box-shadow: inset 3px 0 0 #2563eb;
}

.resource-row > span {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.resource-row strong,
.resource-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-row small {
  color: #6b7280;
}

.resource-row em,
.resource-head em {
  border-radius: 6px;
  background: #dcfce7;
  color: #15803d;
  padding: 4px 8px;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.resource-row em.muted,
.resource-head em.muted {
  background: #f3f4f6;
  color: #6b7280;
}

.resource-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid #edf0f4;
  padding-bottom: 16px;
}

.resource-head > div {
  min-width: 0;
  display: flex;
  gap: 12px;
}

.resource-icon {
  width: 46px;
  height: 46px;
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.resource-head span {
  min-width: 0;
}

.resource-head p {
  margin-top: 5px;
  color: #6b7280;
  line-height: 1.5;
}

.summary-strip {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.summary-strip article {
  min-height: 68px;
  border: 1px solid #e1e5ec;
  border-radius: 8px;
  background: #f8fafc;
  padding: 10px 12px;
  display: grid;
  gap: 5px;
  align-content: center;
}

.summary-strip span {
  color: #6b7280;
  font-size: 12px;
  font-weight: 800;
}

.summary-strip strong {
  min-width: 0;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.intent-card,
.action-panel,
.related-panel,
.agent-context {
  margin-top: 16px;
  border: 1px solid #e1e5ec;
  border-radius: 8px;
  background: #f8fafc;
  padding: 16px;
}

.intent-card p {
  margin: 8px 0 14px;
  color: #374151;
  line-height: 1.7;
}

.intent-card p:last-child {
  margin-bottom: 0;
}

.primary-row,
.secondary-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.primary-row {
  margin-top: 12px;
}

.secondary-actions {
  margin-top: 10px;
}

.primary-btn,
.outline-btn,
.secondary-actions button {
  height: 38px;
  border-radius: 8px;
  padding: 0 13px;
  cursor: pointer;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.primary-btn {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #fff;
}

.outline-btn,
.secondary-actions button {
  border: 1px solid #d8dde5;
  background: #ffffff;
  color: #111827;
}

.outline-btn:hover,
.secondary-actions button:hover,
.related-panel button:hover,
.quick-questions button:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.related-panel {
  display: grid;
  gap: 8px;
}

.related-panel button {
  min-height: 42px;
  border: 1px solid #e1e5ec;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  cursor: pointer;
  text-align: left;
}

.related-panel button span {
  color: #2563eb;
  font-weight: 800;
}

.related-panel button strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-panel p {
  color: #6b7280;
}

.agent-panel header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.agent-avatar {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  display: grid;
  place-items: center;
}

.agent-panel header p {
  margin-top: 4px;
  color: #6b7280;
  line-height: 1.4;
}

.agent-context {
  display: grid;
  gap: 6px;
}

.agent-context strong {
  color: #2563eb;
}

.agent-context span {
  color: #111827;
  font-weight: 800;
}

.agent-context small {
  color: #6b7280;
}

.quick-questions {
  margin-top: 14px;
  display: grid;
  gap: 8px;
}

.quick-questions button {
  min-height: 38px;
  border: 1px solid #e1e5ec;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  text-align: left;
  padding: 8px 10px;
  cursor: pointer;
}

.ask-box {
  min-height: 112px;
  margin-top: 14px;
  border: 1px solid #d8dde5;
  border-radius: 8px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 32px;
  align-items: end;
  gap: 8px;
  padding: 10px;
}

.ask-box textarea {
  min-width: 0;
  min-height: 88px;
  border: 0;
  outline: 0;
  resize: none;
  background: transparent;
  color: #111827;
}

.ask-box button {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: #2563eb;
  color: #fff;
  cursor: pointer;
}

@media (max-width: 1260px) {
  .resource-layout {
    grid-template-columns: 290px minmax(0, 1fr);
  }

  .agent-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 900px) {
  .page-head,
  .resource-layout {
    grid-template-columns: 1fr;
  }

  .agent-panel {
    grid-column: auto;
  }

  .summary-strip {
    grid-template-columns: 1fr;
  }
}
</style>
