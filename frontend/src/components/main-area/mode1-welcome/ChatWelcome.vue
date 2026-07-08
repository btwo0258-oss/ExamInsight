<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import AppIcon from "@/components/common/AppIcon.vue";
import { useConversationStore } from "@/stores/conversation";
import { useAuthStore } from "@/stores/auth";
import { useThemeStore } from "@/stores/theme";
import logoUrl from "@/assets/icons/ExamInsight-Logo.png";
import logoWhiteUrl from "@/assets/icons/ExamInsight-Logo-White.png";

const router = useRouter();
const conversationStore = useConversationStore();
const authStore = useAuthStore();
const themeStore = useThemeStore();

const currentLogo = computed(() => logoWhiteUrl);

async function handleNewChat() {
  if (!authStore.isAuthed) return authStore.openAuthModal();
  await conversationStore.create();
}

async function goToChat() {
  if (!authStore.isAuthed) return authStore.openAuthModal();
  await conversationStore.create();
}

function goToKnowledge() {
  if (!authStore.isAuthed) return authStore.openAuthModal();
  router.push("/knowledge");
}
</script>

<template>
  <div class="welcome">
    <div class="welcome__content">
      <div class="welcome__logo">
        <img :src="currentLogo" alt="ExamInsight Logo" class="welcome-logo-img" />
      </div>

      <h1 class="welcome__title">欢迎使用智能助手</h1>
      <p class="welcome__subtitle">我可以帮您解答问题、提供信息和协助完成各种任务</p>

      <div class="welcome__features">
        <div class="feature-item" @click="goToChat">
          <div class="feature-icon">
            <AppIcon name="zap" :size="24" />
          </div>
          <div class="feature-text">
            <h3>智能对话</h3>
            <p>自然流畅的对话体验</p>
          </div>
        </div>
        <div class="feature-item" @click="goToKnowledge">
          <div class="feature-icon">
            <AppIcon name="book" :size="24" />
          </div>
          <div class="feature-text">
            <h3>知识库支持</h3>
            <p>基于您的知识库提供精准回答</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.welcome {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.welcome__content {
  max-width: 600px;
  text-align: center;
}

.welcome__logo {
  margin-bottom: 12px;
  display: flex;
  justify-content: center;
}

.welcome-logo-img {
  width: 120px;
  height: 120px;
  object-fit: contain;
  border-radius: 12px;
  transition: opacity 0.2s ease;
  flex-shrink: 0;
}

.welcome__title {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 8px;
  color: var(--color-text);
}

.welcome__subtitle {
  font-size: 16px;
  color: var(--color-text-muted);
  margin-bottom: 40px;
  line-height: 1.6;
}

.welcome__features {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-bottom: 40px;
  text-align: left;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
  transition: all 0.2s ease;
}

.feature-item:hover {
  border-color: var(--color-primary);
  transform: translateX(4px);
  box-shadow: var(--shadow-sm);
}

.feature-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-background-soft);
  border-radius: 12px;
  color: var(--color-text);
  flex-shrink: 0;
}

.feature-text {
  flex: 1;
}

.feature-text h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 4px 0;
}

.feature-text p {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
}

.welcome__button {
  padding: 14px 32px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.welcome__button:hover {
  background: var(--color-primary-hover);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
</style>
