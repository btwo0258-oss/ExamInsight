<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, ChevronRight, FolderOpen, LogIn, LogOut, Menu, MessageSquare, Moon, MoreHorizontal, Plus, Sun, UserRound } from 'lucide-vue-next'

import UserProfileModal from '@/components/auth/UserProfileModal.vue'
import logoUrl from '@/assets/icons/ExamInsight-Logo.png'
import { useAuthStore } from '@/stores/auth'
import { useChatV2Store } from '@/stores/chatV2'
import { useThemeStore } from '@/stores/theme'

const emit = defineEmits<{ widthChange: [width: number] }>()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatV2Store()
const themeStore = useThemeStore()

const collapsed = ref(false)
const profileOpen = ref(false)
const accountMenuOpen = ref(false)
const activeMenuId = ref('')
const width = computed(() => collapsed.value ? 72 : 276)
const displayName = computed(() => authStore.user?.nickname || authStore.user?.email?.split('@')[0] || '登录')

watch(width, value => emit('widthChange', value), { immediate: true })

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
  if (String(route.params.id || '') === id) await router.push({ name: 'chat' })
}

async function logout() {
  accountMenuOpen.value = false
  await authStore.logout(router)
}

onMounted(() => {
  if (authStore.isAuthed && !chatStore.conversations.length) void chatStore.loadList().catch(() => undefined)
})
</script>

<template>
  <aside class="student-sidebar" :class="{ collapsed }">
    <header class="sidebar-header">
      <button class="brand" type="button" @click="startNewChat">
        <img :src="logoUrl" alt="ExamInsight" />
        <strong v-if="!collapsed">ExamInsight</strong>
      </button>
      <button class="collapse-button" type="button" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" @click="collapsed = !collapsed">
        <ChevronRight v-if="collapsed" :size="18" />
        <ChevronLeft v-else :size="18" />
      </button>
    </header>

    <nav class="primary-nav">
      <button type="button" :class="{ active: route.name === 'chat' }" @click="startNewChat">
        <Plus :size="19" /><span v-if="!collapsed">新对话</span>
      </button>
      <RouterLink to="/library" :class="{ active: String(route.name).startsWith('library') || route.name === 'resource-preview' }">
        <FolderOpen :size="19" /><span v-if="!collapsed">资料库</span>
      </RouterLink>
    </nav>

    <section v-if="!collapsed && authStore.isAuthed" class="conversation-section">
      <h2>最近对话</h2>
      <div class="conversation-list">
        <div v-for="item in chatStore.conversations" :key="item.id" class="conversation-item" :class="{ active: route.params.id === item.id }">
          <RouterLink :to="`/chat/${item.id}`"><MessageSquare :size="16" /><span>{{ item.title }}</span></RouterLink>
          <button type="button" aria-label="对话操作" @click.stop="activeMenuId = activeMenuId === item.id ? '' : item.id"><MoreHorizontal :size="16" /></button>
          <div v-if="activeMenuId === item.id" class="conversation-menu">
            <button type="button" @click="renameConversation(item.id, item.title)">重命名</button>
            <button class="danger" type="button" @click="deleteConversation(item.id)">删除</button>
          </div>
        </div>
        <p v-if="!chatStore.conversations.length">暂无对话</p>
      </div>
    </section>

    <footer>
      <button class="theme-button" type="button" @click="themeStore.toggle">
        <Sun v-if="themeStore.isDark" :size="18" />
        <Moon v-else :size="18" />
        <span v-if="!collapsed">{{ themeStore.isDark ? '浅色模式' : '深色模式' }}</span>
      </button>
      <div class="account-wrap">
        <button v-if="authStore.isAuthed" class="account-button" type="button" @click="accountMenuOpen = !accountMenuOpen">
          <span class="avatar">{{ Array.from(displayName).slice(0, 1).join('') }}</span>
          <span v-if="!collapsed" class="account-name">{{ displayName }}</span>
          <Menu v-if="!collapsed" :size="16" />
        </button>
        <button v-else class="account-button" type="button" @click="authStore.openAuthModal(route.fullPath)">
          <LogIn :size="18" /><span v-if="!collapsed">登录</span>
        </button>
        <div v-if="accountMenuOpen && authStore.isAuthed" class="account-menu">
          <button type="button" @click="profileOpen = true; accountMenuOpen = false"><UserRound :size="16" />个人资料</button>
          <button type="button" @click="logout"><LogOut :size="16" />退出登录</button>
        </div>
      </div>
    </footer>
  </aside>
  <UserProfileModal :open="profileOpen" @close="profileOpen = false" />
