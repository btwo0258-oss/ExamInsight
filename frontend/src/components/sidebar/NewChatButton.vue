<script setup lang="ts">
import AppIcon from '@/components/common/AppIcon.vue'
import { useConversationStore } from '@/stores/conversation'
import { useAuthStore } from '@/stores/auth'

const conversationStore = useConversationStore()
const authStore = useAuthStore()

async function createNewChat() {
  if (!authStore.isAuthed) return authStore.openAuthModal()
  await conversationStore.create()
}
</script>

<template>
  <div class="new-btn-wrap">
    <button class="new-btn" type="button" @click="createNewChat">
      <AppIcon name="edit" />
      <span>开启新对话</span>
    </button>
  </div>
</template>

<style scoped>
.new-btn-wrap {
  padding: 12px 16px;
}

.new-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--color-primary);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  color: var(--color-on-primary);
  transition: all 0.2s ease;
}

.new-btn :deep(svg) {
  color: var(--color-on-primary);
}

.new-btn:hover {
  border-color: var(--color-primary);
}
</style>
