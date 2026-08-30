<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { RouterView } from 'vue-router'

import AuthModal from '@/components/auth/AuthModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useChatV2Store } from '@/stores/chatV2'
import { useSmartLearningStore } from '@/stores/smartLearning'

const authStore = useAuthStore()
const chatStore = useChatV2Store()
const learningStore = useSmartLearningStore()

async function loadAuthenticatedData() {
  await Promise.all([
    chatStore.loadList().catch(error => console.error('对话列表加载失败:', error)),
    learningStore.fetchSidebarProjects().catch(error => console.error('学习项目列表加载失败:', error)),
  ])
}

onMounted(async () => {
  await authStore.init()
})

watch(() => authStore.user?.id || '', async userId => {
  if (userId) await loadAuthenticatedData()
}, { immediate: true })
</script>

<template>
  <RouterView />
  <AuthModal :open="authStore.authModalOpen" @close="authStore.closeAuthModal" />
</template>
