<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { KnowledgeBase } from '@/api/knowledgeBase'
import AppModal from '@/components/common/AppModal.vue'
import AppButton from '@/components/common/AppButton.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import IconPicker from '@/components/common/IconPicker.vue'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useAppState } from '@/stores/appState'

type Props = {
  open: boolean
  knowledgeBase?: KnowledgeBase
  autoNavigate?: boolean
  autoSwitchMode?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  autoNavigate: false,
  autoSwitchMode: false
})
const emit = defineEmits<{ 
  close: []
  created: [id: number]
}>()

const router = useRouter()
const appState = useAppState()
const kbStore = useKnowledgeBaseStore()

const name = ref('')
const description = ref('')
const icon = ref('book')
const color = ref('#4f46e5') // Default primary color

const colors = [
  '#4f46e5', '#ef4444', '#f59e0b', '#10b981', '#3b82f6', '#8b5cf6', 
  '#ec4899', '#06b6d4', '#84cc16', '#71717a', '#44403c', '#000000'
]

watch(
  () => props.open,
  (open) => {
    if (open) {
      if (props.knowledgeBase) {
        name.value = props.knowledgeBase.name
        description.value = props.knowledgeBase.description || ''
        icon.value = props.knowledgeBase.icon || 'book'
        color.value = props.knowledgeBase.color || '#4f46e5'
      } else {
        name.value = ''
        description.value = ''
        icon.value = 'book'
        color.value = '#4f46e5'
      }
    }
  },
)

async function submit() {
  if (!name.value.trim()) return
  
  if (props.knowledgeBase) {
    await kbStore.update({
      ...props.knowledgeBase,
      name: name.value,
      description: description.value,
      icon: icon.value,
      color: color.value,
    })
  } else {
    const newKb = await kbStore.create({
      name: name.value,
      description: description.value,
      icon: icon.value,
      color: color.value,
    })
    
    if (props.autoSwitchMode) {
      appState.setMode('knowledge')
      appState.setActiveKnowledgeBase(newKb.id)
    }
    
    if (props.autoNavigate) {
      router.push(`/knowledge/${newKb.id}`)
    }
    
    emit('created', newKb.id)
  }
  
  emit('close')
}
</script>

<template>
  <AppModal 
    :open="open" 
    :title="knowledgeBase ? '编辑知识库' : '新建知识库'"
    @close="emit('close')"
  >
    <form class="form" @submit.prevent="submit">
      <div class="field">
        <label class="field__label">名称 <span class="required">*</span></label>
        <input
          v-model="name"
          type="text"
          class="field__input"
          placeholder="输入知识库名称"
          required
        />
      </div>
      
      <div class="field">
        <label class="field__label">描述</label>
        <textarea
          v-model="description"
          class="field__textarea"
          rows="3"
          placeholder="输入知识库描述（可选）"
        />
      </div>
      
      <div class="field field--centered">
        <label class="field__label">图标与颜色</label>
        <div class="picker-container">
          <div class="preview-box" :style="{ backgroundColor: color + '15', color: color }">
            <AppIcon :name="icon" :size="32" :color="color" />
          </div>
          <div class="pickers">
            <IconPicker v-model="icon" />
            <div class="color-picker">
              <button
                v-for="c in colors"
                :key="c"
                type="button"
                class="color-option"
                :class="{ active: color === c }"
                :style="{ backgroundColor: c }"
                @click="color = c"
              />
            </div>
          </div>
        </div>
      </div>
    </form>

    <template #footer>
      <div class="actions">
        <AppButton type="button" variant="ghost" @click="emit('close')">取消</AppButton>
        <AppButton type="submit" variant="primary" @click="submit">{{ knowledgeBase ? '保存' : '创建' }}</AppButton>
      </div>
    </template>
  </AppModal>
</template>

<style scoped>
.form {
  display: grid;
  gap: 20px;
}

.field {
  display: grid;
  gap: 8px;
}

.field--centered {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.field--centered .field__label {
  align-self: flex-start;
}

.picker-container {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  width: 100%;
}

.preview-box {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.pickers {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.color-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.color-option {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
  transition: all 0.2s ease;
}

.color-option:hover {
  transform: scale(1.1);
}

.color-option.active {
  border-color: var(--color-text);
  transform: scale(1.2);
}

.field__label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.field__input,
.field__textarea {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 14px;
  background: var(--color-surface);
  color: var(--color-text);
  transition: border-color 0.2s ease;
}

.field__input:focus,
.field__textarea:focus {
  outline: none;
  border-color: var(--color-primary);
}

.field__textarea {
  resize: vertical;
  min-height: 80px;
}

.required {
  color: #ff4d4f;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}
</style>
