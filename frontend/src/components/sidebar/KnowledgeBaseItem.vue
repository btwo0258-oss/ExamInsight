<script setup lang="ts">
// @ts-nocheck
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { useAppState } from "@/stores/appState";
import { useKnowledgeBaseStore } from "@/stores/knowledgeBase";
import { useConversationStore } from "@/stores/conversation";
import { useMessageStore } from "@/stores/message";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import KnowledgeBaseCreate from "@/components/knowledge/KnowledgeBaseCreate.vue";
import AppIcon from "@/components/common/AppIcon.vue";
import type { KnowledgeBase } from "@/api/knowledgeBase";

const props = defineProps<{
  knowledgeBase: KnowledgeBase;
}>();

const router = useRouter();
const appState = useAppState();
const knowledgeBaseStore = useKnowledgeBaseStore();
const conversationStore = useConversationStore();
const messageStore = useMessageStore();
const showActions = ref(false);
const showActionsPanel = ref(false);
const showDeleteConfirm = ref(false);
const showEditDialog = ref(false);
const isExpanded = ref(false);

const kbConversations = computed(() => {
  return conversationStore.list.filter((conv) => conv.knowledgeBaseId === props.knowledgeBase.id);
});

// 获取对话的显示标题 - 与ChatHeader保持一致
function getConversationDisplayTitle(conv: any): string {
  // 如果有自定义标题，使用自定义标题
  if (conv.title && conv.title !== "新对话") {
    return conv.title;
  }
  // 否则使用第一条消息的内容
  const messages = messageStore.getMessages(conv.id);
  const firstMessage = messages.find((msg) => msg.role === "user")?.content || "";
  return firstMessage || "新对话";
}

function selectKnowledgeBase() {
  appState.setMode("knowledge");
  appState.setActiveKnowledgeBase(props.knowledgeBase.id);
  router.push(`/knowledge/${props.knowledgeBase.id}`);
  // Removed auto-collapsing of sidebar or jumping to chat
}

function toggleExpand() {
  isExpanded.value = !isExpanded.value;
}

function toggleActions() {
  showActionsPanel.value = !showActionsPanel.value;
}

function handleEdit() {
  showEditDialog.value = true;
  showActionsPanel.value = false;
}

function handleDelete() {
  showDeleteConfirm.value = true;
  showActionsPanel.value = false;
}

function confirmDelete() {
  knowledgeBaseStore.remove(props.knowledgeBase.id);
  showDeleteConfirm.value = false;
}

function openConversation(id: number) {
  conversationStore.open(id);
}

function handleRenameConversation(conv: any) {
  const newTitle = prompt("请输入新的对话名称", conv.title);
  if (newTitle && newTitle.trim()) {
    conversationStore.rename(conv.id, newTitle.trim());
    conv.showActionsPanel = false;
  }
}

function handleRemoveFromKnowledgeBase(conv: any) {
  conversationStore.moveToKnowledgeBase(conv.id, null);
  conv.showActionsPanel = false;
}

function handleDeleteConversation(conv: any) {
  if (confirm(`确定要删除对话"${conv.title}"吗？`)) {
    conversationStore.remove(conv.id);
    conv.showActionsPanel = false;
  }
}
</script>

