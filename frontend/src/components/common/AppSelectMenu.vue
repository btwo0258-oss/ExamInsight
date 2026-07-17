<script setup lang="ts" generic="T extends string | number | null">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'

type SelectOption = {
  value: T
  label: string
  icon?: string
  description?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<{
  modelValue: T
  options: SelectOption[]
  ariaLabel?: string
  placeholder?: string
  disabled?: boolean
  createLabel?: string
  compact?: boolean
  minMenuWidth?: number
}>(), {
  ariaLabel: '请选择',
  placeholder: '请选择',
  disabled: false,
  createLabel: '',
  compact: false,
  minMenuWidth: 0,
})

const emit = defineEmits<{
  'update:modelValue': [value: T]
  create: []
}>()

const triggerEl = ref<HTMLButtonElement | null>(null)
const panelEl = ref<HTMLDivElement | null>(null)
const open = ref(false)
const placement = ref<'top' | 'bottom'>('bottom')
const panelStyle = ref<Record<string, string>>({})
const instanceId = `app-select-${Math.random().toString(36).slice(2, 10)}`
const panelId = `${instanceId}-panel`

const selectedOption = computed(() => props.options.find((option) => Object.is(option.value, props.modelValue)))

function optionKey(option: SelectOption, index: number) {
  return `${typeof option.value}-${String(option.value)}-${index}`
}

function isSelected(option: SelectOption) {
  return Object.is(option.value, props.modelValue)
}

async function positionPanel() {
  if (!open.value || !triggerEl.value || !panelEl.value) return
  const rect = triggerEl.value.getBoundingClientRect()
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const edge = 8
  const gap = 6
  const width = Math.min(Math.max(rect.width, props.minMenuWidth), viewportWidth - edge * 2)
  const availableBelow = viewportHeight - rect.bottom - gap - edge
  const availableAbove = rect.top - gap - edge
  placement.value = availableBelow >= 180 || availableBelow >= availableAbove ? 'bottom' : 'top'
  const availableHeight = placement.value === 'bottom' ? availableBelow : availableAbove
  const maxHeight = Math.max(96, Math.min(320, availableHeight))
  const panelHeight = Math.min(panelEl.value.scrollHeight, maxHeight)
  const left = Math.min(Math.max(edge, rect.left), viewportWidth - width - edge)
  const top = placement.value === 'bottom'
    ? rect.bottom + gap
    : Math.max(edge, rect.top - gap - panelHeight)

  panelStyle.value = {
    left: `${left}px`,
    top: `${top}px`,
    width: `${width}px`,
    maxHeight: `${maxHeight}px`,
  }
}

async function setOpen(value: boolean) {
  if (props.disabled) return
  open.value = value
  if (!value) return
  window.dispatchEvent(new CustomEvent('examinsight:select-open', { detail: instanceId }))
  await nextTick()
  await positionPanel()
}

function toggle() {
  void setOpen(!open.value)
}

function selectOption(option: SelectOption) {
  if (option.disabled) return
  emit('update:modelValue', option.value)
  open.value = false
  triggerEl.value?.focus()
}

function createOption() {
  open.value = false
  emit('create')
}

function handleDocumentPointerDown(event: PointerEvent) {
  const target = event.target as Node
  if (triggerEl.value?.contains(target) || panelEl.value?.contains(target)) return
  open.value = false
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !open.value) return
  event.preventDefault()
  open.value = false
  triggerEl.value?.focus()
}

function handleOtherSelect(event: Event) {
  if ((event as CustomEvent<string>).detail !== instanceId) open.value = false
}

function handleViewportChange() {
  if (open.value) void positionPanel()
}

watch(() => props.disabled, (disabled) => {
  if (disabled) open.value = false
})

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown, true)
  document.addEventListener('keydown', handleKeydown)
  window.addEventListener('resize', handleViewportChange)
  window.addEventListener('scroll', handleViewportChange, true)
  window.addEventListener('examinsight:select-open', handleOtherSelect)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown, true)
  document.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', handleViewportChange)
  window.removeEventListener('scroll', handleViewportChange, true)
  window.removeEventListener('examinsight:select-open', handleOtherSelect)
})
</script>

