import { expect, test } from '@playwright/test'

const baseUrl = 'http://127.0.0.1:5173'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.clear()
      sessionStorage.clear()
    })
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    if (path === '/api/v2/auth/session') {
      return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ error: { code: 'AUTH_REQUIRED', message: '需要登录。' } }) })
    }
    if (path === '/api/v2/auth/login' && request.method() === 'POST') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          userId: '01USERACCOUNT000000000000', email: 'student@example.com', displayName: '测试用户',
          authLevel: 'PASSWORD', idleExpiresAt: '2026-08-27T09:00:00Z', absoluteExpiresAt: '2026-09-03T08:00:00Z',
        }),
      })
    }
    if (path === '/api/v2/conversations' && request.method() === 'GET') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, hasMore: false }) })
    }
    if (path === '/api/v2/knowledge-bases' && request.method() === 'GET') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null }) })
    }
    return route.continue()
  })
})

test('first entry opens the new conversation page', async ({ page }) => {
  await page.goto(`${baseUrl}/`)

  await expect(page).toHaveURL(`${baseUrl}/chat`)
  await expect(page.getByRole('heading', { name: '今天想完成什么？' })).toBeVisible()
})

test('guest actions ask for login and keep the draft', async ({ page }) => {
  await page.goto(`${baseUrl}/chat`)
  const composer = page.getByPlaceholder('输入消息')

  await composer.fill('帮我分析这道题')
  await composer.press('Enter')

  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await expect(composer).toHaveValue('帮我分析这道题')
})

test('protected route returns to the original intent after login', async ({ page }) => {
  await page.goto(`${baseUrl}/library`)

  await expect(page).toHaveURL(`${baseUrl}/chat`)
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()

  await page.getByPlaceholder('name@example.com').fill('student@example.com')
  await page.getByPlaceholder('请输入密码').fill('123456')
  await page.getByRole('button', { name: '登录', exact: true }).click()

  await expect(page).toHaveURL(`${baseUrl}/library`)
})
