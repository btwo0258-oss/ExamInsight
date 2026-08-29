<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Folder,
  GraduationCap,
  LogIn,
  LogOut,
  Menu,
  MessageSquare,
  Moon,
  MoreHorizontal,
  SquarePen,
  Pin,
  PinOff,
  Plus,
  Sun,
  Trash2,
  UserRound,
} from 'lucide-vue-next'

import UserProfileModal from '@/components/auth/UserProfileModal.vue'
import logoUrl from '@/assets/icons/ExamInsight-Logo.png'
import { useAuthStore } from '@/stores/auth'
import { useChatV2Store } from '@/stores/chatV2'
import { useThemeStore } from '@/stores/theme'

const SIDEBAR_WIDTH_STORAGE_KEY = 'examinsight.ui.student-sidebar-width'
const DEFAULT_SIDEBAR_WIDTH = 276
const MIN_SIDEBAR_WIDTH = 232
const MAX_SIDEBAR_WIDTH = 420
const COLLAPSED_SIDEBAR_WIDTH = 72

const props = defineProps<{ compactOnMobile?: boolean }>()
const mobileViewport = typeof window.matchMedia === 'function' ? window.matchMedia('(max-width: 640px)') : null
function syncMobileSidebar() { if (props.compactOnMobile) collapsed.value = Boolean(mobileViewport?.matches) }
const emit = defineEmits<{ widthChange: [width: number] }>()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatV2Store()
const themeStore = useThemeStore()

function clampSidebarWidth(value: number) {
  return Math.min(MAX_SIDEBAR_WIDTH, Math.max(MIN_SIDEBAR_WIDTH, value))
}

function readSidebarWidth() {
  const stored = Number(localStorage.getItem(SIDEBAR_WIDTH_STORAGE_KEY))
  return Number.isFinite(stored) ? clampSidebarWidth(stored) : DEFAULT_SIDEBAR_WIDTH
}

const sidebarWidth = ref(readSidebarWidth())
const collapsed = ref(Boolean(props.compactOnMobile && mobileViewport?.matches))
const isResizing = ref(false)
const recentExpanded = ref(true)
const profileOpen = ref(false)
const accountMenuOpen = ref(false)
const activeMenuId = ref('')
const loadingMoreConversations = ref(false)
const sidebarRoot = ref<HTMLElement | null>(null)
let resizeStartX = 0
let resizeStartWidth = DEFAULT_SIDEBAR_WIDTH

const activeWidth = computed(() => collapsed.value ? COLLAPSED_SIDEBAR_WIDTH : sidebarWidth.value)
const displayName = computed(() => authStore.user?.nickname || authStore.user?.email?.split('@')[0] || '登录')
const activeConversationId = computed(() =>
  route.name === 'chat-detail' ? String(route.params.id || '') : '')
const pinnedConversations = computed(() => chatStore.conversations.filter(item => Boolean(item.pinnedAt)))
const recentConversations = computed(() => chatStore.conversations.filter(item => !item.pinnedAt))

watch(activeWidth, value => emit('widthChange', value), { immediate: true })
watch(sidebarWidth, value => localStorage.setItem(SIDEBAR_WIDTH_STORAGE_KEY, String(value)))
watch(() => route.fullPath, () => {
  activeMenuId.value = ''
  accountMenuOpen.value = false
})

async function startNewChat() {
  chatStore.clearActive()
  await router.push({ name: 'chat' })
}

async function renameConversation(id: string, currentTitle: string) {
  activeMenuId.value = ''
  const title = window.prompt('修改对话标题', currentTitle)?.trim()
  if (title && title !== currentTitle) await chatStore.rename(id, title)
}

async function deleteConversation(id: string) {
  activeMenuId.value = ''
  if (!window.confirm('确定删除这个对话吗？')) return
  await chatStore.remove(id)
  if (activeConversationId.value === id) await router.push({ name: 'chat' })
}

