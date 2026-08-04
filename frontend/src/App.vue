<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { RouterView } from 'vue-router'

import AuthModal from '@/components/auth/AuthModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useConversationStore } from '@/stores/conversation'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useExamAnalysisStore } from '@/stores/examAnalysis'
import { useLearningStore } from '@/stores/learning'
import { useLibraryResourceStore } from '@/stores/libraryResource'
import { useMindMapStore } from '@/stores/mindmap'

const authStore = useAuthStore()
const themeStore = useThemeStore()
const conversationStore = useConversationStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const examAnalysisStore = useExamAnalysisStore()
const learningStore = useLearningStore()
const libraryResourceStore = useLibraryResourceStore()
const mindMapStore = useMindMapStore()

async function loadAuthenticatedData() {
  const results = await Promise.allSettled([
    themeStore.syncFromServer(),
    conversationStore.fetchList(),
    knowledgeBaseStore.fetchList(),
    examAnalysisStore.fetchList(),
    learningStore.fetchPlans(),
    libraryResourceStore.fetchList(),
    mindMapStore.fetchList(),
  ])
  results.forEach((result) => {
    if (result.status === 'rejected') console.error('登录数据加载失败:', result.reason)
  })
}

onMounted(async () => {
  await authStore.init()
  if (authStore.isAuthed) await loadAuthenticatedData()
})

// 监听登录状态变化，自动关闭或打开弹窗
watch(() => authStore.isAuthed, async (isAuthed) => {
  if (isAuthed) {
    await loadAuthenticatedData()
  }
})
</script>

<template>
  <RouterView />
  <AuthModal :open="authStore.authModalOpen" @close="authStore.closeAuthModal" />
</template>
