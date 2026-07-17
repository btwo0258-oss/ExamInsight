import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ResourceTypeIcon from '@/components/common/ResourceTypeIcon.vue'
import { resourceVisualTypeFromFile, resourceVisualTypeFromLearningGroup } from '@/utils/resourceVisual'

describe('ResourceTypeIcon', () => {
  it('uses the same semantic resource types for files and learning resources', () => {
    expect(resourceVisualTypeFromFile('课堂演示.pptx')).toBe('presentation')
    expect(resourceVisualTypeFromLearningGroup('PPT')).toBe('presentation')
    expect(resourceVisualTypeFromFile('复习方案.md')).toBe('markdown')
    expect(resourceVisualTypeFromFile('成绩统计.xlsx')).toBe('spreadsheet')
  })

  it('renders the shared type marker and selected layout variant', () => {
    const wrapper = mount(ResourceTypeIcon, {
      props: { type: 'mindmap', variant: 'plain', size: 20 },
    })

    expect(wrapper.attributes('data-resource-type')).toBe('mindmap')
    expect(wrapper.classes()).toContain('resource-type-icon--plain')
  })
})