async function togglePinConversation(id: string, pinned: boolean) {
  activeMenuId.value = ''
  try {
    await chatStore.setPinned(id, pinned)
  } catch (cause) {
    window.alert(cause instanceof Error ? cause.message : '保存置顶状态失败。')
  }
}

async function requestMoreConversations() {
  if (loadingMoreConversations.value || !chatStore.hasMoreConversations) return
  loadingMoreConversations.value = true
  try {
    await chatStore.loadList(true)
  } catch (cause) {
    window.alert(cause instanceof Error ? cause.message : '加载更多对话失败。')
  } finally {
    loadingMoreConversations.value = false
  }
}

function handleConversationScroll(event: Event) {
  const element = event.currentTarget as HTMLElement
  if (element.scrollTop + element.clientHeight < element.scrollHeight - 80) return
  void requestMoreConversations()
}

function toggleSidebar() {
  collapsed.value = !collapsed.value
}

function handleResize(event: PointerEvent) {
  sidebarWidth.value = clampSidebarWidth(resizeStartWidth + event.clientX - resizeStartX)
}

function stopResize() {
  if (!isResizing.value) return
  isResizing.value = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  window.removeEventListener('pointermove', handleResize)
  window.removeEventListener('pointerup', stopResize)
  window.removeEventListener('pointercancel', stopResize)
}

function startResize(event: PointerEvent) {
  if (collapsed.value) return
  isResizing.value = true
  resizeStartX = event.clientX
  resizeStartWidth = sidebarWidth.value
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('pointermove', handleResize)
  window.addEventListener('pointerup', stopResize)
  window.addEventListener('pointercancel', stopResize)
}

function resizeByKeyboard(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  event.preventDefault()
  sidebarWidth.value = clampSidebarWidth(sidebarWidth.value + (event.key === 'ArrowRight' ? 12 : -12))
}

async function logout() {
  accountMenuOpen.value = false
  await authStore.logout(router)
}

function closeTransientMenus(event: PointerEvent) {
  if (sidebarRoot.value?.contains(event.target as Node)) return
  activeMenuId.value = ''
  accountMenuOpen.value = false
}

function closeMenusOnEscape(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  activeMenuId.value = ''
  accountMenuOpen.value = false
}

onMounted(() => {
  mobileViewport?.addEventListener('change', syncMobileSidebar)
  authStore.init()
  if (authStore.isAuthed && !chatStore.conversations.length) void chatStore.loadList().catch(() => undefined)
  document.addEventListener('pointerdown', closeTransientMenus)
  document.addEventListener('keydown', closeMenusOnEscape)
})

onBeforeUnmount(() => {
  mobileViewport?.removeEventListener('change', syncMobileSidebar)
  stopResize()
  document.removeEventListener('pointerdown', closeTransientMenus)
  document.removeEventListener('keydown', closeMenusOnEscape)
})
</script>

