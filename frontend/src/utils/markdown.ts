// @ts-nocheck
import { Marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

function sanitizeIncompleteMarkdown(md: string): string {
  if (!md) return ''

  let sanitized = md

  // 预处理：修复行首标点符号（将行首的标点合并到上一行）
  sanitized = sanitized.replace(/\n([。！？；，、：；])/g, '$1')

  // 预处理：修复标题格式（###文字 -> ### 文字）
  sanitized = sanitized.replace(/^(#{1,6})([^\s#])/gm, '$1 $2')

  // 预处理：修复列表项格式（-文字 -> - 文字）
  sanitized = sanitized.replace(/^([-*+])([^\s-*+])/gm, '$1 $2')
  sanitized = sanitized.replace(/^(\d+\.)([^\s\d])/gm, '$1 $2')

  // 提取代码块内容，避免对其中的符号进行匹配
  const codeBlocks: string[] = []
  sanitized = sanitized.replace(/```[\s\S]*?```/g, (match) => {
    codeBlocks.push(match)
    return `__CODE_BLOCK_${codeBlocks.length - 1}__`
  })

  // 提取行内代码
  const inlineCodes: string[] = []
  sanitized = sanitized.replace(/`[^`]+`/g, (match) => {
    inlineCodes.push(match)
    return `__INLINE_CODE_${inlineCodes.length - 1}__`
  })

  // 修复未闭合的代码块
  const fenceCount = (sanitized.match(/```/g) || []).length
  if (fenceCount % 2 !== 0) {
    sanitized += '\n```'
  }

  // 修复未闭合的粗体
  const boldCount = (sanitized.match(/\*\*/g) || []).length
  if (boldCount % 2 !== 0) {
    sanitized += '**'
  }

  // 修复未闭合的斜体（排除已匹配的粗体）
  const tempWithoutBold = sanitized.replace(/\*\*/g, '')
  const italicCount = (tempWithoutBold.match(/\*/g) || []).length
  if (italicCount % 2 !== 0) {
    sanitized += '*'
  }

  // 修复未闭合的行内代码
  const remainingBackticks = (sanitized.match(/`/g) || []).length
  if (remainingBackticks % 2 !== 0) {
    sanitized += '`'
  }

  // 修复未闭合的引用块
  const lines = sanitized.split('\n')
  const lastLine = lines[lines.length - 1] || ''
  if (lastLine.trim().startsWith('>')) {
    sanitized += '\n'
  }

  // 确保标题后面有换行符（如果还没有的话）
  // 移除强制换行，避免标点符号被挤到下一行

  // 确保列表项后面有换行符（如果还没有的话）
  // 移除强制换行，避免标点符号被挤到下一行

  // 还原代码块和行内代码
  codeBlocks.forEach((block, i) => {
    sanitized = sanitized.replace(`__CODE_BLOCK_${i}__`, block)
  })
  inlineCodes.forEach((code, i) => {
    sanitized = sanitized.replace(`__INLINE_CODE_${i}__`, code)
  })

  return sanitized
}

const markedInstance = new Marked(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code, lang) {
      if (lang && hljs.getLanguage(lang)) {
        return hljs.highlight(code, { language: lang }).value
      }
      return hljs.highlightAuto(code).value
    }
  }),
  {
    breaks: true,
    gfm: true
  }
)

export function renderMarkdownToHtml(markdown: string): string {
  if (!markdown) return ''

  const sanitized = sanitizeIncompleteMarkdown(markdown)
  return markedInstance.parse(sanitized)
}
