import { expect, test } from '@playwright/test'

/**
 * T408: 端到端验收 catalog-cart.spec.ts。
 *
 * 目录与搜索、游客加购、改零后重加、两个标签页的版本冲突，以及两个会话的购物车隔离
 * （AT-05～08/18、AT-R07）。
 */

test.describe('目录与购物车', () => {
  test('首页列出五个分类入口', async ({ page }) => {
    await page.goto('/')

    await expect(page.locator('h1')).toContainText('JPetStore')
    await expect(page.locator('li')).toHaveCount(5)
  })

  test('搜索按名称包含匹配，空结果显示空状态', async ({ page }) => {
    await page.goto('/search?keyword=angel')
    await expect(page.locator('body')).toContainText('Angelfish')

    await page.goto('/search?keyword=nothing-matches-this')
    await expect(page.locator('.search')).toContainText('没有找到')
  })

  test('游客可以加购并在购物车看到明细', async ({ page }) => {
    await page.goto('/')
    await page.locator('a:has-text("Fish")').click()
    await page.locator('li a').first().click()
    await page.locator('button:has-text("加入购物车")').first().click()

    await page.goto('/cart')
    await expect(page.locator('.cart-table')).toContainText('Angelfish')
  })

  test('把数量改成零会移除该明细', async ({ page }) => {
    await page.goto('/')
    await page.locator('a:has-text("Fish")').click()
    await page.locator('li a').first().click()
    await page.locator('button:has-text("加入购物车")').first().click()

    await page.goto('/cart')
    await page.fill('input[name^="quantity-"]', '0')
    await page.locator('input[name^="quantity-"]').press('Enter')

    await expect(page.locator('.cart-table')).toContainText('空')
  })

  test('两个标签页的版本冲突会提示刷新', async ({ browser }) => {
    const context = await browser.newContext()
    const pageOne = await context.newPage()
    const pageTwo = await context.newPage()

    await pageOne.goto('/')
    await pageOne.locator('a:has-text("Fish")').click()
    await pageOne.locator('li a').first().click()
    await pageOne.locator('button:has-text("加入购物车")').first().click()
    await pageOne.goto('/cart')

    await pageTwo.goto('/cart')
    await pageOne.reload()
    await pageTwo.locator('input[name^="quantity-"]').fill('3')
    await pageTwo.locator('input[name^="quantity-"]').press('Enter')
    await pageOne.locator('input[name^="quantity-"]').fill('2')
    await pageOne.locator('input[name^="quantity-"]').press('Enter')

    await expect(pageOne.locator('.cart__refresh')).toBeVisible()

    await context.close()
  })

  test('两个会话的购物车互相隔离', async ({ browser }) => {
    const contextOne = await browser.newContext()
    const contextTwo = await browser.newContext()
    const pageOne = await contextOne.newPage()
    const pageTwo = await contextTwo.newPage()

    await pageOne.goto('/')
    await pageOne.locator('a:has-text("Fish")').click()
    await pageOne.locator('li a').first().click()
    await pageOne.locator('button:has-text("加入购物车")').first().click()

    await pageTwo.goto('/cart')
    await expect(pageTwo.locator('.cart-table')).toContainText('空')

    await pageOne.goto('/cart')
    await expect(pageOne.locator('.cart-table')).toContainText('Angelfish')

    await contextOne.close()
    await contextTwo.close()
  })
})
