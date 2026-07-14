<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import UserProfileModal from '@/components/auth/UserProfileModal.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const showProfileModal = ref(false)

function handleUserClick() {
  if (!authStore.isAuthed) {
    authStore.openAuthModal()
  } else {
    showProfileModal.value = true
  }
}

function handleLogout() {
  authStore.logout(router)
}
</script>

<template>
  <div class="footer">
    <div class="user" @click="handleUserClick">
      <div class="user__avatar">
        <AppIcon name="user" />
      </div>
      <div class="user__info">
        <div class="user__name">{{ authStore.user?.nickname || authStore.user?.username || '未登录' }}</div>
        <div v-if="!authStore.isAuthed" class="user__sub">点击登录或注册</div>
      </div>
    </div>
    
    <button v-if="authStore.isAuthed" class="btn" title="退出登录" @click="handleLogout">
      退出
    </button>

    <!-- 个人资料弹窗 -->
    <UserProfileModal 
      v-if="showProfileModal" 
      :open="showProfileModal" 
      @close="showProfileModal = false" 
    />
  </div>
</template>

<style scoped>
.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  gap: 12px;
}

.user {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  flex: 1;
  min-width: 0;
  padding: 6px;
  border-radius: var(--ui-hover-radius);
}

.user:hover {
  background: var(--ui-hover-bg);
}

.user__avatar {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: var(--color-border);
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.user__info {
  flex: 1;
  min-width: 0;
}

.user__name {
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user__sub {
  font-size: 12px;
  color: var(--color-text-muted);
}

.btn {
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 13px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: var(--ui-hover-radius);
}

.btn:hover {
  background: var(--ui-hover-bg);
  color: var(--color-text);
}
</style>
