<script setup lang="ts">
import AppIcon from '@/components/admin/AppIcon.vue'

type Props = {
  title: string
  value: string | number
  icon: string
  color?: string
}

withDefaults(defineProps<Props>(), {
  color: 'var(--color-primary)'
})
</script>

<template>
  <div class="stats-card">
    <div class="stats-info">
      <div class="stats-title">{{ title }}</div>
      <div class="stats-value">{{ value }}</div>
    </div>
    <div class="stats-icon" :style="{ color: color }">
      <AppIcon :name="icon" :size="24" />
    </div>
  </div>
</template>

<style scoped>
.stats-card {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: var(--shadow-sm);
  transition: transform 0.2s, box-shadow 0.2s;
}

.stats-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stats-title {
  font-size: 14px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}

.stats-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.2;
}

.stats-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background-color: rgba(0, 0, 0, 0.03);
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
