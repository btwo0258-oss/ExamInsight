import privacy from './privacy.zh-CN.json'
import terms from './terms.zh-CN.json'

export type LegalDocument = {
  type: 'terms' | 'privacy'
  title: string
  version: string
  effectiveDate: string
  summary: string
  draftNotice: string
  sections: Array<{
    title: string
    paragraphs: string[]
  }>
}

export const termsDocument = terms as LegalDocument
export const privacyDocument = privacy as LegalDocument
export const CURRENT_TERMS_VERSION = termsDocument.version
export const CURRENT_PRIVACY_VERSION = privacyDocument.version

export function legalDocument(type: LegalDocument['type']): LegalDocument {
  return type === 'terms' ? termsDocument : privacyDocument
}
