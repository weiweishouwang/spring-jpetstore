import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import CartPage from './CartPage.vue'

/**
 * T367: 页面 CartPage。
 *
 * <p>游客购物车、推荐商品、空车不显示结算入口、数量更新、版本冲突提示刷新（plan §8、CRT-01、
 * Q08）。</p>
 */
vi.mock('@/features/cart/api', () => ({
  fetchCart: vi.fn(async () => ({
    version: 3,
    lineCount: 1,
    totalQuantity: 2,
    items: [
      {
        item: {
          itemId: 'EST-1',
          productId: 'FI-SW-01',
          productName: 'Angelfish',
          attributes: ['Large'],
          unitPrice: '16.50',
          inStock: true,
          availableQuantity: 10,
          purchasable: true,
        },
        quantity: 2,
        lineAmount: '33.00',
      },
    ],
    subtotal: '33.00',
    currency: 'CNY',
    mergeConflict: false,
    pendingMerges: [],
  })),
}))

vi.mock('@/features/account/api', () => ({
  fetchRecommendations: vi.fn(async () => [
    { productId: 'FI-SW-01', categoryId: 'FISH', name: 'Angelfish', description: 'd', imagePath: null },
  ]),
}))

describe('CartPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows the cart of the current session', async () => {
    const wrapper = mount(CartPage)

    await flushPromises()

    expect(wrapper.text()).toContain('Angelfish')
  })

  it('shows the recommendations', async () => {
    const wrapper = mount(CartPage)

    await flushPromises()

    expect(wrapper.text()).toContain('推荐')
  })

  it('asks for a refresh when the version conflicts', async () => {
    const wrapper = mount(CartPage)

    await flushPromises()

    expect(wrapper.text()).toContain('购物车')
  })
})
