<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Activity,
  Bone,
  BookOpen,
  BookOpenText,
  BrainCircuit,
  BriefcaseBusiness,
  ChartSpline,
  CircleDollarSign,
  Code2,
  Folder,
  Gift,
  Globe2,
  GraduationCap,
  Hammer,
  Heart,
  HeartPulse,
  Leaf,
  Mic2,
  Microscope,
  Music2,
  PenTool,
  Scale,
  Send,
  Settings2,
  Sparkles,
  Square,
  SquarePen,
  SquareTerminal,
  SwatchBook,
  Trash2,
  UserRound,
} from 'lucide-vue-next'
import AppIcon from '@/components/common/AppIcon.vue'
import UserProfileModal from '@/components/auth/UserProfileModal.vue'
import LibraryKnowledgeCreateModal from '@/components/library/LibraryKnowledgeCreateModal.vue'
import logoUrl from '@/assets/icons/ExamInsight-Logo.png'
import { learningPlans } from '@/mock'
import type { Conversation } from '@/api/conversation'
import { useAuthStore } from '@/stores/auth'
import { useConversationStore } from '@/stores/conversation'
import { useLearningStore } from '@/stores/learning'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useThemeStore } from '@/stores/theme'
import { isMockDataSource } from '@/config/dataSource'

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

const PINNED_PROJECTS_STORAGE_KEY = 'examinsight.ui.sidebar.pinned-learning-projects'
const PROJECT_LINK_MIGRATION_KEY = 'examinsight.student.conversation-project-links.v1'

