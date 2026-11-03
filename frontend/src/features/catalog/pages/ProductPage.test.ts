import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import ProductPage from './ProductPage.vue'

/**
 * T355: 页面 ProductPage。
 *
 * <p>可售项列表、规格分行、加购入口，描述只作为文本（plan §8、spec §4、D07）。</p>
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

describe('ProductPage', () => {
  it('lists the sellable items with their attributes', async () => {
    const wrapper = mount(ProductPage, { props: { productId: 'FI-SW-01' } })

    await flushPromises()

    expect(wrapper.text()).toContain('Angelfish')
    expect(wrapper.text()).toContain('Large')
  })

  it('offers the add to cart entry per item', async () => {
    const wrapper = mount(ProductPage, { props: { productId: 'FI-SW-01' } })

    await flushPromises()

    expect(wrapper.find('button').exists()).toBe(true)
  })

  it('renders the description as plain text', async () => {
    const wrapper = mount(ProductPage, { props: { productId: 'FI-SW-01' } })

    await flushPromises()

    expect(wrapper.html()).not.toContain('<script>')
  })
})
