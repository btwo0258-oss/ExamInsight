<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import MindMap from 'simple-mind-map'
import 'simple-mind-map/dist/simpleMindMap.esm.css'
import type { MindMapRenderConfig, MindMapTreeNode } from '@/types/contracts/artifact'
import { mindMapRenderData, resolveNeutralMindMapRenderConfig } from '@/utils/mindMapTheme'

const props = withDefaults(defineProps<{
  tree: MindMapTreeNode
  renderConfig?: MindMapRenderConfig
  compact?: boolean
}>(), { compact: false })

const container = ref<HTMLElement | null>(null)
const renderFailed = ref(false)
let instance: InstanceType<typeof MindMap> | null = null
let resizeObserver: ResizeObserver | null = null
let themeObserver: MutationObserver | null = null
let renderTimer: number | null = null

const fallbackNodes = computed(() => {
  const result: Array<{ key: string; text: string; depth: number }> = []
  const visit = (node: MindMapTreeNode, depth: number, path: string) => {
    result.push({ key: path, text: node.data?.text || '未命名主题', depth })
    ;(node.children || []).forEach((child, index) => visit(child, depth + 1, `${path}-${index}`))
  }
  visit(normalizeTree(props.tree), 0, 'root')
  return result
})

function normalizeTree(tree: MindMapTreeNode): MindMapTreeNode {
  const cloned = mindMapRenderData(tree || ({ data: { text: '思维导图' }, children: [] } as MindMapTreeNode))
  if (!cloned.data || typeof cloned.data !== 'object') cloned.data = { text: '思维导图' }
  if (!cloned.data.text) cloned.data.text = '思维导图'
  if (!Array.isArray(cloned.children)) cloned.children = []
  cloned.children = cloned.children.map((child) => normalizeTree(child))
  return cloned
}

async function render() {
  await nextTick()
  if (!container.value) return
  if (renderTimer !== null) window.clearTimeout(renderTimer)
  renderFailed.value = false
  instance?.destroy()
  container.value.innerHTML = ''
  try {
    const renderConfig = resolveNeutralMindMapRenderConfig(props.renderConfig)
    instance = new MindMap({
      el: container.value,
      data: mindMapRenderData(normalizeTree(props.tree)),
      theme: renderConfig.theme || 'classic',
      layout: renderConfig.layout || 'logicalStructure',
      readonly: true,
      mousewheelAction: 'zoom',
      initRootNodePosition: ['center', 'center'],
      ...(renderConfig.themeConfig ? { themeConfig: renderConfig.themeConfig } : {}),
    } as any)
    renderTimer = window.setTimeout(() => {
      renderTimer = null
      const current = instance as any
      current?.resize?.()
      const root = current?.renderer?.root
      if (root) current.renderer.moveNodeToCenter(root)
      renderFailed.value = !root || !container.value?.querySelector('svg')
    }, 220)
  } catch (error) {
    console.error('Failed to render mind map preview', error)
    instance = null
    renderFailed.value = true
  }
}

watch(() => [props.tree, props.renderConfig], () => void render(), { deep: true })
onMounted(() => {
  if (container.value && 'ResizeObserver' in window) {
    resizeObserver = new ResizeObserver(() => {
      const current = instance as any
      current?.resize?.()
      const root = current?.renderer?.root
      if (root) current.renderer.moveNodeToCenter(root)
    })
    resizeObserver.observe(container.value)
  }
  if (typeof MutationObserver !== 'undefined') {
    themeObserver = new MutationObserver(() => void render())
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
  }
  void render()
})
onBeforeUnmount(() => {
  if (renderTimer !== null) window.clearTimeout(renderTimer)
  resizeObserver?.disconnect()
  resizeObserver = null
  themeObserver?.disconnect()
  themeObserver = null
  instance?.destroy()
  instance = null
})
</script>

<template>
  <div class="mindmap-preview" :class="{ 'mindmap-preview--compact': compact }">
    <div v-show="!renderFailed" ref="container" class="mindmap-preview__canvas" />
    <div v-if="renderFailed" class="mindmap-preview__fallback">
      <div
        v-for="node in fallbackNodes"
        :key="node.key"
        class="mindmap-preview__fallback-node"
        :class="{ 'mindmap-preview__fallback-node--root': node.depth === 0 }"
        :style="{ marginLeft: `${Math.min(node.depth, 6) * 28}px` }"
      >
        <span />
        <strong>{{ node.text }}</strong>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mindmap-preview { position: relative; width: 100%; height: 100%; min-height: 520px; overflow: hidden; background: var(--color-bg); }
.mindmap-preview__canvas { position: absolute; inset: 0; width: 100%; height: 100%; min-height: inherit; }
.mindmap-preview--compact { min-height: 220px; height: 220px; }
:deep(.simple-mind-map-container) { width: 100%; height: 100%; background: var(--color-bg); }
.mindmap-preview__fallback { height: 100%; min-height: inherit; padding: 28px; overflow: auto; background: var(--color-bg); }
.mindmap-preview__fallback-node { min-height: 36px; display: flex; align-items: center; gap: 10px; color: var(--color-text); }
.mindmap-preview__fallback-node span { width: 8px; height: 8px; flex: 0 0 8px; border-radius: 50%; background: var(--color-text-muted); }
.mindmap-preview__fallback-node strong { max-width: min(720px, 80vw); font-size: 14px; line-height: 1.5; overflow-wrap: anywhere; }
.mindmap-preview__fallback-node--root { margin-bottom: 10px; color: var(--color-bg); }
.mindmap-preview__fallback-node--root strong { padding: 8px 12px; border-radius: 6px; background: var(--color-text); font-size: 16px; }
.mindmap-preview__fallback-node--root span { display: none; }
</style>
