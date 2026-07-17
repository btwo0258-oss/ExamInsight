import { describe, expect, it } from 'vitest'
import { renderMarkdownToHtml } from '@/utils/markdown'

describe('renderMarkdownToHtml', () => {
  it('renders fenced code with syntax highlighting', () => {
    const html = renderMarkdownToHtml('```ts\nconst answer = 42\n```')
    expect(html).toContain('hljs')
    expect(html).toContain('answer')
  })

  it('escapes raw HTML from streamed model output', () => {
    const html = renderMarkdownToHtml('<script>alert(1)</script>')
    expect(html).not.toContain('<script>')
    expect(html).toContain('&lt;script&gt;')
  })
})

