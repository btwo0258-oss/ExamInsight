<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useMessageStore } from '@/stores/message'
import type { ChatMessage } from '@/stores/message'

type Props = { 
  conversationId: number | null 
  containerRef?: HTMLElement | null
}
const props = defineProps<Props>()

const messageStore = useMessageStore()
const isHover = ref(false)
const activeIndex = ref(0)
const isClicking = ref(false)

const tooltipContent = ref('')
const tooltipVisible = ref(false)
const tooltipPos = ref({ top: 0, left: 0 })

let clickTimer: number | null = null

function throttle(fn: Function, delay: number) {
  let lastTime = 0
  return function(this: any, ...args: any) {
    const now = Date.now()
    if (now - lastTime >= delay) {
      lastTime = now
      fn.apply(this, args)
    }
  }
}

const segments = computed(() => {
  if (!props.conversationId) return []
  const allMessages = messageStore.getMessages(props.conversationId)
  
  // 过滤出当前显示的激活版本消息 (逻辑与 MessageArea 保持一致)
  const visibleMessages = allMessages.filter(m => {
    if (!m.turnId) return true // 兼容旧消息
    
    const activeQ = messageStore.getActiveQVersion(props.conversationId!, m.turnId)
    
    if (m.role === 'user') {
      return m.qVersion === activeQ
    } else {
      const activeA = messageStore.getActiveAVersion(props.conversationId!, m.turnId, activeQ)
      return m.qVersion === activeQ && m.aVersion === activeA
    }
  })

  return visibleMessages
    .filter((m: ChatMessage) => m.role === 'user')
    .map((m: ChatMessage, index: number) => ({
      id: m.id,
      index,
      preview: m.content.slice(0, 15) + (m.content.length > 15 ? '...' : ''),
      fullContent: m.content,
    }))
})

const isCompact = computed(() => segments.value.length < 10)

function showTooltip(e: MouseEvent, content: string) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  tooltipContent.value = content
  tooltipPos.value = {
    top: rect.top + rect.height / 2,
    left: rect.left - 8
  }
  tooltipVisible.value = true
}

function hideTooltip() {
  tooltipVisible.value = false
}

function scrollTo(id: string, index: number) {
  activeIndex.value = index
  isClicking.value = true
  if (clickTimer) clearTimeout(clickTimer)
  
  const targetId = `msg-${id}`
  const el = document.getElementById(targetId)
  
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    clickTimer = window.setTimeout(() => {
      isClicking.value = false
    }, 800)
  }
}

function getScrollContainer(): HTMLElement | null {
  if (props.containerRef) return props.containerRef
  return document.querySelector('.message-list')
}

const updateActiveIndex = throttle(() => {
  if (isClicking.value) return
  const container = getScrollContainer()
  if (!container || segments.value.length === 0) return

  const containerRect = container.getBoundingClientRect()
  const containerCenter = containerRect.top + containerRect.height / 2
  
  let closestIndex = 0
  let minDiff = Infinity

  segments.value.forEach((segment, i) => {
    const el = document.getElementById(`msg-${segment.id}`)
    if (el) {
      const rect = el.getBoundingClientRect()
      const elementCenter = rect.top + rect.height / 2
      const diff = Math.abs(elementCenter - containerCenter)
      if (diff < minDiff) {
        minDiff = diff
        closestIndex = i
      }
    }
  })
  
  activeIndex.value = closestIndex
}, 100)

let scrollContainer: HTMLElement | null = null

onMounted(() => {
  nextTick(() => {
    scrollContainer = getScrollContainer()
    if (scrollContainer) {
      scrollContainer.addEventListener('scroll', updateActiveIndex)
      updateActiveIndex()
    }
  })
})

watch(
  () => segments.value.length,
  (newLen, oldLen) => {
    if (newLen > oldLen && oldLen > 0) {
      nextTick(() => {
        const lastSegment = segments.value[segments.value.length - 1]
        if (lastSegment) {
          scrollTo(lastSegment.id, lastSegment.index)
          activeIndex.value = lastSegment.index
        }
      })
    }
  }
)

watch(
  () => segments.value.map(s => s.id).join(','),
  () => {
    nextTick(() => {
      updateActiveIndex()
    })
  }
)

onUnmounted(() => {
  if (clickTimer) clearTimeout(clickTimer)
  scrollContainer?.removeEventListener('scroll', updateActiveIndex)
})
</script>

<template>
  <div 
    class="ds-outline-root"
    :class="{ 'is-hovered': isHover, 'is-compact': isCompact }"
    @mouseenter="isHover = true"
    @mouseleave="isHover = false"
  >
    <div class="outline-wrapper">
      <div class="outline-scroll-viewport">
        <div
          v-for="(seg, idx) in segments"
          :key="seg.id"
          class="outline-row"
          :class="{ 'is-active': idx === activeIndex }"
          @click.stop="scrollTo(seg.id, idx)"
          @mouseenter="showTooltip($event, seg.fullContent)"
          @mouseleave="hideTooltip"
        >
          <span class="row-text">{{ seg.preview }}</span>
          
          <div class="row-indicator">
            <div class="dash-line"></div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <Teleport to="body">
    <div 
      v-if="isHover && tooltipVisible" 
      class="simple-tooltip"
      :style="{
        top: tooltipPos.top + 'px',
        left: tooltipPos.left + 'px'
      }"
    >
      {{ tooltipContent.slice(0, 500) }}{{ tooltipContent.length > 500 ? '...' : '' }}
    </div>
  </Teleport>
