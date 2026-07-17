import { expect, test } from '@playwright/test'

test.describe('资料库与对话资源视觉', () => {
  test.use({ viewport: { width: 1440, height: 960 } })

  test('资料库新建菜单和上传弹窗间距符合预期', async ({ page }) => {
    await page.goto('http://127.0.0.1:5173/library')
    await expect(page.getByRole('heading', { name: '资料库' })).toBeVisible()

    await page.getByRole('button', { name: '新建', exact: true }).click()
    const menu = page.locator('.new-menu')
    await expect(menu.getByRole('button')).toHaveCount(2)
    await expect(menu).toContainText('上传资料')
    await expect(menu).toContainText('新建知识库')
    await expect(menu).not.toContainText('创建思维导图')
    await expect(menu).not.toContainText('生成演示文稿')
    await expect(menu).not.toContainText('生成电子表格')

    await menu.getByRole('button', { name: '上传资料' }).click()
    const field = page.locator('.modal > .field')
    const footer = page.locator('.modal > footer')
    await expect(field).toBeVisible()
    await expect(footer).toBeVisible()

    const fieldBox = await field.boundingBox()
    const footerBox = await footer.boundingBox()
    expect(fieldBox).not.toBeNull()
    expect(footerBox).not.toBeNull()
    expect(footerBox!.y - (fieldBox!.y + fieldBox!.height)).toBeGreaterThanOrEqual(15)
  })

  test('PPT 对话卡片使用分类色图标且按钮区不重叠', async ({ page }) => {
    await page.goto('http://127.0.0.1:5173/chat')
    await page.getByRole('button', { name: '生成 PPT' }).click()

    const card = page.locator('.presentation-card--proposal')
    await expect(card).toBeVisible({ timeout: 15_000 })
    await expect(card.locator('[data-resource-type="presentation"]')).toBeVisible()

    const pageCountInput = card.locator('.presentation-field--pages input')
    const actions = card.locator('.presentation-card__actions')
    const inputBox = await pageCountInput.boundingBox()
    const actionsBox = await actions.boundingBox()
    expect(inputBox).not.toBeNull()
    expect(actionsBox).not.toBeNull()
    expect(actionsBox!.y - (inputBox!.y + inputBox!.height)).toBeGreaterThanOrEqual(13)
  })
})
