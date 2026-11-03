import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { components } from '@/shared/api/schema'

import ConfirmPage from './ConfirmPage.vue'

function address(firstName: string): components['schemas']['Address'] {
  return {
    firstName,
    lastName: 'Last',
    address1: '1-2-3 Street',
    address2: null,
    city: 'City',
    state: 'State',
    zip: '12345',
    country: 'JP',
  }
}

/**
 * T371: 页面 ConfirmPage。
 *
 * <p>显示服务端确认快照；重复点击复用同一个幂等键；变价 409 明确提示重新确认；没有快照就不建单
 * （ORD-06/07、Q03、NFR-03、plan §8）。</p>
 */
vi.mock('@/features/order/api', () => ({
  placeOrder: vi.fn(async () => ({
    status: 201,
    location: '/api/orders/1001',
    replayed: false,
    body: { orderId: '1001', createdAt: '2026-11-03T08:00:00Z', statusCode: 'P', statusLabel: '已提交', totalAmount: '33.00', currency: 'CNY' },
  })),
}))

describe('ConfirmPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows the confirmed content and the total', async () => {
    const wrapper = mount(ConfirmPage, {
      props: {
        checkout: {
          checkoutId: 'ck-1',
          cartVersion: 2,
          createdAt: '2026-11-03T08:00:00Z',
          expiresAt: '2026-11-03T08:15:00Z',
          billingAddress: address('Alice'),
          shippingAddress: address('Bob'),
          payment: { cardType: 'Visa', lastFour: '1111' },
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
          totalAmount: '33.00',
          currency: 'CNY',
        },
      },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('Angelfish')
    expect(wrapper.text()).toContain('33.00')
  })

  it('does not create an order without a snapshot', async () => {
    const wrapper = mount(ConfirmPage)

    await flushPromises()

    expect(wrapper.find('button[type="submit"]').exists()).toBe(false)
  })
})
