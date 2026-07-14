<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import MindMap from 'simple-mind-map'
// @ts-ignore
import KeyboardNavigation from 'simple-mind-map/src/plugins/KeyboardNavigation.js'
// @ts-ignore
import Drag from 'simple-mind-map/src/plugins/Drag.js'
import 'simple-mind-map/dist/simpleMindMap.esm.css'

MindMap.usePlugin(KeyboardNavigation)
MindMap.usePlugin(Drag)

const props = defineProps<{
  treeData?: unknown
  title: string
}>()

const containerRef = ref<HTMLElement | null>(null)
let mindMapInstance: any = null

function fallbackTree() {
  return {
    data: { text: props.title || '思维导图' },
    children: [],
  }
}

function normalizeTreeData() {
  const source = props.treeData || fallbackTree()
  try {
    const cloned = JSON.parse(JSON.stringify(source))
    if (!cloned.data) cloned.data = { text: props.title || '思维导图' }
    if (!cloned.children) cloned.children = []
    return cloned
  } catch {
    return fallbackTree()
  }
}

async function renderMindMap() {
  await nextTick()
  if (!containerRef.value) return

  if (mindMapInstance) {
    mindMapInstance.destroy()
    mindMapInstance = null
  }

  containerRef.value.innerHTML = ''
  mindMapInstance = new MindMap({
    el: containerRef.value,
    data: normalizeTreeData(),
    theme: 'classic',
    layout: 'logicalStructure',
    readonly: true,
    mousewheelAction: 'zoom',
    initRootNodePosition: ['center', 'center'],
    themeConfig: {
      backgroundColor: '#f8fafc',
      lineColor: '#64748b',
      lineWidth: 2,
      rootFillColor: 'transparent',
      rootBorderWidth: 0,
      rootFontSize: 24,
      rootFontWeight: 'bold',
      rootColor: '#111827',
    },
  } as any)

  setTimeout(() => {
    mindMapInstance?.view?.reset?.()
  }, 120)
}

onMounted(renderMindMap)

watch(
  () => props.treeData,
  () => renderMindMap(),
  { deep: true },
)

onBeforeUnmount(() => {
  if (mindMapInstance) {
    mindMapInstance.destroy()
    mindMapInstance = null
  }
})
</script>

<template>
  <div ref="containerRef" class="learning-mindmap-preview" />
</template>

<style scoped>
.learning-mindmap-preview {
  width: 100%;
  min-height: 380px;
  height: 100%;
  background: #f8fafc;
}
</style>
