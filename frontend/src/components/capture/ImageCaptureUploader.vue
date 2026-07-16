<script setup lang="ts">
import { ref } from 'vue'
import { Camera, ImagePlus } from 'lucide-vue-next'
import { IMAGE_ACCEPT, MEDIA_LIMITS, type MediaSource } from '@/types/contracts/media'
import { markMediaSource } from '@/utils/mediaFile'

const props = withDefaults(defineProps<{
  disabled?: boolean
  remainingCount?: number
  showUpload?: boolean
  showCamera?: boolean
}>(), {
  disabled: false,
  remainingCount: MEDIA_LIMITS.composerMaxFiles,
  showUpload: true,
  showCamera: true,
})

const emit = defineEmits<{
  select: [files: File[], source: Extract<MediaSource, 'upload' | 'camera'>]
  error: [message: string]
}>()

const uploadInput = ref<HTMLInputElement | null>(null)
const cameraInput = ref<HTMLInputElement | null>(null)

function validate(files: File[], source: Extract<MediaSource, 'upload' | 'camera'>) {
  if (!files.length) return []
  if (props.remainingCount <= 0) {
    emit('error', `最多只能添加 ${MEDIA_LIMITS.composerMaxFiles} 个附件`)
    return []
  }
  if (files.length > props.remainingCount) {
    emit('error', `还可以添加 ${props.remainingCount} 张图片`)
    return []
  }

  const accepted: File[] = []
  for (const file of files) {
    const extension = file.name.slice(file.name.lastIndexOf('.')).toLowerCase()
    const supported = file.type.startsWith('image/') || ['.jpg', '.jpeg', '.png', '.webp', '.heic', '.heif'].includes(extension)
    if (!supported) {
      emit('error', `文件 ${file.name} 不是受支持的图片`)
      continue
    }
    if (file.size > MEDIA_LIMITS.imageMaxBytes) {
      emit('error', `图片 ${file.name} 超过 10MB 限制`)
      continue
    }
    accepted.push(markMediaSource(file, source))
  }
  return accepted
}

function onChange(event: Event, source: Extract<MediaSource, 'upload' | 'camera'>) {
  const input = event.target as HTMLInputElement
  const files = validate(Array.from(input.files ?? []), source)
  input.value = ''
  if (files.length) emit('select', files, source)
}
</script>

<template>
  <div class="image-actions">
    <button
      v-if="showUpload"
      class="image-action"
      type="button"
      title="上传照片"
      aria-label="上传照片"
      :disabled="disabled || remainingCount <= 0"
      @click="uploadInput?.click()"
    >
      <ImagePlus :size="20" :stroke-width="1.9" />
    </button>
    <button
      v-if="showCamera"
      class="image-action"
      type="button"
      title="拍照"
      aria-label="拍照"
      :disabled="disabled || remainingCount <= 0"
      @click="cameraInput?.click()"
    >
      <Camera :size="20" :stroke-width="1.9" />
    </button>

    <input
      ref="uploadInput"
      hidden
      multiple
      type="file"
      :accept="IMAGE_ACCEPT"
      @change="onChange($event, 'upload')"
    />
    <input
      ref="cameraInput"
      hidden
      type="file"
      :accept="IMAGE_ACCEPT"
      capture="environment"
      @change="onChange($event, 'camera')"
    />
  </div>
</template>

<style scoped>
.image-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.image-action {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}

.image-action:hover:not(:disabled) {
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
}

.image-action:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
