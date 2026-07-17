import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ArtifactCard from '@/components/artifact/ArtifactCard.vue'
import type { ChatArtifactDto } from '@/types/contracts/artifact'

vi.mock('@/api/libraryResource', () => ({
  previewLibraryResource: vi.fn().mockResolvedValue({ status: 'ready' }),
}))

function artifact(overrides: Partial<ChatArtifactDto> = {}): ChatArtifactDto {
  return {
    artifactId: 'mindmap:1001',
    resourceId: 'resource-1',
    title: 'OOP 知识点结构图',
    fileName: 'OOP 知识点结构图.mindmap',
    fileType: 'mindmap',
    format: '思维导图',
    status: 'ready',
    preview: { kind: 'mindmap', mindMap: { data: { text: 'OOP' }, children: [] } },
    editable: true,
    editorRoute: '/mindmap/1001',
    ...overrides,
  }
}

describe('ArtifactCard actions', () => {
  it('places edit first and preview last for editable files', () => {
    const wrapper = mount(ArtifactCard, {
      props: { artifact: artifact() },
      global: { stubs: { MindMapStaticPreview: true } },
    })
    expect(wrapper.findAll('.artifact-card__actions button').map((button) => button.text())).toEqual([
      '编辑',
      '下载',
      '预览',
    ])
  })

  it('keeps spreadsheets preview-only without an edit action', () => {
    const wrapper = mount(ArtifactCard, {
      props: {
        artifact: artifact({
          artifactId: 'spreadsheet:sheet-1',
          fileName: '复习计划.xlsx',
          fileType: 'spreadsheet',
          format: 'XLSX',
          preview: { kind: 'spreadsheet', table: { columns: ['任务'], rows: [['复习']] } },
          editable: false,
          editorRoute: undefined,
        }),
      },
    })
    expect(wrapper.text()).not.toContain('编辑')
    expect(wrapper.findAll('.artifact-card__actions button').map((button) => button.text())).toEqual(['下载', '预览'])
  })
})
