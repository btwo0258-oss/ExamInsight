<script setup lang="ts">
// @ts-nocheck
import { computed, watch, ref, onMounted, onUnmounted } from "vue";
import { useConversationStore } from "@/stores/conversation";
import { useMessageStore } from "@/stores/message";
import { useAuthStore } from "@/stores/auth";

import ChatHeader from "./ChatHeader.vue";
import MessageList from "./message/MessageList.vue";
import AppInput from "@/components/common/AppInput.vue";
import SegmentPanel from "./SegmentPanel.vue";
import MindMapPanel from "./MindMapPanel.vue";

const conversationStore = useConversationStore();
const messageStore = useMessageStore();
const authStore = useAuthStore();

const activeChatId = computed(() => conversationStore.currentId);

watch(
  activeChatId,
  (id) => {
    if (id) {
      messageStore.ensureLoaded(id).then(() => {
        // ensureLoaded 完成后，检查是否有自动发送的消息
        const autoMsgKey = `chat_auto_msg_${id}`;
        const autoMsgStr = sessionStorage.getItem(autoMsgKey);
        if (autoMsgStr) {
          try {
            const autoMsg = JSON.parse(autoMsgStr);
            sessionStorage.removeItem(autoMsgKey);
            messageStore.sendMessage(id, autoMsg.message);
          } catch (e) {
            console.error("Failed to parse auto message:", e);
            sessionStorage.removeItem(autoMsgKey);
          }
        }
      });
    }
  },
  { immediate: true },
);

const currentConversation = computed(() => {
  if (!activeChatId.value) return null;
  return conversationStore.list.find((c: { id: number }) => c.id === activeChatId.value);
});

const messages = computed(() => {
  if (!activeChatId.value) return [];
  const key = String(activeChatId.value);
  const allMsgs = messageStore.byConversation[key] || [];

  const filtered = allMsgs.filter((m) => {
    if (!m.turnId) return true; // 兼容旧消息

    const activeQ = messageStore.getActiveQVersion(activeChatId.value!, m.turnId);
    const mQ = m.qVersion ?? 0;
    const mA = m.aVersion ?? 0;

    if (m.role === "user") {
      return mQ === activeQ;
    } else if (m.role === "assistant") {
      const activeA = messageStore.getActiveAVersion(activeChatId.value!, m.turnId, activeQ);
      return mQ === activeQ && mA === activeA;
    }
    return true;
  });

  // 预先计算每个 turnId 的最早创建时间，用于稳定排序
  const turnMinTime: Record<string, number> = {};
  for (const m of allMsgs) {
    if (m.turnId) {
      if (!turnMinTime[m.turnId] || m.createTime < turnMinTime[m.turnId]) {
        turnMinTime[m.turnId] = m.createTime;
      }
    }
  }

  // 稳定排序：按 turn 的最早时间排序，同一个 turn 内部按角色（user在前）和具体创建时间排序
  return filtered.sort((a, b) => {
    const timeA = a.turnId ? turnMinTime[a.turnId] : a.createTime;
    const timeB = b.turnId ? turnMinTime[b.turnId] : b.createTime;

    if (timeA !== timeB) {
      return timeA - timeB;
    }

    if (a.role !== b.role) {
      return a.role === "user" ? -1 : 1;
    }

    return a.createTime - b.createTime;
  });
});

const showWelcome = computed(() => {
  if (!activeChatId.value) return true;
  return messages.value.length === 0;
});

async function onSend(text: string, files?: File[]) {
  if (!authStore.isAuthed) {
    authStore.openAuthModal();
    return;
  }

  if (!activeChatId.value) {
    const result = await messageStore.createConversation({ firstMessage: text, files });
    const newChatId = result.id;

    // 将消息存储到 sessionStorage，让聊天页面在挂载后自动发送
    sessionStorage.setItem(
      `chat_auto_msg_${newChatId}`,
      JSON.stringify({
        message: text,
        files: files?.map((f) => ({ name: f.name, type: f.type, size: f.size })),
      }),
    );

    await conversationStore.fetchList();
    conversationStore.open(newChatId);
    return;
  }

  // 已经在对话中，直接发送
  await messageStore.sendMessage(activeChatId.value, text, undefined, undefined, undefined, files);
}

function handleKeyDown(e: KeyboardEvent) {
  if (e.ctrlKey && e.shiftKey) {
    if (e.key.toLowerCase() === "c") {
      e.preventDefault();
      // Copy last AI message
      const lastAiMsg = messages.value.filter((m) => m.role === "assistant").pop();
      if (lastAiMsg) {
        import("@/utils/clipboard").then(({ copyText }) => copyText(lastAiMsg.content));
      }
    } else if (e.key.toLowerCase() === "r") {
      e.preventDefault();
      // Regenerate last AI message
      const lastAiMsg = messages.value.filter((m) => m.role === "assistant").pop();
      if (lastAiMsg && activeChatId.value && !messageStore.isStreaming) {
        const turnId = lastAiMsg.turnId || lastAiMsg.id;
        messageStore.regenerate(activeChatId.value, turnId);
      }
    }
  }
}

const showMindMapPanel = ref(false);
const mindMapContent = ref("");
const mindMapTitle = ref("");
const mindMapSidebarCollapsed = ref(false);
const messageListRef = ref<InstanceType<typeof MessageList> | null>(null);

const messageListContainer = computed(() => {
  return messageListRef.value?.scrollContainer ?? null;
});

function onGenerateMindmap(messageId: string, content: string) {
  mindMapContent.value = content;
  mindMapTitle.value = "";
  showMindMapPanel.value = true;
  mindMapSidebarCollapsed.value = false;
}

function onMindMapSaved(mindMapId: number) {
  showMindMapPanel.value = false;
}

function handleToggleMindMapSidebar() {
  mindMapSidebarCollapsed.value = !mindMapSidebarCollapsed.value;
}

onMounted(() => {
  window.addEventListener("keydown", handleKeyDown);
});

onUnmounted(() => {
  window.removeEventListener("keydown", handleKeyDown);
});
</script>

<template>
  <div class="message-area">
    <ChatHeader v-if="currentConversation" :title="currentConversation.title || '新对话'" />

    <div class="message-container">
      <MessageList
        v-if="!showWelcome"
        ref="messageListRef"
        :conversation-id="activeChatId"
        :messages="messages"
        @generate-mindmap="onGenerateMindmap"
      />
    </div>

    <AppInput
      :is-streaming="messageStore.isStreaming"
      placeholder="输入消息，Enter 发送，Shift+Enter 换行"
      @send="onSend"
    />

    <SegmentPanel v-if="!showWelcome" :conversation-id="activeChatId" :container-ref="messageListContainer" />

    <MindMapPanel
      :visible="showMindMapPanel"
      :ai-content="mindMapContent"
      :ai-title="mindMapTitle"
      @close="showMindMapPanel = false"
      @saved="onMindMapSaved"
      @toggle-sidebar="handleToggleMindMapSidebar"
    />

    <div v-if="showMindMapPanel && mindMapSidebarCollapsed" class="mindmap-fab" @click="handleToggleMindMapSidebar">
      <AppIcon name="panel-left-open" :size="20" />
    </div>
  </div>
</template>

<style scoped>
.message-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
}

.message-container {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  position: relative;
}

.mindmap-fab {
  position: fixed;
  top: 12px;
  right: 12px;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 9998;
  color: var(--color-text-muted);
  transition: all 0.2s;
}

.mindmap-fab:hover {
  background: var(--color-surface-hover);
  color: var(--color-primary);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
}
</style>
