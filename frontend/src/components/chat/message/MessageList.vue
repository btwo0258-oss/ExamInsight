<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import MessageBubble from './MessageBubble.vue'
import type { ChatMessage, Citation, MessageVersionGroup } from '@/types/contracts/chatV2'

const ESTIMATED_HEIGHT = 150
const ITEM_GAP = 30
const OVERSCAN_PX = 700

const props = withDefaults(defineProps<{
  messages: ChatMessage[]
  versionGroups: MessageVersionGroup[]
  busy?: boolean
  stageText?: string
  speechLoadingMessageId?: string
  speechPlayingMessageId?: string
  speechErrorMessageId?: string
  speechError?: string
  scrollElement?: HTMLElement | null
}>(), {
  busy: false,
  stageText: '',
  speechLoadingMessageId: '',
  speechPlayingMessageId: '',
  speechErrorMessageId: '',
  speechError: '',
  scrollElement: null,
})

const emit = defineEmits<{
  edit: [messageId: string, content: string]
  regenerate: [messageId: string]
  speak: [message: ChatMessage]
  switchVersion: [branchId: string]
  openAsset: [assetId: string, messageId: string]
  openCitation: [citation: Citation, messageId: string]
  activeUserIndex: [index: number]
  reachTop: []
}>()

const root = ref<HTMLElement | null>(null)
const viewportStart = ref(0)
const viewportEnd = ref(1000)
const layoutRevision = ref(0)
const heights = new Map<string, number>()
const observed = new Map<string, HTMLElement>()
let scrollParent: HTMLElement | null = null
let resizeObserver: ResizeObserver | null = null
let frame = 0
let restoringAnchor = false
// Do not trigger a history request during the initial mount at scrollTop=0.
let topNotified = true

const versionMap = computed(() => new Map(props.versionGroups.map(group => [group.id, group])))
const layout = computed(() => {
  layoutRevision.value
  let top = 0
  return props.messages.map((message, index) => {
    const height = heights.get(message.id) ?? ESTIMATED_HEIGHT
    const item = { message, index, top, height }
    top += height + ITEM_GAP
    return item
  })
})
const totalHeight = computed(() => Math.max(1, layout.value.at(-1)
  ? layout.value.at(-1)!.top + layout.value.at(-1)!.height
  : 1))
const visibleItems = computed(() => layout.value.filter(item => (
  item.top + item.height >= viewportStart.value - OVERSCAN_PX
  && item.top <= viewportEnd.value + OVERSCAN_PX
)))

function updateViewport() {
  if (!scrollParent || !root.value) return
  const listTop = listOffsetTop()
  viewportStart.value = Math.max(0, scrollParent.scrollTop - listTop)
  viewportEnd.value = viewportStart.value + scrollParent.clientHeight

  const nearTop = scrollParent.scrollTop <= 72
  if (nearTop && !topNotified && !restoringAnchor) {
    topNotified = true
    emit('reachTop')
  } else if (!nearTop) {
    topNotified = false
  }

  const center = viewportStart.value + scrollParent.clientHeight / 2
  const users = layout.value.filter(item => item.message.role === 'USER')
  if (users.length) {
    let closest = 0
    let distance = Number.POSITIVE_INFINITY
    users.forEach((item, index) => {
      const current = Math.abs(item.top + item.height / 2 - center)
      if (current < distance) {
        closest = index
        distance = current
      }
    })
    emit('activeUserIndex', closest)
  }
}

function listOffsetTop() {
  if (!scrollParent || !root.value) return 0
  const parentRect = scrollParent.getBoundingClientRect()
  const rootRect = root.value.getBoundingClientRect()
  return rootRect.top - parentRect.top + scrollParent.scrollTop
}

function scheduleViewportUpdate() {
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(updateViewport)
}

function observeItem(messageId: string, element: unknown) {
  const next = element instanceof HTMLElement ? element : null
  const previous = observed.get(messageId)
  if (previous && previous !== next) resizeObserver?.unobserve(previous)
  if (!next) {
    observed.delete(messageId)
    return
  }
  observed.set(messageId, next)
  resizeObserver?.observe(next)
}

