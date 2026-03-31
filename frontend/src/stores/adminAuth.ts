import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface AdminUser {
  id: number
  username: string
  nickname: string
  avatar?: string
}

export const useAuthStore = defineStore('admin-auth', () => {
  const token = ref<string | null>(localStorage.getItem('admin_token'))
  const user = ref<AdminUser | null>(null)

  const isAuthenticated = computed(() => !!token.value)

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('admin_token', newToken)
  }

  function setUser(newUser: AdminUser) {
    user.value = newUser
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem('admin_token')
  }

  return {
    token,
    user,
    isAuthenticated,
    setToken,
    setUser,
    logout
  }
})
