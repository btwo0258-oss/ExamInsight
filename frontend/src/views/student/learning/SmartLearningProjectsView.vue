<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import StudentShell from '@/components/layout/StudentShell.vue'
import { listKnowledgeBases } from '@/api/assetLibraryV2'
import type { KnowledgeBase } from '@/types/contracts/assetLibraryV2'
import type { SmartLearningProject, SmartLearningStage } from '@/types/contracts/smartLearning'
import { useSmartLearningStore } from '@/stores/smartLearning'

const router = useRouter()
const store = useSmartLearningStore()
const keyword = ref('')
const viewMode = ref<'grid' | 'list'>(localStorage.getItem('examinsight.ui.learning-project-view') === 'list' ? 'list' : 'grid')
const knowledgeBases = ref<KnowledgeBase[]>([])
const createOpen = ref(false)
const createName = ref('')
const createKnowledgeBaseId = ref<string | null>(null)
const actionError = ref('')

const entryCards = [
  { title: '制定学习计划', description: '从目标、时间和资料开始，形成一份可执行的学习计划。', icon: 'calendar' },
  { title: '分析学习资料', description: '从教材、笔记和讲义中整理需要掌握的内容。', icon: 'file' },
  { title: '分析试卷或真题', description: '识别题型、重点和薄弱范围，安排后续诊断。', icon: 'file' },
  { title: '完成基础诊断', description: '用一轮短诊断了解当前水平，再决定学习重点。', icon: 'bar-chart' },
]

const filteredProjects = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return store.projects.filter(project => !query || project.name.toLowerCase().includes(query))
})

function stageLabel(stage: SmartLearningStage) {
  const labels: Record<SmartLearningStage, string> = {
    TARGET_REQUIRED: '待填写目标',
    SOURCES_REQUIRED: '待选择资料',
    SCOPE_REQUIRED: '待确认范围',
    DIAGNOSTIC_REQUIRED: '待完成诊断',
    PLAN_REQUIRED: '待确认计划',
    RESOURCE_CONFIG_REQUIRED: '待配置资源',
    READY: '准备完成',
    ARCHIVED: '已归档',
  }
  return labels[stage]
}

function completedCount(project: SmartLearningProject) {
  return [project.targetVersion, project.sourceVersion, project.scopeVersion,
    project.diagnosisVersion, project.planVersion, project.resourceConfigVersion]
    .filter(version => version > 0).length
}

function openCreate() {
  actionError.value = ''
  createName.value = ''
  createKnowledgeBaseId.value = null
  createOpen.value = true
}

async function createProject() {
  const name = createName.value.trim()
  if (!name) return
  try {
    const project = await store.create({ name, knowledgeBaseId: createKnowledgeBaseId.value })
    createOpen.value = false
    await router.push(`/learning/${project.projectId}/setup`)
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '创建学习项目失败。'
  }
}

async function rename(project: SmartLearningProject) {
  const name = window.prompt('修改项目名称', project.name)?.trim()
  if (!name || name === project.name) return
  try { await store.rename(project.projectId, name) } catch (error) {
    actionError.value = error instanceof Error ? error.message : '重命名失败。'
  }
}

async function archive(project: SmartLearningProject) {
  if (!window.confirm(`确定归档“${project.name}”吗？项目资料不会被删除。`)) return
  try { await store.archive(project.projectId) } catch (error) {
    actionError.value = error instanceof Error ? error.message : '归档失败。'
  }
}

async function load() {
  await Promise.allSettled([
    store.fetchProjects(),
    listKnowledgeBases('library', null, 100).then(page => { knowledgeBases.value = page.items }),
  ])
}

function continueProject(project: SmartLearningProject) {
  void router.push(`/learning/${project.projectId}/setup`)
}

onMounted(() => void load())
</script>

