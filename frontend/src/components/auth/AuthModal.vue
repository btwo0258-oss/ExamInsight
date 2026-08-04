<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { AuthApiError, createRegistrationChallenge, verifyRegistrationEmail } from '@/api/v2Auth'
import AppButton from '@/components/common/AppButton.vue'
import AppModal from '@/components/common/AppModal.vue'
import { preloadHumanVerification, runHumanVerification } from '@/services/humanVerification'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import logoUrl from '@/assets/icons/ExamInsight-Logo.png'
import logoWhiteUrl from '@/assets/icons/ExamInsight-Logo-White.png'

type Props = { open: boolean }
type RegistrationStep = 'email' | 'code' | 'profile'

const props = defineProps<Props>()
const emit = defineEmits<{ close: [] }>()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const router = useRouter()

const mode = ref<'login' | 'register'>('login')
const registrationStep = ref<RegistrationStep>('email')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const displayName = ref('')
const verificationCode = ref('')
const challengeId = ref('')
const registrationProof = ref('')
const agreeToPolicies = ref(false)
const ageGateAcknowledged = ref(false)
const localError = ref<string | null>(null)
const captchaBusy = ref(false)
const actionBusy = ref(false)
const resendRemaining = ref(0)
let resendTimer: number | null = null

const currentLogo = computed(() => themeStore.isDark ? logoWhiteUrl : logoUrl)
const visibleError = computed(() => localError.value || authStore.errorMessage)
const isBusy = computed(() => captchaBusy.value || actionBusy.value || authStore.isSubmitting)

function normalizedEmail() {
  return email.value.trim().toLowerCase()
}

function validEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) && value.length <= 254
}

function passwordValidation(): string | null {
  const normalized = password.value.normalize('NFC')
  const length = Array.from(normalized).length
  if (length < 15 || length > 128) return '密码需要包含 15–128 个字符'
  if (/\p{Cc}/u.test(normalized)) return '密码不能包含控制字符'
  if (confirmPassword.value !== password.value) return '两次输入的密码不一致'
  return null
}

function resetRegistrationProgress() {
  registrationStep.value = 'email'
  verificationCode.value = ''
  challengeId.value = ''
  registrationProof.value = ''
  password.value = ''
  confirmPassword.value = ''
  displayName.value = ''
  ageGateAcknowledged.value = false
  stopResendTimer()
}

function switchMode(next: 'login' | 'register') {
  if (mode.value === next) return
  mode.value = next
  localError.value = null
  authStore.errorMessage = null
  password.value = ''
  confirmPassword.value = ''
  if (next === 'register') resetRegistrationProgress()
  preloadHumanVerification()
}

function stopResendTimer() {
  if (resendTimer !== null) window.clearInterval(resendTimer)
  resendTimer = null
  resendRemaining.value = 0
}

function startResendTimer(seconds: number) {
  stopResendTimer()
  resendRemaining.value = Math.max(0, Math.ceil(seconds))
  resendTimer = window.setInterval(() => {
    resendRemaining.value = Math.max(0, resendRemaining.value - 1)
    if (resendRemaining.value === 0) stopResendTimer()
  }, 1_000)
}

function setError(error: unknown, fallback: string) {
  if (error instanceof Error) localError.value = error.message
  else localError.value = fallback
}

async function requestEmailCode() {
  localError.value = null
  const nextEmail = normalizedEmail()
  if (!validEmail(nextEmail)) {
    localError.value = '请输入有效的邮箱地址'
    return
  }
  if (!agreeToPolicies.value) {
    localError.value = '请先同意用户协议与隐私政策'
    return
  }
  if (resendRemaining.value > 0) return

  captchaBusy.value = true
  try {
    const humanVerificationToken = await runHumanVerification()
    actionBusy.value = true
    const challenge = await createRegistrationChallenge({
      email: nextEmail,
      humanVerificationToken,
    })
    challengeId.value = challenge.challengeId
    verificationCode.value = ''
    registrationStep.value = 'code'
    startResendTimer(challenge.resendAfterSeconds)
    preloadHumanVerification()
  } catch (error) {
    setError(error, '验证码发送失败，请稍后重试。')
  } finally {
    captchaBusy.value = false
    actionBusy.value = false
  }
}

async function verifyEmailCode() {
  localError.value = null
  if (!/^\d{6}$/.test(verificationCode.value)) {
    localError.value = '请输入邮件中的 6 位验证码'
    return
  }
  if (!challengeId.value) {
    registrationStep.value = 'email'
    localError.value = '验证码请求已失效，请重新获取'
    return
  }

  actionBusy.value = true
  try {
    const proof = await verifyRegistrationEmail(challengeId.value, verificationCode.value)
    registrationProof.value = proof.registrationProof
    registrationStep.value = 'profile'
    stopResendTimer()
  } catch (error) {
    if (error instanceof AuthApiError && [
      'VERIFICATION_LOCKED',
      'VERIFICATION_EXPIRED',
      'CHALLENGE_NOT_PENDING',
    ].includes(error.code)) {
      registrationStep.value = 'email'
      challengeId.value = ''
    }
    setError(error, '邮箱验证码校验失败。')
  } finally {
    actionBusy.value = false
  }
}

