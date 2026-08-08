<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Eye, EyeOff } from 'lucide-vue-next'
import { useRouter } from 'vue-router'

import {
  AuthApiError,
  createPasswordResetChallenge,
  createRegistrationChallenge,
  resetPassword,
  verifyPasswordResetEmail,
  verifyRegistrationEmail,
} from '@/api/v2Auth'
import AppButton from '@/components/common/AppButton.vue'
import AppModal from '@/components/common/AppModal.vue'
import {
  mountEmbeddedHumanVerification,
  preloadHumanVerification,
  runHumanVerification,
  type EmbeddedHumanVerification,
} from '@/services/humanVerification'
import { useAuthStore } from '@/stores/auth'
import { validateNewPassword } from '@/utils/passwordPolicy'
import { useThemeStore } from '@/stores/theme'
import logoUrl from '@/assets/icons/ExamInsight-Logo.png'
import logoWhiteUrl from '@/assets/icons/ExamInsight-Logo-White.png'
import { CURRENT_PRIVACY_VERSION, CURRENT_TERMS_VERSION } from '@/content/legal'

type Props = { open: boolean }
type CaptchaState = 'idle' | 'loading' | 'ready' | 'verified' | 'sent' | 'error'
type AuthMode = 'login' | 'register' | 'password-reset'

const props = defineProps<Props>()
const emit = defineEmits<{ close: [] }>()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const router = useRouter()

const mode = ref<AuthMode>('login')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const passwordVisible = ref(false)
const confirmPasswordVisible = ref(false)
const verificationCode = ref('')
const challengeId = ref('')
const agreeToPolicies = ref(false)
const localError = ref<string | null>(null)
const localNotice = ref<string | null>(null)
const captchaBusy = ref(false)
const actionBusy = ref(false)
const resendRemaining = ref(0)
const captchaElement = ref<HTMLElement | null>(null)
const captchaButton = ref<HTMLButtonElement | null>(null)
const captchaToken = ref('')
const captchaState = ref<CaptchaState>('idle')
const captchaError = ref('')

let resendTimer: number | null = null
let captchaInitializeTimer: number | null = null
let embeddedCaptcha: EmbeddedHumanVerification | null = null
let captchaMountSequence = 0

const currentLogo = computed(() => themeStore.isDark ? logoWhiteUrl : logoUrl)
const modalTitle = computed(() => ({
  login: '欢迎回来',
  register: '创建 ExamInsight 账户',
  'password-reset': '重置密码',
})[mode.value])
const modalSubtitle = computed(() => ({
  login: '继续你的学习计划',
  register: '使用邮箱验证，保护你的学习资料',
  'password-reset': '通过注册邮箱重新设置密码',
})[mode.value])
const visibleError = computed(() => localError.value || authStore.errorMessage)
const isBusy = computed(() => captchaBusy.value || actionBusy.value || authStore.isSubmitting)
const usesEmailChallenge = computed(() => mode.value !== 'login')
const hasValidChallengeEmail = computed(() => usesEmailChallenge.value && validEmail(normalizedEmail()))
const canRequestEmailCode = computed(() => (
  hasValidChallengeEmail.value
  && captchaState.value === 'verified'
  && Boolean(captchaToken.value)
  && resendRemaining.value === 0
  && !isBusy.value
))

function normalizedEmail() {
  return email.value.trim().toLowerCase()
}

function validEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) && value.length <= 254
}

function passwordValidation(): string | null {
  return validateNewPassword(password.value, confirmPassword.value)
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
    if (resendRemaining.value !== 0) return
    stopResendTimer()
    if (props.open && usesEmailChallenge.value && challengeId.value) {
      void initializeEmbeddedCaptcha()
    }
  }, 1_000)
}

function clearEmailVerification() {
  challengeId.value = ''
  verificationCode.value = ''
  stopResendTimer()
}

function clearChallengeState() {
  clearEmailVerification()
  confirmPassword.value = ''
  confirmPasswordVisible.value = false
  agreeToPolicies.value = false
  destroyEmbeddedCaptcha()
}

