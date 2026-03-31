<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'

type Props = { file: File }
const props = defineProps<Props>()
const emit = defineEmits<{ remove: [] }>()

const isImage = computed(() => props.file.type.startsWith('image/'))
const url = computed(() => {
  if (!isImage.value) return ''
  return URL.createObjectURL(props.file)
})
</script>

<template>
  <div class="card">
    <div v-if="isImage" class="card__preview">
      <img :src="url" class="card__img" />
    </div>
    <div v-else class="card__file">
      <AppIcon name="file" class="card__icon" />
      <div class="card__name">{{ file.name }}</div>
    </div>
    <button class="card__del" type="button" @click.stop="emit('remove')">×</button>
  </div>
</template>

<style scoped>
.card {
  position: relative;
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  padding: 6px 10px;
  gap: 8px;
  max-width: 160px;
}

.card__preview {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  overflow: hidden;
}

.card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card__file {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
}

.card__icon {
  flex-shrink: 0;
}

.card__name {
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__del {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 16px;
  height: 16px;
  border-radius: 999px;
  background: var(--color-text);
  color: var(--color-surface);
  border: none;
  font-size: 12px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
}

.card:hover .card__del {
  opacity: 1;
}
</style>
