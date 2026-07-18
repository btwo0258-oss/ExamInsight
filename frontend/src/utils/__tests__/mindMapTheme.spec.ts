import { describe, expect, it } from 'vitest'
import { mindMapRenderData, mockMindMapRenderConfig, resolveMindMapRenderConfig } from '@/utils/mindMapTheme'

describe('mind map presentation contract', () => {
  it('uses green only as the Mock fallback render configuration', () => {
    const config = mockMindMapRenderConfig()
    expect(config.layout).toBe('logicalStructure')
    expect(config.themeConfig?.backgroundColor).toBe('#ffffff')
    expect((config.themeConfig?.root as { fillColor?: string }).fillColor).toBe('#4f9b8d')
  })

  it('preserves backend node styles without mutating the authoritative tree', () => {
    const source = {
      data: { text: 'OOP', fillColor: '#ff0000', fontSize: 32 },
      children: [{ data: { text: '继承', color: '#0000ff' }, children: [] }],
    }
    const rendered = mindMapRenderData(source)
    expect(rendered.data).toEqual({ text: 'OOP', fillColor: '#ff0000', fontSize: 32 })
    expect(rendered.children?.[0]?.data).toEqual({ text: '继承', color: '#0000ff' })
    expect(source.data.fillColor).toBe('#ff0000')
    expect(rendered).not.toBe(source)
  })

  it('keeps an explicit formal render configuration unchanged', () => {
    const config = { theme: 'dark', layout: 'mindMap', themeConfig: { lineColor: '#123456' } }
    expect(resolveMindMapRenderConfig(config)).toEqual(config)
  })
})
