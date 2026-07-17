import { expect, test } from '@playwright/test'

const baseUrl = 'http://127.0.0.1:5173'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.clear()
    sessionStorage.clear()
  })
})

test('first entry opens the new conversation page', async ({ page }) => {
  await page.goto(`${baseUrl}/`)

  await expect(page).toHaveURL(`${baseUrl}/chat`)
  await expect(page.getByRole('heading', { name: '我们先从哪里开始呢？' })).toBeVisible()
})

test('guest actions ask for login and keep the draft', async ({ page }) => {
  await page.goto(`${baseUrl}/chat`)
  const composer = page.getByPlaceholder('输入消息，Enter 发送，Shift+Enter 换行')

  await composer.fill('帮我分析这道题')
  await composer.press('Enter')

  await expect(page.getByText('请登录或注册以继续')).toBeVisible()
  await expect(composer).toHaveValue('帮我分析这道题')
})

test('protected route returns to the original intent after login', async ({ page }) => {
  await page.goto(`${baseUrl}/library`)

  await expect(page).toHaveURL(`${baseUrl}/chat`)
  await expect(page.getByText('请登录或注册以继续')).toBeVisible()

  await page.getByPlaceholder('请输入账号').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('123456')
  await page.getByRole('button', { name: '登录', exact: true }).click()

  await expect(page).toHaveURL(`${baseUrl}/library`)
})
