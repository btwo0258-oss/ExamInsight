<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import LearningProjectModal from '@/components/learning/LearningProjectModal.vue'
import { learningErrorMessage } from '@/utils/learningErrors'
import StudentShell from '@/components/layout/StudentShell.vue'
import { listKnowledgeBases } from '@/api/assetLibraryV2'
import type { KnowledgeBase } from '@/types/contracts/assetLibraryV2'
import type { SmartLearningProject, SmartLearningStage } from '@/types/contracts/smartLearning'
import { useSmartLearningStore } from '@/stores/smartLearning'

const route = useRoute()
const router = useRouter()
const store = useSmartLearningStore()
const keyword = ref('')
const viewMode = ref<'grid' | 'list'>('grid')
const knowledgeBases = ref<KnowledgeBase[]>([])
const createOpen = ref(false)
const editingProject = ref<SmartLearningProject | null>(null)
const modalBusy = ref(false)
const modalError = ref('')
const actionError = ref('')
const kbError = ref('')
const kbLoading = ref(false)
const kbCursor = ref<string | null>(null)

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
  editingProject.value = null
  modalError.value = ''
  createOpen.value = true
}
function closeCreate() {
  if (modalBusy.value) return
  createOpen.value = false
  modalError.value = ''
}
function rename(project: SmartLearningProject) {
  editingProject.value = project
  modalError.value = ''
  createOpen.value = true
}
async function saveProject(payload: { name: string; icon: string; iconColor: string; knowledgeBaseId?: string | null }) {
  if (modalBusy.value) return
  modalBusy.value = true
  modalError.value = ''
  try {
    if (editingProject.value) {
      await store.rename(editingProject.value.projectId, payload)
      createOpen.value = false
    } else {
      const project = await store.create(payload)
      createOpen.value = false
      await router.push('/learning/' + project.projectId + '/setup')
    }
  } catch (error) { modalError.value = learningErrorMessage(error, '项目未保存，请重试。') }
  finally { modalBusy.value = false }
}
async function loadKnowledgeBases(more = false) {
  if (kbLoading.value || more && !kbCursor.value) return
  kbLoading.value = true
  kbError.value = ''
  try {
    const page = await listKnowledgeBases('library', more ? kbCursor.value : null, 100)
    knowledgeBases.value = more ? [...knowledgeBases.value, ...page.items] : page.items
    kbCursor.value = page.nextCursor
  } catch (error) { kbError.value = learningErrorMessage(error, '知识库列表未能加载，仍可不关联知识库创建项目。') }
  finally { kbLoading.value = false }
}
async function load() { await Promise.allSettled([store.fetchProjects(), loadKnowledgeBases()]) }

function continueProject(project: SmartLearningProject) {
  void router.push(project.stage === 'READY' ? `/learning/${project.projectId}` : `/learning/${project.projectId}/setup`)
}

watch(() => route.query.create, (value) => {
  if (value !== '1') return
  openCreate()
  void router.replace({ name: 'learning-projects', query: { ...route.query, create: undefined } })
}, { immediate: true })

onMounted(() => void load())
</script>

