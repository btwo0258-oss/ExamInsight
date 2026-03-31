import { test, expect } from '@playwright/test';

test.describe('Visual Regression and Interaction Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:5173/chat');
  });

  test('Version Switcher should be at the bottom of the bubble', async ({ page }) => {
    // Wait for the chat area to load
    await page.waitForSelector('.bubble-wrap');
    
    // Check if message-footer is below the bubble
    const bubbleWrap = page.locator('.bubble-content-wrap').first();
    const bubble = bubbleWrap.locator('.bubble');
    const footer = bubbleWrap.locator('.message-footer');
    
    const bubbleBox = await bubble.boundingBox();
    const footerBox = await footer.boundingBox();
    
    if (bubbleBox && footerBox) {
      expect(footerBox.y).toBeGreaterThanOrEqual(bubbleBox.y + bubbleBox.height);
    }
  });

  test('Copy button interaction changes icon to checkmark', async ({ page }) => {
    // Assuming there is at least one message
    await page.waitForSelector('.action-btn[title="复制"]');
    const copyBtn = page.locator('.action-btn[title="复制"]').first();
    
    // Check initial icon
    await expect(copyBtn.locator('svg use')).toHaveAttribute('href', /#copy/);
    
    // Click and check for checkmark
    await copyBtn.click();
    await expect(copyBtn.locator('svg use')).toHaveAttribute('href', /#check/);
    
    // Check animation class
    await expect(copyBtn.locator('svg')).toHaveClass(/anim-pop/);
    
    // Wait 1.5s for revert
    await page.waitForTimeout(1600);
    await expect(copyBtn.locator('svg use')).toHaveAttribute('href', /#copy/);
  });

  test('Theme switching updates "编辑问题" sub-text color seamlessly', async ({ page }) => {
    // Simulate user editing a message
    const editBtn = page.locator('.action-btn[title="编辑"]').first();
    if (await editBtn.isVisible()) {
      await editBtn.click();
      
      const editDesc = page.locator('.edit-desc');
      await expect(editDesc).toBeVisible();
      
      // Light theme color check
      const lightColor = await editDesc.evaluate((el) => window.getComputedStyle(el).color);
      expect(lightColor).toBe('rgb(96, 98, 102)'); // #606266
      
      // Switch to dark theme
      await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'));
      
      // Dark theme color check
      const darkColor = await editDesc.evaluate((el) => window.getComputedStyle(el).color);
      expect(darkColor).toBe('rgb(163, 166, 173)'); // #A3A6AD
    }
  });
});