async function finishRegistration() {
  localError.value = null
  const name = displayName.value.trim()
  if (!name || name.length > 80) {
    localError.value = '请输入不超过 80 个字符的昵称'
    return
  }
  const passwordError = passwordValidation()
  if (passwordError) {
    localError.value = passwordError
    return
  }
  if (!ageGateAcknowledged.value) {
    localError.value = '请确认你已达到产品要求的最低使用年龄'
    return
  }
  if (!registrationProof.value) {
    registrationStep.value = 'email'
    localError.value = '邮箱验证证明已失效，请重新验证邮箱'
    return
  }

  try {
    await authStore.register({
      email: normalizedEmail(),
      password: password.value,
      displayName: name,
      ageGateAcknowledged: true,
      registrationProof: registrationProof.value,
    }, router)
    emit('close')
  } catch (error) {
    if (error instanceof AuthApiError && error.code === 'INVALID_REGISTRATION_PROOF') {
      resetRegistrationProgress()
    }
    setError(error, '注册失败，请稍后重试。')
  }
}

async function submitLogin(humanVerificationToken?: string) {
  await authStore.login({
    email: normalizedEmail(),
    password: password.value,
    humanVerificationToken,
  }, router)
}

async function login() {
  localError.value = null
  if (!validEmail(normalizedEmail())) {
    localError.value = '请输入有效的邮箱地址'
    return
  }
  if (!password.value) {
    localError.value = '请输入密码'
    return
  }

  try {
    await submitLogin()
    emit('close')
  } catch (error) {
    if (!(error instanceof AuthApiError) || error.code !== 'HUMAN_VERIFICATION_REQUIRED') {
      setError(error, '登录失败，请稍后重试。')
      return
    }

    captchaBusy.value = true
    try {
      const humanVerificationToken = await runHumanVerification()
      await submitLogin(humanVerificationToken)
      emit('close')
    } catch (retryError) {
      setError(retryError, '登录失败，请稍后重试。')
    } finally {
      captchaBusy.value = false
      preloadHumanVerification()
    }
  }
}

async function submit() {
  if (isBusy.value) return
  if (mode.value === 'login') return login()
  if (registrationStep.value === 'email') return requestEmailCode()
  if (registrationStep.value === 'code') return verifyEmailCode()
  return finishRegistration()
}

function close() {
  if (isBusy.value) return
  emit('close')
}

watch(() => props.open, open => {
  if (!open) return
  localError.value = null
  authStore.errorMessage = null
  password.value = ''
  confirmPassword.value = ''
  preloadHumanVerification()
})

onBeforeUnmount(stopResendTimer)
</script>

<template>
  <AppModal :open="open" width="min(520px, 100%)" :close-on-backdrop="!isBusy" @close="close">
    <section class="auth" aria-labelledby="auth-title">
      <header class="auth__header">
        <img :src="currentLogo" alt="ExamInsight" class="auth__logo" />
        <div>
          <h1 id="auth-title">{{ mode === 'login' ? '欢迎回来' : '创建 ExamInsight 账户' }}</h1>
          <p>{{ mode === 'login' ? '继续你的学习计划' : '使用邮箱验证，保护你的学习资料' }}</p>
        </div>
      </header>

      <div class="mode-switch" role="tablist" aria-label="登录或注册">
        <button type="button" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button>
        <button type="button" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button>
      </div>

      <div v-if="mode === 'register'" class="steps" aria-label="注册进度">
        <span :class="{ active: registrationStep === 'email' }">1 验证邮箱</span>
        <span :class="{ active: registrationStep === 'code' }">2 输入验证码</span>
        <span :class="{ active: registrationStep === 'profile' }">3 创建账户</span>
      </div>

      <form class="auth__form" @submit.prevent="submit">
        <template v-if="mode === 'login'">
          <label class="field">
            <span>邮箱</span>
            <input v-model="email" type="email" autocomplete="username" placeholder="name@example.com" />
          </label>
          <label class="field">
            <span>密码</span>
            <input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
          </label>
        </template>

        <template v-else-if="registrationStep === 'email'">
          <label class="field">
            <span>邮箱</span>
            <input v-model="email" type="email" autocomplete="email" placeholder="用于登录和接收验证码" />
          </label>
          <label class="check">
            <input v-model="agreeToPolicies" type="checkbox" />
            <span>我已阅读并同意用户协议与隐私政策</span>
          </label>
          <p class="hint">点击获取验证码后会先进行一次滑块验证，验证通过才会发送邮件。</p>
        </template>

        <template v-else-if="registrationStep === 'code'">
          <div class="verified-email">
            <div><span>验证码已发送至</span><strong>{{ normalizedEmail() }}</strong></div>
            <button type="button" @click="resetRegistrationProgress">修改邮箱</button>
          </div>
          <label class="field">
            <span>6 位邮箱验证码</span>
            <input
              v-model="verificationCode"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              placeholder="000000"
            />
          </label>
          <button
            class="resend"
            type="button"
            :disabled="resendRemaining > 0 || isBusy"
            @click="requestEmailCode"
          >
            {{ resendRemaining > 0 ? `${resendRemaining} 秒后可重新发送` : '重新发送验证码' }}
          </button>
        </template>

        <template v-else>
          <div class="verified-email verified-email--success">
            <div><span>邮箱验证完成</span><strong>{{ normalizedEmail() }}</strong></div>
          </div>
          <label class="field">
            <span>昵称</span>
            <input v-model="displayName" autocomplete="nickname" maxlength="80" placeholder="学习空间中显示的名称" />
          </label>
          <label class="field">
            <span>密码</span>
            <input v-model="password" type="password" autocomplete="new-password" placeholder="至少 15 个字符" />
          </label>
          <label class="field">
            <span>确认密码</span>
            <input v-model="confirmPassword" type="password" autocomplete="new-password" placeholder="再次输入密码" />
          </label>
          <label class="check">
            <input v-model="ageGateAcknowledged" type="checkbox" />
            <span>我确认已达到产品要求的最低使用年龄</span>
          </label>
        </template>

        <div v-if="visibleError" class="error" role="alert">{{ visibleError }}</div>

        <AppButton class="submit" type="submit" variant="primary" :loading="isBusy">
          <template v-if="mode === 'login'">登录</template>
          <template v-else-if="registrationStep === 'email'">完成滑块并获取验证码</template>
          <template v-else-if="registrationStep === 'code'">验证邮箱</template>
          <template v-else>创建账户</template>
        </AppButton>
      </form>

      <button class="close" type="button" :disabled="isBusy" aria-label="关闭" @click="close">×</button>
    </section>
  </AppModal>
