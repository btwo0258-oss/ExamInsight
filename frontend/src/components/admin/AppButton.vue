<script setup lang="ts">
import AppIcon from './AppIcon.vue'

type Props = {
  variant?: 'primary' | 'ghost' | 'secondary' | 'danger'
  disabled?: boolean
  loading?: boolean
  type?: 'button' | 'submit' | 'reset'
  size?: 'small' | 'medium' | 'large'
}

withDefaults(defineProps<Props>(), {
  variant: 'primary',
  disabled: false,
  loading: false,
  type: 'button',
  size: 'medium'
})
</script>

<template>
  <button 
    class="btn" 
    :class="[`btn--${variant}`, `btn--${size}`, { 'btn--loading': loading }]" 
    :disabled="disabled || loading" 
    :type="type"
  >
    <div v-if="loading" class="loading-icon">
      <AppIcon name="refresh" class="spin" :size="size === 'small' ? 14 : 16" />
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
  border-radius: var(--radius-sm);
  cursor: pointer;
  background: var(--color-surface);
  color: var(--color-text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  position: relative;
  transition: all 0.2s;
  font-weight: 500;
}

.btn--medium {
  padding: 8px 16px;
  font-size: 14px;
}

.btn--small {
  padding: 4px 10px;
  font-size: 12px;
}

.btn--large {
  padding: 12px 24px;
  font-size: 16px;
}

.btn--primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
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
  background: rgba(0, 0, 0, 0.04);
}

.btn--secondary {
  background: var(--color-surface);
  border-color: var(--color-border);
  color: var(--color-text);
}

.btn--secondary:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.02);
}

.btn--danger {
  background: #fef2f2;
  border-color: #fee2e2;
  color: var(--color-danger);
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
  gap: 6px;
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
