import { describe, expect, it } from 'vitest'
import { inferArtifactRequest } from '../artifactRequest'

describe('artifact UI reservation', () => {
  it.each([
    ['请生成一份可编辑文档，介绍 JavaScript', 'DOCUMENT'],
    ['做一个学习方法 PPT', 'PRESENTATION'],
    ['帮我画一张小猫图片', 'IMAGE'],
    ['请根据这个主题生成思维导图', 'MINDMAP'],
    ['Create a document about JavaScript', 'DOCUMENT'],
    ['生成一份关于如何有效沟通的思维导图', 'MINDMAP'],
    ['请生成一个介绍 JavaScript 原理的 PPT', 'PRESENTATION'],
    ['帮我画一张特别可爱的猫咪图片', 'IMAGE'],
    ['写一份文档，介绍怎么学习 JavaScript', 'DOCUMENT'],
  ])('reserves a matching slot for %s', (text, type) => expect(inferArtifactRequest(text)).toBe(type))

  it.each(['你好', '如何生成一个 PPT？', '不要生成文档', 'PPT 和文档有什么区别', '讲解一下这篇文档'])
    ('does not reserve a card for %s', text => expect(inferArtifactRequest(text)).toBeNull())

  it('uses only explicitly supplied current-branch context for another generation', () => {
    expect(inferArtifactRequest('再生成一个', 'PRESENTATION')).toBe('PRESENTATION')
    expect(inferArtifactRequest('再来一份，主题是如何沟通', 'MINDMAP')).toBe('MINDMAP')
    expect(inferArtifactRequest('再生成一个')).toBeNull()
  })
})
