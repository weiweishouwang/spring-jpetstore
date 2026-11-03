import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import CategoryPage from './CategoryPage.vue'

/**
 * T353: 页面 CategoryPage。
 *
 * <p>分类下的产品列表与名称；分类不存在时不显示伪造页面（plan §8、spec §4）。</p>
 */
vi.mock('@/features/catalog/api', () => ({
  fetchProductsByCategory: vi.fn(async (_client: unknown, categoryId: string) =>
    categoryId === 'FISH'
      ? [{ productId: 'FI-SW-01', categoryId: 'FISH', name: 'Angelfish', description: 'd', imagePath: null }]
      : [],
  ),
}))

describe('CategoryPage', () => {
  it('shows the products of the category', async () => {
    const wrapper = mount(CategoryPage, { props: { categoryId: 'FISH' } })

    await flushPromises()

    expect(wrapper.text()).toContain('Angelfish')
  })

  it('does not show a fake page for an unknown category', async () => {
    const wrapper = mount(CategoryPage, { props: { categoryId: 'NOPE' } })

    await flushPromises()

    expect(wrapper.text()).not.toContain('Angelfish')
  })

  it('shows the loading state first', () => {
    const wrapper = mount(CategoryPage, { props: { categoryId: 'FISH' } })

    expect(wrapper.text()).toContain('加载')
  })
})
