import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { useRouter } from 'vue-router'

import { TOKEN_KEY, USER_KEY, clearStoredAuth } from '@/api/request'
import * as authApi from '@/api/auth'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore } from '@/stores/message'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useExamAnalysisStore } from '@/stores/examAnalysis'
import { useMindMapStore } from '@/stores/mindmap'
import { useLearningStore } from '@/stores/learning'
import { useLibraryResourceStore } from '@/stores/libraryResource'

export type User = authApi.ApiUser

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<User | null>(null)
  const isReady = ref(false)
  const isSubmitting = ref(false)
  const errorMessage = ref<string | null>(null)
  const authModalOpen = ref(false)
  const pendingRoute = ref<string | null>(null)

  const isAuthed = computed(() => Boolean(token.value))

  function init() {
    if (isReady.value) return

    // 先从sessionStorage读取，如果没有再从localStorage读取
    token.value = sessionStorage.getItem(TOKEN_KEY) || localStorage.getItem(TOKEN_KEY)
    const rawUser = sessionStorage.getItem(USER_KEY) || localStorage.getItem(USER_KEY)
    user.value = rawUser ? (JSON.parse(rawUser) as User) : null

    if (!token.value) {
      const guestKeys = Object.keys(sessionStorage).filter(key => key.includes('.guest'))
      guestKeys.forEach(key => sessionStorage.removeItem(key))
    }

    window.addEventListener('auth:logout', () => {
      token.value = null
      user.value = null
    })

    isReady.value = true
  }

  function persist(next: authApi.AuthResult, remember: boolean) {
    token.value = next.token
    user.value = next.user

    if (remember) {
      localStorage.setItem(TOKEN_KEY, next.token)
      localStorage.setItem(USER_KEY, JSON.stringify(next.user))
      sessionStorage.removeItem(TOKEN_KEY)
      sessionStorage.removeItem(USER_KEY)
    } else {
      sessionStorage.setItem(TOKEN_KEY, next.token)
      sessionStorage.setItem(USER_KEY, JSON.stringify(next.user))
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }

  function updateUser(newUser: User) {
    user.value = newUser
    const isRemembered = localStorage.getItem(TOKEN_KEY) !== null
    if (isRemembered) {
      localStorage.setItem(USER_KEY, JSON.stringify(newUser))
    } else {
      sessionStorage.setItem(USER_KEY, JSON.stringify(newUser))
    }
  }

  async function updateProfile(payload: { nickname: string }) {
    if (!user.value) return
    errorMessage.value = null
    isSubmitting.value = true
    try {
      const newUser = await authApi.updateProfile(payload)
      updateUser(newUser)
      return newUser
    } catch (err) {
      errorMessage.value = err instanceof Error ? err.message : '更新资料失败'
      throw err
    } finally {
      isSubmitting.value = false
    }
  }

  function openAuthModal(redirectTo?: string) {
    if (redirectTo) pendingRoute.value = redirectTo
    authModalOpen.value = true
  }

  function closeAuthModal() {
    authModalOpen.value = false
    pendingRoute.value = null
  }

  function clearBusinessStores() {
    useConversationStore().clearAll()
    useMessageStore().clearMemoryState()
    useKnowledgeBaseStore().clearAll()
    useExamAnalysisStore().clearAll()
    useMindMapStore().clearAll()
    useLearningStore().clearAll()
    useLibraryResourceStore().clearAll()
  }

  async function finishAuthentication(router?: ReturnType<typeof useRouter>) {
    const redirectTo = pendingRoute.value
    authModalOpen.value = false
    pendingRoute.value = null
    if (router && redirectTo) await router.replace(redirectTo)
  }

  async function login(payload: { username: string; password: string; remember?: boolean }, router?: ReturnType<typeof useRouter>) {
    errorMessage.value = null
    isSubmitting.value = true
    try {
      const res = await authApi.login(payload)
      persist(res, payload.remember ?? true)

      // 清除当前store的旧数据并重新初始化
      clearBusinessStores()

      // 重新加载新用户的数据是在 App.vue 中 watch 触发的。
      // 如果登录由受保护路由触发，则回到用户原本要去的页面。
      await finishAuthentication(router)
    } catch (err) {
      errorMessage.value = err instanceof Error ? err.message : '登录失败'
      throw err
    } finally {
      isSubmitting.value = false
    }
  }

  async function register(payload: {
    username: string
    password: string
    nickname?: string
    remember?: boolean
  }, router?: ReturnType<typeof useRouter>) {
    errorMessage.value = null
    isSubmitting.value = true
    try {
      const res = await authApi.register(payload)
      persist(res, payload.remember ?? true)

      // 清除当前store的旧数据并重新初始化
      clearBusinessStores()

      // 注册成功也遵循相同的原意图回跳规则。
      await finishAuthentication(router)
    } catch (err) {
      errorMessage.value = err instanceof Error ? err.message : '注册失败'
      throw err
    } finally {
      isSubmitting.value = false
    }
  }

  function logout(router?: ReturnType<typeof useRouter>) {
    token.value = null
    user.value = null
    clearStoredAuth()

    // 清除对话和消息 (在清除auth之后调用，确保使用正确的guest storage key)
    pendingRoute.value = null
    authModalOpen.value = false
    clearBusinessStores()

    window.dispatchEvent(new CustomEvent('auth:logout'))

    if (router) {
      router.push('/chat')
    }
  }

  return {
    token,
    user,
    isReady,
    isAuthed,
    isSubmitting,
    errorMessage,
    authModalOpen,
    pendingRoute,
    init,
    login,
    register,
    logout,
    updateProfile,
    openAuthModal,
    closeAuthModal,
  }
})
