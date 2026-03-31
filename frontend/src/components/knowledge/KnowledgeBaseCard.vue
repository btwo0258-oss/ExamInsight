<script setup lang="ts">
import type { KnowledgeBase } from '@/api/knowledgeBase'
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useConversationStore } from '@/stores/conversation'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useMindMapStore } from '@/stores/mindmap'

import AppIcon from '@/components/common/AppIcon.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import KnowledgeBaseCreate from './KnowledgeBaseCreate.vue'

type Props = {
  knowledgeBase: KnowledgeBase
}

const props = defineProps<Props>()
const emit = defineEmits(['select'])
const conversationStore = useConversationStore()
const kbStore = useKnowledgeBaseStore()
const mindMapStore = useMindMapStore()

const showActions = ref(false)
const showEditDialog = ref(false)
const showDeleteConfirm = ref(false)

const conversationCount = computed(() => {
  return conversationStore.list.filter(conv => conv.knowledgeBaseId === props.knowledgeBase.id).length
})

const mindMapCount = computed(() => {
  return mindMapStore.mindMapList.filter(map => map.kbId === props.knowledgeBase.id).length
})

function toggleActions(e: Event) {
  e.stopPropagation()
  showActions.value = !showActions.value
}

function handleEdit(e: Event) {
  e.stopPropagation()
  showEditDialog.value = true
  showActions.value = false
}

function handleDelete(e: Event) {
  e.stopPropagation()
  showDeleteConfirm.value = true
  showActions.value = false
}

async function confirmDelete() {
  await kbStore.remove(props.knowledgeBase.id)
  showDeleteConfirm.value = false
}

function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.kb-card__more')) {
    showActions.value = false
  }
}

onMounted(() => {
  window.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  window.removeEventListener('click', handleClickOutside)
})

function formatDate(dateString: string): string {
  const date = new Date(dateString)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  if (days < 30) return `${Math.floor(days / 7)}周前`
  return `${Math.floor(days / 30)}月前`
}
</script>

<template>
  <div class="kb-card" @click="emit('select', knowledgeBase.id)">
    <div class="kb-card__header">
      <div 
        class="kb-card__icon"
        :style="{ 
          backgroundColor: knowledgeBase.color ? knowledgeBase.color + '15' : 'transparent',
          color: knowledgeBase.color || 'inherit'
        }"
      >
        <!-- @ts-ignore -->
        <AppIcon :name="knowledgeBase.icon || 'book'" :size="24" :color="knowledgeBase.color" />
      </div>
      <h3 class="kb-card__title">{{ knowledgeBase.name }}</h3>
      <div class="kb-card__more">
        <button class="more-btn" @click="toggleActions">
          <AppIcon name="more-horizontal" :size="18" />
        </button>
        <div v-if="showActions" class="dropdown-menu">
          <div class="menu-item" @click="handleEdit">
            <AppIcon name="edit" :size="14" />
            <span>重命名</span>
          </div>
          <div class="menu-item menu-item--danger" @click="handleDelete">
            <AppIcon name="trash" :size="14" />
            <span>删除</span>
          </div>
        </div>
      </div>
    </div>
    
    <p class="kb-card__description">
      {{ knowledgeBase.description || '暂无描述' }}
    </p>
    
    <div class="kb-card__footer">
      <div class="kb-card__meta">
        <span class="meta-item">
          <AppIcon name="file" :size="14" />
          {{ knowledgeBase.documentCount || 0 }} 文档
        </span>
        <span class="meta-item">
          <AppIcon name="bar-chart" :size="14" />
          {{ conversationCount }} 对话
        </span>
        <span class="meta-item">
          <AppIcon name="layers" :size="14" />
          {{ mindMapCount }} 思维导图
        </span>
        <span class="meta-item">
          <AppIcon name="clock" :size="14" />
          {{ formatDate(knowledgeBase.createTime) }}
        </span>
      </div>
      <div class="kb-card__arrow">→</div>
    </div>

    <KnowledgeBaseCreate
      v-if="showEditDialog"
      :open="showEditDialog"
      :knowledge-base="knowledgeBase"
      @close="showEditDialog = false"
    />

    <ConfirmDialog
      :open="showDeleteConfirm"
      title="确认删除"
      :message="`确定要删除知识库'${knowledgeBase.name}'吗？此操作不可撤销。`"
      confirm-text="删除"
      @close="showDeleteConfirm = false"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.kb-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.kb-card:hover {
  border-color: var(--color-primary);
  transform: translateY(-4px);
  box-shadow: var(--shadow-md);
}

.kb-card__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  position: relative;
}

.kb-card__icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-text);
  transition: all 0.2s ease;
}

.kb-card__title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-card__more {
  position: relative;
  opacity: 0;
  transition: opacity 0.2s;
}

.kb-card:hover .kb-card__more {
  opacity: 1;
}

.more-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.more-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text);
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 4px;
  min-width: 120px;
  box-shadow: var(--shadow-lg);
  z-index: 100;
  margin-top: 4px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--color-text);
  cursor: pointer;
  border-radius: 4px;
}

.menu-item:hover {
  background: var(--color-surface-hover);
}

.menu-item--danger {
  color: #ef4444;
}

.menu-item--danger:hover {
  background: rgba(239, 68, 68, 0.1);
}

.kb-card__description {
  font-size: 14px;
  color: var(--color-text-muted);
  line-height: 1.5;
  margin: 0 0 20px 0;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  min-height: 42px;
}

.kb-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.kb-card__meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.kb-card__arrow {
  font-size: 18px;
  color: var(--color-primary);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.kb-card:hover .kb-card__arrow {
  opacity: 1;
}
</style>