</template>

<style scoped>
.auth { position: relative; display: grid; gap: 20px; color: var(--color-text); }
.auth__header { display: flex; align-items: center; gap: 14px; padding-right: 28px; }
.auth__logo { width: 76px; height: 76px; object-fit: contain; flex: 0 0 auto; }
.auth__header h1 { margin: 0; font-size: 22px; line-height: 1.25; }
.auth__header p { margin: 6px 0 0; color: var(--color-text-muted); font-size: 13px; }
.close { position: absolute; top: -8px; right: -4px; width: 32px; height: 32px; border: 0; border-radius: 50%; background: transparent; color: var(--color-text-muted); font-size: 24px; cursor: pointer; }
.close:hover:not(:disabled) { background: var(--color-hover); color: var(--color-text); }
.mode-switch { display: grid; grid-template-columns: 1fr 1fr; padding: 4px; border-radius: var(--radius-md); background: var(--color-hover); }
.mode-switch button { height: 38px; border: 0; border-radius: 9px; background: transparent; color: var(--color-text-muted); cursor: pointer; font-weight: 700; }
.mode-switch button.active { color: var(--color-text); background: var(--color-surface); box-shadow: var(--shadow-sm); }
.steps { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; font-size: 12px; color: var(--color-text-muted); }
.steps span { padding-bottom: 8px; border-bottom: 2px solid var(--color-border); text-align: center; }
.steps span.active { color: var(--color-text); border-color: var(--color-text); font-weight: 700; }
.auth__form { display: grid; gap: 15px; }
.field { display: grid; gap: 7px; font-size: 13px; font-weight: 700; }
.field input { width: 100%; box-sizing: border-box; border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 13px 14px; background: var(--color-surface); color: var(--color-text); outline: none; font: inherit; font-weight: 400; }
.field input:focus { border-color: var(--color-text-muted); box-shadow: 0 0 0 3px var(--color-hover); }
.check { display: flex; align-items: flex-start; gap: 9px; color: var(--color-text-muted); font-size: 13px; line-height: 1.5; cursor: pointer; }
.check input { margin-top: 3px; accent-color: var(--color-text); }
.hint { margin: -3px 0 0; color: var(--color-text-muted); font-size: 12px; line-height: 1.5; }
.verified-email { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; border-radius: var(--radius-md); background: var(--color-hover); }
.verified-email div { min-width: 0; display: grid; gap: 3px; font-size: 12px; color: var(--color-text-muted); }
.verified-email strong { overflow: hidden; text-overflow: ellipsis; color: var(--color-text); font-size: 13px; }
.verified-email button, .resend { border: 0; padding: 0; background: transparent; color: var(--color-info); cursor: pointer; font-size: 12px; }
.verified-email--success { border: 1px solid color-mix(in srgb, var(--color-success) 35%, var(--color-border)); background: color-mix(in srgb, var(--color-success) 9%, var(--color-surface)); }
.resend { justify-self: start; }
.resend:disabled { color: var(--color-text-muted); cursor: default; }
.error { padding: 10px 12px; border-radius: var(--radius-sm); background: color-mix(in srgb, var(--color-danger) 10%, transparent); color: var(--color-danger); font-size: 13px; line-height: 1.45; }
.submit { width: 100%; min-height: 46px; margin-top: 2px; }
@media (max-width: 520px) {
  .auth__header { align-items: flex-start; }
  .auth__logo { width: 48px; height: 48px; }
  .steps { font-size: 11px; }
}
</style>
