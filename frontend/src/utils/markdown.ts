// @ts-nocheck
function escapeHtml(input: string): string {
  return input
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

export function renderMarkdownToHtml(markdown: string): string {
  if (!markdown) return ''

  // 1. 转义基础 HTML 字符以防 XSS
  let html = markdown
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // 2. 处理代码块 (```code```)
  html = html.replace(/```(?:[a-z0-9]+)?\n([\s\S]*?)```/g, (match, code) => {
    return `<pre><code>${code.trim()}</code></pre>`
  })
  // 处理没有换行的代码块或者未闭合的代码块 (简单处理)
  html = html.replace(/```([\s\S]*?)```/g, (match, code) => {
    return `<pre><code>${code.trim()}</code></pre>`
  })

  // 3. 处理行内代码 (`code`)
  html = html.replace(/`([^`\n]+)`/g, '<code>$1</code>')

  // 4. 处理标题 (# Title)
  html = html.replace(/^# (.*$)/gm, '<h1>$1</h1>')
  html = html.replace(/^## (.*$)/gm, '<h2>$1</h2>')
  html = html.replace(/^### (.*$)/gm, '<h3>$1</h3>')

  // 5. 处理粗体 (**bold**)
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')

  // 6. 处理列表 (- list)
  html = html.replace(/^\s*[-*+]\s+\[ \]\s+(.*$)/gm, '<li><input type="checkbox" disabled /> $1</li>')
  html = html.replace(/^\s*[-*+]\s+\[x\]\s+(.*$)/gm, '<li><input type="checkbox" checked disabled /> $1</li>')
  html = html.replace(/^\s*[-*+]\s+(.*$)/gm, '<li>$1</li>')
  
  // 将连续的 <li> 包裹在 <ul> 中
  html = html.replace(/(<li>.*<\/li>)/gs, '<ul>$1</ul>')
  // 简单清理 <ul> 嵌套
  html = html.replace(/<\/ul>\s*<ul>/g, '')

  // 8. 处理表格 (简单实现)
  const lines = html.split('\n')
  let inTable = false
  let tableHtml = ''
  const newLines = []

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim()
    if (line.startsWith('|') && line.endsWith('|')) {
      if (!inTable) {
        inTable = true
        tableHtml = '<table class="markdown-table">'
      }
      const cells = line.split('|').filter(c => c.trim() !== '').map(c => c.trim())
      if (lines[i + 1]?.includes('---')) {
        tableHtml += '<thead><tr>' + cells.map(c => `<th>${c}</th>`).join('') + '</tr></thead><tbody>'
        i++ // 跳过分隔行
      } else {
        tableHtml += '<tr>' + cells.map(c => `<td>${c}</td>`).join('') + '</tr>'
      }
    } else {
      if (inTable) {
        tableHtml += '</tbody></table>'
        newLines.push(tableHtml)
        inTable = false
        tableHtml = ''
      }
      newLines.push(lines[i])
    }
  }
  if (inTable) {
    tableHtml += '</tbody></table>'
    newLines.push(tableHtml)
  }
  html = newLines.join('\n')

  // 9. 处理换行 (如果不属于 block 元素，则添加 <br />)
  html = html.replace(/\n(?!<(h1|h2|h3|li|ul|pre|code|table|thead|tbody|tr|th|td))/g, '<br />')

  return html
}
