<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import logoUrl from '@/assets/icons/ExamInsight-Logo.png'
import { learningPlans, recentConversations } from '@/mock'
import { useAuthStore } from '@/stores/auth'
import { useConversationStore } from '@/stores/conversation'

type MenuAction = {
  label: string
  icon: string
  action: () => void
  danger?: boolean
  divided?: boolean
}

type SidebarProject = (typeof learningPlans)[number] & {
  pinned?: boolean
}

type SidebarRecent = (typeof recentConversations)[number] & {
  pinned?: boolean
}

const PROJECTS_STORAGE_KEY = 'examinsight.student.sidebar.projects'
const RECENTS_STORAGE_KEY = 'examinsight.student.sidebar.recents'

function readStoredList<T>(key: string, fallback: T[]): T[] {
  try {
    const raw = sessionStorage.getItem(key)
    if (!raw) return fallback
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : fallback
  } catch {
    return fallback
  }
}

function persistList<T>(key: string, list: T[]) {
  sessionStorage.setItem(key, JSON.stringify(list))
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const conversationStore = useConversationStore()
const sidebarProjects = ref<SidebarProject[]>(
  readStoredList(
    PROJECTS_STORAGE_KEY,
    learningPlans.map((plan) => ({ ...plan, pinned: false })),
  ),
)
const sidebarRecents = ref<SidebarRecent[]>(
  readStoredList(
    RECENTS_STORAGE_KEY,
    recentConversations.map((item, index) => ({ ...item, pinned: index < 2 })),
  ),
)
const sidebarCollapsed = ref(false)
const learningExpanded = ref(true)
const projectsExpanded = ref(true)
const pinnedExpanded = ref(true)
const recentExpanded = ref(true)
const menuOpen = ref(false)
const menuHovering = ref(false)
const menuPos = ref({ x: 0, y: 0 })
const menuItems = ref<MenuAction[]>([])
const projectSettingsOpen = ref(false)
const createProjectOpen = ref(false)
const iconPaletteOpen = ref(false)
const editingOpen = ref(false)
const editingKind = ref<'project' | 'recent'>('project')
const editingId = ref<number | null>(null)
const editingTitle = ref('')
const paletteColors = ['#000', '#ff4444', '#ed7d31', '#f6c343', '#4caf5d', '#3b82f6', '#8b5cf6', '#df6f68']
const paletteIcons = [
  'folder',
  'dollar',
  'book',
  'graduation',
  'edit',
  'pen-tool',
  'code',
  'terminal',
  'music',
  'trash',
  'tool',
  'palette',
  'stethoscope',
  'sparkle',
  'plant',
  'briefcase',
  'bar-chart',
  'user',
  'activity',
  'notebook',
  'scales',
  'microphone',
  'airplane',
  'globe',
  'wrench',
  'paw',
  'flask',
  'brain',
  'heart',
  'gift',
]

const activeSection = computed(() => {
  if (route.path.startsWith('/library')) return 'library'
  if (route.path.startsWith('/learning')) return 'learning'
  return 'chat'
})

const activeLearningId = computed(() => {
  const rawId = route.params.id
  if (typeof rawId !== 'string') return null
  const id = Number(rawId)
  return Number.isFinite(id) ? id : null
})

const orderedProjects = computed(() => [
  ...sidebarProjects.value.filter((project) => project.pinned),
  ...sidebarProjects.value.filter((project) => !project.pinned),
])

const currentLearningId = computed(() => activeLearningId.value ?? orderedProjects.value[0]?.id ?? 1)

const pinnedRecent = computed(() => sidebarRecents.value.filter((item) => item.pinned))
const normalRecent = computed(() => sidebarRecents.value.filter((item) => !item.pinned))

function go(path: string) {
  router.push(path)
}

function openMenu(e: MouseEvent, items: MenuAction[]) {
  e.stopPropagation()
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  menuItems.value = items
  menuPos.value = { x: rect.right - 6, y: rect.bottom + 4 }
  menuOpen.value = true
  menuHovering.value = true
}

function closeMenu() {
  menuOpen.value = false
  menuHovering.value = false
}

function closeMenuSoon() {
  menuHovering.value = false
  window.setTimeout(() => {
    if (!menuHovering.value) closeMenu()
  }, 120)
}

function openProjectSettings() {
  projectSettingsOpen.value = true
  closeMenu()
}

function openCreateProject() {
  createProjectOpen.value = true
}

function openLearningHome() {
  learningExpanded.value = true
  go('/learning')
}

function openLearningProjects() {
  learningExpanded.value = true
  projectsExpanded.value = true
  go('/learning/projects')
}

async function createNewChat() {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  await conversationStore.create()
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  closeMenu()
}

function persistProjects() {
  persistList(PROJECTS_STORAGE_KEY, sidebarProjects.value)
}

function persistRecents() {
  persistList(RECENTS_STORAGE_KEY, sidebarRecents.value)
}

function openRename(kind: 'project' | 'recent', id: number, title: string) {
  editingKind.value = kind
  editingId.value = id
  editingTitle.value = title
  editingOpen.value = true
  closeMenu()
}

function submitRename() {
  const title = editingTitle.value.trim()
  if (!title || editingId.value === null) return

  if (editingKind.value === 'project') {
    const project = sidebarProjects.value.find((item) => item.id === editingId.value)
    if (project) {
      project.title = title
      persistProjects()
    }
  } else {
    const recent = sidebarRecents.value.find((item) => item.id === editingId.value)
    if (recent) {
      recent.title = title
      persistRecents()
    }
  }

  editingOpen.value = false
  editingId.value = null
  editingTitle.value = ''
}

function toggleProjectPinned(project: SidebarProject) {
  const target = sidebarProjects.value.find((item) => item.id === project.id)
  if (target) {
    target.pinned = !target.pinned
    persistProjects()
  }
  closeMenu()
}

function deleteProject(project: SidebarProject) {
  sidebarProjects.value = sidebarProjects.value.filter((item) => item.id !== project.id)
  persistProjects()
  if (activeLearningId.value === project.id) {
    go(orderedProjects.value[0] ? `/learning/${orderedProjects.value[0].id}` : '/learning')
  }
  closeMenu()
}

function toggleRecentPinned(item: SidebarRecent) {
  const target = sidebarRecents.value.find((recent) => recent.id === item.id)
  if (target) {
    target.pinned = !target.pinned
    persistRecents()
  }
  closeMenu()
}

function deleteRecent(item: SidebarRecent) {
  sidebarRecents.value = sidebarRecents.value.filter((recent) => recent.id !== item.id)
  persistRecents()
  closeMenu()
}

function openProjectMenu(e: MouseEvent, project: SidebarProject) {
  openMenu(e, [
    { label: '重命名', icon: 'edit', action: () => openRename('project', project.id, project.title) },
    {
      label: project.pinned ? '取消置顶' : '置顶项目',
      icon: project.pinned ? 'pin-off' : 'pin',
      action: () => toggleProjectPinned(project),
    },
    { label: '项目设置', icon: 'settings', action: openProjectSettings },
    { label: '删除项目', icon: 'trash', danger: true, divided: true, action: () => deleteProject(project) },
  ])
}

function openRecentMenu(e: MouseEvent, item: SidebarRecent) {
  openMenu(e, [
    { label: '重命名', icon: 'edit', action: () => openRename('recent', item.id, item.title) },
    {
      label: item.pinned ? '取消置顶' : '置顶',
      icon: item.pinned ? 'pin-off' : 'pin',
      action: () => toggleRecentPinned(item),
    },
    { label: '删除', icon: 'trash', danger: true, divided: true, action: () => deleteRecent(item) },
  ])
}
</script>

<template>
  <aside class="student-sidebar" :class="{ 'student-sidebar--collapsed': sidebarCollapsed }">
    <div v-if="sidebarCollapsed" class="collapsed-pill">
      <button type="button" aria-label="展开侧边栏" @click="toggleSidebar">
        <AppIcon name="sidebar-left" :size="17" />
      </button>
      <button type="button" aria-label="新对话" @click="createNewChat">
        <AppIcon name="edit" :size="17" />
      </button>
      <button type="button" aria-label="资料库" @click="go('/library')">
        <AppIcon name="folder" :size="17" />
      </button>
      <button type="button" aria-label="智能学习" @click="openLearningHome">
        <AppIcon name="graduation" :size="17" />
      </button>
    </div>

    <template v-else>
    <div class="brand-row">
      <button class="brand" type="button" @click="go('/learning')">
        <span class="brand-logo"><img :src="logoUrl" alt="" /></span>
        <span>ExamInsight</span>
      </button>
      <button class="sidebar-toggle" type="button" aria-label="收起侧边栏" @click="toggleSidebar">
        <AppIcon name="sidebar-left" :size="17" />
      </button>
    </div>

    <nav class="nav">
      <div class="nav-row">
        <button
          class="nav-item"
          :class="{ 'nav-item--active': activeSection === 'chat' }"
          type="button"
          @click="createNewChat"
        >
          <AppIcon name="edit" :size="17" />
          <span>新对话</span>
        </button>
      </div>

      <div class="nav-row">
        <button
          class="nav-item"
          :class="{ 'nav-item--active': activeSection === 'library' }"
          type="button"
          @click="go('/library')"
        >
          <AppIcon name="folder" :size="17" />
          <span>资料库</span>
        </button>
      </div>

      <div class="nav-row">
        <button
          class="nav-item"
          :class="{ 'nav-item--active': activeSection === 'learning' }"
          type="button"
          @click="openLearningHome"
        >
          <AppIcon name="graduation" :size="17" />
          <span>智能学习</span>
        </button>
        <button
          class="learning-toggle-btn"
          type="button"
          aria-label="展开智能学习"
          @click.stop="learningExpanded = !learningExpanded"
        >
          <AppIcon
            class="nav-chevron"
            :name="learningExpanded ? 'chevron-down' : 'chevron-right'"
            :size="16"
          />
        </button>
      </div>

      <div v-if="learningExpanded" class="learning-tree">
        <div class="tree-row tree-row--section">
          <button
            class="tree-item tree-item--section"
            :class="{ 'tree-item--active': route.path.startsWith('/learning/projects') }"
            type="button"
            @click="openLearningProjects"
          >
            <AppIcon name="briefcase" :size="15" />
            <span>学习项目</span>
          </button>
          <button
            class="tree-section-toggle"
            type="button"
            aria-label="展开学习项目"
            @click.stop="projectsExpanded = !projectsExpanded"
          >
            <AppIcon :name="projectsExpanded ? 'chevron-down' : 'chevron-right'" :size="14" />
          </button>
          <button
            class="new-learning-btn new-learning-btn--tree"
            type="button"
            aria-label="新建智能学习"
            @click.stop="go('/learning/new')"
          >
            <AppIcon name="edit" :size="15" />
            <span class="nav-tooltip">新建智能学习</span>
          </button>
        </div>
        <template v-if="projectsExpanded">
          <div
            v-for="plan in orderedProjects"
            :key="plan.id"
            class="tree-row tree-row--project"
            @mouseleave="closeMenuSoon"
          >
            <button
              class="tree-item"
              :class="{ 'tree-item--active': activeLearningId === plan.id }"
              type="button"
              :title="plan.title.replace('方案', '')"
              @click="go(`/learning/${plan.id}`)"
            >
              <AppIcon name="book" :size="15" />
              <span :title="plan.title.replace('方案', '')">{{ plan.title.replace('方案', '') }}</span>
            </button>
            <button
              class="tree-more"
              type="button"
              @mouseenter="menuHovering = true"
              @click="openProjectMenu($event, plan)"
            >
              <AppIcon name="more-horizontal" :size="15" />
            </button>
          </div>
        </template>
        <div class="tree-row tree-row--section">
          <button
            class="tree-item tree-item--section"
            :class="{ 'tree-item--active': route.path.includes('/mistakes') }"
            type="button"
            @click="go(`/learning/${currentLearningId}/mistakes`)"
          >
            <AppIcon name="alert-circle" :size="15" />
            <span>错题本</span>
          </button>
        </div>
      </div>
    </nav>

    <section class="recent">
      <div class="recent-section">
        <button class="recent-title" type="button" @click="pinnedExpanded = !pinnedExpanded">
          <span>已置顶</span>
          <AppIcon
            class="recent-title-chevron"
            :name="pinnedExpanded ? 'chevron-down' : 'chevron-right'"
            :size="12"
          />
        </button>
        <div
          v-for="item in pinnedRecent"
          v-if="pinnedExpanded"
          :key="item.id"
          class="recent-row recent-row--pinned"
          @mouseleave="closeMenuSoon"
        >
          <button
            class="recent-item"
            type="button"
            :title="item.title"
            @click="go(`/chat/${item.id}`)"
          >
            <AppIcon name="message-square" :size="15" />
            <span :title="item.title">{{ item.title }}</span>
            <small v-if="item.sourceType === 'learning'" :title="item.sourceLabel">{{ item.sourceLabel }}</small>
          </button>
          <button class="recent-pin" type="button" aria-label="取消置顶" @click="toggleRecentPinned(item)">
            <AppIcon name="pin-off" :size="15" />
          </button>
          <button
            class="recent-more"
            type="button"
            @mouseenter="menuHovering = true"
            @click="openRecentMenu($event, item)"
          >
            <AppIcon name="more-horizontal" :size="15" />
          </button>
        </div>
      </div>

      <div class="recent-section">
        <button class="recent-title" type="button" @click="recentExpanded = !recentExpanded">
          <span>最近</span>
          <AppIcon
            class="recent-title-chevron"
            :name="recentExpanded ? 'chevron-down' : 'chevron-right'"
            :size="12"
          />
        </button>
        <div
          v-for="item in normalRecent"
          v-if="recentExpanded"
          :key="item.id"
          class="recent-row"
          @mouseleave="closeMenuSoon"
        >
          <button
            class="recent-item"
            type="button"
            :title="item.sourceType === 'learning' ? `${item.title} - ${item.sourceLabel}` : item.title"
            @click="go(`/chat/${item.id}`)"
          >
            <span :title="item.title">{{ item.title }}</span>
            <small v-if="item.sourceType === 'learning'" :title="item.sourceLabel">{{ item.sourceLabel }}</small>
          </button>
          <button class="recent-pin" type="button" aria-label="置顶" @click="toggleRecentPinned(item)">
            <AppIcon name="pin" :size="15" />
          </button>
          <button
            class="recent-more"
            type="button"
            @mouseenter="menuHovering = true"
            @click="openRecentMenu($event, item)"
          >
            <AppIcon name="more-horizontal" :size="15" />
          </button>
        </div>
      </div>
    </section>

    <button class="settings" type="button">
      <AppIcon name="settings" :size="20" />
      <span>设置</span>
    </button>

    <footer class="sidebar-footer">
      <div class="theme-toggle-row">
        <span>主题</span>
        <button type="button" aria-label="切换主题"><span /></button>
      </div>
      <button class="login-entry" type="button">
        <span class="login-avatar"><AppIcon name="user" :size="20" /></span>
        <span>
          <strong>未登录</strong>
          <small>点击登录或注册</small>
        </span>
      </button>
    </footer>
    </template>

    <div
      v-if="menuOpen"
      class="floating-menu"
      :style="{ left: `${menuPos.x}px`, top: `${menuPos.y}px` }"
      @mouseenter="menuHovering = true"
      @mouseleave="closeMenuSoon"
      @click.stop
    >
      <template v-for="item in menuItems" :key="item.label">
        <div v-if="item.divided" class="menu-divider" />
        <button class="menu-action" :class="{ 'menu-action--danger': item.danger }" type="button" @click="item.action">
          <AppIcon :name="item.icon" :size="16" />
          <span>{{ item.label }}</span>
        </button>
      </template>
    </div>

    <div v-if="editingOpen" class="modal-backdrop" @click.self="editingOpen = false">
      <section class="rename-modal">
        <header>
          <h2>{{ editingKind === 'project' ? '重命名学习项目' : '重命名对话' }}</h2>
          <button type="button" @click="editingOpen = false">×</button>
        </header>
        <form class="rename-form" @submit.prevent="submitRename">
          <label>
            <span>名称</span>
            <input v-model="editingTitle" autofocus maxlength="40" />
          </label>
          <footer>
            <button class="rename-cancel" type="button" @click="editingOpen = false">取消</button>
            <button class="rename-submit" type="submit">保存</button>
          </footer>
        </form>
      </section>
    </div>

    <div v-if="projectSettingsOpen" class="modal-backdrop" @click.self="projectSettingsOpen = false">
      <section class="project-modal">
        <header>
          <h2>项目设置</h2>
          <button type="button" @click="projectSettingsOpen = false">×</button>
        </header>
        <div class="project-form">
          <label>
            <span>项目名称</span>
            <div class="project-name-field">
              <button class="project-icon-trigger" type="button" @click="iconPaletteOpen = !iconPaletteOpen">
                <AppIcon name="graduation" :size="18" />
              </button>
              <input value="JS" />
            </div>
          </label>
          <label>
            <span>指令</span>
            <small>设置此项目的背景信息并自定义 ChatGPT 的回复方式。</small>
            <textarea placeholder="例如“用西班牙语回答。参考最新的 JavaScript 文档。回答要简短且突出重点。”" />
          </label>
          <label>
            <span>记忆</span>
            <input value="默认" disabled />
            <small>该项目可以访问外部聊天的记忆，反之亦然。此设置无法更改。</small>
          </label>
          <button class="delete-project-btn" type="button">删除项目</button>
        </div>

        <div v-if="iconPaletteOpen" class="icon-palette">
          <div class="palette-colors">
            <button
              v-for="color in paletteColors"
              :key="color"
              class="palette-color"
              :class="{ 'palette-color--active': color === '#000' }"
              :style="{ background: color }"
              type="button"
            />
          </div>
          <div class="palette-icons">
            <button
              v-for="icon in paletteIcons"
              :key="icon"
              :class="{ 'palette-icon--active': icon === 'graduation' }"
              type="button"
            >
              <AppIcon :name="icon" :size="21" />
            </button>
          </div>
          <button class="close-palette" type="button" @click="iconPaletteOpen = false">关闭菜单</button>
        </div>
      </section>
    </div>

    <div v-if="createProjectOpen" class="modal-backdrop" @click.self="createProjectOpen = false">
      <section class="create-project-modal">
        <header>
          <h2>创建项目</h2>
          <button class="project-gear" type="button" @click="iconPaletteOpen = !iconPaletteOpen">
            <AppIcon name="settings" :size="17" />
          </button>
          <button type="button" @click="createProjectOpen = false">×</button>
        </header>
        <label>
          <span>项目名称</span>
          <div class="project-name-field create-name-field">
            <button class="project-icon-trigger project-icon-trigger--muted" type="button" @click="iconPaletteOpen = !iconPaletteOpen">
              <AppIcon name="smile-plus" :size="20" />
            </button>
            <input placeholder="哥本哈根之旅" />
          </div>
        </label>
        <p>项目会保存聊天和文件，以便用于持续进行的工作，或者仅用于让内容井井有条。</p>
        <footer>
          <button class="create-disabled" type="button">创建项目</button>
        </footer>

        <div v-if="iconPaletteOpen" class="icon-palette icon-palette--create">
          <div class="palette-colors">
            <button
              v-for="color in paletteColors"
              :key="color"
              class="palette-color"
              :class="{ 'palette-color--active': color === '#000' }"
              :style="{ background: color }"
              type="button"
            />
          </div>
          <div class="palette-icons">
            <button
              v-for="icon in paletteIcons"
              :key="icon"
              :class="{ 'palette-icon--active': icon === 'folder' }"
              type="button"
            >
              <AppIcon :name="icon" :size="21" />
            </button>
          </div>
          <button class="close-palette" type="button" @click="iconPaletteOpen = false">完成</button>
        </div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.student-sidebar {
  width: 276px;
  height: calc(100vh - 16px);
  margin: 8px 0;
  background: #fffffc;
  border-right: 1px solid #dde3ef;
  border-radius: 0 12px 12px 0;
  display: flex;
  flex-direction: column;
  padding: 14px 12px 12px;
  gap: 12px;
  transition: width 0.16s ease, padding 0.16s ease, border-color 0.16s ease, height 0.16s ease;
  overflow: hidden;
}

.student-sidebar--collapsed {
  width: 0;
  min-width: 0;
  padding: 0;
  margin: 0;
  border-right-color: transparent;
  background: transparent;
  overflow: visible;
}

.collapsed-pill {
  position: fixed;
  left: 13px;
  top: 20px;
  z-index: 240;
  height: 49px;
  padding: 0 12px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.collapsed-pill button {
  width: 30px;
  height: 30px;
  border: 0;
  background: transparent;
  color: #344054;
  cursor: pointer;
  display: grid;
  place-items: center;
}

.collapsed-pill button:hover {
  border-radius: 7px;
  background: #f2f4f7;
  color: #111827;
}

.collapsed-pill :deep(.icon) {
  width: 17px;
  height: 17px;
}

.sidebar-toggle :deep(.icon) {
  width: 17px;
  height: 17px;
}

.collapsed-pill :deep(.icon *),
.sidebar-toggle :deep(.icon *) {
  stroke-width: 2;
}

.nav-item,
.recent-item,
.settings {
  border: 0;
  cursor: pointer;
  background: transparent;
  color: #202838;
  font: inherit;
}

.brand-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 10px;
}

.brand {
  min-width: 0;
  height: 48px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #111827;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px 6px 8px;
  font: inherit;
}

.brand span {
  display: inline-flex;
  align-items: center;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 0;
}

.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
  justify-content: center;
}

