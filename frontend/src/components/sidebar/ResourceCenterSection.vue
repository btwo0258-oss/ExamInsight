<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { useAppState } from '@/stores/appState'

const authStore = useAuthStore()
const appState = useAppState()
const router = useRouter()

function handleViewList() {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  appState.setMode('resource')
  router.push('/resource')
}
</script>

<template>
  <div class="section">
    <div class="section__header" @click="handleViewList" style="cursor: pointer;">
      <div class="title-wrap">
        <AppIcon name="pdf" :size="18" />
        <span class="section__title">资料中心</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.section {
  padding: 8px 16px;
  margin-bottom: 8px;
}

.section__header {
  padding: 8px 12px;
  margin-bottom: 4px;
  border-radius: 8px;
  transition: background-color 0.2s ease;
}

.section__header:hover {
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme='dark'] .section__header:hover {
  background: rgba(255, 255, 255, 0.06);
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}
</style>