<template>
  <StudentShell>
    <div class="learning-projects-page">
      <header class="page-header">
        <div>
          <span class="eyebrow">智能学习</span>
          <h1>学习项目</h1>
          <p>从目标、资料和诊断开始，准备一份属于你的学习计划。</p>
        </div>
        <div class="header-actions">
          <label class="search-field">
            <AppIcon name="search" :size="17" />
            <input v-model="keyword" placeholder="搜索项目" />
          </label>
          <div class="view-switch" aria-label="展示方式">
            <button type="button" :class="{ active: viewMode === 'grid' }" title="网格展示" @click="viewMode = 'grid'">
              <AppIcon name="grid" :size="17" />
            </button>
            <button type="button" :class="{ active: viewMode === 'list' }" title="列表展示" @click="viewMode = 'list'">
              <AppIcon name="list" :size="17" />
            </button>
          </div>
          <button class="primary-button" type="button" @click="openCreate">
            <AppIcon name="plus" :size="17" /> 新建学习
          </button>
        </div>
      </header>

      <section class="entry-grid" aria-label="开始方式">
        <button v-for="card in entryCards" :key="card.title" class="entry-card" type="button" @click="openCreate">
          <span class="entry-icon"><AppIcon :name="card.icon" :size="19" /></span>
          <span class="entry-copy"><strong>{{ card.title }}</strong><small>{{ card.description }}</small></span>
          <AppIcon class="entry-arrow" name="chevron-right" :size="17" />
        </button>
      </section>

      <section v-if="store.errorMessage || actionError" class="page-error" role="alert">
        <AppIcon name="alert-circle" :size="17" />
        <span>{{ actionError || store.errorMessage }}</span>
        <button type="button" @click="actionError = ''; load()">重试</button>
      </section>

      <section class="project-section">
        <div class="section-heading">
          <div><h2>我的学习项目</h2><span>{{ store.projects.length }} 个项目</span></div>
          <button v-if="store.projects.length" type="button" class="text-button" @click="openCreate">新建项目</button>
        </div>
        <div v-if="store.loading && !store.projects.length" class="empty-state"><span class="spinner" />正在加载学习项目…</div>
        <div v-else-if="!filteredProjects.length" class="empty-state">
          <AppIcon name="book" :size="28" />
          <strong>{{ keyword ? '没有匹配的项目' : '还没有学习项目' }}</strong>
          <span>{{ keyword ? '换个关键词试试。' : '创建第一个项目，开始准备学习。' }}</span>
          <button v-if="!keyword" class="primary-button" type="button" @click="openCreate">新建学习</button>
        </div>
        <div v-else class="project-grid" :class="`project-grid--${viewMode}`">
          <article v-for="project in filteredProjects" :key="project.projectId" class="project-card">
            <header class="project-card-header">
              <span class="project-mark" :style="{ color: project.iconColor }"><AppIcon :name="project.icon || 'book'" :size="19" /></span>
              <div><h3 :title="project.name">{{ project.name }}</h3><span class="stage-pill">{{ stageLabel(project.stage) }}</span></div>
              <button class="icon-button" type="button" title="项目操作" @click="rename(project)"><AppIcon name="edit" :size="16" /></button>
            </header>
            <p class="next-step">下一步：{{ project.nextStep }}</p>
            <div class="project-progress"><span><b>{{ completedCount(project) }}</b> / 6 步已确认</span><i><b :style="{ width: `${completedCount(project) / 6 * 100}%` }" /></i></div>
            <p class="project-updated">最近修改 {{ new Date(project.updatedAt).toLocaleString() }}</p>
            <footer class="project-card-footer">
              <button class="primary-button" type="button" @click="continueProject(project)">{{ project.stage === 'READY' ? '查看准备结果' : '继续准备' }}</button>
              <button class="text-button danger-button" type="button" @click="archive(project)">归档</button>
            </footer>
          </article>
        </div>
      </section>
    </div>

    <div v-if="createOpen" class="modal-backdrop" @click.self="createOpen = false">
      <section class="create-modal" role="dialog" aria-modal="true" aria-labelledby="create-learning-title">
        <header><div><span class="eyebrow">新建学习项目</span><h2 id="create-learning-title">先把目标放在一起</h2></div><button class="icon-button" type="button" aria-label="关闭" @click="createOpen = false"><AppIcon name="close" :size="19" /></button></header>
        <label class="form-field"><span>项目名称</span><input v-model="createName" autofocus maxlength="160" placeholder="例如：高数期末复习" @keyup.enter="createProject" /></label>
        <label class="form-field"><span>初始知识库 <small>可选，默认不关联</small></span><select v-model="createKnowledgeBaseId"><option :value="null">不关联知识库</option><option v-for="kb in knowledgeBases" :key="kb.knowledgeBaseId" :value="kb.knowledgeBaseId">{{ kb.name }}</option></select></label>
        <p class="modal-note">创建后可以再选择具体资料。这里只保存项目，不会等待 AI 生成。</p>
        <footer><button class="secondary-button" type="button" @click="createOpen = false">取消</button><button class="primary-button" type="button" :disabled="!createName.trim() || store.saving" @click="createProject">{{ store.saving ? '创建中…' : '创建并继续' }}</button></footer>
      </section>
    </div>
  </StudentShell>