<template>
  <div class="project-item-container" :class="{ 'is-active': appState.activeKnowledgeBaseId === knowledgeBase.id }">
    <div class="project-item-header">
      <div class="project-item" @click="selectKnowledgeBase">
        <button class="expand-btn" @click.stop="toggleExpand">
          <AppIcon :name="isExpanded ? 'chevron-down' : 'chevron-right'" :size="12" />
        </button>
        <AppIcon :name="knowledgeBase.examAnalysisId ? 'pie-chart' : (knowledgeBase.icon || 'book')" :size="18" :color="knowledgeBase.color" class="project-item__icon" />
        <span class="project-item__name" :title="knowledgeBase.name">{{ knowledgeBase.name }}</span>
        <div
          class="project-item__actions"
          @mouseenter="showActions = true"
          @mouseleave="showActions = false"
        >
          <button
            class="actions-btn"
            @click.stop="toggleActions"
            :class="{ 'actions-btn--active': showActionsPanel }"
          >
            <AppIcon name="more-horizontal" :size="16" />
          </button>
          <div v-if="showActionsPanel" class="actions-panel">
            <button class="action-item" @click="handleEdit">编辑</button>
            <div class="action-divider"></div>
            <button class="action-item action-item--danger" @click="handleDelete">删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 显示与知识库关联的对话 -->
    <div v-if="isExpanded" class="conversation-list">
      <div
        v-for="conv in kbConversations"
        :key="conv.id"
        class="conversation-item"
        @mouseenter="conv.showActions = true"
        @mouseleave="conv.showActions = false"
        @click="openConversation(conv.id)"
      >
        <span class="conversation-name">{{ getConversationDisplayTitle(conv) }}</span>
        <div class="conversation-item__actions" @click.stop>
          <button
            class="actions-btn"
            @click.stop="conv.showActionsPanel = !conv.showActionsPanel"
            :class="{ 'actions-btn--active': conv.showActionsPanel }"
          >
            <AppIcon name="more-horizontal" :size="16" />
          </button>
          <div v-if="conv.showActionsPanel" class="actions-panel">
            <button class="action-item" @click="handleRenameConversation(conv)">重命名</button>
            <button class="action-item" @click="handleRemoveFromKnowledgeBase(conv)">
              从{{ knowledgeBase.name }}知识库移除
            </button>
            <div class="action-divider"></div>
            <button class="action-item action-item--danger" @click="handleDeleteConversation(conv)">
              删除
            </button>
          </div>
        </div>
      </div>
    </div>

    <ConfirmDialog
      :open="showDeleteConfirm"
      title="确认删除"
      :message="`确定要删除知识库'${knowledgeBase.name}'吗？`"
      confirm-text="删除"
      @close="showDeleteConfirm = false"
      @confirm="confirmDelete"
    />

    <KnowledgeBaseCreate
      v-if="showEditDialog && knowledgeBase"
      :open="showEditDialog"
      :knowledge-base="knowledgeBase"
      @close="showEditDialog = false"
    />
  </div>
</template>

<style scoped>
.project-item-container {
  position: relative;
  width: 100%;
}

.is-active .project-item-header {
  background-color: #E6F7FF;
  border-radius: 8px;
  position: relative;
}

.is-active .project-item-header::before {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 16px;
  background-color: var(--color-primary, #1677ff);
  border-radius: 0 4px 4px 0;
}

:root[data-theme='dark'] .is-active .project-item-header {
  background-color: rgba(22, 119, 255, 0.15);
}

.project-item-header {
  display: flex;
  align-items: center;
  width: 100%;
}

.project-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease;
  flex: 1;
  min-width: 0;
}

.project-item:hover {
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme='dark'] .project-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.is-active .project-item:hover {
  background: transparent; /* Let the container handle the background */
}

.expand-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.expand-btn:hover {
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme="dark"] .expand-btn:hover {
  background: rgba(255, 255, 255, 0.06);
}

.project-item__icon {
  font-size: 16px;
  line-height: 1;
  flex-shrink: 0;
}

.project-item__name {
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  width: 180px;
  max-width: 180px;
}

.project-item__actions {
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.2s ease;
  flex-shrink: 0;
}

.project-item:hover .project-item__actions {
  opacity: 1;
}

.actions-btn {
  background: none;
  border: none;
  cursor: pointer;
  width: 24px;
  height: 24px;
  padding: 0;
  border-radius: 6px;
  color: var(--color-text-muted);
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.6;
}

.actions-btn:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--color-text);
  opacity: 1;
}

:root[data-theme="dark"] .actions-btn:hover {
  background: rgba(255, 255, 255, 0.06);
}

.actions-btn--active {
  opacity: 1;
  color: var(--color-text);
}

.actions-panel {
  position: fixed;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  min-width: 130px;
  z-index: 99999;
  margin-left: 35px;
  margin-top: -20px;
}

.action-item {
  display: block;
  width: 100%;
  padding: 8px 16px;
  text-align: left;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: var(--color-text);
  transition: background-color 0.2s ease;
  opacity: 1;
  visibility: visible;
}

.action-item:hover {
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme="dark"] .action-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.action-item--danger {
  color: #ff4d4f;
}

.action-item--danger:hover {
  background: rgba(255, 77, 79, 0.1);
}

.action-divider {
  height: 0.5px;
  background: rgba(0, 0, 0, 0.1);
  margin: 4px 8px;
}

:root[data-theme="dark"] .action-divider {
  background: rgba(255, 255, 255, 0.1);
}

.action-item:first-child {
  border-radius: 8px 8px 0 0;
}

.action-item:last-child {
  border-radius: 0 0 8px 8px;
}

.conversation-list {
  margin-left: 24px;
  margin-top: 4px;
  margin-bottom: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-left: 12px;
  border-left: 1px solid var(--color-border);
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s ease;
  font-size: 13px;
  position: relative;
}

.conversation-item:hover {
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme="dark"] .conversation-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.conversation-item__actions {
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.conversation-item:hover .conversation-item__actions {
  opacity: 1;
}

.conversation-item:hover {
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme="dark"] .conversation-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.conversation-name {
  color: var(--color-text);
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
