<script setup lang="ts">
import { computed } from 'vue'

import AppButton from '@/components/common/AppButton.vue'
import AppModal from '@/components/common/AppModal.vue'
import { useAuthStore } from '@/stores/auth'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const authStore = useAuthStore()

const userInitials = computed(() => {
  const name = authStore.user?.nickname || authStore.user?.username || 'U'
  return name.slice(0, 2).toUpperCase()
})
</script>

<template>
  <AppModal :open="open" @close="emit('close')">
    <section class="profile-modal">
      <h2>账户资料</h2>
      <div class="avatar">{{ userInitials }}</div>
      <dl>
        <div><dt>显示名称</dt><dd>{{ authStore.user?.nickname || '未设置' }}</dd></div>
        <div><dt>登录邮箱</dt><dd>{{ authStore.user?.email }}</dd></div>
      </dl>
      <p>公开 Beta 暂不提供资料修改入口；后续接入 V2 用户资料接口后再开放编辑。</p>
      <div class="actions"><AppButton variant="primary" @click="emit('close')">完成</AppButton></div>
    </section>
  </AppModal>
</template>

<style scoped>
.profile-modal { display: grid; gap: 20px; color: var(--color-text); }
h2 { margin: 0; font-size: 20px; }
.avatar { width: 88px; height: 88px; margin: 0 auto; border-radius: 50%; display: grid; place-items: center; background: var(--color-text); color: var(--color-bg); font-size: 28px; font-weight: 700; }
dl { display: grid; gap: 10px; margin: 0; }
dl div { padding: 12px; border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface-subtle); }
dt { color: var(--color-text-muted); font-size: 12px; }
dd { margin: 5px 0 0; overflow-wrap: anywhere; }
p { margin: 0; color: var(--color-text-muted); font-size: 13px; line-height: 1.5; }
.actions { display: flex; justify-content: flex-end; }
</style>
