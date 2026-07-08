<script setup lang="ts">
type Props = {
  open: boolean
  closeOnBackdrop?: boolean
  width?: string
  maxWidth?: string
  title?: string
}

const props = withDefaults(defineProps<Props>(), { 
  closeOnBackdrop: true,
  width: 'min(560px, 100%)',
  maxWidth: '100%'
})
const emit = defineEmits<{ close: [] }>()

function onBackdrop() {
  if (!props.closeOnBackdrop) return
  emit('close')
}
</script>

<template>
  <teleport to="body">
    <div v-if="open" class="modal" @click.self="onBackdrop">
      <div 
        class="modal__card" 
        :style="{ width: props.width, maxWidth: props.maxWidth }"
        :class="{ 'modal__card--full': props.width === '100vw' }"
      >
        <div v-if="title" class="modal__header">
          <h3 class="modal__title">{{ title }}</h3>
          <button class="modal__close" @click="emit('close')">×</button>
        </div>
        <div class="modal__body">
          <slot />
        </div>
        <div v-if="$slots.footer" class="modal__footer">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(4px);
  z-index: 10000;
}

.modal__card {
  border: 1px solid var(--color-border);
  border-radius: 18px;
  background: var(--color-surface);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  overflow: hidden;
  transition: all 0.2s;
}

.modal__card--full {
  max-height: 100vh;
  height: 100vh;
  border-radius: 0;
  border: none;
}

.modal__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
}

.modal__title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.modal__close {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: var(--color-text-muted);
}

.modal__body {
  padding: 24px;
  flex: 1;
  overflow: auto;
}

.modal__footer {
  padding: 16px 24px;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-alt);
}
</style>
