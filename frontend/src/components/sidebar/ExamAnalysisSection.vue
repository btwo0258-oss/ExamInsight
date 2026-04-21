<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import ContextMenu, { type MenuItem } from '@/components/common/ContextMenu.vue'
import { useAuthStore } from '@/stores/auth'
import { useAppState } from '@/stores/appState'
import { useExamAnalysisStore } from '@/stores/examAnalysis'

const authStore = useAuthStore()
const appState = useAppState()
const examStore = useExamAnalysisStore()
const router = useRouter()
const route = useRoute()
const isCollapsed = ref(localStorage.getItem('llm.sidebar.exam.collapsed') === 'true')

const hoveredId = ref<number | null>(null)
const menuOpenId = ref<number | null>(null)
const menuPos = ref({ x: 0, y: 0 })

function toggleCollapse(e: MouseEvent) {
  e.stopPropagation()
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('llm.sidebar.exam.collapsed', String(isCollapsed.value))
}

function handleCreate() {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  router.push('/exam-analysis/new')
}

function handleViewList() {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  appState.setMode('exam-analysis' as any)
  router.push('/exam-analysis')
}

function handleView(id: number) {
  router.push(`/exam-analysis/${id}`)
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
  const newTitle = prompt('重命名分析记录', oldTitle)
  if (newTitle && newTitle !== oldTitle) {
    examStore.rename(id, newTitle)
  }
}

function handleDelete(id: number) {
  if (confirm('确定要删除这条试卷分析记录吗？')) {
    examStore.remove(id)
  }
}

function getMenuItems(id: number, title: string): MenuItem[] {
  return [
    { label: '重命名', action: () => handleRename(id, title) },
    { label: '', divided: true },
    { label: '删除', danger: true, action: () => handleDelete(id) },
  ]
}
</script>

<template>
  <div class="section">
    <div class="section__header" @click="handleViewList">
      <div class="header-left">
        <AppIcon name="pie-chart" :size="14" color="var(--color-text-muted)" />
        <span class="section__title">考试分析</span>
      </div>
      <div class="header-right">
        <span class="section__count">{{ examStore.list.length }}</span>
        <button class="collapse-btn" @click.stop="toggleCollapse">
          <AppIcon :name="isCollapsed ? 'chevron-right' : 'chevron-down'" :size="14" />
        </button>
      </div>
    </div>

    <template v-if="!isCollapsed">
      <div class="section__create">
        <!-- <button class="create-button" @click="handleCreate">
          <AppIcon name="plus" :size="16" />
          <span class="create-text">新建分析</span>
        </button> -->
      </div>

      <div class="section__list">
        <div
          v-for="item in examStore.list.slice(0, 5)"
          :key="item.id"
          class="item"
          :class="{ 'item--active': route.params.id === String(item.id) }"
          @click="handleView(item.id)"
          @mouseenter="hoveredId = item.id"
          @mouseleave="hoveredId = null"
        >
          <div class="item__icon">
            <AppIcon name="pie-chart" :size="16" color="var(--color-text-muted)" />
          </div>
          <span class="item__title">{{ item.title }}</span>

          <button 
            v-show="hoveredId === item.id || menuOpenId === item.id" 
            class="item__actions" 
            :class="{ 'item__actions--active': menuOpenId === item.id }"
            @click.stop="toggleActions($event, item.id)"
          >
            <AppIcon name="more-horizontal" :size="16" />
          </button>

          <ContextMenu
            v-if="menuOpenId === item.id"
            :x="menuPos.x"
            :y="menuPos.y"
            :items="getMenuItems(item.id, item.title)"
            @close="closeMenu"
            @click.stop
          />
        </div>
      </div>
    </template>
  </div>
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
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme='dark'] .section__header:hover {
  background: rgba(255, 255, 255, 0.06);
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
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme='dark'] .create-button:hover {
  background: rgba(255, 255, 255, 0.06);
}

.create-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.section__list {
  display: grid;
  gap: 2px;
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
  background: var(--color-surface-hover);
}

.item--active {
  background: var(--color-surface-hover);
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
