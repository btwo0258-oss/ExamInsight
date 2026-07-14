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
import LearningProfileCard from "@/components/student/LearningProfileCard.vue";
import type { LearningProfileData } from "@/components/student/LearningProfileCard.vue";
import LearningPlanDocument from "@/components/student/LearningPlanDocument.vue";

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
  confirmLearningProfile: [messageId: string];
  updateLearningProfile: [messageId: string, profile: LearningProfileData];
  updateLearningDocument: [messageId: string, content: string];
  regenerateLearningDocument: [messageId: string];
}>();

const isUser = computed(() => props.message.role === "user");
const messageStore = useMessageStore();
const appState = useAppState();

const showMarkdown = ref(true);

watch(() => props.isStreaming, (newVal, oldVal) => {
  if (oldVal && !newVal) {
    showMarkdown.value = false;
    setTimeout(() => {
      showMarkdown.value = true;
    }, 50);
  }
});

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
  if (props.message.kind === "learning-document") {
    emit("regenerateLearningDocument", props.message.id);
    return;
  }
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

async function onGenerateMindmap(_messageId?: string, content?: string) {
  if (!props.conversationId) return;
  emit("generateMindmap", props.message.id, content ?? props.message.content);
}
</script>

<template>
  <div :id="`msg-${message.id}`" class="bubble-wrap" :class="{ 'bubble-wrap--user': isUser }">
    <div v-if="!isUser" class="avatar avatar--ai">
      <AppIcon name="robot" />
    </div>

    <div class="bubble-content-wrap">
      <div v-if="isUser && message.tutorSource" class="tutor-source">
        来自：{{ message.tutorSource.label }}
      </div>
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
          <LearningProfileCard
            v-if="message.kind === 'learning-profile'"
            :profile="message.learningData.profile"
            :loading="message.learningData.loading"
            :confirmed="message.learningData.confirmed"
            @confirm="emit('confirmLearningProfile', message.id)"
            @change="emit('updateLearningProfile', message.id, $event)"
          />
          <LearningPlanDocument
            v-else-if="message.kind === 'learning-document'"
            :content="message.learningData.content"
            :loading="message.learningData.loading"
            @update="(content) => emit('updateLearningDocument', message.id, content)"
          />
          <MarkdownRenderer v-else-if="showMarkdown" :content="message.content" :is-streaming="isStreaming" />
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
        v-if="(!message.kind || (message.kind === 'learning-document' && !message.learningData?.loading)) && !isEditing && (!isStreaming || isUser)"
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
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 16px;
  width: 100%;
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
  margin-left: 0;
}

.avatar--user {
  background: var(--color-text);
  color: var(--color-surface);
  margin-right: 0;
}

.bubble-content-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 774px;
  width: 100%;
}

.bubble-wrap--user {
  justify-content: flex-end;
}

.bubble-wrap--user .bubble-content-wrap {
  display: inline-flex !important;
  flex-direction: column;
  align-items: flex-end !important;
  max-width: 70% !important;
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
  box-sizing: border-box !important;
  overflow: hidden !important;
}

.bubble-wrap--user .bubble .content {
  max-width: 100% !important;
  overflow: hidden !important;
}

.bubble-wrap--user .bubble-content-wrap .edit-mode {
  max-width: 100% !important;
  width: 100% !important;
  box-sizing: border-box !important;
  overflow: hidden !important;
}

.uploaded-files {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.tutor-source {
  padding: 0 4px;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.4;
  text-align: right;
}

.file-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 12px 16px;
  min-width: 220px;
  box-shadow: var(--shadow-sm);
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
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

.file-meta {
  font-size: 12px;
  color: var(--color-text-muted);
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
  color: var(--color-text-muted);
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
  color: var(--color-text);
  background-color: var(--color-hover-strong);
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
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-bottom-right-radius: 4px;
  text-align: left;
}

.bubble--ai {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text);
  border-bottom-left-radius: 4px;
}

.bubble--ai:has(.profile-card),
.bubble--ai:has(.plan-document) {
  width: 100%;
  padding: 0;
  overflow: visible;
  border: 0;
  border-radius: 0;
  background: transparent;
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
  color: var(--color-text-muted);
  margin-top: 8px;
  text-align: right;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s;
}

.response-time:hover {
  color: var(--color-text);
}

.response-error {
  font-size: 12px;
  color: var(--color-danger);
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
  max-width: 100%;
  box-sizing: border-box;
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
  color: var(--color-text-muted);
  transition: color 0.3s;
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

.bubble--user .edit-textarea {
  background-color: var(--color-surface);
  color: var(--color-text);
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
  background-color: var(--color-surface-subtle);
  color: var(--color-text-muted);
}
.edit-actions .cancel:hover {
  background-color: var(--color-hover-strong);
}

/* Submit Button (Regenerate) */
.edit-actions .submit {
  background-color: transparent;
  color: var(--color-on-primary);
}
.edit-actions .submit:hover {
  opacity: 0.8;
}
</style>
