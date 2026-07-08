<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import AppModal from '@/components/common/AppModal.vue'
import AppButton from '@/components/common/AppButton.vue'
import AppIcon from '@/components/common/AppIcon.vue'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const authStore = useAuthStore()
const nickname = ref(authStore.user?.nickname || '')
const errorMessage = ref('')
const isSubmitting = ref(false)

const userInitials = computed(() => {
  const name = nickname.value || authStore.user?.username || 'U'
  return name.slice(0, 2).toUpperCase()
})

async function handleSave() {
  if (!nickname.value.trim()) return
  
  errorMessage.value = ''
  isSubmitting.value = true
  try {
    await authStore.updateProfile({ nickname: nickname.value.trim() })
    emit('close')
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '更新资料失败'
    console.error('Failed to update profile:', err)
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AppModal :open="open" @close="emit('close')">
    <div class="profile-modal">
      <h2 class="title">编辑个人资料</h2>
      
      <div class="avatar-section">
        <div class="avatar-circle">
          <span class="initials">{{ userInitials }}</span>
          <!-- <div class="camera-icon">
            <AppIcon name="camera" :size="14" />
          </div> -->
        </div>
      </div>

      <div class="form">
        <div class="form-item">
          <label>显示名称</label>
          <input 
            v-model="nickname" 
            type="text" 
            placeholder="请输入您的昵称"
            :disabled="isSubmitting"
          />
        </div>
        
        <div class="form-item">
          <label>用户名</label>
          <input 
            :value="authStore.user?.username" 
            type="text" 
            disabled
            class="disabled-input"
          />
        </div>
        
        <p class="hint">个人资料有助于他人识别你的身份。你的姓名和用户名也将用于 ExamInsight 应用。</p>
        
        <div v-if="errorMessage" class="error-msg">
          {{ errorMessage }}
        </div>
      </div>

      <div class="actions">
        <AppButton variant="secondary" @click="emit('close')" :disabled="isSubmitting">
          取消
        </AppButton>
        <AppButton variant="primary" @click="handleSave" :loading="isSubmitting">
          保存
        </AppButton>
      </div>
    </div>
  </AppModal>
</template>

<style scoped>
.profile-modal {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text);
}

.avatar-section {
  display: flex;
  justify-content: center;
  padding: 12px 0;
}

.avatar-circle {
  width: 120px;
  height: 120px;
  border-radius: 60px;
  background: #10a37f; /* DeepSeek/ChatGPT green style */
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  color: white;
}

.initials {
  font-size: 40px;
  font-weight: 500;
  letter-spacing: 1px;
}

.camera-icon {
  position: absolute;
  bottom: 4px;
  right: 4px;
  width: 28px;
  height: 28px;
  background: #343541;
  border: 2px solid var(--color-surface);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  cursor: default;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.form-item label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.form-item input {
  background: transparent;
  border: none;
  font-size: 16px;
  color: var(--color-text);
  padding: 0;
  outline: none;
}

.form-item input:disabled {
  cursor: not-allowed;
}

.disabled-input {
  opacity: 0.7;
}

.hint {
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.5;
  margin: 0;
}

.error-msg {
  font-size: 13px;
  color: #ef4444;
  margin-top: 4px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}
</style>
