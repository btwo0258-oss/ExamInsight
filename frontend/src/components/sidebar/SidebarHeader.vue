<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { useAppState } from '@/stores/appState'
import logoUrl from '@/assets/icons/Aether Logo SVG.jpg'

const router = useRouter()
const appState = useAppState()
const emit = defineEmits<{ close: [] }>()

function goHome() {
  appState.setMode('chat')
  router.push('/chat')
}
</script>

<template>
  <div class="header">
    <div class="header__brand" @click="goHome">
      <img :src="logoUrl" alt="Logo" class="logo-img" />
      <span>Aether</span>
    </div>
    <button class="header__btn" @click="emit('close')">
      <AppIcon name="sidebar-left" :size="20" />
    </button>
  </div>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
}

.header__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 15px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background-color 0.2s ease;
}

.logo-img {
  width: 20px;
  height: 20px;
  object-fit: contain;
  border-radius: 4px;
}

.header__brand:hover {
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme='dark'] .header__brand:hover {
  background: rgba(255, 255, 255, 0.06);
}

.header__btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--color-text-muted);
  display: grid;
  place-items: center;
}

.header__btn:hover {
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme='dark'] .header__btn:hover {
  background: rgba(255, 255, 255, 0.06);
}
</style>
