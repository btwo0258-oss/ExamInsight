<script setup lang="ts">
import { computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";

import SidebarHeader from "./SidebarHeader.vue";
import NewChatButton from "./NewChatButton.vue";
import LearningWorkspaceSection from "./LearningWorkspaceSection.vue";
import ResourceCenterSection from "./ResourceCenterSection.vue";
import ConversationList from "./ConversationList.vue";
import KnowledgeBaseSection from "./KnowledgeBaseSection.vue";
import ExamAnalysisSection from "./ExamAnalysisSection.vue";
import MindMapSection from "./MindMapSection.vue";
import ThemeToggle from "./ThemeToggle.vue";
import SidebarFooter from "./SidebarFooter.vue";
import AppIcon from "@/components/common/AppIcon.vue";

import { useAuthStore } from "@/stores/auth";
import { useConversationStore } from "@/stores/conversation";
import { useKnowledgeBaseStore } from "@/stores/knowledgeBase";
import { useMindMapStore } from "@/stores/mindmap";
import { useAppState } from "@/stores/appState";

type Props = { open: boolean };
defineProps<Props>();
const emit = defineEmits<{ close: [] }>();

const router = useRouter();
const authStore = useAuthStore();
const conversationStore = useConversationStore();
const knowledgeBaseStore = useKnowledgeBaseStore();
const mindMapStore = useMindMapStore();
const appState = useAppState();

const pinnedItems = computed(() => conversationStore.pinnedConversations);

const ungroupedItems = computed(() => conversationStore.ungroupedConversations);

async function openConversation(id: number) {
  await router.push(`/chat/${id}`);
}

async function removeConversation(id: number) {
  await conversationStore.remove(id);
  if (conversationStore.currentId === id) {
    await router.push("/chat");
  }
}

onMounted(async () => {
  authStore.init();
  await knowledgeBaseStore.fetchAll();
  await mindMapStore.fetchList();
  await conversationStore.fetchList();
  // 登录后也不自动创建会话
  if (
    router.currentRoute.value.path.startsWith("/chat") ||
    router.currentRoute.value.path === "/"
  ) {
    await conversationStore.ensureActive(false);
  }
});

watch(
  () => authStore.token,
  async () => {
    await knowledgeBaseStore.fetchAll();
    await mindMapStore.fetchList();
    await conversationStore.fetchList();
    if (
      router.currentRoute.value.path.startsWith("/chat") ||
      router.currentRoute.value.path === "/"
    ) {
      await conversationStore.ensureActive(false);
    }
  },
);
</script>

<template>
  <div class="sidebar-wrap">
    <SidebarHeader @close="emit('close')" />

    <div class="sidebar-wrap__scroll">
      <NewChatButton />

      <LearningWorkspaceSection />
      <ResourceCenterSection />
      <KnowledgeBaseSection />
      <ExamAnalysisSection />
      <MindMapSection />

      <div
        class="divider"
        v-if="
          (knowledgeBaseStore.list.length || mindMapStore.mindMapList.length) &&
          (pinnedItems.length || ungroupedItems.length)
        "
      />

      <!-- 置顶对话 -->
      <div v-if="pinnedItems.length" class="pinned-section">
        <div class="section__header">
          <span class="section__title">置顶</span>
        </div>
        <ConversationList
          :items="pinnedItems"
          :active-id="conversationStore.currentId"
          :show-date="false"
          @open="openConversation"
          @rename="conversationStore.rename"
          @remove="removeConversation"
        />
      </div>

      <!-- 普通对话 -->
      <ConversationList
        v-if="ungroupedItems.length"
        :items="ungroupedItems"
        :active-id="conversationStore.currentId"
        @open="openConversation"
        @rename="conversationStore.rename"
        @remove="removeConversation"
      />
    </div>

    <div class="sidebar-wrap__bottom">
      <ThemeToggle />
      <SidebarFooter />
    </div>
  </div>
</template>

<style scoped>
.sidebar-wrap {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.sidebar-wrap__scroll {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.divider {
  height: 1px;
  background: var(--color-border);
  margin: 8px 16px;
}

.pinned-section {
  margin-bottom: 16px;
}

.section__header {
  padding: 4px 16px 8px;
  margin-bottom: 4px;
}

.section__title {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-muted);
}

.sidebar-wrap__bottom {
  padding: 12px;
  display: grid;
  gap: 10px;
  border-top: 1px solid var(--color-border);
}
</style>
