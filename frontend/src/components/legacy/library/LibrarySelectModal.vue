<script setup lang="ts">
import { computed, ref } from 'vue'
import AppButton from '@/components/common/AppButton.vue'
import { courseKnowledgeBases } from '@/mock'

const props = defineProps<{
  open: boolean
  selectedId?: number | null
}>()

const emit = defineEmits<{
  close: []
  select: [id: number]
}>()

const keyword = ref('')
const pickedId = ref<number | null>(props.selectedId ?? courseKnowledgeBases[0]?.id ?? null)

const filteredLibraries = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return courseKnowledgeBases
  return courseKnowledgeBases.filter((item) =>
    `${item.name}${item.course}${item.description}`.toLowerCase().includes(text),
  )
})

function confirm() {
  if (pickedId.value) emit('select', pickedId.value)
}
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <section class="modal">
      <header class="modal-head">
        <h2>选择课程资料库</h2>
        <button type="button" @click="emit('close')">×</button>
      </header>

      <input v-model="keyword" class="search" placeholder="搜索资料库" />

      <div class="library-list">
        <label
          v-for="item in filteredLibraries"
          :key="item.id"
          class="library-option"
          :class="{ 'library-option--active': pickedId === item.id }"
        >
          <input v-model="pickedId" type="radio" :value="item.id" />
          <span>
            <strong>{{ item.name }}</strong>
            <small>
              {{ item.fileCount }} 个文件 · {{ item.chunkCount }} 个知识片段 ·
              {{ item.status === 'ready' ? '已完成向量化' : '处理中' }}
            </small>
          </span>
        </label>
      </div>

      <footer class="modal-actions">
        <AppButton variant="secondary" @click="emit('close')">取消</AppButton>
        <AppButton variant="primary" @click="confirm">确认选择</AppButton>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.32);
  z-index: 200;
  display: grid;
  place-items: center;
  padding: 24px;
}

.modal {
  width: min(560px, 100%);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.22);
  padding: 18px;
}

.modal-head,
.modal-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.modal-head h2 {
  margin: 0;
  font-size: 18px;
}

.modal-head button {
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: 24px;
  color: var(--color-text-muted);
}

.search {
  width: 100%;
  margin: 16px 0;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}

.library-list {
  display: grid;
  gap: 8px;
  max-height: 330px;
  overflow: auto;
}

.library-option {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  cursor: pointer;
}

.library-option--active {
  border-color: var(--color-text);
  background: var(--color-bg-alt);
}

.library-option span {
  display: grid;
  gap: 5px;
}

.library-option small {
  color: var(--color-text-muted);
}

.modal-actions {
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