function resetAuthModalState() {
  mode.value = 'login'
  email.value = ''
  password.value = ''
  passwordVisible.value = false
  clearChallengeState()
  localError.value = null
  localNotice.value = null
  authStore.errorMessage = null
}

function switchMode(next: AuthMode, force = false) {
  if (mode.value === next || (isBusy.value && !force)) return
  mode.value = next
  localError.value = null
  localNotice.value = null
  authStore.errorMessage = null
  password.value = ''
  confirmPassword.value = ''
  passwordVisible.value = false
  confirmPasswordVisible.value = false
  clearChallengeState()
  if (next === 'login') preloadHumanVerification()
}

function clearCaptchaInitializeTimer() {
  if (captchaInitializeTimer !== null) window.clearTimeout(captchaInitializeTimer)
  captchaInitializeTimer = null
}

function destroyEmbeddedCaptcha() {
  clearCaptchaInitializeTimer()
  captchaMountSequence += 1
  embeddedCaptcha?.destroy()
  embeddedCaptcha = null
  captchaToken.value = ''
  captchaState.value = 'idle'
  captchaError.value = ''
}

function scheduleEmbeddedCaptchaInitialization() {
  clearCaptchaInitializeTimer()
  if (!props.open || !usesEmailChallenge.value || !hasValidChallengeEmail.value) return
  captchaInitializeTimer = window.setTimeout(() => {
    captchaInitializeTimer = null
    void initializeEmbeddedCaptcha()
  }, 300)
}

async function initializeEmbeddedCaptcha() {
  if (!props.open || !usesEmailChallenge.value || !hasValidChallengeEmail.value) {
    destroyEmbeddedCaptcha()
    return
  }

  const mountSequence = ++captchaMountSequence
  embeddedCaptcha?.destroy()
  embeddedCaptcha = null
  captchaToken.value = ''
  captchaError.value = ''
  captchaState.value = 'loading'
  await nextTick()

  const element = captchaElement.value
  const button = captchaButton.value
  if (!element || !button || !props.open || !usesEmailChallenge.value) return

  try {
    const mounted = await mountEmbeddedHumanVerification({
      element,
      button,
      width: element.clientWidth || 440,
      onSuccess(token) {
        captchaToken.value = token
        captchaState.value = 'verified'
        localError.value = null
      },
      onFailure() {
        captchaToken.value = ''
        captchaState.value = 'ready'
      },
    })
    if (mountSequence !== captchaMountSequence) {
      mounted.destroy()
      return
    }
    embeddedCaptcha = mounted
    captchaState.value = 'ready'
  } catch (error) {
    if (mountSequence !== captchaMountSequence) return
    captchaState.value = 'error'
    captchaError.value = error instanceof Error ? error.message : '人机验证加载失败，请重试。'
  }
}

function setError(error: unknown, fallback: string) {
  localError.value = error instanceof Error ? error.message : fallback
}

async function requestEmailCode() {
  localError.value = null
  localNotice.value = null
  if (!validEmail(normalizedEmail())) {
    localError.value = '请输入有效的邮箱地址'
    return
  }
  if (!captchaToken.value || captchaState.value !== 'verified') {
    localError.value = '请先完成滑块验证'
    return
  }
  if (resendRemaining.value > 0) return

  captchaBusy.value = true
  try {
    const createChallenge = mode.value === 'password-reset'
      ? createPasswordResetChallenge
      : createRegistrationChallenge
    const challenge = await createChallenge({
      email: normalizedEmail(),
      humanVerificationToken: captchaToken.value,
    })
    challengeId.value = challenge.challengeId
    verificationCode.value = ''
    captchaToken.value = ''
    captchaState.value = 'sent'
    startResendTimer(challenge.resendAfterSeconds)
    if (mode.value === 'password-reset') {
      localNotice.value = '如果该邮箱已注册，密码重置验证码将发送到该邮箱。'
    }
  } catch (error) {
    setError(error, '验证码发送失败，请稍后重试。')
    void initializeEmbeddedCaptcha()
  } finally {
    captchaBusy.value = false
  }
}

