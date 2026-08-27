import { expect, test, type Route } from '@playwright/test'

const baseUrl = 'http://127.0.0.1:5173'
const asset = {
  assetId: 'UPLOADED01',
  name: '网络复习笔记.md',
  assetType: 'DOCUMENT',
  sourceType: 'UPLOAD',
  status: 'ACTIVE',
  knowledgeBaseCount: 1,
  version: {
    versionId: 'VERSION01',
    versionNumber: 1,
    status: 'READY',
    mimeType: 'text/markdown',
    sizeBytes: 128,
    chunkCount: 3,
    indexedChunkCount: 3,
    failedChunkCount: 0,
    indexStatus: 'READY',
    createdAt: '2026-08-27T08:00:00Z',
  },
  trashedAt: null,
  createdAt: '2026-08-27T08:00:00Z',
  updatedAt: '2026-08-27T08:00:00Z',
}

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

test('附件上传会显示在输入区，并在选择知识库时提交关联', async ({ page }) => {
  let associationRequests = 0

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
    if (path === '/api/v2/conversations' && request.method() === 'GET') {
      return json(route, { items: [], nextCursor: null, hasMore: false })
    }
    if (path === '/api/v2/knowledge-bases' && request.method() === 'GET') {
      return json(route, {
        items: [{
          knowledgeBaseId: 'KB01', name: '计算机网络期末复习', description: '课程讲义', status: 'ACTIVE',
          assetCount: 0, trashedAt: null, createdAt: '2026-08-27T08:00:00Z', updatedAt: '2026-08-27T08:00:00Z',
        }],
        nextCursor: null,
      })
    }
    if (path === '/api/v2/uploads' && request.method() === 'POST') {
      return json(route, {
        uploadId: 'UPLOAD01', originalFilename: '网络复习笔记.md', status: 'UPLOADING', expectedSize: 128,
        uploadedBytes: 0, partSize: 1024 * 1024, expectedPartCount: 1, expiresAt: '2026-08-27T10:00:00Z',
      })
    }
    if (path === '/api/v2/uploads/UPLOAD01/parts/1' && request.method() === 'PUT') {
      return route.fulfill({ status: 204 })
    }
    if (path === '/api/v2/uploads/UPLOAD01/complete' && request.method() === 'POST') {
      return json(route, {
        uploadId: 'UPLOAD01', status: 'PROCESSING', asset: { assetId: asset.assetId, name: asset.name, status: 'ACTIVE' },
        version: { ...asset.version, sha256: 'sha256' }, securityScanJob: { jobId: 'JOB01', status: 'SUCCEEDED', stage: 'SCANNED' },
      })
    }
    if (path === '/api/v2/knowledge-bases/KB01/assets/UPLOADED01' && request.method() === 'PUT') {
      associationRequests += 1
      return json(route, { knowledgeBase: { knowledgeBaseId: 'KB01', name: '计算机网络期末复习', description: '课程讲义', status: 'ACTIVE', assetCount: 1, trashedAt: null, createdAt: '2026-08-27T08:00:00Z', updatedAt: '2026-08-27T08:00:00Z' } })
    }
    if (path === '/api/v2/assets/UPLOADED01' && request.method() === 'GET') {
      return json(route, { asset, knowledgeBases: [{ knowledgeBaseId: 'KB01', name: '计算机网络期末复习' }], purgeJob: null })
    }
    return json(route, {})
  })

  await page.goto(`${baseUrl}/chat`)
  await page.getByRole('button', { name: /不关联知识库/ }).click()
  await page.getByRole('button', { name: '计算机网络期末复习' }).click()
  await page.locator('.file-input').setInputFiles({
    name: '网络复习笔记.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from('# TCP 三次握手'),
  })

  await expect(page.getByText('网络复习笔记.md', { exact: true })).toBeVisible()
  await expect.poll(() => associationRequests).toBe(1)
})
