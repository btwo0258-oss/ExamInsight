<script setup lang="ts">
// @ts-nocheck
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import AppIcon from '@/components/common/AppIcon.vue'
import AppInput from '@/components/common/AppInput.vue'
import StudentShell from '@/components/student/StudentShell.vue'
import MindMapPanel from '@/components/main-area/mode3-chat/MindMapPanel.vue'
import SegmentPanel from '@/components/main-area/mode3-chat/SegmentPanel.vue'
import MessageList from '@/components/main-area/mode3-chat/message/MessageList.vue'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore } from '@/stores/message'
import { useLibraryResourceStore } from '@/stores/libraryResource'

const conversationStore = useConversationStore()
const messageStore = useMessageStore()
const libraryResourceStore = useLibraryResourceStore()

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
const homeInputRef = ref<InstanceType<typeof AppInput> | null>(null)

const messageListContainer = computed(() => messageListRef.value?.scrollContainer ?? null)

const homePromptActions = [
  { icon: 'image', label: '生成图片', prompt: '帮我生成一张适合学习资料使用的图片，主题是：' },
  { icon: 'edit', label: '撰写或编辑', prompt: '帮我撰写或润色这段内容：' },
  { icon: 'search', label: '查找资料', prompt: '帮我查找并整理关于这个主题的资料：' },
  { icon: 'sparkle', label: '生成 PPT', prompt: '帮我生成一份 PPT 大纲，主题是：' },
  { icon: 'mindmap', label: '生成思维导图', prompt: '帮我生成一个思维导图，主题是：' },
]

async function onSend(text: string, files?: File[]) {
  if (files?.length) {
    libraryResourceStore.addFiles(
      files,
      '聊天上传',
      null,
      currentConversation.value?.knowledgeBaseId ?? null,
    )
  }

  if (!activeChatId.value) {
    const newChatId = await conversationStore.create()
    await messageStore.sendMessage(newChatId, text, undefined, undefined, undefined, files)
    return
  }

  await messageStore.sendMessage(activeChatId.value, text, undefined, undefined, undefined, files)
}

function fillHomePrompt(prompt: string) {
  homeInputRef.value?.setText(prompt)
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
  libraryResourceStore.addChatGenerated(
    `${mindMapTitle.value.trim() || pageTitle.value}思维导图`,
    currentConversation.value?.knowledgeBaseId ?? null,
  )
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
      <header v-if="!showWelcome" class="student-chat__header">
        <h1>{{ pageTitle }}</h1>
      </header>

      <div class="student-chat__body">
        <div v-if="showWelcome" class="chat-home">
          <div class="chat-home__main">
            <h1>我们先从哪里开始呢？</h1>

            <div class="home-action-chips">
              <button
                v-for="action in homePromptActions"
                :key="action.label"
                class="home-action-chip"
                type="button"
                @click="fillHomePrompt(action.prompt)"
              >
                <AppIcon :name="action.icon" :size="18" />
                <span>{{ action.label }}</span>
              </button>
            </div>
          </div>

          <div class="chat-home__input">
            <AppInput
              ref="homeInputRef"
              :is-streaming="messageStore.isStreaming"
              placeholder="随心输入"
              @send="onSend"
            />
          </div>

          <p v-if="messageStore.errorMessage" class="chat-error chat-error--home">
            {{ messageStore.errorMessage }}
          </p>
        </div>

        <template v-else>
        <div class="message-container">
          <MessageList
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
          :conversation-id="activeChatId"
          :container-ref="messageListContainer"
        />
        </template>

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

.chat-home {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.chat-home__main {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  padding: 8vh 24px 4vh;
}

.chat-home__main h1 {
  margin: 0 0 28px;
  color: var(--color-text);
  font-size: 26px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 0;
  text-align: center;
}

.chat-home__input {
  width: 100%;
  flex-shrink: 0;
}

.chat-home__input :deep(.footer-hint) {
  display: none;
}

.home-action-chips {
  width: min(760px, 100%);
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.home-action-chip {
  min-height: 64px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  text-align: left;
  font: inherit;
  font-size: 13px;
  line-height: 18px;
  box-shadow: var(--shadow-sm);
  transition: background 0.16s ease, color 0.16s ease, border-color 0.16s ease;
}

.home-action-chip:hover {
  background: var(--color-hover);
  border-color: var(--color-hover-strong);
  color: var(--color-text);
}

.home-action-chip span {
  color: inherit;
  font-weight: 600;
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

.chat-error--home {
  width: min(760px, 100%);
  margin: 12px 0 0;
}

.chat-home .home-action-chip:nth-child(1) {
  color: #8b5cf6;
}

.chat-home .home-action-chip:nth-child(2) {
  color: #64748b;
}

.chat-home .home-action-chip:nth-child(3) {
  color: #16a34a;
}

.chat-home .home-action-chip:nth-child(4) {
  color: #f97316;
}

.chat-home .home-action-chip:nth-child(5) {
  color: #7c3aed;
}

@media (max-width: 760px) {
  .chat-home__main {
    padding: 7vh 16px 3vh;
  }

  .chat-home__main h1 {
    font-size: 22px;
    margin-bottom: 22px;
  }

  .home-action-chips {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
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
