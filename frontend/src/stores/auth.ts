import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { Router } from 'vue-router'

import { clearStoredAuth } from '@/api/request'
import {
  AuthApiError,
  type AuthSession,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
  getSession,
  requestAccountDeletion,
  updateAccountProfile,
} from '@/api/v2Auth'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore } from '@/stores/message'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useExamAnalysisStore } from '@/stores/examAnalysis'
import { useMindMapStore } from '@/stores/mindmap'
import { useLearningStore } from '@/stores/learning'
import { useLibraryResourceStore } from '@/stores/libraryResource'

export type User = {
  id: string
  username: string
  email: string
  nickname: string | null
  avatar: null
}

export const useAuthStore = defineStore('auth', () => {
  const session = ref<AuthSession | null>(null)
  const isReady = ref(false)
  const isSubmitting = ref(false)
  const errorMessage = ref<string | null>(null)
  const authModalOpen = ref(false)
  const pendingRoute = ref<string | null>(null)
  let initialization: Promise<void> | null = null

  const isAuthed = computed(() => session.value !== null)
  const user = computed<User | null>(() => session.value ? {
    id: session.value.userId,
    username: session.value.email,
    email: session.value.email,
    nickname: session.value.displayName,
    avatar: null,
  } : null)

  function clearBusinessStores() {
    useConversationStore().clearAll()
    useMessageStore().clearMemoryState()
    useKnowledgeBaseStore().clearAll()
    useExamAnalysisStore().clearAll()
    useMindMapStore().clearAll()
    useLearningStore().clearAll()
    useLibraryResourceStore().clearAll()
  }

  function expireLocalSession() {
    const wasAuthenticated = session.value !== null
    session.value = null
    clearStoredAuth()
    if (wasAuthenticated) clearBusinessStores()
  }

  async function init(force = false) {
    if (isReady.value && !force) return
    if (initialization) return initialization

    clearStoredAuth()
    initialization = (async () => {
      try {
        session.value = await getSession()
      } catch (error) {
        session.value = null
        if (!(error instanceof AuthApiError && error.status === 401)) {
          errorMessage.value = error instanceof Error ? error.message : '无法确认登录状态'
        }
      } finally {
        isReady.value = true
        initialization = null
      }
    })()
    return initialization
  }

  window.addEventListener('auth:session-expired', expireLocalSession)

  function openAuthModal(redirectTo?: string) {
    if (redirectTo) pendingRoute.value = redirectTo
    errorMessage.value = null
    authModalOpen.value = true
  }

  function closeAuthModal() {
    authModalOpen.value = false
    pendingRoute.value = null
    errorMessage.value = null
  }

  async function finishAuthentication(router?: Router) {
    const redirectTo = pendingRoute.value
    authModalOpen.value = false
    pendingRoute.value = null
    clearBusinessStores()
    if (router && redirectTo) await router.replace(redirectTo)
  }

  async function login(
    payload: { email: string; password: string; humanVerificationToken?: string },
    router?: Router,
  ) {
    errorMessage.value = null
    isSubmitting.value = true
    try {
      session.value = await loginRequest(payload)
      await finishAuthentication(router)
      return session.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '登录失败'
      throw error
    } finally {
      isSubmitting.value = false
    }
  }

  async function register(
    payload: {
      email: string
      password: string
      registrationProof: string
      termsVersion: string
      privacyVersion: string
      legalDocumentsAccepted: boolean
    },
    router?: Router,
  ) {
    errorMessage.value = null
    isSubmitting.value = true
    try {
      session.value = await registerRequest(payload)
      await finishAuthentication(router)
      return session.value
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '注册失败'
      throw error
    } finally {
      isSubmitting.value = false
    }
  }

  async function logout(router?: Router): Promise<boolean> {
    errorMessage.value = null
    isSubmitting.value = true
    try {
      await logoutRequest()
      expireLocalSession()
      pendingRoute.value = null
      authModalOpen.value = false
      if (router) await router.push('/chat')
      return true
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '退出登录失败'
      return false
    } finally {
      isSubmitting.value = false
    }
  }

  async function updateProfile(displayName: string) {
    errorMessage.value = null
    isSubmitting.value = true
    try {
      const account = await updateAccountProfile(displayName)
      if (session.value) session.value = { ...session.value, displayName: account.displayName }
      return account
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '资料保存失败'
      throw error
    } finally {
      isSubmitting.value = false
    }
  }

  async function deleteAccount(currentPassword: string, router?: Router): Promise<boolean> {
    errorMessage.value = null
    isSubmitting.value = true
    try {
      await requestAccountDeletion(currentPassword)
      expireLocalSession()
      pendingRoute.value = null
      authModalOpen.value = false
      if (router) await router.push('/chat')
      return true
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '账号注销失败'
      throw error
    } finally {
      isSubmitting.value = false
    }
  }

  return {
    session,
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
    deleteAccount,
    openAuthModal,
    closeAuthModal,
  }
})
