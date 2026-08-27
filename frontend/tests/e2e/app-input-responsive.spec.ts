import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('llm.token', 'mock-token')
    sessionStorage.setItem('llm.user', JSON.stringify({ id: 1, username: 'mock-user', nickname: 'Mock User' }))
  })
})

test('desktop composer keeps attachment on the left and voice on the right', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto('http://localhost:5173/chat')

  const composer = page.locator('.chat-composer').last()
  await expect(composer.locator('.desktop-attachment')).toBeVisible()
  await expect(composer.locator('.mobile-plus')).toBeHidden()
  await expect(composer.locator('.toolbar-left .image-actions')).toHaveCount(0)

  const rightChildren = await composer.locator('.toolbar-right').evaluate((element) =>
    Array.from(element.children).map((child) => child.className),
  )
  expect(String(rightChildren[0])).toContain('voice-control')
  expect(rightChildren).toHaveLength(2)
})

test('mobile left pill grows upward with attachment, photo upload and camera actions', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('http://localhost:5173/chat')

  const composer = page.locator('.chat-composer').last()
  await expect(composer.locator('.desktop-attachment')).toBeHidden()
  await composer.locator('.mobile-plus').evaluate((button: HTMLButtonElement) => button.click())

  const menu = composer.locator('.mobile-add-menu')
  const pill = composer.locator('.mobile-add-pill')
  await expect(menu).toBeVisible()
  await expect(pill).toHaveClass(/mobile-add-pill--open/)
  await expect(menu.getByRole('button', { name: '上传附件' })).toBeVisible()
  await expect(menu.getByRole('button', { name: '上传照片' })).toBeVisible()
  await expect(menu.getByRole('button', { name: '拍照' })).toBeVisible()
  const menuBox = await menu.boundingBox()
  const pillBox = await pill.boundingBox()
  const plusBox = await composer.locator('.mobile-plus').boundingBox()
  expect(menuBox).not.toBeNull()
  expect(pillBox).not.toBeNull()
  expect(plusBox).not.toBeNull()
  expect(menuBox!.height).toBeGreaterThan(menuBox!.width)
  expect(pillBox!.height).toBeGreaterThan(pillBox!.width)
  expect(Math.abs((pillBox!.x + pillBox!.width / 2) - (plusBox!.x + plusBox!.width / 2))).toBeLessThan(1)
  expect(Math.abs((pillBox!.y + pillBox!.height) - (plusBox!.y + plusBox!.height + 4))).toBeLessThan(1)
  expect(pillBox!.x).toBeGreaterThanOrEqual(0)
  expect(pillBox!.x + pillBox!.width).toBeLessThanOrEqual(390)

  await page.locator('.main-textarea').evaluate((element) => {
    element.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }))
  })
  await expect(menu).toBeHidden()
})
