<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

export type MenuItem = {
  label: string
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
    class="context-menu"
    :style="{ left: `${x}px`, top: `${y}px` }"
  >
    <template v-for="(item, i) in items" :key="i">
      <div v-if="item.divided" class="divider" />
      
      <div
        v-if="!item.divided"
        class="menu-item-wrap"
        @mouseenter="activeSubmenuIndex = i"
      >
        <div
          class="menu-item"
          :class="{
            'menu-item--disabled': item.disabled,
            'menu-item--danger': item.danger,
          }"
          @click.stop="handleClick(item)"
        >
          <span>{{ item.label }}</span>
        </div>

        <div v-if="item.children && activeSubmenuIndex === i" class="submenu">
          <template v-for="(sub, j) in item.children" :key="j">
            <div v-if="sub.divided" class="divider" />
            <div
              v-if="!sub.divided"
              class="menu-item"
              :class="{
                'menu-item--disabled': sub.disabled,
                'menu-item--danger': sub.danger,
              }"
              @click.stop="handleClick(sub)"
            >
              {{ sub.label }}
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
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
  padding: 4px;
  min-width: 140px;
}

.menu-item-wrap {
  position: relative;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  border-radius: 4px;
}

.menu-item:hover {
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme='dark'] .menu-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.menu-item--disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.menu-item--danger {
  color: #ff4d4f;
}

.menu-item--danger:hover {
  background: rgba(255, 77, 79, 0.1);
}

.divider {
  height: 0.5px;
  background: rgba(0, 0, 0, 0.1);
  margin: 2px 0;
}

:root[data-theme='dark'] .divider {
  background: rgba(255, 255, 255, 0.1);
}

.arrow {
  font-size: 10px;
  color: var(--color-text-muted);
}

.submenu {
  position: absolute;
  top: 0;
  left: 100%;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
  padding: 4px;
  min-width: 120px;
  margin-left: 2px;
}
</style>
