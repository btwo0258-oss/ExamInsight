import { expect, test, type Route } from '@playwright/test'

const baseUrl = 'http://127.0.0.1:5173'
const conversationId = '01CONTRACT00000000000000000'
const branchId = '01BRANCH000000000000000000'
const alternateBranchId = '01BRANCH20000000000000000'
const userOneId = '01USER10000000000000000000'
const assistantOneId = '01ASSIST1000000000000000000'
const userTwoId = '01USER20000000000000000000'
const assistantTwoId = '01ASSIST2000000000000000000'

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

test('current chat contract keeps actions, versions and segment navigation visible', async ({ page }) => {
  page.on('pageerror', error => console.error('PAGE_ERROR', error.message))
  page.on('console', message => {
    if (message.type() === 'error') console.error('BROWSER_ERROR', message.text())
  })
  await page.addInitScript(() => {
    sessionStorage.setItem('llm.token', 'mock-token')
    sessionStorage.setItem('llm.user', JSON.stringify({ id: 1, username: 'mock-user', nickname: 'Mock User' }))
  })

  const conversation = {
    id: conversationId,
    title: '复习计算机网络重点',
    titleSource: 'AI',
    type: 'GENERAL',
    status: 'ACTIVE',
    knowledgeBaseId: null,
    activeBranchId: branchId,
    messageCount: 4,
    version: 2,
    pinnedAt: null,
    lastMessageAt: '2026-08-27T08:00:00Z',
    createdAt: '2026-08-27T07:00:00Z',
    updatedAt: '2026-08-27T08:00:00Z',
  }
  const messages = [
    {
      id: userOneId, branchId, versionGroupId: userOneId, parentMessageId: null,
      role: 'USER', status: 'FINALIZED', sequence: 1, content: '什么是 TCP 三次握手？', runId: null,
      attachments: [], citations: [], createdAt: '2026-08-27T07:30:00Z', finalizedAt: '2026-08-27T07:30:00Z',
    },
    {
      id: assistantOneId, branchId, versionGroupId: assistantOneId, parentMessageId: userOneId,
      role: 'ASSISTANT', status: 'FINALIZED', sequence: 2,
      content: 'TCP 三次握手用于建立可靠连接。', runId: '01RUN10000000000000000000',
      attachments: [], citations: [], createdAt: '2026-08-27T07:31:00Z', finalizedAt: '2026-08-27T07:31:00Z',
    },
    {
      id: userTwoId, branchId, versionGroupId: userTwoId, parentMessageId: assistantOneId,
      role: 'USER', status: 'FINALIZED', sequence: 3, content: '那四次挥手呢？', runId: null,
      attachments: [], citations: [], createdAt: '2026-08-27T07:40:00Z', finalizedAt: '2026-08-27T07:40:00Z',
    },
    {
      id: assistantTwoId, branchId, versionGroupId: assistantTwoId, parentMessageId: userTwoId,
      role: 'ASSISTANT', status: 'FINALIZED', sequence: 4, content: '四次挥手用于有序释放连接。', runId: '01RUN20000000000000000000',
      attachments: [], citations: [], createdAt: '2026-08-27T07:41:00Z', finalizedAt: '2026-08-27T07:41:00Z',
    },
  ]

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    if (path === '/api/v2/auth/session') {
      return json(route, {
        userId: '01USERACCOUNT000000000000', email: 'student@example.com', displayName: '测试用户',
        authLevel: 'PASSWORD', idleExpiresAt: '2026-08-27T09:00:00Z', absoluteExpiresAt: '2026-09-03T08:00:00Z',
      })
    }
    if (path === '/api/v2/conversations' && request.method() === 'GET') {
      return json(route, { items: [conversation], nextCursor: null, hasMore: false })
    }
    if (path === `/api/v2/conversations/${conversationId}`) {
      return json(route, { conversation, messages, versionGroups: [
        {
          id: assistantOneId,
          role: 'ASSISTANT',
          versions: [
            { messageId: assistantOneId, branchId, createdAt: '2026-08-27T07:31:00Z' },
            { messageId: '01ASSISTALT000000000000000', branchId: alternateBranchId, createdAt: '2026-08-27T07:32:00Z' },
          ],
        },
      ] })
    }
    if (path === '/api/v2/knowledge-bases' && request.method() === 'GET') {
      return json(route, { items: [], nextCursor: null })
    }
    if (path === '/api/v2/artifacts' && request.method() === 'GET') return json(route, [])
    return json(route, {})
  })

  await page.setViewportSize({ width: 847, height: 900 })
  await page.goto(`${baseUrl}/chat/${conversationId}`)

  await expect(page.locator('.assistant-message')).toHaveCount(2)
  await expect(page.locator('.user-message')).toHaveCount(2)
  await expect(page.locator('.assistant-message').first().locator('button[title="复制"]')).toBeVisible()
  await expect(page.locator('.assistant-message').first().locator('button[title="重新生成"]')).toBeVisible()
  await expect(page.locator('.user-message').first().locator('button[title="编辑"]')).toBeVisible()
  await expect(page.locator('.assistant-message').first().locator('.version-nav')).toBeVisible()
  await expect(page.locator('.segment-panel')).toBeVisible()
  await expect(page.locator('.segment-panel button')).toHaveCount(2)
  await page.locator('.segment-panel button').nth(1).click()
})
