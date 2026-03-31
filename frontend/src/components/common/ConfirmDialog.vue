<script setup lang="ts">
import AppModal from "./AppModal.vue";
import AppButton from "./AppButton.vue";

type Props = {
  open: boolean;
  title: string;
  message?: string;
  confirmText?: string;
  cancelText?: string;
  confirmVariant?: 'primary' | 'danger' | 'secondary' | 'ghost';
};

withDefaults(defineProps<Props>(), {
  confirmText: "确认",
  cancelText: "取消",
  confirmVariant: "primary",
});

const emit = defineEmits<{
  close: [];
  confirm: [];
}>();

function handleConfirm() {
  emit("confirm");
  emit("close");
}

function handleCancel() {
  emit("close");
}
</script>

<template>
  <AppModal :open="open" :title="title" @close="handleCancel">
    <p v-if="message" class="message">{{ message }}</p>
    <slot />

    <template #footer>
      <div class="actions">
        <AppButton v-if="cancelText" variant="ghost" @click="handleCancel">{{
          cancelText
        }}</AppButton>
        <AppButton :variant="confirmVariant" @click="handleConfirm">{{ confirmText }}</AppButton>
      </div>
    </template>
  </AppModal>
</template>

<style scoped>
.message {
  font-size: 14px;
  color: var(--color-text-muted);
  line-height: 1.6;
  margin: 0;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
