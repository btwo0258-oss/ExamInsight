import { expect, test } from '@playwright/test'

const conversationId = '01WAITING00000000000000000'
const now = '2026-08-28T00:00:00Z'
const conversation = { id: conversationId, title: '交互验收', titleSource: 'MANUAL', type: 'GENERAL',
  status: 'ACTIVE', knowledgeBaseId: null, activeBranchId: 'BRANCH', messageCount: 2, version: 0,
  lastMessageAt: now, createdAt: now, updatedAt: now }
const messages = [
  { id: 'QUESTION', branchId: 'BRANCH', versionGroupId: 'QUESTION', parentMessageId: null,
    role: 'USER', status: 'FINALIZED', sequence: 1, content: '介绍一下学习方法', runId: null,
    attachments: [], citations: [], createdAt: now, finalizedAt: now },
  { id: 'ANSWER', branchId: 'BRANCH', versionGroupId: 'ANSWER', parentMessageId: 'QUESTION',
    role: 'ASSISTANT', status: 'FINALIZED', sequence: 2, content: '学习时先理解概念，再通过实践检验。'.repeat(40), runId: 'OLD-RUN',
    attachments: [], citations: [], createdAt: now, finalizedAt: now },
]

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('llm.token', 'mock-token')
    sessionStorage.setItem('llm.user', JSON.stringify({ id: 1, username: 'mock', nickname: '验收' }))
    const state = window as unknown as { qaAudio: Array<{ playing: boolean }>; qaStream?: ReadableStreamDefaultController<Uint8Array> }
    state.qaAudio = []
    class TestAudio {
      playing = false
      onended = null
      onerror = null
      constructor(public src: string) { state.qaAudio.push(this) }
      play() { this.playing = true; return Promise.resolve() }
      pause() { this.playing = false }
      removeAttribute() { this.src = '' }
      load() {}
    }
    window.Audio = TestAudio as unknown as typeof Audio
    const originalFetch = window.fetch.bind(window)
    window.fetch = (input, init) => {
      if (String(input).includes('/ai-runs/NEW-RUN/events')) {
        const stream = new ReadableStream<Uint8Array>({ start(controller) { state.qaStream = controller } })
        return Promise.resolve(new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } }))
      }
      return originalFetch(input, init)
    }
  })
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    const path = url.pathname
    let body: unknown = {}
    if (path === '/api/v2/auth/session') body = { userId: 'USER', email: 'test@example.invalid', displayName: '验收', authLevel: 'PASSWORD', idleExpiresAt: '2030-01-01T00:00:00Z', absoluteExpiresAt: '2030-01-01T00:00:00Z' }
    else if (path === '/api/v2/conversations') body = { items: [conversation], nextCursor: null, hasMore: false }
    else if (path.endsWith('/messages') && route.request().method() === 'POST') body = { userMessageId: 'NEW-USER', assistantMessageId: 'NEW-ANSWER', runId: 'NEW-RUN', eventUrl: '/api/v2/ai-runs/NEW-RUN/events' }
    else if (path.endsWith('/messages') || path === `/api/v2/conversations/${conversationId}`) body = { conversation, messages, versionGroups: [], segments: [], nextCursor: null, hasMore: false }
    else if (path === '/api/v2/artifacts') body = []
    else if (path === '/api/v2/artifacts/NEW-DOC') body = { id: 'NEW-DOC', conversationId, runId: 'NEW-RUN', type: 'DOCUMENT', status: 'DRAFT', title: '示例文档', content: { markdown: '# 示例文档' }, schemaVersion: 1, revision: 1, version: 0, confirmedAssetId: null, confirmedAssetVersionId: null, errorCode: null, createdAt: now, updatedAt: now, confirmedAt: null }
    else if (path === '/api/v2/knowledge-bases') body = { items: [], nextCursor: null }
    await route.fulfill({ json: body })
  })
  await page.goto(`http://localhost:5173/chat/${conversationId}`)
  await expect(page.locator('.assistant-message')).toHaveCount(1)
})

