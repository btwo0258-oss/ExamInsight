<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppButton from '@/components/common/AppButton.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { useLearningStore } from '@/stores/learning'

const props = defineProps<{ open: boolean; selectedId: number | null }>()
const emit = defineEmits<{ close: []; select: [id: number | null] }>()
const learningStore = useLearningStore()
const keyword = ref('')
const pickedId = ref<number | null>(props.selectedId)

const filteredProjects = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return learningStore.plans
  return learningStore.plans.filter((project) => `${project.title}${project.goal}`.toLowerCase().includes(text))
})

watch(() => props.open, (open) => {
  if (!open) return
  pickedId.value = props.selectedId
  keyword.value = ''
})
</script>

<template>
  <div v-if="open" class="project-picker__backdrop" @click.self="emit('close')">
    <section class="project-picker" role="dialog" aria-modal="true" aria-labelledby="project-picker-title">
      <header class="project-picker__header">
        <div>
          <h2 id="project-picker-title">选择关联项目</h2>
          <p>上传文件可关联到一个学习项目及其知识库。</p>
        </div>
        <button class="project-picker__close" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <input v-model="keyword" class="project-picker__search" placeholder="搜索学习项目" />

      <div class="project-picker__list">
        <button
          class="project-picker__option"
          :class="{ 'project-picker__option--active': pickedId === null }"
          type="button"
          @click="pickedId = null"
        >
          <span class="project-picker__copy"><strong>无</strong><small>文件只同步到资料库</small></span>
          <span v-if="pickedId === null" class="project-picker__check"><AppIcon name="check" :size="16" /></span>
        </button>

        <button
          v-for="project in filteredProjects"
          :key="project.id"
          class="project-picker__option"
          :class="{ 'project-picker__option--active': pickedId === project.id }"
          type="button"
          @click="pickedId = project.id"
        >
          <span class="project-picker__copy">
            <strong>{{ project.title }}</strong>
            <small>{{ project.progress }}% · {{ project.status }} · {{ project.period }}</small>
          </span>
          <span v-if="pickedId === project.id" class="project-picker__check"><AppIcon name="check" :size="16" /></span>
        </button>
      </div>

      <footer class="project-picker__footer">
        <AppButton variant="secondary" @click="emit('close')">取消</AppButton>
        <AppButton variant="primary" @click="emit('select', pickedId)">确认选择</AppButton>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.project-picker__backdrop {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.32);
}

.project-picker {
  width: min(520px, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.22);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
}

.project-picker__header {
  flex: 0 0 auto;
  padding: 20px 20px 14px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.project-picker__header h2,
.project-picker__header p {
  margin: 0;
}

.project-picker__header h2 {
  color: var(--color-text);
  font-size: 19px;
}

.project-picker__header p {
  margin-top: 5px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.project-picker__close {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  font-size: 24px;
}

.project-picker__close:hover {
  background: var(--color-hover);
}

.project-picker__search {
  width: calc(100% - 40px);
  height: 42px;
  flex: 0 0 auto;
  margin: 0 20px 12px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  outline: 0;
  background: var(--color-bg);
  color: var(--color-text);
  box-sizing: border-box;
  font: inherit;
}

.project-picker__search:focus {
  border-color: var(--color-primary);
}

.project-picker__list {
  min-height: 0;
  padding: 0 20px;
  display: grid;
  gap: 8px;
  overflow-y: auto;
}

.project-picker__option {
  width: 100%;
  min-height: 62px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 24px;
  align-items: center;
  gap: 12px;
  text-align: left;
  cursor: pointer;
  box-sizing: border-box;
  font: inherit;
}

.project-picker__option:hover {
  background: var(--color-hover);
}

.project-picker__option--active {
  border-color: var(--color-primary);
  background: var(--color-bg-alt);
}

.project-picker__copy {
  min-width: 0;
  display: grid;
  gap: 4px;
  text-align: left;
}

.project-picker__copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-picker__copy small {
  color: var(--color-text-muted);
  font-size: 13px;
}

.project-picker__check {
  color: var(--color-primary);
  display: grid;
  place-items: center;
}

.project-picker__footer {
  flex: 0 0 auto;
  margin-top: 16px;
  padding: 14px 20px;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-height: 640px) {
  .project-picker__backdrop { padding: 12px; }
  .project-picker { max-height: calc(100vh - 24px); }
  .project-picker__header { padding-top: 16px; }
  .project-picker__option { min-height: 56px; }
}
</style>
