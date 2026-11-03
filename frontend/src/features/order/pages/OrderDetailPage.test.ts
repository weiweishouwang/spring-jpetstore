import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import OrderDetailPage from './OrderDetailPage.vue'

/**
 * T375: 页面 OrderDetailPage。
 *
 * <p>成交金额与商品文本来自订单快照；商品被删除也不影响历史订单展示；他人订单统一 404；模拟卡
 * 只展示卡型与尾号（ORD-18/19、Q15、D05、plan §8）。</p>
 */
vi.mock('@/features/order/api', () => ({
  fetchOrder: vi.fn(async (_client: unknown, orderId: string) => {
    if (orderId !== '1001') {
      throw new Error('ORDER_NOT_FOUND')
    }
    return {
      orderId: '1001',
      createdAt: '2026-11-03T08:00:00Z',
      statusCode: 'P',
      statusLabel: '已提交（模拟，未发生真实扣款）',
      totalAmount: '33.00',
      currency: 'CNY',
      items: [
        {
          itemId: 'EST-1',
          productName: 'Angelfish',
          attributes: ['Large'],
          quantity: 2,
          unitPrice: '16.50',
          lineAmount: '33.00',
        },
      ],
      billingAddress: { firstName: 'Alice' },
      shippingAddress: { firstName: 'Bob' },
      payment: { cardType: 'Visa', lastFour: '1111' },
      courier: null,
      region: null,
    }
  }),
}))

describe('OrderDetailPage', () => {
  it('shows the traded amount and the product snapshot', async () => {
    const wrapper = mount(OrderDetailPage, { props: { orderId: '1001' } })

    await flushPromises()

    expect(wrapper.text()).toContain('33.00')
    expect(wrapper.text()).toContain('Angelfish')
  })

  it('shows only the card type and the last four digits', async () => {
    const wrapper = mount(OrderDetailPage, { props: { orderId: '1001' } })

    await flushPromises()

    expect(wrapper.text()).toContain('Visa')
    expect(wrapper.text()).toContain('1111')
    expect(wrapper.text()).not.toContain('FAKE-')
  })

  it('reports an order of somebody else as not found', async () => {
    const wrapper = mount(OrderDetailPage, { props: { orderId: '9999' } })

    await flushPromises()

    expect(wrapper.text()).toContain('不存在')
  })
})
