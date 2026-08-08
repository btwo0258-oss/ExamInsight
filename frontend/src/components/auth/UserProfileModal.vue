<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Eye, EyeOff } from 'lucide-vue-next'

import AppButton from '@/components/common/AppButton.vue'
import AppModal from '@/components/common/AppModal.vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const authStore = useAuthStore()
const router = useRouter()

const displayName = ref('')
const deletionOpen = ref(false)
const deletionPassword = ref('')
const deletionPasswordVisible = ref(false)
const deletionConfirmation = ref('')
const actionBusy = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')

const userInitials = computed(() => {
  const name = authStore.user?.nickname || authStore.user?.username || 'U'
  return Array.from(name).slice(0, 2).join('').toUpperCase()
})

const canDeleteAccount = computed(() => (
  deletionPassword.value.length > 0
  && deletionConfirmation.value.trim() === '注销账号'
  && !actionBusy.value
))

watch(() => props.open, open => {
  if (!open) return
  displayName.value = authStore.user?.nickname || ''
  deletionOpen.value = false
  deletionPassword.value = ''
  deletionConfirmation.value = ''
  deletionPasswordVisible.value = false
  clearFeedback()
})

function clearFeedback() {
  errorMessage.value = ''
  noticeMessage.value = ''
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

async function saveProfile() {
  clearFeedback()
  const value = displayName.value.normalize('NFC').trim()
  const length = Array.from(value).length
  if (length < 1 || length > 20) {
    errorMessage.value = '昵称需要包含 1–20 个字符'
    return
  }
  if (value === authStore.user?.nickname) {
    noticeMessage.value = '昵称没有变化'
    return
  }
  actionBusy.value = true
  try {
    await authStore.updateProfile(value)
    displayName.value = value
    noticeMessage.value = '昵称已保存'
  } catch (error) {
    errorMessage.value = errorText(error, '昵称保存失败')
  } finally {
    actionBusy.value = false
  }
}

function openDeletion() {
  clearFeedback()
  deletionOpen.value = true
}

function cancelDeletion() {
  deletionOpen.value = false
  deletionPassword.value = ''
  deletionConfirmation.value = ''
  deletionPasswordVisible.value = false
  clearFeedback()
}

async function deleteAccount() {
  clearFeedback()
  if (!canDeleteAccount.value) return
  actionBusy.value = true
  try {
    await authStore.deleteAccount(deletionPassword.value, router)
    emit('close')
  } catch (error) {
    errorMessage.value = errorText(error, '账号注销失败')
  } finally {
    actionBusy.value = false
  }
}
</script>

<template>
  <AppModal :open="open" width="min(620px, 100%)" @close="emit('close')">
    <section class="account-settings">
      <header class="account-summary">
        <div class="avatar">{{ userInitials }}</div>
        <div>
          <h2>个人资料</h2>
          <p>管理你的账号与昵称</p>
        </div>
        <button class="close" type="button" aria-label="关闭个人资料" @click="emit('close')">×</button>
      </header>

      <form class="profile-form" @submit.prevent="saveProfile">
        <label class="field nickname-field">
          <span>昵称</span>
          <span class="nickname-control">
            <input
              v-model="displayName"
              maxlength="20"
              autocomplete="nickname"
              placeholder="最多可输入20个字"
            />
            <AppButton type="submit" :loading="actionBusy">保存昵称</AppButton>
          </span>
        </label>
        <label class="field">
          <span>账号</span>
          <input :value="authStore.user?.email" type="email" autocomplete="username" disabled />
        </label>
      </form>

      <p v-if="noticeMessage" class="notice" role="status">{{ noticeMessage }}</p>
      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>

      <section class="danger-zone">
        <div class="danger-heading">
          <div>
            <h3>注销账号</h3>
            <p>注销后会立即退出，账号将无法继续登录，并进入数据删除队列。</p>
          </div>
          <AppButton v-if="!deletionOpen" variant="danger" @click="openDeletion">注销账号</AppButton>
        </div>

        <form v-if="deletionOpen" class="deletion-form" @submit.prevent="deleteAccount">
          <div class="danger-notice">
            <strong>此操作不可在产品内撤销</strong>
            <p>依法需要保留的安全审计记录及备份可能不会在操作瞬间物理清除。</p>
          </div>
          <label class="field">
            <span>当前密码</span>
            <span class="password-control">
              <input
                v-model="deletionPassword"
                :type="deletionPasswordVisible ? 'text' : 'password'"
                autocomplete="current-password"
              />
              <button type="button" :aria-label="deletionPasswordVisible ? '隐藏密码' : '显示密码'" @click="deletionPasswordVisible = !deletionPasswordVisible">
                <Eye v-if="deletionPasswordVisible" :size="18" />
                <EyeOff v-else :size="18" />
              </button>
            </span>
          </label>
          <label class="field">
            <span>输入“注销账号”以确认</span>
            <input v-model="deletionConfirmation" autocomplete="off" placeholder="注销账号" />
          </label>
          <div class="deletion-actions">
            <AppButton variant="ghost" :disabled="actionBusy" @click="cancelDeletion">取消</AppButton>
            <AppButton variant="danger" type="submit" :disabled="!canDeleteAccount" :loading="actionBusy">确认注销</AppButton>
          </div>
        </form>
      </section>
    </section>
  </AppModal>
</template>

<style scoped>
.account-settings { display: grid; gap: 22px; color: var(--color-text); }
.account-summary { position: relative; display: flex; align-items: center; gap: 14px; padding: 0 0 20px; border-bottom: 1px solid var(--color-border); }
.account-summary h2 { margin: 0; font-size: 20px; }
.account-summary p { margin: 5px 0 0; color: var(--color-text-muted); font-size: 13px; }
.avatar { display: grid; width: 48px; height: 48px; flex: 0 0 auto; place-items: center; border-radius: 50%; background: var(--color-text); color: var(--color-bg); font-weight: 800; }
.close { position: absolute; top: -8px; right: -4px; width: 32px; height: 32px; border: 0; border-radius: 50%; background: transparent; color: var(--color-text-muted); font-size: 24px; cursor: pointer; }
.close:hover { background: var(--color-hover); color: var(--color-text); }
.profile-form, .deletion-form { display: grid; gap: 16px; }
.field { position: relative; display: grid; gap: 7px; color: var(--color-text); font-size: 13px; font-weight: 700; }
.field > input, .nickname-control input, .password-control input { width: 100%; height: 46px; box-sizing: border-box; border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 0 14px; background: var(--color-surface); color: var(--color-text); outline: none; font: inherit; font-weight: 400; }
.field > input:focus, .nickname-control input:focus, .password-control input:focus { border-color: var(--color-text-muted); box-shadow: 0 0 0 3px var(--color-hover); }
.field > input:disabled { background: var(--color-hover); color: var(--color-text-muted); cursor: not-allowed; }
.field small { justify-self: end; color: var(--color-text-muted); font-size: 11px; font-weight: 400; }
.nickname-control { display: grid; width: 100%; grid-template-columns: minmax(0, 1fr) auto; align-items: stretch; gap: 14px; }
.nickname-control :deep(.btn) { min-width: 96px; height: 46px; }
.password-control { position: relative; display: block; }
.password-control input { padding-right: 44px; }
.password-control button { position: absolute; top: 50%; right: 7px; display: grid; width: 32px; height: 32px; place-items: center; transform: translateY(-50%); border: 0; border-radius: var(--radius-sm); background: transparent; color: var(--color-text-muted); cursor: pointer; }
.password-control button:hover { background: var(--color-hover); color: var(--color-text); }
.deletion-actions { display: flex; justify-content: flex-end; gap: 10px; }
.notice, .error { margin: -8px 0 0; border-radius: var(--radius-sm); padding: 10px 12px; font-size: 13px; line-height: 1.5; }
.notice { background: color-mix(in srgb, var(--color-success) 10%, transparent); color: var(--color-success); }
.error { background: color-mix(in srgb, var(--color-danger) 10%, transparent); color: var(--color-danger); }
.danger-zone { display: grid; gap: 16px; border-top: 1px solid var(--color-border); padding-top: 20px; }
.danger-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.danger-heading h3 { margin: 0; font-size: 15px; }
.danger-heading p { margin: 6px 0 0; color: var(--color-text-muted); font-size: 12px; line-height: 1.6; }
.deletion-form { border: 1px solid color-mix(in srgb, var(--color-danger) 30%, var(--color-border)); border-radius: var(--radius-md); padding: 16px; background: color-mix(in srgb, var(--color-danger) 4%, var(--color-surface)); }
.danger-notice strong { color: var(--color-danger); font-size: 13px; }
.danger-notice p { margin: 6px 0 0; color: var(--color-text-muted); font-size: 12px; line-height: 1.6; }
@media (max-width: 520px) {
  .danger-heading { align-items: stretch; flex-direction: column; }
}
</style>
