<script setup lang="ts">
import { ref } from 'vue'
import { Check, Copy, LoaderCircle, Pencil, RefreshCw, Square, Volume2 } from 'lucide-vue-next'

import { copyText } from '@/utils/clipboard'

const props = withDefaults(defineProps<{
  role: string
  content: string
  disabled?: boolean
  speechLoading?: boolean
  speechPlaying?: boolean
  speechError?: string
}>(), {
  disabled: false,
  speechLoading: false,
  speechPlaying: false,
  speechError: '',
})

const emit = defineEmits<{
  edit: []
  regenerate: []
  speak: []
}>()
const copied = ref(false)

async function copy() {
  await copyText(props.content)
  copied.value = true
  window.setTimeout(() => { copied.value = false }, 1200)
}
</script>

<template>
  <div class="message-actions" :class="{ 'is-user': role === 'USER' }">
    <div class="message-action-row">
      <button type="button" :title="copied ? '已复制' : '复制'" :disabled="!content" @click="copy">
        <Check v-if="copied" :size="16" />
        <Copy v-else :size="16" />
      </button>
      <button v-if="role === 'USER'" type="button" title="编辑" :disabled="disabled" @click="emit('edit')">
        <Pencil :size="16" />
      </button>
      <template v-else>
        <button
          type="button"
          :title="speechPlaying || speechLoading ? '停止朗读' : '朗读回答'"
          :disabled="!content"
          @click="emit('speak')"
        >
          <LoaderCircle v-if="speechLoading" class="spin" :size="16" />
          <Square v-else-if="speechPlaying" :size="13" fill="currentColor" />
          <Volume2 v-else :size="16" />
        </button>
        <button type="button" title="重新生成" :disabled="disabled" @click="emit('regenerate')">
          <RefreshCw :size="16" />
        </button>
      </template>
    </div>
    <small v-if="speechError" class="speech-error" role="alert">{{ speechError }}</small>
  </div>
</template>

<style scoped>
.message-actions { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; margin-top: 7px; color: var(--color-text-muted); }
.message-actions.is-user { justify-content: flex-end; }
.message-actions.is-user { align-items: flex-end; }
.message-action-row { display: flex; align-items: center; gap: 2px; }
.speech-error { max-width: min(360px, 100%); color: var(--color-danger); font-size: 11px; line-height: 1.4; white-space: normal; }
button { display: grid; width: 30px; height: 30px; padding: 0; place-items: center; border: 0; border-radius: 8px; color: inherit; background: transparent; cursor: pointer; }
button:hover:not(:disabled) { color: var(--color-text); background: var(--color-surface); }
button:disabled { opacity: .4; cursor: not-allowed; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