test('stopping speech silences the audio and prevents the next segment', async ({ page }) => {
  let requests = 0
  await page.route('**/api/v2/media/speech', async route => {
    requests++
    await route.fulfill({ contentType: 'audio/mpeg', body: Buffer.from('mock-audio') })
  })
  await page.getByTitle('朗读回答', { exact: true }).click()
  await expect.poll(() => page.evaluate(() => (window as unknown as { qaAudio: { playing: boolean }[] }).qaAudio.some(audio => audio.playing))).toBe(true)
  await page.getByTitle('停止朗读', { exact: true }).click()
  await expect.poll(() => page.evaluate(() => (window as unknown as { qaAudio: { playing: boolean }[] }).qaAudio.some(audio => audio.playing))).toBe(false)
  await expect(page.getByTitle('朗读回答', { exact: true })).toBeVisible()
  expect(requests).toBe(1)
})

for (const variant of [{ name: 'desktop', width: 1440, height: 1000, theme: 'light' }, { name: 'mobile-dark', width: 390, height: 844, theme: 'dark' }]) {
  test(`${variant.name}: three text skeleton lines sit below the blinking cursor and above the reserved card`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width: variant.width, height: variant.height })
    await page.evaluate(theme => document.documentElement.dataset.theme = theme, variant.theme)
    let release!: () => void
    const accepted = new Promise<void>(resolve => { release = resolve })
    await page.route(`**/api/v2/conversations/${conversationId}/messages`, async route => {
      if (route.request().method() !== 'POST') return route.fallback()
      await accepted
      await route.fulfill({ json: { userMessageId: 'NEW-USER', assistantMessageId: 'NEW-ANSWER', runId: 'NEW-RUN', eventUrl: '/events' } }).catch(() => {})
    })
    try {
      await page.getByRole('textbox', { name: '输入消息', exact: true }).fill('请生成一份学习文档')
      await page.getByRole('textbox', { name: '输入消息', exact: true }).press('Enter')
      const waiting = page.locator('.response-loading')
      await expect(waiting.locator('.response-skeleton')).toBeVisible()
      await expect(page.locator('.assistant-message .artifact-skeleton')).toHaveCount(1)
      await expect(page.locator('.user-message .artifact-card')).toHaveCount(0)
      const cursor = await waiting.locator('.response-loading__cursor').boundingBox()
      await expect(waiting.locator('.response-skeleton__line')).toHaveCount(3)
      const skeleton = await waiting.locator('.response-skeleton').boundingBox()
      const card = await page.locator('.artifact-card').boundingBox()
      expect(skeleton!.y).toBeGreaterThan(cursor!.y + cursor!.height)
      expect(card!.y).toBeGreaterThan(skeleton!.y + skeleton!.height)
      await page.screenshot({ path: testInfo.outputPath('waiting.png') })
    } finally { release() }
  })
}

test('a tool-start event reserves a card and real results replace the same slot', async ({ page }) => {
  await page.getByRole('textbox', { name: '输入消息', exact: true }).fill('请帮我处理这个主题')
  await page.getByRole('textbox', { name: '输入消息', exact: true }).press('Enter')
  await expect.poll(() => page.evaluate(() => Boolean((window as unknown as { qaStream?: unknown }).qaStream))).toBe(true)
  await expect(page.locator('.artifact-card')).toHaveCount(0)
  const event = async (name: string, data: Record<string, unknown>) => page.evaluate(({ name, data }) => {
    const controller = (window as unknown as { qaStream: ReadableStreamDefaultController<Uint8Array> }).qaStream
    controller.enqueue(new TextEncoder().encode(`event: ${name}\ndata: ${JSON.stringify(data)}\n\n`))
  }, { name, data })
  await event('artifact.started', { runId: 'NEW-RUN', generationId: 'GEN1', type: 'DOCUMENT', title: '示例文档' })
  await expect(page.locator('.artifact-skeleton')).toHaveCount(1)
  await event('message.delta', { messageId: 'NEW-ANSWER', delta: '已为你生成文档。' })
  await expect(page.locator('.response-skeleton')).toHaveCount(0)
  await expect(page.locator('.artifact-skeleton')).toHaveCount(1)
  await event('artifact.created', { runId: 'NEW-RUN', generationId: 'GEN1', artifactId: 'NEW-DOC' })
  await expect(page.locator('.artifact-card')).toHaveCount(1)
  await expect(page.locator('.artifact-skeleton')).toHaveCount(0)
  await expect(page.locator('.artifact-card')).toContainText('示例文档')
})