.brand-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.sidebar-toggle {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #667085;
  cursor: pointer;
  display: grid;
  place-items: center;
}

.sidebar-toggle:hover {
  background: #f2f4f7;
  color: #111827;
}

.nav {
  display: grid;
  gap: 6px;
  padding: 0 0 12px;
  border-bottom: 0;
}

.nav-row,
.tree-row,
.recent-row {
  position: relative;
  display: flex;
  align-items: center;
}

.nav-item {
  width: 100%;
  height: 38px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 44px 0 16px;
  font-size: 14px;
  font-weight: 700;
  color: #273246;
}

.nav-item span {
  flex: 1;
  text-align: left;
}

.nav-chevron {
  color: #667085;
}

.nav-item:hover,
.nav-item--active {
  background: #f2f4f7;
  color: #111827;
}

.new-learning-btn,
.learning-toggle-btn,
.tree-section-toggle,
.tree-more,
.recent-more {
  border: 0;
  background: transparent;
  color: #7b8494;
  cursor: pointer;
  display: grid;
  place-items: center;
  border-radius: 6px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.16s ease, background 0.16s ease, color 0.16s ease;
}

.new-learning-btn {
  position: absolute;
  right: 6px;
  width: 26px;
  height: 26px;
  color: #667085;
}

.learning-toggle-btn {
  position: absolute;
  right: 6px;
  width: 26px;
  height: 26px;
  color: #667085;
}

