<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { isImageFile } from '@/utils/mediaFile'

type Props = { file: File }
const props = defineProps<Props>()
const emit = defineEmits<{ remove: [] }>()

const isImage = computed(() => isImageFile(props.file))
const url = ref('')

function revokePreview() {
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
}

watch(() => props.file, (file) => {
  revokePreview()
  if (isImageFile(file)) url.value = URL.createObjectURL(file)
}, { immediate: true })

onBeforeUnmount(revokePreview)

const fileExtension = computed(() => {
  const name = props.file.name
  const lastDot = name.lastIndexOf('.')
  return lastDot !== -1 ? name.substring(lastDot + 1).toLowerCase() : ''
})

const iconName = computed(() => {
  switch (fileExtension.value) {
    case 'pdf':
      return 'pdf'
    case 'doc':
    case 'docx':
      return 'word'
    case 'md':
      return 'markdown'
    case 'txt':
      return 'txt'
    default:
      return 'file'
  }
})

const iconColor = computed(() => {
  switch (fileExtension.value) {
    case 'pdf':
      return '#ef4444'
    case 'doc':
    case 'docx':
      return '#3b82f6'
    case 'md':
      return '#6366f1'
    case 'txt':
      return '#6b7280'
    default:
      return '#9ca3af'
  }
})
</script>

<template>
  <div class="card">
    <div v-if="isImage" class="card__preview">
      <img :src="url" :alt="file.name" class="card__img" />
    </div>
    <div v-else class="card__file">
      <AppIcon :name="iconName" class="card__icon" :color="iconColor" />
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
