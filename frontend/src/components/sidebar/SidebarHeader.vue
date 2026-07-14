<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import AppIcon from "@/components/common/AppIcon.vue";
import { useAppState } from "@/stores/appState";
import { useThemeStore } from "@/stores/theme";
import logoUrl from "@/assets/icons/ExamInsight-Logo.png";
import logoWhiteUrl from "@/assets/icons/ExamInsight-Logo-White.png";

const router = useRouter();
const appState = useAppState();
const themeStore = useThemeStore();
const emit = defineEmits<{ close: [] }>();

const currentLogo = computed(() => logoWhiteUrl);

function goHome() {
  appState.setMode("chat");
  router.push("/chat");
}
</script>

<template>
  <div class="header">
    <div class="header__brand" @click="goHome">
      <img :src="currentLogo" alt="Logo" class="logo-img" />
      <span>ExamInsight</span>
    </div>
    <button class="header__btn" @click="emit('close')">
      <AppIcon name="sidebar-left" :size="20" />
    </button>
  </div>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
}

.header__brand {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 700;
  font-size: 20px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background-color 0.2s ease;
}

.logo-img {
  width: 42px;
  height: 42px;
  object-fit: contain;
  border-radius: 4px;
  transition: opacity 0.2s ease;
  flex-shrink: 0;
}

.header__brand:hover {
  background: var(--ui-hover-bg);
}

.header__btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--color-text-muted);
  display: grid;
  place-items: center;
}

.header__btn:hover {
  background: var(--ui-hover-strong-bg);
}
</style>
