<script setup lang="ts">
// @ts-nocheck
import { computed, watch, onMounted, ref } from "vue";
import type { ChatMessage } from "@/stores/message";
import { useMessageStore } from "@/stores/message";
import { useAppState } from "@/stores/appState";
import MarkdownRenderer from "./MarkdownRenderer.vue";
import SourceChunks from "./SourceChunks.vue";
import AppIcon from "@/components/common/AppIcon.vue";
import MessageActions from "./MessageActions.vue";
import { copyText } from "@/utils/clipboard";

type Props = {
  message: ChatMessage;
  isStreaming?: boolean;
  conversationId?: number | null;
};

const props = withDefaults(defineProps<Props>(), { isStreaming: false });

const emit = defineEmits<{
  copy: [text: string];
  edit: [messageId: string];
  regenerate: [messageId: string];
  generateMindmap: [messageId: string, content: string];
}>();

const isUser = computed(() => props.message.role === "user");
const messageStore = useMessageStore();
const appState = useAppState();

const isEditing = ref(false);
const editDraft = ref(props.message.content);

// Version switcher logic
const versionCount = computed(() => {
  if (!props.conversationId || !props.message.turnId) return 1;
  if (isUser.value) {
    return messageStore.getQVersionCount(props.conversationId, props.message.turnId);
  } else {
    const qVersion = props.message.qVersion ?? 0;
    return messageStore.getAVersionCount(props.conversationId, props.message.turnId, qVersion);
  }
});

const currentVersionIndex = computed(() => {
  if (isUser.value) {
    return (props.message.qVersion ?? 0) + 1;
  } else {
    return (props.message.aVersion ?? 0) + 1;
  }
});

function prevVersion() {
  if (!props.conversationId || !props.message.turnId) return;
  if (isUser.value) {
    const newQ = (props.message.qVersion ?? 0) - 1;
    if (newQ >= 0) messageStore.switchQVersion(props.conversationId, props.message.turnId, newQ);
  } else {
    const qVersion = props.message.qVersion ?? 0;
    const newA = (props.message.aVersion ?? 0) - 1;
    if (newA >= 0)
      messageStore.switchAVersion(props.conversationId, props.message.turnId, qVersion, newA);
  }
}

function nextVersion() {
  if (!props.conversationId || !props.message.turnId) return;
  if (isUser.value) {
    const newQ = (props.message.qVersion ?? 0) + 1;
    if (newQ < versionCount.value)
      messageStore.switchQVersion(props.conversationId, props.message.turnId, newQ);
  } else {
    const qVersion = props.message.qVersion ?? 0;
    const newA = (props.message.aVersion ?? 0) + 1;
    if (newA < versionCount.value)
      messageStore.switchAVersion(props.conversationId, props.message.turnId, qVersion, newA);
  }
}

// Regenerate rate limiting
const isRegenerateDisabled = ref(false);

function reportTelemetry() {
  if (!isUser.value && !props.isStreaming && props.message.durationMs) {
    if (typeof window !== "undefined") {
      const gta = (window as Record<string, unknown>).gta || function () {};
      if (typeof gta === "function") {
        gta("event", "response_time", {
          answerId: props.message.id,
          durationMs: props.message.durationMs,
        });
      }
    }
  }
}

onMounted(() => {
  reportTelemetry();
});

watch(
  () => props.isStreaming,
  (newVal, oldVal) => {
    if (oldVal && !newVal) {
      reportTelemetry();
    }
  },
);

async function onCopy(text: string) {
  await copyText(text);
  // Toast can be added here
}

function onEdit() {
  isEditing.value = true;
  editDraft.value = props.message.content;
}

function cancelEdit() {
  isEditing.value = false;
  editDraft.value = props.message.content;
}

async function submitEdit() {
  if (!editDraft.value.trim() || !props.conversationId) return;
  isEditing.value = false;

  const turnId = props.message.turnId || props.message.id;
  await messageStore.editAndRegenerate(props.conversationId, turnId, editDraft.value);
}

async function onRegenerate() {
  if (!props.conversationId || isRegenerateDisabled.value) return;

  // Rate limiting check
  const now = Date.now();
  const timestampsStr = sessionStorage.getItem("llm.regenerate_timestamps");
  let timestamps: number[] = timestampsStr ? JSON.parse(timestampsStr) : [];

  // Clean up old timestamps (> 10s)
  timestamps = timestamps.filter((t) => now - t < 10000);

  if (timestamps.length >= 3) {
    isRegenerateDisabled.value = true;
    alert("请求过于频繁，请稍后再试（10秒内最多3次）");
    setTimeout(
      () => {
        isRegenerateDisabled.value = false;
      },
      10000 - (now - timestamps[0]),
    );
    return;
  }

  timestamps.push(now);
  sessionStorage.setItem("llm.regenerate_timestamps", JSON.stringify(timestamps));

  const turnId = props.message.turnId || props.message.id;
  await messageStore.regenerate(props.conversationId, turnId);
}

