<script setup lang="ts">
import { ref, onMounted } from 'vue'
import AppIcon from '@/components/admin/AppIcon.vue'
import AppButton from '@/components/admin/AppButton.vue'
import AppModal from '@/components/admin/AppModal.vue'
import { getAllConfigs, updateConfig } from '@/api/adminconfig'
import type { SystemConfig } from '@/types'

// Config Data
const configs = ref<SystemConfig[]>([])
const loading = ref(false)

async function fetchConfigs() {
  loading.value = true
  try {
    const data = await getAllConfigs()
    configs.value = data
  } catch (err) {
    console.error('Failed to fetch configs:', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchConfigs()
})

const showEdit = ref(false)
const editItem = ref<any | null>(null)
const editValue = ref('')

function handleEdit(item: any) {
  editItem.value = item
  editValue.value = item.configValue
  showEdit.value = true
}

async function handleSave() {
  if (editItem.value) {
    try {
      await updateConfig(editItem.value.configKey, editValue.value)
      editItem.value.configValue = editValue.value
      showEdit.value = false
    } catch (err) {
      console.error('Failed to save config:', err)
    }
  }
}
</script>

<template>
  <div class="config-page">
    <header class="page-header">
      <div class="header-info">
        <h2 class="page-title">系统配置</h2>
        <p class="page-subtitle">管理 Aether 系统的全局参数和业务规则</p>
      </div>
      
      <AppButton variant="primary">
        <template #icon><AppIcon name="refresh" :size="16" /></template>
        同步配置
      </AppButton>
    </header>
    
    <div class="config-grid" v-if="configs && configs.length > 0">
      <div v-for="config in configs" :key="config.configKey" class="config-card card">
        <div class="config-main">
          <div class="config-icon-wrap">
            <AppIcon name="settings" :size="20" />
          </div>
          <div class="config-details">
            <div class="config-key">{{ config.configKey }}</div>
            <div class="config-value">{{ config.configValue }}</div>
            <div class="config-desc">{{ config.description || '无描述' }}</div>
          </div>
        </div>
        <div class="config-actions">
          <AppButton variant="ghost" size="small" @click="handleEdit(config)">
            <template #icon><AppIcon name="edit" :size="14" /></template>
            编辑
          </AppButton>
        </div>
      </div>
    </div>
    
    <div v-else class="empty-state" style="text-align: center; padding: 40px; color: var(--color-text-muted);">
      暂无系统配置项
    </div>

    <!-- Edit Modal -->
    <AppModal 
      :open="showEdit" 
      :title="`修改配置: ${editItem?.configKey}`"
      @close="showEdit = false"
    >
      <div class="edit-form">
        <div class="form-item">
          <label>配置值</label>
          <input 
            v-model="editValue" 
            type="text" 
            class="edit-input"
            :placeholder="`请输入 ${editItem?.configKey} 的新值`"
          />
          <p class="edit-hint">{{ editItem?.description }}</p>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer-actions">
          <AppButton variant="ghost" @click="showEdit = false">取消</AppButton>
          <AppButton variant="primary" @click="handleSave">保存修改</AppButton>
        </div>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.config-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
}

.page-subtitle {
  font-size: 14px;
  color: var(--color-text-muted);
}

.config-grid {
  columns: 3 360px;
  column-gap: 20px;
}

.config-card {
  display: inline-block;
  width: 100%;
  margin-bottom: 20px;
  break-inside: avoid;
  padding: 20px;
}

.config-main {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  flex: 1;
}

.config-icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background-color: rgba(0, 0, 0, 0.03);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  flex-shrink: 0;
}

.config-details {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.config-key {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  margin-bottom: 4px;
}

.config-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 8px;
  word-break: break-all;
}

.config-desc {
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.config-actions {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--color-border);
  padding-top: 16px;
}

.edit-form {
  padding: 8px 0;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item label {
  font-size: 14px;
  font-weight: 500;
}

.edit-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background-color: var(--color-bg);
  outline: none;
  font-size: 14px;
  transition: border-color 0.2s;
}

.edit-input:focus {
  border-color: var(--color-primary);
}

.edit-hint {
  font-size: 12px;
  color: var(--color-text-muted);
}

.modal-footer-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