.tree-section-toggle {
  position: absolute;
  right: 4px;
  width: 24px;
  height: 24px;
  color: #667085;
}

.new-learning-btn--tree {
  right: 30px;
  width: 24px;
  height: 24px;
}

.nav-tooltip {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  z-index: 20;
  width: max-content;
  padding: 6px 9px;
  border-radius: 6px;
  background: #111827;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  pointer-events: none;
  opacity: 0;
  transform: translateY(-2px);
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.new-learning-btn:hover .nav-tooltip,
.new-learning-btn:focus-visible .nav-tooltip {
  opacity: 1;
  transform: translateY(0);
}

.nav-row:hover .new-learning-btn,
.nav-row:hover .learning-toggle-btn,
.tree-row:hover .new-learning-btn,
.tree-row:hover .tree-section-toggle,
.tree-row:hover .tree-more,
.recent-row:hover .recent-pin,
.recent-row:hover .recent-more,
.new-learning-btn:focus-visible,
.learning-toggle-btn:focus-visible,
.tree-section-toggle:focus-visible,
.tree-more:focus-visible,
.recent-pin:focus-visible,
.recent-more:focus-visible {
  opacity: 1;
  pointer-events: auto;
}

.new-learning-btn:hover,
.learning-toggle-btn:hover,
.tree-section-toggle:hover,
.tree-more:hover,
.recent-pin:hover,
.recent-more:hover {
  background: #e6ebf3;
  color: #111827;
}

.floating-menu {
  position: fixed;
  z-index: 300;
  width: 198px;
  padding: 8px;
  border: 1px solid #d9dee8;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.14);
}

.menu-action {
  width: 100%;
  height: 38px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #111827;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  font-size: 14px;
  font-weight: 400;
  text-align: left;
}

.menu-action :deep(svg) {
  width: 16px;
  height: 16px;
  stroke-width: 2;
}

.menu-action:hover {
  background: #f2f4f7;
}

.menu-action--danger {
  color: #ff2457;
}

.menu-divider {
  height: 1px;
  background: #e6ebf3;
  margin: 6px 6px;
}

.learning-tree {
  display: grid;
  gap: 3px;
  padding: 2px 0 6px 30px;
}

.tree-row--section {
  margin-top: 2px;
}

.tree-item {
  width: 100%;
  min-height: 34px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #667085;
  cursor: pointer;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  align-items: center;
  gap: 7px;
  padding: 0 30px 0 8px;
  text-align: left;
  font: inherit;
}

.tree-more {
  position: absolute;
  right: 4px;
  width: 24px;
  height: 24px;
}

.tree-item:hover,
.tree-item--active {
  background: #f2f4f7;
  color: #111827;
}

.tree-item span {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
}

.tree-item small {
  color: #8a94a6;
  font-size: 12px;
  white-space: nowrap;
}

.tree-item--section {
  min-height: 34px;
  grid-template-columns: 18px minmax(0, 1fr);
  padding-right: 58px;
  color: #273246;
  font-weight: 700;
}

.tree-row--section + .tree-row--project {
  margin-top: 2px;
}

.tree-row--project .tree-item {
  grid-template-columns: 18px minmax(0, 1fr);
  padding-left: 22px;
}

.tree-row--project {
  padding-left: 16px;
}

.recent {
  min-height: 0;
  flex: 1;
  max-height: none;
  overflow-y: auto;
  display: grid;
  align-content: start;
  gap: 18px;
  padding-right: 2px;
}

.recent-section {
  display: grid;
  gap: 3px;
}

.recent-title {
  width: 100%;
  min-height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  padding: 0 4px;
  color: #111827;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  text-align: left;
}

.recent-title:hover {
  background: rgba(0, 0, 0, 0.04);
}

.recent-title-chevron {
  opacity: 0;
  color: #667085;
  transition: opacity 0.16s ease;
}

.recent-title:hover .recent-title-chevron,
.recent-title:focus-visible .recent-title-chevron {
  opacity: 1;
}

.recent-item {
  width: 100%;
  min-height: 30px;
  border-radius: 8px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
  padding: 6px 58px 6px 4px;
  text-align: left;
  color: #111827;
}

.recent-row--pinned .recent-item {
  grid-template-columns: 18px minmax(0, 1fr) auto;
}

.recent-pin,
.recent-more {
  position: absolute;
  top: 50%;
  width: 24px;
  height: 24px;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-50%);
  transition: opacity 0.16s ease, background 0.16s ease, color 0.16s ease;
}

