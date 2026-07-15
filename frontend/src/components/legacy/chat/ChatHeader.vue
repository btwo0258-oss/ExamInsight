<script setup lang="ts">
import { computed } from 'vue'
import { useConversationStore } from '@/stores/conversation'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useMessageStore } from '@/stores/message'

const conversationStore = useConversationStore()
const kbStore = useKnowledgeBaseStore()
const messageStore = useMessageStore()

const current = computed(() => {
  return conversationStore.list.find((x) => x.id === conversationStore.currentId)
})

// 计算对话的第一条消息
const firstMessage = computed(() => {
  if (!current.value) return ''
  const messages = messageStore.getMessages(current.value.id)
  // 找到第一条用户消息
  return messages.find(msg => msg.role === 'user')?.content || ''
})

// 计算显示的标题
const title = computed(() => {
  // 如果有自定义标题，使用自定义标题
  if (current.value?.title && current.value.title !== '新对话') {
    return current.value.title
  }
  // 否则使用第一条消息的内容
  return firstMessage.value || '新对话'
})

const kbName = computed(() => {
  const kbId = current.value?.knowledgeBaseId
  if (!kbId) return null
  return kbStore.list.find((x) => x.id === kbId)?.name || `知识库 #${kbId}`
})
</script>

<template>
  <div class="header">
    <div class="title-wrap">
      <span class="title">{{ title }}</span>
      <span v-if="kbName" class="tag">{{ kbName }}</span>
    </div>
  </div>
</template>

<style scoped>
.header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid var(--color-border);
  padding: 0 16px;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title {
  font-weight: 700;
  font-size: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

.tag {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--color-text);
  color: var(--color-surface);
  white-space: nowrap;
}
</style>
