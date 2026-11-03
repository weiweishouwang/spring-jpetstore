import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import SearchPage from './SearchPage.vue'

/**
 * T359: 页面 SearchPage。
 *
 * <p>名称搜索、大小写无关、空词与特殊输入按 Q14 处理、空结果明确显示（plan §8、Q14）。</p>
 */
vi.mock('@/features/catalog/api', () => ({
  searchProducts: vi.fn(async (_client: unknown, keyword: string) =>
    keyword.trim().length === 0 || keyword.trim().toLowerCase() === 'nothing-matches'
      ? []
      : [{ productId: 'FI-SW-01', categoryId: 'FISH', name: 'Angelfish', description: 'd', imagePath: null }],
  ),
}))

describe('SearchPage', () => {
  it('searches by product name', async () => {
    const wrapper = mount(SearchPage, { props: { initialKeyword: 'angel' } })

    await flushPromises()

    expect(wrapper.text()).toContain('Angelfish')
  })

  it('refuses an empty keyword', async () => {
    const wrapper = mount(SearchPage, { props: { initialKeyword: '   ' } })

    await flushPromises()

    expect(wrapper.text()).toContain('不能为空')
  })

  it('shows an empty result', async () => {
    const wrapper = mount(SearchPage, { props: { initialKeyword: 'nothing-matches' } })

    await flushPromises()

    expect(wrapper.text()).toContain('没有找到')
  })
})
