<script setup lang="ts">
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-vue-next'
import type { PresentationSlideLayout, PresentationSlideOutline } from '@/types/contracts/presentation'

const props = defineProps<{
  slides: PresentationSlideOutline[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  update: [slides: PresentationSlideOutline[]]
}>()

const layoutOptions: Array<{ value: PresentationSlideLayout; label: string }> = [
  { value: 'cover', label: '封面' },
  { value: 'section', label: '章节' },
  { value: 'content', label: '内容' },
  { value: 'comparison', label: '对比' },
  { value: 'summary', label: '总结' },
]

function replaceSlide(index: number, patch: Partial<PresentationSlideOutline>) {
  emit('update', props.slides.map((slide, slideIndex) => slideIndex === index ? { ...slide, ...patch } : slide))
}

function updatePoints(index: number, value: string) {
  replaceSlide(index, { points: value.split('\n').map((point) => point.trim()).filter(Boolean) })
}

function move(index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= props.slides.length) return
  const next = [...props.slides]
  const [slide] = next.splice(index, 1)
  next.splice(target, 0, slide!)
  emit('update', next.map((item, slideIndex) => ({ ...item, order: slideIndex + 1 })))
}

function remove(index: number) {
  if (props.slides.length <= 3) return
  emit('update', props.slides.filter((_, slideIndex) => slideIndex !== index).map((item, slideIndex) => ({ ...item, order: slideIndex + 1 })))
}

function addSlide() {
  if (props.slides.length >= 30) return
  const order = props.slides.length + 1
  emit('update', [...props.slides, {
    id: `slide-${Date.now()}`,
    order,
    title: '新增页面',
    points: ['补充这一页需要表达的重点'],
    speakerNotes: '',
    layout: 'content',
  }])
}
</script>

<template>
  <div class="outline-editor">
    <article v-for="(slide, index) in slides" :key="slide.id" class="outline-card">
      <header>
        <span>{{ String(index + 1).padStart(2, '0') }}</span>
        <select :value="slide.layout" :disabled="disabled" aria-label="页面版式" @change="replaceSlide(index, { layout: ($event.target as HTMLSelectElement).value as PresentationSlideLayout })">
          <option v-for="option in layoutOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
        <div class="outline-actions">
          <button type="button" title="上移" :disabled="disabled || index === 0" @click="move(index, -1)"><ArrowUp :size="16" /></button>
          <button type="button" title="下移" :disabled="disabled || index === slides.length - 1" @click="move(index, 1)"><ArrowDown :size="16" /></button>
          <button type="button" title="删除页面" :disabled="disabled || slides.length <= 3" @click="remove(index)"><Trash2 :size="16" /></button>
        </div>
      </header>

      <label>
        <span>页面标题</span>
        <input :value="slide.title" :disabled="disabled" maxlength="100" @input="replaceSlide(index, { title: ($event.target as HTMLInputElement).value })" />
      </label>
      <label>
        <span>页面要点（一行一个）</span>
        <textarea :value="slide.points.join('\n')" :disabled="disabled" rows="4" @input="updatePoints(index, ($event.target as HTMLTextAreaElement).value)" />
      </label>
      <label>
        <span>演讲者备注</span>
        <textarea :value="slide.speakerNotes" :disabled="disabled" rows="2" @input="replaceSlide(index, { speakerNotes: ($event.target as HTMLTextAreaElement).value })" />
      </label>
    </article>

    <button class="add-slide" type="button" :disabled="disabled || slides.length >= 30" @click="addSlide">
      <Plus :size="17" />
      添加一页
    </button>
  </div>
</template>

<style scoped>
.outline-editor {
  display: grid;
  gap: 12px;
}

.outline-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.outline-card header {
  display: grid;
  grid-template-columns: 34px 110px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.outline-card header > span {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.outline-actions {
  display: flex;
  justify-content: flex-end;
  gap: 3px;
}

.outline-actions button,
.add-slide {
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}

.outline-actions button {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 50%;
}

.outline-actions button:hover:not(:disabled) {
  background: var(--ui-hover-strong-bg);
  color: var(--color-text);
}

.outline-actions button:disabled,
.add-slide:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.outline-card label {
  display: grid;
  gap: 6px;
}

.outline-card label span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.outline-card input,
.outline-card textarea,
.outline-card select {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  background: var(--color-bg);
  color: var(--color-text);
  font: inherit;
}

.outline-card input,
.outline-card select {
  height: 36px;
  padding: 0 10px;
}

.outline-card textarea {
  padding: 9px 10px;
  line-height: 1.5;
  resize: vertical;
}

.add-slide {
  min-height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px dashed var(--color-border);
  border-radius: 8px;
}

.add-slide:hover:not(:disabled) {
  border-color: var(--color-text-muted);
  background: var(--ui-hover-bg);
  color: var(--color-text);
}

@media (max-width: 640px) {
  .outline-card header {
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .outline-actions {
    grid-column: 1 / -1;
  }
}
</style>
