<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { copyText } from '@/utils/clipboard'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; resize: [] }>()
const hexInput = ref<HTMLInputElement | null>(null)
const hexDraft = ref(props.modelValue.toUpperCase())
const copyStatus = ref('')
const hue = ref(0), saturation = ref(0), brightness = ref(0)
const hexValid = computed(() => /^#?[\da-f]{6}$/i.test(hexDraft.value.trim()))
const colorDescription = computed(() => `饱和度 ${Math.round(saturation.value)}%，亮度 ${Math.round(brightness.value)}%`)
const clamp = (value: number, max = 100) => Math.max(0, Math.min(max, value))

function readColor(hex: string) {
  if (!/^#[\da-f]{6}$/i.test(hex)) return
  const r = parseInt(hex.slice(1, 3), 16) / 255
  const g = parseInt(hex.slice(3, 5), 16) / 255
  const b = parseInt(hex.slice(5, 7), 16) / 255
  const max = Math.max(r, g, b), min = Math.min(r, g, b), delta = max - min
  if (delta) hue.value = ((max === r ? (g - b) / delta : max === g ? (b - r) / delta + 2 : (r - g) / delta + 4) * 60 + 360) % 360
  saturation.value = max ? delta / max * 100 : 0
  brightness.value = max * 100
}

function currentHex() {
  const s = saturation.value / 100, v = brightness.value / 100
  const channel = (offset: number) => {
    const k = (offset + hue.value / 60) % 6
    return Math.round(255 * (v - v * s * Math.max(0, Math.min(k, 4 - k, 1)))).toString(16).padStart(2, '0')
  }
  return `#${channel(5)}${channel(3)}${channel(1)}`
}

// Black/white lose hue in RGB; don't reset a hue the user is actively dragging.
watch(() => props.modelValue, color => {
  if (currentHex().toLowerCase() !== color.toLowerCase()) readColor(color)
  hexDraft.value = color.toUpperCase()
  copyStatus.value = ''
}, { immediate: true })
watch([hexValid, copyStatus], async () => { await nextTick(); emit('resize') })

function publishColor() {
  const color = currentHex()
  hexDraft.value = color.toUpperCase()
  copyStatus.value = ''
  emit('update:modelValue', color)
}

function updatePlane(event: PointerEvent) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  if (!rect.width || !rect.height) return
  saturation.value = clamp((event.clientX - rect.left) / rect.width * 100)
  brightness.value = clamp((1 - (event.clientY - rect.top) / rect.height) * 100)
  publishColor()
}

function startPlane(event: PointerEvent) {
  if (event.button !== 0) return
  const plane = event.currentTarget as HTMLElement
  plane.focus({ preventScroll: true })
  plane.setPointerCapture(event.pointerId)
  updatePlane(event)
}

function dragPlane(event: PointerEvent) {
  if ((event.currentTarget as HTMLElement).hasPointerCapture(event.pointerId)) updatePlane(event)
}

function stopPlane(event: PointerEvent) {
  const plane = event.currentTarget as HTMLElement
  if (plane.hasPointerCapture(event.pointerId)) plane.releasePointerCapture(event.pointerId)
}

function adjustPlane(event: KeyboardEvent) {
  const step = event.shiftKey ? 10 : 1
  if (event.key === 'ArrowLeft') saturation.value = clamp(saturation.value - step)
  else if (event.key === 'ArrowRight') saturation.value = clamp(saturation.value + step)
  else if (event.key === 'ArrowUp') brightness.value = clamp(brightness.value + step)
  else if (event.key === 'ArrowDown') brightness.value = clamp(brightness.value - step)
  else return
  event.preventDefault()
  publishColor()
}

function changeHue(event: Event) {
  hue.value = clamp(Number((event.target as HTMLInputElement).value), 360)
  publishColor()
}

function commitHex() {
  if (!hexValid.value) return
  const color = `#${hexDraft.value.trim().replace(/^#/, '')}`.toLowerCase()
  readColor(color)
  hexDraft.value = color.toUpperCase()
  copyStatus.value = ''
  emit('update:modelValue', color)
}

async function copyColor() {
  if (!hexValid.value) return
  commitHex()
  try {
    await copyText(hexDraft.value)
    copyStatus.value = '颜色已复制'
  } catch {
    hexInput.value?.focus()
    hexInput.value?.select()
    copyStatus.value = '复制失败，请手动复制颜色值'
  }
}
</script>

<template>
  <div class="custom-color-editor" role="group" aria-label="自定义项目颜色">
    <div class="color-plane" :style="{ '--hue': hue }" tabindex="0" role="slider" aria-label="饱和度和亮度，左右键调饱和度，上下键调亮度" :aria-valuenow="Math.round(saturation)" :aria-valuetext="colorDescription" :aria-valuemin="0" :aria-valuemax="100" @pointerdown.prevent="startPlane" @pointermove="dragPlane" @pointerup="stopPlane" @pointercancel="stopPlane" @keydown="adjustPlane">
      <span class="color-cursor" :style="{ left: `${saturation}%`, top: `${100 - brightness}%`, background: modelValue }" />
    </div>
    <input class="hue-slider" type="range" min="0" max="360" step="1" :value="hue" :style="{ '--hue': hue }" aria-label="色相" @input="changeHue" />
    <div class="hex-row"><span>十六进制</span><span class="hex-field" :class="{ invalid: !hexValid }">
      <input ref="hexInput" v-model="hexDraft" aria-label="十六进制颜色" :aria-invalid="!hexValid" maxlength="7" spellcheck="false" autocomplete="off" @input="commitHex" @blur="commitHex" @keydown.enter.prevent="commitHex" />
      <button type="button" :disabled="!hexValid" aria-label="复制颜色值" :title="copyStatus || '复制颜色值'" @click="copyColor"><AppIcon :name="copyStatus === '颜色已复制' ? 'check' : 'copy'" :size="14" /></button>
    </span></div>
    <small v-if="!hexValid" class="hex-error">请输入六位颜色值，例如 #3B82F6</small>
    <small v-if="copyStatus" class="copy-status" role="status">{{ copyStatus }}</small>
  </div>
</template>

<style scoped>
.custom-color-editor,.custom-color-editor * { box-sizing: border-box; }
.custom-color-editor { display: grid; gap: 12px; padding: 0 5px 14px; }
.color-plane { position: relative; height: 136px; border-radius: 10px; background: linear-gradient(to top, #000, transparent), linear-gradient(to right, #fff, transparent), hsl(var(--hue) 100% 50%); cursor: crosshair; touch-action: none; user-select: none; box-shadow: inset 0 0 0 1px rgb(0 0 0 / .08); }
.color-cursor { position: absolute; width: 14px; height: 14px; border: 2px solid white; border-radius: 50%; box-shadow: 0 1px 3px rgb(0 0 0 / .35); transform: translate(-50%, -50%); pointer-events: none; }
.hue-slider { appearance: none; -webkit-appearance: none; display: block; width: 100%; height: 12px; margin: 0; padding: 0; border: 0; border-radius: 999px; background: linear-gradient(to right, #f00, #ff0, #0f0, #0ff, #00f, #f0f, #f00); cursor: pointer; }
.hue-slider::-webkit-slider-thumb { appearance: none; -webkit-appearance: none; width: 16px; height: 16px; border: 2px solid white; border-radius: 50%; background: hsl(var(--hue) 100% 50%); box-shadow: 0 1px 3px rgb(0 0 0 / .3); }
.hue-slider::-moz-range-thumb { width: 12px; height: 12px; border: 2px solid white; border-radius: 50%; background: hsl(var(--hue) 100% 50%); box-shadow: 0 1px 3px rgb(0 0 0 / .3); }
.hex-row { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 12px; color: var(--color-text-muted); font-size: 12px; }
.hex-field { display: flex; align-items: center; min-width: 0; height: 34px; padding: 0 5px 0 9px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-surface); }
.hex-field input { width: 100%; min-width: 0; padding: 0; border: 0; outline: 0; background: transparent; color: var(--color-text); font: 12px ui-monospace, monospace; }
.hex-field button { display: grid; place-items: center; flex: 0 0 24px; height: 24px; padding: 0; border: 0; border-radius: 5px; background: transparent; color: var(--color-text-muted); cursor: pointer; }
.hex-field button:hover:not(:disabled) { background: var(--color-hover); color: var(--color-text); }
.hex-field button:disabled { opacity: .4; cursor: not-allowed; }
.hex-field:focus-within { border-color: var(--color-text-muted); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-text) 8%, transparent); }
.hex-field.invalid { border-color: var(--color-danger); }
.hex-error,.copy-status { font-size: 11px; line-height: 1.5; color: var(--color-text-muted); }
.hex-error { color: var(--color-danger); }
button:focus-visible,.color-plane:focus-visible,.hue-slider:focus-visible { outline: 2px solid var(--color-text-muted); outline-offset: 3px; }
</style>
