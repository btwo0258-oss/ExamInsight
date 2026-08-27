<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { ChatMessage, MessageSegment } from '@/types/contracts/chatV2'

const props = withDefaults(defineProps<{
  messages: ChatMessage[]
  segments?: MessageSegment[]
  activeIndex?: number
}>(), { segments: () => [], activeIndex: 0 })

const emit = defineEmits<{ navigate: [messageId: string, index: number] }>()

const isHover = ref(false)
const tooltipContent = ref('')
const tooltipVisible = ref(false)
const tooltipPos = ref({ top: 0, left: 0 })
let tooltipFrame: number | null = null
let tooltipAnchor: HTMLElement | null = null

const visibleSegments = computed(() => props.segments.length
  ? props.segments.map((segment, index) => ({
      id: segment.id,
      index,
      preview: segment.preview || `第 ${index + 1} 个问题`,
      fullContent: segment.preview || `第 ${index + 1} 个问题`,
    }))
  : props.messages
      .filter(message => message.role === 'USER')
      .map((message, index) => ({
        id: message.id,
        index,
        preview: message.content.trim().slice(0, 15) + (message.content.trim().length > 15 ? '...' : ''),
        fullContent: message.content.trim(),
      })))

const isCompact = computed(() => visibleSegments.value.length < 10)

function showTooltip(event: MouseEvent, content: string) {
  tooltipAnchor = event.currentTarget as HTMLElement
  tooltipContent.value = content
  tooltipVisible.value = false
  if (tooltipFrame !== null) cancelAnimationFrame(tooltipFrame)
  tooltipFrame = requestAnimationFrame(syncTooltipPosition)
}

function syncTooltipPosition() {
  if (!tooltipAnchor || !isHover.value) {
    tooltipFrame = null
    return
  }
  const rect = tooltipAnchor.getBoundingClientRect()
  tooltipPos.value = { top: rect.top + rect.height / 2, left: rect.left - 8 }
  tooltipVisible.value = true
  tooltipFrame = requestAnimationFrame(syncTooltipPosition)
}

function hideTooltip() {
  tooltipAnchor = null
  tooltipVisible.value = false
  if (tooltipFrame !== null) cancelAnimationFrame(tooltipFrame)
  tooltipFrame = null
}

function closeOutline() {
  isHover.value = false
  hideTooltip()
}

function navigate(segment: { id: string; index: number }) {
  emit('navigate', segment.id, segment.index)
}

watch(() => visibleSegments.value.map(segment => segment.id).join(','), () => {
  nextTick(() => {
    if (!visibleSegments.value.length) closeOutline()
  })
})

onBeforeUnmount(() => hideTooltip())
</script>

<template>
  <nav
    v-if="visibleSegments.length"
    class="segment-panel ds-outline-root"
    :class="{ 'is-hovered': isHover, 'is-compact': isCompact }"
    aria-label="对话问题导航"
    @pointerenter="isHover = true"
    @pointerleave="closeOutline"
    @focusin="isHover = true"
  >
    <div class="outline-wrapper">
      <div class="outline-scroll-viewport">
        <button
          v-for="segment in visibleSegments"
          :key="segment.id"
          type="button"
          class="outline-row"
          :class="{ 'is-active': segment.index === activeIndex }"
          :aria-label="`第 ${segment.index + 1} 个问题：${segment.preview}`"
          @click="navigate(segment)"
          @mouseenter="showTooltip($event, segment.fullContent)"
          @mouseleave="hideTooltip"
        >
          <span class="row-text">{{ segment.preview }}</span>
          <span class="row-indicator"><i class="dash-line" /></span>
        </button>
      </div>
    </div>
  </nav>

  <Teleport to="body">
    <div
      v-if="isHover && tooltipVisible"
      class="simple-tooltip"
      :style="{ top: `${tooltipPos.top}px`, left: `${tooltipPos.left}px` }"
    >
      {{ tooltipContent.slice(0, 500) }}{{ tooltipContent.length > 500 ? '...' : '' }}
    </div>
  </Teleport>
</template>

<style scoped>
.ds-outline-root {
  position: fixed;
  top: 50%;
  right: 12px;
  z-index: 40;
  display: flex;
  width: 38px;
  justify-content: flex-end;
  transform: translateY(-50%);
  transition: width .28s ease;
}
.ds-outline-root.is-hovered { width: 220px; }
.outline-wrapper {
  display: flex;
  width: 100%;
  max-height: min(420px, 62vh);
  flex-direction: column;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 13px;
  background: color-mix(in srgb, var(--color-surface) 68%, transparent);
  transition: background .25s ease, border-color .25s ease, box-shadow .25s ease;
}
.is-hovered .outline-wrapper { border-color: var(--color-border); background: var(--color-surface); box-shadow: var(--shadow-lg); }
.outline-scroll-viewport { flex: 1; padding: 7px 0; overflow-x: hidden; overflow-y: auto; scrollbar-width: none; }
.outline-row {
  display: flex;
  width: 100%;
  height: 27px;
  align-items: center;
  justify-content: flex-end;
  padding: 0 7px 0 14px;
  border: 0;
  color: var(--color-text-muted);
  background: transparent;
  cursor: pointer;
  white-space: nowrap;
}
.outline-row:hover { color: var(--color-text); background: var(--color-hover-strong); }
.row-text {
  flex: 1;
  max-width: 0;
  margin-right: 10px;
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 13px;
  opacity: 0;
  text-align: left;
  text-overflow: ellipsis;
  transition: max-width .28s ease, opacity .18s ease;
}
.is-hovered .row-text { max-width: 168px; opacity: 1; }
.outline-row:hover .row-text, .is-active .row-text { color: var(--color-text); }
.is-active .row-text { font-weight: 600; }
.row-indicator { display: flex; width: 21px; flex-shrink: 0; justify-content: center; }
.dash-line { display: block; width: 12px; height: 3px; border-radius: 3px; background: color-mix(in srgb, var(--color-text-muted) 55%, transparent); transition: width .2s ease, height .2s ease, background .2s ease; }
.is-active .dash-line { width: 21px; height: 4px; background: var(--color-text); }
.outline-scroll-viewport::-webkit-scrollbar { width: 0; }
@media (max-width: 900px) { .ds-outline-root { display: none; } }
</style>

<style>
.simple-tooltip { position: fixed; z-index: 9999; max-width: 300px; min-width: 80px; padding: 10px 14px; border-radius: 8px; color: var(--color-tooltip-text); background: var(--color-tooltip-bg); box-shadow: var(--shadow-md); font-size: 13px; line-height: 1.6; pointer-events: none; transform: translate(-100%, -50%); word-break: break-all; overflow-wrap: break-word; white-space: normal; }
</style>
