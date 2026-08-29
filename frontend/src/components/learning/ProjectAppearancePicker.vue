<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import IconPicker from '@/components/common/IconPicker.vue'
import ProjectColorEditor from './ProjectColorEditor.vue'
import { useAnchoredPopover } from '@/composables/useAnchoredPopover'
defineProps<{ icon: string; color: string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:icon': [value: string]; 'update:color': [value: string] }>()
const { trigger, panel, open, style, toggle, close, position } = useAnchoredPopover(266, 'start')
const customOpen = ref(false)
watch(open, value => { if (!value) customOpen.value = false })
const colors = [{ value: '#667085', label: '中性色' }, { value: '#ef4444', label: '红色' }, { value: '#f97316', label: '橙色' }, { value: '#fbbf24', label: '黄色' }, { value: '#4caf50', label: '绿色' }, { value: '#3b82f6', label: '蓝色' }, { value: '#8b5cf6', label: '紫色' }, { value: '#ec4899', label: '粉色' }]
async function openPicker() { await toggle(); await nextTick(); panel.value?.querySelector<HTMLButtonElement>('button[aria-pressed="true"]')?.focus() }
async function toggleCustom() { customOpen.value = !customOpen.value; await nextTick(position) }
</script>
<template>
  <button ref="trigger" class="appearance-trigger" type="button" aria-label="选择项目图标和颜色" aria-haspopup="dialog" :aria-expanded="open" :disabled="disabled" :style="{ color: color === '#667085' ? 'var(--color-text)' : color }" @click="openPicker"><AppIcon :name="icon" :size="20" /></button>
  <Teleport to="body"><div v-if="open" ref="panel" data-project-appearance class="appearance-panel ui-menu-panel" role="dialog" aria-label="项目图标和颜色" :style="style">
    <div class="colors" role="group" aria-label="项目颜色"><button v-for="choice in colors" :key="choice.value" type="button" :aria-label="choice.label" :aria-pressed="color.toLowerCase() === choice.value" :class="{ selected: color.toLowerCase() === choice.value }" :style="{ '--swatch': choice.value === '#667085' ? 'var(--color-text)' : choice.value }" @click="emit('update:color', choice.value)" /></div>
    <button class="custom-color" :class="{ expanded: customOpen }" type="button" :aria-expanded="customOpen" @click="toggleCustom"><span class="color-wheel"><i :style="{ background: color }" /></span><span>自定义颜色</span><AppIcon :name="customOpen ? 'chevron-up' : 'chevron-down'" :size="14" /></button>
    <ProjectColorEditor v-if="customOpen" :model-value="color" @update:model-value="emit('update:color', $event)" @resize="position" />
    <div class="picker-grid"><IconPicker :model-value="icon" compact @update:model-value="emit('update:icon', $event); nextTick(position)" /></div>
    <footer><button type="button" @click="close(true)">完成</button></footer>
  </div></Teleport>
</template>
<style scoped>
.appearance-trigger,.appearance-panel,.appearance-panel * { box-sizing: border-box; }
.appearance-trigger { width: 36px; height: 36px; flex: 0 0 36px; display: grid; place-items: center; border: 0; border-radius: 8px; background: transparent; cursor: pointer; }
.appearance-trigger:hover { background: var(--color-hover); }
.appearance-panel { z-index: 20020; border: 1px solid var(--color-border); border-radius: 14px; background: var(--color-surface); box-shadow: var(--shadow-lg); padding: 14px; color: var(--color-text); overflow: auto; overscroll-behavior: contain; scrollbar-width: thin; }
.colors { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px 8px; }
.colors button { width: 24px; height: 24px; border: 0; border-radius: 50%; background: var(--swatch); padding: 0; cursor: pointer; justify-self: center; }
.colors button.selected { box-shadow: inset 0 0 0 3px var(--color-surface); outline: 2px solid var(--swatch); outline-offset: 1px; }
.custom-color { width: 100%; display: flex; align-items: center; gap: 10px; cursor: pointer; font: inherit; font-size: 13px; margin: 14px 0 10px; padding: 6px 4px; border: 0; border-radius: 8px; background: transparent; color: inherit; text-align: left; }
.custom-color:hover,.custom-color.expanded { background: var(--color-hover); }
.custom-color>span:nth-child(2) { flex: 1; }
.color-wheel { width: 24px; height: 24px; flex-shrink: 0; border-radius: 50%; background: conic-gradient(#f44336,#ffeb3b,#4caf50,#2196f3,#9c27b0,#f44336); padding: 2px; }
.color-wheel i { display: block; width: 100%; height: 100%; border-radius: 50%; }
.picker-grid { border-top: 1px solid var(--color-border); padding-top: 14px; }
.appearance-panel footer { border-top: 1px solid var(--color-border); padding-top: 10px; margin-top: 14px; }
.appearance-panel footer button { background: transparent; border: 0; color: inherit; cursor: pointer; font: inherit; font-size: 13px; padding: 5px 4px; }
button:focus-visible { outline: 2px solid var(--color-text-muted); outline-offset: 3px; }
</style>
