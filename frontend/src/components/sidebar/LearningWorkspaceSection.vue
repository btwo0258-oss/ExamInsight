<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { useAppState } from '@/stores/appState'

const appState = useAppState()
const router = useRouter()

function openLearningWorkspace() {
  appState.setMode('learning')
  router.push('/learning')
}
</script>

<template>
  <div class="section">
    <button class="nav-button" type="button" @click="openLearningWorkspace">
      <AppIcon name="brain" :size="18" />
      <span>学习工作台</span>
    </button>
  </div>
</template>

<style scoped>
.section {
  padding: 8px 16px;
  margin-bottom: 4px;
}

.nav-button {
  width: 100%;
  border: none;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
}

.nav-button:hover {
  background: var(--ui-hover-bg);
}
</style>
