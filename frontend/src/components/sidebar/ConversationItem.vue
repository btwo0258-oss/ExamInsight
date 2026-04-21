<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import ContextMenu, { type MenuItem } from '@/components/common/ContextMenu.vue'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore } from '@/stores/message'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useRouter } from 'vue-router'
import KnowledgeBaseCreate from '../knowledge/KnowledgeBaseCreate.vue'
import type { Conversation } from '@/api/conversation'

import AppIcon from '@/components/common/AppIcon.vue'

type Props = {
  item: Conversation
  active: boolean
}
const props = defineProps<Props>()
const emit = defineEmits<{
  open: [id: number]
  rename: [id: number, title: string]
  remove: [id: number]
}>()

const conversationStore = useConversationStore()
const messageStore = useMessageStore()
const knowledgeBaseStore = useKnowledgeBaseStore()
const router = useRouter()

// 获取关联知识库的图标和颜色
const kbIcon = computed(() => {
  if (!props.item.knowledgeBaseId) return null
  const kb = knowledgeBaseStore.list.find(k => k.id === props.item.knowledgeBaseId)
  return kb ? { name: kb.icon || 'book', color: kb.color } : null
})

const editing = ref(false)
const draft = ref(props.item.title || '新对话')
const inputEl = ref<HTMLInputElement | null>(null)
const menuOpen = ref(false)
const menuPos = ref({ x: 0, y: 0 })
const showActions = ref(false)
const showCreateKb = ref(false)
const createKbOptions = computed(() => ({
  autoNavigate: false,
  autoSwitchMode: false
}))

// 计算显示的标题 - 与ChatHeader保持一致
const displayTitle = computed(() => {
  // 如果有自定义标题，使用自定义标题
  if (props.item.title && props.item.title !== '新对话') {
    return props.item.title
  }
  // 否则使用第一条消息的内容
  const messages = messageStore.getMessages(props.item.id)
  const firstMessage = messages.find(msg => msg.role === 'user')?.content || ''
  return firstMessage || '新对话'
})

watch(
  () => props.item.title,
  (t) => {
    if (!editing.value) draft.value = t || '新对话'
  },
)

async function startEdit() {
  editing.value = true
  draft.value = props.item.title || '新对话'
  await nextTick()
  inputEl.value?.focus()
  inputEl.value?.select()
  menuOpen.value = false
  showActions.value = false
}

function submitEdit() {
  editing.value = false
  emit('rename', props.item.id, draft.value)
}

function toggleActions(e: MouseEvent) {
  e.stopPropagation()
  menuOpen.value = !menuOpen.value
  if (menuOpen.value) {
    const rect = (e.target as HTMLElement).getBoundingClientRect()
    menuPos.value = { x: rect.right, y: rect.top }
  }
}

function handleActionClick() {
  showActions.value = false
}

async function handleMoveToKnowledgeBase(kbId: number | null) {
  await conversationStore.moveToKnowledgeBase(props.item.id, kbId)
  handleActionClick()
}

async function handleCreateKnowledgeBase() {
  showCreateKb.value = true
  menuOpen.value = false
}

async function handleKnowledgeBaseCreated(kbId: number) {
  await conversationStore.moveToKnowledgeBase(props.item.id, kbId)
  showCreateKb.value = false
}

const menuItems = computed<MenuItem[]>(() => {
  const knowledgeBaseItems = knowledgeBaseStore.list.map(kb => ({
    label: kb.name,
    action: () => handleMoveToKnowledgeBase(kb.id)
  }))
  
  return [
    { label: '重命名', action: startEdit },
    {
      label: props.item.isPinned ? '取消置顶' : '置顶对话',
      action: () => {
        conversationStore.togglePin(props.item.id)
        handleActionClick()
      },
    },
    {
      label: '移至知识库',
      children: [
        { label: '+ 新建知识库', action: handleCreateKnowledgeBase },
        { divided: true, label: '' },
        ...knowledgeBaseItems
      ]
    },
    { divided: true, label: '' },
    { 
      label: '删除对话', 
      danger: true, 
      action: () => {
        emit('remove', props.item.id)
        handleActionClick()
      }
    },
  ]
})
</script>

<template>
  <div class="item" :class="{ 'item--active': active }" @click="emit('open', item.id)" @mouseenter="showActions = true" @mouseleave="showActions = false">
    <div v-if="kbIcon" class="item__icon">
      <!-- @ts-ignore -->
      <AppIcon :name="kbIcon.name" :color="kbIcon.color" :size="16" />
    </div>
    
    <input
      v-if="editing"
      ref="inputEl"
      v-model="draft"
      class="item__input"
      @click.stop
      @keydown.enter.prevent="submitEdit"
      @blur="submitEdit"
    />
    <div v-else class="item__title">{{ displayTitle }}</div>
    
    <div class="item__actions">
      <button 
        class="actions-btn" 
        @click.stop="toggleActions($event)"
        :class="{ 'actions-btn--active': menuOpen }"
      >
        <AppIcon name="more-horizontal" :size="16" />
      </button>
      
      <ContextMenu
        v-if="menuOpen"
        :x="menuPos.x"
        :y="menuPos.y"
        :items="menuItems"
        @close="menuOpen = false"
      />
      
      <KnowledgeBaseCreate
        v-if="showCreateKb"
        :open="showCreateKb"
        :auto-navigate="createKbOptions.autoNavigate"
        :auto-switch-mode="createKbOptions.autoSwitchMode"
        @close="showCreateKb = false"
        @created="handleKnowledgeBaseCreated"
      />
    </div>
  </div>
</template>

<style scoped>
.item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  cursor: pointer;
  user-select: none;
  position: relative;
}

.item:hover {
  background: rgba(0, 0, 0, 0.04);
}

:root[data-theme='dark'] .item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.item--active {
  background: rgba(51, 51, 51, 0.08);
}

:root[data-theme='dark'] .item--active {
  background: rgba(255, 255, 255, 0.08);
}

.item__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.item__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.item__input {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--color-border);
  background: transparent;
  border-radius: 8px;
  padding: 6px 8px;
  outline: none;
}

.item__actions {
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.item:hover .item__actions {
  opacity: 1;
}

.actions-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  opacity: 0;
}

.actions-btn:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--color-text);
  opacity: 1;
}

:root[data-theme='dark'] .actions-btn:hover {
  background: rgba(255, 255, 255, 0.06);
}

.actions-btn--active {
  opacity: 1;
  color: var(--color-text);
}
</style>