<template>
  <div class="app-select-menu" :class="{ 'app-select-menu--compact': compact }">
    <button
      ref="triggerEl"
      class="app-select-menu__trigger"
      type="button"
      role="combobox"
      :aria-label="ariaLabel"
      :aria-controls="panelId"
      :aria-expanded="open"
      aria-haspopup="listbox"
      :disabled="disabled"
      @click="toggle"
    >
      <span v-if="selectedOption?.icon" class="app-select-menu__trigger-icon">
        <AppIcon :name="selectedOption.icon" :size="compact ? 14 : 16" />
      </span>
      <span class="app-select-menu__value" :class="{ 'is-placeholder': !selectedOption }">
        {{ selectedOption?.label ?? placeholder }}
      </span>
      <AppIcon :name="open ? 'chevron-up' : 'chevron-down'" :size="compact ? 13 : 15" class="app-select-menu__chevron" />
    </button>

    <Teleport to="body">
      <Transition name="app-select-popover">
        <div
          v-if="open"
          :id="panelId"
          ref="panelEl"
          class="app-select-menu__panel ui-menu-panel"
          :data-placement="placement"
          :style="panelStyle"
          role="listbox"
          :aria-label="ariaLabel"
        >
          <button
            v-for="(option, index) in options"
            :key="optionKey(option, index)"
            class="app-select-menu__option ui-menu-item"
            type="button"
            role="option"
            :aria-selected="isSelected(option)"
            :disabled="option.disabled"
            @click="selectOption(option)"
          >
            <span v-if="option.icon" class="ui-menu-icon">
              <AppIcon :name="option.icon" :size="16" />
            </span>
            <span class="app-select-menu__option-copy">
              <span>{{ option.label }}</span>
              <small v-if="option.description">{{ option.description }}</small>
            </span>
            <AppIcon v-if="isSelected(option)" name="check" :size="15" class="app-select-menu__check" />
          </button>

          <template v-if="createLabel">
            <div class="ui-menu-divider" />
            <button class="app-select-menu__option ui-menu-item" type="button" @click="createOption">
              <span class="ui-menu-icon"><AppIcon name="plus" :size="16" /></span>
              <span class="app-select-menu__option-copy"><span>{{ createLabel }}</span></span>
            </button>
          </template>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.app-select-menu {
  width: 100%;
  min-width: 0;
}

.app-select-menu__trigger {
  width: 100%;
  min-width: 0;
  height: 40px;
  padding: 0 11px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--color-bg);
  color: var(--color-text);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color .16s ease, background-color .16s ease, box-shadow .16s ease;
}

.app-select-menu__trigger:hover:not(:disabled) {
  border-color: var(--color-text-muted);
  background: var(--ui-hover-bg);
}

.app-select-menu__trigger:focus-visible,
.app-select-menu__trigger[aria-expanded="true"] {
  outline: 0;
  border-color: var(--color-text-muted);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-text) 7%, transparent);
}

.app-select-menu__trigger:disabled {
  opacity: .48;
  cursor: not-allowed;
}

.app-select-menu--compact .app-select-menu__trigger {
  height: 32px;
  padding: 0 8px;
  border-radius: 7px;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
}

.app-select-menu__trigger-icon,
.app-select-menu__chevron,
.app-select-menu__check {
  flex: 0 0 auto;
  color: var(--color-text-muted);
}

.app-select-menu__value {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-select-menu__value.is-placeholder {
  color: var(--color-text-muted);
}

.app-select-menu__panel {
  position: fixed;
  z-index: 10040;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  transform-origin: top center;
}

.app-select-menu__panel[data-placement="top"] {
  transform-origin: bottom center;
}

.app-select-menu__option-copy {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 2px;
  text-align: left;
}

.app-select-menu__option-copy > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-select-menu__option-copy small {
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 11px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-select-menu__option:disabled {
  opacity: .42;
  cursor: not-allowed;
}

.app-select-popover-enter-active,
.app-select-popover-leave-active {
  transition: opacity .16s ease, transform .2s cubic-bezier(.2, .8, .2, 1);
}

.app-select-popover-enter-from,
.app-select-popover-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(.985);
}

.app-select-menu__panel[data-placement="top"].app-select-popover-enter-from,
.app-select-menu__panel[data-placement="top"].app-select-popover-leave-to {
  transform: translateY(6px) scale(.985);
}

@media (prefers-reduced-motion: reduce) {
  .app-select-popover-enter-active,
  .app-select-popover-leave-active,
  .app-select-menu__trigger {
    transition-duration: .01ms;
  }
}
</style>