.recent-pin {
  right: 30px;
}

.recent-more {
  right: 4px;
}

.recent-row:hover .recent-item {
  background: rgba(0, 0, 0, 0.04);
  color: #111827;
}

.recent-item span {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
}

.recent-item small {
  color: #8a94a6;
  font-size: 12px;
  max-width: 82px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-pin {
  border: 0;
  background: transparent;
  color: #98a2b3;
  cursor: pointer;
  display: grid;
  place-items: center;
  border-radius: 6px;
}

.recent-pin:hover {
  background: rgba(0, 0, 0, 0.04);
  color: #111827;
}

.settings {
  margin-top: auto;
  height: 44px;
  border-top: 1px solid #e6ebf3;
  padding-top: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  color: #344054;
  display: none;
}

.sidebar-footer {
  margin-top: auto;
  padding: 10px 0 0;
  border-top: 1px solid #edf0f5;
  display: grid;
  gap: 16px;
}

.theme-toggle-row {
  min-height: 46px;
  border: 1px solid #e4e7ec;
  border-radius: 10px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

.theme-toggle-row button {
  width: 44px;
  height: 26px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #f5f5f5;
  cursor: pointer;
  padding: 2px;
}

.theme-toggle-row button span {
  display: block;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.16);
}

.login-entry {
  border: 0;
  background: transparent;
  color: #111827;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px 8px;
  text-align: left;
}

.login-avatar {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  background: #f2f4f7;
  color: #344054;
  display: grid;
  place-items: center;
}

.login-entry strong,
.login-entry small {
  display: block;
}

.login-entry strong {
  font-size: 14px;
  font-weight: 800;
}

.login-entry small {
  color: #667085;
  font-size: 12px;
  margin-top: 1px;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 260;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.38);
}

