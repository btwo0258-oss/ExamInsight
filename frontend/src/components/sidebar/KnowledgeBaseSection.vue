<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import KnowledgeBaseItem from './KnowledgeBaseItem.vue'
import KnowledgeBaseCreate from '../knowledge/KnowledgeBaseCreate.vue'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useAuthStore } from '@/stores/auth'
import { useAppState } from '@/stores/appState'

const knowledgeBaseStore = useKnowledgeBaseStore()
const authStore = useAuthStore()
const appState = useAppState()
const router = useRouter()
const createOpen = ref(false)
const isCollapsed = ref(localStorage.getItem('llm.sidebar.kb.collapsed') === 'true')

function handleCreate() {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  createOpen.value = true
}

function toggleCollapse(e: MouseEvent) {
  e.stopPropagation()
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('llm.sidebar.kb.collapsed', String(isCollapsed.value))
}

function handleViewList() {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  appState.setMode('knowledge')
  appState.setActiveKnowledgeBase(null)
  router.push('/knowledge')
}
</script>

<template>
  <div class="section">
    <div class="section__header" @click="handleViewList">
      <div class="header-left">
        <AppIcon name="folder" :size="14" color="var(--color-text-muted)" />
        <span class="section__title">知识库</span>
      </div>
      <div class="header-right">
        <span class="section__count">{{ knowledgeBaseStore.list.length }}</span>
        <button class="collapse-btn" @click.stop="toggleCollapse">
          <AppIcon :name="isCollapsed ? 'chevron-right' : 'chevron-down'" :size="14" />
        </button>
      </div>
    </div>

    <template v-if="!isCollapsed">
      <!-- <div class="section__create">
        <button class="create-button" @click="handleCreate">
          <AppIcon name="plus" :size="16" />
          <span class="create-text">新建知识库</span>
        </button>
      </div> -->

      <div class="section__list">
        <KnowledgeBaseItem
          v-for="kb in knowledgeBaseStore.list"
          :key="kb.id"
          :knowledge-base="kb"
        />
      </div>
    </template>

    <KnowledgeBaseCreate 
      :open="createOpen" 
      :auto-navigate="true"
      :auto-switch-mode="true"
      @close="createOpen = false" 
    />
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
</style>