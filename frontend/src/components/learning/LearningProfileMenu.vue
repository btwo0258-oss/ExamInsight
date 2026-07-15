<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'

const props = defineProps<{
  modelValue: string | string[]
  options: string[]
  multiple?: boolean
  placeholder?: string
  disabled?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string | string[]] }>()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

const selected = computed(() => Array.isArray(props.modelValue) ? props.modelValue : [props.modelValue].filter(Boolean))
const label = computed(() => selected.value.length ? selected.value.join('、') : (props.placeholder || '请选择'))

function choose(option: string) {
  if (props.multiple) {
    const values = new Set(selected.value)
    values.has(option) ? values.delete(option) : values.add(option)
    emit('update:modelValue', Array.from(values))
    return
  }
  emit('update:modelValue', option)
  open.value = false
}

function closeFromOutside(event: MouseEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false
}

onMounted(() => document.addEventListener('mousedown', closeFromOutside))
onUnmounted(() => document.removeEventListener('mousedown', closeFromOutside))
</script>

<template>
  <div ref="root" class="profile-menu">
    <button class="profile-menu__trigger" type="button" :disabled="disabled" @click="open = !open">
      <span :class="{ muted: !selected.length }">{{ label }}</span>
      <AppIcon name="chevron-down" :size="14" />
    </button>
    <div v-if="open && !disabled" class="profile-menu__panel ui-menu-panel">
      <button
        v-for="option in options"
        :key="option"
        class="profile-menu__option ui-menu-item"
        type="button"
        :aria-selected="selected.includes(option)"
        @click="choose(option)"
      >
        <span>{{ option }}</span>
        <AppIcon v-if="selected.includes(option)" name="check" :size="14" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.profile-menu { position: relative; min-width: 0; }
.profile-menu__trigger { width: 100%; min-height: 36px; padding: 5px 9px; display: flex; align-items: center; justify-content: space-between; gap: 8px; border: 1px solid color-mix(in srgb, var(--color-border) 88%, var(--color-text-muted)); border-radius: 8px; background: var(--profile-input-bg, var(--color-surface)); color: var(--color-text); font: inherit; font-weight: 600; text-align: left; cursor: pointer; box-shadow: 0 1px 1px color-mix(in srgb, var(--color-text) 4%, transparent); }
.profile-menu__trigger:hover:not(:disabled), .profile-menu__trigger:focus-visible { border-color: color-mix(in srgb, var(--color-text) 40%, var(--color-border)); background: var(--profile-input-bg, var(--color-surface)); outline: none; box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-text) 7%, transparent); }
.profile-menu__trigger:disabled { cursor: default; }
.profile-menu__trigger span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.profile-menu__trigger .muted { color: var(--color-text-muted); }
.profile-menu__panel { position: absolute; z-index: 20; top: calc(100% + 4px); left: 0; width: max(100%, 190px); max-height: 320px; padding: 6px; overflow-x: hidden; overflow-y: auto; background: var(--profile-field-bg, var(--color-surface)) !important; }
.profile-menu__panel::before { content: ''; position: absolute; left: 0; right: 0; top: -5px; height: 5px; }
.profile-menu__option { width: 100%; display: flex; align-items: center; justify-content: space-between; background: transparent !important; }
.profile-menu__option:hover:not([aria-selected='true']), .profile-menu__option:focus-visible:not([aria-selected='true']) { background: var(--ui-hover-bg) !important; }
.profile-menu__option[aria-selected='true'] { background: var(--profile-selected-bg, var(--color-hover-strong)) !important; color: var(--color-text) !important; font-weight: 650; }
</style>
