<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";

import AppModal from "@/components/common/AppModal.vue";
import AppButton from "@/components/common/AppButton.vue";
import { useAuthStore } from "@/stores/auth";
import { forgotPassword } from "@/api/auth";
import logoUrl from "@/assets/icons/Aether Logo SVG.jpg";

type Props = { open: boolean };
const props = defineProps<Props>();
const emit = defineEmits<{ close: [] }>();

const authStore = useAuthStore();
const router = useRouter();

const mode = ref<"login" | "register">("login");
const username = ref("");
const password = ref("");
const confirmPassword = ref("");
const nickname = ref("");
const remember = ref(true);
const agree = ref(false);
const showForgotPassword = ref(false);
const forgotPasswordLoading = ref(false);
const forgotPasswordMessage = ref("");

const title = computed(() => (mode.value === "login" ? "欢迎来到Aether" : "欢迎来到Aether"));
const subTitle = computed(() =>
  mode.value === "login" ? "请登录或注册以继续" : "请填写以下信息完成注册",
);

const actionText = computed(() => (mode.value === "login" ? "登录" : "注册"));

const validationError = computed((): string | null => {
  const u = username.value.trim();
  const p = password.value.trim();
  if (!u) return "请输入账号";
  if (!p) return "请输入密码";
  if (mode.value === "register") {
    if (!nickname.value.trim()) return "请输入昵称";
    if (p.length < 6 || p.length > 20) return "密码长度需为6-20位";
    if (confirmPassword.value.trim() !== p) return "两次密码不一致";
    if (!agree.value) return "请先同意用户协议和隐私政策";
  }
  return null;
});

const canSubmit = computed(() => {
  return validationError.value === null;
});

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    authStore.init();
    authStore.errorMessage = null;
    password.value = "";
    confirmPassword.value = "";
  },
);

function close() {
  emit("close");
}

async function handleForgotPassword() {
  if (!username.value.trim()) {
    forgotPasswordMessage.value = "请输入用户名";
    return;
  }
  forgotPasswordLoading.value = true;
  forgotPasswordMessage.value = "";
  try {
    const result = await forgotPassword(username.value);
    forgotPasswordMessage.value = result.message || "密码重置申请已提交，请等待管理员处理";
    
    // Auto close after 2 seconds
    setTimeout(() => {
      showForgotPassword.value = false;
      forgotPasswordMessage.value = "";
    }, 2000);
  } catch (error: any) {
    forgotPasswordMessage.value = error.response?.data?.message || "提交失败，请稍后重试";
  } finally {
    forgotPasswordLoading.value = false;
  }
}

async function submit() {
  if (!canSubmit.value) return;
  const u = username.value.trim();
  const p = password.value.trim();
  if (mode.value === "login") {
    await authStore.login({ username: u, password: p, remember: remember.value }, router);
    close();
    return;
  }
  await authStore.register({
    username: u,
    password: p,
    nickname: nickname.value.trim(),
    remember: remember.value,
  });
  close();
}

function goToAdminLogin() {
  close();
  router.push("/admin/login");
}
</script>