.rename-modal,
.project-modal,
.create-project-modal {
  position: relative;
  width: min(512px, 100%);
  border: 1px solid #d9dee8;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 20px 56px rgba(15, 23, 42, 0.18);
  padding: 16px;
  overflow: visible;
}

.create-project-modal {
  width: min(512px, 100%);
}

.rename-modal {
  width: min(420px, 100%);
}

.rename-modal header,
.project-modal header,
.create-project-modal header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}

.rename-modal h2,
.project-modal h2,
.create-project-modal h2 {
  flex: 1;
  margin: 0;
  color: #111827;
  font-size: 20px;
  font-weight: 500;
}

.rename-modal header button,
.project-modal header button,
.create-project-modal header button {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #344054;
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 20px;
  line-height: 1;
}

.project-gear {
  font-size: 0 !important;
}

.rename-modal header button:hover,
.project-modal header button:hover,
.create-project-modal header button:hover {
  background: #f2f4f7;
}

.rename-form,
.project-form,
.create-project-modal {
  display: grid;
  gap: 16px;
}

.rename-form label,
.project-form label,
.create-project-modal label {
  display: grid;
  gap: 8px;
  color: #111827;
  font-size: 14px;
  font-weight: 500;
}

.rename-form input {
  height: 42px;
  min-width: 0;
  width: 100%;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  background: #fff;
  color: #111827;
  box-sizing: border-box;
  padding: 0 12px;
  font-size: 14px;
}

