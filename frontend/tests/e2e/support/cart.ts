import { expect, type Page } from '@playwright/test'

/**
 * T410: 端到端验收的公共动作。
 *
 * <p>加购必须等写请求落地再继续下一步：整页导航会中断还在飞行的请求，加购被丢弃时页面只会
 * 安静地显示空购物车，测试就退化成竞态（CI 上表现为随机失败）。等响应同时确认写操作真的
 * 成功，而不是被中断或拒绝。</p>
 */

/** 首页 → Fish → 第一个产品 → 加购第一个可售项；等加购写请求返回成功。 */
export async function addFirstItemToCart(page: Page): Promise<void> {
  await page.goto('/')
  await page.locator('a:has-text("Fish")').click()
  await page.locator('main li a').first().click()
  const added = page.waitForResponse(
    (response) => response.url().includes('/api/cart/items') && response.request().method() === 'POST',
  )
  await page.locator('button:has-text("加入购物车")').first().click()
  expect((await added).status()).toBe(200)
}
