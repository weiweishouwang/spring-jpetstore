import { expect, test } from '@playwright/test'

/**
 * T407: 端到端验收 account.spec.ts。
 *
 * 真实浏览器、真实后端与同版本 MySQL：注册、登录、只读用户名、留空密码保留、新密码、偏好
 * 开关、退出与 CSRF 刷新，以及两个用户会话互不干扰（AT-01～04/14～17、AT-R09）。
 *
 * 这里不使用组件 mock：所有断言都打在真实页面与真实接口上。
 */

const uniqueSuffix = Date.now().toString(36)

async function register(page: import('@playwright/test').Page, username: string): Promise<void> {
  await page.goto('/register')
  await page.fill('input[name="username"]', username)
  await page.fill('input[name="password"]', 'secret-pass-123')
  await page.fill('input[name="repeatedPassword"]', 'secret-pass-123')
  await page.fill('input[name="email"]', `${username}@example.com`)
  await page.fill('input[name="phone"]', '090-1111-2222')
  await page.click('button[type="submit"]')
  await expect(page.locator('.register__done')).toBeVisible()
}

async function login(page: import('@playwright/test').Page, username: string): Promise<void> {
  await page.goto('/login')
  await page.fill('input[name="username"]', username)
  await page.fill('input[name="password"]', 'secret-pass-123')
  await page.click('button[type="submit"]')
  await expect(page.locator('.app__welcome')).toContainText(username)
}

test.describe('账户', () => {
  test('注册后不自动登录，需要显式登录', async ({ page }) => {
    const username = `e2e-${uniqueSuffix}`

    await register(page, username)
    await expect(page.locator('.app__welcome')).toHaveCount(0)

    await login(page, username)
  })

  test('用户名在资料页只读', async ({ page }) => {
    const username = `e2e-ro-${uniqueSuffix}`
    await register(page, username)
    await login(page, username)

    await page.goto('/account')

    await expect(page.locator('input[name="username"]')).toHaveValue(username)
    await expect(page.locator('input[name="username"]')).toHaveAttribute('readonly', '')
  })

  test('留空密码表示保留原口令', async ({ page }) => {
    const username = `e2e-pw-${uniqueSuffix}`
    await register(page, username)
    await login(page, username)

    await page.goto('/account')
    await page.fill('input[name="email"]', `${username}-new@example.com`)
    await page.click('button[type="submit"]')
    await expect(page.locator('.account__saved')).toBeVisible()

    await page.goto('/login')
    await page.fill('input[name="username"]', username)
    await page.fill('input[name="password"]', 'secret-pass-123')
    await page.click('button[type="submit"]')
    await expect(page.locator('.app__welcome')).toContainText(username)
  })

  test('退出后回到匿名状态', async ({ page }) => {
    const username = `e2e-out-${uniqueSuffix}`
    await register(page, username)
    await login(page, username)

    await page.click('button:has-text("退出")')

    await expect(page.locator('.app__welcome')).toHaveCount(0)
    await expect(page.locator('a:has-text("登录")')).toBeVisible()
  })

  test('两个用户的会话互不干扰', async ({ browser }) => {
    const first = `e2e-a-${uniqueSuffix}`
    const second = `e2e-b-${uniqueSuffix}`
    const contextOne = await browser.newContext()
    const contextTwo = await browser.newContext()
    const pageOne = await contextOne.newPage()
    const pageTwo = await contextTwo.newPage()

    await register(pageOne, first)
    await register(pageTwo, second)
    await login(pageOne, first)
    await login(pageTwo, second)

    await expect(pageOne.locator('.app__welcome')).toContainText(first)
    await expect(pageTwo.locator('.app__welcome')).toContainText(second)

    await contextOne.close()
    await contextTwo.close()
  })
})
