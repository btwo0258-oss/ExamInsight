<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import ContextMenu, { type MenuItem } from '@/components/common/ContextMenu.vue'
import PromptModal from '@/components/common/PromptModal.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import { useMindMapStore } from '@/stores/mindmap'
import { useAuthStore } from '@/stores/auth'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'

const mindMapStore = useMindMapStore()
const authStore = useAuthStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const router = useRouter()
const route = useRoute()
const createOpen = ref(false)
const isCollapsed = ref(localStorage.getItem('llm.sidebar.mm.collapsed') === 'true')

function toggleCollapse(e: MouseEvent) {
  e.stopPropagation()
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('llm.sidebar.mm.collapsed', String(isCollapsed.value))
}

const hoveredId = ref<number | null>(null)
const menuOpenId = ref<number | null>(null)
const menuPos = ref({ x: 0, y: 0 })

// Delete Modal State
const showDeleteConfirm = ref(false)
const deletingId = ref<number | null>(null)
const deletingTitle = ref('')

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

async function handleCreate() {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  openPrompt('新建思维导图', '', async (title) => {
    if (title) {
      const id = await mindMapStore.createMap(title)
      router.push(`/mindmap/${id}`)
    }
  })
}

function handleViewList() {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  router.push('/mindmap')
}

function handleView(id: number) {
  router.push(`/mindmap/${id}`)
}

function toggleActions(e: MouseEvent, id: number) {
  e.stopPropagation()
  if (menuOpenId.value === id) {
    menuOpenId.value = null
  } else {
    menuOpenId.value = id
    const rect = (e.target as HTMLElement).getBoundingClientRect()
    menuPos.value = { x: rect.right, y: rect.top }
  }
}

function closeMenu() {
  menuOpenId.value = null
}

function handleRename(id: number, oldTitle: string) {
  openPrompt('重命名思维导图', oldTitle, (newTitle) => {
    if (newTitle && newTitle !== oldTitle) {
      mindMapStore.renameMap(id, newTitle)
    }
  })
}

async function handleMoveToKnowledgeBase(id: number, kbId: number | null) {
  await mindMapStore.moveToKB(id, kbId)
}

function handleDelete(id: number, title: string) {
  deletingId.value = id
  deletingTitle.value = title
  showDeleteConfirm.value = true
}

async function confirmDelete() {
  if (deletingId.value !== null) {
    await mindMapStore.deleteMap(deletingId.value)
    if (route.params.id === String(deletingId.value)) {
      router.push('/mindmap')
    }
    showDeleteConfirm.value = false
    deletingId.value = null
  }
}

function getMenuItems(id: number, title: string): MenuItem[] {
  const knowledgeBaseItems = knowledgeBaseStore.list.map(kb => ({
    label: kb.name,
    icon: 'folder',
    action: () => handleMoveToKnowledgeBase(id, kb.id)
  }))
  
  return [
    // { label: mindMapStore.isPinned(id) ? '取消置顶' : '置顶', action: () => mindMapStore.togglePin(id) },
    { label: '重命名', icon: 'edit', action: () => handleRename(id, title) },
    {
      label: '移至知识库',
      icon: 'folder',
      children: [
        ...knowledgeBaseItems
      ]
    },
    { label: '', divided: true },
    { label: '删除', icon: 'trash', danger: true, action: () => handleDelete(id, title) },
  ]
}
</script>

<template>
  <div class="section">
    <div class="section__header" @click="handleViewList">
      <div class="header-left">
        <AppIcon name="layers" :size="14" color="var(--color-text-muted)" />
        <span class="section__title">思维导图</span>
      </div>
      <div class="header-right">
        <span v-if="authStore.isAuthed" class="section__count">{{ mindMapStore.mindMapList.length }}</span>
        <button class="collapse-btn" @click.stop="toggleCollapse">
          <AppIcon :name="isCollapsed ? 'chevron-right' : 'chevron-down'" :size="14" />
        </button>
      </div>
    </div>

    <template v-if="!isCollapsed">
      <div class="section__create">
        <button class="create-button" @click="handleCreate">
          <AppIcon name="plus" :size="16" />
          <span class="create-text">新建思维导图</span>
        </button>
      </div>

      <div class="section__list">
        <div
          v-for="map in mindMapStore.mindMapList.slice(0, 5)"
          :key="map.id"
          class="item"
          :class="{ 'item--active': route.params.id === String(map.id) }"
          @click="handleView(map.id)"
          @mouseenter="hoveredId = map.id"
          @mouseleave="hoveredId = null"
        >
          <div class="item__icon">
            <AppIcon name="layers" :size="16" color="#8b5cf6" />
          </div>
          <span class="item__title">{{ map.title }}</span>
          
          <!-- <div v-show="mindMapStore.isPinned(map.id) && hoveredId !== map.id && menuOpenId !== map.id" class="item__pin">
            <AppIcon name="star" :size="12" />
          </div> -->

          <button 
            v-show="hoveredId === map.id || menuOpenId === map.id" 
            class="item__actions" 
            :class="{ 'item__actions--active': menuOpenId === map.id }"
            @click.stop="toggleActions($event, map.id)"
          >
            <AppIcon name="more-horizontal" :size="16" />
          </button>

          <ContextMenu
            v-if="menuOpenId === map.id"
            :x="menuPos.x"
            :y="menuPos.y"
            :items="getMenuItems(map.id, map.title)"
            @close="closeMenu"
            @click.stop
          />
        </div>
      </div>
    </template>
  </div>

  <PromptModal
    :open="promptState.open"
    :title="promptState.title"
    :default-value="promptState.defaultValue"
    label="名称"
    placeholder="请输入思维导图名称"
    @close="promptState.open = false"
    @confirm="handlePromptConfirm"
  />

  <ConfirmDialog
    :open="showDeleteConfirm"
    title="确认删除"
    :message="`确定要删除思维导图'${deletingTitle}'吗？`"
    confirm-text="删除"
    confirm-variant="primary"
    @close="showDeleteConfirm = false"
    @confirm="confirmDelete"
  />
</template>

<style scoped>
.section {
  padding: 8px 16px;
}

.section__header {
  padding: 4px 8px;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  border-radius: 6px;
  transition: background-color 0.2s;
}

.section__header:hover {
  background: var(--color-hover);
}

.header-left, .header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section__title {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-muted);
}

.section__count {
  font-size: 11px;
  color: var(--color-text-muted);
  background: var(--color-bg-alt);
  padding: 1px 6px;
  border-radius: 10px;
}

.collapse-btn {
  background: transparent;
  border: none;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  cursor: pointer;
}

.section__create {
  margin-bottom: 8px;
}

.create-button {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.create-button:hover {
  background: var(--color-hover);
}

.create-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 36px 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-text);
  transition: background-color 0.2s;
  margin-bottom: 2px;
  position: relative;
}

.item:hover {
  background: var(--color-hover);
}

.item--active {
  background: var(--color-hover-strong);
  font-weight: 500;
}

.item__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
}

.item__title {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 20px;
}

.item__pin {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
}

.item__actions {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
}

.item__actions:hover,
.item__actions--active {
  background: var(--color-border);
  color: var(--color-text);
}

</style>
