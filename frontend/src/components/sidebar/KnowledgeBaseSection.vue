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

function handleCreate() {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  createOpen.value = true
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
    <div class="section__header" @click="handleViewList" style="cursor: pointer;">
      <span class="section__title">知识库</span>
    </div>

    <div class="section__create">
      <button class="create-button" @click="handleCreate">
        <AppIcon name="plus" :size="16" />
        <span class="create-text">新建知识库</span>
      </button>
    </div>

    <div class="section__list">
      <KnowledgeBaseItem
        v-for="kb in knowledgeBaseStore.list"
        :key="kb.id"
        :knowledge-base="kb"
      />
    </div>

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
}

.section__title {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-muted);
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