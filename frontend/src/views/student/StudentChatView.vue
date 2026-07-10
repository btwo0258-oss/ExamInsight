<script setup lang="ts">
// @ts-nocheck
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import AppIcon from '@/components/common/AppIcon.vue'
import AppInput from '@/components/common/AppInput.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import MindMapPanel from '@/components/main-area/mode3-chat/MindMapPanel.vue'
import SegmentPanel from '@/components/main-area/mode3-chat/SegmentPanel.vue'
import MessageList from '@/components/main-area/mode3-chat/message/MessageList.vue'
import { useAuthStore } from '@/stores/auth'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore } from '@/stores/message'

const authStore = useAuthStore()
const conversationStore = useConversationStore()
const messageStore = useMessageStore()

const activeChatId = computed(() => conversationStore.currentId)

watch(
  activeChatId,
  (id) => {
    if (!id) return
    messageStore.ensureLoaded(id).then(() => {
      const autoMsgKey = `chat_auto_msg_${id}`
      const autoMsgStr = sessionStorage.getItem(autoMsgKey)
      if (!autoMsgStr) return

      try {
        const autoMsg = JSON.parse(autoMsgStr)
        sessionStorage.removeItem(autoMsgKey)
        messageStore.sendMessage(id, autoMsg.message)
      } catch (err) {
        console.error('Failed to parse auto message:', err)
        sessionStorage.removeItem(autoMsgKey)
      }
    })
  },
  { immediate: true },
)

const currentConversation = computed(() => {
  if (!activeChatId.value) return null
  return conversationStore.list.find((item) => item.id === activeChatId.value) || null
})

const pageTitle = computed(() => currentConversation.value?.title || '新对话')

const messages = computed(() => {
  if (!activeChatId.value) return []
  const key = String(activeChatId.value)
  const allMsgs = messageStore.byConversation[key] || []

  const filtered = allMsgs.filter((m) => {
    if (!m.turnId) return true

    const activeQ = messageStore.getActiveQVersion(activeChatId.value, m.turnId)
    const mQ = m.qVersion ?? 0
    const mA = m.aVersion ?? 0

    if (m.role === 'user') {
      return mQ === activeQ
    }
    if (m.role === 'assistant') {
      const activeA = messageStore.getActiveAVersion(activeChatId.value, m.turnId, activeQ)
      return mQ === activeQ && mA === activeA
    }
    return true
  })

  const turnMinTime: Record<string, number> = {}
  for (const m of allMsgs) {
    if (m.turnId && (!turnMinTime[m.turnId] || m.createTime < turnMinTime[m.turnId])) {
      turnMinTime[m.turnId] = m.createTime
    }
  }

  return filtered.sort((a, b) => {
    const timeA = a.turnId ? turnMinTime[a.turnId] : a.createTime
    const timeB = b.turnId ? turnMinTime[b.turnId] : b.createTime

    if (timeA !== timeB) return timeA - timeB
    if (a.role !== b.role) return a.role === 'user' ? -1 : 1
    return a.createTime - b.createTime
  })
})

const showWelcome = computed(() => !activeChatId.value || messages.value.length === 0)

const showMindMapPanel = ref(false)
const mindMapContent = ref('')
const mindMapTitle = ref('')
const mindMapSidebarCollapsed = ref(false)
const messageListRef = ref<InstanceType<typeof MessageList> | null>(null)

const messageListContainer = computed(() => messageListRef.value?.scrollContainer ?? null)

async function onSend(text: string, files?: File[]) {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
    return
  }

  if (!activeChatId.value) {
    const result = await messageStore.createConversation({ firstMessage: text, files })
    const newChatId = result.id

    sessionStorage.setItem(
      `chat_auto_msg_${newChatId}`,
      JSON.stringify({
        message: text,
        files: files?.map((file) => ({ name: file.name, type: file.type, size: file.size })),
      }),
    )

    await conversationStore.fetchList()
    conversationStore.open(newChatId)
    return
  }

  await messageStore.sendMessage(activeChatId.value, text, undefined, undefined, undefined, files)
}

function handleKeyDown(e: KeyboardEvent) {
  if (!e.ctrlKey || !e.shiftKey) return

  if (e.key.toLowerCase() === 'c') {
    e.preventDefault()
    const lastAiMsg = messages.value.filter((m) => m.role === 'assistant').pop()
    if (lastAiMsg) {
      import('@/utils/clipboard').then(({ copyText }) => copyText(lastAiMsg.content))
    }
    return
  }

  if (e.key.toLowerCase() === 'r') {
    e.preventDefault()
    const lastAiMsg = messages.value.filter((m) => m.role === 'assistant').pop()
    if (lastAiMsg && activeChatId.value && !messageStore.isStreaming) {
      const turnId = lastAiMsg.turnId || lastAiMsg.id
      messageStore.regenerate(activeChatId.value, turnId)
    }
  }
}

function onGenerateMindmap(messageId: string, content: string) {
  mindMapContent.value = content
  mindMapTitle.value = ''
  showMindMapPanel.value = true
  mindMapSidebarCollapsed.value = false
}

function onMindMapSaved() {
  showMindMapPanel.value = false
}

function handleToggleMindMapSidebar() {
  mindMapSidebarCollapsed.value = !mindMapSidebarCollapsed.value
}

onMounted(() => {
  conversationStore.init()
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
})
</script>

<template>
  <StudentShell>
    <section class="student-chat">
      <header class="student-chat__header">
        <h1>{{ pageTitle }}</h1>
      </header>

      <div class="student-chat__body">
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

        <p v-if="messageStore.errorMessage" class="chat-error">
          {{ messageStore.errorMessage }}
        </p>

        <SegmentPanel
          v-if="!showWelcome"
          :conversation-id="activeChatId"
          :container-ref="messageListContainer"
        />

        <MindMapPanel
          :visible="showMindMapPanel"
          :ai-content="mindMapContent"
          :ai-title="mindMapTitle"
          @close="showMindMapPanel = false"
          @saved="onMindMapSaved"
          @toggle-sidebar="handleToggleMindMapSidebar"
        />

        <button
          v-if="showMindMapPanel && mindMapSidebarCollapsed"
          class="mindmap-fab"
          type="button"
          @click="handleToggleMindMapSidebar"
        >
          <AppIcon name="panel-left-open" :size="20" />
        </button>
      </div>
    </section>
  </StudentShell>
</template>

<style scoped>
.student-chat {
  height: 100%;
  min-width: 0;
  background: var(--color-bg);
  color: var(--color-text);
  display: flex;
  flex-direction: column;
  position: relative;
}

.student-chat__header {
  height: 58px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.student-chat__header h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0;
}

.student-chat__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}

.message-container {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  position: relative;
}

.chat-error {
  width: min(800px, calc(100% - 32px));
  margin: -28px auto 16px;
  color: var(--color-danger);
  font-size: 12px;
}

.mindmap-fab {
  position: fixed;
  top: 12px;
  right: 12px;
  z-index: 9998;
  width: 44px;
  height: 44px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  box-shadow: var(--shadow-md);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: background 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
}

.mindmap-fab:hover {
  background: var(--color-hover);
  color: var(--color-text);
  box-shadow: var(--shadow-lg);
}
</style>
