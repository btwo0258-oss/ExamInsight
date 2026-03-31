import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import KnowledgeBaseList from '@/components/knowledge/KnowledgeBaseList.vue'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useConversationStore } from '@/stores/conversation'

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn()
  }),
  useRoute: () => ({
    params: {}
  })
}))

// Mock AppButton and AppIcon
vi.mock('@/components/common/AppButton.vue', () => ({
  default: {
    name: 'AppButton',
    template: '<button @click="$emit(\'click\')"><slot/></button>'
  }
}))

vi.mock('@/components/common/AppIcon.vue', () => ({
  default: {
    name: 'AppIcon',
    template: '<span>Icon</span>'
  }
}))

vi.mock('@/components/knowledge/KnowledgeBaseCard.vue', () => ({
  default: {
    name: 'KnowledgeBaseCard',
    props: ['knowledgeBase'],
    template: '<div class="kb-card" @click="$emit(\'click\')">{{ knowledgeBase.name }}</div>'
  }
}))

describe('KnowledgeBaseList.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders empty state when no knowledge bases', async () => {
    const store = useKnowledgeBaseStore()
    store.fetchAll = vi.fn().mockResolvedValue(undefined)
    const convStore = useConversationStore()
    convStore.fetchList = vi.fn().mockResolvedValue(undefined)
    
    const wrapper = mount(KnowledgeBaseList)
    store.list = [] // empty
    await new Promise(resolve => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('暂无知识库')
  })

  it('renders list of knowledge bases', async () => {
    const store = useKnowledgeBaseStore()
    store.fetchAll = vi.fn().mockResolvedValue(undefined)
    const convStore = useConversationStore()
    convStore.fetchList = vi.fn().mockResolvedValue(undefined)
    
    store.list = [
      { id: 1, name: 'KB 1', createTime: '', updateTime: '' },
      { id: 2, name: 'KB 2', createTime: '', updateTime: '' }
    ]
    const wrapper = mount(KnowledgeBaseList)
    await new Promise(resolve => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.empty').exists()).toBe(false)
    const cards = wrapper.findAll('.kb-card')
    expect(cards.length).toBe(2)
    expect(cards[0].text()).toContain('KB 1')
  })

  it('shows error state when fetching fails', async () => {
    const store = useKnowledgeBaseStore()
    store.fetchAll = vi.fn().mockRejectedValue(new Error('Network error'))
    const convStore = useConversationStore()
    convStore.fetchList = vi.fn().mockResolvedValue(undefined)
    
    const wrapper = mount(KnowledgeBaseList)
    // Wait for the mounted hook's loadData to finish
    await new Promise(resolve => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('加载失败')
  })
})