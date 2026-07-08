<template>
  <StudentShell>
    <div class="content">
      <ChatWelcome v-if="!conversationStore.list.length" />
      <MessageArea v-else />
    </div>
  </StudentShell>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'

import StudentShell from '@/components/student/StudentShell.vue'
import MessageArea from '@/components/main-area/mode3-chat/MessageArea.vue'
import ChatWelcome from '@/components/main-area/mode1-welcome/ChatWelcome.vue'
import { useConversationStore } from '@/stores/conversation'

const conversationStore = useConversationStore()

onMounted(() => {
  conversationStore.ensureActive(false)
})
</script>

<style scoped>
.content {
  height: 100vh;
  display: flex;
  justify-content: center;
  min-width: 0;
}
</style>
