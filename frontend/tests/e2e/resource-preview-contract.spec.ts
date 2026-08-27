import { expect, test, type Route } from '@playwright/test'
import JSZip from 'jszip'

const baseUrl = 'http://localhost:5173'

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function docxFixture(withPageLayout: boolean) {
  const zip = new JSZip()
  zip.file('[Content_Types].xml', `<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>`)
  zip.file('_rels/.rels', `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>`)
  zip.file('word/document.xml', `<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="40"/></w:rPr><w:t>文档预览验收</w:t></w:r></w:p><w:p><w:r><w:t>这段正文应当在纸张内部显示，不能贴着边缘。</w:t></w:r></w:p>${withPageLayout ? '<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1080" w:right="1440" w:bottom="1080" w:left="1440"/></w:sectPr>' : ''}</w:body></w:document>`)
  return zip.generateAsync({ type: 'nodebuffer' })
}

test.describe('统一资源预览契约', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('llm.token', 'mock-token')
      sessionStorage.setItem('llm.user', JSON.stringify({ id: 1, username: 'mock-user', nickname: 'Mock User' }))
    })
    await page.route('**/api/**', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const path = url.pathname
      if (!path.startsWith('/api/')) return route.continue()
      if (path === '/api/v2/auth/session') {
        return json(route, {
          userId: '01USERACCOUNT000000000000', email: 'student@example.com', displayName: '测试用户',
          authLevel: 'PASSWORD', idleExpiresAt: '2026-08-27T09:00:00Z', absoluteExpiresAt: '2026-09-03T08:00:00Z',
        })
      }
      if (path === '/api/v2/assets/MARKDOWN01/preview') {
        return json(route, {
          assetId: 'MARKDOWN01', versionId: 'VERSION01', name: '复习笔记.md', sourceType: 'UPLOAD',
          mimeType: 'text/markdown', sizeBytes: 320, extension: 'md', renderer: 'markdown', status: 'ready',
          contentUrl: '/api/v2/assets/MARKDOWN01/content', downloadUrl: '/api/v2/assets/MARKDOWN01/content',
          downloadAvailable: true, reason: null, updatedAt: '2026-08-27T08:00:00Z',
        })
      }
      if (path === '/api/v2/assets/MARKDOWN01/content') {
        return route.fulfill({
          status: 200,
          contentType: 'text/markdown',
          body: '# 复习重点\n\n```ts\nconst answer = 42\n```',
        })
      }
      if (path === '/api/v2/assets/LARGE01/preview') {
        return json(route, {
          assetId: 'LARGE01', versionId: 'VERSION02', name: '超大文件.pptx', sourceType: 'UPLOAD',
          mimeType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
          sizeBytes: 100000000, extension: 'pptx', renderer: 'pptx', status: 'too_large', contentUrl: null,
          downloadUrl: '/api/v2/assets/LARGE01/content', downloadAvailable: true,
          reason: '文件超过在线预览限制。', updatedAt: '2026-08-27T08:00:00Z',
        })
      }
      return json(route, {})
    })
  })

  test('Markdown 使用统一阅读器外壳并保留安全渲染内容', async ({ page }) => {
    await page.goto(`${baseUrl}/resources/MARKDOWN01/preview?returnTo=/library`)

    await expect(page.locator('.resource-preview-workspace')).toBeVisible()
    await expect(page.locator('.preview-breadcrumb strong')).toHaveText('复习笔记.md')
    await expect(page.locator('.markdown-document h1')).toHaveText('复习重点')
    await expect(page.locator('.markdown-document pre code')).toContainText('const answer = 42')
  })

  test('超过预览预算时只显示下载降级，不请求原文件内容', async ({ page }) => {
    let contentRequests = 0
    page.on('request', (request) => {
      if (request.url().includes('/api/v2/assets/LARGE01/content')) contentRequests += 1
    })

    await page.goto(`${baseUrl}/resources/LARGE01/preview?returnTo=/library`)

    await expect(page.getByRole('heading', { name: '文件过大，暂不在线预览' })).toBeVisible()
    await expect(page.getByRole('button', { name: '下载文件' })).toBeVisible()
    expect(contentRequests).toBe(0)
  })

  for (const scenario of [
    { id: 'LEGACYDOCX', sourceType: 'AI_GENERATED', layout: false, width: 794, padding: 90 },
    { id: 'UPLOADEDDOCX', sourceType: 'UPLOAD', layout: true, width: 816, padding: 96 },
  ]) {
    test(`${scenario.id} 保持纸张几何与正文留白`, async ({ page }) => {
      const content = await docxFixture(scenario.layout)
      await page.route(`**/api/v2/assets/${scenario.id}/**`, async route => {
        if (new URL(route.request().url()).pathname.endsWith('/preview')) {
          return json(route, { assetId: scenario.id, versionId: 'DOCXVERSION', name: '文档预览验收.docx',
            sourceType: scenario.sourceType, extension: 'docx', renderer: 'docx', status: 'ready',
            mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            sizeBytes: content.length, contentUrl: `/api/v2/assets/${scenario.id}/content`,
            downloadAvailable: true, updatedAt: '2026-08-28T08:00:00Z' })
        }
        return route.fulfill({ status: 200, body: content, contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
      })
      await page.goto(`${baseUrl}/resources/${scenario.id}/preview?returnTo=/library`)
      const sheet = page.locator('.docx-wrapper > section.docx').first()
      await expect(sheet).toBeVisible()
      await expect(sheet).toContainText('文档预览验收')
      const geometry = await sheet.evaluate(element => {
        const rect = element.getBoundingClientRect()
        const style = getComputedStyle(element)
        const paragraph = element.querySelector('p')!.getBoundingClientRect()
        return { width: rect.width, height: rect.height, padding: parseFloat(style.paddingLeft),
          contentInset: paragraph.left - rect.left }
      })
      expect(Math.abs(geometry.width - scenario.width)).toBeLessThan(2)
      expect(geometry.height).toBeGreaterThan(1000)
      expect(Math.abs(geometry.padding - scenario.padding)).toBeLessThan(2)
      expect(geometry.contentInset).toBeGreaterThan(80)
      if (!scenario.layout) await expect(page.locator('[data-preview-defaults="true"]')).toBeVisible()
      else await expect(page.locator('[data-preview-defaults="true"]')).toHaveCount(0)
    })
  }
})
