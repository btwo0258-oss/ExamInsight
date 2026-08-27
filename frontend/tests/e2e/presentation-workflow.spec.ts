import { expect, test } from '@playwright/test'

test.describe.skip('PPT workspace（旧版独立路由，V2 已由对话生成卡与独立编辑页接管）', () => {
  test.use({ viewport: { width: 1440, height: 960 } })

  test('runs config, outline, generation and preview in Mock mode', async ({ page }) => {
    await page.goto('http://localhost:5173/presentations/new?topic=Java%20%E5%A4%9A%E6%80%81&title=Java%20%E5%A4%9A%E6%80%81%E5%A4%8D%E4%B9%A0&returnTo=/chat')
    await page.evaluate(() => sessionStorage.clear())
    await page.reload()

    await expect(page.getByRole('heading', { name: 'Java 多态复习' })).toBeVisible()
    await expect(page.getByRole('button', { name: '生成页面大纲' })).toBeEnabled()
    await expect(page.locator('body')).not.toHaveCSS('overflow-x', 'scroll')

    await page.getByRole('button', { name: '生成页面大纲' }).click()
    await expect(page.getByRole('heading', { name: '检查并编辑页面大纲' })).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.outline-card')).toHaveCount(8)

    await page.locator('.outline-card').nth(1).getByLabel('页面标题').fill('动态绑定与运行时类型')
    await page.getByRole('button', { name: '确认大纲并生成' }).click()

    await expect(page.getByRole('button', { name: '下载 PPTX' })).toBeVisible({ timeout: 20_000 })
    await expect(page.locator('.slide-list button')).toHaveCount(8)
    await expect(page.locator('.slide-stage .slide-preview')).toBeVisible()
    await expect(page.locator('.slide-list button').nth(1).getByText('动态绑定与运行时类型', { exact: true })).toBeVisible()

    await page.locator('.slide-list button').nth(1).click()
    await page.getByRole('button', { name: '编辑当前页' }).click()
    await expect(page.getByText('编辑当前页', { exact: true })).toBeVisible()
    await page.getByLabel('页面标题').fill('运行时多态机制')
    await page.getByLabel('页面要点（一行一个）').fill('动态绑定发生在运行时\n父类引用可以指向子类对象')
    await page.getByLabel('演讲者备注').fill('结合 Java 示例解释方法分派。')
    await page.getByRole('button', { name: '保存修改' }).click()

    await expect(page.getByText('当前页已保存')).toBeVisible()
    await expect(page.getByRole('button', { name: '下载 PPTX' })).toBeVisible()
    await expect(page.locator('.preview-toolbar strong')).toHaveText('运行时多态机制')
    await expect(page.locator('.slide-list button').nth(1)).toHaveClass(/selected/)
    await expect(page.locator('.slide-list button').nth(1).getByText('运行时多态机制', { exact: true })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('button', { name: '下载 PPTX' })).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.slide-list button').nth(1).getByText('运行时多态机制', { exact: true })).toBeVisible()
    await page.screenshot({ path: 'test-results/presentation-workspace.png', fullPage: true })
  })
})
