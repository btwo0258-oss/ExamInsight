<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { useAnchoredPopover } from '@/composables/useAnchoredPopover'

const props = withDefaults(defineProps<{ modelValue: string | string[] | null; multiple?: boolean; min?: string; max?: string; ariaLabel?: string; placeholder?: string; disabled?: boolean }>(), { ariaLabel: '选择日期', placeholder: '选择日期' })
const emit = defineEmits<{ 'update:modelValue': [value: string | string[]] }>()
const { trigger, panel, open, style, toggle, close } = useAnchoredPopover(304)
const month = ref(new Date())
const selected = computed(() => Array.isArray(props.modelValue) ? props.modelValue : props.modelValue ? [props.modelValue] : [])
const display = computed(() => props.multiple ? selected.value.length ? `已选 ${selected.value.length} 天` : props.placeholder : selected.value[0]?.replaceAll('-', '/') || props.placeholder)
function iso(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` }
function dateOf(value: string) { return new Date(`${value}T12:00:00`) }
const today = iso(new Date())
const days = computed(() => {
  const first = new Date(month.value.getFullYear(), month.value.getMonth(), 1, 12)
  first.setDate(first.getDate() - (first.getDay() + 6) % 7)
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(first); date.setDate(date.getDate() + index)
    const value = iso(date)
    return { value, day: date.getDate(), outside: date.getMonth() !== month.value.getMonth(), disabled: Boolean((props.min && value < props.min || props.max && value > props.max) && !selected.value.includes(value)) }
  })
})
async function openCalendar() {
  if (props.disabled) return
  if (!open.value) month.value = dateOf(selected.value[0] || props.min || today)
  await toggle()
  await nextTick()
  panel.value?.querySelector<HTMLButtonElement>('.calendar-day[aria-pressed="true"], .calendar-day.today:not(:disabled), .calendar-day:not(:disabled)')?.focus()
}
function choose(value: string) {
  if (props.multiple) emit('update:modelValue', selected.value.includes(value) ? selected.value.filter(day => day !== value) : [...selected.value, value].sort())
  else { emit('update:modelValue', value); close(true) }
}
function changeMonth(offset: number) { month.value = new Date(month.value.getFullYear(), month.value.getMonth() + offset, 1, 12) }
async function moveFocus(event: KeyboardEvent, value: string) {
  const offset = ({ ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 } as Record<string, number>)[event.key]
  if (!offset) return
  event.preventDefault()
  const date = dateOf(value); date.setDate(date.getDate() + offset)
  if (props.min && iso(date) < props.min || props.max && iso(date) > props.max) return
  month.value = new Date(date.getFullYear(), date.getMonth(), 1, 12)
  await nextTick()
  panel.value?.querySelector<HTMLButtonElement>(`[data-date="${iso(date)}"]`)?.focus()
}
</script>

<template>
  <div class="app-date-picker">
    <button ref="trigger" type="button" class="date-trigger" :aria-label="ariaLabel" aria-haspopup="dialog" :aria-expanded="open" :disabled="disabled" @click="openCalendar">
      <span :class="{ placeholder: !selected.length }">{{ display }}</span><AppIcon name="calendar" :size="16" />
    </button>
    <Teleport to="body">
      <div v-if="open" ref="panel" class="date-panel ui-menu-panel" :style="style" role="dialog" :aria-label="ariaLabel">
        <header><strong aria-live="polite">{{ month.getFullYear() }} 年 {{ month.getMonth() + 1 }} 月</strong><button type="button" aria-label="上个月" @click="changeMonth(-1)"><AppIcon name="chevron-left" :size="17" /></button><button type="button" aria-label="下个月" @click="changeMonth(1)"><AppIcon name="chevron-right" :size="17" /></button></header>
        <div class="calendar-week"><span v-for="day in ['一','二','三','四','五','六','日']" :key="day">{{ day }}</span></div>
        <div class="calendar-grid"><button v-for="day in days" :key="day.value" class="calendar-day" :class="{ outside: day.outside, today: day.value === today, selected: selected.includes(day.value) }" :data-date="day.value" type="button" :aria-label="day.value" :aria-pressed="selected.includes(day.value)" :disabled="day.disabled" @keydown="moveFocus($event, day.value)" @click="choose(day.value)">{{ day.day }}</button></div>
        <div v-if="multiple && selected.length" class="selected-dates"><button v-for="value in selected" :key="value" type="button" :aria-label="`移除 ${value}`" @click="choose(value)">{{ value }} <AppIcon name="close" :size="12" /></button></div>
        <footer><button type="button" @click="emit('update:modelValue', multiple ? [] : '')">清除</button><span v-if="multiple">可选择多个日期</span><button v-if="multiple" class="done" type="button" @click="close(true)">完成</button><button v-else type="button" :disabled="Boolean(max && today > max || min && today < min)" @click="choose(today)">今天</button></footer>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.calendar-grid .calendar-day.selected:hover:not(:disabled){background:var(--color-primary);color:var(--color-on-primary)}
.app-date-picker,.app-date-picker *,.date-panel,.date-panel *{box-sizing:border-box}.app-date-picker{min-width:0;width:100%}.date-trigger{width:100%;height:40px;padding:0 12px;display:flex;align-items:center;justify-content:space-between;gap:10px;border:1px solid var(--color-border);border-radius:9px;background:var(--color-surface);color:var(--color-text);font:inherit;font-size:14px;cursor:pointer}.date-trigger span{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.date-trigger svg{flex-shrink:0;color:var(--color-text-muted)}.placeholder{color:var(--color-text-muted)}button:focus-visible{outline:2px solid var(--color-text-muted);outline-offset:2px}button:disabled{opacity:.4;cursor:not-allowed}.date-panel{z-index:20020;padding:12px;overflow:auto;color:var(--color-text);background:var(--color-surface);border:1px solid var(--color-border);border-radius:14px;box-shadow:var(--shadow-lg);font-size:13px}.date-panel header{display:flex;align-items:center;gap:5px;margin-bottom:10px}.date-panel header strong{flex:1;font-weight:600}.date-panel button{font:inherit;color:inherit;cursor:pointer;border:0;background:transparent;border-radius:7px}.date-panel header button{height:30px;width:30px;display:grid;place-items:center}.date-panel button:hover:not(:disabled){background:var(--color-hover)}.calendar-week,.calendar-grid{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:3px}.calendar-week{text-align:center;color:var(--color-text-muted);line-height:28px}.calendar-day{height:32px}.calendar-day.outside{color:var(--color-text-muted)}.calendar-day.today:not(.selected){box-shadow:inset 0 0 0 1px var(--color-border)}.calendar-grid .calendar-day.selected{background:var(--color-primary);color:var(--color-on-primary)}.date-panel footer{display:flex;align-items:center;gap:8px;border-top:1px solid var(--color-border);margin-top:10px;padding-top:10px}.date-panel footer button{padding:6px 8px}.date-panel footer span{flex:1;font-size:12px;color:var(--color-text-muted)}.date-panel footer button:last-child{margin-left:auto}.selected-dates{display:flex;gap:5px;flex-wrap:wrap;max-height:80px;overflow:auto;margin-top:10px}.selected-dates button{display:flex;align-items:center;gap:4px;background:var(--color-hover);padding:4px 6px;font-size:11px}
</style>
