<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMindMapStore } from '@/stores/mindmap'
import AppIcon from '@/components/common/AppIcon.vue'
import AppButton from '@/components/common/AppButton.vue'
import AppInput from '@/components/common/AppInput.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import PromptModal from '@/components/common/PromptModal.vue'
import TheSidebar from '@/components/sidebar/TheSidebar.vue'
import { useAuthStore } from '@/stores/auth'

const store = useMindMapStore()
const router = useRouter()
const authStore = useAuthStore()

const sidebarOpen = ref(false)

onMounted(() => {
  const raw = localStorage.getItem('llm.sidebar.open')
  // 如果之前打开了，这里保持打开状态，否则默认关闭
  sidebarOpen.value = raw === '1'
})

watch(sidebarOpen, (open) => {
  localStorage.setItem('llm.sidebar.open', open ? '1' : '0')
})

const searchQuery = ref('')
const showDeleteConfirm = ref(false)
const deletingId = ref<number | null>(null)
const deletingTitle = ref('')

const filteredList = computed(() => {
  return store.mindMapList.filter(map => 
    map.title.toLowerCase().includes(searchQuery.value.toLowerCase())
  )
})

onMounted(async () => {
  if (authStore.isAuthed) {
    await store.fetchList()
  }
})

// Prompt Modal State
const promptState = ref({
  open: false,
  title: '',
  defaultValue: '',
  onConfirm: (val: string) => {}
})

function openPrompt(title: string, defaultValue: string, onConfirm: (val: string) => void) {
  promptState.value = {
    open: true,
    title,
    defaultValue,
    onConfirm
  }
}

function handlePromptConfirm(value: string) {
  promptState.value.onConfirm(value)
}

function handleCreate() {
  if (!authStore.isAuthed) return authStore.openAuthModal()
  openPrompt('新建思维导图', '未命名思维导图', async (title) => {
    if (title) {
      const id = await store.createMap(title)
      router.push(`/mindmap/${id}`)
    }
  })
}

function handleEdit(id: number) {
  router.push(`/mindmap/${id}`)
}

function handleRename(id: number, oldTitle: string) {
  openPrompt('重命名思维导图', oldTitle, (newTitle) => {
    if (newTitle && newTitle !== oldTitle) {
      store.renameMap(id, newTitle)
    }
  })
}

function handleDelete(id: number, title: string) {
  deletingId.value = id
  deletingTitle.value = title
  showDeleteConfirm.value = true
}

async function confirmDelete() {
  if (deletingId.value !== null) {
    await store.deleteMap(deletingId.value)
    showDeleteConfirm.value = false
    deletingId.value = null
  }
}
</script>