async function onGenerateMindmap() {
  if (!props.conversationId) return;
  emit("generateMindmap", props.message.id, props.message.content);
}
</script>

<template>
  <div :id="`msg-${message.id}`" class="bubble-wrap" :class="{ 'bubble-wrap--user': isUser }">
    <div v-if="!isUser" class="avatar avatar--ai">
      <AppIcon name="robot" />
    </div>

    <div class="bubble-content-wrap">
      <!-- Uploaded files display -->
      <div v-if="isUser && message.files && message.files.length > 0" class="uploaded-files">
        <div v-for="(file, idx) in message.files" :key="idx" class="file-card">
          <AppIcon name="file" :size="24" :color="idx % 2 === 0 ? '#ef4444' : '#3b82f6'" />
          <div class="file-info">
            <div class="file-name">{{ file.name }}</div>
            <div class="file-meta">
              {{ file.type ? file.type.toUpperCase().replace("APPLICATION/", "") : "FILE" }}
              {{ (file.size / 1024).toFixed(2) }} KB
            </div>
          </div>
        </div>
      </div>

      <div class="bubble" :class="isUser ? 'bubble--user' : 'bubble--ai'">
        <div v-if="isUser" class="content">
          <div v-if="isEditing" class="edit-mode">
            <div class="edit-header">
              <div class="edit-title">编辑问题</div>
              <div class="edit-desc">修改提交后系统将重新生成回答</div>
            </div>
            <textarea v-model="editDraft" class="edit-textarea" rows="3"></textarea>
            <div class="edit-actions">
              <button class="btn cancel" @click="cancelEdit">取消</button>
              <button class="btn submit" @click="submitEdit">保存并重新生成</button>
            </div>
          </div>
          <div v-else>
            {{ message.content }}
          </div>
        </div>
        <div v-else class="content content--ai">
          <MarkdownRenderer :content="message.content" />
          <span v-if="isStreaming" class="cursor" />
          <!-- @ts-ignore -->
          <SourceChunks v-if="message.sourceChunks?.length" :chunks="message.sourceChunks" />
          <div
            class="response-time"
            v-if="!isStreaming && message.durationMs"
            @click="appState.toggleTimeUnit"
            title="点击切换单位"
          >
            响应耗时：{{
              appState.timeUnit === "s"
                ? (message.durationMs / 1000).toFixed(3) + " s"
                : message.durationMs + " ms"
            }}
          </div>
          <div class="response-error" v-else-if="!isStreaming && message.errorMsg">
            <details>
              <summary>响应失败</summary>
              <p>{{ message.errorMsg }}</p>
            </details>
          </div>
        </div>
      </div>

      <div
        class="message-footer"
        :class="{ 'message-footer--user': isUser }"
        v-if="!isEditing && (!isStreaming || isUser)"
      >
        <div v-if="versionCount > 1" class="version-switcher">
          <button class="version-btn" :disabled="currentVersionIndex <= 1" @click="prevVersion">
            <AppIcon name="chevron-left" :size="12" />
          </button>
          <span class="version-text">{{ currentVersionIndex }} / {{ versionCount }}</span>
          <button
            class="version-btn"
            :disabled="currentVersionIndex >= versionCount"
            @click="nextVersion"
          >
            <AppIcon name="chevron-right" :size="12" />
          </button>
        </div>

        <MessageActions
          :message="message"
          :is-regenerate-disabled="isRegenerateDisabled"
          @copy="onCopy"
          @edit="onEdit"
          @regenerate="onRegenerate"
          @generate-mindmap="onGenerateMindmap"
        />
      </div>
    </div>

    <div v-if="isUser" class="avatar avatar--user">
      <AppIcon name="user" />
    </div>
  </div>
</template>