<template>
  <aside
    ref="sidebarRoot"
    class="student-sidebar"
    :class="{ 'student-sidebar--collapsed': collapsed, 'student-sidebar--resizing': isResizing }"
    :style="{ '--student-sidebar-width': `${activeWidth}px` }"
  >
    <div
      v-if="!collapsed"
      class="sidebar-resize-handle"
      role="separator"
      aria-label="调整侧边栏宽度"
      aria-orientation="vertical"
      tabindex="0"
      @pointerdown="startResize"
      @keydown="resizeByKeyboard"
    />

    <header class="sidebar-header">
      <button class="brand" type="button" @click="startNewChat">
        <span class="brand-logo">
          <img :src="logoUrl" :class="{ 'brand-logo--dark': themeStore.isDark }" alt="" />
        </span>
        <strong v-if="!collapsed">ExamInsight</strong>
      </button>
      <button class="collapse-button" type="button" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" @click="toggleSidebar">
        <ChevronRight v-if="collapsed" :size="18" />
        <ChevronLeft v-else :size="18" />
      </button>
    </header>

    <nav class="primary-nav" aria-label="主要导航">
      <button type="button" :class="{ active: route.name === 'chat' || route.name === 'chat-detail' }" @click="startNewChat">
        <SquarePen :size="19" />
        <span v-if="!collapsed">新对话</span>
      </button>
      <RouterLink
        to="/library"
        :class="{ active: String(route.name).startsWith('library') || route.name === 'resource-preview' }"
      >
        <Folder :size="19" />
        <span v-if="!collapsed">资料库</span>
      </RouterLink>
      <div
        class="primary-nav-row"
        :class="{ active: String(route.name).startsWith('learning') }"
      >
        <RouterLink to="/learning" class="primary-nav-link">
          <GraduationCap :size="19" />
          <span v-if="!collapsed">智能学习</span>
        </RouterLink>
        <button
          v-if="!collapsed"
          class="primary-nav-add"
          type="button"
          aria-label="新建学习项目"
          title="新建学习项目"
          @click.stop="router.push({ name: 'learning-projects', query: { create: '1' } })"
        >
          <Plus :size="16" />
        </button>
      </div>
    </nav>

    <section v-if="!collapsed && authStore.isAuthed" class="conversation-section">
      <button class="section-title" type="button" @click="recentExpanded = !recentExpanded">
        <span>最近</span>
        <ChevronDown :size="14" :class="{ folded: !recentExpanded }" />
      </button>
      <div v-show="recentExpanded" class="conversation-list" @scroll.passive="handleConversationScroll">
        <div v-if="pinnedConversations.length" class="conversation-group-label">已置顶</div>
        <div
          v-for="item in pinnedConversations"
          :key="item.id"
          class="conversation-item"
          :class="{ active: activeConversationId === item.id }"
        >
          <RouterLink :to="`/chat/${item.id}`" :title="item.title">
            <MessageSquare :size="15" />
            <span>{{ item.title }}</span>
          </RouterLink>
          <button
            class="conversation-more"
            type="button"
            aria-label="对话操作"
            @click.stop="activeMenuId = activeMenuId === item.id ? '' : item.id"
          >
            <MoreHorizontal :size="16" />
          </button>
          <div v-if="activeMenuId === item.id" class="conversation-menu">
            <button type="button" @click="renameConversation(item.id, item.title)">重命名</button>
            <button type="button" @click="togglePinConversation(item.id, false)"><PinOff :size="15" />取消置顶</button>
            <button class="danger" type="button" @click="deleteConversation(item.id)"><Trash2 :size="15" />删除</button>
          </div>
        </div>
        <div v-if="pinnedConversations.length" class="conversation-group-label">最近</div>
        <div
          v-for="item in recentConversations"
          :key="item.id"
          class="conversation-item"
          :class="{ active: activeConversationId === item.id }"
        >
          <RouterLink :to="`/chat/${item.id}`" :title="item.title">
            <MessageSquare :size="15" />
            <span>{{ item.title }}</span>
          </RouterLink>
          <button
            class="conversation-more"
            type="button"
            aria-label="对话操作"
            @click.stop="activeMenuId = activeMenuId === item.id ? '' : item.id"
          >
            <MoreHorizontal :size="16" />
          </button>
          <div v-if="activeMenuId === item.id" class="conversation-menu">
            <button type="button" @click="renameConversation(item.id, item.title)">重命名</button>
            <button type="button" @click="togglePinConversation(item.id, true)"><Pin :size="15" />置顶</button>
            <button class="danger" type="button" @click="deleteConversation(item.id)"><Trash2 :size="15" />删除</button>
          </div>
        </div>
        <p v-if="!chatStore.conversations.length">暂无对话</p>
        <button v-if="chatStore.hasMoreConversations" class="load-more-conversations" type="button" :disabled="chatStore.listLoading || loadingMoreConversations" @click="requestMoreConversations">
          {{ chatStore.listLoading || loadingMoreConversations ? '加载中…' : '加载更多' }}
        </button>
      </div>
    </section>

    <footer class="sidebar-footer">
      <div v-if="!collapsed" class="theme-toggle-row">
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
      <button v-else class="compact-action" type="button" aria-label="切换主题" @click="themeStore.toggle">
        <Sun v-if="themeStore.isDark" :size="18" />
        <Moon v-else :size="18" />
      </button>

      <div class="account-wrap">
        <button
          v-if="authStore.isAuthed"
          class="account-button"
          type="button"
          @click="accountMenuOpen = !accountMenuOpen"
        >
          <span class="avatar">{{ Array.from(displayName).slice(0, 1).join('') }}</span>
          <span v-if="!collapsed" class="account-copy">
            <strong>{{ displayName }}</strong>
            <small>查看个人资料</small>
          </span>
          <Menu v-if="!collapsed" :size="16" />
        </button>
        <button v-else class="account-button" type="button" @click="authStore.openAuthModal(route.fullPath)">
          <LogIn :size="19" />
          <span v-if="!collapsed" class="account-copy"><strong>登录</strong><small>登录或注册账户</small></span>
        </button>
        <div v-if="accountMenuOpen && authStore.isAuthed" class="account-menu">
          <button type="button" @click="profileOpen = true; accountMenuOpen = false">
            <UserRound :size="16" />个人资料
          </button>
          <button type="button" @click="logout"><LogOut :size="16" />退出登录</button>
        </div>
      </div>
    </footer>
  </aside>
  <UserProfileModal :open="profileOpen" @close="profileOpen = false" />