<template>
  <AppModal :open="open" :close-on-backdrop="true" @close="close">
    <div class="auth">
      <div class="auth__brand">
        <div class="auth__logo"><img :src="logoUrl" alt="Logo" class="logo-img" /></div>
      </div>

      <div class="auth__title">{{ title }}</div>
      <div class="auth__sub">{{ subTitle }}</div>

      <form class="auth__form" @submit.prevent="submit">
        <label class="field">
          <div class="field__label">账号</div>
          <input
            v-model="username"
            class="field__input"
            placeholder="请输入账号"
            autocomplete="username"
          />
        </label>

        <label v-if="mode === 'register'" class="field">
          <div class="field__label">用户昵称</div>
          <input
            v-model="nickname"
            class="field__input"
            placeholder="请输入昵称"
            autocomplete="nickname"
          />
        </label>

        <div v-if="mode === 'register'" class="row">
          <label class="field">
            <div class="field__label">密码</div>
            <input
              v-model="password"
              class="field__input"
              placeholder="6-20位密码"
              type="password"
              autocomplete="new-password"
            />
          </label>
          <label class="field">
            <div class="field__label">确认密码</div>
            <input
              v-model="confirmPassword"
              class="field__input"
              placeholder="再次输入"
              type="password"
              autocomplete="new-password"
            />
          </label>
        </div>

        <label v-else class="field">
          <div class="field__label">密码</div>
          <input
            v-model="password"
            class="field__input"
            placeholder="请输入密码"
            type="password"
            autocomplete="current-password"
          />
        </label>

        <div v-if="mode === 'login'" class="meta">
          <label class="check">
            <input v-model="remember" class="check__box" type="checkbox" />
            <span>记住登录状态</span>
          </label>
          <button class="link" type="button" @click="showForgotPassword = true">忘记密码</button>
        </div>

        <div v-else class="meta">
          <label class="check">
            <input v-model="agree" class="check__box" type="checkbox" />
            <span>我已阅读并同意 用户协议 和 隐私政策</span>
          </label>
        </div>

        <div v-if="validationError || authStore.errorMessage" class="error">
          {{ validationError || authStore.errorMessage }}
        </div>

        <AppButton class="primary" :disabled="authStore.isSubmitting || !canSubmit" type="submit">
          {{ authStore.isSubmitting ? "处理中…" : actionText }}
        </AppButton>

        <div class="switch">
          <template v-if="mode === 'login'">
            还没有账号？
            <button class="link link--strong" type="button" @click="mode = 'register'">
              点击注册
            </button>
            <div style="margin-top: 12px">
              <button
                class="link link--strong"
                type="button"
                @click="goToAdminLogin"
                style="color: var(--color-primary)"
              >
                管理员入口
              </button>
            </div>
          </template>
          <template v-else>
            已有账号？
            <button class="link link--strong" type="button" @click="mode = 'login'">
              点击登录
            </button>
          </template>
        </div>
      </form>
    </div>
  </AppModal>

  <AppModal
    v-if="showForgotPassword"
    :open="showForgotPassword"
    @close="showForgotPassword = false"
  >
    <div class="forgot-password">
      <div class="forgot-password__header">
        <h2>忘记密码</h2>
        <p>请输入您的用户名，系统将提交密码重置申请给管理员</p>
      </div>
      <div class="forgot-password__content">
        <label class="field">
          <div class="field__label">用户名</div>
          <input v-model="username" class="field__input" type="text" placeholder="请输入用户名" />
        </label>
        <div
          v-if="forgotPasswordMessage"
          class="forgot-password__message"
          :class="{ success: forgotPasswordMessage.includes('成功') || forgotPasswordMessage.includes('已提交') }"
        >
          {{ forgotPasswordMessage }}
        </div>
      </div>
      <div class="forgot-password__footer">
        <AppButton type="secondary" @click="showForgotPassword = false">取消</AppButton>
        <AppButton type="primary" :loading="forgotPasswordLoading" @click="handleForgotPassword"
          >提交申请</AppButton
        >
      </div>
    </div>
  </AppModal>
</template>

<style scoped>
.auth {
  max-width: 620px;
  margin: 0 auto;
}

.auth__brand {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 18px;
}

.auth__logo {
  display: flex;
  justify-content: center;
  align-items: center;
}

.logo-img {
  width: 80px;
  height: auto;
  object-fit: contain;
}

.auth__brand-name {
  font-size: 14px;
  color: var(--color-text-muted);
}

.auth__title {
  text-align: center;
  font-size: 28px;
  font-weight: 900;
  letter-spacing: 0.2px;
}

.auth__sub {
  text-align: center;
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 26px;
}

.auth__form {
  display: grid;
  gap: 16px;
}

.field {
  display: grid;
  gap: 8px;
}

.field__label {
  font-size: 14px;
  font-weight: 700;
}

.field__input {
  width: 100%;
  border: 0;
  border-radius: 14px;
  padding: 16px 16px;
  background: rgba(0, 0, 0, 0.04);
  outline: none;
}

:root[data-theme="dark"] .field__input {
  background: rgba(255, 255, 255, 0.06);
}

.row {
  display: grid;
  gap: 14px;
  grid-template-columns: 1fr 1fr;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.check__box {
  width: 18px;
  height: 18px;
  accent-color: var(--color-text);
}

.link {
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
  color: var(--color-text-muted);
}

.link--strong {
  color: var(--color-text);
  font-weight: 700;
}

.forgot-password {
  max-width: 400px;
  margin: 0 auto;
}

.forgot-password__header {
  text-align: center;
  margin-bottom: 24px;
}

.forgot-password__header h2 {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 8px;
}

.forgot-password__header p {
  color: #666;
  font-size: 14px;
}

.forgot-password__content {
  margin-bottom: 24px;
}

.forgot-password__message {
  margin-top: 12px;
  padding: 10px;
  border-radius: 6px;
  background: #fee;
  color: #c33;
  font-size: 14px;
  text-align: center;
}

.forgot-password__message.success {
  background: #efe;
  color: #3c3;
}

:root[data-theme="dark"] .forgot-password__message {
  background: rgba(255, 0, 0, 0.1);
  color: #f88;
}

:root[data-theme="dark"] .forgot-password__message.success {
  background: rgba(0, 255, 0, 0.1);
  color: #8f8;
}

.forgot-password__footer {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.error {
  font-size: 12px;
  color: #d73a49;
}

.primary {
  width: 100%;
  height: 54px;
  border-radius: 14px;
  font-size: 18px;
}

.switch {
  text-align: center;
  font-size: 14px;
  color: var(--color-text-muted);
}
</style>