<template>
  <StudentShell compact-on-mobile>
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
            <button type="button" :class="{ active: viewMode === 'grid' }" :aria-pressed="viewMode === 'grid'" title="网格展示" @click="viewMode = 'grid'">
              <AppIcon name="grid" :size="17" />
            </button>
            <button type="button" :class="{ active: viewMode === 'list' }" :aria-pressed="viewMode === 'list'" title="列表展示" @click="viewMode = 'list'">
              <AppIcon name="list" :size="17" />
            </button>
          </div>
          <button class="primary-button" type="button" @click="openCreate">
            <AppIcon name="plus" :size="17" /> 新建学习
          </button>
        </div>
      </header>

      <section v-if="!createOpen && (store.errorMessage || actionError)" class="page-error" role="alert">
        <AppIcon name="alert-circle" :size="17" />
        <span>{{ actionError || store.errorMessage }}</span>
        <button type="button" @click="actionError = ''; load()">重试</button>
      </section>

      <section class="project-section">
        <div class="section-heading">
          <div><h2>我的学习项目</h2><span>{{ store.projects.length }} 个项目</span></div>
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
              <span class="project-mark" :style="{ color: project.iconColor === '#667085' ? 'var(--color-text)' : project.iconColor }"><AppIcon :name="project.icon || 'notebook'" :size="19" /></span>
              <div><h3 :title="project.name">{{ project.name }}</h3><span class="stage-pill">{{ stageLabel(project.stage) }}</span></div>
              <button v-if="viewMode === 'grid'" class="icon-button" type="button" title="修改项目" aria-label="修改项目" @click="rename(project)"><AppIcon name="edit" :size="16" /></button>
            </header>
            <div class="project-card-body">
              <p class="next-step">下一步：{{ project.nextStep }}</p>
              <div class="project-progress"><span><b>{{ completedCount(project) }}</b> / 6 步已确认</span><i><b :style="{ width: `${completedCount(project) / 6 * 100}%` }" /></i></div>
              <p class="project-updated" :title="`最近修改 ${new Date(project.updatedAt).toLocaleString()}`">最近修改 {{ new Date(project.updatedAt).toLocaleString() }}</p>
            </div>
            <footer class="project-card-footer">
              <button v-if="viewMode === 'list'" class="icon-button" type="button" title="修改项目" aria-label="修改项目" @click="rename(project)"><AppIcon name="edit" :size="16" /></button>
              <button class="primary-button" type="button" @click="continueProject(project)">{{ project.stage === 'READY' ? '进入学习工作台' : '继续准备' }}</button>
            </footer>
          </article>
        </div>
      </section>
    </div>

    <LearningProjectModal v-if="createOpen" :project="editingProject" :knowledge-bases="knowledgeBases"
      :busy="modalBusy" :error="modalError" :kb-error="kbError" :kb-loading="kbLoading" :has-more-knowledge-bases="Boolean(kbCursor)"
      @close="closeCreate" @submit="saveProject" @retry-knowledge-bases="loadKnowledgeBases()" @more-knowledge-bases="loadKnowledgeBases(true)" />
  </StudentShell>
</template>