</template>

<style scoped>
.student-sidebar {
  position: relative;
  z-index: 50;
  display: flex;
  width: var(--student-sidebar-width);
  height: 100%;
  flex: 0 0 var(--student-sidebar-width);
  box-sizing: border-box;
  flex-direction: column;
  padding: 14px 12px 12px;
  overflow: hidden;
  border-right: 1px solid var(--color-border);
  border-radius: 0 14px 14px 0;
  color: var(--color-text);
  background: var(--color-sidebar);
  transition: width .3s cubic-bezier(.4, 0, .2, 1), flex-basis .3s cubic-bezier(.4, 0, .2, 1), padding .3s ease;
}

.student-sidebar--resizing { transition: none; }
.student-sidebar--collapsed { padding: 14px 0 12px; border-radius: 0 12px 12px 0; }

.sidebar-resize-handle {
  position: absolute;
  z-index: 10;
  top: 14px;
  right: 0;
  bottom: 14px;
  width: 8px;
  cursor: col-resize;
  touch-action: none;
  outline: 0;
}

.sidebar-resize-handle::after {
  position: absolute;
  top: 50%;
  right: 2px;
  width: 3px;
  height: 44px;
  border-radius: 999px;
  background: var(--color-border);
  content: '';
  opacity: 0;
  transform: translateY(-50%);
  transition: opacity .18s ease, background .18s ease;
}

.sidebar-resize-handle:hover::after,
.sidebar-resize-handle:focus-visible::after,
.student-sidebar--resizing .sidebar-resize-handle::after { opacity: 1; background: var(--color-text-muted); }

