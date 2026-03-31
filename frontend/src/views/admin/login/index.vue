<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/adminAuth'
import AppIcon from '@/components/admin/AppIcon.vue'
import AppButton from '@/components/admin/AppButton.vue'
import { login } from '@/api/adminAuth'

const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  
  loading.value = true
  error.value = ''
  
  try {
    const res: any = await login({ username: username.value, password: password.value, isAdmin: true })
    if (res && res.token) {
      authStore.setToken(res.token)
      authStore.setUser({
        id: res.user?.id || 1,
        username: res.user?.username || username.value,
        nickname: res.user?.nickname || '管理员'
      })
      router.push('/admin/dashboard')
    } else {
      error.value = '登录失败，请检查用户名和密码'
    }
  } catch (err: any) {
    error.value = err.message || '登录失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <div class="brand">
          <AppIcon name="bar-chart" :size="32" />
          <h1 class="brand-text">Aether Admin</h1>
        </div>
        <p class="subtitle">管理后台登录</p>
      </div>
      
      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-item">
          <label for="username">用户名</label>
          <div class="input-wrap">
            <AppIcon name="user" :size="16" />
            <input 
              id="username"
              v-model="username" 
              type="text" 
              placeholder="admin"
              required
            />
          </div>
        </div>
        
        <div class="form-item">
          <label for="password">密码</label>
          <div class="input-wrap">
            <AppIcon name="lock" :size="16" />
            <input 
              id="password"
              v-model="password" 
              type="password" 
              placeholder="123456"
              required
            />
          </div>
        </div>
        
        <div v-if="error" class="error-message">
          <AppIcon name="close" :size="14" />
          <span>{{ error }}</span>
        </div>
        
        <AppButton 
          type="submit" 
          variant="primary" 
          :loading="loading"
          class="submit-btn"
        >
          登 录
        </AppButton>
        <div style="text-align: center; margin-top: 16px;">
          <a href="/" style="color: var(--color-text-muted); text-decoration: none; font-size: 14px; cursor: pointer;">
            返回用户界面
          </a>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-bg);
}

.login-card {
  width: 400px;
  padding: 40px;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 8px;
}

.brand-text {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -1px;
}

.subtitle {
  font-size: 14px;
  color: var(--color-text-muted);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

.input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background-color: var(--color-bg);
  transition: border-color 0.2s;
}

.input-wrap:focus-within {
  border-color: var(--color-primary);
}

.input-wrap input {
  flex: 1;
  border: none;
  background: none;
  outline: none;
  font-size: 14px;
  color: var(--color-text);
}

.error-message {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-danger);
  font-size: 13px;
  background-color: #fff1f0;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
}

.submit-btn {
  width: 100%;
  height: 44px;
  margin-top: 10px;
}
</style>
