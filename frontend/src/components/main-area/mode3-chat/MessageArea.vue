<script setup lang="ts">
// @ts-nocheck
import { computed, watch } from 'vue'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore } from '@/stores/message'
import { useAuthStore } from '@/stores/auth'

import ChatHeader from './ChatHeader.vue'
import MessageList from './message/MessageList.vue'
import AppInput from '@/components/common/AppInput.vue'
import SegmentPanel from './SegmentPanel.vue'

const conversationStore = useConversationStore()
const messageStore = useMessageStore()
const authStore = useAuthStore()

const activeChatId = computed(() => conversationStore.currentId)

watch(activeChatId, (id) => {
  if (id) {
    messageStore.ensureLoaded(id)
  }
}, { immediate: true })

const currentConversation = computed(() => {
  if (!activeChatId.value) return null
  return conversationStore.list.find((c: { id: number }) => c.id === activeChatId.value)
})

const messages = computed(() => {
  if (!activeChatId.value) return []
  const key = String(activeChatId.value)
  const allMsgs = messageStore.byConversation[key] || []
  
  const filtered = allMsgs.filter(m => {
    if (!m.turnId) return true // 兼容旧消息
    
    const activeQ = messageStore.getActiveQVersion(activeChatId.value!, m.turnId)
    const mQ = m.qVersion ?? 0
    const mA = m.aVersion ?? 0
    
    if (m.role === 'user') {
      return mQ === activeQ
    } else if (m.role === 'assistant') {
      const activeA = messageStore.getActiveAVersion(activeChatId.value!, m.turnId, activeQ)
      return mQ === activeQ && mA === activeA
    }
    return true
  })

  // 预先计算每个 turnId 的最早创建时间，用于稳定排序
  const turnMinTime: Record<string, number> = {}
  for (const m of allMsgs) {
    if (m.turnId) {
      if (!turnMinTime[m.turnId] || m.createTime < turnMinTime[m.turnId]) {
        turnMinTime[m.turnId] = m.createTime
      }
    }
  }

  // 稳定排序：按 turn 的最早时间排序，同一个 turn 内部按角色（user在前）和具体创建时间排序
  return filtered.sort((a, b) => {
    const timeA = a.turnId ? turnMinTime[a.turnId] : a.createTime
    const timeB = b.turnId ? turnMinTime[b.turnId] : b.createTime

    if (timeA !== timeB) {
      return timeA - timeB
    }
    
    if (a.role !== b.role) {
      return a.role === 'user' ? -1 : 1
    }

    return a.createTime - b.createTime
  })
})

const showWelcome = computed(() => {
  if (!activeChatId.value) return true
  return messages.value.length === 0
})

async function onSend(text: string, files?: File[]) {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }
  
  if (!activeChatId.value) {
    // 1. 立即跳转到 chat 路由，此时 activeChatId 还是空的
    // 但我们可以预先生成一个 ID 或者等待创建
    const newChatId = await messageStore.createConversation({ firstMessage: text, files })
    
    // 2. 这里的 createConversation 内部已经调用了 sendMessage
    // 我们只需要确保路由跳转足够快
    await conversationStore.fetchList()
    conversationStore.open(newChatId)
    return
  }
  
  // 已经在对话中，直接发送
  await messageStore.sendMessage(activeChatId.value, text, undefined, undefined, undefined, files)
}

function handleKeyDown(e: KeyboardEvent) {
  if (e.ctrlKey && e.shiftKey) {
    if (e.key.toLowerCase() === 'c') {
      e.preventDefault()
      // Copy last AI message
      const lastAiMsg = messages.value.filter(m => m.role === 'assistant').pop()
      if (lastAiMsg) {
        import('@/utils/clipboard').then(({ copyText }) => copyText(lastAiMsg.content))
      }
    } else if (e.key.toLowerCase() === 'r') {
      e.preventDefault()
      // Regenerate last AI message
      const lastAiMsg = messages.value.filter(m => m.role === 'assistant').pop()
      if (lastAiMsg && activeChatId.value && !messageStore.isStreaming) {
        const turnId = lastAiMsg.turnId || lastAiMsg.id
        messageStore.regenerate(activeChatId.value, turnId)
      }
    }
  }
}

import { onMounted, onUnmounted } from 'vue'

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
})
</script>

<template>
  <div class="message-area">
    <ChatHeader v-if="currentConversation" :title="currentConversation.title || '新对话'" />
    
    <div class="message-container">
      <MessageList 
        v-if="!showWelcome" 
        :conversation-id="activeChatId" 
        :messages="messages" 
      />
    </div>
    
    <AppInput 
      :is-streaming="messageStore.isStreaming" 
      placeholder="输入消息，Enter 发送，Shift+Enter 换行"
      @send="onSend" 
    />
    
    <SegmentPanel v-if="!showWelcome" :conversation-id="activeChatId" />
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
</style>