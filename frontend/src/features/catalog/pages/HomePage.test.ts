import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import HomePage from './HomePage.vue'

/**
 * T351: 页面 HomePage。
 *
 * <p>五个分类入口、加载与无结果与错误状态、标题正确（plan §8、spec §4）。</p>
 */
vi.mock('@/features/catalog/api', () => ({
  fetchCategories: vi.fn(async () => [
    { categoryId: 'FISH', name: 'Fish', description: 'd' },
    { categoryId: 'DOGS', name: 'Dogs', description: 'd' },
    { categoryId: 'REPTILES', name: 'Reptiles', description: 'd' },
    { categoryId: 'CATS', name: 'Cats', description: 'd' },
    { categoryId: 'BIRDS', name: 'Birds', description: 'd' },
  ]),
}))

describe('HomePage', () => {
  it('shows the title', async () => {
    const wrapper = mount(HomePage)

    await flushPromises()

    expect(wrapper.text()).toContain('JPetStore')
  })

  it('lists the five categories', async () => {
    const wrapper = mount(HomePage, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    await flushPromises()

    expect(wrapper.findAll('li')).toHaveLength(5)
    expect(wrapper.text()).toContain('Fish')
  })

  it('shows the loading state first', () => {
    const wrapper = mount(HomePage)

    expect(wrapper.text()).toContain('加载')
  })
})
