import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import MessageBubble from '@/components/main-area/mode3-chat/message/MessageBubble.vue'
import { createPinia, setActivePinia } from 'pinia'
import * as clipboard from '@/utils/clipboard'
import { useMessageStore } from '@/stores/message'

vi.mock('@/utils/clipboard', () => ({
  copyText: vi.fn()
}))

// Mock AppIcon, MarkdownRenderer, etc.
vi.mock('@/components/common/AppIcon.vue', () => ({
  default: { name: 'AppIcon', template: '<span>Icon</span>' }
}))
vi.mock('@/components/main-area/mode3-chat/message/MarkdownRenderer.vue', () => ({
  default: { name: 'MarkdownRenderer', template: '<div>{{ $attrs.content }}</div>' }
}))

describe('MessageBubble.vue Actions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('handles copy action', async () => {
    const wrapper = mount(MessageBubble, {
      props: {
        message: { id: '1', role: 'assistant', content: 'AI Response', createTime: Date.now() },
        isStreaming: false
      }
    })
    
    // Find copy button in MessageActions
    const copyBtn = wrapper.find('button[title="复制"]')
    expect(copyBtn.exists()).toBe(true)
    
    await copyBtn.trigger('click')
    expect(clipboard.copyText).toHaveBeenCalledWith('AI Response')
  })

  it('handles edit action and submit', async () => {
    const wrapper = mount(MessageBubble, {
      props: {
        message: { id: '2', role: 'user', content: 'User query', createTime: Date.now(), turnId: 'turn2' },
        conversationId: 1
      }
    })

    const editBtn = wrapper.find('button[title="编辑"]')
    expect(editBtn.exists()).toBe(true)

    await editBtn.trigger('click')
    
    // Should show textarea
    const textarea = wrapper.find('textarea.edit-textarea')
    expect(textarea.exists()).toBe(true)
    expect((textarea.element as HTMLTextAreaElement).value).toBe('User query')
    
    // Submit edit
    await textarea.setValue('Edited query')
    
    const store = useMessageStore()
    store.editAndRegenerate = vi.fn()
    
    await wrapper.find('.btn.submit').trigger('click')
    expect(store.editAndRegenerate).toHaveBeenCalledWith(
      1, 
      'turn2', 
      'Edited query'
    )
  })

  it('handles regenerate action for AI message', async () => {
    const store = useMessageStore()
    store.regenerate = vi.fn()
    
    // Set up mock messages for store to find the question
    store.byConversation = {
      '1': [
        { id: '1', role: 'user', content: 'User query', createTime: Date.now(), turnId: 'turn1', qVersion: 0 },
        { id: '2', role: 'assistant', content: 'AI Response', createTime: Date.now(), parentId: 1, turnId: 'turn1', qVersion: 0, aVersion: 0 }
      ]
    }

    const wrapper = mount(MessageBubble, {
      props: {
        message: { id: '2', role: 'assistant', content: 'AI Response', createTime: Date.now(), parentId: 1, turnId: 'turn1', qVersion: 0, aVersion: 0 },
        conversationId: 1,
        isStreaming: false
      }
    })

    const regenBtn = wrapper.find('button[title="重新生成"]')
    await regenBtn.trigger('click')

    expect(store.regenerate).toHaveBeenCalledWith(
      1, 
      'turn1'
    )
  })
})