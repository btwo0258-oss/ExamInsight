import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import StudentChatView from '@/views/student/chat/StudentChatView.vue'
import { useAuthStore } from '@/stores/auth'
import { useConversationStore } from '@/stores/conversation'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useLearningStore } from '@/stores/learning'
import { useMessageStore } from '@/stores/message'
import type { LearningProfileData } from '@/types/contracts/learning'

const routeState = vi.hoisted(() => ({
  name: 'learning-setup',
  params: { id: '1' } as Record<string, string>,
  query: { projectId: '2' } as Record<string, string>,
}))
const router = vi.hoisted(() => ({
  push: vi.fn(async () => undefined),
  replace: vi.fn(async () => undefined),
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => router,
}))

const AppInputStub = defineComponent({
  name: 'AppInput',
  emits: ['send', 'stop'],
  methods: {
    setText() {},
  },
  template: `
    <div class="test-input">
      <slot name="context" />
      <button class="test-send" type="button" @click="$emit('send', '我想系统学习 Java 面向对象')">制定方案</button>
    </div>
  `,
})

const profile: LearningProfileData = {
  goal: '系统学习',
  subject: 'Java 面向对象知识库',
  foundation: '基础一般',
  weakPoints: ['继承', '多态'],
  period: '2 周',
  dailyTime: '每天 60 分钟',
  preferences: ['练习驱动'],
  source: 'Java 面向对象知识库',
  extra: '',
}

function setupStores(generateLearningProfile: ReturnType<typeof vi.fn>) {
  const pinia = createPinia()
  setActivePinia(pinia)

  const authStore = useAuthStore()
  authStore.session = {
    userId: 'test-user',
    email: 'test@example.com',
    displayName: '测试用户',
    authLevel: 'PASSWORD',
    idleExpiresAt: new Date(Date.now() + 60_000).toISOString(),
    absoluteExpiresAt: new Date(Date.now() + 120_000).toISOString(),
  }
  authStore.isReady = true

  const conversationStore = useConversationStore()
  conversationStore.list = [{
    id: 1,
    title: '方案制定',
    knowledgeBaseId: 101,
    isPinned: false,
    messageCount: 0,
    createTime: new Date().toISOString(),
    updateTime: new Date().toISOString(),
    projectId: 2,
    projectName: 'Java 学习计划',
    conversationType: 'learning-setup',
  }]

  const knowledgeBaseStore = useKnowledgeBaseStore()
  knowledgeBaseStore.list = [{
    id: 101,
    name: 'Java 面向对象知识库',
    icon: 'folder',
    createTime: new Date().toISOString(),
    updateTime: new Date().toISOString(),
  }]
  knowledgeBaseStore.fetchList = vi.fn(async () => undefined)

  const learningStore = useLearningStore()
  learningStore.plans = [{
    id: 2,
    title: 'Java 学习计划',
    knowledgeBaseId: 101,
    stages: [],
    resources: [],
    exercises: [],
  }] as typeof learningStore.plans
  learningStore.fetchPlans = vi.fn(async () => undefined)
  learningStore.getLearningSetupState = vi.fn(async () => null)
  learningStore.saveLearningSetupState = vi.fn(async (_projectId, state) => state)
  learningStore.resumePlanGeneration = vi.fn(async () => null)
  learningStore.generateLearningProfile = generateLearningProfile

  const messageStore = useMessageStore()
  messageStore.ensureLoaded = vi.fn(async () => true)

  return { pinia, messageStore }
}

function mountPage(pinia: ReturnType<typeof createPinia>) {
  return mount(StudentChatView, {
    global: {
      plugins: [pinia],
      stubs: {
        AppInput: AppInputStub,
        AppIcon: true,
        StudentShell: { template: '<main><slot /></main>' },
        MessageList: { template: '<div class="test-message-list" />' },
        SegmentPanel: true,
        LibraryKnowledgeCreateModal: true,
      },
    },
  })
}

describe('StudentChatView learning profile flow', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    sessionStorage.clear()
    localStorage.clear()
    router.push.mockClear()
    router.replace.mockClear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('uses the shared knowledge-base store when requesting a learning profile', async () => {
    const generateLearningProfile = vi.fn(async () => ({ profile }))
    const { pinia, messageStore } = setupStores(generateLearningProfile)
    const wrapper = mountPage(pinia)
    await flushPromises()

    await wrapper.find('.test-send').trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(900)
    await flushPromises()

    expect(generateLearningProfile).toHaveBeenCalledWith(expect.objectContaining({
      conversationId: 1,
      knowledgeBaseId: 101,
      source: 'Java 面向对象知识库',
      subject: 'Java 面向对象知识库',
    }))
    expect(generateLearningProfile.mock.calls[0]?.[0]).not.toHaveProperty('knowledgeTags')
    expect(messageStore.getMessages(1)).toEqual(expect.arrayContaining([
      expect.objectContaining({
        role: 'assistant',
        kind: 'learning-profile',
        errorMsg: undefined,
        learningData: expect.objectContaining({ loading: false, profile }),
      }),
    ]))

    wrapper.unmount()
  })

  it('retries the failed profile generation in the original assistant message', async () => {
    const generateLearningProfile = vi.fn()
      .mockRejectedValueOnce(new Error('temporary profile error'))
      .mockResolvedValueOnce({ profile })
    const { pinia, messageStore } = setupStores(generateLearningProfile)
    const wrapper = mountPage(pinia)
    await flushPromises()

    await wrapper.find('.test-send').trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(900)
    await flushPromises()

    const failedMessages = messageStore.getMessages(1)
    const failedAssistant = failedMessages.find((message) => message.role === 'assistant')
    expect(failedAssistant).toEqual(expect.objectContaining({
      content: '学习画像生成失败，请重试。',
      errorMsg: 'temporary profile error',
    }))
    const messageCount = failedMessages.length

    await wrapper.find('.chat-error__actions button').trigger('click')
    expect(messageStore.getMessages(1)).toHaveLength(messageCount)
    expect(messageStore.getMessages(1).find((message) => message.id === failedAssistant?.id)).toEqual(expect.objectContaining({
      kind: 'learning-profile',
      content: '',
      errorMsg: undefined,
      learningData: expect.objectContaining({ loading: true }),
    }))

    await vi.advanceTimersByTimeAsync(900)
    await flushPromises()

    expect(generateLearningProfile).toHaveBeenCalledTimes(2)
    expect(messageStore.getMessages(1)).toHaveLength(messageCount)
    expect(messageStore.getMessages(1).find((message) => message.id === failedAssistant?.id)).toEqual(expect.objectContaining({
      kind: 'learning-profile',
      errorMsg: undefined,
      learningData: expect.objectContaining({ loading: false, profile }),
    }))

    wrapper.unmount()
  })
})
