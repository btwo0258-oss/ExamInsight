import { expect, test, type Page, type Route } from '@playwright/test'

const baseUrl = 'http://localhost:5173'

const readyAsset = {
  assetId: 'ASSET01',
  name: '计算机网络期末笔记.pdf',
  assetType: 'DOCUMENT',
  sourceType: 'UPLOAD',
  status: 'ACTIVE',
  knowledgeBaseCount: 1,
  version: {
    versionId: 'VERSION01', versionNumber: 1, status: 'READY', mimeType: 'application/pdf',
    sizeBytes: 2048000, chunkCount: 46, indexedChunkCount: 46, failedChunkCount: 0,
    indexStatus: 'READY', createdAt: '2026-08-09T10:00:00Z',
  },
  trashedAt: null,
  createdAt: '2026-08-09T10:00:00Z',
  updatedAt: '2026-08-09T10:05:00Z',
}

const processingAsset = {
  ...readyAsset,
  assetId: 'ASSET02',
  name: '操作系统复习提纲.docx',
  knowledgeBaseCount: 0,
  version: { ...readyAsset.version, versionId: 'VERSION02', status: 'PROCESSING', indexStatus: 'WAITING_FOR_PARSE', chunkCount: 0, indexedChunkCount: 0 },
}

const imageAsset = {
  ...readyAsset,
  assetId: 'ASSET04',
  name: 'exam-outline.png',
  assetType: 'IMAGE',
  knowledgeBaseCount: 0,
  version: {
    ...readyAsset.version,
    versionId: 'VERSION04',
    mimeType: 'image/png',
    sizeBytes: 68,
  },
}

const trashedAsset = {
  ...readyAsset,
  assetId: 'ASSET03',
  name: '已删除的复习资料.pdf',
  status: 'TRASHED',
  trashedAt: '2026-08-09T11:00:00Z',
}

const knowledgeBase = {
  knowledgeBaseId: 'KB01',
  name: '计算机网络期末复习',
  description: '课程讲义、重点笔记和历年练习',
  status: 'ACTIVE',
  assetCount: 1,
  trashedAt: null,
  createdAt: '2026-08-08T10:00:00Z',
  updatedAt: '2026-08-09T10:05:00Z',
}

const trashedKnowledgeBase = {
  ...knowledgeBase,
  knowledgeBaseId: 'KB02',
  name: '已删除的知识库',
  status: 'TRASHED',
  trashedAt: '2026-08-09T11:00:00Z',
}

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function mockApi(page: Page) {
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    if (!path.startsWith('/api/')) return route.continue()
    if (path === '/api/v2/auth/session') {
      return json(route, {
        userId: 'USER01', email: 'student@example.com', displayName: '紫涵', authLevel: 'PASSWORD',
        idleExpiresAt: '2026-08-10T10:00:00Z', absoluteExpiresAt: '2026-08-16T10:00:00Z',
      })
    }
    if (path === '/api/v2/assets' && request.method() === 'GET') {
      return json(route, { items: url.searchParams.get('view') === 'trash' ? [trashedAsset] : [readyAsset, processingAsset, imageAsset], nextCursor: null })
    }
    if (path === '/api/v2/assets/ASSET04/content') {
      return route.fulfill({
        status: 200,
        contentType: 'image/png',
        body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64'),
      })
    }
    if (path === '/api/v2/knowledge-bases' && request.method() === 'GET') {
      return json(route, { items: url.searchParams.get('view') === 'trash' ? [trashedKnowledgeBase] : [knowledgeBase], nextCursor: null })
    }
    if (path === '/api/v2/knowledge-bases/KB01') return json(route, { knowledgeBase })
    if (path === '/api/v2/knowledge-bases/KB01/assets') return json(route, { items: [readyAsset], nextCursor: null })
    if (path === '/api/kb/list' || path === '/api/conversations' || path.includes('/learning') || path.includes('/resources') || path.includes('/mindmap')) {
      return json(route, { data: [] })
    }
    return json(route, { data: [] })
  })
}

