<script setup lang="ts">
import { ref, watch } from 'vue'
import AppModal from './AppModal.vue'
import AppButton from './AppButton.vue'

type Props = {
  open: boolean
  title: string
  label?: string
  placeholder?: string
  defaultValue?: string
  confirmText?: string
  cancelText?: string
}

const props = withDefaults(defineProps<Props>(), {
  label: '名称',
  placeholder: '请输入...',
  defaultValue: '',
  confirmText: '确认',
  cancelText: '取消'
})

const emit = defineEmits<{
  close: []
  confirm: [value: string]
}>()

const inputValue = ref(props.defaultValue)

watch(
  () => props.open,
  (open) => {
    if (open) {
      inputValue.value = props.defaultValue
    }
  }
)

function handleSubmit() {
  if (!inputValue.value.trim()) return
  emit('confirm', inputValue.value.trim())
  emit('close')
}
</script>

<template>
  <AppModal :open="open" :title="title" @close="emit('close')" width="400px">
    <form class="form" @submit.prevent="handleSubmit">
      <div class="field">
        <label v-if="label" class="field__label">{{ label }} <span class="required">*</span></label>
        <input
          v-model="inputValue"
          type="text"
          class="field__input"
          :placeholder="placeholder"
          required
        />
      </div>
    </form>
    <template #footer>
      <div class="actions">
        <AppButton type="button" variant="ghost" @click="emit('close')">{{ cancelText }}</AppButton>
        <AppButton type="button" variant="primary" @click="handleSubmit">{{ confirmText }}</AppButton>
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

.field__label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.field__input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 14px;
  background: var(--color-surface);
  color: var(--color-text);
  transition: border-color 0.2s ease;
}

.field__input:focus {
  outline: none;
  border-color: var(--color-primary);
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
