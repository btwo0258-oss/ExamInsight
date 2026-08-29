import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

export function useAnchoredPopover(width = 304, align: 'start' | 'end' = 'end') {
  const trigger = ref<HTMLElement | null>(null)
  const panel = ref<HTMLElement | null>(null)
  const open = ref(false)
  const style = ref<Record<string, string>>({ visibility: 'hidden' })
  function position() {
    if (!open.value || !trigger.value || !panel.value) return
    const rect = trigger.value.getBoundingClientRect()
    const edge = 10, gap = 6
    const actualWidth = Math.min(width, window.innerWidth - edge * 2)
    const below = window.innerHeight - rect.bottom - gap - edge
    const above = rect.top - gap - edge
    const flip = below < panel.value.scrollHeight && above > below
    const maxHeight = Math.max(80, Math.min(520, flip ? above : below))
    const height = Math.min(panel.value.scrollHeight, maxHeight)
    const left = Math.max(edge, Math.min(align === 'end' ? rect.right - actualWidth : rect.left, window.innerWidth - actualWidth - edge))
    style.value = { position: 'fixed', left: `${left}px`, top: `${flip ? Math.max(edge, rect.top - gap - height) : rect.bottom + gap}px`, width: `${actualWidth}px`, maxHeight: `${maxHeight}px`, visibility: 'visible' }
  }
  function close(focus = false) { open.value = false; if (focus) trigger.value?.focus() }
  async function toggle() {
    if (open.value) { close(); return }
    style.value = { visibility: 'hidden', width: `${width}px` }
    open.value = true
    await nextTick()
    position()
  }
  function pointer(event: PointerEvent) {
    const target = event.target as Node
    if (!panel.value?.contains(target) && !trigger.value?.contains(target)) close()
  }
  function key(event: KeyboardEvent) {
    if (event.key === 'Escape' && open.value) { event.preventDefault(); event.stopImmediatePropagation(); close(true) }
  }
  onMounted(() => {
    document.addEventListener('pointerdown', pointer, true)
    document.addEventListener('keydown', key, true)
    window.addEventListener('resize', position)
    window.addEventListener('scroll', position, true)
  })
  onBeforeUnmount(() => {
    document.removeEventListener('pointerdown', pointer, true)
    document.removeEventListener('keydown', key, true)
    window.removeEventListener('resize', position)
    window.removeEventListener('scroll', position, true)
  })
  return { trigger, panel, open, style, position, toggle, close }
}