.rename-form input:focus {
  border-color: #111827;
  outline: none;
}

.rename-form footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.rename-cancel,
.rename-submit {
  height: 36px;
  border-radius: 8px;
  padding: 0 14px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
}

.rename-cancel {
  border: 1px solid #dbe2ec;
  background: #fff;
  color: #344054;
}

.rename-submit {
  border: 1px solid #111827;
  background: #111827;
  color: #fff;
}

.project-form small {
  color: #8a94a6;
  font-size: 12px;
  line-height: 1.5;
}

.project-name-field {
  height: 38px;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  align-items: center;
}

.project-icon-trigger {
  width: 30px !important;
  height: 30px !important;
  margin-left: 4px;
  border: 0 !important;
  border-radius: 8px !important;
  background: transparent !important;
  color: #8b5cf6 !important;
  display: grid;
  place-items: center;
  cursor: pointer;
  padding: 0;
}

.project-icon-trigger:hover {
  background: #f4f4f5 !important;
}

.project-icon-trigger--muted {
  color: #9aa3b2 !important;
}

.create-name-field {
  grid-template-columns: 36px minmax(0, 1fr);
}

.project-name-field input,
.project-form input,
.project-form textarea,
.create-project-modal input {
  min-width: 0;
  width: 100%;
  border: 1px solid #dbe2ec;
  border-radius: 8px;
  background: #fff;
  color: #111827;
  box-sizing: border-box;
  font-size: 14px;
  font-weight: 400;
}

