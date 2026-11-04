import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ItemPage from './ItemPage.vue'

/**
 * T357: 页面 ItemPage。
 *
 * <p>售价、库存与缺货提示与加购走购物车仓库；提示只是参考，不是库存预占（plan §8、CRT-04、
 * Q02）。</p>
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

// vi.mock 工厂会被提升到文件顶部，共享的 mock 要用 vi.hoisted 创建。
const { addItem } = vi.hoisted(() => ({
  addItem: vi.fn(async () => ({
    version: 1,
    lineCount: 1,
    totalQuantity: 1,
    items: [],
    subtotal: '16.50',
    currency: 'CNY',
    mergeConflict: false,
    pendingMerges: [],
  })),
}))

vi.mock('@/features/cart/api', () => ({ addItem }))

describe('ItemPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    addItem.mockClear()
  })

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

  it('adds the item to the cart when the button is clicked', async () => {
    const wrapper = mount(ItemPage, { props: { itemId: 'EST-1' } })

    await flushPromises()
    await wrapper.find('button').trigger('click')

    await flushPromises()

    expect(addItem).toHaveBeenCalledWith(expect.anything(), 'EST-1', 0)
  })
})
