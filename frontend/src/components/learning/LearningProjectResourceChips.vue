<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'

type ResourceChip = {
  id: number | string
  group: string
}

const props = defineProps<{
  resources: ResourceChip[]
}>()

const rowRef = ref<HTMLElement>()
const measureRef = ref<HTMLElement>()
const visibleCount = ref(props.resources.length)
let resizeObserver: ResizeObserver | undefined

function resourceIcon(group: string) {
  if (group === '思维导图') return 'mind-topic'
  if (group === '代码案例') return 'code'
  if (group === 'PPT') return 'presentation'
  return 'file'
}

function recalculate() {
  const row = rowRef.value
  const measure = measureRef.value
  if (!row || !measure) return

  const gap = Number.parseFloat(getComputedStyle(row).columnGap) || 0
  const availableWidth = row.clientWidth
  const chips = Array.from(measure.children) as HTMLElement[]
  let usedWidth = 0
  let count = 0

  for (const chip of chips) {
    const nextWidth = usedWidth + (count > 0 ? gap : 0) + chip.getBoundingClientRect().width
    if (nextWidth > availableWidth + 0.5) break
    usedWidth = nextWidth
    count += 1
  }

  visibleCount.value = count
}

watch(
  () => props.resources.map((resource) => `${resource.id}:${resource.group}`).join('|'),
  async () => {
    await nextTick()
    recalculate()
  },
)

onMounted(async () => {
  await nextTick()
  recalculate()
  if (rowRef.value) {
    resizeObserver = new ResizeObserver(recalculate)
    resizeObserver.observe(rowRef.value)
  }
})

onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<template>
  <div ref="rowRef" class="resource-chips" :title="resources.map((resource) => resource.group).join('、')">
    <span v-for="resource in resources.slice(0, visibleCount)" :key="resource.id" class="resource-chip">
      <AppIcon :name="resourceIcon(resource.group)" :size="15" />
      {{ resource.group }}
    </span>

    <div ref="measureRef" class="resource-chip-measure" aria-hidden="true">
      <span v-for="resource in resources" :key="resource.id" class="resource-chip">
        <AppIcon :name="resourceIcon(resource.group)" :size="15" />
        {{ resource.group }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.resource-chips {
  position: relative;
  min-width: 0;
  min-height: 30px;
  max-height: 30px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
  overflow: hidden;
}

.resource-chip {
  flex: 0 0 auto;
  height: 30px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  padding: 0 9px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--color-text-muted);
  font-size: 13px;
  white-space: nowrap;
  box-sizing: border-box;
}

.resource-chip-measure {
  position: absolute;
  left: 0;
  top: 0;
  width: max-content;
  display: flex;
  gap: 8px;
  visibility: hidden;
  pointer-events: none;
}
</style>