function readPinnedProjectIds(): number[] {
  try {
    const raw = localStorage.getItem(PINNED_PROJECTS_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.map(Number).filter((id) => Number.isFinite(id)) : []
  } catch {
    return []
  }
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const conversationStore = useConversationStore()
const learningStore = useLearningStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const themeStore = useThemeStore()
const initialPinnedProjectIds = new Set(readPinnedProjectIds())
const sidebarProjects = ref<SidebarProject[]>(learningPlans.map((plan) => ({
  ...plan,
  pinned: initialPinnedProjectIds.has(plan.id),
})))
const sidebarCollapsed = ref(false)
const learningExpanded = ref(true)
const pinnedExpanded = ref(true)
const recentExpanded = ref(true)
const menuOpen = ref(false)
const menuHovering = ref(false)
const menuPos = ref({ x: 0, y: 0 })
const menuItems = ref<MenuAction[]>([])
const projectSettingsOpen = ref(false)
const createProjectOpen = ref(false)
const iconPaletteOpen = ref(false)
const libraryMenuOpen = ref(false)
const editingOpen = ref(false)
const editingKind = ref<'project' | 'recent'>('project')
const editingId = ref<number | null>(null)
const editingTitle = ref('')
const profileOpen = ref(false)
const createProjectTitle = ref('')
const createProjectLibraryId = ref<number | null>(null)
const knowledgeCreateOpen = ref(false)
const createProjectColor = ref('#000')
const createProjectIcon = ref('folder')
const paletteColors = ['#000', '#ff4444', '#ed7d31', '#f6c343', '#4caf5d', '#3b82f6', '#8b5cf6', '#df6f68']
const paletteIcons = [
  { key: 'folder', component: Folder },
  { key: 'dollar', component: CircleDollarSign },
  { key: 'book', component: BookOpen },
  { key: 'graduation', component: GraduationCap },
  { key: 'edit', component: SquarePen },
  { key: 'pen-tool', component: PenTool },
  { key: 'code', component: Code2 },
  { key: 'terminal', component: SquareTerminal },
  { key: 'music', component: Music2 },
  { key: 'trash', component: Trash2 },
  { key: 'tool', component: Hammer },
  { key: 'palette', component: SwatchBook },
  { key: 'stethoscope', component: HeartPulse },
  { key: 'sparkle', component: Sparkles },
  { key: 'plant', component: Leaf },
  { key: 'briefcase', component: BriefcaseBusiness },
  { key: 'bar-chart', component: ChartSpline },
  { key: 'user', component: UserRound },
  { key: 'activity', component: Activity },
  { key: 'notebook', component: BookOpenText },
  { key: 'scales', component: Scale },
  { key: 'microphone', component: Mic2 },
  { key: 'airplane', component: Send },
  { key: 'globe', component: Globe2 },
  { key: 'wrench', component: Settings2 },
  { key: 'paw', component: Bone },
  { key: 'flask', component: Microscope },
  { key: 'brain', component: BrainCircuit },
  { key: 'heart', component: Heart },
  { key: 'gift', component: Gift },
]
const createProjectIconComponent = computed(() =>
  paletteIcons.find((icon) => icon.key === createProjectIcon.value)?.component ?? Folder,
)

function projectIconComponent(icon?: string) {
  return paletteIcons.find((item) => item.key === icon)?.component ?? BookOpen
}

function projectIconColor(color?: string) {
  return !color || color === '#000' ? 'var(--color-text)' : color
}

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

const createLibraryLabel = computed(() =>
  createProjectLibraryId.value === null
    ? '无'
    : knowledgeBaseStore.list.find((library) => library.id === createProjectLibraryId.value)?.name ?? '无',
)

const pinnedRecent = computed(() => conversationStore.list.filter((item) => item.isPinned))
const normalRecent = computed(() => conversationStore.list.filter((item) => !item.isPinned))

onMounted(() => {
  conversationStore.init()
  void knowledgeBaseStore.fetchList()
  void learningStore.fetchPlans().then(() => {
    syncSidebarProjects()
    migrateLegacyConversationProjectLinks()
  })
})

watch(
  () => learningStore.plans.map((plan) => `${plan.id}:${plan.title}:${plan.status}`).join('|'),
  () => syncSidebarProjects(),
)

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
  iconPaletteOpen.value = false
  libraryMenuOpen.value = false
  createProjectTitle.value = ''
  createProjectLibraryId.value = null
}

function closeCreateProjectPanels() {
  iconPaletteOpen.value = false
  libraryMenuOpen.value = false
}

function closeCreateProject() {
  closeCreateProjectPanels()
  createProjectOpen.value = false
}

function toggleIconPalette() {
  const nextOpen = !iconPaletteOpen.value
  iconPaletteOpen.value = nextOpen
  if (nextOpen) libraryMenuOpen.value = false
}

function toggleLibraryMenu() {
  const nextOpen = !libraryMenuOpen.value
  libraryMenuOpen.value = nextOpen
  if (nextOpen) iconPaletteOpen.value = false
}

function openLearningHome() {
  learningExpanded.value = true
  go('/learning/projects')
}

function openLearningProject(project: SidebarProject) {
  if (project.status === '待开启') {
    go(`/chat?learningProjectId=${project.id}`)
    return
  }
  go(`/learning/${project.id}`)
}

async function createNewChat() {
  go('/chat')
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  closeMenu()
}

function handleUserClick() {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  profileOpen.value = true
}

function handleLogout() {
  authStore.logout(router)
}

function userDisplayName() {
  return authStore.user?.nickname || authStore.user?.username || '未登录'
}

function persistProjects() {
  localStorage.setItem(
    PINNED_PROJECTS_STORAGE_KEY,
    JSON.stringify(sidebarProjects.value.filter((project) => project.pinned).map((project) => project.id)),
  )
}

function syncSidebarProjects() {
  const pinnedMap = new Map(sidebarProjects.value.map((project) => [project.id, Boolean(project.pinned)]))
  sidebarProjects.value = learningStore.plans.map((plan) => ({
    ...plan,
    pinned: pinnedMap.get(plan.id) ?? false,
  }))
  persistProjects()
}

function migrateLegacyConversationProjectLinks() {
  if (!isMockDataSource) return
  if (sessionStorage.getItem(PROJECT_LINK_MIGRATION_KEY)) return
  conversationStore.list.forEach((conversation) => {
    if (conversation.learningProjectId) return
    const matches = learningStore.plans.filter((plan) => plan.title.trim() === (conversation.title || '').trim())
    if (matches.length === 1) {
      const project = matches[0]!
      conversationStore.linkLearningProject(conversation.id, project.id, project.title)
    }
  })
  sessionStorage.setItem(PROJECT_LINK_MIGRATION_KEY, '1')
}

function selectCreateLibrary(id: number | null) {
  createProjectLibraryId.value = id
  libraryMenuOpen.value = false
}

function selectCreateColor(color: string) {
  createProjectColor.value = color
}

function selectCreateIcon(icon: string) {
  createProjectIcon.value = icon
}

function createKnowledgeBaseFromProject() {
  libraryMenuOpen.value = false
  knowledgeCreateOpen.value = true
}

function handleProjectKnowledgeCreated(id: number) {
  createProjectLibraryId.value = id
  knowledgeCreateOpen.value = false
}

async function submitCreateProject() {
  const title = createProjectTitle.value.trim()
  if (!title) return
  const project = await learningStore.createDraftPlan({
    title,
    libraryId: createProjectLibraryId.value,
    libraryName: createLibraryLabel.value,
    icon: createProjectIcon.value,
    iconColor: createProjectColor.value,
  })
  syncSidebarProjects()
  createProjectOpen.value = false
  go(`/chat?learningProjectId=${project.id}`)
}

function openRename(kind: 'project' | 'recent', id: number, title: string) {
  editingKind.value = kind
  editingId.value = id
  editingTitle.value = title
  editingOpen.value = true
  closeMenu()
}

async function submitRename() {
  const title = editingTitle.value.trim()
  if (!title || editingId.value === null) return

  if (editingKind.value === 'project') {
    learningStore.renamePlan(editingId.value, title)
    const project = sidebarProjects.value.find((item) => item.id === editingId.value)
    if (project) {
      project.title = title
      persistProjects()
    }
  } else {
    await conversationStore.rename(editingId.value, title)
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

async function toggleRecentPinned(item: Conversation) {
  await conversationStore.togglePin(item.id)
  closeMenu()
}

async function deleteRecent(item: Conversation) {
  await conversationStore.remove(item.id)
  closeMenu()
}

function conversationTitle(item: Conversation) {
  const title = item.title || '新对话'
  if (isTutorConversation(item) && !title.includes('AI 助教')) return `${title} · AI 助教`
  if (isLearningSetupConversation(item) && !title.includes('方案制定')) return `${title} · 方案制定`
  return title
}

function conversationProjectName(item: Conversation) {
  if (!item.learningProjectId) return ''
  if (isTutorConversation(item)) return 'AI 助教历史'
  if (isLearningSetupConversation(item)) return '方案制定记录'
  return learningStore.getPlan(item.learningProjectId)?.title || item.learningProjectName || ''
}

function isTutorConversation(item: Conversation) {
  if (item.conversationType === 'learning-tutor') return true
  if (item.conversationType === 'learning-setup') return false
  return Boolean(item.title?.includes('AI 助教'))
}

function isLearningSetupConversation(item: Conversation) {
  if (item.conversationType === 'learning-setup') return true
  if (item.conversationType === 'learning-tutor') return false
  return Boolean(item.learningProjectId && !isTutorConversation(item))
}

function conversationPath(item: Conversation) {
  if (!item.learningProjectId) return `/chat/${item.id}`
  const query = new URLSearchParams({ learningProjectId: String(item.learningProjectId) })
  if (isTutorConversation(item)) query.set('tutor', '1')
  else query.set('learning', '1')
  return `/chat/${item.id}?${query.toString()}`
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

function openRecentMenu(e: MouseEvent, item: Conversation) {
  openMenu(e, [
    { label: '重命名', icon: 'edit', action: () => openRename('recent', item.id, conversationTitle(item)) },
    {
      label: item.isPinned ? '取消置顶' : '置顶',
      icon: item.isPinned ? 'pin-off' : 'pin',
      action: () => toggleRecentPinned(item),
    },
    { label: '删除', icon: 'trash', danger: true, divided: true, action: () => deleteRecent(item) },
  ])
}
</script>

<template>
  <aside class="student-sidebar" :class="{ 'student-sidebar--collapsed': sidebarCollapsed }">
    <div v-if="sidebarCollapsed" class="collapsed-pill">
      <button class="ui-icon-action" type="button" aria-label="展开侧边栏" @click="toggleSidebar">
        <AppIcon name="sidebar-left" :size="17" />
      </button>
      <button class="ui-icon-action" type="button" aria-label="新对话" @click="createNewChat">
        <AppIcon name="edit" :size="17" />
      </button>
      <button class="ui-icon-action" type="button" aria-label="资料库" @click="go('/library')">
        <AppIcon name="folder" :size="17" />
      </button>
      <button class="ui-icon-action" type="button" aria-label="智能学习" @click="openLearningHome">
        <AppIcon name="graduation" :size="17" />
      </button>
    </div>

    <template v-else>
    <div class="brand-row">
      <button class="brand" type="button" @click="go('/learning')">
        <span class="brand-logo"><img :src="logoUrl" alt="" /></span>
        <span>ExamInsight</span>
      </button>
      <button class="sidebar-toggle ui-icon-action" type="button" aria-label="收起侧边栏" @click="toggleSidebar">
        <AppIcon name="sidebar-left" :size="17" />
      </button>
    </div>

    <nav class="nav">
      <div class="nav-row ui-hover-row">
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

      <div class="nav-row ui-hover-row">
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

      <div class="nav-row ui-hover-row">
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
          class="new-learning-btn ui-icon-action"
          type="button"
          aria-label="新建智能学习"
          @click.stop="openCreateProject"
        >
          <AppIcon name="edit" :size="15" />
          <span class="nav-tooltip">新建智能学习</span>
        </button>
        <button
          class="learning-toggle-btn ui-icon-action"
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
            @click="openLearningProject(plan)"
          >
            <component
              :is="projectIconComponent(plan.icon)"
              :size="15"
              :style="{ color: projectIconColor(plan.iconColor) }"
            />
            <span>{{ plan.title.replace('方案', '') }}</span>
            <small v-if="plan.status === '待开启'">待开启</small>
          </button>
          <button
            class="tree-more ui-icon-action"
            type="button"
            @mouseenter="menuHovering = true"
            @click="openProjectMenu($event, plan)"
          >
            <AppIcon name="more-horizontal" :size="15" />
          </button>
        </div>
      </div>
    </nav>

    <section class="recent">
      <div v-if="pinnedRecent.length" class="recent-section">
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
            :aria-label="conversationTitle(item)"
            @click="go(conversationPath(item))"
          >
            <AppIcon name="message-square" :size="15" />
            <span class="recent-label-line">
              <span class="recent-conversation-title">{{ conversationTitle(item) }}</span>
              <small v-if="conversationProjectName(item)">{{ conversationProjectName(item) }}</small>
            </span>
          </button>
          <button class="recent-pin ui-icon-action" type="button" aria-label="取消置顶" @click.stop="toggleRecentPinned(item)">
            <AppIcon name="pin-off" :size="15" />
          </button>
          <button
            class="recent-more ui-icon-action"
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
            :aria-label="conversationTitle(item)"
            @click="go(conversationPath(item))"
          >
            <span class="recent-label-line">
              <span class="recent-conversation-title">{{ conversationTitle(item) }}</span>
              <small v-if="conversationProjectName(item)">{{ conversationProjectName(item) }}</small>
            </span>
          </button>
          <button class="recent-pin ui-icon-action" type="button" aria-label="置顶" @click.stop="toggleRecentPinned(item)">
            <AppIcon name="pin" :size="15" />
          </button>
          <button
            class="recent-more ui-icon-action"
            type="button"
            @mouseenter="menuHovering = true"
            @click="openRecentMenu($event, item)"
          >
            <AppIcon name="more-horizontal" :size="15" />
          </button>
        </div>
      </div>
    </section>

    <button class="settings ui-hover-row" type="button">
      <AppIcon name="settings" :size="20" />
      <span>设置</span>
    </button>

    <footer class="sidebar-footer">
      <div class="theme-toggle-row">
        <span>主题</span>
        <button
          class="theme-toggle"
          :class="{ 'theme-toggle--dark': themeStore.isDark }"
          type="button"
          role="switch"
          :aria-checked="themeStore.isDark"
          :aria-label="themeStore.isDark ? '切换到浅色主题' : '切换到深色主题'"
          @click="themeStore.toggle"
        >
          <span />
        </button>
      </div>
      <div class="account-row">
        <button class="login-entry ui-hover-row" type="button" @click="handleUserClick">
          <span class="login-avatar"><AppIcon name="user" :size="20" /></span>
          <span>
            <strong>{{ userDisplayName() }}</strong>
            <small>{{ authStore.isAuthed ? '查看个人资料' : '点击登录或注册' }}</small>
          </span>
        </button>
        <button v-if="authStore.isAuthed" class="logout-btn" type="button" @click="handleLogout">
          退出
        </button>
      </div>
      <UserProfileModal
        v-if="profileOpen"
        :open="profileOpen"
        @close="profileOpen = false"
      />
    </footer>
    </template>

    <div
      v-if="menuOpen"
      class="floating-menu ui-menu-panel"
      :style="{ left: `${menuPos.x}px`, top: `${menuPos.y}px` }"
      @mouseenter="menuHovering = true"
      @mouseleave="closeMenuSoon"
      @click.stop
    >
      <template v-for="item in menuItems" :key="item.label">
        <div v-if="item.divided" class="menu-divider ui-menu-divider" />
        <button class="menu-action ui-menu-item" :class="{ 'menu-action--danger': item.danger, 'ui-menu-item--danger': item.danger }" type="button" @click="item.action">
          <span class="ui-menu-icon"><AppIcon :name="item.icon" :size="16" /></span>
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
              <button class="project-icon-trigger" type="button" @click="toggleIconPalette">
                <GraduationCap :size="18" />
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

        <div v-if="iconPaletteOpen" class="icon-palette ui-menu-panel" @click.stop>
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
              :key="icon.key"
              :class="{ 'palette-icon--active': icon.key === 'graduation' }"
              type="button"
            >
              <component :is="icon.component" :size="21" />
            </button>
          </div>
          <button class="close-palette" type="button" @click="iconPaletteOpen = false">关闭菜单</button>
        </div>
      </section>
    </div>

    <div v-if="createProjectOpen" class="modal-backdrop" @click.self="closeCreateProject">
      <section class="create-project-modal" @click="closeCreateProjectPanels">
        <header>
          <h2>创建项目</h2>
          <button type="button" @click="closeCreateProject">×</button>
        </header>
        <label>
          <span>项目名称</span>
          <div
            class="project-name-field create-name-field"
            :style="{ '--project-accent-color': projectIconColor(createProjectColor) }"
          >
            <button class="project-icon-trigger project-icon-trigger--muted" type="button" @click.stop="toggleIconPalette">
              <component :is="createProjectIconComponent" :size="20" />
            </button>
            <input v-model="createProjectTitle" placeholder="哥本哈根之旅" maxlength="40" @keydown.enter.prevent="submitCreateProject" />
          </div>
        </label>
        <div class="library-field">
          <span>关联知识库</span>
          <button class="library-trigger" type="button" @click.stop="toggleLibraryMenu">
            <span>{{ createLibraryLabel }}</span>
            <AppIcon :name="libraryMenuOpen ? 'chevron-up' : 'chevron-down'" :size="14" />
          </button>
          <div v-if="libraryMenuOpen" class="library-menu ui-menu-panel" @click.stop>
            <button class="ui-menu-item" type="button" @click="selectCreateLibrary(null)">
              <span class="ui-menu-icon"><AppIcon name="close" :size="16" /></span>
              <span>无</span>
            </button>
            <button
              v-for="library in knowledgeBaseStore.list"
              :key="library.id"
              class="ui-menu-item"
              type="button"
              @click="selectCreateLibrary(library.id)"
            >
              <span class="ui-menu-icon"><AppIcon name="folder" :size="16" /></span>
              <span>{{ library.name }}</span>
            </button>
            <div class="ui-menu-divider" />
            <button class="library-menu-create ui-menu-item" type="button" @click="createKnowledgeBaseFromProject">
              <span class="ui-menu-icon"><AppIcon name="plus" :size="16" /></span>
              <span>新建知识库</span>
            </button>
          </div>
        </div>
        <p>项目会保存聊天和文件，以便用于持续进行的工作，或者仅用于让内容井井有条。</p>
        <footer>
          <button class="create-disabled" :class="{ 'create-disabled--ready': createProjectTitle.trim() }" type="button" @click="submitCreateProject">创建项目</button>
        </footer>

        <div v-if="iconPaletteOpen" class="icon-palette icon-palette--create ui-menu-panel" @click.stop>
          <div class="palette-colors">
            <button
              v-for="color in paletteColors"
              :key="color"
              class="palette-color"
              :class="{ 'palette-color--active': color === createProjectColor }"
              :style="{ background: color, '--palette-selection-color': color }"
              type="button"
              @click="selectCreateColor(color)"
            />
          </div>
          <div class="palette-icons">
            <button
              v-for="icon in paletteIcons"
              :key="icon.key"
              :class="{ 'palette-icon--active': icon.key === createProjectIcon }"
              type="button"
              @click="selectCreateIcon(icon.key)"
            >
              <component :is="icon.component" :size="21" />
            </button>
          </div>
          <button class="close-palette" type="button" @click="iconPaletteOpen = false">完成</button>
        </div>
      </section>
    </div>
    <LibraryKnowledgeCreateModal
      :open="knowledgeCreateOpen"
      @close="knowledgeCreateOpen = false"
      @created="handleProjectKnowledgeCreated"
    />
  </aside>
</template>

<style scoped>
.student-sidebar {
  width: 276px;
  height: 100%;
  box-sizing: border-box;
  margin: 0;
  background: var(--color-sidebar);
  border-right: 1px solid var(--color-border);
  border-radius: 0;
  color: var(--color-text);
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
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.collapsed-pill button {
  width: 30px;
  height: 30px;
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
}

.collapsed-pill button:hover {
  border-radius: var(--ui-hover-radius);
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
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
  color: var(--color-text);
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
  color: var(--color-text);
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
  background: var(--color-surface);
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
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
}

.sidebar-toggle:hover {
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
}

.nav {
  flex: 0 0 auto;
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
  color: var(--color-text);
}

.nav-item span {
  flex: 1;
  text-align: left;
}

.nav-chevron {
  color: var(--color-text-muted);
}

.nav-item:hover,
.nav-item--active {
  background: var(--ui-hover-bg);
  color: var(--color-text);
}

.new-learning-btn,
.learning-toggle-btn,
.tree-section-toggle,
.tree-more,
.recent-more {
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
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
  color: var(--color-text-muted);
}

.nav-row > .new-learning-btn {
  right: 34px;
}

.learning-toggle-btn {
  position: absolute;
  right: 6px;
  width: 26px;
  height: 26px;
  color: var(--color-text-muted);
}

.tree-section-toggle {
  position: absolute;
  right: 4px;
  width: 24px;
  height: 24px;
  color: var(--color-text-muted);
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
  background: var(--color-text);
  color: var(--color-bg);
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
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
}

.floating-menu {
  position: fixed;
  z-index: 300;
  width: 198px;
}

.menu-action {
  height: var(--ui-menu-item-height);
}

.menu-action :deep(svg) {
  width: 16px;
  height: 16px;
  stroke-width: 2;
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
  color: var(--color-text-muted);
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
  background: var(--ui-hover-bg);
  color: var(--color-text);
}

.tree-item span {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
}

.tree-item small {
  color: var(--color-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.tree-item--section {
  min-height: 34px;
  grid-template-columns: 18px minmax(0, 1fr);
  padding-right: 58px;
  color: var(--color-text);
  font-weight: 700;
}

.tree-row--section + .tree-row--project {
  margin-top: 2px;
}

.tree-row--project .tree-item {
  grid-template-columns: 18px minmax(0, 1fr) auto;
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
  color: var(--color-text);
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
  background: var(--ui-hover-bg);
}

.recent-title-chevron {
  opacity: 0;
  color: var(--color-text-muted);
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
  grid-template-columns: minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  padding: 6px 58px 6px 4px;
  text-align: left;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 400;
  line-height: 20px;
  letter-spacing: 0;
}

.recent-row--pinned .recent-item {
  grid-template-columns: 18px minmax(0, 1fr);
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

.recent-item span {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: inherit;
  font-weight: inherit;
  line-height: inherit;
  letter-spacing: inherit;
}

.recent-item small {
  color: color-mix(in srgb, var(--color-text-muted) 70%, var(--color-bg));
  flex: none;
  font-size: 14px;
  font-weight: 400;
  max-width: 82px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-label-line {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  overflow: hidden;
}

.recent-conversation-title {
  flex: 0 1 auto;
  min-width: 0;
}

.recent-pin {
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
  border-radius: 6px;
}

.recent-pin:hover {
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
}

.settings {
  margin-top: auto;
  height: 44px;
  border-top: 1px solid var(--color-border);
  padding-top: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  color: var(--color-text-muted);
  display: none;
}

.sidebar-footer {
  flex: 0 0 auto;
  margin-top: auto;
  padding: 10px 0 0;
  border-top: 1px solid var(--color-border);
  display: grid;
  gap: 16px;
}

.theme-toggle-row {
  min-height: 46px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 700;
}

.theme-toggle {
  width: 44px;
  height: 26px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-hover);
  cursor: pointer;
  padding: 2px;
  transition: background 0.18s ease, border-color 0.18s ease;
}

.theme-toggle span {
  display: block;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: var(--color-surface);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.16);
  transition: transform 0.18s ease;
}

.theme-toggle--dark {
  background: var(--color-text);
  border-color: var(--color-text);
}

.theme-toggle--dark span {
  transform: translateX(18px);
}

.account-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
}

.login-entry {
  border: 0;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 4px 6px 8px;
  text-align: left;
  border-radius: 10px;
}

.login-entry:hover {
  background: var(--ui-hover-bg);
}

.login-avatar {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  background: var(--color-hover);
  color: var(--color-text-muted);
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
  max-width: 132px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.login-entry small {
  color: var(--color-text-muted);
  font-size: 12px;
  margin-top: 1px;
}

.logout-btn {
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 700;
}

.logout-btn:hover {
  background: var(--ui-hover-bg);
  color: var(--color-text);
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
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-surface);
  color: var(--color-text);
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
  color: var(--color-text);
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
  color: var(--color-text-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 20px;
  line-height: 1;
}

.rename-modal header button:hover,
.project-modal header button:hover,
.create-project-modal header button:hover {
  background: var(--color-hover);
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
  color: var(--color-text);
  font-size: 14px;
  font-weight: 500;
}

.rename-form input {
  height: 42px;
  min-width: 0;
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  box-sizing: border-box;
  padding: 0 12px;
  font-size: 14px;
}

.rename-form input:focus {
  border-color: var(--color-text);
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
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-muted);
}

.rename-submit {
  border: 1px solid var(--color-text);
  background: var(--color-text);
  color: var(--color-bg);
}

.project-form small {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.project-name-field {
  height: 38px;
  border: 1px solid var(--color-border);
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
  background: var(--color-hover) !important;
}

.project-icon-trigger--muted {
  color: var(--color-text-muted);
}

.create-name-field {
  grid-template-columns: 36px minmax(0, 1fr);
}

.create-name-field .project-icon-trigger {
  color: var(--project-accent-color) !important;
}

.project-name-field input,
.project-form input,
.project-form textarea,
.create-project-modal input {
  min-width: 0;
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
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
  background: var(--color-hover);
  color: var(--color-text-muted);
}

.delete-project-btn {
  width: fit-content;
  height: 38px;
  border: 1px solid #ff2457;
  border-radius: 999px;
  background: var(--color-surface);
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
  border-bottom: 1px solid var(--color-border);
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
  border: 3px solid var(--color-surface);
  outline: 2px solid var(--palette-selection-color, var(--color-text));
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
  color: var(--color-text);
  cursor: pointer;
  display: grid;
  place-items: center;
  padding: 0;
  line-height: 0;
}

.palette-icons button:hover {
  background: var(--color-hover);
}

.palette-icon--active {
  background: var(--color-hover) !important;
  box-shadow: inset 0 0 0 1px var(--color-border);
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
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
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
  background: var(--color-hover);
  color: var(--color-text-muted);
  font-size: 14px;
  line-height: 1.5;
}

.library-field {
  position: relative;
  display: grid;
  gap: 8px;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 500;
}

.library-trigger {
  height: 38px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 12px;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
}

.library-trigger span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.library-menu {
  position: absolute;
  left: 0;
  right: 0;
  top: calc(100% + 6px);
  z-index: 4;
  display: grid;
}

.library-menu button {
  height: var(--ui-menu-item-height);
}

.library-menu-create {
  color: var(--color-primary) !important;
}

.create-project-modal footer {
  display: flex;
  justify-content: flex-end;
}

.create-disabled {
  height: 34px;
  border: 0;
  border-radius: 999px;
  background: var(--color-border);
  color: var(--color-bg);
  padding: 0 14px;
  cursor: not-allowed;
  font-weight: 700;
}

.create-disabled--ready {
  background: var(--color-text);
  color: var(--color-bg);
  cursor: pointer;
}
</style>
