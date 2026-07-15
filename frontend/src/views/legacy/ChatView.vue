<template>
  <div class="layout" :class="{ 'layout--open': sidebarOpen }">
    <aside class="drawer" :class="{ 'drawer--open': sidebarOpen }">
      <TheSidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    </aside>

    <main class="content">
      <ChatWelcome v-if="!conversationStore.list.length" />
      <MessageArea v-else />
    </main>

    <div v-if="!sidebarOpen" class="mini">
      <button class="mini__btn" type="button" @click="sidebarOpen = true">
        <AppIcon name="sidebar-left" :size="20" />
      </button>
      <button class="mini__btn" type="button" @click="onNewChat">
        <AppIcon name="edit" :size="20" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import TheSidebar from '@/components/sidebar/TheSidebar.vue'
import MessageArea from '@/components/legacy/chat/MessageArea.vue'
import ChatWelcome from '@/components/legacy/chat/ChatWelcome.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { useConversationStore } from '@/stores/conversation'

const conversationStore = useConversationStore()
const sidebarOpen = ref(true)

onMounted(() => {
  const raw = localStorage.getItem('llm.sidebar.open')
  if (raw === '0') sidebarOpen.value = false
})

watch(sidebarOpen, (open) => {
  localStorage.setItem('llm.sidebar.open', open ? '1' : '0')
})

async function onNewChat() {
  await conversationStore.create()
}
</script>

<style scoped>
.layout {
  height: 100vh;
  position: relative;
  display: flex;
  transition: padding-left 180ms ease;
  padding-left: 0;
}

.layout--open {
  padding-left: var(--sidebar-width);
}

.drawer {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  width: var(--sidebar-width);
  background: var(--color-sidebar);
  border-right: 1px solid var(--color-border);
  transform: translateX(-100%);
  transition: transform 180ms ease;
  z-index: 30;
}

.drawer--open {
  transform: translateX(0);
}

.content {
  flex: 1;
  height: 100vh;
  display: flex;
  justify-content: center;
  min-width: 0;
}

.mini {
  position: fixed;
  top: 12px;
  left: 12px;
  display: inline-flex;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 999px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  z-index: 25;
}

.mini__btn {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--color-text-muted);
  display: grid;
  place-items: center;
}

.mini__btn:hover {
  background: var(--color-hover);
  color: var(--color-text);
}
</style>
