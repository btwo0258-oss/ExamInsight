import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { Marked, type Tokens } from 'marked'
import { markedHighlight } from 'marked-highlight'

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[character] ?? character)
}

function normalizeStreamingMarkdown(source: string) {
  let value = source
    .replace(/\n([。！？；，、：])/g, '$1')
    .replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
    .replace(/^([-*+])([^\s-*+])/gm, '$1 $2')
    .replace(/^(\d+\.)([^\s\d])/gm, '$1 $2')

  // Close an in-flight fenced block for rendering only. The stored message is untouched.
  const fenceCount = (value.match(/^```/gm) ?? []).length
  if (fenceCount % 2 !== 0) value += '\n```'
  return value
}

const markedInstance = new Marked(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code, language) {
      if (language && hljs.getLanguage(language)) {
        return hljs.highlight(code, { language }).value
      }
      return hljs.highlightAuto(code).value
    },
  }),
  {
    async: false,
    breaks: true,
    gfm: true,
    renderer: {
      html(token: Tokens.HTML) {
        return escapeHtml(token.text)
      },
    },
  },
)

export function renderMarkdownToHtml(markdown: string) {
  if (!markdown) return ''
  const parsed = markedInstance.parse(normalizeStreamingMarkdown(markdown))
  const html = typeof parsed === 'string' ? parsed : ''
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'script', 'iframe', 'object', 'embed'],
    FORBID_ATTR: ['style'],
  })
}
