<script setup lang="ts">
import { nextTick, ref, watch } from "vue";

import MessageBubble from "./MessageBubble.vue";
import type { ChatMessage } from "@/stores/message";

type Props = {
  conversationId: number | null;
  messages: ChatMessage[];
};

const props = defineProps<Props>();
const emit = defineEmits<{
  generateMindmap: [messageId: string, content: string];
}>();

const wrap = ref<HTMLDivElement | null>(null);
const autoScroll = ref(true);

function onScroll() {
  const el = wrap.value;
  if (!el) return;
  const distance = el.scrollHeight - el.scrollTop - el.clientHeight;
  autoScroll.value = distance < 120;
}

async function scrollToBottom() {
  await nextTick();
  const el = wrap.value;
  if (!el) return;
  el.scrollTop = el.scrollHeight;
}

watch(
  () => {
    const last = props.messages[props.messages.length - 1];
    return [
      wrap.value,
      autoScroll.value,
      props.messages.length,
      last?.content.length ?? 0,
      last?.streaming ?? false,
    ];
  },
  async () => {
    if (!autoScroll.value) return;
    await scrollToBottom();
  },
  { deep: false },
);
</script>

<template>
  <div ref="wrap" class="list" @scroll="onScroll">
    <div class="list__inner">
      <MessageBubble
        v-for="m in messages"
        :key="m.turnId ? `${m.turnId}-${m.role}-${m.qVersion ?? 0}-${m.aVersion ?? 0}` : m.id"
        :message="m"
        :conversation-id="conversationId"
        :is-streaming="m.streaming"
        @generate-mindmap="(id, content) => emit('generateMindmap', id, content)"
      />
    </div>
  </div>
</template>

<style scoped>
.list {
  height: 100%;
  overflow: auto;
  display: flex;
  flex-direction: column;
}

.list__inner {
  width: min(800px, 100%);
  margin: 0 auto;
  padding: 24px 28px 20px;
  display: grid;
  gap: 16px;
  flex: 1;
}
</style>
