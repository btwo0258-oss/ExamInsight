<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import MarkdownRenderer from './MarkdownRenderer.vue'

export type Chunk = {
  docName: string
  chunkIndex: number
  content: string
  _score?: number
}

type Props = { chunks: Chunk[] }
defineProps<Props>()

const expandedIndex = ref<number | null>(null)

function toggle(i: number) {
  expandedIndex.value = expandedIndex.value === i ? null : i
}
</script>

<template>
  <div class="source-chunks" v-if="chunks.length > 0">
    <div class="header">引用来源</div>
    <div class="list">
      <div
        v-for="(c, i) in chunks"
        :key="i"
        class="chunk"
        :class="{ 'chunk--expanded': expandedIndex === i }"
        @click="toggle(i)"
      >
        <div class="chunk__title">
          <span class="chunk__idx">[{{ i + 1 }}]</span>
          <span class="chunk__name">{{ c.docName }} (块 {{ c.chunkIndex }})<span v-if="c._score !== undefined" class="chunk__score"> - 相似度: {{ (c._score * 100).toFixed(1) }}%</span></span>
          <AppIcon name="chevron-right" :size="16" class="chunk__arrow" />
        </div>
        <div v-if="expandedIndex === i" class="chunk__content">
          <MarkdownRenderer :content="c.content" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.source-chunks {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed var(--color-border);
}

.header {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.chunk {
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.02);
  cursor: pointer;
  overflow: hidden;
}

:root[data-theme='dark'] .chunk {
  background: rgba(255, 255, 255, 0.02);
}

.chunk__title {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  font-size: 12px;
  gap: 6px;
}

.chunk__idx {
  color: var(--color-text-muted);
}

.chunk__name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chunk__score {
  color: #10a37f;
  font-size: 11px;
}

.chunk__arrow {
  font-size: 10px;
  color: var(--color-text-muted);
  transition: transform 150ms ease;
}

.chunk--expanded .chunk__arrow {
  transform: rotate(90deg);
}

.chunk__content {
  padding: 0 10px 10px;
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
  white-space: pre-wrap;
}
</style>
