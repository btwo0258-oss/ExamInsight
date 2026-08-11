<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { resourceVisuals } from '@/utils/resourceVisual'
import type { ResourceVisualType } from '@/utils/resourceVisual'

const props = withDefaults(defineProps<{
  type: ResourceVisualType
  size?: number
  containerSize?: number
  variant?: 'badge' | 'plain'
}>(), {
  size: 18,
  containerSize: 30,
  variant: 'badge',
})

const visual = computed(() => resourceVisuals[props.type] ?? resourceVisuals.other)
const iconColor = computed(() => props.variant === 'plain' ? 'currentColor' : visual.value.color)
const style = computed(() => ({
  '--resource-icon-color': iconColor.value,
  '--resource-icon-container-size': `${props.variant === 'plain' ? props.size : props.containerSize}px`,
  '--resource-icon-radius': `${Math.max(4, Math.round(props.containerSize * 0.25))}px`,
}))
</script>

<template>
  <span
    class="resource-type-icon"
    :class="`resource-type-icon--${variant}`"
    :data-resource-type="type"
    :style="style"
    aria-hidden="true"
  >
    <AppIcon :name="visual.icon" :size="size" :color="iconColor" />
  </span>
</template>

<style scoped>
.resource-type-icon {
  width: var(--resource-icon-container-size);
  height: var(--resource-icon-container-size);
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  color: var(--resource-icon-color);
}

.resource-type-icon--badge {
  border-radius: var(--resource-icon-radius);
  background: color-mix(in srgb, var(--resource-icon-color) 12%, var(--color-surface));
}
</style>
