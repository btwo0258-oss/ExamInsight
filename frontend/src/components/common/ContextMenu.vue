<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'

export type MenuItem = {
  label: string
  icon?: string
  action?: () => void
  disabled?: boolean
  danger?: boolean
  divided?: boolean
  children?: MenuItem[]
}

type Props = {
  x: number
  y: number
  items: MenuItem[]
}

defineProps<Props>()
const emit = defineEmits<{ close: [] }>()

const menuEl = ref<HTMLDivElement | null>(null)
const activeSubmenuIndex = ref<number | null>(null)

function hasIcons(items: MenuItem[]) {
  return items.some(item => !item.divided && Boolean(item.icon))
}

function handleClick(item: MenuItem) {
  if (item.disabled || item.children) return
  item.action?.()
  emit('close')
}

function onClickOutside(e: MouseEvent) {
  if (!menuEl.value?.contains(e.target as Node)) {
    emit('close')
  }
}

onMounted(() => {
  document.addEventListener('click', onClickOutside, true)
  document.addEventListener('contextmenu', onClickOutside, true)
})

onUnmounted(() => {
  document.removeEventListener('click', onClickOutside, true)
  document.removeEventListener('contextmenu', onClickOutside, true)
})
</script>

<template>
  <div
    ref="menuEl"
    class="context-menu ui-menu-panel"
    :style="{ left: `${x}px`, top: `${y}px` }"
  >
    <template v-for="(item, i) in items" :key="i">
      <div v-if="item.divided" class="ui-menu-divider" />
      
      <div
        v-if="!item.divided"
        class="menu-item-wrap"
        @mouseenter="activeSubmenuIndex = i"
      >
        <div
          class="ui-menu-item"
          :class="{
            'ui-menu-item--disabled': item.disabled,
            'ui-menu-item--danger': item.danger,
          }"
          @click.stop="handleClick(item)"
        >
          <span v-if="hasIcons(items)" class="ui-menu-icon">
            <AppIcon v-if="item.icon" :name="item.icon" :size="16" />
          </span>
          <span class="menu-label">{{ item.label }}</span>
          <AppIcon v-if="item.children" name="chevron-right" :size="14" class="submenu-arrow" />
        </div>

        <div v-if="item.children && activeSubmenuIndex === i" class="submenu ui-menu-panel">
          <template v-for="(sub, j) in item.children" :key="j">
            <div v-if="sub.divided" class="ui-menu-divider" />
            <div
              v-if="!sub.divided"
              class="ui-menu-item"
              :class="{
                'ui-menu-item--disabled': sub.disabled,
                'ui-menu-item--danger': sub.danger,
              }"
              @click.stop="handleClick(sub)"
            >
              <span v-if="hasIcons(item.children || [])" class="ui-menu-icon">
                <AppIcon v-if="sub.icon" :name="sub.icon" :size="16" />
              </span>
              <span class="menu-label">{{ sub.label }}</span>
            </div>
          </template>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.context-menu {
  position: fixed;
  z-index: 100;
  min-width: 168px;
}

.menu-item-wrap {
  position: relative;
}

.menu-label {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
}

.submenu-arrow {
  flex: 0 0 auto;
  color: var(--color-text-muted);
}

.submenu {
  position: absolute;
  top: 0;
  left: 100%;
  min-width: 168px;
  margin-left: 6px;
}
</style>
