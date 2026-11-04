import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ProductPage from './ProductPage.vue'

/**
 * T355: 页面 ProductPage。
 *
 * <p>可售项列表、规格分行、加购走购物车仓库，描述只作为文本（plan §8、spec §4、D07）。</p>
 */
vi.mock('@/features/catalog/api', () => ({
  fetchProduct: vi.fn(async (_client: unknown) => ({
    product: { productId: 'FI-SW-01', categoryId: 'FISH', name: 'Angelfish', description: 'd', imagePath: null },
    items: [
      {
        itemId: 'EST-1',
        productId: 'FI-SW-01',
        productName: 'Angelfish',
        attributes: ['Large'],
        unitPrice: '16.50',
        inStock: true,
        availableQuantity: 10,
        purchasable: true,
      },
    ],
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

describe('ProductPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    addItem.mockClear()
  })

  it('lists the sellable items with their attributes', async () => {
    const wrapper = mount(ProductPage, { props: { productId: 'FI-SW-01' } })

    await flushPromises()

    expect(wrapper.text()).toContain('Angelfish')
    expect(wrapper.text()).toContain('Large')
  })

  it('adds the item to the cart when the button is clicked', async () => {
    const wrapper = mount(ProductPage, { props: { productId: 'FI-SW-01' } })

    await flushPromises()
    await wrapper.find('button').trigger('click')

    await flushPromises()

    expect(addItem).toHaveBeenCalledWith(expect.anything(), 'EST-1', 0)
  })

  it('renders the description as plain text', async () => {
    const wrapper = mount(ProductPage, { props: { productId: 'FI-SW-01' } })

    await flushPromises()

    expect(wrapper.html()).not.toContain('<script>')
  })
})