.sidebar-header { display: flex; height: 48px; align-items: center; justify-content: space-between; gap: 8px; padding: 0 8px; }
.brand { display: flex; min-width: 0; align-items: center; gap: 8px; padding: 6px 10px 6px 6px; border: 0; border-radius: 9px; color: inherit; background: transparent; cursor: pointer; }
.brand-logo { display: grid; width: 32px; height: 32px; flex: 0 0 auto; place-items: center; overflow: hidden; border-radius: 7px; background: var(--color-surface); }
.brand-logo img { width: 100%; height: 100%; object-fit: contain; transition: filter .2s ease; }
.brand-logo img.brand-logo--dark { filter: brightness(0) invert(1); }
.brand strong { overflow: hidden; font-size: 20px; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }
.collapse-button, .compact-action { display: grid; width: 34px; height: 34px; flex: 0 0 auto; padding: 0; place-items: center; border: 0; border-radius: 9px; color: var(--color-text-muted); background: transparent; cursor: pointer; }
.collapse-button:hover, .compact-action:hover { color: var(--color-text); background: var(--ui-hover-strong-bg, var(--color-hover)); }
.student-sidebar--collapsed .sidebar-header {
  display: grid;
  width: 100%;
  height: auto;
  grid-template-columns: 40px;
  align-content: start;
  justify-content: center;
  justify-items: center;
  gap: 8px;
  padding: 0;
}
.student-sidebar--collapsed .brand,
.student-sidebar--collapsed .collapse-button,
.student-sidebar--collapsed .compact-action,
.student-sidebar--collapsed .account-button {
  display: grid;
  width: 40px;
  height: 40px;
  min-height: 40px;
  margin-inline: auto;
  padding: 0;
  place-items: center;
}
.student-sidebar--collapsed .brand-logo { width: 32px; height: 32px; }

.primary-nav { display: grid; gap: 6px; margin-top: 14px; }
.primary-nav button, .primary-nav a { display: flex; width: 100%; min-height: 40px; box-sizing: border-box; align-items: center; gap: 11px; padding: 0 16px; border: 0; border-radius: 9px; color: inherit; background: transparent; font: inherit; font-size: 14px; font-weight: 700; text-decoration: none; cursor: pointer; }
.primary-nav button:hover, .primary-nav a:hover, .primary-nav .active { background: var(--ui-hover-bg, var(--color-hover)); }
.primary-nav-row { display: flex; width: 100%; min-height: 40px; align-items: center; border-radius: 9px; }
.primary-nav-row.active { background: var(--ui-hover-bg, var(--color-hover)); }
.primary-nav-row .primary-nav-link { flex: 1; }
.primary-nav-row .primary-nav-link:hover { background: transparent; }
.primary-nav-add { display: grid !important; width: 32px !important; min-height: 32px !important; margin-right: 4px; padding: 0 !important; place-items: center; border-radius: 7px !important; color: var(--color-text-muted) !important; }
.primary-nav-add:hover, .primary-nav-add:focus-visible { background: var(--ui-hover-strong-bg, var(--color-hover)) !important; color: var(--color-text) !important; }
.student-sidebar--collapsed .primary-nav { width: 100%; justify-items: center; }
.student-sidebar--collapsed .primary-nav button,
.student-sidebar--collapsed .primary-nav a {
  display: grid;
  width: 40px;
  min-height: 40px;
  padding: 0;
  place-items: center;
}
.student-sidebar--collapsed .primary-nav-row { width: 40px; justify-content: center; }
.student-sidebar--collapsed .primary-nav-row .primary-nav-link { width: 40px; flex: 0 0 40px; }