<style scoped>
.learning-projects-page,
.learning-projects-page * { box-sizing: border-box; }
.learning-projects-page { min-height: 100%; padding: 34px 36px 70px; background: var(--color-bg); color: var(--color-text); }
.page-header, .project-section { width: min(1320px, 100%); margin: 0 auto; }
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
.project-section { margin-top: 38px; } .section-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; } .section-heading > div { display: flex; align-items: baseline; gap: 10px; } .section-heading h2 { font-size: 20px; } .section-heading span { color: var(--color-text-muted); font-size: 13px; }
.text-button { border: 0; background: transparent; color: var(--color-text-muted); } .text-button:hover { color: var(--color-text); } .danger-button:hover { color: var(--color-danger); }
.project-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 15px; }
.project-grid--list { grid-template-columns: minmax(0, 1fr); gap: 10px; }
.project-card { min-width: 0; padding: 18px; border: 1px solid var(--color-border); border-radius: 9px; background: var(--color-surface); box-shadow: var(--shadow-sm); }
.project-card-header { min-width: 0; display: flex; align-items: flex-start; gap: 10px; } .project-mark { width: 36px; height: 36px; display: grid; place-items: center; flex: 0 0 36px; border-radius: 9px; background: var(--color-hover); } .project-card-header > div { min-width: 0; flex: 1; } h3 { overflow: hidden; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; } .stage-pill { display: inline-block; max-width: 100%; margin-top: 6px; padding: 3px 7px; overflow: hidden; border-radius: 5px; background: var(--color-hover); color: var(--color-text-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.icon-button { width: 30px; height: 30px; display: grid; place-items: center; flex: 0 0 30px; border-radius: 6px; } .icon-button:hover { background: var(--color-hover); color: var(--color-text); }
.next-step { min-height: 42px; margin-top: 16px; overflow-wrap: anywhere; color: var(--color-text); line-height: 1.5; } .project-progress { margin-top: 12px; color: var(--color-text-muted); font-size: 12px; } .project-progress > span b { color: var(--color-text); } .project-progress i { display: block; height: 5px; margin-top: 7px; overflow: hidden; border-radius: 99px; background: var(--color-border); } .project-progress i b { display: block; height: 100%; border-radius: inherit; background: var(--color-primary); } .project-updated { margin-top: 13px; overflow-wrap: anywhere; color: var(--color-text-muted); font-size: 12px; }
.project-card-body { min-width: 0; }
.project-grid--grid .project-card { display: flex; flex-direction: column; }
.project-grid--grid .project-card-body { flex: 1; }
.project-card-footer { display: flex; align-items: center; justify-content: flex-end; gap: 8px; margin-top: 17px; } .project-card-footer .primary-button { min-width: 0; min-height: 35px; padding-inline: 11px; font-size: 13px; } .project-card-footer .danger-button { flex: 0 0 auto; }
.project-section { container-type: inline-size; container-name: learning-projects; }
.project-grid--list .project-card {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(260px, 1.5fr) auto;
  align-items: center;
  gap: 24px;
  min-height: 96px;
  padding: 18px 20px;
  border-radius: 10px;
  box-shadow: none;
}
.project-grid--list .project-card-header { align-items: center; }
.project-grid--list .project-card-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  grid-template-areas: 'next progress' 'updated progress';
  align-items: center;
  gap: 6px 24px;
}
.project-grid--list .next-step { grid-area: next; min-height: 0; margin: 0; font-size: 14px; }
.project-grid--list .project-progress { grid-area: progress; margin: 0; }
.project-grid--list .project-updated { grid-area: updated; margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.project-grid--list .project-card-footer { margin: 0; flex-wrap: nowrap; }
.project-grid--list .project-card-footer .primary-button { white-space: nowrap; }
.empty-state { min-height: 260px; display: grid; place-items: center; align-content: center; gap: 9px; color: var(--color-text-muted); text-align: center; } .empty-state strong { color: var(--color-text); } .empty-state .primary-button { margin-top: 6px; } .spinner { width: 20px; height: 20px; border: 2px solid var(--color-border); border-top-color: var(--color-primary); border-radius: 50%; animation: spin .8s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
.page-error { width: min(1320px, 100%); display: flex; align-items: center; gap: 8px; margin: 20px auto 0; padding: 11px 13px; border: 1px solid color-mix(in srgb, var(--color-danger) 30%, var(--color-border)); border-radius: 8px; background: color-mix(in srgb, var(--color-danger) 7%, var(--color-surface)); color: var(--color-danger); } .page-error span { flex: 1; min-width: 0; overflow-wrap: anywhere; } .page-error button { border: 0; background: transparent; color: inherit; }
@media (max-width: 1100px) { .project-grid--grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) { .learning-projects-page { padding: 22px 16px 56px; } .page-header { display: block; } .header-actions { margin-top: 20px; flex-wrap: wrap; } .search-field { flex: 1; width: auto; } .project-grid { grid-template-columns: minmax(0, 1fr); } }
@container learning-projects (max-width: 900px) {
  .project-grid--list .project-card { grid-template-columns: minmax(0, 1fr) auto; gap: 14px 20px; }
  .project-grid--list .project-card-header { grid-column: 1; grid-row: 1; }
  .project-grid--list .project-card-footer { grid-column: 2; grid-row: 1; }
  .project-grid--list .project-card-body { grid-column: 1 / -1; grid-row: 2; }
}
@container learning-projects (max-width: 480px) {
  .project-grid--list .project-card { padding: 14px; gap: 12px; }
  .project-grid--list .project-card-body { grid-template-columns: minmax(0, 1fr) 100px; column-gap: 14px; }
  .project-grid--list .project-card-footer { gap: 4px; }
}
</style>
