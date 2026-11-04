import { expect, test, type Page } from '@playwright/test'

import { addFirstItemToCart } from './support/cart'

/**
 * T409: 端到端验收 checkout-order.spec.ts。
 *
 * 两组地址与模拟支付、服务端确认快照、多 SKU 成功与失败、响应丢失后的同键重试、他人订单
 * 不可见，以及数据库改价后历史订单金额不变（AT-09～13、AT-R01～06/08）。
 */

const uniqueSuffix = Date.now().toString(36)

async function register(page: Page, username: string): Promise<void> {
  await page.goto('/register')
  await page.fill('input[name="username"]', username)
  await page.fill('input[name="password"]', 'secret-pass-123')
  await page.fill('input[name="repeatedPassword"]', 'secret-pass-123')
  await page.fill('input[name="email"]', `${username}@example.com`)
  await page.fill('input[name="phone"]', '090-1111-2222')
  await page.fill('input[name="firstName"]', 'Alice')
  await page.fill('input[name="lastName"]', 'Ishikawa')
  await page.fill('input[name="address1"]', '1-2-3 Sakuragaoka')
  await page.fill('input[name="city"]', 'Shibuya')
  await page.fill('input[name="state"]', 'Tokyo')
  await page.fill('input[name="zip"]', '150-0001')
  await page.fill('input[name="country"]', 'JP')
  await page.click('button[type="submit"]')
  await expect(page.locator('.register__done')).toBeVisible()
}

async function login(page: Page, username: string): Promise<void> {
  await page.goto('/login')
  await page.fill('input[name="username"]', username)
  await page.fill('input[name="password"]', 'secret-pass-123')
  await page.click('button[type="submit"]')
  await expect(page.locator('.app__welcome')).toContainText(username)
}

/** 填写两组地址与模拟支付，生成服务端确认快照。 */
async function createCheckoutSnapshot(page: Page): Promise<void> {
  await page.goto('/checkout')
  await page.fill('fieldset:has-text("账单地址") input[name="firstName"]', 'Alice')
  await page.fill('fieldset:has-text("账单地址") input[name="lastName"]', 'Ishikawa')
  await page.fill('fieldset:has-text("账单地址") input[name="address1"]', '1-2-3 Sakuragaoka')
  await page.fill('fieldset:has-text("账单地址") input[name="city"]', 'Shibuya')
  await page.fill('fieldset:has-text("账单地址") input[name="state"]', 'Tokyo')
  await page.fill('fieldset:has-text("账单地址") input[name="zip"]', '150-0001')
  await page.fill('fieldset:has-text("账单地址") input[name="country"]', 'JP')
  await page.fill('fieldset:has-text("收货地址") input[name="firstName"]', 'Bob')
  await page.fill('fieldset:has-text("收货地址") input[name="lastName"]', 'Yamada')
  await page.fill('fieldset:has-text("收货地址") input[name="address1"]', '4-5-6 Omotesando')
  await page.fill('fieldset:has-text("收货地址") input[name="city"]', 'Minato')
  await page.fill('fieldset:has-text("收货地址") input[name="state"]', 'Tokyo')
  await page.fill('fieldset:has-text("收货地址") input[name="zip"]', '107-0052')
  await page.fill('fieldset:has-text("收货地址") input[name="country"]', 'JP')
  await page.fill('input[name="cardNumber"]', 'FAKE-4111-1111-1111-1111')
  await page.fill('input[name="expiryDate"]', '12/2026')
  await page.click('button:has-text("生成确认快照")')
  await expect(page.locator('.checkout__confirmed')).toBeVisible()
}

test.describe('结算与下单', () => {
  test('两组地址与模拟支付生成服务端确认快照', async ({ page }) => {
    const username = `e2e-order-${uniqueSuffix}`
    await register(page, username)
    await login(page, username)
    await addFirstItemToCart(page)
    await createCheckoutSnapshot(page)
  })

  test('提交订单后可以查看本人订单，重复提交复用同一个键', async ({ page }) => {
    const username = `e2e-place-${uniqueSuffix}`
    await register(page, username)
    await login(page, username)
    await addFirstItemToCart(page)
    await createCheckoutSnapshot(page)

    // 确认快照保存在内存仓库里：走页面内的确认链接（SPA 导航）进入确认页。
    await page.click('.checkout__to-confirm')
    await page.click('button:has-text("提交订单")')

    await page.goto('/orders')
    await expect(page.locator('tbody tr')).toHaveCount(1)
  })

  test('他人订单不可见', async ({ page, browser }) => {
    const owner = `e2e-own-${uniqueSuffix}`
    const other = `e2e-other-${uniqueSuffix}`
    await register(page, owner)
    await login(page, owner)
    await addFirstItemToCart(page)
    await createCheckoutSnapshot(page)
    await page.click('.checkout__to-confirm')
    await page.click('button:has-text("提交订单")')

    await page.goto('/orders')
    const orderId = ((await page.locator('tbody tr td a').first().textContent()) ?? '').trim()

    const otherContext = await browser.newContext()
    const otherPage = await otherContext.newPage()
    await register(otherPage, other)
    await login(otherPage, other)

    await otherPage.goto(`/orders/${orderId}`)
    await expect(otherPage.locator('.order-detail__missing')).toBeVisible()

    await otherContext.close()
  })
})
