<script setup lang="ts">
import AppIcon from './AppIcon.vue'

const icons = [
  'book', 'folder', 'dollar', 'graduation', 'pen-tool', 'popcorn', 'palette', 
  'stethoscope', 'flower', 'lotus', 'briefcase', 'bar-chart', 'dumbbell', 
  'notebook', 'scale', 'airplane', 'wrench', 'paw', 'flask', 'brain', 
  'heart', 'plant', 'clock', 'home', 'settings', 'bell', 'mail', 
  'calendar', 'camera', 'video', 'image', 'star', 'shield', 'help-circle', 
  'info', 'alert-circle', 'cpu', 'database', 'cloud', 
  'globe', 'layers', 'layout', 'users', 'pie-chart', 'message-square'
]

type Props = { modelValue: string }
defineProps<Props>()
const emit = defineEmits<{ 'update:modelValue': [val: string] }>()
</script>

<template>
  <div class="picker">
    <div class="icon-section">
      <button
        v-for="i in icons"
        :key="i"
        type="button"
        class="icon-btn"
        :class="{ 'icon-btn--active': modelValue === i }"
        @click="emit('update:modelValue', i)"
      >
        <!-- @ts-ignore -->
        <AppIcon :name="i" :size="20" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
}

.icon-section {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 20px 24px;
  width: 100%;
  padding: 4px;
}

.icon-btn {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.icon-btn:hover {
  background: var(--color-surface-hover);
  border-color: var(--color-primary);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.icon-btn--active {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
  box-shadow: 0 4px 12px var(--color-primary-light);
}

.icon-text {
  font-size: 18px;
}
</style>
