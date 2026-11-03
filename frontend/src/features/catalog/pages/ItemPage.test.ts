import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import ItemPage from './ItemPage.vue'

/**
 * T357: 页面 ItemPage。
 *
 * <p>售价、库存与缺货提示与加购；提示只是参考，不是库存预占（plan §8、CRT-04、Q02）。</p>
 */
vi.mock('@/features/catalog/api', () => ({
  fetchItem: vi.fn(async (_client: unknown, itemId: string) => ({
    itemId,
    productId: 'FI-SW-01',
    productName: 'Angelfish',
    attributes: ['Large'],
    unitPrice: '16.50',
    inStock: itemId !== 'EST-21',
    availableQuantity: itemId === 'EST-21' ? 0 : 10,
    purchasable: itemId !== 'EST-21',
  })),
}))

describe('ItemPage', () => {
  it('shows the price and the stock', async () => {
    const wrapper = mount(ItemPage, { props: { itemId: 'EST-1' } })

    await flushPromises()

    expect(wrapper.text()).toContain('16.50')
    expect(wrapper.text()).toContain('10')
  })

  it('shows an out of stock hint without reserving stock', async () => {
    const wrapper = mount(ItemPage, { props: { itemId: 'EST-21' } })

    await flushPromises()

    expect(wrapper.text()).toContain('缺货')
    expect(wrapper.text()).toContain('不代表')
  })

  it('offers the add to cart entry', async () => {
    const wrapper = mount(ItemPage, { props: { itemId: 'EST-1' } })

    await flushPromises()

    expect(wrapper.find('button').exists()).toBe(true)
  })
})
