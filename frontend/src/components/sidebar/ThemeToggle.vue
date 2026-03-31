<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
</script>

<template>
  <div class="theme">
    <div class="theme__label">主题</div>
    <button
      class="switch"
      type="button"
      role="switch"
      :aria-checked="themeStore.mode === 'dark'"
      @click="themeStore.toggle"
    >
      <span class="switch__knob" />
    </button>
  </div>
</template>

<style scoped>
.theme {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.theme__label {
  font-size: 13px;
  font-weight: 700;
}

.switch {
  width: 44px;
  height: 26px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: rgba(0, 0, 0, 0.04);
  padding: 2px;
  cursor: pointer;
  position: relative;
}

:root[data-theme='dark'] .switch {
  background: rgba(255, 255, 255, 0.06);
}

.switch__knob {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  display: block;
  transform: translateX(0);
  transition: transform 180ms ease;
}

.switch[aria-checked='true'] .switch__knob {
  transform: translateX(18px);
}
</style>