async function finishRegistration() {
  localError.value = null
  localNotice.value = null
  if (!validEmail(normalizedEmail())) {
    localError.value = '请输入有效的邮箱地址'
    return
  }
  if (!challengeId.value) {
    localError.value = '请先完成滑块验证并获取邮箱验证码'
    return
  }
  if (!/^\d{6}$/.test(verificationCode.value)) {
    localError.value = '请输入邮件中的 6 位验证码'
    return
  }
  const passwordError = passwordValidation()
  if (passwordError) {
    localError.value = passwordError
    return
  }
  if (!agreeToPolicies.value) {
    localError.value = '请先同意用户协议与隐私政策'
    return
  }

  actionBusy.value = true
  try {
    const proof = await verifyRegistrationEmail(challengeId.value, verificationCode.value)
    await authStore.register({
      email: normalizedEmail(),
      password: password.value,
      registrationProof: proof.registrationProof,
      termsVersion: CURRENT_TERMS_VERSION,
      privacyVersion: CURRENT_PRIVACY_VERSION,
      legalDocumentsAccepted: true,
    }, router)
    emit('close')
  } catch (error) {
    if (error instanceof AuthApiError && [
      'VERIFICATION_LOCKED',
      'VERIFICATION_EXPIRED',
      'CHALLENGE_NOT_PENDING',
      'INVALID_REGISTRATION_PROOF',
    ].includes(error.code)) {
      clearEmailVerification()
      void initializeEmbeddedCaptcha()
    }
    setError(error, '注册失败，请稍后重试。')
  } finally {
    actionBusy.value = false
  }
}

