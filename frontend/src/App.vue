<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { RouterView } from 'vue-router'

import AuthModal from '@/components/auth/AuthModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useConversationStore } from '@/stores/conversation'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useExamAnalysisStore } from '@/stores/examAnalysis'

const authStore = useAuthStore()
const themeStore = useThemeStore()
const conversationStore = useConversationStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const examAnalysisStore = useExamAnalysisStore()

onMounted(async () => {
  authStore.init()
  
  // 如果用户已登录，加载对话、知识库和考试分析数据
  if (authStore.isAuthed) {
    await themeStore.syncFromServer()
    await conversationStore.fetchList()
    await knowledgeBaseStore.fetchList()
    await examAnalysisStore.fetchList()
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
