import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import OrdersPage from './OrdersPage.vue'

/**
 * T373: 页面 OrdersPage。
 *
 * <p>只显示本人的订单摘要，最新优先且首期不分页，空列表有明确提示，未登录引导登录
 * （ORD-17、Q11、NFR-02、plan §8）。</p>
 */
vi.mock('@/features/order/api', () => ({
  listOrders: vi.fn(async () => [
    {
      orderId: '1002',
      createdAt: '2026-11-03T08:00:00Z',
      statusCode: 'P',
      statusLabel: '已提交（模拟，未发生真实扣款）',
      totalAmount: '33.00',
      currency: 'CNY',
    },
    {
      orderId: '1001',
      createdAt: '2026-10-01T08:00:00Z',
      statusCode: 'P',
      statusLabel: '已提交（模拟，未发生真实扣款）',
      totalAmount: '16.50',
      currency: 'CNY',
    },
  ]),
}))

describe('OrdersPage', () => {
  it('lists the own orders newest first', async () => {
    const wrapper = mount(OrdersPage)

    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0]?.text()).toContain('1002')
    expect(rows[1]?.text()).toContain('1001')
  })

  it('shows the order total as a string', async () => {
    const wrapper = mount(OrdersPage)

    await flushPromises()

    expect(wrapper.text()).toContain('33.00')
  })
})
