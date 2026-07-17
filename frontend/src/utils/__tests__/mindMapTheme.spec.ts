import { describe, expect, it } from 'vitest'
import { mindMapRenderData, mindMapThemeConfig } from '@/utils/mindMapTheme'

describe('mind map presentation contract', () => {
  it('uses the green logical-structure theme on every surface', () => {
    const theme = mindMapThemeConfig()
    expect(theme.backgroundColor).toBe('#ffffff')
    expect(theme.root.fillColor).toBe('#4f9b8d')
    expect(theme.second.borderColor).toBe('#4f9b8d')
  })

  it('removes stale per-node colors without mutating content', () => {
    const source = {
      data: { text: 'OOP', fillColor: '#ff0000', fontSize: 32 },
      children: [{ data: { text: '继承', color: '#0000ff' }, children: [] }],
    }
    const rendered = mindMapRenderData(source)
    expect(rendered.data).toEqual({ text: 'OOP' })
    expect(rendered.children?.[0]?.data).toEqual({ text: '继承' })
    expect(source.data.fillColor).toBe('#ff0000')
  })
})
