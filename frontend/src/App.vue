<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { RouterView } from 'vue-router'

import AuthModal from '@/components/auth/AuthModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useConversationStore } from '@/stores/conversation'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'

const authStore = useAuthStore()
const themeStore = useThemeStore()
const conversationStore = useConversationStore()
const knowledgeBaseStore = useKnowledgeBaseStore()

onMounted(async () => {
  themeStore.init()
  authStore.init()
  
  // 如果用户已登录，加载对话和知识库数据
  if (authStore.isAuthed) {
    await conversationStore.fetchList()
    await knowledgeBaseStore.fetchList()
  }
})

// 监听登录状态变化，自动关闭或打开弹窗
watch(() => authStore.isAuthed, async (isAuthed) => {
  if (isAuthed) {
    // 登录成功后加载数据
    await themeStore.syncFromServer()
    await conversationStore.fetchList()
    await knowledgeBaseStore.fetchList()
  }
})
</script>

<template>
  <RouterView />
  <AuthModal :open="authStore.authModalOpen" @close="authStore.closeAuthModal" />
</template>