.conversation-section { min-height: 0; flex: 1; margin-top: 22px; overflow: hidden; }
.section-title { display: flex; width: 100%; height: 30px; align-items: center; justify-content: space-between; padding: 0 8px; border: 0; color: var(--color-text-muted); background: transparent; font: inherit; font-size: 12px; font-weight: 600; cursor: pointer; }
.section-title svg { opacity: .58; transition: transform .18s ease; }
.section-title svg.folded { transform: rotate(-90deg); }
.conversation-list { height: calc(100% - 30px); overflow: auto; scrollbar-width: thin; }
.conversation-list > p { margin: 12px 8px; color: var(--color-text-muted); font-size: 12px; }
.conversation-group-label { margin: 10px 8px 4px; color: var(--color-text-muted); font-size: 11px; font-weight: 650; }
.conversation-item { position: relative; display: flex; align-items: center; border-radius: 8px; }
.conversation-item:hover, .conversation-item.active { background: var(--ui-hover-bg, var(--color-hover)); }
.conversation-item > a { display: flex; min-width: 0; height: 36px; flex: 1; align-items: center; gap: 9px; padding: 0 34px 0 8px; color: inherit; font-size: 14px; text-decoration: none; }
.conversation-item > a span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conversation-more { position: absolute; right: 4px; display: none; width: 26px; height: 26px; padding: 0; place-items: center; border: 0; border-radius: 7px; color: var(--color-text-muted); background: transparent; cursor: pointer; }
.conversation-item:hover > .conversation-more, .conversation-item:focus-within > .conversation-more { display: grid; }
.conversation-more:hover { color: var(--color-text); background: var(--ui-hover-strong-bg, var(--color-hover)); }
.conversation-menu, .account-menu { position: absolute; z-index: 80; display: grid; min-width: 132px; padding: 6px; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-surface); box-shadow: var(--shadow-lg); }
.conversation-menu { top: 34px; right: 0; }
.conversation-menu button, .account-menu button { display: flex; min-height: 36px; align-items: center; gap: 8px; padding: 0 10px; border: 0; border-radius: 8px; color: inherit; background: transparent; text-align: left; cursor: pointer; }
.conversation-menu button:hover, .account-menu button:hover { background: var(--ui-hover-bg, var(--color-hover)); }
.conversation-menu button.danger { color: var(--color-danger); }
.load-more-conversations { display: block; width: calc(100% - 16px); min-height: 30px; margin: 8px; padding: 0 8px; border: 1px solid var(--color-border); border-radius: 8px; color: var(--color-text-muted); background: transparent; font: inherit; font-size: 11px; cursor: pointer; }
.load-more-conversations:hover:not(:disabled) { color: var(--color-text); background: var(--ui-hover-bg, var(--color-hover)); }
.load-more-conversations:disabled { cursor: wait; opacity: .65; }

.sidebar-footer { display: grid; flex: 0 0 auto; gap: 12px; margin-top: auto; padding-top: 12px; border-top: 1px solid var(--color-border); }
.theme-toggle-row { display: flex; min-height: 46px; align-items: center; justify-content: space-between; padding: 0 12px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); font-size: 14px; font-weight: 700; }
.theme-toggle { width: 44px; height: 26px; padding: 2px; border: 1px solid var(--color-border); border-radius: 999px; background: var(--color-hover); cursor: pointer; transition: background .18s ease, border-color .18s ease; }
.theme-toggle span { display: block; width: 20px; height: 20px; border-radius: 50%; background: var(--color-surface); box-shadow: 0 1px 3px rgb(15 23 42 / 16%); transition: transform .18s ease; }
.theme-toggle--dark { border-color: var(--color-text); background: var(--color-text); }
.theme-toggle--dark span { transform: translateX(18px); }
.account-wrap { position: relative; }
.account-button { display: flex; width: 100%; min-height: 48px; align-items: center; gap: 10px; padding: 5px 7px; border: 0; border-radius: 10px; color: inherit; background: transparent; cursor: pointer; }
.account-button:hover { background: var(--ui-hover-bg, var(--color-hover)); }
.avatar { display: grid; width: 32px; height: 32px; flex: 0 0 auto; place-items: center; border-radius: 50%; color: var(--color-bg); background: var(--color-text); font-size: 13px; }
.account-copy { display: grid; min-width: 0; flex: 1; gap: 2px; text-align: left; }
.account-copy strong, .account-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.account-copy strong { font-size: 14px; }
.account-copy small { color: var(--color-text-muted); font-size: 11px; font-weight: 400; }
.account-menu { right: 0; bottom: 54px; left: 0; }
.student-sidebar--collapsed .sidebar-footer { width: 100%; justify-items: center; gap: 8px; }
.student-sidebar--collapsed .account-menu { right: auto; bottom: 0; left: 48px; }

@media (prefers-reduced-motion: reduce) {
  .student-sidebar, .section-title svg { transition-duration: .01ms !important; }
}
</style>
