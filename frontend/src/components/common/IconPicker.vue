<script setup lang="ts">
import AppIcon from './AppIcon.vue'

const icons = [
  'book', 'folder', 'dollar', 'graduation', 'pen-tool', 'briefcase', 'notebook', 'airplane', 'flask', 'heart', 'plant', 'clock', 'home', 'settings', 'bell', 'mail', 'calendar', 'camera', 'video', 'image', 'star', 'shield', 'database', 'cloud', 'globe', 'layers', 'layout', 'users', 'pie-chart', 'message-square'
]

type Props = { modelValue: string; compact?: boolean }
defineProps<Props>()
const emit = defineEmits<{ 'update:modelValue': [val: string] }>()
</script>

<template>
  <div class="picker" :class="{ 'picker--compact': compact }">
    <div class="icon-section" role="group" aria-label="项目图标">
      <button
        v-for="i in icons"
        :key="i"
        type="button"
        class="icon-btn"
        :class="{ 'icon-btn--active': modelValue === i }"
        :aria-label="i"
        :aria-pressed="modelValue === i"
        :title="i"
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
.picker--compact .icon-section { box-sizing: border-box; grid-template-columns: repeat(6,minmax(0,1fr)); gap: 7px 5px; padding: 0; }
.picker--compact .icon-btn { box-sizing: border-box; width: 32px; height: 32px; border: 0; border-radius: 8px; background: transparent; color: var(--color-text); box-shadow: none; transform: none; }
.picker--compact .icon-btn:hover, .picker--compact .icon-btn--active { background: var(--color-hover); box-shadow: none; transform: none; }
.picker--compact .icon-btn--active { outline: 1px solid var(--color-border); }
</style>