<style scoped>
.bubble-wrap {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.bubble-wrap--user {
  justify-content: flex-end;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar--ai {
  background: var(--color-border);
  color: var(--color-text);
}

.avatar--user {
  background: var(--color-text);
  color: var(--color-surface);
}

.bubble-content-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: calc(100% - 48px);
}

.bubble-wrap--user {
  justify-content: flex-end;
}

.bubble-wrap--user .bubble-content-wrap {
  display: inline-flex !important;
  flex-direction: column;
  align-items: flex-end !important;
  max-width: 80% !important;
  width: auto !important;
  min-width: 0 !important;
}

.bubble-wrap--user .bubble {
  width: auto !important;
  max-width: 100% !important;
  display: inline-block !important;
  word-wrap: break-word !important;
  white-space: normal !important;
  text-align: left !important;
  min-width: 0 !important;
}

.uploaded-files {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.file-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background-color: var(--color-surface, #ffffff);
  border: 1px solid var(--color-border, #e5e7eb);
  border-radius: 12px;
  padding: 12px 16px;
  min-width: 220px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

:root[data-theme="dark"] .file-card {
  background-color: #1e1e20;
  border-color: #303133;
}

.file-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow: hidden;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text, #111827);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

:root[data-theme="dark"] .file-name {
  color: #e4e7ed;
}

.file-meta {
  font-size: 12px;
  color: var(--color-text-muted, #6b7280);
}

.message-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 4px;
}

.message-footer--user {
  justify-content: flex-end;
}

.version-switcher {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-secondary, #909399);
  user-select: none;
}

.version-btn {
  background: transparent;
  border: none;
  color: inherit;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition:
    background-color 0.2s,
    color 0.2s;
}

.version-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.version-btn:not(:disabled):hover {
  color: var(--color-text, #303133);
  background-color: rgba(0, 0, 0, 0.05);
}
:root[data-theme="dark"] .version-btn:not(:disabled):hover {
  color: #e4e7ed;
  background-color: rgba(255, 255, 255, 0.1);
}

.bubble {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  display: inline-block;
  max-width: 100%;
}

.bubble--user {
  background: #303133;
  color: #ffffff;
  border-bottom-right-radius: 4px;
  text-align: left;
}

.bubble--ai {
  background: #ffffff;
  border: 1px solid #e4e7ed;
  color: #303133;
  border-bottom-left-radius: 4px;
}

:root[data-theme="dark"] .bubble--user {
  background: #ffffff;
  color: #303133;
}

:root[data-theme="dark"] .bubble--ai {
  background: #1e1e20;
  border: 1px solid #303133;
  color: #e4e7ed;
}

.cursor {
  display: inline-block;
  width: 8px;
  height: 16px;
  background-color: currentColor;
  vertical-align: middle;
  animation: blink 1s step-end infinite;
  margin-left: 4px;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

.response-time {
  font-size: 12px;
  color: var(--color-text-secondary, #666);
  margin-top: 8px;
  text-align: right;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s;
}

.response-time:hover {
  color: var(--color-text, #333);
}

:root[data-theme="dark"] .response-time:hover {
  color: #fff;
}

.response-error {
  font-size: 12px;
  color: var(--color-error, #ff4d4f);
  margin-top: 8px;
}

.response-error details summary {
  cursor: pointer;
  outline: none;
}

.edit-mode {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  min-width: 250px;
}

.edit-header {
  margin-bottom: 4px;
}

.edit-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}

.edit-desc {
  font-size: 12px;
  color: #606266;
  transition: color 0.3s;
}
:root[data-theme="dark"] .edit-desc {
  color: #a3a6ad;
}

.edit-textarea {
  width: 100%;
  padding: 8px 12px;
  border-radius: 6px;
  border: none;
  font-family: inherit;
  font-size: 14px;
  resize: vertical;
  outline: none;
}

/* Light mode (User bubble is dark grey) */
.bubble--user .edit-textarea {
  background-color: #ffffff;
  color: #303133;
}

/* Dark mode (User bubble is white) */
:root[data-theme="dark"] .bubble--user .edit-textarea {
  background-color: #1e1e20;
  color: #e4e7ed;
}

.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  align-items: center;
}

.edit-actions .btn {
  padding: 6px 16px;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s;
  position: relative;
  overflow: hidden;
}

/* Ripple effect */
.edit-actions .btn::after {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.2);
  transform: translate(-50%, -50%) scale(0);
  border-radius: 50%;
  opacity: 0;
  transition:
    transform 0.3s,
    opacity 0.3s;
}
.edit-actions .btn:active::after {
  transform: translate(-50%, -50%) scale(2);
  opacity: 1;
  transition: 0s;
}

/* Cancel Button */
.edit-actions .cancel {
  background-color: #f0f2f5;
  color: #606266;
}
.edit-actions .cancel:hover {
  background-color: #e4e7ed;
}
:root[data-theme="dark"] .edit-actions .cancel {
  background-color: #303133;
  color: #e4e7ed;
}
:root[data-theme="dark"] .edit-actions .cancel:hover {
  background-color: #404246;
}

/* Submit Button (Regenerate) */
.edit-actions .submit {
  background-color: transparent;
  color: #ffffff;
}
.edit-actions .submit:hover {
  opacity: 0.8;
}
:root[data-theme="dark"] .edit-actions .submit {
  background-color: transparent;
  color: #303133;
}
</style>