.project-name-field input {
  border: 0;
}

.project-form input,
.create-project-modal input {
  height: 38px;
  padding: 0 12px;
}

.project-form textarea {
  min-height: 62px;
  padding: 10px 12px;
  resize: vertical;
}

.project-form input:disabled {
  background: #f8fafc;
  color: #8a94a6;
}

.delete-project-btn {
  width: fit-content;
  height: 38px;
  border: 1px solid #ff2457;
  border-radius: 999px;
  background: #fff;
  color: #ff2457;
  padding: 0 14px;
  cursor: pointer;
  font-weight: 600;
}

.icon-palette {
  position: absolute;
  left: 6px;
  top: 126px;
  width: 260px;
  padding: 12px 12px 0;
  border: 1px solid #d9dee8;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.16);
  z-index: 2;
  overflow: hidden;
}

.icon-palette--create {
  left: 8px;
  top: 126px;
}

.palette-colors,
.palette-icons {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}

.palette-colors {
  padding-bottom: 12px;
  border-bottom: 1px solid #e6ebf3;
}

.palette-color {
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: 999px;
  cursor: pointer;
  box-sizing: border-box;
}

.palette-color--active {
  border: 3px solid #fff;
  outline: 2px solid #111827;
  outline-offset: -1px;
  box-shadow: none;
}