async function finishPasswordReset() {
  localError.value = null
  localNotice.value = null
  if (!validEmail(normalizedEmail())) {
    localError.value = '请输入有效的邮箱地址'
    return
  }
  if (!challengeId.value) {
    localError.value = '请先完成滑块验证并获取邮箱验证码'
    return
  }
  if (!/^\d{6}$/.test(verificationCode.value)) {
    localError.value = '请输入邮件中的 6 位验证码'
    return
  }
  const passwordError = passwordValidation()
  if (passwordError) {
    localError.value = passwordError
    return
  }

  actionBusy.value = true
  try {
    const proof = await verifyPasswordResetEmail(challengeId.value, verificationCode.value)
    const accountEmail = normalizedEmail()
    const updatedPassword = password.value
    await resetPassword({
      email: accountEmail,
      newPassword: updatedPassword,
      passwordResetToken: proof.passwordResetToken,
    })
    switchMode('login', true)
    email.value = accountEmail
    await nextTick()
    password.value = updatedPassword
    localNotice.value = '密码已重置，请使用新密码登录。'
  } catch (error) {
    if (error instanceof AuthApiError && [
      'VERIFICATION_LOCKED',
      'VERIFICATION_EXPIRED',
      'CHALLENGE_NOT_PENDING',
      'PASSWORD_RESET_EXPIRED',
      'INVALID_PASSWORD_RESET_TOKEN',
    ].includes(error.code)) {
      clearEmailVerification()
      void initializeEmbeddedCaptcha()
    }
    setError(error, '密码重置失败，请稍后重试。')
  } finally {
    actionBusy.value = false
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
  localNotice.value = null
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
  if (mode.value === 'register') return finishRegistration()
  return finishPasswordReset()
}

function close() {
  if (!isBusy.value) emit('close')
}

watch(() => props.open, open => {
  if (!open) {
    resetAuthModalState()
    return
  }
  resetAuthModalState()
  preloadHumanVerification()
})

watch([mode, () => props.open], ([nextMode, open]) => {
  if (open && nextMode !== 'login') scheduleEmbeddedCaptchaInitialization()
  else destroyEmbeddedCaptcha()
})

watch(email, () => {
  if (!usesEmailChallenge.value || !props.open) return
  if (captchaToken.value || challengeId.value) clearEmailVerification()
  destroyEmbeddedCaptcha()
  scheduleEmbeddedCaptchaInitialization()
})

onBeforeUnmount(() => {
  stopResendTimer()
  destroyEmbeddedCaptcha()
})
</script>

<template>
  <AppModal :open="open" width="min(520px, 100%)" :close-on-backdrop="false" @close="close">
    <section class="auth" aria-labelledby="auth-title">
      <header class="auth__header">
        <img :src="currentLogo" alt="ExamInsight" class="auth__logo" />
        <div>
          <h1 id="auth-title">{{ modalTitle }}</h1>
          <p>{{ modalSubtitle }}</p>
        </div>
      </header>

      <form class="auth__form" autocomplete="on" @submit.prevent="submit">
        <template v-if="mode === 'login'">
          <label class="field">
            <span>邮箱</span>
            <input
              id="login-email"
              v-model="email"
              name="username"
              type="email"
              autocomplete="username"
              placeholder="name@example.com"
            />
          </label>
          <label class="field">
            <span>密码</span>
            <span class="password-control">
              <input
                v-model="password"
                id="login-password"
                name="password"
                :type="passwordVisible ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
              />
              <button
                class="password-toggle"
                type="button"
                :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                :aria-pressed="passwordVisible"
                @click="passwordVisible = !passwordVisible"
              >
                <Eye v-if="passwordVisible" :size="18" aria-hidden="true" />
                <EyeOff v-else :size="18" aria-hidden="true" />
              </button>
            </span>
          </label>
          <div class="login-actions">
            <button class="text-action" type="button" :disabled="isBusy" @click="switchMode('password-reset')">
              忘记密码？
            </button>
            <div class="alternate">
              <span>还没有账号？</span>
              <button type="button" :disabled="isBusy" @click="switchMode('register')">去注册</button>
            </div>
          </div>
        </template>

        <template v-else>
          <label class="field">
            <span>邮箱</span>
            <input
              :id="mode === 'register' ? 'register-email' : 'password-reset-email'"
              v-model="email"
              name="username"
              type="email"
              autocomplete="username"
              :placeholder="mode === 'register' ? '用于登录和接收验证码' : '请输入注册邮箱'"
            />
          </label>

          <div v-if="hasValidChallengeEmail" class="captcha" :class="`captcha--${captchaState}`">
            <div ref="captchaElement" class="captcha__element"></div>
            <button ref="captchaButton" class="captcha__trigger" type="button" tabindex="-1" aria-hidden="true"></button>
            <div v-if="captchaState === 'loading'" class="captcha__loading" aria-live="polite">
              <span class="captcha__loading-handle">››</span>
              <span>人机验证加载中</span>
            </div>
            <div v-else-if="captchaState === 'error'" class="captcha__fallback" role="alert">
              <span>{{ captchaError }}</span>
              <button type="button" @click="initializeEmbeddedCaptcha">重新加载</button>
            </div>
            <div v-else-if="captchaState === 'verified'" class="captcha__verified" aria-live="polite">
              <span aria-hidden="true">✓</span>
              <span>验证通过</span>
            </div>
            <div v-else-if="captchaState === 'sent'" class="captcha__verified" aria-live="polite">
              <span aria-hidden="true">✓</span>
              <span>{{ mode === 'password-reset' ? '验证码请求已处理' : '验证码已发送' }}</span>
            </div>
          </div>

          <div class="code-row">
            <input
              v-model="verificationCode"
              :id="mode === 'register' ? 'register-verification-code' : 'password-reset-verification-code'"
              name="one-time-code"
              aria-label="6 位邮箱验证码"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              placeholder="6 位邮箱验证码"
            />
            <button type="button" :disabled="!canRequestEmailCode" @click="requestEmailCode">
              {{ resendRemaining > 0 ? `${resendRemaining} 秒` : challengeId ? '重新获取' : '获取验证码' }}
            </button>
          </div>

          <label class="field">
            <span>密码</span>
            <span class="password-control">
              <input
                v-model="password"
                :id="mode === 'register' ? 'register-password' : 'password-reset-password'"
                name="new-password"
                :type="passwordVisible ? 'text' : 'password'"
                autocomplete="new-password"
                minlength="8"
                maxlength="16"
                placeholder="8–16 位，至少包含字母和数字"
              />
              <button
                class="password-toggle"
                type="button"
                :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                :aria-pressed="passwordVisible"
                @click="passwordVisible = !passwordVisible"
              >
                <Eye v-if="passwordVisible" :size="18" aria-hidden="true" />
                <EyeOff v-else :size="18" aria-hidden="true" />
              </button>
            </span>
          </label>
          <label class="field">
            <span>确认密码</span>
            <span class="password-control">
              <input
                v-model="confirmPassword"
                :id="mode === 'register' ? 'register-password-confirmation' : 'password-reset-password-confirmation'"
                name="new-password-confirmation"
                :type="confirmPasswordVisible ? 'text' : 'password'"
                autocomplete="new-password"
                minlength="8"
                maxlength="16"
                placeholder="再次输入密码"
              />
              <button
                class="password-toggle"
                type="button"
                :aria-label="confirmPasswordVisible ? '隐藏确认密码' : '显示确认密码'"
                :aria-pressed="confirmPasswordVisible"
                @click="confirmPasswordVisible = !confirmPasswordVisible"
              >
                <Eye v-if="confirmPasswordVisible" :size="18" aria-hidden="true" />
                <EyeOff v-else :size="18" aria-hidden="true" />
              </button>
            </span>
          </label>

          <div v-if="mode === 'register'" class="policy-row">
            <label class="check">
              <input v-model="agreeToPolicies" type="checkbox" />
              <span>
                我已阅读并同意
                <RouterLink class="legal-link" to="/terms" target="_blank" rel="noopener" @click.stop>用户协议</RouterLink>
                与
                <RouterLink class="legal-link" to="/privacy" target="_blank" rel="noopener" @click.stop>隐私政策</RouterLink>
              </span>
            </label>
            <div class="alternate">
              <span>已有账号？</span>
              <button type="button" :disabled="isBusy" @click="switchMode('login')">去登录</button>
            </div>
          </div>
          <div v-else class="alternate alternate--end">
            <span>想起密码了？</span>
            <button type="button" :disabled="isBusy" @click="switchMode('login')">去登录</button>
          </div>
        </template>

        <div v-if="localNotice" class="notice" role="status">{{ localNotice }}</div>
        <div v-if="visibleError" class="error" role="alert">{{ visibleError }}</div>

        <AppButton class="submit" type="submit" variant="primary" :loading="isBusy">
          {{ mode === 'login' ? '登录' : mode === 'register' ? '注册' : '重置密码' }}
        </AppButton>
      </form>

      <button class="close" type="button" :disabled="isBusy" aria-label="关闭" @click="close">×</button>
    </section>
  </AppModal>
</template>

<style scoped>
.auth { --auth-control-height: 48px; --auth-control-font-size: 14px; --auth-control-padding-x: 14px; position: relative; display: grid; gap: 20px; color: var(--color-text); }
.auth__header { display: flex; align-items: center; gap: 14px; padding-right: 28px; }
.auth__logo { width: 76px; height: 76px; object-fit: contain; flex: 0 0 auto; }
.auth__header h1 { margin: 0; font-size: 22px; line-height: 1.25; }
.auth__header p { margin: 6px 0 0; color: var(--color-text-muted); font-size: 13px; }
.close { position: absolute; top: -8px; right: -4px; width: 32px; height: 32px; border: 0; border-radius: 50%; background: transparent; color: var(--color-text-muted); font-size: 24px; cursor: pointer; }
.close:hover:not(:disabled) { background: var(--color-hover); color: var(--color-text); }
.auth__form { display: grid; gap: 14px; }
.field { display: grid; gap: 7px; font-size: 13px; font-weight: 700; }
.field input, .code-row input { width: 100%; height: var(--auth-control-height); box-sizing: border-box; border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 0 var(--auth-control-padding-x); background: var(--color-surface); color: var(--color-text); outline: none; font-family: inherit; font-size: var(--auth-control-font-size); font-weight: 400; line-height: 1; }
.field input:focus, .code-row input:focus { border-color: var(--color-text-muted); box-shadow: 0 0 0 3px var(--color-hover); }
.password-control { position: relative; display: block; }
.password-control input { padding-right: 44px; }
.password-toggle { position: absolute; top: 50%; right: 8px; display: grid; width: 32px; height: 32px; place-items: center; transform: translateY(-50%); border: 0; border-radius: var(--radius-sm); padding: 0; background: transparent; color: var(--color-text-muted); cursor: pointer; }
.password-toggle:hover { background: var(--color-hover); color: var(--color-text); }
.password-toggle:focus-visible { outline: 2px solid var(--color-text-muted); outline-offset: 1px; }
.captcha { position: relative; width: 100%; height: var(--auth-control-height); min-height: var(--auth-control-height); }
.captcha__element { width: 100%; height: var(--auth-control-height); min-height: var(--auth-control-height); }
.captcha__trigger { position: absolute; width: 1px; height: 1px; overflow: hidden; opacity: 0; pointer-events: none; }
.captcha__loading, .captcha__fallback, .captcha__verified { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; gap: 10px; height: var(--auth-control-height); min-height: var(--auth-control-height); box-sizing: border-box; border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-hover); color: var(--color-text-muted); font-size: var(--auth-control-font-size); }
.captcha__loading-handle { position: absolute; left: 0; display: grid; place-items: center; width: 58px; height: 100%; border-radius: var(--radius-sm); background: var(--color-surface); box-shadow: var(--shadow-sm); font-size: 18px; }
.captcha__fallback { justify-content: space-between; padding: 0 12px; color: var(--color-danger); }
.captcha__fallback button { border: 0; padding: 0; background: transparent; color: var(--color-text); cursor: pointer; font: inherit; text-decoration: underline; }
.captcha__verified { border-color: color-mix(in srgb, var(--color-success) 40%, var(--color-border)); background: color-mix(in srgb, var(--color-success) 10%, var(--color-surface)); color: var(--color-success); font-weight: 700; }
.code-row { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: 10px; width: 100%; }
.code-row button { height: var(--auth-control-height); box-sizing: border-box; border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 0 var(--auth-control-padding-x); background: var(--color-surface); color: var(--color-text); cursor: pointer; font-family: inherit; font-size: var(--auth-control-font-size); line-height: 1; }
.code-row button:hover:not(:disabled) { background: var(--color-hover); }
.code-row button:disabled { color: var(--color-text-muted); cursor: not-allowed; opacity: .65; }
.policy-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.check { display: flex; min-width: 0; align-items: flex-start; gap: 8px; color: var(--color-text-muted); font-size: 12px; line-height: 1.5; cursor: pointer; }
.check input { flex: 0 0 auto; margin-top: 2px; accent-color: var(--color-text); }
.legal-link { color: var(--color-text); font-weight: 700; text-decoration: underline; text-underline-offset: 2px; }
.login-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.text-action { border: 0; padding: 0; background: transparent; color: var(--color-text); cursor: pointer; font: inherit; font-size: 12px; font-weight: 700; }
.text-action:hover:not(:disabled) { text-decoration: underline; }
.text-action:disabled { color: var(--color-text-muted); cursor: default; }
.alternate { display: flex; flex: 0 0 auto; justify-content: flex-end; gap: 4px; color: var(--color-text-muted); font-size: 12px; }
.alternate--end { justify-self: end; }
.alternate button { border: 0; padding: 0; background: transparent; color: var(--color-text); cursor: pointer; font: inherit; font-weight: 700; }
.alternate button:hover:not(:disabled) { text-decoration: underline; }
.alternate button:disabled { color: var(--color-text-muted); cursor: default; }
.notice { padding: 10px 12px; border-radius: var(--radius-sm); background: color-mix(in srgb, var(--color-success) 10%, transparent); color: var(--color-success); font-size: 13px; line-height: 1.45; }
.error { padding: 10px 12px; border-radius: var(--radius-sm); background: color-mix(in srgb, var(--color-danger) 10%, transparent); color: var(--color-danger); font-size: 13px; line-height: 1.45; }
.submit { width: 100%; min-height: var(--auth-control-height); font-size: var(--auth-control-font-size); }
@media (max-width: 520px) {
  .auth__header { align-items: flex-start; }
  .auth__logo { width: 48px; height: 48px; }
  .policy-row { flex-wrap: wrap; }
  .policy-row .alternate { margin-left: auto; }
}
</style>