async function scrollToMessage(
  messageId: string,
  behavior: ScrollBehavior = 'smooth',
  anchor?: { artifactId?: string; offset?: number },
) {
  const item = layout.value.find(candidate => candidate.message.id === messageId)
  if (!item || !scrollParent || !root.value) return false
  restoringAnchor = Boolean(anchor)
  try {
    scrollParent.scrollTo({
      top: Math.max(0, listOffsetTop() + item.top - (anchor ? 24 : (scrollParent.clientHeight - item.height) / 2)),
      behavior: anchor ? 'auto' : behavior,
    })
    updateViewport()
    await nextTick()
    if (anchor) {
      // Bring the virtual item into the DOM first, then use its measured card position.
      // Bounded frames let ResizeObserver settle; no polling or fetching the whole history.
      for (let attempt = 0; attempt < 3; attempt += 1) {
        await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
        if (!scrollParent || !root.value) return false
        const messageElement = observed.get(messageId)
        const artifactElement = anchor.artifactId && messageElement
          ? [...messageElement.querySelectorAll<HTMLElement>('[data-artifact-id]')]
            .find(element => element.dataset.artifactId === anchor.artifactId)
          : undefined
        const target = artifactElement ?? messageElement
        if (!target) continue
        scrollParent.scrollTop += target.getBoundingClientRect().top
          - scrollParent.getBoundingClientRect().top - (anchor.offset ?? 24)
        updateViewport()
        await nextTick()
      }
    }
    return true
  } finally {
    restoringAnchor = false
  }
}

defineExpose({ scrollToMessage })

function connectScrollParent() {
  scrollParent?.removeEventListener('scroll', scheduleViewportUpdate)
  scrollParent = props.scrollElement ?? root.value?.closest<HTMLElement>('.chat-scroll') ?? null
  scrollParent?.addEventListener('scroll', scheduleViewportUpdate, { passive: true })
  scheduleViewportUpdate()
}

onMounted(() => {
  resizeObserver = new ResizeObserver((entries) => {
    let changed = false
    let anchorDelta = 0
    const shouldFollowLatest = Boolean(!restoringAnchor && scrollParent
      && scrollParent.scrollHeight - scrollParent.scrollTop - scrollParent.clientHeight < 160)
    const previousLayout = new Map(layout.value.map(item => [item.message.id, item]))
    entries.forEach((entry) => {
      const id = (entry.target as HTMLElement).dataset.virtualMessageId
      const height = Math.ceil(entry.borderBoxSize?.[0]?.blockSize ?? entry.contentRect.height)
      if (id && height > 0 && heights.get(id) !== height) {
        const previous = previousLayout.get(id)
        const previousHeight = heights.get(id) ?? ESTIMATED_HEIGHT
        if (previous && previous.top < viewportStart.value) anchorDelta += height - previousHeight
        heights.set(id, height)
        changed = true
      }
    })
    if (!changed) return
    layoutRevision.value += 1
    void nextTick(() => {
      if (scrollParent && !restoringAnchor && shouldFollowLatest) scrollParent.scrollTop = scrollParent.scrollHeight
      else if (scrollParent && !restoringAnchor && anchorDelta) scrollParent.scrollTop += anchorDelta
      scheduleViewportUpdate()
    })
  })
  connectScrollParent()
})

watch(() => props.scrollElement, connectScrollParent)
watch(() => props.messages.length, () => {
  // Allow another top-load after a page has been prepended.
  if (scrollParent && scrollParent.scrollTop > 72) topNotified = false
})
watch(() => props.messages.map(message => `${message.id}:${message.content.length}:${message.status}`).join('|'),
  () => nextTick(scheduleViewportUpdate))

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  scrollParent?.removeEventListener('scroll', scheduleViewportUpdate)
  resizeObserver?.disconnect()
})
</script>

<template>
  <section ref="root" class="virtual-message-list" :style="{ height: `${totalHeight}px` }" aria-live="polite">
    <div
      v-for="item in visibleItems"
      :key="item.message.id"
      :ref="element => observeItem(item.message.id, element)"
      class="virtual-message-item"
      :data-virtual-message-id="item.message.id"
      :style="{ transform: `translateY(${item.top}px)` }"
    >
      <MessageBubble
        :message="item.message"
        :version-group="versionMap.get(item.message.versionGroupId)"
        :busy="busy"
        :stage-text="stageText"
        :speech-loading="speechLoadingMessageId === item.message.id"
        :speech-playing="speechPlayingMessageId === item.message.id"
        :speech-error="speechErrorMessageId === item.message.id ? speechError : ''"
        @edit="(...args) => emit('edit', ...args)"
        @regenerate="emit('regenerate', $event)"
        @speak="emit('speak', $event)"
        @switch-version="emit('switchVersion', $event)"
        @open-asset="(...args) => emit('openAsset', ...args)"
        @open-citation="(...args) => emit('openCitation', ...args)"
      >
        <slot name="after-message" :message="item.message" />
      </MessageBubble>
    </div>
  </section>
</template>

<style scoped>
.virtual-message-list { position: relative; width: min(820px, 100%); margin: 0 auto; contain: layout style; }
.virtual-message-item { position: absolute; top: 0; left: 0; width: 100%; min-width: 0; will-change: transform; }
</style>
