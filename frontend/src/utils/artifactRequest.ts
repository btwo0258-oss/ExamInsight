import type { ArtifactType } from '@/types/contracts/chatV2'

const artifactNames: ReadonlyArray<readonly [ArtifactType, RegExp]> = [
  ['MINDMAP', /思维导图|脑图|mind\s*map/i],
  ['PRESENTATION', /pptx?|演示文稿|幻灯片|slides?|presentation/i],
  ['IMAGE', /图片|图像|插画|海报|一[张幅](?:[^，。！？\n]{0,16})?图|\b(?:image|picture|illustration|poster)\b/i],
  ['DOCUMENT', /文档|docx|word|\bdocument\b/i],
]

function firstArtifactType(text: string): ArtifactType | null {
  let type: ArtifactType | null = null
  let earliest = Infinity
  for (const [candidate, pattern] of artifactNames) {
    const match = pattern.exec(text)
    if (match && match.index < earliest) {
      type = candidate
      earliest = match.index
    }
  }
  return type
}

/** A conservative UI reservation only; it never chooses tools or changes the backend request. */
export function inferArtifactRequest(content: string, previousType?: ArtifactType): ArtifactType | null {
  const text = content.trim()
  if (!text) return null
  // Examine the instruction before the action, not words inside the requested topic.
  // “生成一份关于如何沟通的思维导图” is a request; “如何生成思维导图” isn't.
  const action = /重新生成|再生成|生成|制作|再做|再来|做|画|写|导出|整理成|输出为|输出成|\b(?:regenerate|generate|create|make|draw|write|export)\b/gi
  for (const match of text.matchAll(action)) {
    const actionIndex = match.index ?? 0
    const prefix = text.slice(0, actionIndex).split(/[。！？!?；;\n]/).at(-1) ?? ''
    if (/(不要|不用|不需要|无需|别|取消|停止|不想|禁止)(?:再|继续|帮我|为我|\s)*$|\b(?:don't|do not|cancel|stop)\s*$/i.test(prefix)) continue
    if (/如何|怎么|怎样|是什么|区别|原理|\b(?:how|what|why|explain)\b/i.test(prefix)) continue
    const remainder = text.slice(actionIndex + match[0].length)
    const type = firstArtifactType(remainder.slice(0, 160))
    if (type) return type
    // Follow-ups reserve the same kind of card without forcing another paid tool call.
    if (previousType && /^(再生成|重新生成|再做|再来|regenerate)$/i.test(match[0])) return previousType
  }
  return null
}
