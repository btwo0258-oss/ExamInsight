<script setup lang="ts">
import { ref } from 'vue'
import Sidebar from './Sidebar.vue'
import AppIcon from '@/components/admin/AppIcon.vue'
import { useAuthStore } from '@/stores/adminAuth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

function handleLogout() {
  authStore.logout()
  router.push('/admin/login')
}
</script>

<template>
  <div class="admin-layout">
    <Sidebar />
    
    <div class="main-container">
      <header class="admin-header">
        <div class="header-left">
          <!-- Page title or breadcrumbs could go here -->
        </div>
        <div class="header-right">
          <div class="user-info">
            <div class="user-avatar">
              <AppIcon name="shield" :size="18" />
            </div>
            <span class="user-nickname">{{ authStore.user?.nickname || '管理员' }}</span>
          </div>
          <button class="logout-btn" @click="handleLogout" title="退出登录">
            <AppIcon name="logout" :size="18" />
            <span>退出</span>
          </button>
        </div>
      </header>
      
      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background-color: var(--color-bg);
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.admin-header {
  height: var(--header-height);
  background-color: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background-color: var(--color-bg-alt);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border);
}

.user-nickname {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

.logout-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  transition: all 0.2s;
  font-size: 14px;
}

.logout-btn:hover {
  background-color: rgba(0, 0, 0, 0.04);
  color: var(--color-danger);
}

.admin-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background-color: var(--color-bg);
}
</style>
