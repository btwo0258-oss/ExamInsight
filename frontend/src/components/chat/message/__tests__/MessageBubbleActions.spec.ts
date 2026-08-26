import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import MessageBubble from '@/components/chat/message/MessageBubble.vue'
import * as clipboard from '@/utils/clipboard'
import type { ChatMessage } from '@/types/contracts/chatV2'

vi.mock('@/utils/clipboard', () => ({ copyText: vi.fn() }))
vi.mock('@/components/chat/message/MarkdownRenderer.vue', () => ({
  default: { props: ['content'], template: '<div class="markdown-stub">{{ content }}</div>' },
}))
vi.mock('@/components/chat/ChatAttachmentList.vue', () => ({
  default: { template: '<div class="attachment-stub" />' },
}))

function message(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: '01MESSAGE00000000000000000',
    branchId: '01BRANCH000000000000000000',
    versionGroupId: '01MESSAGE00000000000000000',
    parentMessageId: null,
    role: 'ASSISTANT',
    status: 'FINALIZED',
    sequence: 2,
    content: 'AI Response',
    runId: '01RUN00000000000000000000',
    attachments: [],
    citations: [],
    createdAt: '2026-08-26T00:00:00Z',
    finalizedAt: '2026-08-26T00:00:01Z',
    ...overrides,
  }
}

describe('MessageBubble V2 actions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('copies an assistant answer', async () => {
    const wrapper = mount(MessageBubble, { props: { message: message() } })
    await wrapper.get('button[title="复制"]').trigger('click')
    expect(clipboard.copyText).toHaveBeenCalledWith('AI Response')
  })

  it('edits a user message and emits a real branch request', async () => {
    const wrapper = mount(MessageBubble, {
      props: { message: message({ role: 'USER', content: 'User query', sequence: 1, runId: null }) },
    })
    await wrapper.get('button[title="编辑"]').trigger('click')
    const textarea = wrapper.get('textarea')
    expect((textarea.element as HTMLTextAreaElement).value).toBe('User query')
    await textarea.setValue('Edited query')
    await wrapper.get('button[title="保存并重新生成"]').trigger('click')
    expect(wrapper.emitted('edit')?.[0]).toEqual(['01MESSAGE00000000000000000', 'Edited query'])
  })

  it('emits regenerate for an assistant message', async () => {
    const wrapper = mount(MessageBubble, { props: { message: message() } })
    await wrapper.get('button[title="重新生成"]').trigger('click')
    expect(wrapper.emitted('regenerate')?.[0]).toEqual(['01MESSAGE00000000000000000'])
  })

  it('switches between persisted branch versions', async () => {
    const wrapper = mount(MessageBubble, {
      props: {
        message: message(),
        versionGroup: {
          id: '01MESSAGE00000000000000000',
          role: 'ASSISTANT',
          versions: [
            { messageId: '01MESSAGE00000000000000000', branchId: '01BRANCH000000000000000000', createdAt: '2026-08-26T00:00:00Z' },
            { messageId: '01MESSAGE20000000000000000', branchId: '01BRANCH200000000000000000', createdAt: '2026-08-26T00:01:00Z' },
          ],
        },
      },
    })
    await wrapper.get('button[title="下一个版本"]').trigger('click')
    expect(wrapper.emitted('switchVersion')?.[0]).toEqual(['01BRANCH200000000000000000'])
  })
})
