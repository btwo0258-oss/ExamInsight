<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { RouterView } from 'vue-router'

import AuthModal from '@/components/auth/AuthModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useConversationStore } from '@/stores/conversation'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useExamAnalysisStore } from '@/stores/examAnalysis'
import { isMockDataSource } from '@/config/dataSource'

const authStore = useAuthStore()
const themeStore = useThemeStore()
const conversationStore = useConversationStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const examAnalysisStore = useExamAnalysisStore()

onMounted(async () => {
  authStore.init()
  
  if (authStore.isAuthed) await themeStore.syncFromServer()

  // Mock guest data is scoped to the current tab and must also be restored on refresh.
  if (authStore.isAuthed || isMockDataSource) {
    await Promise.all([
      conversationStore.fetchList(),
      knowledgeBaseStore.fetchList(),
      examAnalysisStore.fetchList(),
    ])
  }
})

// 监听登录状态变化，自动关闭或打开弹窗
watch(() => authStore.isAuthed, async (isAuthed) => {
  if (isAuthed) {
    // 登录成功后加载数据
    await themeStore.syncFromServer()
    await conversationStore.fetchList()
    await knowledgeBaseStore.fetchList()
    await examAnalysisStore.fetchList()
  }
})
</script>

<template>
  <RouterView />
  <AuthModal :open="authStore.authModalOpen" @close="authStore.closeAuthModal" />
</template>
