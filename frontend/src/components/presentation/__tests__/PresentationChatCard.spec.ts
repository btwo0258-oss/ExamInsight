import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PresentationChatCard from '@/components/presentation/PresentationChatCard.vue'
import AppSelectMenu from '@/components/common/AppSelectMenu.vue'
import type { PresentationChatCardDto } from '@/types/contracts/presentation'

function proposal(): PresentationChatCardDto {
  return {
    cardType: 'presentation',
    view: 'proposal',
    status: 'draft',
    conversationId: 1,
    sourceMessageId: 2,
    config: {
      topic: 'Java 多态',
      title: 'Java 多态',
      pageCount: 8,
      templateId: 'ink-focus',
      aspectRatio: '16:9',
      style: 'academic',
      audience: 'student',
      language: 'zh-CN',
    },
  }
}

describe('PresentationChatCard', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('edits proposal fields and exposes the three confirmed actions', async () => {
    const wrapper = mount(PresentationChatCard, { props: { data: proposal() } })

    expect(wrapper.text()).toContain('取消')
    expect(wrapper.text()).toContain('更多设置')
    expect(wrapper.text()).toContain('生成大纲')
    expect(wrapper.find('[data-resource-type="presentation"]').exists()).toBe(true)

    await wrapper.find('input[placeholder="输入 PPT 主题"]').setValue('数据结构复习')
    const update = wrapper.emitted('update')?.at(-1)?.[0] as PresentationChatCardDto
    expect(update.config.topic).toBe('数据结构复习')
    expect(update.config.title).toBe('数据结构复习')

    const knowledgeSelect = wrapper.findComponent(AppSelectMenu)
    expect(knowledgeSelect.exists()).toBe(true)
    knowledgeSelect.vm.$emit('update:modelValue', 9)
    const associationUpdate = wrapper.emitted('update')?.at(-1)?.[0] as PresentationChatCardDto
    expect(associationUpdate.knowledgeBaseId).toBe(9)
  })

  it('renders preview, download, and knowledge-base actions for a ready result', () => {
    const data: PresentationChatCardDto = {
      ...proposal(),
      view: 'result',
      status: 'ready',
      presentationId: 'ppt-1',
      fileName: 'Java 多态.pptx',
      previewPageCount: 8,
    }
    const wrapper = mount(PresentationChatCard, { props: { data } })

    expect(wrapper.text()).toContain('PPT 已生成')
    expect(wrapper.text()).toContain('预览')
    expect(wrapper.text()).toContain('下载')
    expect(wrapper.text()).toContain('添加到知识库')
  })
})