<template>
  <div class="layout" :class="{ 'layout--open': sidebarOpen }">
    <aside class="drawer" :class="{ 'drawer--open': sidebarOpen }">
      <TheSidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    </aside>

    <main class="content">
      <div class="mindmap-list-view">
        <div class="header">
          <div class="header__info">
            <h1 class="title">思维导图管理</h1>
            <p class="subtitle">管理您的所有知识脉络与思维导图</p>
          </div>
          <AppButton variant="primary" @click="handleCreate">
            <template #icon><AppIcon name="plus" :size="18" /></template>
            新建导图
          </AppButton>
        </div>

        <div class="filter-bar">
          <div class="search-input">
            <AppIcon name="search" :size="18" />
            <input v-model="searchQuery" placeholder="搜索思维导图..." />
          </div>
        </div>

        <div v-if="filteredList.length === 0" class="empty-state">
          <AppIcon name="layers" :size="64" color="var(--color-text-muted)" />
          <h3>暂无思维导图</h3>
          <p>点击“新建导图”开始记录您的灵感</p>
        </div>

        <div v-else class="grid">
          <div 
            v-for="map in filteredList" 
            :key="map.id" 
            class="map-card"
            @click="handleEdit(map.id)"
          >
            <div class="map-card__preview">
              <AppIcon name="layers" :size="48" color="#8b5cf6" />
            </div>
            <div class="map-card__content">
              <div class="map-card__header">
                <h3 class="map-card__title">{{ map.title }}</h3>
                <div class="map-card__pin" v-if="store.isPinned(map.id)">
                  <AppIcon name="star" :size="14" color="var(--color-primary)" />
                </div>
              </div>
              <div class="map-card__meta">
                <span>更新于 {{ new Date(map.updateTime).toISOString().split('T')[0] }}</span>
              </div>
              <div class="map-card__actions">
                <button class="action-btn" @click.stop="handleRename(map.id, map.title)" title="重命名">
                  <AppIcon name="edit" :size="16" />
                </button>
                <button class="action-btn" @click.stop="store.togglePin(map.id)" :title="store.isPinned(map.id) ? '取消置顶' : '置顶'">
                  <AppIcon name="star" :size="16" :color="store.isPinned(map.id) ? 'var(--color-primary)' : 'inherit'" />
                </button>
                <button class="action-btn action-btn--danger" @click.stop="handleDelete(map.id, map.title)" title="删除">
                  <AppIcon name="trash" :size="16" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <div v-if="!sidebarOpen" class="mini">
      <button class="mini__btn" type="button" @click="sidebarOpen = true">
        <AppIcon name="sidebar-left" :size="20" />
      </button>
    </div>

    <ConfirmDialog
      :open="showDeleteConfirm"
      title="确认删除"
      :message="`确定要删除思维导图'${deletingTitle}'吗？`"
      confirm-text="删除"
      confirm-variant="primary"
      @close="showDeleteConfirm = false"
      @confirm="confirmDelete"
    />

    <PromptModal
      :open="promptState.open"
      :title="promptState.title"
      :default-value="promptState.defaultValue"
      label="名称"
      placeholder="请输入思维导图名称"
      @close="promptState.open = false"
      @confirm="handlePromptConfirm"
    />
  </div>
</template>

<style scoped>
.plain-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-input-bg, #ffffff);
  color: var(--color-text);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.plain-input:focus {
  border-color: var(--color-primary);
}

.layout {
  height: 100vh;
  position: relative;
  display: flex;
  transition: padding-left 180ms ease;
  padding-left: 0;
}

.layout--open {
  padding-left: var(--sidebar-width);
}

.drawer {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
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
  height: 100vh;
  overflow-y: auto;
  min-width: 0;
}

.mini {
  position: fixed;
  top: 12px;
  left: 12px;
  display: inline-flex;
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
  color: var(--color-text-muted);
  display: grid;
  place-items: center;
}

.mini__btn:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--color-text);
}

.mindmap-list-view {
  max-width: 1200px;
  margin: 0 auto;
  padding: 64px 40px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.title {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 4px;
}

.subtitle {
  font-size: 16px;
  color: var(--color-text-muted);
}

.filter-bar {
  margin-bottom: 32px;
}

.search-input {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  padding: 10px 16px;
  border-radius: 12px;
  width: 320px;
  transition: all 0.2s;
}

.search-input:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
}

.search-input input {
  border: none;
  background: transparent;
  outline: none;
  font-size: 14px;
  color: var(--color-text);
  width: 100%;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100px 0;
  color: var(--color-text-muted);
  text-align: center;
}

.empty-state h3 {
  margin-top: 20px;
  font-size: 20px;
  color: var(--color-text);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}

.map-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
}

.map-card:hover {
  transform: translateY(-4px);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-md);
}

.map-card__preview {
  height: 140px;
  background: var(--color-bg-alt);
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid var(--color-border);
}

.map-card__content {
  padding: 16px;
}

.map-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 8px;
}

.map-card__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.map-card__meta {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 16px;
}

.map-card__actions {
  display: flex;
  gap: 8px;
  border-top: 1px solid var(--color-border);
  padding-top: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}

.map-card:hover .map-card__actions {
  opacity: 1;
}

.action-btn {
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  display: flex;
  transition: all 0.2s;
}

.action-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text);
}

.action-btn--danger:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

.confirm-input-box {
  padding: 16px 0;
}

.confirm-label {
  font-size: 14px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}
</style>