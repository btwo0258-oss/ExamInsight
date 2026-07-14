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
  viewMode?: 'grid' | 'list'
}

const props = withDefaults(defineProps<Props>(), {
  viewMode: 'grid'
})
const emit = defineEmits(['select'])
const conversationStore = useConversationStore()
const kbStore = useKnowledgeBaseStore()
const mindMapStore = useMindMapStore()

const showActions = ref(false)
const showEditDialog = ref(false)
const showDeleteConfirm = ref(false)
const showTooltip = ref(false)

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
  if (!dateString) return '未知时间'
  const date = new Date(dateString)
  if (isNaN(date.getTime())) return '无效日期'
  
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  
  return `创建时间 ${year}-${month}-${day}`
}
</script>

<template>
  <div class="kb-card" :class="[`kb-card--${viewMode}`, { 'kb-card--active': showActions }]" @click="emit('select', knowledgeBase.id)">
    <div class="kb-card__header">
      <div 
        class="kb-card__icon"
        :style="{ backgroundColor: knowledgeBase.color + '15', color: knowledgeBase.color }"
      >
        <AppIcon :name="knowledgeBase.examAnalysisId ? 'pie-chart' : (knowledgeBase.icon || 'folder')" :size="20" />
      </div>
      <div class="kb-card__main">
        <div class="tooltip-container" @mouseenter="showTooltip = true" @mouseleave="showTooltip = false">
          <h3 class="kb-card__title">{{ knowledgeBase.name }}</h3>
          <div v-if="showTooltip && knowledgeBase.name.length > 20" class="custom-tooltip">
            {{ knowledgeBase.name }}
          </div>
        </div>
        <p class="kb-card__description" :title="knowledgeBase.description || '暂无描述'">{{ knowledgeBase.description || '暂无描述' }}</p>
      </div>
      
      <div class="kb-card__footer">
        <div class="kb-card__stats">
          <div class="kb-card__stat">
            <AppIcon name="message-square" :size="14" />
            <span>{{ conversationCount }}</span>
          </div>
          <div class="kb-card__stat">
            <AppIcon name="folder" :size="14" />
            <span>{{ knowledgeBase.documentCount || 0 }}</span>
          </div>
          <div class="kb-card__stat">
            <AppIcon name="layers" :size="14" />
            <span>{{ mindMapCount }}</span>
          </div>
        </div>
        <div class="kb-card__time">
          <span>{{ formatDate(knowledgeBase.updateTime) }}</span>
        </div>
      </div>

      <div class="kb-card__actions">
        <div class="actions-wrapper">
          <button class="dots-btn" @click.stop="showActions = !showActions">
            <AppIcon name="more-horizontal" :size="20" />
          </button>
          <div class="overlay" v-if="showActions" @click.stop="showActions = false"></div>
          <div class="actions-menu ui-menu-panel" v-if="showActions">
            <div class="menu-item ui-menu-item" @click.stop="handleEdit">
              <AppIcon class="ui-menu-icon" name="edit" :size="16" />
              <span>编辑信息</span>
            </div>
            <div class="menu-item ui-menu-item ui-menu-item--danger" @click.stop="handleDelete">
              <AppIcon class="ui-menu-icon" name="trash" :size="16" />
              <span>删除知识库</span>
            </div>
          </div>
        </div>
      </div>
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
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  overflow: visible;
}

.kb-card--active {
  z-index: 200;
}

.kb-card:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.kb-card__header {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  gap: 16px;
}

.kb-card__icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kb-card__main {
  flex: 1;
  min-width: 0;
  width: 100%;
  overflow: hidden;
}

.kb-card__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 4px 0;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-card__description {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-card__footer {
  display: flex;
  align-items: center;
  margin-right: 12px;
}

.kb-card__stats {
  display: flex;
  align-items: center;
  gap: 16px;
}

.kb-card__stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.kb-card__time {
  display: none;
}

.kb-card__actions {
  display: flex;
  align-items: center;
}

.actions-wrapper {
  position: relative;
}

.dots-btn {
  background: transparent;
  border: none;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--color-text-muted);
  transition: all 0.2s;
}

.dots-btn:hover {
  background: var(--color-bg-alt);
  color: var(--color-text);
}

.overlay {
  position: fixed;
  inset: 0;
  z-index: 90;
}

.actions-menu {
  position: absolute;
  top: 100%;
  right: 0;
  min-width: 168px;
  z-index: 100;
  margin-top: 4px;
}

/* Grid layout overrides */
.kb-card--grid {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 120px;
  box-sizing: border-box;
}

.kb-card--grid .kb-card__header {
  flex-direction: column;
  align-items: flex-start;
  flex: 1;
  padding: 16px;
  width: 100%;
  box-sizing: border-box;
}

.kb-card--grid .kb-card__footer {
  margin-top: 16px;
  margin-right: 0;
  margin-bottom: 8px;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
}

.kb-card--grid .kb-card__stats {
  flex-direction: row;
  align-items: center;
  gap: 12px;
}

.kb-card--grid .kb-card__time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.kb-card--grid .kb-card__actions {
  position: absolute;
  top: 16px;
  right: 16px;
}

.kb-card--grid .kb-card__main {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  overflow: hidden;
}

.kb-card--grid .kb-card__title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  display: block;
  margin: 0;
}

.kb-card--grid .kb-card__description {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  width: 100%;
}

.tooltip-container {
  position: relative;
  display: block;
  width: 100%;
  overflow: hidden;
}

.custom-tooltip {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.8);
  color: white;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
  z-index: 1000;
  margin-bottom: 8px;
  opacity: 0;
  animation: tooltipFadeIn 0.1s ease-out forwards;
  pointer-events: none;
}

.custom-tooltip::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 4px solid transparent;
  border-top-color: rgba(0, 0, 0, 0.8);
}

@keyframes tooltipFadeIn {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}
</style>
