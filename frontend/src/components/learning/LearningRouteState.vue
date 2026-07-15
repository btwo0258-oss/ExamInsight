<script setup lang="ts">
defineProps<{
  loading: boolean
  error?: string
  hasPlan: boolean
}>()

const emit = defineEmits<{ retry: []; back: [] }>()
</script>

<template>
  <section v-if="loading" class="learning-route-state" aria-live="polite">
    <strong>正在加载学习项目…</strong>
  </section>
  <section v-else-if="error" class="learning-route-state learning-route-state--error" role="alert">
    <strong>学习项目加载失败</strong>
    <span>{{ error }}</span>
    <button type="button" @click="emit('retry')">重试</button>
  </section>
  <section v-else-if="!hasPlan" class="learning-route-state">
    <strong>学习项目不存在或已被删除</strong>
    <button type="button" @click="emit('back')">返回学习项目</button>
  </section>
</template>

<style scoped>
.learning-route-state {
  min-height: 520px;
  padding: 24px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  background: var(--color-bg);
  color: var(--color-text-muted);
  text-align: center;
}

.learning-route-state strong {
  color: var(--color-text);
  font-size: 18px;
}

.learning-route-state button {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.learning-route-state--error span {
  max-width: 620px;
  color: var(--color-danger);
  overflow-wrap: anywhere;
}
</style>
