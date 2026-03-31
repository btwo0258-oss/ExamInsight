<script setup lang="ts">
import AppModal from './AppModal.vue'
import AppButton from './AppButton.vue'

type Props = {
  open: boolean
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  variant?: 'primary' | 'danger'
}

withDefaults(defineProps<Props>(), {
  confirmText: '确认',
  cancelText: '取消',
  variant: 'primary'
})

const emit = defineEmits<{
  close: []
  confirm: []
}>()

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('close')
}
</script>

<template>
  <AppModal :open="open" :title="title" @close="handleCancel">
    <p class="message">{{ message }}</p>
    
    <template #footer>
      <div class="actions">
        <AppButton v-if="cancelText" variant="ghost" @click="handleCancel">{{ cancelText }}</AppButton>
        <AppButton :variant="variant === 'danger' ? 'danger' : 'primary'" @click="handleConfirm">
          {{ confirmText }}
        </AppButton>
      </div>
    </template>
  </AppModal>
</template>

<style scoped>
.message {
  font-size: 15px;
  color: #4b5563;
  line-height: 1.6;
  margin: 0;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
