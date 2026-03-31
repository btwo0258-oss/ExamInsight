<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TheSidebar from '@/components/sidebar/TheSidebar.vue'
import KnowledgeBaseList from '@/components/knowledge/KnowledgeBaseList.vue'
import KnowledgeBaseDetail from '@/components/knowledge/KnowledgeBaseDetail.vue'
import AppIcon from '@/components/common/AppIcon.vue'

const route = useRoute()
const sidebarOpen = ref(true)

onMounted(() => {
  const raw = localStorage.getItem('llm.sidebar.open')
  if (raw === '0') sidebarOpen.value = false
})

watch(sidebarOpen, (open) => {
  localStorage.setItem('llm.sidebar.open', open ? '1' : '0')
})
</script>

<template>
  <div class="layout" :class="{ 'layout--open': sidebarOpen }">
    <aside class="drawer" :class="{ 'drawer--open': sidebarOpen }">
      <TheSidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    </aside>

    <main class="content">
      <KnowledgeBaseDetail v-if="route.params.id" :id="String(route.params.id)" />
      <KnowledgeBaseList v-else />
    </main>

    <div v-if="!sidebarOpen" class="mini">
      <button class="mini__btn" type="button" @click="sidebarOpen = true">
        <AppIcon name="sidebar-left" :size="20" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.layout {
  height: 100vh;
  position: relative;
}

.layout--open {
  margin-left: var(--sidebar-width);
}

.drawer {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  width: var(--sidebar-width);
  background: var(--color-sidebar);
  border-right: 1px solid var(--color-border);
  z-index: 30;
  transform: translateX(-100%);
  transition: transform 0.3s ease;
}

.drawer--open {
  transform: translateX(0);
}

.content {
  height: 100vh;
  padding: 0 16px;
  overflow: auto;
}

.mini {
  position: fixed;
  top: 16px;
  left: 16px;
  z-index: 29;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mini__btn {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.mini__btn:hover {
  background: var(--color-surface-hover);
}

.mini__btn svg {
  color: var(--color-text);
}
</style>
