<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from "vue";
import { useModelStore } from "@/stores/model";

const props = withDefaults(defineProps<{ align?: "left" | "right" }>(), { align: "left" });

const modelStore = useModelStore();
const isOpen = ref(false);

const models = computed(() => modelStore.list);

const currentModelName = computed(() => {
  const m = models.value.find((item) => item.name === modelStore.currentModel);
  return m ? m.displayName || m.name : modelStore.isLoading ? "加载模型" : "选择模型";
});

function toggleDropdown() {
  isOpen.value = !isOpen.value;
}

function selectModel(model: { name: string; displayName?: string }) {
  modelStore.setCurrent(model.name);
  isOpen.value = false;
}

const closeDropdown = (e: MouseEvent) => {
  if (!(e.target as HTMLElement).closest(".model-switch")) {
    isOpen.value = false;
  }
};

onMounted(() => {
  document.addEventListener("click", closeDropdown);
  modelStore.fetchList();
});
onUnmounted(() => document.removeEventListener("click", closeDropdown));
</script>

<template>
  <div class="model-switch">
    <button class="model-trigger ui-hover-row" @click.stop="toggleDropdown" type="button">
      <span>{{ currentModelName }}</span>
      <svg class="chevron" viewBox="0 0 24 24" width="14" :class="{ rotate: isOpen }">
        <path
          d="M6 9l6 6 6-6"
          fill="none"
          stroke="currentColor"
          stroke-width="2.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>

    <transition name="slide-up">
      <div
        v-if="isOpen"
        class="dropdown-menu ui-menu-panel"
        :class="{ 'dropdown-menu--right': props.align === 'right' }"
      >
        <div
          v-for="(model, index) in models"
          :key="model.name"
          class="menu-item ui-menu-item"
          :aria-selected="modelStore.currentModel === model.name"
          @click="selectModel(model)"
        >
          <div class="item-info">
            <div class="item-name">{{ model.displayName }}</div>
            <div class="item-desc">{{ model.description || (index === 0 ? "适合日常对话" : "支持深度推理") }}</div>
          </div>
          <div v-if="modelStore.currentModel === model.name" class="check-icon">✓</div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.model-switch {
  position: relative;
  display: inline-block;
}

.model-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  background: transparent;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.model-trigger:hover {
  background: var(--color-hover);
  color: var(--color-text);
}

.chevron {
  transition: transform 0.2s;
  opacity: 0.6;
}

.chevron.rotate {
  transform: rotate(180deg);
}

.dropdown-menu {
  position: absolute;
  bottom: calc(100% + 12px);
  left: 0;
  width: 260px;
  /* 确保 z-index 足够大，能够遮盖对话框/输入框容器 */
  z-index: 2000;
}

.dropdown-menu--right {
  right: 0;
  left: auto;
}

@media (max-width: 767px) {
  .dropdown-menu {
    width: min(260px, calc(100vw - 32px));
  }
}

.menu-item {
  justify-content: space-between;
  padding: 4px 10px;
}

.item-name {
  font-size: 14px;
  font-weight: 400;
  line-height: 16px;
  color: var(--color-text);
}

.item-desc {
  font-size: 12px;
  line-height: 14px;
  color: var(--color-text-muted);
}

.check-icon {
  color: #10a37f;
  font-weight: bold;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition:
    opacity 0.2s,
    transform 0.2s;
}
.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