</template>

<style scoped>
.ds-outline-root {
  position: fixed;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1000;
  width: 36px;
  transition: width 0.42s cubic-bezier(0.2, 0.8, 0.2, 1);
  display: flex;
  justify-content: flex-end;
}

.ds-outline-root.is-hovered {
  width: 220px;
}

.outline-wrapper {
  width: 36px;
  height: 360px;
  background: rgba(255, 255, 252, 0.62);
  border: 1px solid transparent;
  border-radius: 12px;
  box-shadow: none;
  transform-origin: right center;
  transition:
    width 0.42s cubic-bezier(0.2, 0.8, 0.2, 1),
    background 0.34s ease,
    border-color 0.34s ease,
    box-shadow 0.34s ease;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

:root[data-theme='dark'] .outline-wrapper {
  background: rgba(30, 30, 30, 0.5);
}

.is-compact .outline-wrapper {
  height: auto;
  max-height: 400px;
}

.is-hovered .outline-wrapper {
  width: 220px;
  background: #fffffc;
  border: 1px solid rgba(0,0,0,0.06);
  box-shadow: 0 8px 24px rgba(0,0,0,0.08);
}

:root[data-theme='dark'] .is-hovered .outline-wrapper {
  background: #1e1e1e;
  border-color: rgba(255,255,255,0.08);
  box-shadow: 0 8px 24px rgba(0,0,0,0.4);
}

.outline-scroll-viewport {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 6px 0;
  scrollbar-width: none;
  transition: padding 0.34s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.is-hovered .outline-scroll-viewport {
  padding: 10px 0;
}

.ds-outline-root:not(.is-compact) .outline-scroll-viewport {
  height: 340px;
}

.outline-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 22px;
  padding: 0 7px 0 16px;
  cursor: pointer;
  white-space: nowrap;
  transition:
    height 0.34s cubic-bezier(0.2, 0.8, 0.2, 1),
    padding 0.34s cubic-bezier(0.2, 0.8, 0.2, 1),
    background 0.2s;
}

.is-hovered .outline-row {
  justify-content: space-between;
  height: 32px;
  padding: 0 8px 0 16px;
}

.is-hovered .outline-row:hover {
  background: rgba(0, 0, 0, 0.06);
}

:root[data-theme='dark'] .is-hovered .outline-row:hover {
  background: rgba(255, 255, 255, 0.08);
}

.row-text {
  font-size: 13px;
  color: #888;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-right: 12px;
  flex: 1;
  max-width: 0;
  opacity: 0;
  transform: translateX(8px);
  transition:
    max-width 0.34s cubic-bezier(0.2, 0.8, 0.2, 1),
    opacity 0.2s ease 0.18s,
    transform 0.26s ease 0.14s;
}

.is-hovered .row-text {
  max-width: 170px;
  opacity: 1;
  transform: translateX(0);
}

:root[data-theme='dark'] .row-text { color: #b0b0b0; }
.is-hovered .outline-row:hover .row-text { color: #1a1a1a; }
:root[data-theme='dark'] .is-hovered .outline-row:hover .row-text { color: #ffffff; }
.is-active .row-text { color: #1a1a1a; font-weight: 600; }
:root[data-theme='dark'] .is-active .row-text { color: #ffffff !important; }

.row-indicator {
  width: 20px;
  display: flex;
  justify-content: center;
  flex-shrink: 0;
}

.dash-line {
  width: 12px;
  height: 3px;
  background: #c0c0c0;
  border-radius: 2px;
  transition: all 0.2s;
}

:root[data-theme='dark'] .dash-line { background: #666; }
.is-hovered .outline-row:hover .dash-line {
  width: 16px;
  height: 4px;
  background: #737373;
}
:root[data-theme='dark'] .is-hovered .outline-row:hover .dash-line { background: #d4d4d4; }
.is-active .dash-line { background: #1a1a1a !important; width: 20px; height: 4px; }
:root[data-theme='dark'] .is-active .dash-line { background: #ffffff !important; }

.outline-scroll-viewport::-webkit-scrollbar { width: 0px; }
</style>

<style>
/* 简洁圆角矩形 Tooltip，无箭头 */
.simple-tooltip {
  position: fixed;
  z-index: 9999;
  background: #1f1f1f;
  color: #f0f0f0;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  pointer-events: none;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  
  /* 定位：向左对齐，垂直居中 */
  transform: translate(-100%, -50%);
  
  /* 文本换行 */
  max-width: 300px;
  min-width: 80px;
  word-break: break-all;
  overflow-wrap: break-word;
  white-space: normal;
}

/* 黑夜模式 */
:root[data-theme='dark'] .simple-tooltip {
  background: #2a2a2a;
  color: #e0e0e0;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
}
</style>