</template>

<style scoped>
.learning-projects-page { min-height: 100%; padding: 34px 36px 70px; background: var(--color-bg); color: var(--color-text); }
.page-header, .project-section, .entry-grid { width: min(1320px, 100%); margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; }
.eyebrow { display: block; color: var(--color-primary); font-size: 12px; font-weight: 800; letter-spacing: .08em; }
h1, h2, h3, p { margin: 0; } h1 { margin-top: 4px; font-size: 31px; line-height: 1.2; } .page-header p { margin-top: 8px; color: var(--color-text-muted); }
.header-actions { display: flex; align-items: center; gap: 10px; }
.search-field { width: 206px; height: 42px; display: flex; align-items: center; gap: 8px; padding: 0 12px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-surface); color: var(--color-text-muted); }
.search-field input { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; color: var(--color-text); }
button { font: inherit; cursor: pointer; } button:disabled { cursor: not-allowed; opacity: .55; }
.primary-button, .secondary-button { min-height: 40px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; padding: 0 15px; border-radius: 8px; font-weight: 700; }
.primary-button { border: 1px solid var(--color-primary); background: var(--color-primary); color: var(--color-on-primary); } .secondary-button { border: 1px solid var(--color-border); background: var(--color-surface); color: var(--color-text); }
.view-switch { height: 42px; display: flex; gap: 3px; padding: 3px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-surface); }
.view-switch button, .icon-button { border: 0; background: transparent; color: var(--color-text-muted); } .view-switch button { width: 34px; border-radius: 6px; } .view-switch button.active, .view-switch button:hover { background: var(--color-hover); color: var(--color-text); }
.entry-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 13px; margin-top: 30px; }
.entry-card { min-height: 116px; position: relative; display: flex; align-items: flex-start; gap: 12px; padding: 18px; border: 1px solid var(--color-border); border-radius: 9px; background: var(--color-surface); color: var(--color-text); text-align: left; transition: border-color .15s, transform .15s; }
.entry-card:hover { border-color: var(--color-text-muted); transform: translateY(-1px); } .entry-icon { width: 34px; height: 34px; display: grid; place-items: center; flex: 0 0 34px; border-radius: 8px; background: var(--color-hover); color: var(--color-primary); }
.entry-copy { display: grid; gap: 6px; min-width: 0; } .entry-copy strong { font-size: 15px; } .entry-copy small { color: var(--color-text-muted); line-height: 1.5; } .entry-arrow { position: absolute; right: 14px; bottom: 15px; color: var(--color-text-muted); }
.project-section { margin-top: 38px; } .section-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; } .section-heading > div { display: flex; align-items: baseline; gap: 10px; } .section-heading h2 { font-size: 20px; } .section-heading span { color: var(--color-text-muted); font-size: 13px; }
.text-button { border: 0; background: transparent; color: var(--color-text-muted); } .text-button:hover { color: var(--color-text); } .danger-button:hover { color: var(--color-danger); }
.project-grid { display: flex; flex-wrap: wrap; gap: 15px; } .project-grid--list { display: grid; grid-template-columns: 1fr; }
.project-card { min-width: 0; flex: 0 0 calc((100% - 45px) / 4); padding: 18px; border: 1px solid var(--color-border); border-radius: 9px; background: var(--color-surface); box-shadow: var(--shadow-sm); }
.project-grid--list .project-card { width: 100%; flex-basis: auto; } .project-card-header { display: flex; align-items: flex-start; gap: 10px; } .project-mark { width: 36px; height: 36px; display: grid; place-items: center; flex: 0 0 36px; border-radius: 9px; background: var(--color-hover); } .project-card-header > div { min-width: 0; flex: 1; } h3 { overflow: hidden; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; } .stage-pill { display: inline-block; margin-top: 6px; padding: 3px 7px; border-radius: 5px; background: var(--color-hover); color: var(--color-text-muted); font-size: 12px; }
.icon-button { width: 30px; height: 30px; display: grid; place-items: center; flex: 0 0 30px; border-radius: 6px; } .icon-button:hover { background: var(--color-hover); color: var(--color-text); }
.next-step { min-height: 42px; margin-top: 16px; color: var(--color-text); line-height: 1.5; } .project-progress { margin-top: 12px; color: var(--color-text-muted); font-size: 12px; } .project-progress > span b { color: var(--color-text); } .project-progress i { display: block; height: 5px; margin-top: 7px; overflow: hidden; border-radius: 99px; background: var(--color-border); } .project-progress i b { display: block; height: 100%; border-radius: inherit; background: var(--color-primary); } .project-updated { margin-top: 13px; color: var(--color-text-muted); font-size: 12px; }
.project-card-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 17px; } .project-card-footer .primary-button { min-height: 35px; padding-inline: 11px; font-size: 13px; }
.empty-state { min-height: 260px; display: grid; place-items: center; align-content: center; gap: 9px; color: var(--color-text-muted); text-align: center; } .empty-state strong { color: var(--color-text); } .empty-state .primary-button { margin-top: 6px; } .spinner { width: 20px; height: 20px; border: 2px solid var(--color-border); border-top-color: var(--color-primary); border-radius: 50%; animation: spin .8s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
.page-error { width: min(1320px, 100%); display: flex; align-items: center; gap: 8px; margin: 20px auto 0; padding: 11px 13px; border: 1px solid color-mix(in srgb, var(--color-danger) 30%, var(--color-border)); border-radius: 8px; background: color-mix(in srgb, var(--color-danger) 7%, var(--color-surface)); color: var(--color-danger); } .page-error span { flex: 1; } .page-error button { border: 0; background: transparent; color: inherit; }
.modal-backdrop { position: fixed; inset: 0; z-index: 100; display: grid; place-items: center; padding: 20px; background: color-mix(in srgb, #111 34%, transparent); } .create-modal { width: min(500px, 100%); padding: 24px; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-surface); box-shadow: var(--shadow-lg); } .create-modal header { display: flex; justify-content: space-between; align-items: flex-start; } .create-modal h2 { margin-top: 4px; font-size: 22px; } .form-field { display: grid; gap: 7px; margin-top: 22px; color: var(--color-text); font-weight: 700; } .form-field small { color: var(--color-text-muted); font-weight: 400; } .form-field input, .form-field select { height: 42px; padding: 0 11px; border: 1px solid var(--color-border); border-radius: 7px; outline: 0; background: var(--color-bg); color: var(--color-text); } .form-field input:focus, .form-field select:focus { border-color: var(--color-primary); } .modal-note { margin-top: 15px; color: var(--color-text-muted); font-size: 13px; line-height: 1.5; } .create-modal footer { display: flex; justify-content: flex-end; gap: 9px; margin-top: 25px; }
@media (max-width: 1100px) { .entry-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .project-card { flex-basis: calc((100% - 15px) / 2); } }
@media (max-width: 760px) { .learning-projects-page { padding: 22px 16px 56px; } .page-header { display: block; } .header-actions { margin-top: 20px; flex-wrap: wrap; } .search-field { flex: 1; width: auto; } .entry-grid { grid-template-columns: 1fr; margin-top: 24px; } .project-card { flex-basis: 100%; } }
</style>
