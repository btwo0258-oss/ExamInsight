import { expect, test } from '@playwright/test'

test.describe.skip('资料库与对话资源视觉（旧版独立 PPT 工作区，V2 已由对话生成卡接管）', () => {
  test.use({ viewport: { width: 1440, height: 960 } })

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
