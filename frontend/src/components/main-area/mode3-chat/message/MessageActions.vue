<script setup lang="ts">
import { computed, ref } from "vue";
import type { ChatMessage } from "@/stores/message";
import AppIcon from "@/components/common/AppIcon.vue";

type Props = {
  message: ChatMessage;
  isRegenerateDisabled?: boolean;
};

const props = withDefaults(defineProps<Props>(), { isRegenerateDisabled: false });
const emit = defineEmits<{
  copy: [text: string];
  edit: [messageId: string];
  regenerate: [messageId: string];
  generateMindmap: [messageId: string, content: string];
}>();

const isUser = computed(() => props.message.role === "user");
const copied = ref(false);

function handleCopy() {
  emit("copy", props.message.kind === "learning-document" ? props.message.learningData?.content || "" : props.message.content);
  copied.value = true;
  setTimeout(() => {
    copied.value = false;
  }, 1500);
}

function handleEdit() {
  emit("edit", props.message.id);
}

function handleRegenerate() {
  if (props.isRegenerateDisabled) return;
  emit("regenerate", props.message.id);
}

function handleGenerateMindmap() {
  const content = props.message.kind === "learning-document" ? props.message.learningData?.content || "" : props.message.content;
  emit("generateMindmap", props.message.id, content);
}
</script>

<template>
  <div class="message-actions" :class="{ 'message-actions--user': isUser }">
    <button class="action-btn" title="复制" @click="handleCopy">
      <AppIcon :name="copied ? 'check' : 'copy'" :size="16" :class="{ 'anim-pop': copied }" />
    </button>

    <template v-if="isUser">
      <button class="action-btn" title="编辑" @click="handleEdit">
        <AppIcon name="edit" :size="16" />
      </button>
    </template>

    <template v-else>
      <button
        class="action-btn"
        title="重新生成"
        :disabled="isRegenerateDisabled"
        @click="handleRegenerate"
      >
        <AppIcon name="refresh-single" :size="16" />
      </button>
      <button class="action-btn" title="AI生成思维导图" @click="handleGenerateMindmap">
        <AppIcon name="layers" :size="16" />
      </button>
    </template>
  </div>
</template>

<style scoped>
.message-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color 0.2s ease;
  padding: 0;
}

.action-btn:hover:not(:disabled) {
  color: var(--color-text);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.anim-pop {
  animation: pop 0.2s ease-out;
}

@keyframes pop {
  0% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
