import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import CheckoutPage from './CheckoutPage.vue'

/**
 * T369: 页面 CheckoutPage。
 *
 * <p>先登录；两组地址与模拟支付提交后由服务端生成确认快照；快照失效要求重新确认
 * （ORD-02～07、Q03、plan §8）。</p>
 */
vi.mock('@/features/order/api', () => ({
  createCheckout: vi.fn(async () => ({
    checkoutId: 'ck-1',
    cartVersion: 2,
    createdAt: '2026-11-03T08:00:00Z',
    expiresAt: '2026-11-03T08:15:00Z',
    billingAddress: { firstName: 'Alice' },
    shippingAddress: { firstName: 'Bob' },
    payment: { cardType: 'Visa', lastFour: '1111' },
    items: [],
    totalAmount: '33.00',
    currency: 'CNY',
  })),
}))

vi.mock('@/features/cart/api', () => ({
  fetchCart: vi.fn(async () => ({
    version: 2,
    lineCount: 1,
    totalQuantity: 2,
    items: [],
    subtotal: '33.00',
    currency: 'CNY',
    mergeConflict: false,
    pendingMerges: [],
  })),
}))

describe('CheckoutPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('asks for both addresses and the simulated payment', async () => {
    const wrapper = mount(CheckoutPage)

    await flushPromises()

    expect(wrapper.text()).toContain('账单地址')
    expect(wrapper.text()).toContain('收货地址')
    expect(wrapper.text()).toContain('模拟支付')
  })

  it('creates the server side snapshot', async () => {
    const wrapper = mount(CheckoutPage)

    await flushPromises()
    await wrapper.find('form').trigger('submit')

    await flushPromises()

    expect(wrapper.text()).toContain('确认')
  })
})