.palette-icons {
  padding: 12px 0;
  row-gap: 8px;
}

.palette-icons button {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #111827;
  cursor: pointer;
  display: grid;
  place-items: center;
  padding: 0;
  line-height: 0;
}

.palette-icons button:hover {
  background: #f2f4f7;
}

.palette-icon--active {
  background: #f4f4f5 !important;
  box-shadow: inset 0 0 0 1px #e5e7eb;
}

.palette-icons button :deep(svg) {
  width: 20px;
  height: 20px;
  margin: auto;
}

.close-palette {
  width: calc(100% + 24px);
  height: 48px;
  margin: 0 -12px;
  padding: 0 20px;
  border: 0;
  border-top: 1px solid #e6ebf3;
  background: #fff;
  color: #111827;
  text-align: left;
  cursor: pointer;
  font-size: 14px;
  font-weight: 400;
  border-radius: 0 0 16px 16px;
}

.create-project-modal p {
  margin: 0;
  padding: 12px;
  border-radius: 12px;
  background: #f2f4f7;
  color: #667085;
  font-size: 14px;
  line-height: 1.5;
}

.create-project-modal footer {
  display: flex;
  justify-content: flex-end;
}

.create-disabled {
  height: 34px;
  border: 0;
  border-radius: 999px;
  background: #c7c7c7;
  color: #fff;
  padding: 0 14px;
  cursor: not-allowed;
  font-weight: 700;
}
</style>
