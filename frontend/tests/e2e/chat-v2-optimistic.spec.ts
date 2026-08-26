import { expect, test, type Route } from '@playwright/test'

const baseUrl = 'http://127.0.0.1:5173'

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

test('enters the detail route and paints the user turn before conversation creation returns', async ({ page }) => {
  page.on('pageerror', error => console.error('PAGE_ERROR', error.message))
  page.on('console', message => {
    if (message.type() === 'error') console.error('BROWSER_ERROR', message.text())
  })
  let releaseCreate!: () => void
  const createGate = new Promise<void>((resolve) => { releaseCreate = resolve })
  let observeCreate!: (conversationId: string) => void
  const createObserved = new Promise<string>((resolve) => { observeCreate = resolve })
  let conversationId = ''
  const branchId = '01BRANCH000000000000000000'
  const userMessageId = '01USER0000000000000000000'
  const assistantMessageId = '01ASSISTANT00000000000000'
  const runId = '01RUN00000000000000000000'
  const timestamps = {
    createdAt: '2026-08-26T08:00:00Z',
    updatedAt: '2026-08-26T08:00:00Z',
  }

  function conversation() {
    return {
      id: conversationId, title: '新对话', type: 'GENERAL', status: 'ACTIVE',
      knowledgeBaseId: null, activeBranchId: branchId, messageCount: 2, version: 1,
      lastMessageAt: timestamps.updatedAt, ...timestamps,
    }
  }

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    if (!path.startsWith('/api/')) return route.continue()
    if (path === '/api/v2/auth/session') {
      return json(route, {
        userId: '01USERACCOUNT000000000000', email: 'student@example.com', displayName: '测试用户',
        authLevel: 'PASSWORD', idleExpiresAt: '2026-08-26T09:00:00Z',
        absoluteExpiresAt: '2026-09-02T08:00:00Z',
      })
    }
    if (path === '/api/v2/knowledge-bases' && request.method() === 'GET') {
      return json(route, { items: [], nextCursor: null })
    }
    if (path === '/api/v2/conversations' && request.method() === 'GET') {
      return json(route, { items: [], nextCursor: null, hasMore: false })
    }
    if (path === '/api/v2/conversations' && request.method() === 'POST') {
      conversationId = String(request.postDataJSON().conversationId)
      observeCreate(conversationId)
      await createGate
      return json(route, conversation(), 201)
    }
    if (path === `/api/v2/conversations/${conversationId}/messages`) {
      return json(route, { userMessageId, assistantMessageId, runId, eventUrl: `/api/v2/ai-runs/${runId}/events` }, 202)
    }
    if (path === `/api/v2/ai-runs/${runId}/events`) {
      return route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: [
          `id: 1\nevent: run.stage_changed\ndata: ${JSON.stringify({ stage: 'generating' })}\n\n`,
          `id: 2\nevent: message.delta\ndata: ${JSON.stringify({ messageId: assistantMessageId, delta: '这是流式回答。' })}\n\n`,
          `id: 3\nevent: run.completed\ndata: ${JSON.stringify({ runId, messageId: assistantMessageId })}\n\n`,
        ].join(''),
      })
    }
    if (path === `/api/v2/ai-runs/${runId}`) {
      return json(route, {
        id: runId, conversationId, branchId, requestMessageId: userMessageId,
        responseMessageId: assistantMessageId, status: 'SUCCEEDED', stage: 'completed',
        cancellable: false, errorCode: null, safeErrorMessage: null,
        createdAt: timestamps.createdAt, startedAt: timestamps.createdAt,
        completedAt: timestamps.updatedAt,
      })
    }
    if (path === `/api/v2/conversations/${conversationId}`) {
      return json(route, {
        conversation: conversation(),
        messages: [
          {
            id: userMessageId, branchId, parentMessageId: null, role: 'USER', status: 'FINALIZED',
            sequence: 1, content: '请立即回答', runId: null, attachments: [], citations: [],
            createdAt: timestamps.createdAt, finalizedAt: timestamps.createdAt,
          },
          {
            id: assistantMessageId, branchId, parentMessageId: userMessageId, role: 'ASSISTANT',
            status: 'FINALIZED', sequence: 2, content: '这是流式回答。', runId,
            attachments: [], citations: [], createdAt: timestamps.createdAt,
            finalizedAt: timestamps.updatedAt,
          },
        ],
      })
    }
    if (path === '/api/v2/artifacts') return json(route, [])
    return json(route, {})
  })

  await page.goto(`${baseUrl}/chat`)
  const composer = page.getByPlaceholder('输入消息')
  await expect(composer).toBeVisible()
  await composer.fill('请立即回答')
  await composer.press('Enter')

  const observedId = await createObserved
  await expect(page).toHaveURL(`${baseUrl}/chat/${observedId}`)
  await expect(page.locator('.user-message')).toHaveText('请立即回答')
  await expect(page.locator('.run-status')).toBeVisible()

  releaseCreate()
  await expect(page.locator('.assistant-message')).toContainText('这是流式回答。')
  await expect(page.locator('.run-status')).toHaveCount(0)
})
