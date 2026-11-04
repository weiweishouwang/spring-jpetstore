import { expect, type Page } from '@playwright/test'

/**
 * T410: 端到端验收的公共动作（订单）。
 *
 * <p>提交订单必须等写请求落地再继续下一步：整页导航会中断还在飞行的请求，订单没有建成时
 * 列表页只会显示空列表，测试就退化成竞态（CI 上表现为随机失败）。等响应同时确认下单真的
 * 成功，而不是被中断或拒绝。</p>
 */

/** 在确认页提交订单，等下单写请求返回 201。 */
export async function placeOrder(page: Page): Promise<void> {
  const placed = page.waitForResponse(
    (response) => response.url().includes('/api/orders') && response.request().method() === 'POST',
  )
  await page.click('button:has-text("提交订单")')
  expect((await placed).status()).toBe(201)
}
