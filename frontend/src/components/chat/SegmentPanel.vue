<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage } from '@/types/contracts/chatV2'

const props = withDefaults(defineProps<{
  messages: ChatMessage[]
  activeIndex?: number
}>(), { activeIndex: 0 })

const emit = defineEmits<{ navigate: [messageId: string, index: number] }>()
const segments = computed(() => props.messages
  .filter(message => message.role === 'USER')
  .map((message, index) => ({
    id: message.id,
    index,
    preview: message.content.trim().replace(/\s+/g, ' ') || `第 ${index + 1} 个问题`,
  })))
</script>

<template>
  <nav v-if="segments.length" class="segment-panel" aria-label="对话问题导航">
    <div class="segment-list">
      <button
        v-for="segment in segments"
        :key="segment.id"
        type="button"
        :class="{ active: segment.index === activeIndex }"
        :title="segment.preview"
        @click="emit('navigate', segment.id, segment.index)"
      >
        <span>{{ segment.preview }}</span>
        <i />
      </button>
    </div>
  </nav>
</template>

<style scoped>
.segment-panel { position: fixed; top: 50%; right: 12px; z-index: 40; width: 38px; transform: translateY(-50%); transition: width .28s ease; }
.segment-panel:hover, .segment-panel:focus-within { width: 220px; }
.segment-list { max-height: min(420px, 62vh); padding: 7px 0; overflow: auto; border: 1px solid transparent; border-radius: 13px; background: color-mix(in srgb, var(--color-surface) 68%, transparent); scrollbar-width: none; transition: background .25s ease, border-color .25s ease, box-shadow .25s ease; }
.segment-panel:hover .segment-list, .segment-panel:focus-within .segment-list { border-color: var(--color-border); background: var(--color-surface); box-shadow: var(--shadow-lg); }
button { display: flex; width: 100%; height: 27px; padding: 0 7px 0 14px; align-items: center; justify-content: flex-end; border: 0; color: var(--color-text-muted); background: transparent; cursor: pointer; }
button:hover { color: var(--color-text); background: var(--color-hover-strong); }
button span { flex: 1; max-width: 0; margin-right: 10px; overflow: hidden; opacity: 0; text-align: left; text-overflow: ellipsis; white-space: nowrap; transition: max-width .28s ease, opacity .18s ease; }
.segment-panel:hover button span, .segment-panel:focus-within button span { max-width: 168px; opacity: 1; }
button i { display: block; width: 12px; height: 3px; flex: 0 0 auto; border-radius: 3px; background: color-mix(in srgb, var(--color-text-muted) 55%, transparent); transition: width .2s ease, height .2s ease, background .2s ease; }
button.active { color: var(--color-text); font-weight: 600; }
button.active i { width: 21px; height: 4px; background: var(--color-text); }
.segment-list::-webkit-scrollbar { display: none; }
@media (max-width: 900px) { .segment-panel { display: none; } }
</style>