</template>

<style scoped>
.student-sidebar { position: relative; z-index: 50; display: flex; flex: 0 0 276px; flex-direction: column; width: 276px; height: 100%; padding: 12px; border-right: 1px solid var(--color-border); background: var(--color-sidebar); transition: width .18s ease, flex-basis .18s ease; }
.student-sidebar.collapsed { flex-basis: 72px; width: 72px; padding: 12px 10px; }
.sidebar-header { display: flex; align-items: center; justify-content: space-between; height: 44px; }
.brand { display: flex; min-width: 0; align-items: center; gap: 9px; padding: 5px; border: 0; color: inherit; background: transparent; cursor: pointer; }
.brand img { width: 30px; height: 30px; object-fit: contain; }
.brand strong { overflow: hidden; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.collapse-button { display: grid; width: 32px; height: 32px; padding: 0; place-items: center; border: 0; border-radius: 9px; color: var(--color-text-muted); background: transparent; cursor: pointer; }
.collapse-button:hover { color: var(--color-text); background: var(--color-hover); }
.primary-nav { display: grid; gap: 4px; margin-top: 16px; }
.primary-nav button, .primary-nav a, .theme-button { display: flex; align-items: center; gap: 11px; min-height: 42px; padding: 0 12px; border: 0; border-radius: 11px; color: inherit; background: transparent; font: inherit; text-decoration: none; cursor: pointer; }
.primary-nav button:hover, .primary-nav a:hover, .primary-nav .active, .theme-button:hover { background: var(--color-hover); }
.collapsed .primary-nav button, .collapsed .primary-nav a, .collapsed .theme-button { justify-content: center; padding: 0; }
.conversation-section { min-height: 0; flex: 1; margin-top: 22px; overflow: hidden; }
.conversation-section h2 { margin: 0 9px 8px; color: var(--color-text-muted); font-size: 12px; font-weight: 500; }
.conversation-list { height: calc(100% - 24px); overflow: auto; }
.conversation-list > p { margin: 12px 10px; color: var(--color-text-muted); font-size: 12px; }
.conversation-item { position: relative; display: flex; align-items: center; border-radius: 10px; }
.conversation-item:hover, .conversation-item.active { background: var(--color-hover); }
.conversation-item > a { display: flex; min-width: 0; flex: 1; align-items: center; gap: 9px; height: 38px; padding: 0 9px; color: inherit; text-decoration: none; }
.conversation-item > a span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conversation-item > button { display: none; width: 30px; height: 30px; padding: 0; place-items: center; border: 0; border-radius: 8px; color: inherit; background: transparent; cursor: pointer; }
.conversation-item:hover > button, .conversation-item:focus-within > button { display: grid; }
.conversation-menu, .account-menu { position: absolute; z-index: 70; display: grid; min-width: 130px; padding: 6px; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-bg); box-shadow: var(--shadow-lg); }
.conversation-menu { top: 34px; right: 0; }
.conversation-menu button, .account-menu button { display: flex; align-items: center; gap: 8px; min-height: 36px; padding: 0 10px; border: 0; border-radius: 8px; color: inherit; background: transparent; text-align: left; cursor: pointer; }
.conversation-menu button:hover, .account-menu button:hover { background: var(--color-hover); }
.conversation-menu button.danger { color: var(--color-danger); }
.student-sidebar footer { display: grid; gap: 4px; margin-top: auto; }
.account-wrap { position: relative; }
.account-button { display: flex; width: 100%; height: 46px; align-items: center; gap: 10px; padding: 0 9px; border: 0; border-radius: 12px; color: inherit; background: transparent; cursor: pointer; }
.account-button:hover { background: var(--color-hover); }
.avatar { display: grid; width: 30px; height: 30px; flex: 0 0 auto; place-items: center; border-radius: 50%; color: var(--color-bg); background: var(--color-text); font-size: 13px; }
.account-name { min-width: 0; flex: 1; overflow: hidden; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.account-menu { right: 0; bottom: 50px; left: 0; }
.collapsed .account-button { justify-content: center; padding: 0; }
.collapsed .account-menu { left: 48px; bottom: 0; }
</style>