test.describe('V2 个人资料库', () => {
  test.use({ viewport: { width: 1440, height: 960 } })

  test.beforeEach(async ({ page }) => {
    page.on('pageerror', (error) => console.error('PAGE_ERROR', error.message))
    page.on('console', (message) => {
      if (message.type() === 'error') console.error('BROWSER_ERROR', message.text())
    })
    await page.addInitScript(() => {
      localStorage.clear()
      sessionStorage.clear()
    })
    await mockApi(page)
  })

  test('展示真实状态并进入知识库内部管理', async ({ page }) => {
    await page.goto(`${baseUrl}/library`)

    await expect(page.getByRole('heading', { name: '资料库' })).toBeVisible()
    await expect(page.getByText('计算机网络期末笔记.pdf')).toBeVisible()
    await expect(page.getByText('可用于 AI').first()).toBeVisible()
    await expect(page.getByText('操作系统复习提纲.docx')).toBeVisible()
    await expect(page.getByText('解析中')).toBeVisible()
    await expect(page.getByText('主要知识点')).toHaveCount(0)
    await expect(page.getByText('推荐用途')).toHaveCount(0)
    const documentCard = page.locator('.asset-card').filter({ hasText: readyAsset.name })
    await expect(documentCard.locator('.asset-card-visual')).toBeVisible()
    await expect(documentCard.locator('.asset-thumbnail')).toHaveCount(0)
    expect(await documentCard.getByRole('button', { name: `选择 ${readyAsset.name}` }).evaluate((node) => node.closest('article') !== null)).toBe(true)
    const imageCard = page.locator('.asset-card').filter({ hasText: imageAsset.name })
    await expect(imageCard.locator('.asset-thumbnail img')).toBeVisible()
    await page.screenshot({ path: 'test-results/library-v2-home.png', fullPage: true })

    await page.getByText('计算机网络期末复习', { exact: true }).click()
    await expect(page).toHaveURL(`${baseUrl}/library/KB01`)
    await expect(page.getByRole('heading', { name: '文件列表' })).toBeVisible()
    await expect(page.getByText('移除只会解除知识库关联，不会删除个人资料库原文件。')).toBeVisible()
    await page.screenshot({ path: 'test-results/library-v2-detail.png', fullPage: true })
  })

  test('上传入口说明真实支持范围和可选知识库关联', async ({ page }) => {
    await page.goto(`${baseUrl}/library`)
    await page.getByRole('button', { name: '新建', exact: true }).click()
    await page.getByRole('button', { name: '上传资料', exact: true }).click()

    await expect(page.getByRole('heading', { name: '上传学习资料' })).toBeVisible()
    await expect(page.getByText(/支持 PDF、DOCX、PPTX、XLSX/)).toBeVisible()
    await expect(page.getByText('上传后加入知识库')).toBeVisible()
    await expect(page.getByRole('option', { name: '计算机网络期末复习' })).toHaveCount(1)
    await page.screenshot({ path: 'test-results/library-v2-upload.png', fullPage: true })
  })

  test('保留网格、列表、分类、筛选、批量选择和项目菜单', async ({ page }) => {
    await page.goto(`${baseUrl}/library`)

    const gridButton = page.getByRole('button', { name: '网格视图' })
    const listButton = page.getByRole('button', { name: '列表视图' })
    await expect(gridButton).toBeVisible()
    await expect(listButton).toBeVisible()

    const fileCard = page.locator('.asset-card').filter({ hasText: '计算机网络期末笔记.pdf' })
    await fileCard.hover()
    await fileCard.getByRole('button', { name: '资料菜单' }).click()
    await expect(fileCard.getByRole('button', { name: '加入知识库' })).toBeVisible()
    await page.locator('.library-page').click({ position: { x: 10, y: 10 } })

    await expect(page.getByRole('button', { name: '知识库', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '知识库', exact: true }).click()
    await expect(page.getByText('计算机网络期末复习', { exact: true })).toBeVisible()
    await expect(page.getByText('计算机网络期末笔记.pdf', { exact: true })).toHaveCount(0)
    await page.getByRole('button', { name: '全部', exact: true }).click()

    await page.getByRole('button', { name: '筛选' }).click()
    await expect(page.getByText('来源', { exact: true })).toBeVisible()
    await expect(page.getByText('文件类型', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: /最近删除/ })).toBeVisible()
    await page.keyboard.press('Escape')

    await listButton.click()
    await expect(page.locator('.asset-list-head')).toBeVisible()
    await gridButton.click()
    await page.getByRole('button', { name: '选择 计算机网络期末笔记.pdf' }).click()
    await expect(page.locator('.bulk-actions').getByRole('button', { name: '加入知识库' })).toBeVisible()
    await expect(page.locator('.bulk-actions').getByRole('button', { name: '移入回收站' })).toBeVisible()

    await page.getByRole('button', { name: '新建', exact: true }).click()
    await expect(page.getByRole('button', { name: '上传资料', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '新建知识库', exact: true })).toBeVisible()
  })

  test('浅色和深色主题都使用统一变量', async ({ page }) => {
    await page.goto(`${baseUrl}/library`)
    await expect(page.getByText('计算机网络期末笔记.pdf')).toBeVisible()
    await page.screenshot({ path: 'test-results/library-v2-light.png', fullPage: true })
    await page.getByRole('switch', { name: '切换到深色主题' }).click()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    await expect
      .poll(() =>
        page
          .locator('.asset-card--knowledge')
          .evaluate((element) => getComputedStyle(element).backgroundColor),
      )
      .toBe('rgb(21, 21, 24)')
    await page.screenshot({ path: 'test-results/library-v2-dark.png', fullPage: true })
  })

  test('最近删除同时支持分类、网格、列表和独立选择区', async ({ page }) => {
    await page.goto(`${baseUrl}/library`)
    await page.getByRole('button', { name: '筛选' }).click()
    await page.getByRole('button', { name: /最近删除/ }).click()

    await expect(page.getByRole('heading', { name: '最近删除' })).toBeVisible()
    await expect(page.getByText('已删除的知识库', { exact: true })).toBeVisible()
    await expect(page.getByText('已删除的复习资料.pdf', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '知识库', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '网格视图' })).toBeVisible()
    await page.getByRole('button', { name: '列表视图' }).click()
    await expect(page.locator('.asset-list')).toBeVisible()
    await expect(page.getByRole('button', { name: '选择全部' })).toBeVisible()
    const selection = page.getByRole('button', { name: '选择 已删除的复习资料.pdf' })
    expect(await selection.evaluate((node) => node.closest('article'))).toBeNull()
  })
})
