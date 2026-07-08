<script setup lang="ts">
import AppIcon from './AppIcon.vue'

type Props = {
  variant?: 'primary' | 'ghost' | 'secondary' | 'danger'
  disabled?: boolean
  loading?: boolean
  type?: 'button' | 'submit' | 'reset'
}

withDefaults(defineProps<Props>(), {
  variant: 'primary',
  disabled: false,
  loading: false,
  type: 'button',
})
</script>

<template>
  <button 
    class="btn" 
    :class="[`btn--${variant}`, { 'btn--loading': loading }]" 
    :disabled="disabled || loading" 
    :type="type"
  >
    <div v-if="loading" class="loading-icon">
      <AppIcon name="refresh-cw" class="spin" :size="16" />
    </div>
    <div class="content" :class="{ 'content--hidden': loading }">
      <slot name="icon" />
      <slot />
    </div>
  </button>
</template>

<style scoped>
.btn {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 8px 12px;
  cursor: pointer;
  background: var(--color-surface);
  color: var(--color-text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  position: relative;
  transition: all 0.2s;
}

.btn--primary {
  background: var(--color-text);
  border-color: var(--color-text);
  color: var(--color-bg);
}

.btn--primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn--ghost {
  background: transparent;
  border-color: transparent;
}

.btn--ghost:hover:not(:disabled) {
  background: var(--color-surface-hover);
}

.btn--secondary {
  background: var(--color-surface);
  border-color: var(--color-border);
  color: var(--color-text);
}

.btn--secondary:hover:not(:disabled) {
  background: var(--color-surface-hover);
}

.btn--danger {
  background: #fef2f2;
  border-color: #fee2e2;
  color: #ef4444;
}

.btn--danger:hover:not(:disabled) {
  background: #fee2e2;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.loading-icon {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
}

.content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.content--hidden {
  visibility: hidden;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
