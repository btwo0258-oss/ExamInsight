/** Only used for AI-generated files. Uploaded Office documents keep their own layout. */
type SectionProperties = {
  pageSize?: { width?: string | null; height?: string | null; orientation?: string | null }
  pageMargins?: Partial<Record<'top' | 'right' | 'bottom' | 'left' | 'header' | 'footer' | 'gutter', string | null>>
}

type DocumentNode = {
  type?: string
  styleName?: string
  props?: SectionProperties & { sectionProps?: SectionProperties }
  cssStyle?: Record<string, string>
  children?: DocumentNode[]
}

export type ParsedDocx = {
  documentPart: { body: DocumentNode }
  stylesPart?: unknown
}

function fillSection(section: SectionProperties) {
  section.pageSize ??= {}
  section.pageSize.width ??= '595.3pt'
  section.pageSize.height ??= '841.9pt'
  section.pageMargins ??= {}
  for (const side of ['top', 'right', 'bottom', 'left'] as const) {
    section.pageMargins[side] ??= '68pt'
  }
  section.pageMargins.header ??= '28pt'
  section.pageMargins.footer ??= '28pt'
}

/** Mutates only the in-memory render model, never the stored/downloaded DOCX bytes. */
export function applyGeneratedDocxDefaults(document: ParsedDocx) {
  const body = document.documentPart.body
  body.props ??= {}
  fillSection(body.props)
  const legacy = !document.stylesPart
  if (legacy) {
    body.cssStyle = {
      'font-family': '"Microsoft YaHei", Calibri, sans-serif',
      'font-size': '11pt',
      color: '#202020',
      ...body.cssStyle,
    }
  }
  const pending = [...(body.children ?? [])]
  while (pending.length) {
    const node = pending.pop()!
    if (node.props?.sectionProps) fillSection(node.props.sectionProps)
    if (legacy && node.type === 'paragraph') {
      const heading = /^heading[1-6]$/i.test(node.styleName ?? '')
      node.cssStyle = {
        'line-height': heading ? '1.35' : '1.5',
        'margin-top': heading ? '14pt' : '0pt',
        'margin-bottom': heading ? '8pt' : '6pt',
        ...node.cssStyle,
      }
    }
    if (node.children) pending.push(...node.children)
  }
  return legacy
}
