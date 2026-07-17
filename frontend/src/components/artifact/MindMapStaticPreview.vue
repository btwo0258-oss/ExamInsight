<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import MindMap from 'simple-mind-map'
import 'simple-mind-map/dist/simpleMindMap.esm.css'
import type { MindMapTreeNode } from '@/types/contracts/artifact'
import { mindMapRenderData, mindMapThemeConfig } from '@/utils/mindMapTheme'

const props = withDefaults(defineProps<{
  tree: MindMapTreeNode
  compact?: boolean
}>(), { compact: false })

const container = ref<HTMLElement | null>(null)
let instance: InstanceType<typeof MindMap> | null = null

async function render() {
  await nextTick()
  if (!container.value) return
  instance?.destroy()
  container.value.innerHTML = ''
  instance = new MindMap({
    el: container.value,
    data: mindMapRenderData(props.tree),
    theme: 'classic',
    layout: 'logicalStructure',
    readonly: true,
    mousewheelAction: 'zoom',
    initRootNodePosition: ['center', 'center'],
    themeConfig: mindMapThemeConfig(),
  } as any)
  window.setTimeout(() => {
    const root = (instance as any)?.renderer?.root
    if (root) (instance as any).renderer.moveNodeToCenter(root)
  }, 80)
}

watch(() => props.tree, () => void render(), { deep: true })
onMounted(() => void render())
onBeforeUnmount(() => {
  instance?.destroy()
  instance = null
})
</script>

<template>
  <div class="mindmap-preview" :class="{ 'mindmap-preview--compact': compact }">
    <div ref="container" class="mindmap-preview__canvas" />
  </div>
</template>

<style scoped>
.mindmap-preview { width: 100%; height: 100%; min-height: 520px; overflow: hidden; background: #fff; }
.mindmap-preview__canvas { width: 100%; height: 100%; }
.mindmap-preview--compact { min-height: 220px; height: 220px; }
:deep(.simple-mind-map-container) { width: 100%; height: 100%; background: #fff; }
</style>
