<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { RouterView } from 'vue-router'

import AuthModal from '@/components/auth/AuthModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useChatV2Store } from '@/stores/chatV2'

const authStore = useAuthStore()
const chatStore = useChatV2Store()

async function loadAuthenticatedData() {
  await chatStore.loadList().catch(error => console.error('对话列表加载失败:', error))
}

onMounted(async () => {
  await authStore.init()
  if (authStore.isAuthed) await loadAuthenticatedData()
})

watch(() => authStore.isAuthed, async isAuthed => {
  if (isAuthed) await loadAuthenticatedData()
})
</script>

<template>
  <RouterView />
  <AuthModal :open="authStore.authModalOpen" @close="authStore.closeAuthModal" />
</template>
